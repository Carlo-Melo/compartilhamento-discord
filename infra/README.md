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

Os dados ficam no volume Docker `compartilhamento-discord-dev_postgres-data`, montado em `/var/lib/postgresql`, conforme o [layout da imagem PostgreSQL 18](https://hub.docker.com/_/postgres). Não existe bind mount de dados para o OneDrive. A aplicação usa Flyway na inicialização para criar somente as tabelas de bot e vínculo, além do histórico técnico de migração. Não há scripts de inicialização do Compose para dados de produto.

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

## T-03 — prova restrita em duas máquinas

Preparação implementada, **homologação pendente**. O usuário adiou as decisões externas; nenhum listener LAN, regra de firewall, confiança de certificado ou serviço externo foi criado nesta entrega. A [matriz manual T-03](../tests/manual/T-03-captura-real.md) registra o que ainda exige duas máquinas e consentimento humano.

A prova usa [compose.capture.yaml](docker/compose.capture.yaml), projeto separado `compartilhamento-discord-capture-proof`, e a mesma imagem/digest LiveKit da T-02. O IP ICE é configurado explicitamente; trocar `localhost` pela URL da máquina não configura mídia nem torna HTTP um contexto seguro. O backend e a API administrativa SFU continuam no loopback. O Angular de desenvolvimento termina HTTPS e encaminha somente a API auxiliar T-03 e os caminhos de sinalização LiveKit v0/v1; `/twirp/**`, a API T-02 e a gestão Spring não são encaminhadas. Esse servidor é uma ferramenta de teste numa LAN privada controlada, sem configuração de produção ou publicação na internet.

| Endereço na máquina que hospeda SFU/backend | Uso | Acesso necessário |
|---|---|---|
| `<IPv4-privado>:4443/tcp` | HTTPS Angular + WebSocket seguro | As duas máquinas do teste |
| `<IPv4-privado>:7891/tcp` | ICE/TCP | As duas máquinas do teste |
| `<IPv4-privado>:7892/udp` | ICE/UDP mux | As duas máquinas do teste |
| `127.0.0.1:7890/tcp` | Administração SFU + sinalização interna | Backend e proxy locais |
| `127.0.0.1:8080/tcp`, `8081/tcp` | API backend e gestão | Somente host; não abrir na LAN |

O navegador troca mídia por ICE diretamente nas portas 7891/7892; HTTPS/WSS na 4443 sozinho não prova conectividade WebRTC. Não há TURN nesta configuração. Duas redes sem rota privada exigem decisão e preparação adicionais; NAT, rede de convidados, isolamento entre clientes Wi-Fi, VPN e firewall podem impedir ICE. Não criar redirecionamento no roteador ou liberar firewall como correção automática. O fallback TURN e suas evidências permanecem na T-04. [Portas oficiais LiveKit](https://docs.livekit.io/transport/self-hosting/ports-firewall/).

Antes de executar, o operador precisa:

1. Escolher a máquina SFU e um IPv4 RFC1918 estável dessa interface (10/8, 172.16/12 ou 192.168/16), confirmar a segunda máquina Windows e a rota entre ambas. Esta implementação só aceita IP privado explícito; não escolhe fornecedor, domínio ou rede externa.
2. Providenciar certificado de servidor PEM e chave PEM locais, com SAN **IP** exatamente igual ao IPv4 escolhido, validade e uso de servidor corretos. A cadeia emissora precisa ser confiável no Windows/Chrome/Edge **das duas máquinas**. Uma autoridade local já administrada pode emitir esse certificado; a escolha e instalação da confiança ficam com o operador. Não usar aviso ignorado, flags de navegador inseguras, `--unsafely-treat-insecure-origin-as-secure`, ou HTTP no IP como evidência. Se o endereço mudar, emitir certificado correspondente e regenerar a configuração conscientemente. A chave privada TLS permanece apenas no host, fora do Git; não compartilhar a chave ou `.env.capture` com o espectador.
3. Decidir eventuais regras de firewall restritas às máquinas do teste e às três portas da tabela. Conferir Docker Desktop/Linux e o encaminhamento das portas no Windows. Nada disso é alterado pelos scripts.
4. Disponibilizar Chrome e Edge e reservar 15 minutos por combinação, som de teste sem conversa privada e uma pessoa para selecionar **tela inteira + compartilhar áudio do sistema**. Captura não pode ser autorizada pela automação.

Na raiz, depois dessas decisões (o IP abaixo é **exemplo**, substitua pela interface real):

```powershell
# Gera uma vez, sem substituir .env.livekit nem abrir qualquer porta:
.\scripts\Initialize-CaptureProof.ps1 -LanIp '192.168.1.50'
. .\scripts\Import-CaptureEnvironment.ps1
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml config --quiet
# Iniciar apenas quando rede/firewall e TLS estiverem preparados:
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml up -d --wait
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml ps

# Banco local existente; importa apenas variáveis necessárias:
. .\scripts\Import-DevEnvironment.ps1
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
java -jar .\backend\target-capture-proof\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=capture-proof --livekit.enabled=true --capture.proof.enabled=true
```

Em outro terminal, Node 24 no PATH e dependências `frontend/npm ci` já instaladas:

```powershell
.\scripts\Start-CaptureProofFrontend.ps1 -CertificatePath 'C:\certificados\capture-cert.pem' -PrivateKeyPath 'C:\certificados\capture-key.pem'
```

Abrir `https://<IPv4-privado>:4443/dev/capture/broadcast` no apresentador e `/dev/capture/watch` na outra máquina. Conferir que não há alerta TLS e que o apresentador informa contexto seguro. O operador consulta `.env.capture` localmente no seu editor e informa apenas a chave do respectivo papel no campo protegido da página. Entregar **somente** a chave de espectador à pessoa da segunda máquina por meio privado já disponível; nunca via URL, Git, log, screenshot ou conversa com o agente. As chaves experimentais não são tickets de produto; ficam em memória no navegador e autorizam somente esta prova. API key/secret SFU jamais vão para a página. Não ativar logs verbosos nem copiar as URLs internas de sinalização do SDK, que podem conter JWT.

O proxy [proxy.capture.cjs](../frontend/proxy.capture.cjs) preserva Host/Origin para validação no backend, rejeita destino/origem divergentes e limita caminhos a `/api/dev/capture/session`, `/api/dev/capture/session/viewer`, `/api/dev/capture/session/access`, `/rtc`, `/rtc/validate`, `/rtc/v1`, `/rtc/v1/validate`. Em HTTP/2 valida `:authority` e eventual Host antes de normalizar para o upstream HTTP/1, preservando método e caminho; ambos divergentes são negados. Isso evita 404 no navegador enquanto clientes HTTP/1 funcionavam. Trata erros antes do logger Vite para retirar a query da URL interna. Rotas administrativas não chegam à SFU. A configuração local da T-02 e o build normal de produção permanecem separados. O uso do proxy vem do [dev-server oficial Angular](https://angular.dev/tools/cli/serve#proxying-to-a-backend-server); o comportamento de configuração e erro foi conferido nas dependências fixadas do repositório.

Antes de encerrar, usar **Parar** no apresentador e conferir confirmação de exclusão. Se a página foi fechada ou a exclusão falhou, repetir a limpeza no host, sem exibir as chaves:

```powershell
. .\scripts\Import-CaptureEnvironment.ps1
$captureOrigin = [Uri]$env:CAPTURE_PROOF_ORIGIN
$captureHeaders = @{
    Host = $captureOrigin.Authority
    Origin = $env:CAPTURE_PROOF_ORIGIN
    'X-Capture-Proof' = 'restricted-development'
    'X-Capture-Key' = $env:CAPTURE_PROOF_PRESENTER_KEY
}
Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:8080/api/dev/capture/session' -Headers $captureHeaders
Remove-Variable captureHeaders
```

Repetir se a SFU não confirmar a exclusão; manter o backend ativo para essa tentativa. Em seguida `Ctrl+C` no frontend/backend e:

```powershell
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml down
```

Isso encerra só a SFU T-03, sem tocar na T-02 ou no volume PostgreSQL. Reinício abrupto do backend não implementa a limpeza de startup do produto (T-19); após falha, encerrar **esta** composição SFU antes de reiniciar a prova, eliminando suas salas voláteis. Não reutilizar acesso/sala da execução antiga. Para mudar IP ou chaves, encerrar a prova e preservar/remover conscientemente os dois arquivos gerados locais antes de executar o inicializador novamente; ele nunca os sobrescreve.

Verificações independentes executadas em 26/10/2026: parser Compose com `.env.capture.example` e `config --quiet` aprovado; 13 verificações PowerShell de setup/importação/restauração e 7 testes Node do proxy aprovados, incluindo Vite real em loopback com upstream falso. Nenhum desses resultados comprova TLS confiável, abertura das portas, RTP entre máquinas ou áudio real do Windows.

## T-04 — TURN TLS opt-in

A prova T-04 amplia a ferramenta experimental T-03 e prepara TURN **embedded** da mesma SFU **v1.13.10**, sem novo serviço/dependência. O cenário T-03 sem TURN permanece em `compose.capture.yaml` e `livekit.capture.template.yaml`. O [overlay](docker/compose.capture-turn.yaml) troca somente o YAML montado da SFU e acrescenta os listeners TURN; imagem/digest, limite de dez participantes, `auto_create=false`, origem privada HTTPS e administração `127.0.0.1:7890` são preservados. Não deve ser iniciado nesta preparação sem as decisões de rede/TLS do operador.

O SDK browser **2.22.3** usa os ICE servers retornados pela SFU somente se `rtcConfig.iceServers` não estiver definido. Um array vazio é verdadeiro nessa condição JavaScript e suprime esses servidores. A conexão experimental remove esse override e permite somente política `all` ou `relay`; visitantes não fornecem URLs nem credenciais ICE. No modo relay, `iceTransportPolicy=relay` exige alocação TURN para conectar. O modo direto usa o cenário sem TURN. Nenhum dos modos, isoladamente, comprova recepção de mídia.

| Porta/endereço na máquina SFU | Uso |
|---|---|
| `<IPv4-privado>:4443/tcp` | HTTPS/WSS Angular, igual à T-03 |
| `<IPv4-privado>:7891/tcp`, `7892/udp` | ICE direto, igual à T-03 |
| `<IPv4-privado>:443/tcp` | TURN/TLS, com certificado DNS e TLS terminado na SFU; porta anunciada pelo TURN embedded na versão fixada |
| `<IPv4-privado>:30000–30099/udp` | Intervalo limitado de alocações relay, com mapeamento idêntico no Docker; fluxo SFU/relay, sem exigir UDP do navegador no ensaio TLS |
| `127.0.0.1:7890/tcp` | Administração e sinalização interna; não encaminhar para outra rede |

O TURN/UDP está desativado no overlay para permitir um ensaio inequívoco do caminho TLS quando o UDP do cliente está bloqueado. O servidor gera credenciais temporárias TURN na sinalização autenticada; nenhuma credencial estática é fornecida ao visitante ou colocada no template. TTL de alocação inicial 300s, quota quatro alocações por credencial e intervalo de cem portas limitam o auxiliar. Isso não substitui capacidade medida. A política de peers privados da versão fixada nega RFC1918 por padrão: o template permite somente o IPv4 da própria SFU `/32`; a política embedded também restringe o destino às portas de mídia da SFU. Não usar uma liberação geral de rede privada.

O código `iceServersForParticipant` da **v1.13.10** anuncia `turns:<domain>:443?transport=tcp` para TURN embedded, mesmo que `turn.tls_port` tenha outro valor. Por isso este auxiliar exige listener e publicação **443:443**. A porta 5349 aparece como padrão interno na documentação geral, mas não funciona como alternativa externa direta neste cenário; se 443 não estiver disponível, registrar a restrição e decidir uma topologia compatível antes de executar. HTTPS Angular permanece na4443 e não colide.

Entradas necessárias antes de executar:

1. Ambiente T-03 já preparado, máquina SFU e IPv4 RFC1918 estável, Docker Linux funcional e clientes Windows disponíveis. A preparação continua restrita a LAN/rota privada; para redes distintas, o operador precisa definir uma rota privada/VPN existente que alcance esse endereço. Acesso público, NAT/roteador, fornecedor e domínio público ainda exigem decisão e autorização próprias.
2. Nome DNS dedicado TURN, resolvendo para esse IP em todos os clientes, e certificado PEM com SAN DNS correspondente, cadeia completa e chave PEM local. A cadeia precisa ser aceita pelo navegador também na conexão TURN; certificado da página com SAN **IP** continua separado. O script confere formato/nome/período do certificado; confiança da cadeia, SAN e correspondência com a chave precisam ser confirmadas no ambiente real. Nenhuma CA, certificado confiável, DNS ou arquivo hosts é instalado automaticamente. Não ignorar erros TLS.
3. Decisão do operador sobre regras de rede necessárias e limitadas aos clientes do ensaio. Para o teste com UDP bloqueado, preservar HTTPS/WSS e TURN/TLS TCP443, bloquear UDP **na saída do cliente testado** e confirmar por diagnóstico que o relay foi selecionado. O proxy Angular não encaminha TURN; é um listener TLS próprio. Não usar o proxy HTTP para TCP TURN.

Preparar uma única vez, usando nome e caminhos fictícios substituídos localmente:

```powershell
.\scripts\Initialize-CaptureTurn.ps1 -TurnDomain 'turn.example.test' -CertificatePath 'C:\certificados\turn-cert.pem' -PrivateKeyPath 'C:\certificados\turn-key.pem'
. .\scripts\Import-CaptureEnvironment.ps1
. .\scripts\Import-CaptureTurnEnvironment.ps1
# Encerre a prova/sala e sua composicao antes de mudar entre sem TURN e com TURN.
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml down
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml config --quiet
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml up -d --wait
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml ps
```

Não publicar `docker compose config` sem `--quiet`: a interpolação inclui segredos SFU. Backend e frontend iniciam pelos mesmos comandos T-03; nenhuma mudança em Host/Origin, chaves por papel, CSRF ou loopback administrativo é necessária. Parâmetros TURN vêm exclusivamente da preparação do operador e da SFU. Se o Docker Desktop/NAT não encaminhar os relays corretamente, registrar a falha antes de decidir uma topologia diferente; não ampliar exposição ou alterar regras automaticamente.

O ensaio deve separar três resultados:

1. **Direto:** usar somente `compose.capture.yaml`, conexão normal e par selecionado sem candidato `relay`; conferir RTP de vídeo/áudio recebido, quadros decodificados, reprodução e áudio audível.
2. **Relay exigido:** usar overlay TLS e política relay na conexão experimental. Conferir no relatório de transporte o **par efetivamente selecionado**, com candidato local `relay`. `protocol=udp` descreve o transporte do candidato relayed e pode aparecer mesmo sobre TURN/TLS; `relayProtocol=tls` identifica o transporte navegador→TURN. Se o navegador omitir `relayProtocol`, registrar a limitação e obter evidência equivalente da configuração efetiva/ensaio UDP bloqueado, sem inferir TLS só de `protocol`. Contadores precisam crescer após o par estar selecionado.
3. **Redes distintas/UDP bloqueado:** registrar redes/rota de modo genérico, versões e qual cliente teve UDP bloqueado; repetir com relay exigido, áudio/vídeo recebidos e transporte TLS comprovado. TCP direto ou sinalização bem-sucedida não aprovam TURN. Não exportar SDP, endereços, URLs com JWT, senhas ICE ou `webrtc-internals` bruto. [Roteiro e resultados T-04](../tests/manual/T-04-multiespectador-turn.md).

Limpeza: usar **Sair** nos espectadores e **Parar** no apresentador, confirmar exclusão da sala ou repetir o DELETE administrativo T-03. Só depois parar frontend/backend e remover exclusivamente a composição experimental:

```powershell
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml down
# Opcional, depois de conferir os arquivos: remove somente a configuracao gerada.
Remove-Item -LiteralPath '.env.capture.turn', 'work/capture-turn/livekit.capture-turn.generated.yaml'
Remove-Item -LiteralPath 'work/capture-turn'
```

Não usar `--volumes`, excluir `.env.capture` ou apagar certificados/chaves compartilhados com outros testes. Restaurar somente as regras de rede criadas conscientemente pelo operador. Para retornar ao cenário direto, subir somente o Compose T-03. Reinício da SFU experimental elimina suas salas; limpeza automática do produto antes de readiness continua na T-19.

Verificação local de **27/10/2026**: **23 checks TURN** aprovados (incluindo parse/merge Compose real e preservação de imagem/digest, mount único read-only e administração loopback) e **13 checks T-03** aprovados. Certificado efêmero utilizado só em arquivo temporário, sem instalação de confiança. Nenhum listener LAN/TURN foi iniciado por esses testes. Alocação TURN, TLS aceito pelo navegador e par ICE relay real continuam pendentes de homologação; o sucesso dos scripts não os comprova.

Fontes oficiais compatíveis consultadas em 27/10/2026: [configuração v1.13.10](https://github.com/livekit/livekit/blob/v1.13.10/config-sample.yaml), [TURN embedded e autenticação v1.13.10](https://github.com/livekit/livekit/blob/v1.13.10/pkg/service/turn.go), [anúncio de ICE/TURN v1.13.10](https://github.com/livekit/livekit/blob/v1.13.10/pkg/service/roommanager.go), [ICE do SDK 2.22.3](https://github.com/livekit/client-sdk-js/blob/v2.22.3/src/room/RTCEngine.ts), [portas e firewall](https://docs.livekit.io/transport/self-hosting/ports-firewall/). Os fontes fixados são a referência para opções; a documentação corrente de portas pode evoluir.
