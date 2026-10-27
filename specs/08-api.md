# 08 — Contratos HTTP e integrações
Contrato estrutural: [api/openapi.json](api/openapi.json), OpenAPI 3.1. Java 21/Spring Boot + Angular, mesma origem. Sem endpoints de cadastro/login OAuth de usuários.

## Regras
UUID de sessão; IDs Discord sempre strings. Bot/guild/user/papel vêm da interaction assinada e do contexto consumido do ticket. Não confiar nesses campos no corpo do browser.
JSON camelCase; nunca entidades JPA. Cache-Control:no-store para contexto, ticket e mídia. Erro: code, message em pt-BR, requestId, retryable; sem credenciais ou conteúdo de provedores.
`requestId` é UUID gerado pelo backend para cada requisição e também enviado em `X-Request-Id`; valores recebidos nesse header não são refletidos. Erros de filtros Spring Security e MVC usam o mesmo envelope. JSON malformado/entrada inválida retorna 400; erro inesperado retorna 500 com mensagem fixa. Detalhes de SQL, stacktrace, caminhos e corpos de provedores nunca integram a resposta.
401 sem contexto/ticket inválido; 403 papel/CSRF; 404 sessão fora do escopo/inexistente; 409 estado/vaga/chave; 410 terminal; 429+Retry-After; 503 dependência indisponível.
CSRF + cookie bootstrap exigidos também na troca de ticket, com Origin da aplicação. Segurança padrão OpenAPI para rotas privadas é accessCookie; mutações incluem csrfHeader.
Idempotency-Key em start/join/reconnect/end: chave 16–128 caracteres, descriptor em memória até expiração do contexto, máximo 8h. exchange é explicitamente de uso único e não idempotente.
Nenhuma credencial web ou mídia é persistida no PostgreSQL.

Códigos gerais da base T-05:

| HTTP | code | retryable |
|---|---|---|
| 400 | `INVALID_REQUEST` — JSON, validação ou caminho rejeitado pelo firewall | false |
| 401 | `ACCESS_REQUIRED` — ausência de contexto autorizado | false |
| 403 | `FORBIDDEN`, `CSRF_INVALID`, `ORIGIN_INVALID` | false |
| 404 | `NOT_FOUND` | false |
| 405 | `METHOD_NOT_ALLOWED` | false |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | false |
| 500 | `INTERNAL_ERROR` — mensagem fixa | false |
| 503 | `SERVICE_UNAVAILABLE`, `MEDIA_UNAVAILABLE` | true |

Esses códigos são a base atual; códigos dos futuros casos de uso continuam nas suas definições. A cadeia de segurança pode rejeitar antes do MVC, sem revelar existência de recurso privado. GET público dispensa contexto, mas uma Origin externa explicitamente enviada ou indicação cross-site é rejeitada com `403 ORIGIN_INVALID`, inclusive em `/api/csrf` e `/health/live`; sondas sem Origin continuam conforme contrato de saúde.

## Endpoints web
| Método / caminho | Acesso | Resultado |
|---|---|---|
| GET /api/csrf | Público/bootstrap | Token CSRF e cookie XSRF-TOKEN para SPA |
| POST /api/access/exchange | Bootstrap + CSRF + Origin | Consome ticket; cria cookie ACCESS_SESSION em memória e destino fixo |
| GET /api/access/session | Contexto | Papel, sessão autorizada e validade; sem token |
| POST /api/access/revoke | Contexto + CSRF | Invalida contexto; OWNER encerra, VIEWER sai; trata revogação de mídia |
| GET /api/sessions/{id} | Contexto da sessão | Snapshot e ações permitidas |
| POST /api/sessions/{id}/start | OWNER | Reserva geração/publicação e retorna conexão STARTING |
| POST /api/sessions/{id}/join | VIEWER | Reserva vaga e retorna grant de assinatura em LIVE |
| POST /api/sessions/{id}/reconnect | Lease do contexto | Credencial atual após revalidação |
| POST /api/sessions/{id}/heartbeat | Lease/contexto | Estado, geração e autorização válida |
| POST /api/sessions/{id}/leave | VIEWER | Libera lease idempotentemente; não equivale a revogar cookie |
| POST /api/sessions/{id}/end | OWNER | ENDING/terminal e limpeza; admin encerra via comando Discord |

Não há POST público de criação de ticket/sessão ou de vínculo. Emitir ticket somente como resposta à interaction Discord validada.
exchange recebe apenas ticket (43 caracteres base64url). Consumir em lock, validar escopo/boot/validade e associação. Sucesso 200 com AccessContext + nextPath interno, rotacionando cookie. Ticket vencido/usado/boot anterior:401 ACCESS_INVALID; associação/vínculo inválidos:403 ACCESS_DENIED; dependência:503. Após consumo com falha, novo ticket vem do bot.
Não devolver ticket, dados Discord de terceiros ou tokens de bot. Código não é JWT de mídia.
GET privado sem contexto retorna401; página oferece voltar ao Discord, sem login.
start recebe sourceSurface (monitor/window/browser), audioScope (system/window/tab) e profile; homologação inclui system. Dados são declarados; SFU confirma tracks. join exige LIVE e papel VIEWER; dono que tentar join recebe409 USE_BROADCAST_ROUTE.
Snapshot a cada 5s e heartbeat10s; evitar retry infinito. Contexto/SFU precisam continuar válidos, não apenas cookie existente.

## DTOs
AccessContext: sessionId, botId, guildId, discordUserId, displayName mínimo, role OWNER/VIEWER, expiresAt, bootId. ID de contexto fica só em cookie opaco; dados vêm do ticket original.
AccessExchangeResponse: access (AccessContext), nextPath (/s/{id} ou /s/{id}/transmitir).
SessionView: id, botId, guildId, title, owner{discordUserId,displayName}, status, reason nullable, role OWNER/VIEWER, viewerCount, maxViewers=9, generation nullable, profile, actions{canStart,canJoin,canEnd}, createdAt, liveAt/endedAt nullable, version.
MediaConnection: sessionId, generation, roomName, identity, role PUBLISHER/SUBSCRIBER, serverUrl WSS, token efêmero, expiresAt, leaseId. Somente em memória no navegador.
Heartbeat: leaseId + generation; retorna state, generation, authorizedUntil, shouldReconnect, shouldStop. Nenhum dado do cliente declara LIVE.
Estado terminal pode retornar snapshot ao contexto ainda em janela de limpeza; depois invalidar acesso, retornando401. Interface trata tanto terminal quanto401 interrompendo mídia.

## Integrações e saúde
POST /integrations/discord/interactions: Ed25519/timestamp/corpo bruto, aplicação configurada e contexto GUILD. PING->PONG; comandos e botão custom_id->ACK/defer privado <=3s. Link URL não aciona endpoint.
POST /integrations/livekit/webhook: autenticar assinatura/hash com SDK e corpo bruto, deduplicar em memória e enfileirar. Aceite é volátil: crash perde evento e exige limpeza no próximo boot.
GET /health/live público sem configuração. GET /health/ready privado; depende de banco de vínculos e conclusão de limpeza de salas antigas. Não expor Actuator env/configprops/heapdump.

Bootstrap T-01: `/health/live` retorna somente `{"status":"UP"}` (200) ou `{"status":"DOWN"}` (503) conforme liveness do processo, sem testar banco. Em desenvolvimento, a API escuta em `127.0.0.1:8080`; o Angular usa `/api/health/live` e o proxy retira apenas o prefixo `/api` do caminho de saúde. `/health/ready` fica exclusivamente na gestão `127.0.0.1:8081`, fora do proxy Angular, retornando somente status com 200/503. Nesta etapa readiness verifica aplicação + conexão JDBC; a limpeza SFU prevista no contrato será incorporada na T-19. Não há outros endpoints do produto implementados.

T-05 implementa `GET /api/csrf` e a base deny-by-default, sem implementar os demais casos de uso do produto. Sem contexto, GET privado retorna `401 ACCESS_REQUIRED`; mutação primeiro verifica Origin (`403 ORIGIN_INVALID`) e CSRF (`403 CSRF_INVALID`) e, mesmo válidos, continua sem autorização de produto (401). O cookie técnico já se chama `ACCESS_SESSION`, mas contém somente bootstrap CSRF, sem contexto de usuário autorizado; seu identificador será rotacionado na troca real T-07. `/api/access/exchange` continua bloqueado; bootstrap não simula troca de ticket. Rotas de integração sem verificação de assinatura continuam negadas. Gestão aceita somente GET de readiness na sua porta/interface; demais endpoints Actuator são negados, inclusive saúde agregada ou detalhes internos.

Nos auxiliares T-02/T-03, T-05 preserva os códigos/mensagens específicos e amplia explicitamente o contrato `Failure` de dois para quatro campos: `code`, `message`, `requestId`, `retryable`. Falhas de integração usam `retryable=true`; rejeições de acesso/validação usam `false`. Erros estruturais MVC usam os códigos gerais. Os clientes existentes não dependem de um objeto com somente dois campos: T-02 apresenta mensagem sanitizada e T-03 trata status com mensagens locais. As exceções específicas ao token CSRF, métodos/caminhos e proteções alternativas constam no [relatório T-05](../tests/manual/T-05-seguranca-backend.md); não ampliam a API definitiva.

T-02 acrescenta um [contrato auxiliar local separado](api/livekit-proof.openapi.json), sob `/api/dev/livekit/rooms`, sem alterar as regras do contrato público acima. Suas fontes não entram no artefato padrão; exigem build Maven e profile Spring `livekit-proof`, flag explícita e loopback, com profiles `prod`/`production` proibidos. Somente salas e identidades aleatórias criadas pelo próprio processo, TTL de token 120s, respostas `no-store`; nunca API key/secret. O marcador não secreto, Host e origem limitam a ferramenta local, sem representar autorização de usuários. Não há emissão de tickets nem endpoint livre na configuração de produção. [Cenários da prova](tests/livekit-local.feature).

T-03 acrescenta um [contrato auxiliar restrito separado](api/capture-proof.openapi.json), sob `/api/dev/capture/session`. Exige artefato/profile/opt-in `capture-proof`, origem HTTPS privada exata, proxy local e chave experimental distinta por papel, sem endpoint de token por sala/papel arbitrário. Não há cookies ou tickets de produto nesse auxiliar. Criar/excluir é exclusivo do apresentador; espectador só obtém credencial de assinatura da sala ativa e consulta estado. `live` é resultado de consulta administrativa à SFU; o cliente não o declara. Normal/prod/production excluem o auxiliar. [Cenários T-03](tests/capture-real.feature) e [validação pendente](../tests/manual/T-03-captura-real.md).

## Códigos

Revisão do auxiliar T-03 (contrato `0.3.0`): `GET /api/dev/capture/session/access` retorna somente `{role:PRESENTER|VIEWER}` após validar a chave experimental nas mesmas barreiras do auxiliar. Não acessa SFU, cria sala ou emite JWT. A UI valida após colagem, rejeita papel de outra página e invalida respostas antigas ao editar. A checagem antecipada não substitui autorização nas operações. Erros mantêm envelope sanitizado; o consumidor apresenta mensagens locais e somente códigos permitidos/UUIDs. Log técnico do auxiliar usa ação/status/requestId, sem URL, corpo, chave, JWT ou identidade. Não é um endpoint de login ou ticket do produto.
ACCESS_REQUIRED, ACCESS_INVALID, ACCESS_DENIED, ACCESS_EXPIRED, FORBIDDEN, SESSION_NOT_FOUND, SESSION_ENDED, SESSION_ALREADY_OPEN, SESSION_NOT_LIVE, USE_BROADCAST_ROUTE, CAPACITY_REACHED, LEASE_REQUIRED, STALE_GENERATION, INVALID_STATE, IDEMPOTENCY_CONFLICT, AUDIO_REQUIRED, GUILD_NOT_ENABLED, GUILD_NOT_CONFIGURED, BOT_GUILD_NOT_BOUND, BINDING_UNAVAILABLE, DISCORD_UNAVAILABLE, MEDIA_UNAVAILABLE, RATE_LIMITED.
