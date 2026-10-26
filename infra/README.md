# Infraestrutura local — PostgreSQL e LiveKit

A composição de desenvolvimento inicia somente PostgreSQL **18.6** (`postgres:18.6-bookworm`). Docker Desktop deve estar instalado e em execução com containers Linux; use Docker Compose v2 com suporte a `up --wait`. Execute os comandos abaixo no PowerShell, na raiz do projeto.

```powershell
Copy-Item .env.example .env
# Edite .env e substitua DATABASE_PASSWORD por uma senha local aleatória.
notepad .env
docker compose --env-file .env -f infra/docker/compose.yaml config --quiet
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait
docker compose --env-file .env -f infra/docker/compose.yaml ps
```

O projeto Compose se chama `compartilhamento-discord-dev`; o serviço é `postgres`. A porta padrão é **127.0.0.1:5432**, acessível apenas na própria máquina. O backend é executado no host e usa a URL JDBC informada em `.env`. O Compose lê `.env` com `--env-file`; o Spring Boot precisa receber as mesmas variáveis no ambiente do processo, conforme [backend/README.md](../backend/README.md).

| Variável | Uso |
|---|---|
| `POSTGRES_DB` | Nome do banco criado na primeira inicialização. Deve corresponder ao nome no final de `DATABASE_URL`. |
| `POSTGRES_PORT` | Porta local publicada no loopback; padrão `5432`. |
| `DATABASE_URL` | URL JDBC do backend; para esta composição, `jdbc:postgresql://127.0.0.1:5432/compartilhamento_discord`. |
| `DATABASE_USERNAME` | Usuário local criado pelo container e usado pelo backend. |
| `DATABASE_PASSWORD` | Senha local do banco; obrigatória, sem valor padrão no Compose. |

`.env.example` contém apenas valores fictícios; `.env` é ignorado pelo Git. Use valores sem espaços, aspas, `#` ou `$`, compatíveis com o importador PowerShell; uma senha local aleatória hexadecimal atende ao formato. Não publique o arquivo local nem a saída de `docker compose config` sem `--quiet`, pois ela inclui a senha interpolada. As variáveis de criação só são aplicadas quando o volume está vazio; editar `.env` não altera automaticamente credenciais de um banco existente.

Se outro PostgreSQL já ocupar a porta `5432`, preserve esse serviço e altere **juntos** `POSTGRES_PORT=5433` e `DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/compartilhamento_discord` no `.env`. Essa alternativa mantém o banco Docker no loopback. Na validação inicial deste projeto a porta `5432` já estava ocupada por PostgreSQL local; foi usada a porta `5433` somente no `.env` ignorado.

Para verificar a conexão e executar uma consulta real sem exibir a senha:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT 1 AS conectado;"'
```

Os dados ficam no volume Docker `compartilhamento-discord-dev_postgres-data`, montado em `/var/lib/postgresql`, conforme o [layout da imagem PostgreSQL 18](https://hub.docker.com/_/postgres). Não existe bind mount de dados para o OneDrive. A T-01 não cria tabelas nem scripts SQL de inicialização; o modelo bot–servidor será implementado na T-06.

Para encerrar sem apagar os dados:

```powershell
# Para os containers, mantendo-os para a próxima inicialização.
docker compose --env-file .env -f infra/docker/compose.yaml stop
# Alternativa: remove containers e rede, preservando o volume de dados.
docker compose --env-file .env -f infra/docker/compose.yaml down
```

Evite `down --volumes`: ele apaga o banco local. Não há política de reinício automático; inicie o banco explicitamente quando for desenvolver.

## LiveKit local — T-02

A SFU usa uma composição separada, [docker/compose.livekit.yaml](docker/compose.livekit.yaml), com projeto `compartilhamento-discord-livekit-dev` e serviço `livekit`. Iniciar ou encerrar essa composição não modifica o PostgreSQL nem seu volume. A imagem oficial está fixada em **LiveKit Server v1.13.10**, incluindo digest SHA256; não há `latest`. Docker Desktop precisa usar containers Linux.

Na raiz, gere uma única vez as credenciais locais. O script usa gerador criptográfico, não imprime valores e recusa sobrescrever um arquivo existente:

```powershell
.\scripts\Initialize-LiveKitLocal.ps1
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml config --quiet
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml pull
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml up -d --wait
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml ps
```

`.env.livekit.example` contém somente exemplos fictícios. `.env.livekit` é ignorado pelo Git e separado do `.env` PostgreSQL existente. O Compose entrega a chave/segredo ao servidor pela variável oficial `LIVEKIT_KEYS`. Não publique `docker compose config` sem `--quiet`, `docker inspect` completo ou o conteúdo dos arquivos locais: podem conter credenciais.

| Porta no Windows | Uso |
|---|---|
| `127.0.0.1:7880/tcp` | API administrativa autenticada e sinalização WebSocket; backend usa HTTP e navegador usa WebSocket. |
| `127.0.0.1:7881/tcp` | Transporte WebRTC ICE/TCP. |
| `127.0.0.1:7882/udp` | Transporte WebRTC ICE/UDP mux. |

Somente portas em loopback são publicadas no Windows; o processo escuta na rede interna do container para que o encaminhamento Docker funcione. A configuração [livekit/livekit.local.yaml](livekit/livekit.local.yaml) anuncia `127.0.0.1` como candidato ICE, desativa descoberta de IP público e habilita candidatos loopback. O escopo validado é de clientes **no mesmo computador**, com tracks sintéticas; duas máquinas e uso remoto ficam para as próximas etapas. Nenhuma regra de firewall foi criada ou alterada.

| Variável | Uso |
|---|---|
| `LIVEKIT_INTERNAL_URL` | `http://127.0.0.1:7880`; SDK JVM no backend do host. |
| `LIVEKIT_PUBLIC_URL` | `ws://127.0.0.1:7880`; conexão do SDK browser local. |
| `LIVEKIT_API_KEY` | Identificador aleatório da credencial do servidor, somente ambiente backend/Compose. |
| `LIVEKIT_API_SECRET` | Segredo aleatório de 32 bytes, somente ambiente backend/Compose. |

No terminal que iniciará o backend, importe o ambiente do banco e o ambiente LiveKit. O importador LiveKit apenas lê os quatro nomes acima, sem executar conteúdo; URLs precisam permanecer no loopback local:

```powershell
. .\scripts\Import-DevEnvironment.ps1
. .\scripts\Import-LiveKitEnvironment.ps1
# Ative o perfil/auxiliar de prova conforme backend/README.md.
```

A SFU usa `room.auto_create=false`, limite padrão de **10 participantes**, codecs VP8/Opus e TURN desativado. O backend precisa criar a sala explicitamente; um token de participante não tem permissão para criá-la. Nenhum serviço de egress, ingress, gravação, Redis, armazenamento de mídia ou volume persistente foi acrescentado. O bind mount é somente leitura para o YAML público de configuração, sem dados PostgreSQL no OneDrive.

Verifique processo e configuração sem exibir credenciais:

```powershell
Invoke-WebRequest http://127.0.0.1:7880/ | Select-Object StatusCode
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml exec -T livekit /livekit-server --version
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml exec -T livekit /livekit-server --config /etc/livekit/livekit.local.yaml ports
```

A saúde HTTP confirma que a SFU responde. Criação/consulta/exclusão, grants e mídia são verificados pela prova descrita no [backend](../backend/README.md), [frontend](../frontend/README.md) e [relatório T-02](../tests/manual/T-02-livekit.md). Build ou saúde isoladamente não comprovam essas permissões nem áudio do Windows.

Para encerrar somente a SFU local:

```powershell
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml stop
# Opcional: remove apenas o container/rede desta composição separada.
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml down
```

Não há reinício automático ou infraestrutura de produção. A prova elimina somente as próprias salas; a limpeza de namespace no startup/readiness de produção fica na T-19. TURN, TLS, proxy público e deploy continuam nas tarefas futuras. Não encaminhar áudio/vídeo WebRTC através do Spring Boot.

Referências oficiais consultadas em 26/10/2026: [release v1.13.10](https://github.com/livekit/livekit/releases/tag/v1.13.10), [opções da versão fixada](https://github.com/livekit/livekit/blob/v1.13.10/config-sample.yaml), [carregamento de `LIVEKIT_KEYS`](https://github.com/livekit/livekit/blob/v1.13.10/cmd/server/main.go), [portas e transportes](https://docs.livekit.io/transport/self-hosting/ports-firewall/). Operação futura em [specs/09-infra-e-operacao.md](../specs/09-infra-e-operacao.md).
