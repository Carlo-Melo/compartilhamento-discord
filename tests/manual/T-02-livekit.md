# T-02 — integração local LiveKit
Data: **26/10/2026**. Situação: **T-02 CONCLUÍDA**. Integração Java e Angular validada contra SFU real, incluindo recepção de vídeo/áudio sintéticos e permissões. T-03 e seguintes não iniciadas.

## Ambiente e versões
Windows 11/PowerShell; Java Temurin 21.0.12.1+1, Spring Boot 4.1.2 e Maven Wrapper/Maven 3.3.4/3.9.16 preservados. Node 24.22.1/npm 11.19.1, Angular 22.2.3 e CLI 22.2.4 preservados. Docker Engine 29.7.2/Compose 5.4.0. Navegador integrado apresentou user-agent Windows x64/Chrome 155.0.0.0; isso não homologa Chrome/Edge externos.

LiveKit Server **1.13.10**, SDK JVM oficial **0.16.0**, SDK browser **2.22.3**. [Inventário e fontes](../../specs/14-bootstrap-inventario.md). Dependências OkHttp alinhadas em 5.3.2 e Jackson 2 em 2.22.2, conforme requisito do SDK; sem trocar Boot ou linguagem. Árvore conferida sem classes OkHttp 4/5 duplicadas.

SFU em Compose separado, sem volume, com portas exclusivamente 127.0.0.1:7880/TCP, 7881/TCP e 7882/UDP. room.auto_create=false, max_participants=10, codecs VP8/Opus, sem TURN, gravação ou egress. Credenciais aleatórias em `.env.livekit` ignorado, não exibidas. PostgreSQL/volume existentes preservados; nenhum schema/tabela foi criado.

## Testes automatizados executados
Com SFU real ativa e `LIVEKIT_REAL_TEST_ENABLED=true`, `clean verify` passou no backend normal e no profile `livekit-proof`: **5 testes, 0 falhas, 0 erros, 0 pulados**.

| Classe / teste | Evidência |
|---|---|
| `LiveKitTokenTest` — apresentador | JWT real do SDK: assinatura HMAC validada independentemente, sala/identidade, TTL 120s, somente fontes de tela/áudio, sem assinatura/dados/admin/criação. |
| `LiveKitTokenTest` — espectador | JWT real: sala/identidade, assinatura permitida e publicação/dados/admin/criação ausentes ou negados. |
| `LiveKitTokenTest` — configuração | Integração desabilitada por padrão; habilitada sem credenciais falha com mensagem sanitizada. |
| `LiveKitLocalIntegrationTest` — SFU real | Java cria/consulta duas salas próprias, limite10; token de A com query solicitando B entra somente em A e B permanece vazia. WebSocket usa Authorization header, sem token na URL do teste. |
| Mesmo teste SFU real | AddTrack enviado diretamente, contornando qualquer guard do browser: SFU responde **NOT_ALLOWED** para vídeo SCREEN_SHARE e áudio SCREEN_SHARE_AUDIO do espectador; consulta confirma zero tracks. |
| Mesmo teste SFU real | DeleteRoom encerra a sinalização e remove A; reuso do mesmo token antes dos120s recebe **HTTP404**; A não é recriada. A e B removidas independentemente em finally. |
| `LiveKitLocalIntegrationTest` — autenticação inválida | Chave fictícia desconhecida na SFU produz AUTHENTICATION_FAILED e erro fixo, sem causa ou credencial/corpo do provedor expostos. |

Esses testes provam administração, grants e sinalização reais, **não transporte de RTP**. Sem a flag real, os dois testes SFU são explicitamente pulados; três testes locais continuam executados. Reports ficam em `backend/target/surefire-reports` ou `backend/target-livekit-proof/surefire-reports` (ignorados).

## Verificações manuais e por comandos
- Docker: imagem/versão/digest, healthcheck, parser estrito, mount da configuração somente leitura e `compose config --quiet` aprovados. Nenhuma configuração integral com segredo foi impressa.
- Backend/HTTP: liveness e readiness PostgreSQL UP. Mesmo JAR auxiliar iniciado sem opt-in e com profiles `livekit-proof,prod` respondeu404 na rota de emissão. Sem marcador, sem Origin em POST ou com Origin externa:403, sem emitir token.
- Empacotamento: JAR normal não contém controllers/services/DTOs auxiliares. Profile usa diretório separado `target-livekit-proof`, evitando resíduos entre builds. Build Angular production exclui rota/ferramenta/SDK; development os inclui por import lazy.
- `npm ci`, builds Angular development/production e `git diff --check` aprovados. Auditoria npm após instalação reportou0 vulnerabilidades; resultado pontual, sem garantia permanente.
- Arquivos locais `.env`, `.env.livekit`, caches, builds e evidências ignorados. Nenhum segredo/token foi incluído em documentação, URL da aplicação ou resultado de ferramenta. O SDK browser usa a credencial somente em memória no seu transporte de sinalização; suas URLs internas não foram copiadas e logs SDK foram desativados.

## Prova Angular aprovada contra SFU real
A interface `/dev/livekit` foi acionada por clique via automação de UI no navegador integrado. O roteiro da ferramenta fez dez verificações programáticas, todas **APROVADAS**; não é uma suíte Cucumber/Playwright de CI. Nenhum mock ou interceptação de resposta foi usado.

| Verificação da ferramenta | Resultado real |
|---|---|
| Criar e consultar sala | Java/SFU confirmaram sala própria vazia e limite10. |
| Conectar clientes distintos | Apresentador e espectador conectados por RTC; identidades distintas confirmadas na consulta administrativa Java. |
| Publicar tracks sintéticas | SFU confirmou duas tracks do apresentador, screen_share e screen_share_audio. |
| Receber e decodificar | RTP crescente; amostra da execução final: **27.612 bytes de vídeo**, **4.897 bytes de áudio**, **9 quadros decodificados**, **RMS0,0422**; vídeo0,86s/áudio0,91s. |
| Barreira local do espectador | SDK recusou publicação normal com permissões de espectador. |
| Barreira efetiva da SFU | Cópia local das permissões alterada apenas para enviar AddTrack adversarial; JWT inalterado. SFU respondeu **NOT_ALLOWED** ao AddTrack, e consulta confirmou zero tracks do espectador. Timeout isolado não conta como aprovação. |
| Escopo do token válido | Query room=B com JWT válido de A conectou somente em A; B não recebeu a identidade. |
| Adulteração de sala | Alterado somente video.room para B, preservando assinatura antiga: **401** da SFU; B permaneceu vazia. |
| Excluir e desconectar | DeleteRoom confirmado; ambos os clientes receberam desconexão e sala ausente. |
| Reusar token antigo | Mesmo JWT ainda no TTL recusado após exclusão; consulta Java confirmou sala ausente, sem recriação automática. |

As tentativas iniciais usavam overrides locais de maxRetries=0 e timeouts de6s/8s e falhavam em ICE/checking. A configuração final remove esses overrides e usa os padrões do SDK fixado (1 retry, timeouts15s). Com ela, a jornada passou duas vezes, inclusive a execução final que exige resposta NOT_ALLOWED explícita. Não foi isolado qual override individual causava a falha; não se atribui o problema a firewall ou ao navegador.

Opções diagnósticas temporárias `force_tcp`, ICE Lite e negociação clássica foram revertidas. A SFU final mantém UDP/TCP e o SDK usa sua negociação padrão. Apenas contagens/classes/estados de diagnóstico foram expostos, sem SDP ou credenciais ICE. Nenhuma política/firewall foi alterada e nenhuma ação adicional do operador foi necessária.

Screenshot final: `tests/artifacts/T-02-livekit-aprovado.png` (ignorado, sem segredos). Canvas e OscillatorNode geraram sinais sintéticos; RTP, decodificação, avanço e RMS foram medidos no receptor real. Essas amostras não são benchmark de latência/qualidade.

## Itens fora desta validação
Não executados: captura real ou áudio do Windows, duas máquinas, Chrome/Edge externos, nove espectadores, TURN, latência/capacidade/custo e limpeza de startup/readiness de produção. A prova local não homologa esses cenários. Eles permanecem nas tarefas próprias; a próxima é T-03.

## Reproduzir e retomar
Pré-requisitos/runtimes da T-01 e instruções em [backend](../../backend/README.md), [frontend](../../frontend/README.md) e [infra](../../infra/README.md). Na raiz, com Java21/Node24 no PATH:

```powershell
# Somente na primeira configuração; não sobrescreve arquivo já existente:
if (-not (Test-Path .env.livekit)) { .\scripts\Initialize-LiveKitLocal.ps1 }
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait
docker compose --env-file .env.livekit -f infra/docker/compose.livekit.yaml up -d --wait
. .\scripts\Import-DevEnvironment.ps1
. .\scripts\Import-LiveKitEnvironment.ps1
$env:LIVEKIT_REAL_TEST_ENABLED = 'true'
.\backend\mvnw.cmd -f backend/pom.xml -Plivekit-proof clean verify
java -jar .\backend\target-livekit-proof\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=livekit-proof --livekit.enabled=true --livekit.proof.enabled=true
```

Em outro terminal com Node24: `cd frontend`, `npm.cmd ci`, `npm.cmd start`. Abra http://127.0.0.1:4200/dev/livekit no Chrome/Edge e clique **Executar prova local**. Não há captura de dispositivos. Registrar resultados reais de conexão e contadores; falhas genéricas não aprovam testes negativos.

Antes de encerrar o backend, executar o DELETE de limpeza própria documentado em backend/README.md. Ele tenta todos os nomes gerados pelo processo, inclusive após falha; não varre salas de terceiros. Depois Ctrl+C nos terminais, `stop postgres` no Compose do banco e `down` somente no Compose LiveKit, sem `--volumes`. Os processos/containers temporários desta execução foram encerrados após a validação; volume PostgreSQL e serviços preexistentes preservados.

Próxima tarefa: **T-03 — captura real de vídeo e áudio de sistema entre duas máquinas**, ainda não iniciada. Sem bot, tickets, vínculo persistente, captura real ou limpeza de startup/readiness de produção nesta entrega.
