# T-06 — persistência mínima e capacidade volátil

Data: **29/10/2026**. Estado: **implementada, validação parcial; não concluída**. Rastreabilidade: RF-26 / AC-24. Nenhuma API de produto foi acrescentada ou alterada; OpenAPI permanece igual. O Gherkin descreve o comportamento e não possui step definitions.

## Entrega

- Spring Data JPA, Flyway e módulo PostgreSQL nas versões gerenciadas por Spring Boot 4.1.2. Migração V1 cria `discord_bot` e `bot_guild_binding`; PK, FK, UNIQUE e NOT NULL seguem a spec 07. PostgreSQL cria os índices das PKs e unicidades. Não há colunas de segredo ou dados operacionais. Flyway mantém sua tabela técnica.
- Hibernate valida, não cria nem altera schema; open-in-view está desligado. Com banco inacessível ou migração inválida na inicialização normal, o processo não sobe. Nenhum endpoint administrativo ou serialização de entidades foi acrescentado.
- `RuntimeCapacityStore` contém somente uma reserva global, 64 locks fixos por faixa de guild e lock global adquirido primeiro. Repetir a mesma reserva é idempotente; outra sessão ou guild não a libera. A reserva não tem TTL automático: a futura orquestração deve liberar após confirmar a limpeza necessária. Uma nova instância começa vazia; não há coordenação entre processos. Esta primitiva ainda não cria sessões nem verifica a SFU.
- Tickets, contextos, filas, leases, deduplicação e respectivas regras de TTL pertencem às tarefas dos fluxos correspondentes. O comportamento de limpeza SFU antes de readiness é T-19.

## Evidências executadas

| Verificação | Resultado | Limite |
|---|---|---|
| `clean verify` padrão | 27 testes, 0 falhas/erros, 5 pulados; build aprovado | Integração PostgreSQL opt-in, dois testes SFU e dois testes HTTP de auxiliares ausentes pulados |
| `clean verify -Plivekit-proof` | 27 testes, 0 falhas/erros, 4 pulados; build aprovado | PostgreSQL opt-in, dois SFU reais e auxiliar capture ausente pulados |
| `clean verify -Pcapture-proof` | 42 testes, 0 falhas/erros, 4 pulados; build aprovado | PostgreSQL opt-in, dois SFU reais e auxiliar LiveKit ausente pulados |
| `PostgresPersistenceIntegrationTest` isolado | 1 teste aprovado com PostgreSQL **16.15** local, cluster descartável em loopback, encerrado e removido | Não é PostgreSQL 18.6 do Compose |
| `RuntimeCapacityStoreTest` | 3 testes aprovados em cada suíte: 40 tentativas concorrentes em cinco guilds, liberação obsoleta/incompatível e nova instância vazia | Não prova limpeza SFU ou execução distribuída |

O teste PostgreSQL inicia a aplicação real, aplica V1, grava um bot e dois vínculos via repositórios JPA, verifica unicidades, PK, FK, NOT NULL, recusa exclusão do bot vinculado e inventário exato das tabelas `discord_bot`, `bot_guild_binding` e `flyway_schema_history`. Um segundo contexto de aplicação valida novamente as migrações, confirma dados e uma única aplicação de V1. A nova instância de contexto não é reinício de processo ou de servidor PostgreSQL. O teste recusa schema `public` não vazio antes de alterar dados.

A primeira tentativa dos dois perfis no sandbox falhou ao criar conexões loopback locais; a repetição autorizada passou. O daemon Docker estava indisponível. PostgreSQL 16 foi executado temporariamente fora dessa restrição; credenciais aleatórias foram usadas somente no ambiente local temporário e removidas. Os testes HTTP que precisam simular banco indisponível desativam Flyway, validação do schema e acesso aos metadados JDBC somente nesses testes, preservando a inicialização normal. As contagens acima incluem testes repetidos entre builds.

## Reprodução

Com Java 21 e Maven Wrapper na raiz, execute:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean verify
.\backend\mvnw.cmd -f backend/pom.xml -Plivekit-proof clean verify
.\backend\mvnw.cmd -f backend/pom.xml -Pcapture-proof clean verify
```

Para PostgreSQL 18.6 real via Testcontainers, com Docker disponível e acesso à imagem `postgres:18.6-bookworm`:

```powershell
$env:T06_POSTGRES_TEST_ENABLED = 'true'
.\backend\mvnw.cmd -f backend/pom.xml '-Dtest=PostgresPersistenceIntegrationTest' test
Remove-Item Env:T06_POSTGRES_TEST_ENABLED
```

Como alternativa, fornecer `T06_POSTGRES_JDBC_URL`, `T06_POSTGRES_USERNAME` e `T06_POSTGRES_PASSWORD` no ambiente para um banco **descartável**, exclusivamente em loopback e com schema `public` vazio. O teste grava dados e Flyway nesse banco; nunca apontar para dados existentes. Não imprimir nem versionar a senha. Limpar as variáveis após o teste.

## Pendente

- Repetir o teste com PostgreSQL 18.6 e testar reinício do processo/banco no ambiente-alvo. A execução em 16 não homologa a versão do Compose.
- AC-24 integral depende de configurar o bot e dos fluxos reais de transmitir, assistir e sair, além de inspeção de backup; eles ainda não existem. T-06 não é declarada concluída.
- T-07 implementa ticket/contexto e T-19 a limpeza de salas antigas antes da readiness. Não há garantia de limpeza ou prova de acesso inválido após reinício por esta entrega.
- Homologações G1/T-03 e G2/T-04, mídia real, TURN e nove espectadores permanecem pendentes. Doubles e testes de build não comprovam mídia real.
