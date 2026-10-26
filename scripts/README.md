# Scripts de desenvolvimento
`Import-DevEnvironment.ps1` importa as cinco variáveis da T-01 a partir do `.env` da raiz para o processo PowerShell atual, sem executar o conteúdo nem exibir os valores. Execute a partir da raiz:

```powershell
. .\scripts\Import-DevEnvironment.ps1
```

Aceita somente `POSTGRES_DB`, `POSTGRES_PORT`, `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`, no formato simples `NAME=value`. Valores devem ser não vazios, sem espaços, aspas, `#` ou `$`, para manter a mesma interpretação no Compose. O arquivo é validado antes de aplicar qualquer variável. Use uma senha local aleatória hexadecimal; não armazene credenciais no script.

Builds usam os comandos dos READMEs de backend/frontend. Migrations, backup/restore e operação de produção continuam reservados às próximas tarefas. A futura operação Linux terá comandos próprios, sem embutir credenciais.

T-02 acrescenta `Initialize-LiveKitLocal.ps1`, que gera credenciais criptograficamente aleatórias em `.env.livekit` sem sobrescrever arquivo existente nem imprimir valores, e `Import-LiveKitEnvironment.ps1`, que valida/importa as quatro variáveis locais LiveKit. Ambos são exclusivos de desenvolvimento; URLs em loopback fixo, sem auto-habilitar o auxiliar HTTP ou iniciar produção. Consulte [infra](../infra/README.md) e [backend](../backend/README.md).
