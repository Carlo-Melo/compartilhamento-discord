# T-03 — captura real de tela e áudio de sistema

Data: **26/10/2026**. **IMPLEMENTADA; NÃO HOMOLOGADA.** G1/POC-01 e conclusão da T-03 permanecem pendentes. O usuário pediu registrar as decisões externas para resolver depois e avançar o desenvolvimento independente. Não houve seleção/consentimento de captura pelo agente, teste real de Windows ou transmissão entre dois computadores nesta entrega.

## Entrega implementada

- Páginas Angular distintas de apresentador e espectador, somente no build `capture-proof`. Clique explícito chama `getDisplayMedia` com vídeo/áudio e sugestões de áudio de sistema; o navegador e o usuário decidem a fonte. Prévia silenciada, mensagens em português e validação de tracks vivas, sem medir volume para autorizar publicação.
- Liberação de todas as tracks em falha/cancelamento/resultado tardio/parada/navegação. Término de vídeo **ou** áudio encerra o par; nova tentativa fica bloqueada enquanto há operação/limpeza pendente. Não há câmera, microfone ou gravação.
- Publicação LiveKit `screen_share` + `screen_share_audio`; VP8/Opus, sem simulcast. Padrão 720p/30 fps/alvo 2 Mbps, opção 1080p/30 fps/teto 4 Mbps, áudio até 128 kbps. São sugestões e limites de negociação, sem promessa de qualidade constante.
- Estado de transmissão exige consulta administrativa Java à SFU confirmar ambas fontes do apresentador esperado. Reconexão por até 30s, com nova confirmação ou encerramento. Confirmação de publicação não comprova som audível nem captura real do Windows.
- Espectador assina somente fontes de tela/áudio da identidade autorizada, recebe player separado, botão de áudio por gesto, volume, tela cheia e saída. Contadores de RTP/quadros ajudam diagnosticar; não substituem ouvir o som.
- Backend auxiliar separado por fontes, profile Maven/Spring e opt-in. Chaves experimentais por papel, origem HTTPS privada exata, backend/administração SFU em loopback; uma sala e duas identidades fixas por execução de sala. Máximo 32 salas por processo. [Contrato auxiliar](../../specs/api/capture-proof.openapi.json).
- Compose SFU, proxy e scripts T-03 separados da prova local T-02. Nenhuma dependência nova, tabela, bot ou ticket definitivo foi adicionado. Nenhum serviço pago, conta, publicação, regra de firewall ou confiança de certificado foi criado.

## Ambiente de desenvolvimento e verificações aprovadas

Windows/PowerShell do workspace; runtimes portáteis já existentes Java Temurin **21.0.12.1+1**, Node **24.22.1** e npm **11.19.1**. Spring Boot **4.1.2**, Angular **22.2.3**, CLI **22.2.4**, TypeScript **6.0.3** e LiveKit SDK JVM **0.16.0** / browser **2.22.3** preservados. SFU configurada para LiveKit **1.13.10** com mesmo digest fixado da T-02. Versões exatas do Windows e dos navegadores para homologação ainda precisam ser registradas nos dois computadores.

| Verificação | Resultado desta entrega | Limite da evidência |
|---|---|---|
| Ciclo de vida frontend | **45 testes aprovados**: tracks obrigatórias, erros sanitizados, ended, silêncio, extras, stop idempotente, seletores tardios/concorrentes, publicação parcial, confirmação SFU, cleanup/retry, saída de espectador e reconexão 30s; rejeição4xx não exclui sala de tentativa anterior | Doubles de captura/rede/SDK; não é captura do Windows |
| Proxy T-03 | **7 testes aprovados**: caminhos limitados, Host/Origin, sanitização; integração Vite real em loopback com upstream falso | Sem TLS confiável, SFU ou mídia real |
| Setup PowerShell | **13 verificações aprovadas**: parser, geração, não sobrescrita, IPs inválidos, importação sem executar conteúdo e sem aplicação parcial, restauração de variáveis presentes/ausentes | Sem configuração real da LAN |
| Compose T-03 | `config --quiet` aprovado com exemplo fictício | Não iniciou listener LAN, não comprova Docker/ICE |
| Backend `capture-proof` | `clean verify`: **13 testes, 11 aprovados, 2 SFU pulados** na execução inicial | Oito testes novos de regra/segurança usam adapter fake; três existentes de assinatura/grants/configuração; SFU real opt-in |
| Backend normal e `livekit-proof` | `clean verify`: **5 testes cada, 3 aprovados, 2 SFU pulados** na execução inicial | Preserva testes e isolamento T-02; não revalida RTP |
| Empacotamento Java | JAR normal sem auxiliares; T-02 sem classes T-03; T-03 sem classes T-02 | Inspeção dos três JARs |
| HTTP backend em loopback | Normal404; auxiliar sem opt-in404; `capture-proof,prod`404 e `capture-proof,production`404; sem chave403; consulta de espectador válida200; DELETE espectador403; DELETE apresentador sem sala204; Origin externa403 | Processos temporários com credenciais de teste somente em memória, sem SFU |
| Angular | Builds `capture-proof`, `development` e `production` aprovados; bundle production sem rota/ferramenta/SDK T-02/T-03 | Checagem TypeScript/templates/empacotamento |

As evidências SFU/RTP sintéticas anteriores da [T-02](T-02-livekit.md) pertencem àquela execução. Não são apresentadas como homologação desta captura. Relatórios temporários/cache/build ficam em caminhos ignorados, sem credenciais incluídas na documentação.

## Falhas e limitações observadas

- O PATH inicial apontava Java17/Node22; foram usados os runtimes Java21/Node24 existentes em `work/tools`, sem mudar instalação global. Uma primeira tentativa de build Angular com caminho relativo errado recusou Node22; a execução com Node24 passou.
- O sandbox bloqueou listeners/conexões de loopback em algumas verificações; os testes locais de proxy/processo que precisaram foram executados após aprovação automática da ferramenta, sem exposição LAN.
- Docker Desktop estava sem daemon Linux disponível. Iniciar o aplicativo existente em segundo plano não disponibilizou o daemon durante as verificações; por isso os dois testes SFU reais foram **pulados nesta rodada**. Nenhuma configuração Docker foi alterada. Consultar saúde/build não substitui essa execução pendente.
- O aplicativo Docker Desktop pode permanecer aberto após essa tentativa local; não havia registro de PIDs anterior para distinguir com segurança processos preexistentes. Não foram encerrados processos por nome. Todos os processos Java temporários e o servidor Angular de teste foram encerrados, e não foi criada configuração definitiva `.env.capture`/YAML LAN.
- A inspeção visual das páginas em navegador automatizado não foi executada: não havia navegador controlável disponível. O servidor temporário de UI ficou apenas no loopback e foi encerrado; TypeScript/templates foram verificados pelo build, sem interação com seletor nativo.
- Nenhum teste de mídia real falhou porque **não foi executado**. Não há evidência para restringir compatibilidade, retirar áudio ou adotar aplicativo auxiliar.

## Decisões e ações externas adiadas para o usuário

1. Disponibilizar dois computadores Windows; identificar qual hospedará SFU/backend/frontend e quem operará cada máquina. Registrar versões exatas de Windows/build, Chrome/build e Edge/build. Windows11 é a matriz inicial; Windows10 só entra se disponível e explicitamente incluído.
2. Definir rede e endereço IPv4 privado estável da máquina SFU. A preparação entregue atende LAN privada/rota privada já existente, com IP RFC1918 explícito. Redes diferentes sem essa rota exigem decisão adicional; não foi escolhido VPN, fornecedor ou serviço TURN.
3. Providenciar certificado PEM/chave local para HTTPS com SAN **IP** correspondente ao endereço escolhido e cadeia confiável nas **duas máquinas**. Definir como administrar a autoridade/certificado e instalar a confiança. Não ignorar alertas TLS nem usar flags inseguras; a chave privada permanece apenas no host.
4. Autorizar/configurar, se necessárias, regras de firewall limitadas às duas máquinas: TCP4443 HTTPS/WSS, TCP7891 ICE e UDP7892 ICE. Conferir Docker Desktop/Linux, rota e ausência de isolamento de Wi-Fi. Nenhuma alteração automática de firewall ou roteador foi feita; acesso pela internet/abertura de roteador não faz parte da preparação entregue.
5. Reservar operador e pelo menos **15 minutos por combinação** da matriz abaixo; preparar tela dinâmica e som de teste, sem conversa privada, com Discord desktop aberto e fones. Só o operador seleciona/autoriza captura. Compartilhamento do áudio inteiro pode incluir vozes e notificações.
6. Informar localmente as chaves experimentais nos campos do papel e entregar somente a de espectador ao segundo operador por meio privado já disponível. Não enviar segredos ao agente ou à conversa; não copiar `.env.capture` para a segunda máquina.

## Como iniciar e usar depois das decisões

Com Java21/Node24/Docker Desktop Linux disponíveis, seguir [infra/T-03](../../infra/README.md#t-03--prova-restrita-em-duas-máquinas), que detalha certificados, portas e conectividade. Na raiz, substitua o IPv4 fictício pela interface real:

```powershell
# Apenas uma vez; preserva arquivos existentes:
.\scripts\Initialize-CaptureProof.ps1 -LanIp '192.168.1.50'
. .\scripts\Import-CaptureEnvironment.ps1
. .\scripts\Import-DevEnvironment.ps1
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml config --quiet
docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml up -d --wait
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
java -jar .\backend\target-capture-proof\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=capture-proof --livekit.enabled=true --capture.proof.enabled=true
```

Em outro terminal na raiz, com dependências frontend instaladas (`cd frontend`, `npm.cmd ci`, voltar à raiz), certificado/chave já emitidos:

```powershell
.\scripts\Start-CaptureProofFrontend.ps1 -CertificatePath 'C:\certificados\capture-cert.pem' -PrivateKeyPath 'C:\certificados\capture-key.pem'
```

Abrir no apresentador `https://<IPv4-privado>:4443/dev/capture/broadcast`, sem alerta TLS e com contexto seguro indicado. Informar a chave de apresentador, selecionar qualidade e clicar **Selecionar tela e iniciar**. O operador escolhe monitor/Tela inteira e habilita compartilhar áudio do sistema. Se não retornar áudio, a tentativa deve falhar e liberar o vídeo; registrar a ausência antes de qualquer decisão de compatibilidade.

No segundo computador, abrir a mesma origem em `/dev/capture/watch`, informar somente a chave de espectador e clicar **Entrar como espectador**. Se necessário, **Ouvir áudio**. Verificar movimento de vídeo e som audível e iniciar cronômetro de 15min **após** ambas fontes confirmadas. Nenhum token/segredo deve ser incluído em screenshot, URL compartilhada, log ou relatório; logs do SDK e diagnóstico verboso do proxy ficam desativados.

Ao terminar: **Sair** no espectador, **Parar transmissão** no apresentador; confirmar exclusão ou usar **Repetir limpeza**. Se fechou a página/limpeza incerta, usar o DELETE com chave em memória documentado no [backend](../../backend/README.md#t-03--auxiliar-restrito-para-captura-real). Só então Ctrl+C nos processos e `docker compose --env-file .env.capture -f infra/docker/compose.capture.yaml down`. Não usar `--volumes`. Após reinício abrupto do backend, recriar essa SFU descartável antes de nova prova; limpeza automática do produto antes de readiness é T-19.

## Matriz real a executar — nenhum resultado preenchido por inferência

| Apresentador A | Receptor B | Windows/build A e B | Fonte/opção áudio | Rede/hardware | Duração | Vídeo + som audível | Resultado |
|---|---|---|---|---|---|---|---|
| Chrome (versão a registrar) | Chrome (versão a registrar) | A registrar | Monitor + áudio inteiro | A registrar | ≥15min | Não testado | **NÃO EXECUTADO** |
| Chrome (versão a registrar) | Edge (versão a registrar) | A registrar | Monitor + áudio inteiro | A registrar | ≥15min | Não testado | **NÃO EXECUTADO** |
| Edge (versão a registrar) | Chrome (versão a registrar) | A registrar | Monitor + áudio inteiro | A registrar | ≥15min | Não testado | **NÃO EXECUTADO** |
| Edge (versão a registrar) | Edge (versão a registrar) | A registrar | Monitor + áudio inteiro | A registrar | ≥15min | Não testado | **NÃO EXECUTADO** |

Para cada linha, registrar data/operadores, versões exatas, CPU/memória, monitor/resolução, captura escolhida, áudio marcado, tipo de rede, interrupções, codecs negociados se observáveis sem exportar SDP/credenciais, duração medida e observação do som no receptor. Aba com áudio é teste adicional voluntário; não substitui a homologação de áudio do computador. Não gravar conversa ou mídia pessoal como evidência.

## Falhas e encerramentos reais a exercitar por navegador apresentador

| Ação humana | Resultado exigido | Estado atual |
|---|---|---|
| Abrir página sem clicar | Nenhuma captura/seletor automático | Não executado no navegador Windows homologado |
| Cancelar seletor ou negar permissão | Erro acionável, sem sala/publicação, recursos liberados | **NÃO EXECUTADO REAL** |
| Selecionar sem compartilhar áudio | Recusa e parada de todo vídeo; sem estado de transmissão | **NÃO EXECUTADO REAL** |
| Compartilhar conteúdo temporariamente em silêncio | Track viva continua válida; não encerra por volume zero | **NÃO EXECUTADO REAL** |
| Bloqueio de autoplay no receptor | Botão Ouvir áudio recupera som por gesto | **NÃO EXECUTADO REAL** |
| Parar pelo site | Indicador nativo encerra, tracks param, receptor perde mídia, sala removida | **NÃO EXECUTADO REAL** |
| Parar pelo controle nativo | Término do par, desconexão e exclusão confirmada | **NÃO EXECUTADO REAL** |
| Término isolado do áudio/vídeo obrigatório, quando reproduzível | Encerra o par; não fica anunciando transmissão | **NÃO EXECUTADO REAL** |
| Sair/reentrar no espectador | Recursos locais liberados, sem apagar sala do apresentador | **NÃO EXECUTADO REAL** |
| Interrupção de transporte e retorno | Reconectando; até30s recupera com confirmação ou encerra | **NÃO EXECUTADO REAL** |
| Backend indisponível ao parar | Captura local para; limpeza fica pendente e pode ser repetida | **NÃO EXECUTADO REAL** |

Se áudio do sistema falhar em ambiente aprovado, registrar navegador/Windows, fonte/opção selecionada e resultado sem segredos. Consultar o usuário antes de restringir suporte, remover áudio ou adotar aplicativo auxiliar. **Não marcar T-03 concluída até existir a evidência real da matriz e dos encerramentos.**

Referências de implementação consultadas: [getDisplayMedia e consentimento](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia), [opções de publicação LiveKit](https://docs.livekit.io/reference/client-sdk-js/interfaces/TrackPublishOptions.html) e [portas LiveKit](https://docs.livekit.io/transport/self-hosting/ports-firewall/). A referência web pode avançar de versão; os tipos/fontes **2.22.3** instalados foram usados para conferir a implementação fixada.
