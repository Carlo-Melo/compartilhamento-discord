# language: pt
Funcionalidade: Compartilhamento de tela e áudio organizado por bot Discord
  O bot organiza a sessão e o site transmite; a conversa de voz permanece no Discord.
  Estes cenários ainda precisam de step definitions e execução real.

  @AC-01
  Cenário: Configuração administrativa e permissões mínimas
    Dado um servidor habilitado com o bot instalado
    Quando um administrador configura um canal de texto do próprio servidor
    Então a configuração é salva somente se o bot puder ver, enviar e incorporar links
    E um membro sem Manage Guild não pode alterar esse canal

  @AC-02
  Cenário: Criar sessão sem anunciar transmissão inexistente
    Dado que não existe sessão aberta no servidor
    Quando um membro executa /compartilhar
    Então recebe um link privado de apresentador para uma única sessão CREATED
    E nenhuma mensagem pública de AO VIVO é criada

  @AC-03
  Cenário: Acesso pelo bot sem login no site
    Dado um ticket OWNER privado emitido por uma interação válida no Discord
    Quando o navegador o troca por POST com CSRF e mesma origem antes de sessenta segundos
    Então recebe contexto em memória restrito ao bot, servidor, sessão e papel originais
    E o site não solicita cadastro, senha ou autorização OAuth de usuário
    E o fragmento contendo o ticket é removido da URL

  @AC-04
  Esquema do Cenário: URL pública não substitui acesso concedido pelo bot
    Dado um visitante na condição "<condicao>"
    Quando solicita conteúdo ou token de mídia de uma sessão
    Então a operação é negada sem revelar título, participantes ou credenciais

    Exemplos:
      | condicao                                      |
      | sem contexto e sem ticket privado              |
      | contexto de outro servidor ou bot              |
      | servidor fora da allowlist                     |
      | vínculo bot-servidor inexistente               |
      | ticket expirado ou emitido em boot anterior    |

  @AC-05
  Cenário: Somente o criador pode publicar
    Dado uma sessão criada por uma pessoa
    Quando outro membro ou um administrador tenta obter permissão de publicação
    Então a API nega a operação
    E o grant de espectador não permite publicar vídeo, áudio, câmera ou dados

  @AC-06
  Cenário: Captura começa somente com escolha explícita
    Dado o apresentador com contexto autorizado pelo bot na página de preparação
    Quando a página é carregada
    Então não existe captura ativa
    Quando clica em Escolher tela
    Então o navegador apresenta o seletor nativo
    E cancelar não inicia publicação

  @AC-07 @manual
  Cenário: Transmissão real de monitor e áudio do computador
    Dado dois computadores Windows com navegador e versão homologados
    E uma fonte local de vídeo e som de teste, sem usar microfone
    Quando o apresentador compartilha monitor e áudio do sistema
    Então o espectador recebe imagem em movimento e som audível por quinze minutos
    E o relatório identifica sistema, navegador, rede e versões da mídia

  @AC-08
  Cenário: Fonte sem track de áudio não passa como sucesso
    Dado que o seletor retornou vídeo mas nenhuma track de áudio
    Quando o apresentador tenta prosseguir
    Então as tracks selecionadas são paradas
    E aparece orientação para escolher uma fonte com áudio
    E a sessão não fica LIVE nem gera anúncio público

  @AC-09
  Cenário: Confirmação de live vem da SFU
    Dado uma sessão STARTING
    Quando o navegador declara que iniciou mas a SFU confirma apenas vídeo
    Então a sessão continua sem anúncio de AO VIVO
    Quando a SFU confirma vídeo e áudio do dono na geração atual
    Então a sessão fica LIVE e o anúncio é solicitado pela outbox

  @AC-10
  Cenário: Assistir pelo link do bot
    Dado uma transmissão LIVE e um membro com acesso emitido pelo bot
    Quando clica no botão interativo Assistir e abre o link privado recebido
    Então a página conecta à sessão correta sem instalar aplicativo
    E oferece volume, tela cheia e saída
    E se autoplay bloquear som há um botão Ativar áudio

  @AC-11
  Cenário: Última vaga e criação concorrentes
    Dado oito espectadores conectados e uma vaga disponível
    Quando dois usuários distintos tentam entrar simultaneamente
    Então apenas um deles obtém a última reserva
    E nunca há mais de nove espectadores
    E comandos simultâneos de criação não geram duas sessões abertas no servidor

  @AC-12
  Cenário: Contagem considera pessoas conectadas
    Dado um apresentador, dois espectadores e uma reserva ainda não conectada
    Quando a SFU e o backend reconciliam participantes
    Então a contagem de espectadores é dois
    E outra aba da mesma conta substitui a conexão sem contar pessoa adicional
    E a saída de uma conexão antiga não apaga a conexão nova

  @AC-13
  Cenário: Recuperar queda breve e parar após o prazo
    Dado uma sessão LIVE com as duas tracks
    Quando a rede cai e volta em menos de trinta segundos
    Então a reconexão exige autorização ainda válida e restaura as tracks
    Quando a conexão não se recupera no prazo de trinta segundos
    Então a sessão é encerrada e as tracks locais são paradas

  @AC-14
  Esquema do Cenário: Encerrar por qualquer controle autorizado
    Dado uma transmissão LIVE com espectadores
    Quando o apresentador encerra por "<controle>"
    Então a captura local é parada quando o cliente recebe a instrução ou perde seu lease
    E a sala é excluída, não pode ser recriada automaticamente e o anúncio é atualizado
    E repetir a operação não cria outra sala

    Exemplos:
      | controle               |
      | botão Encerrar do site |
      | controle do navegador  |
      | comando do bot         |

  @AC-15
  Cenário: Perda de áudio ou fonte não mantém live falsa
    Dado uma sessão LIVE com vídeo e áudio
    Quando a track de áudio termina de verdade
    Então a publicação de vídeo também é interrompida e a sessão termina por AUDIO_LOST
    E silêncio de uma track viva não é tratado como áudio perdido

  @AC-16
  Cenário: Perder associação ao servidor remove acesso
    Dado um espectador autorizado assistindo
    Quando deixa o servidor e a ausência é observada
    Então não recebe novos tokens
    E seu acesso à mídia é removido dentro do prazo de sessenta segundos
    E os membros válidos migram para nova geração sem reutilizar a sala antiga
    E indisponibilidade de verificação além do prazo também interrompe o acesso

  @AC-17
  Cenário: Status e encerramento respeitam papel
    Dado uma sessão no servidor
    Quando um membro consulta status
    Então recebe resposta privada sobre essa sessão
    Quando um espectador tenta encerrar
    Então a operação é negada
    E o criador ou administrador autorizado consegue encerrar

  @AC-18
  Cenário: Integrar sem aceitar falsificação ou regredir estado
    Dado endpoints de integração que validam assinaturas nos bytes originais
    Quando chega assinatura inválida, evento duplicado ou evento antigo
    Então nenhuma sessão é criada em duplicidade ou reaberta indevidamente
    E falhas 429 respeitam Retry-After sem flood
    E uma interação legítima recebe ACK antes de três segundos

  @AC-19
  Cenário: Segurança da aplicação e ausência de gravação
    Dado um contexto temporário autorizado pelo bot no navegador
    Quando uma origem externa tenta trocar ticket ou executar mutação sem CSRF
    Então a operação é negada
    E tokens de bot e segredos não aparecem em bundle, URL, logs ou snapshot
    E tickets temporários aparecem somente no link privado e são removidos ao abrir
    E não existem egress de gravação, mídia persistida ou câmera e microfone habilitados

  @AC-20
  Cenário: Reinício mantém vínculos mas invalida todos os acessos operacionais
    Dado um vínculo bot-servidor persistido, uma sala ativa e tickets e contextos em memória
    Quando o backend reinicia
    Então o vínculo continua no PostgreSQL
    E os tickets e contextos antigos não autorizam acesso
    E as salas antigas do namespace são excluídas antes de readiness
    E os usuários precisam voltar ao bot para iniciar novamente
    E clicar no botão de uma transmissão antiga não restaura sua sessão

  @AC-19 @T-05
  Cenário: Base nega acesso enquanto o contexto definitivo não existe
    Dado o backend normal sem autenticação definitiva implementada
    Quando um visitante solicita uma rota privada com identidade e papel inventados em headers
    Então recebe 401 com code ACCESS_REQUIRED
    E a resposta tem code, message em português, requestId próprio e retryable false
    E não recebe formulário de login, redirect ou desafio HTTP Basic
    E o header X-Request-Id enviado pelo visitante não é refletido

  @AC-19 @T-05
  Cenário: Bootstrap CSRF não cria contexto autorizado
    Dado uma sessão técnica obtida por GET /api/csrf
    Quando a mutação usa Origin exata e X-XSRF-TOKEN vinculado a essa sessão
    Então a verificação CSRF é satisfeita
    E a operação do produto continua negada sem contexto definitivo
    Quando a mesma mutação usa token de outro bootstrap ou Origin externa
    Então recebe 403 padronizado sem executar a operação
    E CORS não autoriza a origem externa

  @AC-19 @T-05
  Cenário: Readiness e detalhes internos não são públicos
    Dado as interfaces de API e gestão em loopback
    Quando consulto GET /health/live na API
    Então recebo somente status de liveness sem detalhes
    E GET /health/ready é acessível somente na porta da gestão
    E env, configprops, heapdump e demais métodos e caminhos da gestão são negados

  @AC-19 @T-05
  Cenário: Segurança preserva isolamento e papéis das ferramentas experimentais
    Dado os artefatos normal, livekit-proof e capture-proof separados
    Quando uso o artefato normal, opt-in desligado ou profile prod ou production
    Então as rotas experimentais permanecem ausentes com 404
    Quando uso um auxiliar explicitamente habilitado
    Então só os métodos e caminhos declarados atravessam sua cadeia específica
    E Host, Origin, loopback e marcador continuam exigidos
    E espectador da T-03 não pode criar ou excluir sala
    E nenhuma rota de integração futura é liberada antes de verificar assinatura

  @AC-21 @performance
  Cenário: Capacidade e consumo observáveis
    Dado um apresentador e nove espectadores durante duas horas
    Quando a carga roda no servidor e configuração candidatos ao piloto
    Então são registrados CPU, memória, perdas, bitrate e saída mensal projetada
    E o custo total apresentado cabe em duzentos reais por mês no cenário aprovado
    E a evidência não contém conteúdo de tela ou conversa pessoal

  @AC-22
  Cenário: Interface mínima acessível e acionável
    Dado um usuário navegando somente por teclado
    Quando percorre preparação, erro e player
    Então encontra foco visível, nomes de controles e mensagens em português
    E o estado de transmissão não depende apenas de cor ou spinner

  @AC-23
  Cenário: Token antigo não reabre mídia aposentada
    Dado tokens originais e renovados de uma geração de sala
    Quando o backend encerra ou rotaciona essa geração e confirma exclusão da sala
    Então tentativas com esses tokens não recriam a sala porque auto_create está desabilitado
    E nenhum token antigo pode assinar ou publicar na nova geração
    E a reconexão legítima recebe uma credencial diferente após reautorização

  @AC-24
  Cenário: Banco armazena somente identificação do bot e relação com servidor
    Dado o schema de produto com discord_bot e bot_guild_binding
    Quando o bot é configurado para uma guild autorizada
    Então o vínculo é salvo sem duplicatas e sem token ou dado de usuário
    Quando usuários transmitem, assistem e saem
    Então não são criados registros de usuário, sessão, acesso, transmissão, participante ou fila
    E o backup contém somente os vínculos e metadados técnicos de schema

  @AC-25
  Cenário: Ticket tem uso único atômico e escopo fixado na interação
    Dado um ticket VIEWER privado ainda válido
    Quando dois navegadores tentam consumi-lo simultaneamente
    Então no máximo uma troca tem sucesso
    E nenhum cliente pode trocar o servidor, sessão, bot ou papel para OWNER
    E um ticket com mais de sessenta segundos é negado
    E o teste documenta que quem possuir um ticket repassado antes do consumo pode ser o primeiro a usá-lo
