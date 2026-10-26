# Inventário técnico — T-01 e integração local T-02
Verificação em 26/10/2026. Mantidas as linhas de referência de [06](06-arquitetura.md); isto não homologa mídia, navegadores ou o produto completo.

| Componente | Versão fixada / utilizada | Fonte primária |
|---|---|---|
| Java | linguagem e runtime 21; validado com Temurin 21.0.12.1+1 | [Adoptium](https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse) |
| Spring Boot | 4.1.2 | [Requisitos](https://docs.spring.io/spring-boot/system-requirements.html), [artefato](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/4.1.2/) |
| Maven / Wrapper | 3.9.16 / 3.3.4 | [Maven](https://maven.apache.org/docs/3.9.16/release-notes.html), [Wrapper](https://maven.apache.org/tools/wrapper/) |
| Angular | 22.2.3 | [Registro oficial npm](https://registry.npmjs.org/@angular/core/22.2.3) |
| Angular CLI / build | 22.2.4 | [Registro oficial npm](https://registry.npmjs.org/@angular/cli/22.2.4) |
| Node / npm | 24.22.1 LTS / 11.19.1 | [Distribuição oficial](https://nodejs.org/dist/v24.22.1/) |
| TypeScript / RxJS | 6.0.3 / 7.8.2 | [Matriz Angular](https://angular.dev/reference/versions) |
| Vite (tooling Angular) | 8.3.5, override de patch restrito ao builder | [Aviso do mantenedor](https://github.com/vitejs/vite/security/advisories/GHSA-9jrq-w75r-8gcw) |
| PostgreSQL | 18.6-bookworm | [Versões suportadas](https://www.postgresql.org/support/versioning/), [imagem oficial](https://hub.docker.com/_/postgres) |
| Docker / Compose observados | Engine 29.7.2 / Compose 5.4.0 | Ambiente local; Compose v2 com `up --wait` é suficiente |

Spring Boot 4.1.2 aceita Java 21 e Maven 3.9.16. Angular 22 exige Node `^24.15.0`, TypeScript `>=6.0.0 <6.1.0` e RxJS `^7.4.0`. Os manifests usam patches exatos e o `package-lock.json` fixa a árvore npm. Não utilizar TypeScript `latest` (7.x) nessa linha. Dependências transitivas Java são gerenciadas pelo BOM Spring Boot; nenhuma versão `latest` ou `SNAPSHOT` externa é usada.

O Wrapper oficial `only-script` não requer JAR versionado. `maven-wrapper.properties` fixa a distribuição Maven e o SHA256 `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`. O ZIP Maven foi conferido contra o SHA512 publicado no Maven Central; o ZIP do Wrapper foi conferido contra seu SHA1 oficial (SHA256/SHA512 não publicados).

Para validar sem alterar Java/Node globais, runtimes portáteis oficiais foram baixados em `work/tools/` (ignorado). JDK e Node tiveram SHA256 conferido com seus metadados oficiais. Essa pasta não faz parte do build distribuído: uma máquina nova deve instalar os pré-requisitos e executar Wrapper / `npm ci`.

## Dependências da T-01
Backend: Spring MVC, JDBC/Hikari, Actuator e driver PostgreSQL. Plugin Enforcer impede build fora do JDK 21. Sem JPA, Flyway, Security, OAuth2 Client, sessão JDBC, bot ou SDK de mídia neste corte. Não existe schema de produto ou SQL de inicialização.

Frontend: Angular standalone, Router e HttpClient, sem SSR, biblioteca visual ou runner de testes artificial. Estruturas `core`, `shared` e `features` preservadas. O proxy mantém `/api` nas rotas normais e ajusta apenas `/api/health/...` para o contrato de saúde `/health/...`.

Readiness de bootstrap verifica aplicação e JDBC. A obrigação de limpar salas antigas antes de readiness permanece no contrato e será implementada na T-19, quando houver SFU. A saúde mínima não declara o produto pronto para transmitir.

## Acréscimos da T-02
Verificados em 26/10/2026, sem atualizar as versões base da T-01.

| Componente | Versão fixa | Evidência primária |
|---|---|---|
| LiveKit Server | v1.13.10 | [Release oficial](https://github.com/livekit/livekit/releases/tag/v1.13.10) |
| SDK JVM oficial | `io.livekit:livekit-server:0.16.0` | [POM publicado](https://repo.maven.apache.org/maven2/io/livekit/livekit-server/0.16.0/livekit-server-0.16.0.pom), [build JVM 17](https://github.com/livekit/server-sdk-kotlin/blob/v0.16.0/build.gradle.kts) |
| SDK browser | `livekit-client:2.22.3` | [Release oficial](https://github.com/livekit/client-sdk-js/releases/tag/v2.22.3) |
| OkHttp BOM / JVM | 5.3.2 | Alinhado ao POM do SDK; elimina a combinação Retrofit/OkHttp 4 com classes JVM 5 duplicadas |
| Jackson 2 do SDK | 2.22.2 | [POM e correções SDK](https://github.com/livekit/server-sdk-kotlin/blob/v0.16.0/CHANGELOG.md); preservado o requisito do SDK, sem alterar Jackson 3 do Boot |
| Build Helper | 3.6.1 | Somente profile Maven `livekit-proof`, para compilar as fontes auxiliares em diretório separado |
| JUnit Jupiter | Gerenciado pelo BOM Spring Boot | Dependência somente de teste para assinatura/grants e integração SFU opt-in |

Imagem fixada também pelo digest `sha256:89ca9ce0791e18d6e03bf6e2c49ceb27e1c754eae8bd50c989386c1a5963791a`. Configuração local: auto_create=false, max_participants=10, VP8/Opus, sem TURN/egress/gravação. Portas TCP 7880/7881 e UDP 7882 publicadas somente no loopback; nenhum volume adicional. API administrativa acessível somente localmente nesta prova.

SDK JVM compilado para Java 17 é consumível pelo JDK 21; o código da aplicação permanece Java. A árvore final alinha Retrofit 3.0.0, OkHttp 5.3.2, Jackson 2.22.2 e as dependências gerenciadas pelo Boot. O artefato `okhttp:5.3.2` contém metadados; as classes JVM ficam somente em `okhttp-jvm:5.3.2`. Não há OkHttp 4.12 concorrente.

Artefato normal em `backend/target`; auxiliar em `backend/target-livekit-proof`, ignorado pelo Git. Frontend de produção não inclui a rota ou o SDK da ferramenta. Os testes JUnit e a recepção de vídeo/áudio sintéticos no Angular contra SFU real passaram. A configuração final usa os parâmetros padrão de conexão do SDK (retry/timeouts); os overrides locais mais curtos foram removidos. Isso não homologa captura ou áudio do Windows. [Resultados e limites T-02](../tests/manual/T-02-livekit.md).
