param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env.livekit')
)

$ErrorActionPreference = 'Stop'
$localPath = [IO.Path]::GetFullPath($Path)
if (Test-Path -LiteralPath $localPath) {
    throw 'O arquivo de credenciais LiveKit já existe. Ele foi preservado; importe-o com Import-LiveKitEnvironment.ps1.'
}

function New-LocalSecret([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) }
    finally { $generator.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

$apiKey = 'local' + (New-LocalSecret 12)
$apiSecret = New-LocalSecret 32
$content = @(
    '# Credenciais locais geradas; arquivo ignorado pelo Git. Não compartilhar.'
    'LIVEKIT_INTERNAL_URL=http://127.0.0.1:7880'
    'LIVEKIT_PUBLIC_URL=ws://127.0.0.1:7880'
    "LIVEKIT_API_KEY=$apiKey"
    "LIVEKIT_API_SECRET=$apiSecret"
) -join [Environment]::NewLine

# CreateNew evita sobrescrever credenciais mesmo em duas chamadas concorrentes.
$stream = [IO.File]::Open($localPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
$writer = New-Object IO.StreamWriter($stream, (New-Object Text.UTF8Encoding($false)))
try { $writer.WriteLine($content) }
finally { $writer.Dispose() }
Write-Output 'Credenciais LiveKit locais criadas em arquivo ignorado. Nenhum valor foi exibido.'
