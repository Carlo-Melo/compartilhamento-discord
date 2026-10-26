# language: pt
@T-01 @manual
Funcionalidade: Base local executável e integração real de saúde
  Critérios manuais de bootstrap, sem step definitions automatizados.
  Evidência executada em tests/manual/T-01-bootstrap.md.

  Cenário: Angular recebe a saúde real do backend
    Dado o backend Java 21 e o Angular em execução local
    Quando a página inicial consulta /api/health/live pelo proxy de desenvolvimento
    Então recebe a resposta do backend em /health/live
    E exibe Backend disponível com status UP

  Cenário: Falha real do backend e nova tentativa
    Dado que a página já recebeu uma resposta real UP
    Quando o processo backend é encerrado e a saúde é consultada novamente
    Então a página exibe Backend indisponível e permite Tentar novamente
    Quando o backend é reiniciado e o usuário tenta novamente
    Então a página volta a exibir Backend disponível

  Cenário: Readiness diferencia falha do banco de liveness
    Dado o PostgreSQL de desenvolvimento com conexão JDBC real disponível
    E readiness retorna 200 com status UP em 127.0.0.1:8081/health/ready
    Quando somente o container PostgreSQL deste projeto é parado
    Então readiness retorna 503 com status DOWN
    E 127.0.0.1:8080/health/live continua retornando 200 com status UP
    Quando o banco é recuperado preservando o volume
    Então readiness volta a retornar 200 com status UP

  Cenário: Gestão e banco permanecem locais e sem dados de produto
    Dado o Compose e o backend configurados para loopback
    Então a porta 8080 não oferece /health/ready
    E o Actuator não expõe env
    E o PostgreSQL não possui tabelas de produto
