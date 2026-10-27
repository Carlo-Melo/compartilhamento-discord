# Scripts de desenvolvimento
`Import-DevEnvironment.ps1` importa as cinco variáveis da T-01 a partir do `.env` da raiz para o processo PowerShell atual, sem executar o conteúdo nem exibir os valores. Execute a partir da raiz:

```powershell
. .\scripts\Import-DevEnvironment.ps1
```

Aceita somente `POSTGRES_DB`, `POSTGRES_PORT`, `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`, no formato simples `NAME=value`. Valores devem ser não vazios, sem espaços, aspas, `#` ou `$`, para manter a mesma interpretação no Compose. O arquivo é validado antes de aplicar qualquer variável. Use uma senha local aleatória hexadecimal; não armazene credenciais no script.

Builds usam os comandos dos READMEs de backend/frontend. Migrations, backup/restore e operação de produção continuam reservados às próximas tarefas. A futura operação Linux terá comandos próprios, sem embutir credenciais.

T-02 acrescenta `Initialize-LiveKitLocal.ps1`, que gera credenciais criptograficamente aleatórias em `.env.livekit` sem sobrescrever arquivo existente nem imprimir valores, e `Import-LiveKitEnvironment.ps1`, que valida/importa as quatro variáveis locais LiveKit. Ambos são exclusivos de desenvolvimento; URLs em loopback fixo, sem auto-habilitar o auxiliar HTTP ou iniciar produção. Consulte [infra](../infra/README.md) e [backend](../backend/README.md).

## T-03 — preparação restrita para duas máquinas

- `Initialize-CaptureProof.ps1 -LanIp <IPv4-privado>` gera `.env.capture` com chaves criptográficas distintas por papel/SFU e `infra/livekit/livekit.capture.generated.yaml` com o IP anunciado no ICE. Recusa IP público/loopback e arquivos existentes. Não inicia serviços nem altera rede.
- `Import-CaptureEnvironment.ps1` valida o arquivo inteiro e só depois importa os oito nomes previstos. Nunca executa seu conteúdo. A T-02 continua usando seu importador e suas portas próprios.
- `Start-CaptureProofFrontend.ps1 -CertificatePath <PEM> -PrivateKeyPath <KEY>` exige Node 24.15+, certificado/chave existentes e inicia apenas a configuração Angular `capture-proof`, no IPv4 privado:4443 com HTTPS. A confiança do certificado nas duas máquinas é responsabilidade do operador; o script não instala certificados nem altera firewall. `DEBUG`/`NODE_DEBUG` são removidos durante o processo para impedir diagnóstico com URL interna de sinalização; seus valores anteriores são restaurados ao sair.
- `Test-CaptureProofSetup.ps1` verifica sintaxe, geração, não sobrescrita, rejeição de dados inválidos e importação atômica, sem abrir listeners. Usa e remove uma pasta própria em `work/`. Execute também `node --test frontend/proxy.capture.test.cjs` para checar os limites de encaminhamento e a sanitização de erro do proxy.

Os passos de TLS, portas, início e encerramento estão em [infra](../infra/README.md#t-03--prova-restrita-em-duas-máquinas). Nenhum script escolhe uma rede, instala uma autoridade certificadora ou autoriza captura em nome do usuário.

## T-04 — TURN TLS opt-in

O cenário sem TURN continua usando os mesmos scripts/arquivos T-03. O overlay TURN é preparado separadamente e usa a mesma imagem/digest SFU:

- `Initialize-CaptureTurn.ps1 -TurnDomain <DNS> -CertificatePath <PEM> -PrivateKeyPath <KEY>` valida primeiro o ambiente T-03, o nome DNS canônico e certificado PEM no período de validade e com o nome escolhido. Requer chave PEM local não cifrada; a SFU verifica a correspondência criptográfica ao iniciar. TURN/TLS usa TCP443: o servidor fixado anuncia essa porta para TURN embedded. Gera `.env.capture.turn` e `work/capture-turn/livekit.capture-turn.generated.yaml`, ambos ignorados. Recusa saída existente e restaura o ambiente anterior após validar T-03. Não cria certificado, entrada DNS, listener ou regra de rede.
- `Import-CaptureTurnEnvironment.ps1` valida integralmente os arquivos e o template restrito antes de aplicar somente as seis variáveis TURN. Rejeita porta/bind inesperados, caminhos relativos, nomes extras/duplicados e configuração alterada. Não executa conteúdo nem imprime segredos. Importar o ambiente T-03 continua sendo um passo separado.
- `Test-CaptureTurnSetup.ps1` executa testes locais de sintaxe, geração, não sobrescrita, domínio/certificado, validação atômica, preservação da T-03 sem TURN e merge Compose (se o CLI Docker existir). O certificado efêmero de teste fica apenas em arquivos temporários removidos, sem instalar confiança. A fixture de certificado usa PowerShell 7/.NET existente no workspace. Não depende do daemon Docker e não comprova alocação TURN, RTP ou confiança TLS no navegador.

```powershell
.\scripts\Test-CaptureProofSetup.ps1
.\scripts\Test-CaptureTurnSetup.ps1
```

Preparação de domínio/TLS, comandos com os dois arquivos de ambiente, portas, teste direto/relay e limpeza estão em [infra/T-04](../infra/README.md#t-04--turn-tls-opt-in). A prova entre redes distintas depende de uma rota privada escolhida pelo operador; esta preparação não publica a administração nem amplia a origem HTTPS permitida.
