# language: pt
@T-02 @local
Funcionalidade: SDKs Java e Angular com SFU real local
  Contrato da prova técnica; não representa autenticação definitiva do produto.
  Os cenários são documentais, sem step definitions Cucumber.
  JUnit e o botão da ferramenta executam verificações reais conforme tests/manual/T-02-livekit.md.

  Cenário: Administração real e grants mínimos
    Dado o LiveKit local com auto_create desabilitado
    Quando o gateway Java cria e consulta uma sala própria
    Então a SFU confirma seu nome e o limite de dez participantes
    E o token de apresentador permite somente screen_share e screen_share_audio
    E o token de espectador permite assinatura sem publicação
    E os tokens não possuem roomAdmin ou roomCreate e vencem em 120 segundos

  Cenário: SFU nega publicação de espectador sem depender do guard do cliente
    Dado um token VIEWER conectado à SFU real
    Quando o teste envia AddTrack diretamente pela sinalização
    Então a SFU responde NOT_ALLOWED para vídeo e áudio de compartilhamento
    E a consulta administrativa confirma zero tracks do espectador

  Cenário: A sala assinada no token prevalece sobre a solicitada
    Dado duas salas próprias A e B
    E um token emitido exclusivamente para A
    Quando o cliente tenta solicitar B na conexão usando esse token
    Então a SFU conecta a identidade somente em A
    E B não recebe aquela identidade

  Cenário: Exclusão impede recriação com o token ainda válido
    Dado uma identidade conectada na sala própria A
    Quando o Java exclui A
    Então a conexão recebe encerramento e a consulta confirma ausência de A
    Quando o mesmo token é reutilizado antes de vencer
    Então a SFU nega a conexão e A permanece ausente

  Cenário: Transporte sintético no Angular
    Dado o auxiliar local e a SFU em execução explícita
    Quando o operador inicia a prova de desenvolvimento
    Então apresentador e espectador conectam com identidades distintas
    E o espectador recebe bytes RTP crescentes de vídeo e áudio sintéticos
    E há quadros decodificados e sinal de áudio recebido
    E esse resultado não homologa áudio do Windows ou captura real

  Cenário: Auxiliar indisponível fora do desenvolvimento local
    Dado o artefato backend padrão ou o profile Spring prod
    Então a emissão auxiliar não está disponível
    E o build Angular de produção não inclui a ferramenta
    Dado o artefato de prova iniciado com flag e profile explícitos
    Quando uma requisição não atende loopback, Host, origem ou marcador exigidos
    Então o auxiliar rejeita a requisição sem emitir credenciais
