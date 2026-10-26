param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw 'Arquivo .env ausente. Copie .env.example para .env e configure os valores locais.'
}

# Formato simples NAME=value, sem expansão ou execução de conteúdo do arquivo.
$allowedNames = @('POSTGRES_DB', 'POSTGRES_PORT', 'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD')
$values = @{}
foreach ($line in Get-Content -LiteralPath $Path) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
        throw 'Linha inválida no .env. Use NAME=value, sem aspas e sem comandos PowerShell.'
    }
    $name = $Matches[1]
    $value = $Matches[2]
    if ($name -notin $allowedNames) { throw "Variável não prevista no bootstrap: $name" }
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Valor ausente para $name" }
    if ($value -match '[\s\x22\x27\x23\x24]') {
        throw "Use valor simples sem espaços, aspas, # ou cifrão para $name, compatível com o Compose."
    }
    $values[$name] = $value
}
foreach ($name in $allowedNames) {
    if (-not $values.ContainsKey($name)) {
        throw "Variável obrigatória ausente: $name"
    }
}
foreach ($name in $allowedNames) {
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}
