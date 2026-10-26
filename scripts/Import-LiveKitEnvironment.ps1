param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env.livekit')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw 'Arquivo .env.livekit ausente. Execute scripts/Initialize-LiveKitLocal.ps1 primeiro.'
}

# NAME=value é lido como dado: sem dot-source, expansão ou execução do conteúdo.
$allowedNames = @('LIVEKIT_INTERNAL_URL', 'LIVEKIT_PUBLIC_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET')
$values = @{}
foreach ($line in Get-Content -LiteralPath $Path) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
        throw 'Linha inválida em .env.livekit. Use NAME=value, sem comandos ou aspas.'
    }
    $name = $Matches[1]
    $value = $Matches[2]
    if ($name -notin $allowedNames) { throw "Variável não prevista no ambiente LiveKit local: $name" }
    if ($values.ContainsKey($name)) { throw "Variável duplicada no ambiente LiveKit local: $name" }
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match '[\s\x22\x27\x23\x24]') {
        throw "Use valor simples sem espaços, aspas, # ou cifrão para $name."
    }
    $values[$name] = $value
}
foreach ($name in $allowedNames) {
    if (-not $values.ContainsKey($name)) { throw "Variável obrigatória ausente: $name" }
}
if ($values['LIVEKIT_INTERNAL_URL'] -ne 'http://127.0.0.1:7880' -or
    $values['LIVEKIT_PUBLIC_URL'] -ne 'ws://127.0.0.1:7880') {
    throw 'A prova T-02 usa exclusivamente LiveKit no loopback, porta 7880.'
}
if ($values['LIVEKIT_API_KEY'] -notmatch '^[A-Za-z0-9_-]{16,128}$' -or
    $values['LIVEKIT_API_SECRET'] -notmatch '^[A-Za-z0-9_-]{32,256}$' -or
    $values['LIVEKIT_API_KEY'].StartsWith('substitua_') -or
    $values['LIVEKIT_API_SECRET'].StartsWith('substitua_')) {
    throw 'Credenciais LiveKit inválidas ou fictícias. Gere-as com Initialize-LiveKitLocal.ps1.'
}
foreach ($name in $allowedNames) {
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}
