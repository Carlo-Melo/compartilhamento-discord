# Independent validation only: no services, listeners, firewall or certificates.
$ErrorActionPreference = 'Stop'
$rootPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$workRoot = Join-Path $rootPath 'work'
[IO.Directory]::CreateDirectory($workRoot) | Out-Null
$caseRoot = Join-Path $workRoot ('capture-setup-test-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($caseRoot) | Out-Null
$envPath = Join-Path $caseRoot '.env.capture'
$yamlPath = Join-Path $caseRoot 'capture.yaml'
$names = @('CAPTURE_LAN_IP', 'CAPTURE_PROOF_ORIGIN', 'CAPTURE_PROOF_PRESENTER_KEY',
    'CAPTURE_PROOF_VIEWER_KEY', 'LIVEKIT_INTERNAL_URL', 'LIVEKIT_PUBLIC_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$checks = 0
function Require-Failure([scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Caso invalido foi aceito.' }
}
try {
    foreach ($script in @('Initialize-CaptureProof.ps1', 'Import-CaptureEnvironment.ps1', 'Start-CaptureProofFrontend.ps1')) {
        $tokens = $null
        $parseErrors = $null
        [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $script), [ref]$tokens, [ref]$parseErrors) | Out-Null
        if ($parseErrors.Count -gt 0) { throw 'Script PowerShell com erro de sintaxe.' }
        $checks++
    }
    & (Join-Path $PSScriptRoot 'Initialize-CaptureProof.ps1') -LanIp '192.168.254.254' -EnvironmentPath $envPath -ConfigPath $yamlPath | Out-Null
    . (Join-Path $PSScriptRoot 'Import-CaptureEnvironment.ps1') -Path $envPath
    if ($env:CAPTURE_LAN_IP -ne '192.168.254.254' -or $env:LIVEKIT_INTERNAL_URL -ne 'http://127.0.0.1:7890' -or
        $env:CAPTURE_PROOF_PRESENTER_KEY -eq $env:CAPTURE_PROOF_VIEWER_KEY -or
        (Get-Content -LiteralPath $yamlPath -Raw).Contains('__LAN_IP__')) { throw 'Ambiente gerado inconsistente.' }
    $checks++
    $original = [IO.File]::ReadAllText($envPath)
    Require-Failure { & (Join-Path $PSScriptRoot 'Initialize-CaptureProof.ps1') -LanIp '192.168.254.254' -EnvironmentPath $envPath -ConfigPath $yamlPath }
    if ([IO.File]::ReadAllText($envPath) -ne $original) { throw 'Arquivo existente foi alterado.' }
    $checks++
    foreach ($invalidIp in @('127.0.0.1', '0.0.0.0', '8.8.8.8', 'localhost', '192.168.1.50;whoami')) {
        Require-Failure { & (Join-Path $PSScriptRoot 'Initialize-CaptureProof.ps1') -LanIp $invalidIp -EnvironmentPath (Join-Path $caseRoot 'bad.env') -ConfigPath (Join-Path $caseRoot 'bad.yaml') }
        $checks++
    }
    $badPath = Join-Path $caseRoot 'bad.env'
    [IO.File]::WriteAllText($badPath, $original + "`nUNEXPECTED_VARIABLE=ignored`n")
    $env:CAPTURE_LAN_IP = 'unchanged'
    Require-Failure { . (Join-Path $PSScriptRoot 'Import-CaptureEnvironment.ps1') -Path $badPath }
    if ($env:CAPTURE_LAN_IP -ne 'unchanged') { throw 'Importacao parcial ocorreu antes da validacao.' }
    $checks++
    [IO.File]::WriteAllText($badPath, $original.Replace('CAPTURE_LAN_IP=192.168.254.254', 'CAPTURE_LAN_IP=$(Write-Output forbidden)'))
    Require-Failure { . (Join-Path $PSScriptRoot 'Import-CaptureEnvironment.ps1') -Path $badPath }
    $checks++
} finally {
    foreach ($name in $names) {
        if ($null -eq $previous[$name]) {
            Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
    # Verificar alvo absoluto antes da unica remocao recursiva, sempre na pasta de teste.
    $resolvedCase = [IO.Path]::GetFullPath($caseRoot)
    $resolvedWork = [IO.Path]::GetFullPath($workRoot) + [IO.Path]::DirectorySeparatorChar
    if ($resolvedCase.StartsWith($resolvedWork, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedCase -Recurse -Force
    }
}
foreach ($name in $names) {
    if ($null -eq $previous[$name]) {
        if (Test-Path -LiteralPath ('Env:' + $name)) { throw 'Variavel originalmente ausente nao foi removida.' }
    } elseif ([Environment]::GetEnvironmentVariable($name, 'Process') -cne $previous[$name]) {
        throw 'Ambiente original nao foi restaurado.'
    }
}
$checks++
Write-Output "$checks verificacoes de setup T-03 aprovadas, sem servicos ou segredos exibidos."
