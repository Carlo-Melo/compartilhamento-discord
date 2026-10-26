param(
    [Parameter(Mandatory = $true)][string]$LanIp,
    [string]$EnvironmentPath = (Join-Path $PSScriptRoot '..\.env.capture'),
    [string]$ConfigPath = (Join-Path $PSScriptRoot '..\infra\livekit\livekit.capture.generated.yaml')
)

$ErrorActionPreference = 'Stop'
$address = $null
if (-not [Net.IPAddress]::TryParse($LanIp, [ref]$address) -or
    $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork -or
    $address.ToString() -ne $LanIp) {
    throw 'Informe um IPv4 privado canonico da maquina que executara a SFU.'
}
$octets = $address.GetAddressBytes()
if (-not ($octets[0] -eq 10 -or
    ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
    ($octets[0] -eq 192 -and $octets[1] -eq 168))) {
    throw 'A prova T-03 aceita somente IPv4 privado RFC1918; loopback e IP publico nao sao aceitos.'
}
$envTarget = [IO.Path]::GetFullPath($EnvironmentPath)
$configTarget = [IO.Path]::GetFullPath($ConfigPath)
foreach ($target in @($envTarget, $configTarget)) {
    if ((Test-Path -LiteralPath $target) -or
        -not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($target)) -PathType Container)) {
        throw 'Destino ja existe ou pasta ausente. Nenhum arquivo existente sera sobrescrito.'
    }
}
if ($envTarget -eq $configTarget) { throw 'Os dois arquivos de saida precisam ser distintos.' }

function New-CaptureSecret([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) }
    finally { $generator.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}
function Write-CaptureNewFile([string]$Target, [string]$Content) {
    $stream = [IO.File]::Open($Target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $writer = New-Object IO.StreamWriter($stream, (New-Object Text.UTF8Encoding($false)))
    try { $writer.WriteLine($Content) }
    finally { $writer.Dispose() }
}

$content = @(
    '# T-03: arquivo privado ignorado; nao publicar, imprimir ou enviar ao chat.'
    "CAPTURE_LAN_IP=$LanIp"
    "CAPTURE_PROOF_ORIGIN=https://${LanIp}:4443"
    ('CAPTURE_PROOF_PRESENTER_KEY=' + (New-CaptureSecret 32))
    ('CAPTURE_PROOF_VIEWER_KEY=' + (New-CaptureSecret 32))
    'LIVEKIT_INTERNAL_URL=http://127.0.0.1:7890'
    "LIVEKIT_PUBLIC_URL=wss://${LanIp}:4443"
    ('LIVEKIT_API_KEY=capture' + (New-CaptureSecret 12))
    ('LIVEKIT_API_SECRET=' + (New-CaptureSecret 32))
) -join [Environment]::NewLine
$template = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\infra\livekit\livekit.capture.template.yaml') -Raw
$rendered = $template.Replace('__LAN_IP__', $LanIp)
# CreateNew preserva arquivos mesmo em chamadas concorrentes. Se a segunda gravacao
# falhar, o primeiro arquivo permanece privado para inspecao local, sem reexecucao cega.
Write-CaptureNewFile $envTarget $content
Write-CaptureNewFile $configTarget $rendered
Write-Output 'Ambiente privado e configuracao LAN T-03 criados. Nenhum segredo foi exibido; nenhum servico foi iniciado.'
