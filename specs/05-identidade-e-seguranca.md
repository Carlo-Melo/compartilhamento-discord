# 05 — Acesso pelo bot e segurança
Status: substitui o login OAuth de usuários previsto na versão 1.1.

## Origem da autorização
O usuário interage no Discord; a interaction assinada identifica application, guild, member e ação. Backend confirma vínculo persistido do bot com guild, allowlist e associação via GET /guilds/{guildId}/members/{userId} usando o token do bot.
O site não pede conta, senha, email, consentimento OAuth de usuário ou token Discord pessoal. Não existe tabela de usuários ou Spring Session JDBC.
A identidade da interação serve para emitir autorização; um ticket bearer não comprova a identidade da pessoa física que abre o navegador se for repassado. Não afirmar que o bot autentica automaticamente qualquer visitante do site.

## Ticket temporário
- Código criptograficamente aleatório de 256 bits (base64url de 43 caracteres), uso único e TTL 60s.
- Guardar somente digest SHA-256 em memória, junto de botId/guildId/sessionId, discordUserId, displayName mínimo, papel, bootId e expiresAt.
- Emitir somente após interaction válida; destino interno fixado pelo backend. Não aceitar papel, userId, guildId ou redirect do navegador.
- Entrega apenas em resposta privada do bot, no fragmento /acesso#ticket=CODIGO. Nada em query, URL pública, logs ou histórico de métricas.
- Angular extrai em memória e limpa fragmento imediatamente via replaceState. Nenhum analytics/script de terceiros nessa página. GET nunca consome ticket nem revela informações.
- Antes do POST de troca, obter CSRF/bootstrap da mesma origem. Consumo atômico (check+consume sob lock), falhar fechado para expirado, repetido, boot diferente ou sessão encerrada. Revalidar vínculo e associação antes de criar contexto.
- Se validação externa falhar, não restaurar ticket já consumido; obter novo acesso pelo bot. Retry da troca de código não é idempotente e nunca cria dois contextos.
- Não vincular ticket a IP: NAT e mudanças de rede não comprovam conta Discord.

## Contexto de acesso web, somente em memória
POST /api/access/exchange cria contexto e cookie ACCESS_SESSION, HttpOnly, Secure em HTTPS, SameSite=Lax, sem Domain. Rotacionar ID de sessão/preacesso após sucesso.
Escopo: um bot, um servidor, uma sessão, um Discord user e papel OWNER ou VIEWER. Não dar permissão sobre outras salas. Expiração ociosa 30min; absoluta 8h; fim da transmissão invalida os contextos. Não persistir HttpSession, contextos ou tickets em banco, Redis, arquivo ou backup.
Novo ticket consumido para o mesmo usuário/sessão substitui contexto anterior, retira a conexão antiga e invalida seus acessos. Emissão do ticket sozinha não derruba acesso existente; consumo sim. Cookie único do piloto pode substituir contexto de outra sessão.
Abrir o site diretamente sem contexto pede retorno ao bot; não redireciona para login.
Sair via POST /api/access/revoke invalida contexto e acesso de mídia; OWNER encerra a sessão, VIEWER sai. Não usar o termo logout na interface.
Expirar/invalidar acesso com mídia emitida requer remoção e rotação/exclusão da sala conforme 03; expiração de cookie sozinha não desconecta WebRTC.

## Autorização contínua
Ações conferem contexto web, escopo, bootId, vínculo e estado atual. Usuário e papel não podem vir confiados do Angular. OWNER publica/encerra; VIEWER assina/sai; administração fica nos comandos do Discord.
Associação via REST do bot: cache até 30s. Revalidar conectados a cada 30s; falta observável de membro remove acesso em até 60s. 404 é ausência; timeout/429/5xx é indisponibilidade. Depois de 60s sem última confirmação válida, cortar mídia e exigir novo acesso pelo bot.
Vínculo bot–guild é validado a cada emissão e rechecado em operação; se DB ficar indisponível não emitir acessos novos. Contextos ativos têm no máximo 60s desde a última confirmação de vínculo.
Não listar nem armazenar todos os membros. Permissões administrativas de comandos vêm da interaction válida e, quando necessária revalidação, do proprietário/cargos resolvidos com REST do bot.
401 sem contexto; 403 papel insuficiente; 404 recurso inexistente/fora do escopo. Ticket inválido retorna 401 ACCESS_INVALID, sem revelar se usuário/guild existem.

## CSRF, origens e credenciais
Mesma origem para Angular e /api. GET /api/csrf fornece token e cookie XSRF-TOKEN; mutações, inclusive access/exchange, enviam X-XSRF-TOKEN e usam sessão de bootstrap. Validar Origin. SameSite não substitui CSRF.
Nas rotas do produto, exceções de CSRF somente nas integrações assinadas, depois de implementar a verificação da assinatura. Discord/LiveKit ainda não verificados continuam negados na T-05. Spring Security trata sessão em memória e CSRF; sem dependências OAuth2 Client e Spring Session JDBC.
CORS fechado, dev com proxy Angular. CSP restrita a aplicação/SFU; frame-ancestors none; display-capture próprio; câmera/microfone desabilitados. Cache-Control:no-store e Referrer-Policy:no-referrer nas rotas de acesso/mídia.
Bot token/chaves SFU apenas em segredos de ambiente. Nenhuma credencial de usuário Discord é solicitada ou guardada. Token de mídia apenas em memória com grants mínimos. Os tickets são credenciais temporárias legítimas; a proibição de segredos em links continua para bot/infra.

## Base entregue na T-05 e fronteira com T-07

A política é negar por padrão. Somente `GET /health/live` e `GET /api/csrf` são públicos na API; `GET /health/ready` é acessível exclusivamente na gestão em loopback. Sem contexto definitivo, rotas privadas continuam negadas com `401 ACCESS_REQUIRED`, inclusive com headers de identidade/papel, cookie inventado ou Authorization fornecidos pelo navegador. Não há formulário de login, HTTP Basic, senha gerada ou contexto de usuário provisório.

O bootstrap CSRF cria somente uma sessão técnica em memória e um token sincronizado. Cookie `XSRF-TOKEN` é legível pela SPA; o cookie da sessão técnica já usa o nome contratual `ACCESS_SESSION`, com HttpOnly, SameSite=Lax, sem Domain, restrito a cookies e Secure quando o transporte é HTTPS. A existência desse cookie não representa contexto autorizado: nesta etapa guarda somente CSRF, sem SecurityContext persistido. Mutações da API exigem Origin exata configurada em `app.security.origin` e token `X-XSRF-TOKEN` vinculado à mesma sessão. A origem local padrão é `http://127.0.0.1:4200`; o proxy mantém a mesma origem vista pelo navegador. Ausência/erro de Origin e de CSRF retorna 403. CORS não libera origens externas.

T-02/T-03 continuam contratos experimentais separados. Somente os métodos/caminhos existentes nos artefatos habilitados têm a exceção específica ao token CSRF: essas chamadas não usam cookies como autorização e exigem seus headers customizados, Origin/Host exatos e loopback; T-03 exige adicionalmente capacidade secreta por papel. Um site externo não consegue enviar esses headers pelo navegador sem preflight autorizado, e CORS permanece fechado. As barreiras dos auxiliares integram a cadeia de segurança antes da autorização; não há liberação geral de `/api/dev/**`. Artefato ausente, opt-in desligado ou profiles de produção preservam 404. Essa exceção experimental não se aplica às futuras rotas do produto.

Respostas da API incluem no-store, nosniff, bloqueio de framing e Referrer-Policy no-referrer, além de CSP para o conteúdo da própria resposta. Cabeçalhos enviados pela API **não protegem o documento Angular**, servido por outro componente. O servidor/proxy do documento ainda deve aplicar CSP com `frame-ancestors 'none'`, `connect-src` restrito à aplicação/SFU e Permissions-Policy com `display-capture=(self)`, `camera=()` e `microphone=()`. Não usar `display-capture=()` ou bloquear WSS/recursos de mídia necessários às provas autorizadas. A política final do documento e HTTPS de produção serão validados na T-20.

Ticket, troca atômica, rotação do identificador após troca e associação de contexto autorizado ao cookie `ACCESS_SESSION` continuam na T-07. Persistência de bot/vínculo entra na T-06; limpeza de salas antes de readiness fica na T-19. [Política e evidência T-05](../tests/manual/T-05-seguranca-backend.md).

## Revogação e reinício
Na extensão experimental T-04, `POST` e `DELETE /api/dev/capture/session/viewer` também exigem correlação UUID v4 em `X-Capture-Viewer`, além da capacidade VIEWER e das barreiras existentes. O UUID correlaciona uma tentativa e não identifica pessoa ou conta Discord. Identidade de mídia é gerada pelo backend, até nove por geração; repetição reutiliza a identidade. A saída remove o participante, sem afirmar revogação dos JWTs e sem reciclar vaga para outro visitante. A sala tem prazo absoluto de uma hora; expiração fecha emissão e exige exclusão confirmada antes de liberar outra geração. Essa restrição pertence apenas à prova; acesso e revogação definitivos continuam nas tarefas de tickets/geração.

Em self-hosted, DeleteRoom + auto_create=false + nome não reutilizado protegem contra JWT antigo; remover participante isoladamente não invalida todos os tokens.
No reinício, bootId muda, mapas/cookies deixam de autorizar e todas as salas antigas do namespace exclusivo são excluídas antes de readiness. Não reaproveitar mídia ativa só porque vínculo bot–servidor continua no banco.
Nenhuma garantia de continuidade de sessão/fila/contador no reinício. A chamada permanece no Discord; a transmissão externa precisa ser reiniciada.

## Limites e minimização
Rate limits em memória: tickets 5/min por usuário/sessão e 30/min por guild; troca 10/min por IP e bootstrap, com cuidado para NAT do grupo; mídia 20/min por contexto; demais ações 60/min. Ajustar por evidência.
Não persistir IDs de participantes, títulos, tickets, IPs, conteúdo de tela/áudio ou histórico de acessos em logs do produto. Logs de infraestrutura devem ser minimizados/redigidos; métricas agregadas são técnicas.
Persistência restrita não significa ausência de tratamento de dados; não declarar isenção legal por esse desenho. Não prometer E2EE contra o operador da SFU.
