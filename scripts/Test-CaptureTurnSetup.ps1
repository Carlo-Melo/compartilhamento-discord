# Testes locais com certificado efemero autoassinado APENAS em arquivo; nao e
# instalado/confiado, nao abre listener e nao comprova TURN/TLS no navegador.
$ErrorActionPreference = 'Stop'
$rootPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$workRoot = Join-Path $rootPath 'work'
[IO.Directory]::CreateDirectory($workRoot) | Out-Null
$caseRoot = Join-Path $workRoot ('capture-turn-test-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($caseRoot) | Out-Null
$names = @('CAPTURE_TURN_BIND_IP', 'CAPTURE_TURN_DOMAIN', 'CAPTURE_TURN_TLS_PORT',
    'CAPTURE_TURN_CERT_PATH', 'CAPTURE_TURN_KEY_PATH', 'CAPTURE_TURN_CONFIG_PATH',
    'CAPTURE_LAN_IP', 'CAPTURE_PROOF_ORIGIN', 'CAPTURE_PROOF_PRESENTER_KEY',
    'CAPTURE_PROOF_VIEWER_KEY', 'LIVEKIT_INTERNAL_URL', 'LIVEKIT_PUBLIC_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$checks = 0
function Require-TurnFailure([scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Caso TURN invalido foi aceito.' }
}
try {
    foreach ($script in @('Capture-TurnSupport.ps1', 'Initialize-CaptureTurn.ps1', 'Import-CaptureTurnEnvironment.ps1')) {
        $tokens = $null; $parseErrors = $null
        [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $script), [ref]$tokens, [ref]$parseErrors) | Out-Null
        if ($parseErrors.Count -gt 0) { throw 'Script TURN com erro de sintaxe.' }
        $checks++
    }
    $baseEnv = Join-Path $caseRoot '.env.capture'
    $baseYaml = Join-Path $caseRoot 'capture.yaml'
    & (Join-Path $PSScriptRoot 'Initialize-CaptureProof.ps1') -LanIp '192.168.254.254' -EnvironmentPath $baseEnv -ConfigPath $baseYaml | Out-Null
    $certPath = Join-Path $caseRoot 'turn.pem'; $keyPath = Join-Path $caseRoot 'turn.key'
    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=turn.example.test', $rsa,
            [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $san = [Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
        $san.AddDnsName('turn.example.test'); $request.CertificateExtensions.Add($san.Build())
        $certificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddMinutes(-5), [DateTimeOffset]::UtcNow.AddHours(1))
        try { [IO.File]::WriteAllText($certPath, $certificate.ExportCertificatePem()) }
        finally { $certificate.Dispose() }
        [IO.File]::WriteAllText($keyPath, $rsa.ExportPkcs8PrivateKeyPem())
    } finally { $rsa.Dispose() }
    $turnEnv = Join-Path $caseRoot '.env.capture.turn'
    $outputDir = Join-Path $caseRoot 'generated'
    $argsValid = @{ TurnDomain = 'turn.example.test'; CertificatePath = $certPath; PrivateKeyPath = $keyPath;
        CaptureEnvironmentPath = $baseEnv; EnvironmentPath = $turnEnv; OutputDirectory = $outputDir }
    $env:CAPTURE_LAN_IP = 'preserved-before-validation'
    & (Join-Path $PSScriptRoot 'Initialize-CaptureTurn.ps1') @argsValid | Out-Null
    if ($env:CAPTURE_LAN_IP -ne 'preserved-before-validation') { throw 'Inicializador TURN alterou ambiente T-03.' }
    $checks++
    . (Join-Path $PSScriptRoot 'Import-CaptureTurnEnvironment.ps1') -Path $turnEnv -CaptureEnvironmentPath $baseEnv
    $rendered = [IO.File]::ReadAllText($env:CAPTURE_TURN_CONFIG_PATH)
    if ($env:CAPTURE_TURN_TLS_PORT -ne '443' -or $env:CAPTURE_TURN_BIND_IP -ne '192.168.254.254' -or
        $rendered -match '__[A-Z_]+__' -or $rendered -notmatch 'auto_create: false' -or
        $rendered -notmatch 'max_participants: 10' -or $rendered -notmatch '192\.168\.254\.254/32' -or
        $rendered -notmatch 'udp_port: 0' -or $env:CAPTURE_LAN_IP -ne 'preserved-before-validation') { throw 'Configuracao TURN inconsistente.' }
    $checks++
    $original = [IO.File]::ReadAllText($turnEnv)
    Require-TurnFailure { & (Join-Path $PSScriptRoot 'Initialize-CaptureTurn.ps1') @argsValid }
    if ([IO.File]::ReadAllText($turnEnv) -cne $original) { throw 'Ambiente TURN existente alterado.' }
    $checks++
    foreach ($invalidDomain in @('https://turn.example.test', 'turn.example.test:443', '*.example.test', 'TURN.example.test', 'localhost', '192.168.1.50', 'turn.example.test;whoami')) {
        $invalidArgs = $argsValid.Clone(); $invalidArgs.TurnDomain = $invalidDomain
        Require-TurnFailure { & (Join-Path $PSScriptRoot 'Initialize-CaptureTurn.ps1') @invalidArgs }
        $checks++
    }
    $otherDomain = $argsValid.Clone(); $otherDomain.TurnDomain = 'other.example.test'
    Require-TurnFailure { & (Join-Path $PSScriptRoot 'Initialize-CaptureTurn.ps1') @otherDomain }
    $checks++
    $badPath = Join-Path $caseRoot 'bad.env'
    foreach ($badContent in @(
        ($original + "`nUNEXPECTED_VARIABLE=ignored`n"),
        $original.Replace('CAPTURE_TURN_TLS_PORT=443', 'CAPTURE_TURN_TLS_PORT=80'),
        $original.Replace('CAPTURE_TURN_TLS_PORT=443', 'CAPTURE_TURN_TLS_PORT=5349'),
        $original.Replace('CAPTURE_TURN_BIND_IP=192.168.254.254', 'CAPTURE_TURN_BIND_IP=8.8.8.8'),
        $original.Replace('CAPTURE_TURN_DOMAIN=turn.example.test', 'CAPTURE_TURN_DOMAIN=$(Write-Output forbidden)'),
        ($original + "`nCAPTURE_TURN_TLS_PORT=443`n")
    )) {
        [IO.File]::WriteAllText($badPath, $badContent)
        $env:CAPTURE_TURN_TLS_PORT = 'unchanged'
        Require-TurnFailure { . (Join-Path $PSScriptRoot 'Import-CaptureTurnEnvironment.ps1') -Path $badPath -CaptureEnvironmentPath $baseEnv }
        if ($env:CAPTURE_TURN_TLS_PORT -ne 'unchanged' -or $env:CAPTURE_LAN_IP -ne 'preserved-before-validation') { throw 'Importacao TURN parcial.' }
        $checks++
    }
    [IO.File]::WriteAllText($env:CAPTURE_TURN_CONFIG_PATH, $rendered.Replace('auto_create: false', 'auto_create: true'))
    Require-TurnFailure { . (Join-Path $PSScriptRoot 'Import-CaptureTurnEnvironment.ps1') -Path $turnEnv -CaptureEnvironmentPath $baseEnv }
    $checks++
    $baseline = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '..\infra\livekit\livekit.capture.template.yaml'))
    if ($baseline -notmatch 'enabled: false') { throw 'Cenario T-03 sem TURN foi alterado.' }
    $checks++
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        [IO.File]::WriteAllText($env:CAPTURE_TURN_CONFIG_PATH, $rendered)
        . (Join-Path $PSScriptRoot 'Import-CaptureEnvironment.ps1') -Path $baseEnv
        . (Join-Path $PSScriptRoot 'Import-CaptureTurnEnvironment.ps1') -Path $turnEnv -CaptureEnvironmentPath $baseEnv
        $composeArgs = @('compose', '--env-file', $baseEnv, '--env-file', $turnEnv,
            '-f', (Join-Path $rootPath 'infra/docker/compose.capture.yaml'),
            '-f', (Join-Path $rootPath 'infra/docker/compose.capture-turn.yaml'), 'config')
        & docker @composeArgs --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Compose TURN rejeitou a configuracao.' }
        # Inspecao estruturada em memoria; config inclui segredos, nunca imprimir.
        $model = (& docker @composeArgs --format json | ConvertFrom-Json)
        if ($LASTEXITCODE -ne 0) { throw 'Compose TURN nao gerou modelo.' }
        $service = $model.services.livekit
        $configVolumes = @($service.volumes | Where-Object target -eq '/etc/livekit/livekit.capture.yaml')
        $adminPorts = @($service.ports | Where-Object target -eq 7880)
        $turnPorts = @($service.ports | Where-Object { $_.target -eq 443 -and $_.protocol -eq 'tcp' })
        if ($configVolumes.Count -ne 1 -or -not $configVolumes[0].read_only -or
            $adminPorts.Count -ne 1 -or $adminPorts[0].host_ip -ne '127.0.0.1' -or
            $turnPorts.Count -ne 1 -or $turnPorts[0].host_ip -ne '192.168.254.254' -or
            $service.image -ne 'livekit/livekit-server:v1.13.10@sha256:89ca9ce0791e18d6e03bf6e2c49ceb27e1c754eae8bd50c989386c1a5963791a') {
            throw 'Merge Compose alterou isolamento, imagem ou listener restrito.'
        }
        $model = $null
        $checks++
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $resolvedCase = [IO.Path]::GetFullPath($caseRoot)
    $resolvedWork = [IO.Path]::GetFullPath($workRoot) + [IO.Path]::DirectorySeparatorChar
    if ($resolvedCase.StartsWith($resolvedWork, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedCase -Recurse -Force
    }
}
Write-Output "$checks verificacoes TURN aprovadas. Sem listeners, confianca TLS ou segredos exibidos."
