# T-05 — segurança, validação e organização do backend

Data: **26/10/2026**. **T-05 CONCLUÍDA**, com builds, testes e HTTP Spring real aprovados. Esta entrega estabelece segurança básica e falha fechada. **Não implementa acesso definitivo do produto** e não conclui a homologação de mídia da T-03.

## Implementação

- Spring Security **7.1.2**, Jakarta Validation **3.1.1** e Hibernate Validator **9.1.5.Final**, gerenciados pelo BOM Spring Boot **4.1.2** já fixado; Java 21 preservado. Sem OAuth2 Client, Spring Session JDBC, usuários, senhas ou autenticação provisória.
- Política explícita por método/caminho e negação padrão. Sem formulário/redirect de login, Basic ou desafio WWW-Authenticate. IDs, papéis, Authorization e cookies inventados enviados pelo cliente não concedem acesso.
- `GET /api/csrf` cria token sincronizado e bootstrap técnico em memória. Retorna `headerName`, `parameterName`, `token`; publica cookie legível `XSRF-TOKEN`. O cookie técnico já tem nome `ACCESS_SESSION`, HttpOnly, SameSite=Lax, sem Domain e transporte somente por cookie. A origem HTTPS configurada ativa Secure nos dois cookies, inclusive atrás do proxy local; headers Forwarded arbitrários não alteram essa decisão. Ele guarda CSRF, sem contexto autorizado ou SecurityContext persistido. Reinício perde esse estado; não há banco, Redis ou arquivo de sessão.
- Mutações do produto exigem Origin única e exata (`app.security.origin`/`APP_ORIGIN`, padrão `http://127.0.0.1:4200`) e `X-XSRF-TOKEN` da mesma sessão de bootstrap. Não basta enviar um cookie/token escolhido pelo cliente ou token de outra sessão. CORS não libera origens externas. Angular usa proxy de mesma origem.
- Erros MVC e da cadeia de segurança: `{code,message,requestId,retryable}`, mensagem fixa/sanitizada em português. UUID gerado pelo backend em cada requisição e header `X-Request-Id`, sem refletir valor recebido. Não retornar ou registrar corpos de entrada, credenciais, tickets, JWTs, stacktrace, SQL, caminhos internos ou conteúdo de provedores.
- Controller auxiliar T-02 valida o papel com Bean Validation; regras de autorização/papel permanecem em security/service. Integração SFU permanece em integration/livekit. Não há entidade/repository/tabela artificial.

| Erro geral | HTTP | retryable |
|---|---|---|
| `INVALID_REQUEST` (JSON/validação/entrada) | 400 | false |
| `ACCESS_REQUIRED` | 401 | false |
| `FORBIDDEN`, `ORIGIN_INVALID`, `CSRF_INVALID` | 403 | false |
| `NOT_FOUND` | 404 | false |
| `METHOD_NOT_ALLOWED` | 405 | false |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | false |
| `INTERNAL_ERROR` (mensagem fixa) | 500 | false |
| `SERVICE_UNAVAILABLE`, `MEDIA_UNAVAILABLE` | 503 | true |

Segurança pode negar a requisição antes do despacho MVC; nesse caso o cliente recebe a rejeição da cadeia, sem revelar se há um controller privado. Os códigos 400/404/405/415 do MVC se aplicam quando a requisição já atravessou a política correspondente.

## Política por rota e método

| Interface | Método e caminho | Resultado/política |
|---|---|---|
| API loopback 8080 | `GET /health/live` | Público; somente `{status:UP/DOWN}`, 200/503; sem teste de banco |
| API loopback 8080 | `GET /api/csrf` | Público; bootstrap técnico no-store, sem acesso de produto |
| Gestão loopback 8081 | `GET /health/ready` | Somente nessa interface/porta; somente status, 200/503; aplicação + JDBC |
| API | `GET /health/ready` | Negado; não expõe a readiness da gestão |
| Gestão | Demais caminhos/métodos, inclusive `/health`, `/env`, `/configprops`, `/heapdump` | Negados; sem detalhes internos |
| API | GET privado ou caminho não liberado | 401 sem contexto; sem login/Basic |
| API | Mutação privada ou caminho não liberado | 403 para Origin/CSRF inválidos; mesmo com ambos válidos, 401 sem contexto |
| API | Integrações futuras Discord/LiveKit | Negadas; nenhuma liberação antes de verificar assinaturas |
| API | Prefixos experimentais sem classes/configuração ativa | 404 preservado, sem emissão de credenciais |
| T-02 habilitada | `POST`, `DELETE /api/dev/livekit/rooms`; `GET`, `DELETE /api/dev/livekit/rooms/{name}`; `POST /api/dev/livekit/rooms/{name}/tokens` | Somente cadeia auxiliar; loopback, Host aprovado e marcador obrigatório; Origin exata nas mutações e rejeitada quando divergente em GET; salas/identidades próprias |
| T-03 habilitada | `GET`, `POST`, `DELETE /api/dev/capture/session`; `POST /api/dev/capture/session/viewer` | Somente cadeia auxiliar; loopback, Host/Origin HTTPS exatos, marcador e chave válida; criar/excluir somente apresentador, viewer somente espectador, status ambos |
| API | Outros métodos/caminhos sob `/api/dev/**` | Sem liberação ampla; negados |

As portas acima são os padrões documentados; testes automatizados usam portas aleatórias somente em loopback. Readiness ainda não verifica limpeza SFU: essa obrigação é T-19. A porta de gestão não faz parte do proxy Angular.

## Integração com as provas T-02/T-03

Os filtros experimentais executam na cadeia Spring Security antes da autorização, mantendo suas barreiras em todas as rotas declaradas. Os registros de filtro fora da cadeia não podem executar a mesma verificação duas vezes. O build normal não contém fontes auxiliares; cada profile Maven gera diretório/JAR próprio. Profile Spring, opt-in e bloqueios de `prod`/`production` continuam obrigatórios; T-03 permanece incompatível com profile `livekit-proof`.

Somente os métodos/caminhos experimentais declarados têm exceção ao **token** CSRF: suas credenciais não vêm de cookies, e headers customizados obrigatórios não são enviáveis por formulário HTML. T-02 usa marcador não secreto com isolamento estritamente local; T-03 exige chave secreta distinta por papel. Ambas validam Origin nas mutações e recusam preflight externo/CORS. Isso não autentica usuários do produto e não permite isentar as integrações futuras ainda sem assinatura. Não existe `permitAll` abrangente em `/api/**` ou `/api/dev/**`.

Os contratos auxiliares passam de `0.1.0` para `0.2.0`: `Failure` agora exige `requestId` UUID e `retryable` além de `code`/`message`, preservando códigos específicos anteriores. Falha SFU usa `SFU_UNAVAILABLE`, HTTP 503 e retryable=true; rejeições de acesso e validação não são retryable. Erros estruturais de JSON/Bean Validation seguem os códigos gerais. T-02 usa mensagem sanitizada e T-03 trata status com mensagens locais; campos adicionais não alteram ações do cliente. Contratos em [T-02](../../specs/api/livekit-proof.openapi.json) e [T-03](../../specs/api/capture-proof.openapi.json).

## Cabeçalhos e componente Angular

O backend aplica Cache-Control no-store, X-Content-Type-Options nosniff, bloqueio de framing e Referrer-Policy no-referrer, além de CSP para conteúdo de suas próprias respostas. **Um cabeçalho entregue só na resposta da API não protege o documento Angular.** O servidor/proxy que entrega o HTML precisa aplicar CSP restrita à aplicação/SFU, `frame-ancestors 'none'` e Permissions-Policy `display-capture=(self), camera=(), microphone=()`.

Não bloquear a captura consentida, WSS da SFU e recursos de mídia dos auxiliares. A política final do documento, HTTPS de produção e gestão de certificados pertencem à T-20; esta entrega não abre listener LAN, firewall ou confiança TLS. A prova T-03 continua exigindo HTTPS confiável nas duas máquinas e consentimento no seletor nativo.

## Resultados da validação

Verificações executadas no Windows/PowerShell do workspace com Java 21 e os runtimes locais já existentes, sem iniciar serviço externo. **Zero falhas e zero erros** nas três execuções finais. As contagens incluem repetições da suíte comum em cada artefato; não representam testes únicos somados.

| Verificação | Resultado desta execução | Pulados/limite da evidência |
|---|---|---|
| Backend normal: `clean verify` | **23 testes: 19 aprovados, 4 pulados**; build aprovado | 2 SFU reais opt-in e 2 HTTP de auxiliares cujas classes estão ausentes |
| Backend `livekit-proof`: `clean verify` inicial e `verify` final após regressão do firewall | **23 testes: 20 aprovados, 3 pulados** na execução final; build aprovado | 2 SFU reais opt-in e 1 HTTP T-03, ausente nesse artefato |
| Backend `capture-proof`: `clean verify` | **31 testes: 28 aprovados, 3 pulados**; build aprovado | 2 SFU reais opt-in e 1 HTTP T-02, ausente nesse artefato |
| `SecurityHttpTest` comum | **10 testes HTTP reais aprovados** em cada artefato | Processos Spring/servidores em loopback; sem contexto autorizado ou mídia |
| `ProofSecurityHttpTest` | Ausência em quatro configurações aprovada; HTTP T-02/T-03 habilitado aprovado no artefato correspondente | Sem SFU ativa: erro 503 deliberadamente provocado e sanitizado |
| `ApiExceptionHandlerTest` | **5 testes MVC aprovados** em cada artefato | MockMvc standalone complementa os testes HTTP reais; não simula autorização de produto |
| Isolamento dos três JARs | Normal sem classes/DTOs auxiliares; T-02 contém somente seu auxiliar; T-03 contém somente seu auxiliar; nenhuma fixture de teste nos JARs | Inspeção dos artefatos empacotados |
| Dependências | Sem OAuth2 Client, Spring Session, JPA ou Flyway nos JARs | Versões de segurança/validação vêm do BOM |
| Sessões e logs | Nenhum `SESSIONS.ser` nos diretórios de build; nenhum marcador de senha gerada ou headers Authorization/Set-Cookie/X-Capture-Key nos três logs de build | Inspeção sem imprimir ambiente ou arquivos de segredos; não equivale a homologar infraestrutura futura |
| Frontend | **45 testes de captura aprovados** e builds `production`, `development` e `capture-proof` aprovados | Testes usam doubles; nenhum seletor nativo ou áudio Windows foi homologado |
| OpenAPI e diff | 3 JSONs válidos; referências internas resolvidas: produto 95, T-02 28, T-03 18; quatro campos de erro exigidos; `git diff --check` aprovado | Parse/referências estruturais, sem alegar execução dos cenários Gherkin |

Os dez testes HTTP da base exercitam: liveness/cabeçalhos; gestão separada com readiness DOWN deliberada; negação a headers/cookies/Basic inventados e ausência de login; bootstrap sem autorização; Origin e CSRF vinculados à sessão; CORS externo negado; requestId próprio; token somente em header e rejeição de outro bootstrap/reinício; Secure determinado pela origem HTTPS e Forwarded ignorado; firewall rejeitando caminho ambíguo com erro padronizado. Cookies/token válidos permitem atravessar CSRF e continuam recebendo 401 sem contexto.

Os testes HTTP dos auxiliares mantêm 404 em artefato/configuração ausente, opt-in desligado e profiles `prod`/`production`; exercitam Host, Origin, marcador, credenciais por papel, espectador sem administração, JSON malformado/validação T-02 e falha SFU sanitizada. O filtro de firewall também usa envelope com quatro campos. Testes MVC adicionais verificam 400/404/405/415, erro inesperado 500 sem detalhes e fallback 503 sanitizado.

Uma execução HTTP inicial encontrou um defeito concreto no bootstrap: definir `Set-Cookie` via `ResponseEntity.header` sobrescrevia o cookie de sessão criado pelo contêiner. A correção usa adição de header e preserva **ambos** os cookies; a regressão final confirmou o par, o vínculo CSRF e a negação após reinício. Não restou falha aberta dessa execução.

**Não executados nesta T-05:** os dois testes contra SFU real opt-in; readiness UP com PostgreSQL disponível; RTP/mídia real; captura/áudio Windows entre duas máquinas; nove espectadores/TURN; jornada Discord/tickets; TLS real de produção e cabeçalhos do documento Angular. Evidências anteriores T-01/T-02 continuam nos respectivos relatórios e não são apresentadas como repetidas nesta rodada. Nenhum serviço externo foi iniciado para estes testes.

## Como reproduzir

Na raiz com JDK 21 no PATH, os builds/testes de segurança usam servidores efêmeros em loopback e não exigem PostgreSQL/SFU disponíveis:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean verify
.\backend\mvnw.cmd -f backend/pom.xml -Plivekit-proof clean verify
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
```

Os resultados JUnit ficam em `backend/target/surefire-reports`, `backend/target-livekit-proof/surefire-reports` e `backend/target-capture-proof/surefire-reports`. Dentro de `frontend`, com Node 24: `npm.cmd run test:capture`, `npm.cmd run build`, `npm.cmd run build -- --configuration development` e `npm.cmd run build -- --configuration capture-proof` reproduzem as verificações dos consumidores.

Para smoke manual do artefato normal, iniciar conforme [backend](../../backend/README.md) e consultar `/health/live`, `/api/csrf`, `/api/access/session` e a readiness na porta de gestão. Guardar cookies/token somente em memória e não imprimir os valores. Confirmar que bootstrap não concede acesso, erro tem quatro campos e não há Location de login nem WWW-Authenticate. Não criar endpoint de diagnóstico para isso. Para auxiliares, usar os comandos e a limpeza explícita dos READMEs existentes; não imprimir chaves, JWTs ou arquivos `.env`.

## Limitações e próximas etapas

- **T-06:** JPA/Flyway e persistência somente de identificação de bot/vínculo com servidor. Nenhuma tabela de usuário, sessão, ticket, transmissão ou fila.
- **T-07:** tickets privados 256 bits/60s, consumo atômico, rotação do ID de bootstrap, contexto autorizado em memória e ciclo de acesso/revogação. A política atual deve continuar falhando fechada até esse mecanismo existir.
- T-08/T-09 e integrações: vínculos/associação REST, papéis e assinaturas reais; nenhuma exceção antecipada concedida.
- **T-19:** invalidar runtime no boot e limpar salas antigas antes de readiness. Readiness atual não declara essa garantia.
- **T-03:** homologação real Windows/Chrome/Edge entre duas máquinas ainda pendente; decisões de máquina/rede/certificados/firewall adiadas pelo usuário. **T-04** continua pendente.
- Nenhum deploy, conta externa, serviço pago, login OAuth, bot, gravação, Activity ou aplicativo nativo foi criado.

Referência técnica consultada: [Spring Security 7.1.2 — CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), que descreve o repositório de token em sessão, resolução do token e tratamento das rejeições. A T-05 mantém o token esperado na sessão técnica em memória e exige header para a SPA; as exceções experimentais são específicas e não desabilitam a proteção do produto.
