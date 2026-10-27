# language: pt
@t04 @experimental @g2_pendente
Funcionalidade: Prova restrita de nove espectadores, TURN e gerações de mídia
  Contrato auxiliar capture-proof; não implementa /join ou identidade Discord.
  Cenários documentais sem step definitions; resultados em tests/manual/T-04-multiespectador-turn.md.

  Cenário: Nove espectadores possuem identidades distintas emitidas pelo backend
    Dado uma sala criada pelo apresentador com max_participants igual a dez
    E nove tentativas de espectador com correlações UUID v4 diferentes somente em memória
    Quando os espectadores obtêm acesso pela rota experimental protegida
    Então cada correlação recebe uma identidade de mídia distinta gerada pelo backend
    E nenhum visitante escolhe sala, papel ou identidade de mídia
    E a SFU permite apenas assinatura aos espectadores
    E a décima correlação recebe 409 CAPTURE_PROOF_CAPACITY

  Cenário: Corrida pela última vaga
    Dado oito correlações de espectador já emitidas nesta geração
    Quando duas novas correlações pedem a última vaga concorrentemente
    Então exatamente uma recebe a nona identidade
    E a outra recebe a recusa de capacidade
    E o estado nunca excede nove correlações

  Cenário: Repetição e saída preservam o limite de credenciais da geração
    Dado uma correlação que já recebeu uma identidade de espectador
    Quando essa correlação repete a entrada após falha ou saída
    Então recebe a mesma identidade sem consumir outra vaga
    E uma conexão com a mesma identidade substitui a anterior na SFU
    Quando o espectador solicita DELETE na rota viewer com sua correlação
    Então apenas seu participante é removido da SFU
    E nenhuma sala é excluída pelo espectador
    E a vaga permanece atribuída até o fim da geração
    E essa saída não é anunciada como revogação de JWT original ou renovado

  Cenário: Contagem distingue conexões de credenciais emitidas
    Dado nove vagas emitidas e apenas dois espectadores presentes na SFU
    Quando consulto o estado da prova
    Então viewerCount é dois sem contar apresentador, reservas ou identidades estranhas
    E viewerSlotsUsed é nove
    E falha de consulta SFU não produz uma contagem confirmada

  Cenário: Estado expirado exige limpeza remota confirmada
    Dado uma geração experimental com prazo absoluto de uma hora
    Quando o prazo expira
    Então novos acessos são negados
    E a rotina experimental tenta excluir a sala e confirmar ausência
    E falha de limpeza preserva o nome fechado para repetição
    E outra sala nunca reutiliza o nome anterior

  Cenário: Rotas novas preservam as barreiras experimentais
    Dado a emissão e a saída de espectador
    Então métodos e caminhos são autorizados explicitamente
    E exigem chave VIEWER, marcador, Host, origem e transporte da prova
    E correlação ausente ou inválida não emite identidade
    E não ficam disponíveis no artefato normal, sem opt-in ou em prod e production
    E a política de produto continua negando acesso sem contexto definitivo

  @manual @rede
  Cenário: Relay é comprovado pelo par selecionado e pela mídia recebida
    Dado TURN TLS opt-in com domínio e certificado confiável preparados pelo operador
    E sinalização HTTPS e WSS protegidas da prova
    Quando conecto com política ICE relay
    Então o par ICE selecionado contém candidato relay
    E registro somente tipos de candidatos, protocolo e relayProtocol sem IP ou SDP
    E cada espectador recebe RTP crescente, vídeo decodificado e áudio de teste
    E repetir entre redes distintas com UDP bloqueado exige relay TLS sobre TCP
    E sucesso apenas de sinalização não aprova o cenário

  @manual @sfu_real
  Cenário: Geração anterior não acessa a nova transmissão
    Dado tokens iniciais e um token renovado recebido realmente da SFU pelo SDK
    E uma tentativa de espectador de publicar negada explicitamente pela SFU
    Quando o apresentador exclui a sala A
    Então todos os participantes são desconectados e A fica ausente
    E tokens iniciais e renovado ainda válidos não recriam A
    Quando uma nova transmissão é criada em B com nome e identidades diferentes
    Então tokens de A não acessam B nem recebem sua mídia
    E B continua recebendo vídeo e áudio somente nas identidades autorizadas
    E gerar outro JWT no gateway não conta como renovação real
