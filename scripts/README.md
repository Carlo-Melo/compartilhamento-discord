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
