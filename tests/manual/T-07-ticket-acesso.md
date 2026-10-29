# T-07 — ticket e acesso web em memória

Data: **29/10/2026**. Estado: **em implementação e validação; não concluída**. Rastreabilidade: RF-04/RF-27, AC-03/AC-25.

## Escopo e fronteiras

O objetivo é uma credencial de 256 bits, TTL de 60 segundos e uso único atômico, trocada mediante Origin e CSRF por contexto vinculado à sessão em memória, com rotação do identificador. O ticket é bearer: se compartilhado antes do consumo, o primeiro portador pode utilizá-lo. Não comprova a identidade da pessoa no navegador.

A integração real depende da interação assinada da T-09 e da validação de vínculo/associação da T-08; o frontend de entrada por fragmento pertence à T-11. Não há justificativa para rota pública que emita tickets com identidade, escopo ou papel escolhidos pelo navegador. Limpeza das salas antigas da SFU antes de readiness pertence à T-19 e não é declarada implementada.

## Implementação

- Store em memória com códigos de 256 bits gerados aleatoriamente, apenas digest SHA-256 armazenado, vencimento em 60 segundos e marcação atômica de consumo. A falha de revalidação não restaura a credencial; uma nova instância parte sem os registros anteriores.
- Limites de 10 mil tickets e 10 mil contextos, emissão de até cinco por minuto por usuário/sessão e 30 por guild; limpeza periódica a cada cinco segundos e também nas operações do store. A troca é limitada a 10 tentativas por minuto por endereço remoto e por sessão de bootstrap.
- Contextos expiram após 30 minutos ociosos ou oito horas absolutas; o novo consumo para o mesmo usuário/sessão substitui o anterior. A troca usa CSRF e Origin existentes, rotaciona a sessão de bootstrap e oferece somente destino interno fixo. `GET /api/access/session` consulta o contexto autorizado.
- A verificação de vínculo, associação e estado da sessão tem implementação padrão indisponível, falhando fechada até T-08/T-09. A implementação não disponibiliza rota pública de emissão nem efetiva autorização com identidades escolhidas pelo navegador. Rotas de operações futuras permanecem bloqueadas.

## Evidência

| Verificação | Resultado | Limite |
|---|---|---|
| `clean verify` backend padrão, Java 21 e Maven portátil offline | 43 testes executados no total: 38 aprovados, 5 ignorados, zero falhas ou erros | Os testes ignorados dependem de integrações ou auxiliares opt-in |
| `clean verify -Plivekit-proof` | 43 testes no total: 39 aprovados, 4 ignorados, zero falhas ou erros | Não comprova SFU real nem mídia |
| `clean verify -Pcapture-proof` | 58 testes no total: 54 aprovados, 4 ignorados, zero falhas ou erros | Não comprova captura real ou homologação G1/G2 |
| Testes HTTP da troca e segurança | 6 testes HTTP de acesso e 10 da base de segurança aprovados na suíte padrão | A fronteira confiável/associação é simulada nos testes e indisponível na configuração real; não comprova jornada Discord |
| `AccessProcessRestartHttpTest` isolado | 1 teste aprovado entre dois processos JVM/Tomcat reais; o ticket e o contexto do primeiro processo não autorizam acesso no segundo | Emissão e verificador substituídos somente no harness; banco e Flyway desativados. Não comprova PostgreSQL, Discord real ou limpeza SFU |
| Reabertura de contexto Spring | Verificada na suíte | Mesmo processo, não é reinicialização do processo ou sistema operacional |
| Validação sintática de `specs/api/openapi.json` com `python -m json.tool` | Aprovada | Confere JSON, não valida todos os aspectos semânticos de OpenAPI |

O comando foi executado na pasta `backend`, usando `../work/tools/apache-maven-3.9.16/bin/mvn.cmd -o -Dmaven.repo.local=../work/maven/repository clean verify` e JDK `../work/tools/jdk-21.0.12.1+1`. Os perfis adicionais usaram a mesma invocação com `-Plivekit-proof` e `-Pcapture-proof`, respectivamente. Os cenários Gherkin não têm step definitions; testes simulados e build não comprovam mídia ou uma interação Discord real.

## Pendências preservadas

- A T-06 foi validada parcialmente com PostgreSQL 16: ainda faltam PostgreSQL 18 e o AC-24 integral com fluxos posteriores e backup.
- As homologações de mídia real T-03/G1 e T-04/G2 seguem pendentes; testes simulados não comprovam captura, áudio, nove espectadores ou TURN.
- A aceitação integral de AC-03 exige a interação privada Discord e a remoção do fragmento no navegador; AC-25 deve ser avaliado com as fronteiras confiáveis efetivas.
