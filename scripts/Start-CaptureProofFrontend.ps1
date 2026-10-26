param(
    [Parameter(Mandatory = $true)][string]$CertificatePath,
    [Parameter(Mandatory = $true)][string]$PrivateKeyPath,
    [string]$EnvironmentPath = (Join-Path $PSScriptRoot '..\.env.capture')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Import-CaptureEnvironment.ps1') -Path $EnvironmentPath
$certFile = [IO.Path]::GetFullPath($CertificatePath)
$keyFile = [IO.Path]::GetFullPath($PrivateKeyPath)
foreach ($file in @($certFile, $keyFile)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'Certificado ou chave TLS nao encontrado.' }
}
$nodeVersion = & node --version
if ($LASTEXITCODE -ne 0 -or $nodeVersion -notmatch '^v24\.' -or [version]$nodeVersion.TrimStart('v') -lt [version]'24.15.0') {
    throw 'Configure Node 24.15+ no PATH antes de iniciar o Angular 22.'
}
# DEBUG pode registrar a URL interna de sinalizacao que contem JWT. Nunca habilitar
# diagnostico verboso do proxy nesta prova; ambiente anterior e restaurado ao sair.
$priorDebug = [Environment]::GetEnvironmentVariable('DEBUG', 'Process')
$priorNodeDebug = [Environment]::GetEnvironmentVariable('NODE_DEBUG', 'Process')
$priorPort = [Environment]::GetEnvironmentVariable('PORT', 'Process')
try {
    [Environment]::SetEnvironmentVariable('DEBUG', $null, 'Process')
    [Environment]::SetEnvironmentVariable('NODE_DEBUG', $null, 'Process')
    [Environment]::SetEnvironmentVariable('PORT', '4443', 'Process')
    Push-Location (Join-Path $PSScriptRoot '..\frontend')
    try {
        & node node_modules/@angular/cli/bin/ng.js serve --configuration capture-proof --host $env:CAPTURE_LAN_IP --port 4443 --ssl --ssl-cert $certFile --ssl-key $keyFile --proxy-config proxy.capture.cjs --hmr=false --live-reload=false
        if ($LASTEXITCODE -ne 0) { throw 'O Angular terminou com erro. Confirme rede, TLS e dependencias conforme infra/README.md.' }
    } finally { Pop-Location }
} finally {
    if ($null -eq $priorDebug) { Remove-Item -LiteralPath Env:DEBUG -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable('DEBUG', $priorDebug, 'Process') }
    if ($null -eq $priorNodeDebug) { Remove-Item -LiteralPath Env:NODE_DEBUG -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable('NODE_DEBUG', $priorNodeDebug, 'Process') }
    if ($null -eq $priorPort) { Remove-Item -LiteralPath Env:PORT -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable('PORT', $priorPort, 'Process') }
}
