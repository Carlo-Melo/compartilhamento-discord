param(
    [Parameter(Mandatory = $true)][string]$TurnDomain,
    [Parameter(Mandatory = $true)][string]$CertificatePath,
    [Parameter(Mandatory = $true)][string]$PrivateKeyPath,
    [string]$CaptureEnvironmentPath = (Join-Path $PSScriptRoot '..\.env.capture'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\work\capture-turn'),
    [string]$EnvironmentPath = (Join-Path $PSScriptRoot '..\.env.capture.turn')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Capture-TurnSupport.ps1')
Assert-CaptureTurnDomain $TurnDomain
$lanIp = Get-CaptureTurnBase $CaptureEnvironmentPath
$certFile = Get-CaptureTurnPath $CertificatePath
$keyFile = Get-CaptureTurnPath $PrivateKeyPath
Assert-CaptureTurnCertificate $certFile $keyFile $TurnDomain
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
$envTarget = [IO.Path]::GetFullPath($EnvironmentPath)
if ((Test-Path -LiteralPath $outputPath) -or (Test-Path -LiteralPath $envTarget) -or
    -not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($envTarget)) -PathType Container)) {
    throw 'Destino TURN existente ou pasta do ambiente ausente. Nenhum arquivo sera sobrescrito.'
}
$configPath = Get-CaptureTurnPath (Join-Path $outputPath 'livekit.capture-turn.generated.yaml')
$template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '..\infra\livekit\livekit.capture-turn.template.yaml'))
$rendered = $template.Replace('__LAN_IP__', $lanIp).Replace('__TURN_DOMAIN__', $TurnDomain)
$content = @(
    '# T-04 opt-in local. Nao publicar este arquivo nem os certificados/chaves.'
    "CAPTURE_TURN_BIND_IP=$lanIp"
    "CAPTURE_TURN_DOMAIN=$TurnDomain"
    'CAPTURE_TURN_TLS_PORT=443'
    "CAPTURE_TURN_CERT_PATH=$certFile"
    "CAPTURE_TURN_KEY_PATH=$keyFile"
    "CAPTURE_TURN_CONFIG_PATH=$configPath"
) -join [Environment]::NewLine
# CreateNew preserva o ambiente existente inclusive em concorrencia. Falha parcial
# deixa arquivos locais para inspecao; nunca removemos automaticamente segredo.
$stream = [IO.File]::Open($envTarget, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    [IO.Directory]::CreateDirectory($outputPath) | Out-Null
    $configStream = [IO.File]::Open($configPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $configWriter = New-Object IO.StreamWriter($configStream, (New-Object Text.UTF8Encoding($false)))
    try { $configWriter.Write($rendered) } finally { $configWriter.Dispose() }
    $writer = New-Object IO.StreamWriter($stream, (New-Object Text.UTF8Encoding($false)))
    try { $writer.WriteLine($content) } finally { $writer.Dispose(); $stream = $null }
} finally { if ($null -ne $stream) { $stream.Dispose() } }
Write-Output 'Preparacao TURN TLS opt-in criada. Nenhum servico, DNS, firewall ou confianca TLS foi alterado.'
