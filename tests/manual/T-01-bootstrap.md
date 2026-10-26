# Evidência de bootstrap — T-01
**Resultado: aprovado**, execução em 26/10/2026, Windows/PowerShell, Docker Desktop com containers Linux. Somente T-01; nenhuma POC ou teste de produto marcado como aprovado.

## Ambiente e preparação
- Inicialmente disponíveis: Temurin 17.0.20, Node 22.22.0/npm 10.9.4, Docker Engine 29.7.2 e Compose 5.4.0; sem Maven global ou cliente psql no PATH.
- Usados para a etapa: Temurin **21.0.12.1+1**, Node **24.22.1**/npm **11.19.1**, Maven Wrapper **3.3.4** com Maven **3.9.16**; runtimes portáteis em `work/tools/`, ignorados. Checksums conferidos nas fontes oficiais.
- Aplicações: Spring Boot **4.1.2**, Angular **22.2.3**, CLI/build **22.2.4**, TypeScript **6.0.3**, RxJS **7.8.2**; PostgreSQL **18.6-bookworm**. [Inventário e compatibilidade](../../specs/14-bootstrap-inventario.md).
- PostgreSQL nativo já ocupava `5432`; foi preservado. Compose deste projeto usou `127.0.0.1:5433` no `.env` ignorado, com senha local aleatória não exibida. Dados em named volume `compartilhamento-discord-dev_postgres-data`, sem bind mount no OneDrive.
- Rede e Docker eram inacessíveis no sandbox. Downloads, builds com dependências, containers e servidores foram executados com acesso local autorizado. O primeiro servidor Angular, iniciado no sandbox, gerou timeout; reiniciado fora dele, respondeu corretamente. Não houve mudança de firewall ou exposição pública para contornar isso.

## Verificações executadas
| Verificação real | Resultado |
|---|---|
| `backend/mvnw.cmd ... clean verify` com JDK 21 | BUILD SUCCESS; JAR executável gerado. Sem testes unitários artificiais; pastas de testes preservadas. |
| `npm ci` e `npm run build` com Node 24/npm 11 | Aprovados; bundle inicial 219,55 kB. Lockfile utilizado. |
| Auditoria npm após patch Vite 8.3.5 | 0 vulnerabilidades reportadas na execução; não é garantia permanente. |
| `docker compose ... config --quiet` e `up -d --wait` | Aprovados; container saudável. |
| psql no container, conexão TCP autenticada `SELECT 1` | Retornou 1; servidor 18.6. `public` sem tabelas. |
| `GET 127.0.0.1:8080/health/live` | 200, `{"status":"UP"}` e `Cache-Control: no-store`. |
| `GET 127.0.0.1:8081/health/ready` | 200, `{"status":"UP"}`. Hikari abriu conexão `org.postgresql.jdbc.PgConnection` real. |
| Parar somente PostgreSQL Docker | Ready 503 `DOWN`; live 200 `UP`. |
| Recuperar PostgreSQL pelo Compose, preservando volume | Ready voltou a 200 `UP`. |
| Gestão: `/health/ready` na porta API e `/env` na gestão | Ambos 404; detalhes da saúde não expostos. |
| `GET 127.0.0.1:4200/` e `/api/health/live` | HTML 200 e proxy 200 `{"status":"UP"}` vindo do backend real. |
| Navegador integrado na página Angular | Exibiu **Backend disponível. Resposta recebida: UP.** |
| Encerrar o processo backend e clicar Verificar novamente | Exibiu **Backend indisponível**, com **Tentar novamente** habilitado. |
| Reiniciar backend e clicar Tentar novamente | Voltou a exibir **Backend disponível**, sem mock ou interceptação de resposta. |
| `git diff --check`, JSON OpenAPI e importação `.env` | Sem erro; `.env`, caches, builds e runtimes ignorados pelo Git. |

Os [cenários do bootstrap](../../specs/tests/bootstrap.feature) descrevem essas verificações manuais; não têm step definitions e não são apresentados como suíte automatizada. Templates Angular/TypeScript foram compilados; a transição de carregamento está implementada com timeout de 8s e botão desabilitado, mas o limite de 8s não foi medido com rede artificialmente lenta.

Screenshot local da recuperação: `tests/artifacts/T-01-bootstrap.png` (ignorado pelo Git, sem dados pessoais/segredos). Os processos temporários foram encerrados ao fim da validação, preservando o volume de desenvolvimento e os serviços anteriores da máquina.

## Limites
Readiness nesta etapa verifica aplicação + JDBC; a limpeza de salas SFU depende da T-19. Sem bot, tickets, captura, mídia, entidades JPA, migrations, login OAuth, sessão JDBC ou tabelas de produto. Chrome/Edge Windows e áudio real precisam de homologação nas POCs; esta execução do navegador integrado comprova somente a página de saúde. Não houve deploy, registro de contas ou contratação.

T-01 concluída. Próxima tarefa T-02, não iniciada.
