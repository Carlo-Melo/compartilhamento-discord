# T-04 — nove espectadores, TURN e isolamento de tokens

Data: **27/10/2026**. **Implementação entregue; validação parcial; G1/G2 não homologados.** O usuário decidiu nesta execução: **deixar a homologação para depois e entregar implementação e roteiro**. Os relatos anteriores de compartilhamento/player não informam a matriz completa, duração ou áudio de sistema audível; não são apresentados como evidência desta execução. O ensaio de duas horas continua na **T-23**.

## Implementação e decisões

Foi evoluído o auxiliar `capture-proof` da T-03. Reutilizar captura, player, chaves por papel, proxy HTTPS e source sets evita duplicar barreiras de segurança e ciclo de tracks. As páginas e os controllers continuam fora dos artefatos padrão. API definitiva, `ProductAccessPolicy`, CSRF do produto, PostgreSQL, bot e tickets não recebem autorização provisória.

- Uma geração ativa, **um apresentador + nove identidades distintas de espectador**, limite SFU dez. A identidade de mídia é aleatória e emitida pelo backend, sem sala/papel/identidade escolhidos pelo navegador.
- `X-Capture-Viewer` é um UUID v4 de correlação da página, mantido somente em memória. Não é pessoa, conta Discord, ticket ou contexto definitivo. Repetir a correlação após timeout, falha ou saída obtém a mesma identidade e não gasta outra vaga. Uma conexão com a mesma identidade substitui a anterior na SFU. Navegar/recarregar/fechar a página perde a correlação e pode gastar outra vaga; manter a mesma página durante o ensaio.
- A décima correlação recebe **409 `CAPTURE_PROOF_CAPACITY`**. Reserva da última vaga é atômica; chamadas de rede não mantêm o lock global. Token/resposta incertos preservam a identidade para repetição.
- `DELETE /api/dev/capture/session/viewer`, com chave VIEWER e mesma correlação, remove somente aquele participante. É idempotente para correlação ausente. **Não revoga JWT original/renovado nem libera a vaga para outro visitante.** A mesma correlação pode retornar. Um novo grupo exige encerrar a geração e criar outra sala.
- Estado limitado a nove correlações, uma sala ativa, **32 gerações por processo e prazo absoluto de uma hora por geração**. Expiração nega emissão e a cada 30s tenta excluir/confirmar ausência na SFU. Falha retém o nome fechado para repetição. Reiniciar exige encerrar a SFU experimental antiga; limpeza de startup/readiness do produto permanece T-19.
- Estado separa `viewerCount` (identidades próprias presentes na SFU, deduplicadas, sem apresentador) de `viewerSlotsUsed` (vagas emitidas) e `maxViewers=9`. Não inferir recepção RTP desses números.
- TURN/TLS integrado da **mesma imagem/digest** em overlay opt-in. Baseline sem TURN preservada. Servidores ICE vêm da SFU configurada, sem entrada arbitrária do visitante. O override `iceServers: []` foi retirado; a política `relay` preserva os servidores da SFU e força relay para o ensaio.
- Diagnóstico copia somente tipos/protocolos do par ICE **selecionado**, indicação relay e contadores de mídia. Não exporta `RTCStatsReport`, candidatos/IPs/portas, URLs ICE, SDP, JWT ou credenciais.

Contrato auxiliar: [OpenAPI](../../specs/api/capture-proof.openapi.json). Cenários documentais: [multiespectador-turn.feature](../../specs/tests/multiespectador-turn.feature). O comportamento de vagas desta prova não implementa o `/join` definitivo da T-14.

## Ambiente e fontes compatíveis

Windows/PowerShell do workspace, JDK Temurin **21.0.12.1+1**, Maven Wrapper **3.3.4** / Maven **3.9.16**, Spring Boot **4.1.2**. Node **24.22.1**, npm **11.19.1**, Angular **22.2.3**/CLI **22.2.4**, SDK browser **2.22.3**, SDK JVM **0.16.0**, SFU **1.13.10**. Nenhuma versão/dependência foi trocada. Runtimes portáteis já existentes em `work/tools`; nenhuma instalação global alterada.

Fontes oficiais consultadas nesta execução:

- [SDK JS 2.22.3 — RTCEngine](https://github.com/livekit/client-sdk-js/blob/v2.22.3/src/room/RTCEngine.ts): só copia `iceServers` da resposta SFU se o campo local estiver ausente; array vazio é truthy e bloqueava essa cópia.
- [LiveKit 1.13.10 — config-sample](https://github.com/livekit/livekit/blob/v1.13.10/config-sample.yaml) e [TURN integrado](https://github.com/livekit/livekit/blob/v1.13.10/pkg/service/turn.go): configuração TLS, faixa de relay, limites e restrição de peers privados.
- [RoomManager 1.13.10](https://github.com/livekit/livekit/blob/v1.13.10/pkg/service/roommanager.go): anúncio TURN integrado usa **443 externa**; por isso o auxiliar rejeita 5349 como alternativa externa. Primeiro refresh de token é enviado ao iniciar o worker, depois a cada cinco minutos; validade renovada de pelo menos dez minutos. Também pode fornecer STUN padrão quando não há STUN efetivo. Não afirmar ausência de STUN público apenas por `stun_servers: []`.

## Resultados desta execução

As verificações efetivamente executadas e os resultados finais estão na tabela de regressões ao fim deste documento. Não houve conexão SFU real, recepção sintética nem captura de tela nesta execução. O pipe `dockerDesktopLinuxEngine` não existia; iniciar o Docker Desktop já instalado em segundo plano não disponibilizou o daemon, confirmado também fora do sandbox. O aplicativo pode permanecer aberto após essa tentativa; nenhum processo Docker foi encerrado por nome, preservando serviços preexistentes. Não foi alterada configuração Docker, firewall, roteador, DNS ou confiança TLS.

Os testes Java opt-in contra SFU foram preparados para exigir também **refresh_token recebido da própria SFU**, assinatura/escopo válidos e reuso antes da expiração após excluir A, mantendo outra sala B já existente com nome diferente. Receber outro JWT emitido pelo gateway não conta como renovação. Esses testes validam administração/sinalização/grants, não RTP nem uma transmissão iniciada depois da exclusão. Sem daemon/flag permanecem explicitamente pulados; **não aprovar o critério do token renovado**.

| Tipo de evidência | Estado nesta execução |
|---|---|
| Doubles backend/frontend de identidades, concorrência, falhas e limpeza | Ver regressões abaixo; não comprovam rede ou mídia |
| HTTP Spring real em loopback, segurança e empacotamento | Ver regressões abaixo; não comprovam SFU |
| Geração/importação TURN e merge Compose | Ver regressões abaixo; parser não comprova alocação relay |
| Integração SFU real, inclusive token renovado recebido por sinalização | **Não executada**, daemon indisponível |
| Nove receptores com RTP de vídeo/áudio sintéticos | **Não executado**; harness opt-in entregue |
| Par ICE relay selecionado, TLS/TCP efetivo, redes distintas/UDP bloqueado | **Não executado** |
| Captura real Windows, som de sistema audível, Chrome/Edge, duração por combinação | **Não executado**; homologação humana adiada |

## Preparar e executar a prova depois

Seguir primeiro [infra T-03/T-04](../../infra/README.md), [backend](../../backend/README.md) e [frontend](../../frontend/README.md). Não imprimir `.env`, tokens, headers, SDP, URLs internas de sinalização ou saída de `docker compose config` sem `--quiet`.

1. Operador disponibiliza computadores Windows/clientes e identifica IP SFU privado estável, rota privada entre redes, versões Windows/Chrome/Edge e participantes. Uma aba nova tem correlação distinta, mas nove abas no mesmo computador não comprovam capacidade entre máquinas/redes.
2. Providenciar HTTPS da origem `https://<IPv4-privado>:4443`, SAN IP correspondente e cadeia confiável nos clientes. Para TURN, domínio que resolva à interface privada SFU e certificado PEM válido para esse **domínio**, com chave PEM apenas no host. Confiança/DNS/firewall/rota pertencem ao operador. Esta prova não prepara um endpoint público.
3. Separar portas: **4443/TCP** HTTPS/WSS, **7891/TCP e 7892/UDP** mídia direta, **443/TCP** TURN/TLS e **30000–30099/UDP** relays no host SFU. **7890/TCP**, backend **8080** e gestão **8081** permanecem em loopback. Bloqueio UDP do ensaio aplica-se aos clientes, mantendo a comunicação interna SFU/relay; TURN/TLS não elimina o transporte UDP do lado servidor.
4. Usar tela dinâmica e som de teste, com fones e sem conversa privada; somente o apresentador escolhe monitor e autoriza áudio no seletor nativo. Prévia permanece silenciada.

Na raiz, com Java 21/Node 24 no PATH e dependências já presentes:

```powershell
# Primeira preparação, apenas após escolher IP/rede/certificados; exemplo fictício:
.\scripts\Initialize-CaptureProof.ps1 -LanIp '192.168.1.50'
. .\scripts\Import-CaptureEnvironment.ps1
. .\scripts\Import-DevEnvironment.ps1
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml config --quiet
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml up -d --wait
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
java -jar .\backend\target-capture-proof\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=capture-proof --livekit.enabled=true --capture.proof.enabled=true
```

Em outro terminal:

```powershell
.\scripts\Start-CaptureProofFrontend.ps1 -CertificatePath 'C:\certificados\capture-cert.pem' -PrivateKeyPath 'C:\certificados\capture-key.pem'
```

Chaves experimentais somente no campo protegido do respectivo papel. Abrir `/dev/capture/broadcast`, validar chave e clicar para autorizar captura com vídeo/áudio. Abrir **nove páginas de espectador** em `/dev/capture/watch`, cada uma validando chave VIEWER e entrando. Registrar nove identidades distintas por comparação somente em memória; persistir apenas contagem/booleano da comparação, sem nomes/credenciais. Conferir `viewerCount=9` e `viewerSlotsUsed=9`, vídeo em movimento e som audível em **cada** receptor. Tentar décimo espectador e registrar409. Sair/repetir em uma das nove páginas sem recarregá-la: mesma identidade/vaga; a contagem SFU cai/sobe. Nova correlação continua recusada até outra geração.

### Direto, relay obrigatório e UDP bloqueado

**Direto:** baseline sem TURN, política ICE padrão. Copiar diagnóstico de envio/recepção e registrar tipos do par selecionado e protocolo; par indisponível não é prova. Registrar mídia recebida, perdas/falhas e contadores crescentes. Essa configuração ainda pode receber STUN padrão da SFU.

**Relay:** parar transmissão, confirmar exclusão e encerrar a composição SFU. Preparar overlay sem sobrescrever os arquivos anteriores:

```powershell
.\scripts\Initialize-CaptureTurn.ps1 -TurnDomain 'turn.exemplo.test' -CertificatePath 'C:\certificados\turn-cert.pem' -PrivateKeyPath 'C:\certificados\turn-key.pem'
. .\scripts\Import-CaptureTurnEnvironment.ps1
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml config --quiet
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml up -d --wait
```

Selecionar política **relay** antes de conectar cada cliente. Exigir `relay=sim` no par selecionado e registrar `relayProtocol` cliente–TURN como TLS/TCP conforme navegador. `localProtocol=udp` pode descrever candidato relayed no lado servidor; sozinho não prova UDP entre cliente e TURN. Se o navegador não expuser `relayProtocol`, registrar lacuna de transporte e obter observação complementar minimizada do operador; não inferir TLS apenas pelo candidato relay. Exigir RTP crescente/quadros decodificados/áudio recebido em todos os receptores; HTTPS/WSS ou alocação TURN isolados não aprovam.

**Redes distintas:** repetir em duas redes com rota privada/VPN já decidida pelo usuário. Operador bloqueia UDP de saída nos clientes de forma controlada, permite HTTPS4443 e TURN/TLS443 e registra política aplicada sem endereços pessoais. Política relay e par selecionado devem confirmar relay TLS/TCP, mantendo vídeo e áudio. Remover o bloqueio depois. Nenhum script modifica firewall/roteador para esse teste.

## Matriz para concluir G2

| Critério | Evidência a coletar | Estado |
|---|---|---|
| Um apresentador + nove espectadores | Dez identidades distintas confirmadas SFU, nove RTP/quadros/áudio, hardware/redes/versões | Pendente |
| Décimo e concorrência pela última vaga |409; corrida automatizada no service e observação SFU sem 11º participante | Parte automatizada nas regressões; SFU pendente |
| Espectador não publica | AddTrack adversarial efetivamente enviado; SFU `NOT_ALLOWED` e zero tracks do espectador | Pendente nesta execução |
| Token A não acessa B | Grant A prevalece sobre solicitação B; B não recebe identidade de A | Pendente nesta execução |
| Excluir desconecta participantes | Disconnect de todos e confirmação administrativa de ausência | Pendente nesta execução |
| JWT inicial ainda válido não recria A | Reuso antes de expirar recusado com sala ausente | Pendente nesta execução |
| JWT renovado real não recria A | Capturar refresh da SFU só em memória; reuso ainda válido recusado | Pendente nesta execução |
| Geração antiga não recebe mídia nova | Nova B publica vídeo/áudio; tokens iniciais/renovados de A não entram/recebem em B; nomes distintos | Pendente |
| Relay obrigatório e UDP bloqueado | Par selecionado relay e transporte cliente–TURN efetivo, RTP/áudio recebidos entre redes | Pendente |

A T-02 contém resultados históricos parciais de grants/isolamento/exclusão e recepção sintética; não substitui executar esta matriz. Som de sistema real/matriz de 15min continuam no G1/T-03. Duas horas e custo continuam T-23/G4.

## Limpeza

Usar **Sair** nos espectadores e **Parar transmissão** no apresentador; confirmar ausência SFU. Em falha, manter backend/SFU ativos e repetir limpeza pelo botão ou DELETE protegido do [README backend](../../backend/README.md). Desconectar todos os clientes/harness, parar tracks, fechar AudioContexts e timers. `Ctrl+C` nos processos iniciados. Remover somente a composição usada:

```powershell
# Com TURN:
docker compose --env-file .env.capture --env-file .env.capture.turn -f infra/docker/compose.capture.yaml -f infra/docker/compose.capture-turn.yaml down
# Sem TURN, usar em vez do comando acima:
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml down
```

Não usar `--volumes`, derrubar PostgreSQL ou encerrar serviços preexistentes por nome. Arquivos locais `.env.capture`, `.env.capture.turn`, certificados/chaves e `work/capture-turn/` permanecem privados/ignorados e só devem ser removidos conscientemente pelo operador após encerrar os processos. Não reutilizar uma SFU com salas antigas depois de crash/reinício do backend.

## Regressões reproduzíveis

Na raiz, JDK21 e cache local Maven disponíveis:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean verify
.\backend\mvnw.cmd -f backend/pom.xml -Plivekit-proof clean verify
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
.\scripts\Test-CaptureProofSetup.ps1
.\scripts\Test-CaptureTurnSetup.ps1
```

Dentro de `frontend`, Node24:

```powershell
npm.cmd run test:capture
node --test proxy.capture.test.cjs
npm.cmd run build
npm.cmd run build -- --configuration development
npm.cmd run build -- --configuration capture-proof
```

Para repetir integração Java de sinalização/grants em SFU **T-02 loopback** real: iniciar Compose T-02 conforme infra, importar `.env.livekit` com o script próprio, definir `LIVEKIT_REAL_TEST_ENABLED=true` e rodar os builds acima. Não apontar esses testes para outra SFU nem imprimir credenciais. O teste exige refresh real e não aprova apenas gerar segundo JWT no gateway.

| Comando/verificação | Resultado final em 27/10/2026 |
|---|---|
| Maven `clean verify` | **19 aprovados, 4 pulados**, zero falhas/erros |
| Maven `-Plivekit-proof clean verify` | **20 aprovados, 3 pulados**, zero falhas/erros |
| Maven `-Pcapture-proof clean verify` | **35 aprovados, 3 pulados**, zero falhas/erros; 12 testes de service, incluindo capacidade/concorrência/saída/TTL/falhas |
| `npm.cmd run test:capture` | **67 aprovados**, zero falhas |
| `node --test proxy.capture.test.cjs` | **9 aprovados**, zero falhas |
| Três builds Angular solicitados | **Aprovados**: production, development e capture-proof |
| Typecheck e build Vite isolado do harness T-04 | **Aprovados**, sem execução de mídia |
| `node --check tests/t04/serve.mjs` | **Aprovado** |
| `Test-CaptureProofSetup.ps1` | **13 verificações aprovadas** |
| `Test-CaptureTurnSetup.ps1` | **23 verificações aprovadas**, incluindo merge real Compose sem daemon e rejeição de porta 5349 |
| Inspeção dos JARs | Normal: zero classes auxiliares; T-02: zero classes T-03/T-04; capture-proof: zero classes T-02 |
| Inspeção JS produção | Zero marcadores de captura/provas/SDK/harness pesquisados |
| `git diff --check` | **Aprovado**; avisos de normalização CRLF/LF, sem erro de whitespace |

Os pulados Java incluem dois testes SFU opt-in e os testes do auxiliar ausente em cada artefato. Os testes HTTP usam Spring/Tomcat reais apenas em loopback. A tentativa inicial no sandbox falhou com `Permission denied: connect` no loopback; repetição com aprovação automática de execução local passou, sem mudar implementação, certificados ou rede. O Wrapper usa cache existente em `work/maven` (`MAVEN_USER_HOME` e `-Dmaven.repo.local`), pois o diretório padrão fora do workspace não era gravável no sandbox. Logs/builds/cache ficam ignorados em `work`/`target*`.

### Harness sintético separado

[README do harness](../../frontend/tests/t04/README.md) documenta o entrypoint independente das rotas/bundles Angular. Não chamar captura real de sintética nem confundir contadores SFU com recepção. Ele gera canvas e tom, mede RTP/quadros/RMS nos nove clientes, disputa a última vaga, observa pares ICE selecionados e tenta publicar adversarialmente sem mudar JWT. Depois exclui A e publica uma transmissão sintética B, testando tokens iniciais e o token renovado realmente observado. Nenhuma execução desta ferramenta foi atribuída ao presente relatório.

Depois de preparar backend/SFU/TLS como acima, parar o servidor Angular 4443 e, na raiz:

```powershell
. .\scripts\Import-CaptureEnvironment.ps1
Set-Location frontend
node node_modules/typescript/bin/tsc -p tests/t04/tsconfig.json
node tests/t04/serve.mjs --opt-in-t04 'C:\certificados\capture-cert.pem' 'C:\certificados\capture-key.pem'
```

Abrir a raiz da origem HTTPS privada, informar as duas chaves experimentais nos campos protegidos e clicar para executar. Primeiro **Direto** sem overlay; depois **TURN** com overlay. Salvar somente o resultado sanitizado, data/versões/duração/cenário e contadores. Falha de timeout não aprova teste negativo; ausência de token renovado observado mantém esse critério pendente. Um ensaio curto no mesmo computador não homologa nove máquinas, redes distintas, som audível ou duas horas.
