# 08 — Contratos HTTP e integrações
Contrato estrutural: [api/openapi.json](api/openapi.json), OpenAPI 3.1. Java 21/Spring Boot + Angular, mesma origem. Sem endpoints de cadastro/login OAuth de usuários.

## Regras
UUID de sessão; IDs Discord sempre strings. Bot/guild/user/papel vêm da interaction assinada e do contexto consumido do ticket. Não confiar nesses campos no corpo do browser.
JSON camelCase; nunca entidades JPA. Cache-Control:no-store para contexto, ticket e mídia. Erro: code, message em pt-BR, requestId, retryable; sem credenciais ou conteúdo de provedores.
401 sem contexto/ticket inválido; 403 papel/CSRF; 404 sessão fora do escopo/inexistente; 409 estado/vaga/chave; 410 terminal; 429+Retry-After; 503 dependência indisponível.
CSRF + cookie bootstrap exigidos também na troca de ticket, com Origin da aplicação. Segurança padrão OpenAPI para rotas privadas é accessCookie; mutações incluem csrfHeader.
Idempotency-Key em start/join/reconnect/end: chave 16–128 caracteres, descriptor em memória até expiração do contexto, máximo 8h. exchange é explicitamente de uso único e não idempotente.
Nenhuma credencial web ou mídia é persistida no PostgreSQL.

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

T-02 acrescenta um [contrato auxiliar local separado](api/livekit-proof.openapi.json), sob `/api/dev/livekit/rooms`, sem alterar as regras do contrato público acima. Suas fontes não entram no artefato padrão; exigem build Maven e profile Spring `livekit-proof`, flag explícita e loopback, com profiles `prod`/`production` proibidos. Somente salas e identidades aleatórias criadas pelo próprio processo, TTL de token 120s, respostas `no-store`; nunca API key/secret. O marcador não secreto, Host e origem limitam a ferramenta local, sem representar autorização de usuários. Não há emissão de tickets nem endpoint livre na configuração de produção. [Cenários da prova](tests/livekit-local.feature).

T-03 acrescenta um [contrato auxiliar restrito separado](api/capture-proof.openapi.json), sob `/api/dev/capture/session`. Exige artefato/profile/opt-in `capture-proof`, origem HTTPS privada exata, proxy local e chave experimental distinta por papel, sem endpoint de token por sala/papel arbitrário. Não há cookies ou tickets de produto nesse auxiliar. Criar/excluir é exclusivo do apresentador; espectador só obtém credencial de assinatura da sala ativa e consulta estado. `live` é resultado de consulta administrativa à SFU; o cliente não o declara. Normal/prod/production excluem o auxiliar. [Cenários T-03](tests/capture-real.feature) e [validação pendente](../tests/manual/T-03-captura-real.md).

## Códigos
ACCESS_REQUIRED, ACCESS_INVALID, ACCESS_DENIED, ACCESS_EXPIRED, FORBIDDEN, SESSION_NOT_FOUND, SESSION_ENDED, SESSION_ALREADY_OPEN, SESSION_NOT_LIVE, USE_BROADCAST_ROUTE, CAPACITY_REACHED, LEASE_REQUIRED, STALE_GENERATION, INVALID_STATE, IDEMPOTENCY_CONFLICT, AUDIO_REQUIRED, GUILD_NOT_ENABLED, GUILD_NOT_CONFIGURED, BOT_GUILD_NOT_BOUND, BINDING_UNAVAILABLE, DISCORD_UNAVAILABLE, MEDIA_UNAVAILABLE, RATE_LIMITED.
