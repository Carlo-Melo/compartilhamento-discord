# Backend — Java 21 / Spring Boot
Base executável da T-01. Requer JDK **21** (não apenas JRE); Maven é obtido pelo Wrapper **3.3.4**, fixado em **3.9.16** com SHA256. Spring Boot **4.1.2**. Primeira execução requer internet para baixar Maven e dependências.

## Executar no Windows / PowerShell
Na raiz do repositório, configure `.env` conforme o [README principal](../README.md) e inicie o PostgreSQL. No terminal do backend:

```powershell
# Ajuste para a instalação local de JDK 21.
$env:JAVA_HOME = 'C:\caminho\para\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
. .\scripts\Import-DevEnvironment.ps1
cd backend
.\mvnw.cmd --version
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

O build exige Java 21 e gera `target/backend-0.0.1-SNAPSHOT.jar`. Como alternativa ao último comando: `java -jar .\target\backend-0.0.1-SNAPSHOT.jar`. Encerre com `Ctrl+C` no terminal que iniciou o processo. O build não precisa de banco; a verificação de readiness precisa.

## Configuração e saúde
O Spring recebe `DATABASE_URL` (JDBC), `DATABASE_USERNAME` e `DATABASE_PASSWORD` do ambiente; não lê `.env` automaticamente. O script acima importa o formato simples `NAME=value`, sem executar o conteúdo nem exibir valores. Use valores sem espaços, aspas, `#` ou `$`, para manter a interpretação igual à do Compose; uma senha local aleatória hexadecimal atende ao formato. Valores e exemplos estão na raiz.

- API local: `127.0.0.1:8080`.
- `GET http://127.0.0.1:8080/health/live`: `200 {"status":"UP"}` quando o processo está vivo; não testa banco.
- Gestão somente em loopback: `GET http://127.0.0.1:8081/health/ready`: `200 {"status":"UP"}` quando aplicação e conexão JDBC estão disponíveis; `503 {"status":"DOWN"}` em indisponibilidade. Nenhum detalhe de configuração é exposto.
- Apenas saúde do Actuator é exposta na porta de gestão. `/health/ready` não está na porta pública da API nem no proxy Angular.

```powershell
Invoke-RestMethod http://127.0.0.1:8080/health/live
Invoke-RestMethod http://127.0.0.1:8081/health/ready
```

Nesta etapa readiness verifica somente aplicação e banco: não atesta mídia, bot ou limpeza de salas. A limpeza obrigatória antes de readiness entra na T-19. As portas são locais; esta configuração não é um deploy de produção.

Pacote base proposto: br.com.compartilhamentodiscord.
Camadas: controller (HTTP/entrada) -> service (casos de uso e autorização) -> repository (somente cadastro bot/vínculo) -> PostgreSQL. Estado operacional em services de memória, sem JPA.
entity representa persistência; dto/request e dto/response representam contratos; mapper faz conversão; integration/discord e integration/livekit encapsulam provedores.
security trata tickets e contexto web em memória/CSRF, sem login OAuth; config reúne configuração tipada; exception padroniza erros; scheduler trata limpeza, reconciliação e fila volátil.
Não colocar regra em controller, repository ou no cliente Angular. Não manter transação de banco aberta durante chamadas externas.

Dependências desta etapa: Spring MVC, JDBC (pool Hikari), Actuator e driver PostgreSQL, com versões transitivas gerenciadas pelo BOM Spring Boot. Health usa controller -> service -> DTO. Repository/entity continuam reservados, pois não há persistência a implementar na T-01. Nenhum SQL de inicialização ou tabela é criado.

Spring Security, Validation e padronização geral de erros ficam para T-05; JPA/Flyway e o modelo bot–servidor, para T-06. A integração LiveKit da T-02 é descrita abaixo. Sem OAuth2 Client ou Spring Session JDBC.

Pastas de testes preservadas; não foram criados testes unitários que apenas repetem a resposta de saúde. Evidências de build, JDBC e HTTP real ficam em [tests/manual/T-01-bootstrap.md](../tests/manual/T-01-bootstrap.md).

## T-02 — SDK oficial e prova local isolada
O SDK JVM oficial `io.livekit:livekit-server:0.16.0` (Kotlin/JVM 17) é consumido pelo Java 21 em `integration/livekit`. O gateway cria, consulta e exclui salas e consulta participantes/tracks. Configuração tipada `livekit.*`, timeout total de 5s e falhas sanitizadas; sem logs de credenciais ou corpos do provedor. API key/secret nunca são enviados ao Angular. Mídia segue diretamente navegador–SFU.

O backend assina JWTs de **120s**, com sala e identidade específicas. Apresentador: publica somente `screen_share` e `screen_share_audio`, sem assinatura ou dados. Espectador: somente assinatura, sem publicação de mídia/dados. Nenhum desses tokens concede `roomAdmin` ou `roomCreate`. O vencimento impede uma nova entrada; não é mecanismo de desconexão de clientes já conectados.

| Propriedade / ambiente | Padrão / uso |
|---|---|
| `livekit.enabled` / `LIVEKIT_ENABLED` | `false`; integração opt-in |
| `livekit.api-url` / `LIVEKIT_INTERNAL_URL` | `http://127.0.0.1:7880` na prova |
| `livekit.browser-url` / `LIVEKIT_PUBLIC_URL` | `ws://127.0.0.1:7880` na prova |
| `livekit.api-key` / `LIVEKIT_API_KEY` | Sem credencial padrão |
| `livekit.api-secret` / `LIVEKIT_API_SECRET` | Sem credencial padrão; ao menos 32 caracteres |
| `livekit.request-timeout` | `5s`, validado entre 1s e 30s |
| `livekit.proof.enabled` / `LIVEKIT_PROOF_ENABLED` | `false`; flag adicional do auxiliar |
| `livekit.proof.origin` | Exatamente `http://127.0.0.1:4200` |

Não existe endpoint de emissão de tokens no artefato normal. Os controllers, services e DTOs auxiliares ficam em `src/livekit-proof/java`, compilados **somente** pelo profile Maven `livekit-proof`, em **`target-livekit-proof`**, separado de `target`. Também exigem profile Spring `livekit-proof`, flag explícita e backend/SFU em loopback; profiles `prod` e `production` os desabilitam. Requisições locais exigem Host autorizado, origem exata nas mutações e marcador não secreto `X-LiveKit-Proof: local-development`. Não há CORS liberado. Isso é isolamento de uma prova local, não autenticação do produto nem substituto dos tickets futuros.

Na raiz, com JDK 21 e banco local configurados, prepare a SFU conforme [infra](../infra/README.md). Gere `.env.livekit` somente se ainda não existir. Depois:

```powershell
. .\scripts\Import-DevEnvironment.ps1
. .\scripts\Import-LiveKitEnvironment.ps1
# Opcional: habilita os dois testes reais (requer SFU já iniciada).
$env:LIVEKIT_REAL_TEST_ENABLED = 'true'
.\backend\mvnw.cmd -f backend/pom.xml -Plivekit-proof clean verify
java -jar .\backend\target-livekit-proof\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=livekit-proof --livekit.enabled=true --livekit.proof.enabled=true
```

Mantenha esse terminal aberto. Em outro terminal inicie o Angular e abra `/dev/livekit`, conforme [frontend](../frontend/README.md). O auxiliar gera seus próprios nomes/identidades; não aceita sala arbitrária ou identidade fornecida pelo cliente. Limites por processo: 4 salas simultâneas, 32 nomes alocados e 8 tokens por sala. Somente nomes criados por esse processo podem ser consultados, receber tokens ou ser excluídos.

Antes de encerrar o backend, remova explicitamente as salas próprias ainda registradas (nenhum token/segredo é retornado):

```powershell
$proofHeaders = @{ Origin = 'http://127.0.0.1:4200'; 'X-LiveKit-Proof' = 'local-development' }
Invoke-RestMethod -Method Delete -Uri http://127.0.0.1:8080/api/dev/livekit/rooms -Headers $proofHeaders
```

A limpeza tenta todos os nomes próprios; falhas retornam 503 para permitir nova tentativa. Depois use `Ctrl+C` no backend/Angular e encerre somente o Compose LiveKit descrito em infra. Não recompile um JAR em execução. Não há limpeza de startup/readiness nesta etapa (T-19).

Para gerar o artefato normal: `.\backend\mvnw.cmd -f backend/pom.xml clean verify`. Sem `LIVEKIT_REAL_TEST_ENABLED=true`, os dois testes SFU são pulados explicitamente; os três testes locais de assinatura/grants/configuração continuam executados. O relatório [T-02](../tests/manual/T-02-livekit.md) distingue execução automatizada, prova no navegador e itens não homologados. O contrato auxiliar separado está em [OpenAPI local](../specs/api/livekit-proof.openapi.json); o contrato público do produto não oferece essa emissão.

## T-03 — auxiliar restrito para captura real

Implementado separadamente da T-02 em `src/capture-proof/java`, com testes em `src/capture-proof-test/java`. O artefato padrão não contém estas classes. O profile Maven **capture-proof** gera `target-capture-proof`; executar requer também profile Spring **capture-proof** e opt-in `CAPTURE_PROOF_ENABLED=true`. Os profiles `prod`, `production` ou `livekit-proof` desativam este auxiliar. T-03 permanece **pendente de homologação real**; builds e testes de lógica não comprovam áudio do Windows.

Prepare a configuração separada e os certificados confiáveis conforme [infra](../infra/README.md). Quando as duas máquinas e a rede forem definidas pelo operador, com Java 21 no PATH:

```powershell
. .\scripts\Import-DevEnvironment.ps1
. .\scripts\Import-CaptureEnvironment.ps1
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
java -jar .\backend\target-capture-proof\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=capture-proof --livekit.enabled=true --capture.proof.enabled=true
```

O backend continua em **127.0.0.1:8080**; a API administrativa SFU fica em loopback, em porta diferente da T-02. O proxy HTTPS mantém Host e Origin exatos da origem LAN. O backend rejeita configuração sem HTTPS/WSS na mesma origem, IP público ou bind público da API; somente IPv4 privado RFC1918 está preparado nesta prova. Rede externa/VPN/TURN depende da decisão e preparação de rede, sem abrir portas automaticamente.

As chaves `CAPTURE_PROOF_PRESENTER_KEY` e `CAPTURE_PROOF_VIEWER_KEY` devem ser aleatórias, diferentes e conter 32 a 128 caracteres ASCII seguros. São capacidades experimentais por papel, inseridas apenas no campo de senha da página; não são login de produto ou tickets Discord. Não colocar em URL, bundle, log, screenshot ou armazenamento web. Não imprimir `.env.capture` nem valores de ambiente. A chave de espectador permite só obter sua credencial de assinatura e consultar estado; criar/excluir exige chave de apresentador. Todas as chamadas exigem também `X-Capture-Proof: restricted-development`, origem/Host exatos e transporte do proxy local. GET pode omitir Origin; mutações exigem Origin.

O [contrato auxiliar T-03](../specs/api/capture-proof.openapi.json) define somente `/api/dev/capture/session` e `/viewer`, sem emissão por sala ou papel arbitrário. Uma sala ativa e duas identidades estáveis por sala limitam a prova a um apresentador e um espectador; nova aba do mesmo papel substitui a conexão anterior. Há limite de 32 salas por execução e nomes nunca reutilizados. JWT inicial dura 120s para entrada; expiração não desconecta cliente já conectado. `live=true` depende de consulta Java à SFU confirmando vídeo `SCREEN_SHARE` e áudio `SCREEN_SHARE_AUDIO` da identidade esperada, sem mute; silêncio não é ausência de track.

O estado é apenas memória; nenhum usuário, ticket, transmissão ou chave é persistido no PostgreSQL. Criação/exclusão incertas preservam o nome próprio para repetir limpeza. Antes de parar o backend, encerre pela página ou execute sem imprimir os cabeçalhos:

```powershell
$captureOrigin = [Uri]$env:CAPTURE_PROOF_ORIGIN
$captureHeaders = @{
    Host = $captureOrigin.Authority
    Origin = $env:CAPTURE_PROOF_ORIGIN
    'X-Capture-Proof' = 'restricted-development'
    'X-Capture-Key' = $env:CAPTURE_PROOF_PRESENTER_KEY
}
Invoke-RestMethod -Method Delete -Uri http://127.0.0.1:8080/api/dev/capture/session -Headers $captureHeaders
Remove-Variable captureHeaders
```

Se receber 409, aguarde a operação corrente e repita. Em 503, mantenha backend/SFU ativos e repita; ausência da sala precisa ser confirmada. O shutdown tenta limpeza, mas encerramento abrupto não a garante. Depois da limpeza, encerre os processos e remova a composição SFU **T-03**; recrie essa SFU descartável antes de reiniciar a prova. Não reutilizar uma SFU com salas antigas após reinício do backend; limpeza de startup/readiness do produto continua na T-19.

Validação automatizada do auxiliar: **13 testes, 0 falhas/erros, 2 pulados** (dois testes de SFU real opt-in não executados nesta rodada). Oito testes novos cobrem papéis, origem/Host/loopback/marcador, configuração separada, fontes obrigatórias, limpeza após falha, limite e concorrência sem lock durante rede; três testes existentes validam grants/assinatura/configuração. Builds padrão/T-02 também aprovados, cada um com três testes locais aprovados e dois SFU pulados. Inspeção dos JARs confirmou separação das classes dos dois auxiliares e ausência de ambos no JAR normal.

Smoke HTTP real com processos Java temporários **somente em loopback** confirmou: JAR normal, auxiliar sem opt-in e profiles prod/production retornam 404; auxiliar habilitado sem chave retorna 403; consulta autenticada sem sala retorna 200; espectador não exclui (403); apresentador exclui estado vazio de forma idempotente (204); Origin externa é negada (403). Não houve mídia neste smoke. Docker Desktop não disponibilizou o daemon nesta rodada; não se repetiu a integração SFU real da T-02. Relatório e roteiro humano em [T-03](../tests/manual/T-03-captura-real.md).
