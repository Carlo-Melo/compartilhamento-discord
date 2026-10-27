# 06 — Stack e arquitetura em camadas
## Decisões aprovadas pelo usuário
Java **21** é a versão da linguagem/runtime; Spring Boot é o framework, com numeração independente. Angular no frontend e PostgreSQL exclusivamente para identificar bots e seus vínculos com servidores. Backend monolítico em camadas.

## Versões de referência verificadas em 25/10/2026
| Componente | Linha de referência | Estado |
|---|---|---|
| Java | 21 LTS | Aprovado pelo usuário |
| Spring Boot | 4.1.x, documentação consultada 4.1.2 | Baseline técnica proposta; Java 21 compatível |
| Angular/CLI | 22.x | Baseline técnica proposta |
| Node para tooling Angular | 24.15+ na linha 24 | Conforme matriz Angular 22 |
| TypeScript / RxJS | 6.0.x / 7.x compatível | Gerenciado pelo bootstrap Angular |
| PostgreSQL | 18.x estável, patch atualizado | Baseline técnica proposta |
| Maven | Wrapper 3.9.x | Proposto |
| LiveKit | Servidor e SDKs com versões fixadas na POC | Proposto; não usar latest em deploy |

T-01 fixa patches compatíveis nessas linhas, Maven Wrapper e lockfile npm. Versões exatas e fontes estão no [inventário do bootstrap](14-bootstrap-inventario.md). Se a equipe preferir outra linha de Spring/Angular, registrar ADR antes de gerar código. Não confundir escolha de linha de referência com homologação.

## Componentes e caminho dos dados
~~~mermaid
flowchart LR
 D[Discord: comando e botao] -->|interaction assinada| C[Controller Spring]
 A[Angular no navegador] -->|HTTPS /api e cookie| C
 C --> S[Service: regras e acesso pelo bot]
 S --> R[Repository: somente bot e vinculo]
 S --> M[Runtime em memoria: acesso e transmissao]
 R --> P[(PostgreSQL)]
 S --> I[Integracoes Discord e LiveKit]
 I --> D
 I --> L[LiveKit: SFU e TURN]
 A <-->|WebRTC: audio e video| L
~~~

Vídeo não passa por controller/service nem é salvo no banco. Bot é uma integração do mesmo backend; SFU é componente de infraestrutura independente.

## Camadas e responsabilidades
| Pasta/pacote | Responsabilidade | Restrições |
|---|---|---|
| controller | DTOs HTTP, validação estrutural, resposta/status | Sem regra de negócio ou acesso direto a repository |
| service | Casos de uso, autorização, estados, limites, orquestração | Não reimplementar transporte WebRTC |
| repository | Persistir/consultar bot e vínculo com guild | Sem usuários, sessões, tickets, filas ou HTTP externo |
| entity | Mapeamento JPA e invariantes persistidas | Não serializar em API |
| dto/request, dto/response | Contratos públicos | Validar limites e separar credenciais |
| mapper | Conversão explícita | Não decidir autorização |
| integration/discord | Assinatura, interação, emissão privada de acesso, REST e rate limits | Sem token pessoal de usuário ou segredo no cliente |
| integration/livekit | Tokens, sala, grants, eventos | APIs administrativas somente backend |
| security, config | Ticket, contexto web em memória, cookie, CSRF e configuração | Sem login OAuth de usuários ou sessão JDBC |
| exception | Erros padronizados e códigos | Sem stacktrace em produção |
| scheduler | Fila em memória, expiração e limpeza SFU | Sem outbox persistida; boot invalida runtime |

Transações JPA curtas somente para bot/vínculo. Serviços de runtime encapsulam mapas, locks, tickets, acesso web, sessões e fila em memória. Aplicar resultados de rede por versionamento/compare-and-set, sem segurar lock durante HTTP. Estado só vira LIVE após verificação de mídia. Reinício reconstrói somente vínculos e limpa salas antigas; não recupera o runtime.

## Decisões internas propostas
- ADR-01: Spring MVC, Spring Security com autorização por tickets, Validation, Data JPA, Flyway e Actuator; sem WebFlux sem necessidade medida.
- ADR-02: Angular standalone, Router, HttpClient; SDK LiveKit browser independente de framework. Sem React, NgRx ou biblioteca visual obrigatórios no MVP.
- ADR-03: PostgreSQL contém apenas discord_bot e bot_guild_binding (mais metadados técnicos Flyway). Contexto web, ticket, transmissão, participantes, deduplicação e fila em memória local. Sem Spring Session JDBC, Redis ou broker. Um processo; readiness bloqueada até limpar salas antigas.
- ADR-04: SDK oficial LiveKit Java/Kotlin, artefato JVM consumido por Java. Homologar criar/apagar sala, grants e validar webhook; não criar microserviço Node para usar outro SDK.
- ADR-05: Interactions Discord por HTTP e REST. Gateway só se uma necessidade nova justificar.
- ADR-06: Polling de estado no site a cada 5s + eventos de transporte do SDK. Evitar SSE/WebSocket próprio no primeiro corte; heartbeat do apresentador a cada 10s.
- ADR-07: Docker Compose para dev e piloto Linux; PostgreSQL/SFU privados; proxy HTTPS atende Angular e /api.
- ADR-08: Uma VPS e uma sala global simultânea no piloto. Modelo aceita servidores distintos, mas expansão depende de capacidade/custo.
- ADR-09: Sem microserviços, arquitetura hexagonal, abstrações genéricas de CRUD ou event sourcing. Reavaliar somente com problema concreto.

## Testes previstos
O auxiliar T-04 evolui o source set `capture-proof`, em vez de duplicar o player e sua cadeia de proteção. Um mapa limitado a nove correlações por geração, prazo absoluto e limpeza com confirmação SFU mantêm o estado volátil limitado. Não há repository ou entidades para esse estado. TURN integrado permanece componente LiveKit opt-in, sem serviço de negócio Node ou novas dependências.

JUnit/Spring Boot Test para tickets/autorização e concorrência em memória; Testcontainers PostgreSQL para as duas tabelas de bot/vínculo; teste arquitetural ArchUnit proposto para impedir controller -> repository; Angular usa o runner da linha selecionada; Playwright para E2E e captura sintética, complementada por teste manual real de áudio.

## Base implementada na T-05

Spring Security e Jakarta Bean Validation são gerenciados pelo BOM já fixado. `security`/`config` concentram política por método, bootstrap CSRF em memória, origem e cabeçalhos; `exception` padroniza erros de MVC e da cadeia de segurança. Controllers permanecem finos, com DTOs explícitos; o corpo de emissão auxiliar T-02 valida estruturalmente o papel antes do service. Integração LiveKit permanece encapsulada. Não há repository, entidade ou tabela artificial para preencher camadas.

A autorização do produto falha fechada enquanto o contexto real não existe. A T-07 deverá substituir a negação pelo mecanismo de contexto validado e manter a autorização de escopo/papel nos services/security. A sessão técnica de CSRF não é contexto de usuário. Fontes auxiliares continuam fora do artefato normal; suas proteções participam das cadeias específicas sem permitir prefixos amplos. [Detalhamento e validação](../tests/manual/T-05-seguranca-backend.md).
