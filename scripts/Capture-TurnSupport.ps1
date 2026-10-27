# Funcoes internas: nenhum listener, importacao global, DNS ou alteracao de confianca.
function Get-CaptureTurnBase([string]$Path) {
    $names = @('CAPTURE_LAN_IP', 'CAPTURE_PROOF_ORIGIN', 'CAPTURE_PROOF_PRESENTER_KEY',
        'CAPTURE_PROOF_VIEWER_KEY', 'LIVEKIT_INTERNAL_URL', 'LIVEKIT_PUBLIC_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET')
    $previous = @{}
    foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    try {
        . (Join-Path $PSScriptRoot 'Import-CaptureEnvironment.ps1') -Path $Path
        return $env:CAPTURE_LAN_IP
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
}
function Assert-CaptureTurnDomain([string]$Domain) {
    # Canonico, sem wildcard/URL/porta; DNS e rota sao decisoes do operador.
    if ($Domain.Length -gt 253 -or $Domain -cne $Domain.ToLowerInvariant() -or
        $Domain -notmatch '^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$') {
        throw 'Informe o dominio DNS TURN canonico, em minusculas, sem URL, porta ou wildcard.'
    }
}
function Get-CaptureTurnPath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    if ($full -match '[\r\n\x22\x27\x23\x24]') { throw 'Caminho local incompativel com arquivo de ambiente Compose.' }
    return $full.Replace('\', '/')
}
function Assert-CaptureTurnCertificate([string]$CertificatePath, [string]$PrivateKeyPath, [string]$Domain) {
    foreach ($path in @($CertificatePath, $PrivateKeyPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Certificado ou chave TURN local ausente.' }
    }
    $pem = [IO.File]::ReadAllText($CertificatePath)
    $match = [regex]::Match($pem, '-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END CERTIFICATE-----')
    if (-not $match.Success) { throw 'Certificado TURN deve estar em PEM.' }
    $certificate = $null
    try {
        $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList @(,[Convert]::FromBase64String($match.Groups[1].Value))
        if ($certificate.NotBefore.ToUniversalTime() -gt [DateTime]::UtcNow -or $certificate.NotAfter.ToUniversalTime() -le [DateTime]::UtcNow -or
            $certificate.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::DnsName, $false) -cne $Domain) {
            throw 'Certificado TURN expirado, ainda invalido ou nome DNS diferente do dominio escolhido.'
        }
    } catch { throw 'Certificado TURN invalido para o dominio e periodo escolhidos.' }
    finally { if ($null -ne $certificate) { $certificate.Dispose() } }
    if ([IO.File]::ReadAllText($PrivateKeyPath) -notmatch '-----BEGIN (?:RSA |EC )?PRIVATE KEY-----') {
        throw 'Chave TURN deve estar em PEM nao cifrado, protegido localmente pelo operador.'
    }
    # A SFU confirma correspondencia certificado/chave ao iniciar. Confianca e SAN
    # nas maquinas clientes precisam ser verificados pelo operador; nao instalar CA.
}
