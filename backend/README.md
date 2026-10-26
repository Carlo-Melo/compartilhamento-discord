# Backend — Java 21 / Spring Boot
Estrutura reservada, sem classes executáveis ou pom.xml neste estágio.

Pacote base proposto: br.com.compartilhamentodiscord.
Camadas: controller (HTTP/entrada) -> service (casos de uso e autorização) -> repository (somente cadastro bot/vínculo) -> PostgreSQL. Estado operacional em services de memória, sem JPA.
entity representa persistência; dto/request e dto/response representam contratos; mapper faz conversão; integration/discord e integration/livekit encapsulam provedores.
security trata tickets e contexto web em memória/CSRF, sem login OAuth; config reúne configuração tipada; exception padroniza erros; scheduler trata limpeza, reconciliação e fila volátil.
Não colocar regra em controller, repository ou no cliente Angular. Não manter transação de banco aberta durante chamadas externas.

Proposta de bibliotecas para bootstrap: Maven Wrapper, Spring MVC, Spring Security, Data JPA, Validation, Actuator, Flyway, driver PostgreSQL; SDK oficial LiveKit JVM quando validado. Conferir versões em specs/06-arquitetura.md.
Testes de backend em src/test/java, separados por service, repository, controller, security e integration.
