# 02 — Fluxos, rotas e interface mínima
## F-01 — Vínculo e configuração
O operador configura a identidade da aplicação/bot e seus segredos fora do banco. Administrador instala o bot e executa /transmissao configurar canal:#transmissoes. A interação assinada e Manage Guild autorizam o backend a confirmar o bot no servidor e criar/validar o vínculo persistido.
O PostgreSQL guarda apenas bot e vínculo com guild. O canal de anúncios é configuração de ambiente do piloto ou ajuste em memória. /configurar informa quando a escolha só vale até reiniciar. Não criar campo de canal no banco nesta versão.

## F-02 — Apresentador sem login separado
1. Membro executa /compartilhar [titulo] no Discord.
2. Backend valida assinatura, vínculo bot–servidor, allowlist, associação e reserva em memória uma sessão CREATED.
3. Bot responde de forma privada com Abrir transmissão, contendo https://APP_ORIGIN/acesso#ticket={codigoAleatorio}. Ticket vale 60s e só pode ser consumido uma vez.
4. Angular lê o fragmento, remove-o imediatamente com replaceState, obtém CSRF e envia o código em POST /api/access/exchange. Não há cadastro ou redirecionamento de login.
5. Backend consome atomicamente o ticket e cria autorização web em memória, vinculada à interação original, usuário Discord, servidor, sessão e papel OWNER. Define cookie HttpOnly; a resposta traz o destino interno fixado pelo backend.
6. Página mostra aviso do áudio inteiro do computador, incluindo Discord/notificações. Usuário confirma ciência e clica Escolher tela.
7. Handler local chama getDisplayMedia antes de await de rede; seletor e autorização continuam obrigatórios.
8. Prévia silenciada; exigir track de vídeo e áudio vivas. Sem áudio, parar tudo e orientar nova escolha.
9. Clique Iniciar solicita lease/token, conecta e publica. Só após confirmação das duas tracks pela SFU o backend muda para LIVE e o bot anuncia.
10. Encerrar para tracks localmente e solicita limpeza idempotente. Refresh exige nova captura; não reabrir seletor ou captura automaticamente.

Ticket vencido, usado ou perdido: voltar à interação privada ou executar /transmissao status para gerar novo acesso. O ticket não é renovado pelo site sem passar pelo bot.

## F-03 — Espectador
1. Na mensagem pública, clicar Assistir, um botão custom_id que envia interaction ao bot.
2. Bot valida membro, servidor e sessão e responde só àquela pessoa com o botão Abrir transmissão.
3. O botão privado abre /acesso#ticket=..., que troca o ticket e encaminha a /s/{id}. Esta passagem pode exigir dois cliques no Discord; botão de link público não fornece identidade.
4. Página mostra estado/vagas; clique Assistir conecta com grant somente de assinatura.
5. Autoplay bloqueado mostra Ativar áudio; não solicita câmera, microfone ou captura ao espectador.
Acesso a /s/{id} sem autorização web mostra Volte ao Discord e clique em Assistir, sem conteúdo privado.
Uma nova aba do mesmo contexto/usuário não ocupa uma segunda vaga; identidade de mídia estável por usuário e geração. Novo acesso do mesmo usuário à mesma sessão substitui o anterior.

## F-04 — Falhas e reinício
- Cancelar seletor mantém preparação, sem publicação.
- Ausência de track de áudio bloqueia início; silêncio com track viva não bloqueia.
- Fonte/áudio termina: parar ambas as tracks e encerrar com SOURCE_ENDED/AUDIO_LOST.
- Rede perdida: reconectar por até 30s com autorização válida; depois parar captura e encerrar.
- Backend reiniciou ou autorização expirou: parar transporte/captura e retornar ao bot para novo acesso; não pedir login.
- Associação não verificável por mais de 60s: cortar acesso e orientar nova tentativa no bot.
- Sala lotada: não emitir token de mídia, mostrar Atualizar e manter espectadores existentes.
- Banco indisponível: não criar/alterar vínculo ou emitir acessos novos sem vínculo verificável. Contextos existentes usam o vínculo validado por até 60s; depois interromper se não houver verificação.
- Fila em memória e IDs de mensagens se perdem no reinício. Botão antigo é respondido com sessão encerrada; não prometer correção automática do texto de mensagens antigas.

## Rotas e controles
| Rota | Conteúdo |
|---|---|
| / | Instrução para usar o bot no Discord; sem botão de cadastro/login |
| /acesso | Consumo do ticket privado; nenhum conteúdo ou segredo em GET |
| /acesso-invalido | Ticket usado/vencido/inválido; instrução para voltar ao bot |
| /s/:id/transmitir | Estado, aviso de áudio, seleção, prévia, iniciar e encerrar |
| /s/:id | Estado, vagas, player, volume, ativar áudio, tela cheia e sair |
| /configuracao-pendente | Administrador precisa configurar vínculo/canal |
| /nao-suportado | Requisitos e navegador/fonte não homologados |
| /acesso-negado | Sem nome, título ou miniatura |

## UI mínima
HTML semântico, foco visível, labels, aria-live, contraste e mensagens em pt-BR. Botões evitam dupla operação. Vídeo com object-fit:contain. A evolução visual autorizada em 27/10/2026 segue a [spec 15 — Design system](15-design-system.md), com apresentação comum para saúde, ferramentas experimentais e futuras páginas. Não altera os fluxos nem cria rotas definitivas ainda pendentes.
Indicadores distinguem track disponível, autoplay bloqueado e track perdida. Volume é apenas local ao espectador. O mockup anterior é referência; Activity não faz parte do MVP.
