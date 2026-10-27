param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env.capture.turn'),
    [string]$CaptureEnvironmentPath = (Join-Path $PSScriptRoot '..\.env.capture')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Capture-TurnSupport.ps1')
if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'Ambiente TURN ausente. Execute Initialize-CaptureTurn.ps1 primeiro.' }
$names = @('CAPTURE_TURN_BIND_IP', 'CAPTURE_TURN_DOMAIN', 'CAPTURE_TURN_TLS_PORT',
    'CAPTURE_TURN_CERT_PATH', 'CAPTURE_TURN_KEY_PATH', 'CAPTURE_TURN_CONFIG_PATH')
$values = @{}
foreach ($line in Get-Content -LiteralPath $Path) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.+)$') { throw 'Linha TURN invalida. Use somente NAME=value.' }
    $name = $Matches[1]; $value = $Matches[2]
    if ($name -notin $names -or $values.ContainsKey($name) -or $value -match '[\r\n\x22\x27\x23\x24]' -or $value -ne $value.Trim()) {
        throw 'Variavel TURN nao prevista, duplicada ou com valor invalido.'
    }
    $values[$name] = $value
}
foreach ($name in $names) { if (-not $values.ContainsKey($name)) { throw 'Variavel TURN obrigatoria ausente.' } }
$lanIp = Get-CaptureTurnBase $CaptureEnvironmentPath
if ($values['CAPTURE_TURN_BIND_IP'] -ne $lanIp -or $values['CAPTURE_TURN_TLS_PORT'] -ne '443') {
    throw 'Bind/porta TURN divergentes da prova privada.'
}
Assert-CaptureTurnDomain $values['CAPTURE_TURN_DOMAIN']
foreach ($name in @('CAPTURE_TURN_CERT_PATH', 'CAPTURE_TURN_KEY_PATH', 'CAPTURE_TURN_CONFIG_PATH')) {
    if (-not [IO.Path]::IsPathRooted($values[$name]) -or (Get-CaptureTurnPath $values[$name]) -cne $values[$name]) {
        throw 'Caminhos TURN devem ser absolutos e canonicos, conforme inicializador.'
    }
}
Assert-CaptureTurnCertificate $values['CAPTURE_TURN_CERT_PATH'] $values['CAPTURE_TURN_KEY_PATH'] $values['CAPTURE_TURN_DOMAIN']
if (-not (Test-Path -LiteralPath $values['CAPTURE_TURN_CONFIG_PATH'] -PathType Leaf)) { throw 'Configuracao TURN gerada ausente.' }
$expected = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '..\infra\livekit\livekit.capture-turn.template.yaml')).Replace('__LAN_IP__', $lanIp).Replace('__TURN_DOMAIN__', $values['CAPTURE_TURN_DOMAIN'])
if ([IO.File]::ReadAllText($values['CAPTURE_TURN_CONFIG_PATH']) -cne $expected) { throw 'Configuracao TURN divergente do template restrito. Regenere conscientemente.' }
# Nenhum valor e aplicado antes de validar integralmente os dois arquivos e TLS.
foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process') }
