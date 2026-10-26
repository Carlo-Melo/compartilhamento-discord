# 03 — Captura e distribuição de mídia
Status: especificação de comportamento. LiveKit self-hosted é a solução técnica proposta; não homologada.

## Contrato de captura
- Captura somente no Windows em Chrome/Edge homologados. Registrar versões exatas na POC; não inferir suporte pelo user-agent.
- HTTPS em ambiente compartilhado; localhost apenas no desenvolvimento suportado pelo navegador.
- getDisplayMedia após clique explícito, video habilitado e audio solicitado. Não pedir câmera/microfone.
- Oferecer tela inteira com áudio do sistema. Aba com áudio pode ser alternativa voluntária, sem substituir o requisito de som do computador na homologação.
- Opções como systemAudio/include e windowAudio são sugestões ao navegador, não garantia de retorno de áudio. Inspecionar as tracks retornadas.
- Prévia local silenciada para evitar retorno. Parar todas as tracks da captura anterior antes de iniciar outra.
- Exigir uma track de vídeo e uma de áudio vivas, com origem autorizada. Cancelamento não inicia sessão nem anuncia live.
- Não usar volume acima de zero como pré-condição: o jogo pode estar em silêncio. Confirmar som audível com conteúdo de teste separado.
- Perda da track de vídeo ou áudio durante LIVE encerra a sessão. Silêncio com track viva não encerra.
- Aviso explícito de que áudio de sistema inclui Discord e notificações. Usar fones reduz retorno acústico, mas não remove a call da captura; nunca prometer isolamento do jogo.

## Perfil de publicação
Padrão 720p, 30 fps, alvo inicial 2 Mbps; opcional 1080p, 30 fps, teto inicial 4 Mbps. Áudio Opus até 128 kbps. São limites/objetivos de negociação, não garantia de qualidade constante. Provar codec inicial VP8 + Opus e testar fallback H.264 se necessário. Não criar simulcast/transcodificação no servidor no piloto. Revisitar se heterogeneidade de rede impedir assistir.
Encoder fica no navegador do apresentador. Um upload para a SFU; fan-out para espectadores. Spring Boot não recebe frames, SDP para transcodificar, arquivos ou áudio.

## Sala e grants
- Nome de sala não reutilizável: session UUID + geração aleatória. Só backend cria salas.
- Configuração obrigatória da SFU: room.auto_create=false; max_participants=10; sem egress/gravação; API administrativa inacessível pelo proxy público.
- Apresentador: publicar apenas screen_share e screen_share_audio; não assinar outros participantes; não publicar dados; sem roomAdmin/roomCreate.
- Espectador: canSubscribe=true, canPublish=false, canPublishData=false; sem grants administrativos.
- Identidade de mídia estável por usuário/papel/geração; uma conexão por identidade Discord recebida na interaction e contexto temporário. Nova aba substitui a conexão anterior.
- Backend confere contexto temporário emitido pelo bot, vínculo bot–servidor e associação via REST do bot antes de emitir qualquer token. TTL inicial de emissão: 120 segundos; não confundir expiração com desconexão de uma conexão já aberta.
- SDK/servidor podem renovar tokens. Em self-hosted, remoção não garante revogação de tokens já emitidos. O mecanismo de encerramento é excluir a sala, impedir recriação automática e nunca reutilizar seu nome. Na remoção de espectador, remover participante e migrar os autorizados para uma nova geração, excluindo a antiga. Provar isso na POC-02.

## Confirmação e reconexão
STARTING vira LIVE apenas quando a API/evento verificado da SFU confirmar vídeo e áudio vivos do apresentador esperado na geração atual. Cliente não pode declarar LIVE.
ListParticipants reconcilia fatos quando webhook atrasar ou chegar fora de ordem.
Ao perder transporte, ir para RECONNECTING, manter captura por até 30s e tentar SDK com backoff; token novo só após autorização no backend. Sem recuperação no limite, encerrar e parar tracks.
Em rotação de geração, disconnect da sala antiga e republicar as tracks ainda autorizadas na nova. Não invocar seletor automaticamente se a captura tiver terminado.

## Reprodução
Usar SDK browser no Angular, sem depender de componentes React. Assinar só vídeo/áudio do apresentador validado. Vídeo inline com object-fit:contain; áudio ligado após gesto quando autoplay bloquear. Volume local 0–100, mute local, tela cheia. Sem seek/download/replay. Aplicar limite de buffering e recuperação do SDK; mostrar Reconectando em vez de congelar silenciosamente.
Contagem vem de participantes espectadores confirmados pela SFU, deduplicados por identidade, sem reservas nem apresentador.
Não capturar automaticamente miniatura: cartão Discord usa imagem genérica/sem imagem no MVP.

## POC-01: condição de avanço
Em duas máquinas reais, homologar monitor + áudio de sistema, reproduzir som de teste e vídeo por 15min; parar pelo site e pelo navegador; testar Chrome e Edge. Se não houver som disponível em uma combinação, registrar como não suportada, não anunciar sucesso e pedir decisão sobre restrição ou app auxiliar.

## Estado volátil
Tokens, leases e associação com a sala ficam em memória. Em reinício, a aplicação elimina salas antigas do namespace antes de aceitar novos acessos; vínculo persistido no banco não restaura mídia. O usuário solicita novo ticket ao bot. A permissão nativa para escolher/capturar tela continua necessária.
