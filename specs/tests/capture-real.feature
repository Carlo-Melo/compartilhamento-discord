# language: pt
@t03 @experimental @sem_homologacao_windows
Funcionalidade: Prova restrita de captura real T-03
  Contrato auxiliar /api/dev/capture/session, fora da API definitiva.
  Estes cenários documentam comportamento; não têm step definitions.

  Cenário: Colar chave confirma o papel antes de iniciar qualquer captura
    Quando eu colar uma chave na página de apresentador ou espectador
    Então a página deve indicar validação em andamento após uma pausa curta
    E consultar GET /api/dev/capture/session/access com a chave somente no header
    E indicar chave válida, recusada, papel incorreto ou backend indisponível
    E nenhuma sala, token de mídia ou captura deve ser criado pela validação
    E editar a chave deve invalidar a confirmação anterior imediatamente
    E respostas atrasadas não devem validar outro valor
    E somente um novo clique após validar o papel correto pode iniciar a captura

  Cenário: Diagnóstico visível sem credenciais
    Dado que estou executando uma tentativa manual
    Então devo acompanhar seleção, conexão, publicação, confirmação e encerramento
    E falhas devem informar a etapa e uma ação de recuperação em português
    E erros HTTP devem apresentar o UUID de requisição quando disponível
    E falha de limpeza não deve esconder o erro original
    E posso copiar os últimos 40 avisos da página sem chaves, tokens ou mídia
    E o histórico é volátil e não é enviado ou persistido automaticamente
    E o log do backend contém somente ação, status HTTP e UUID de correlação

  Cenário: Captura exige gesto e consentimento nativo
    Dado que abri a página experimental de apresentador
    Então nenhum seletor deve abrir automaticamente
    Quando eu clicar em selecionar tela e iniciar
    Então o navegador deve solicitar uma fonte com vídeo e áudio
    E somente eu posso escolher a fonte e autorizar sua captura

  Esquema do Cenário: Falha de captura não cria uma transmissão
    Quando a seleção retornar <resultado>
    Então devo receber orientação acionável em português
    E todas as tracks retornadas devem ser liberadas
    E nenhuma sala deve ser criada
    Exemplos:
      | resultado                  |
      | cancelamento               |
      | permissão negada           |
      | vídeo ausente ou encerrado |
      | áudio ausente ou encerrado |

  Cenário: Confirmação da publicação depende da SFU
    Dado que o apresentador retornou vídeo e áudio vivos da captura
    Quando o SDK publicar screen_share e screen_share_audio
    Então a página deve aguardar consulta administrativa à SFU
    E só deve indicar transmissão após confirmar ambas fontes do apresentador esperado
    E uma track de áudio viva em silêncio não deve encerrar a captura

  Cenário: Parada durante uma operação pendente
    Dado que há seleção, criação, conexão ou publicação pendente
    Quando eu parar a prova
    Então resultados tardios não devem reativar a transmissão
    E recursos locais e sala criada devem ser liberados
    E iniciar outra prova deve permanecer bloqueado até terminar a limpeza

  Cenário: Encerrar uma fonte obrigatória encerra o par
    Dado que a transmissão foi confirmada
    Quando vídeo ou áudio terminar pelo controle nativo do navegador
    Então todas as tracks e a conexão local devem ser encerradas
    E a exclusão da sala deve ser confirmada ou ficar pendente com repetição visível

  Cenário: Espectador recebe somente as fontes autorizadas
    Dado que entrei com a chave experimental de espectador
    Então não posso criar, excluir ou publicar na sala
    E só devo assinar tela e áudio da identidade do apresentador fornecida pelo backend
    E devo poder liberar áudio por clique e sair sem apagar a sala

  Cenário: Acesso experimental não entra em produção
    Dado um artefato normal ou perfil prod ou production
    Então a API auxiliar de captura deve estar indisponível
    E o bundle de produção não deve conter as páginas nem clientes auxiliares
    E o proxy HTTPS nunca deve encaminhar administração SFU

  @manual @g1
  Cenário: Homologação real em duas máquinas Windows
    Dado HTTPS confiável e mídia roteável entre as duas máquinas
    Quando um operador autorizar monitor e áudio de sistema no Chrome ou Edge
    Então o receptor deve exibir vídeo e som de teste audível por 15 minutos por combinação
    E o relatório deve registrar versões, fonte, rede, duração e falhas
    E a T-03 permanece pendente enquanto essa evidência não existir
