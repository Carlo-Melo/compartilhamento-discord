param([string]$Path = (Join-Path $PSScriptRoot '..\.env.capture'))

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw 'Arquivo .env.capture ausente. Execute Initialize-CaptureProof.ps1 apos escolher a rede privada.'
}
$allowedNames = @('CAPTURE_LAN_IP', 'CAPTURE_PROOF_ORIGIN', 'CAPTURE_PROOF_PRESENTER_KEY',
    'CAPTURE_PROOF_VIEWER_KEY', 'LIVEKIT_INTERNAL_URL', 'LIVEKIT_PUBLIC_URL',
    'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET')
$values = @{}
foreach ($line in Get-Content -LiteralPath $Path) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Linha invalida. Use somente NAME=value.' }
    $name = $Matches[1]
    $value = $Matches[2]
    if ($name -notin $allowedNames -or $values.ContainsKey($name)) { throw 'Variavel nao prevista ou duplicada.' }
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match '[\s\x22\x27\x23\x24]') {
        throw 'Valor invalido: espacos, aspas, # e cifrao nao sao permitidos.'
    }
    $values[$name] = $value
}
foreach ($name in $allowedNames) {
    if (-not $values.ContainsKey($name)) { throw "Variavel obrigatoria ausente: $name" }
}
$address = $null
$lan = $values['CAPTURE_LAN_IP']
if (-not [Net.IPAddress]::TryParse($lan, [ref]$address) -or
    $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork -or $address.ToString() -ne $lan) {
    throw 'IPv4 privado invalido.'
}
$octets = $address.GetAddressBytes()
if (-not ($octets[0] -eq 10 -or
    ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
    ($octets[0] -eq 192 -and $octets[1] -eq 168))) { throw 'A prova exige IPv4 privado RFC1918.' }
if ($values['CAPTURE_PROOF_ORIGIN'] -ne "https://${lan}:4443" -or
    $values['LIVEKIT_PUBLIC_URL'] -ne "wss://${lan}:4443" -or
    $values['LIVEKIT_INTERNAL_URL'] -ne 'http://127.0.0.1:7890') {
    throw 'URLs T-03 divergentes da origem HTTPS privada e API administrativa loopback esperadas.'
}
foreach ($name in @('CAPTURE_PROOF_PRESENTER_KEY', 'CAPTURE_PROOF_VIEWER_KEY', 'LIVEKIT_API_SECRET')) {
    if ($values[$name] -notmatch '^[a-f0-9]{64}$') { throw 'Use os segredos aleatorios gerados pelo inicializador.' }
}
if ($values['CAPTURE_PROOF_PRESENTER_KEY'] -eq $values['CAPTURE_PROOF_VIEWER_KEY'] -or
    $values['LIVEKIT_API_KEY'] -notmatch '^capture[a-f0-9]{24}$') {
    throw 'Chaves invalidas ou papeis usando a mesma credencial.'
}
# Validar integralmente antes de aplicar. Conteudo nunca e executado ou exibido.
foreach ($name in $allowedNames) { [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process') }
