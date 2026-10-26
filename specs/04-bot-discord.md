# 04 — Bot e integração Discord
## Instalação, identificação e vínculo
A aplicação Discord identifica o bot; o bot faz a ponte entre servidor e site. Código de aplicação no backend Spring Boot, com interactions HTTPS assinadas e Discord REST. Sem conexão de voz, Go Live, self-bot ou Gateway obrigatório.
Scopes de instalação bot + applications.commands. A autorização para instalar o bot no servidor continua no Discord; não é login de usuários no site.
Permissões mínimas no canal: View Channel, Send Messages, Embed Links. Não solicitar Administrator ou acesso a mensagens privadas. Get Guild Member individual é usado para conferir associação; não importar a lista inteira de membros.
Banco: discord_bot e bot_guild_binding, conforme 07. Um bot pode ter vários vínculos com servidores. Piloto usa uma aplicação configurada por deploy; suporte a várias credenciais simultâneas exige roteamento por aplicação validado.
Application ID da interação deve corresponder ao bot configurado e ao vínculo; não confiar em botId/guildId vindos do site.

## Comandos e ações
| Ação | Quem | Resultado |
|---|---|---|
| /transmissao configurar canal | Manage Guild | Confirma bot/servidor e persiste vínculo; canal por ambiente ou memória |
| /compartilhar [titulo] | Membro | Cria sessão em memória e entrega ticket OWNER privado |
| Botão público Assistir (custom_id) | Membro | Valida contexto e entrega ticket VIEWER privado |
| /transmissao status | Membro | Estado privado e novo ticket do papel permitido, se aplicável |
| /transmissao encerrar | Criador ou Manage Guild | Encerra sessão idempotentemente pelo Discord |

Título opcional de 1–80 caracteres; padrão Tela de {nome}. Escapar menções e allowed_mentions vazio.
Somente GUILD, guild habilitada na allowlist e vínculo válido. Configuração verifica canal e permissões do bot, sem guardar canal ou configurador em tabela.
Sessão já aberta: dono recebe estado/novo acesso, sem criar outra; outro membro não toma posse nem ganha papel de apresentador.
Administrador encerra pelo comando; não conceder acesso OWNER a ele no site.

## Handoff para o site
O botão público Assistir usa custom_id, por exemplo watch:{UUID}, e NÃO contém ticket.
A interaction fornece usuário/servidor ao bot. Validar também sessão da mensagem, guild e application ID antes de emitir código.
Responder privadamente com Abrir transmissão -> https://APP_ORIGIN/acesso#ticket={codigo}. O link é pessoal no sentido de emissão por usuário, mas é bearer: quem o possuir antes do consumo pode utilizá-lo.
Código opaco aleatório de 256 bits, TTL 60s, uso único, escopo OWNER ou VIEWER; registro somente em memória. Nunca usar bot token ou JWT de mídia como esse código.
Botão URL comum não gera interaction nem redirecionamento automático controlado pelo bot; o fluxo tem botão público e resposta privada com link. Não prometer um único clique que identifica o usuário e abre navegador.
Para renovar acesso, clicar novamente/consultar status no Discord; não criar cadastro ou login no site.

## Anúncio e atualização
ACK/defer privado antes de 3s; meta p95 <=1s. Operações posteriores executam após defer.
Anúncio público somente depois de vídeo e áudio confirmados: título, apresentador, Ao vivo, contador e botão custom_id Assistir. Sem ticket, segredo ou prévia da tela.
channel_id/message_id ficam apenas no estado em memória. Coalescer contagem em 15s e priorizar encerramento; remover Assistir em terminal enquanto contexto existir.
Fila de atualizações com backoff em memória; respeitar Retry-After e buckets. Sem outbox persistida, sem promessa de entrega após reinício ou exactly-once remoto.
Ao perder contexto por reinício, mensagem antiga pode permanecer com texto desatualizado; o clique sempre revalida e responde Sessão encerrada. Não reconstruir sessão a partir da mensagem ou criar histórico no banco.

## Verificação de eventos
Ed25519 sobre timestamp + corpo bruto; assinatura inválida -> 401. Tratar PING e contexto GUILD, janela de replay 5min e deduplicação por interaction_id em memória. Após reinício, rejeitar interações de ação cujo snowflake interaction_id indique criação anterior ao boot atual, mesmo se assinatura/timestamp forem válidos; validar assinatura antes de interpretar o ID. Isso também rejeita retries legítimos anteriores ao boot e exige nova ação. PING não emite acesso e é tratado separadamente. Testar replay do mesmo corpo com timestamp original e renovado, sem usar banco de deduplicação.
Validar permissões atuais do membro para administrar. O papel no site vem do ticket, não de corpo de requisição.
Tokens de continuação da interaction ficam só em memória pelo tempo necessário; nunca em logs.
