# 10 — Aceitação e estratégia de validação
Estado: bootstrap **T-01 validado localmente em 26/10/2026**; [evidência e limites](../tests/manual/T-01-bootstrap.md). Os critérios de produto abaixo e as POCs de mídia continuam **não executados**. Saúde e build não comprovam funcionamento da transmissão.

T-02 concluída: cinco testes JUnit aprovados (três locais e dois contra SFU real) e dez verificações da ferramenta Angular aprovadas, com RTP de vídeo/áudio recebido e decodificado, grants, escopo e exclusão/reuso. G1 e G2 completos continuam pendentes: captura real, nove espectadores e TURN não foram executados. [Relatório detalhado](../tests/manual/T-02-livekit.md).

T-03 implementada em prova restrita, com testes automatizados de validação/liberação de tracks, publicação parcial, cancelamento concorrente, confirmação SFU e encerramento. Os testes usam doubles para falhas; não comprovam captura Windows nem som audível. **G1 permanece pendente**: decisões externas e participação nas duas máquinas foram adiadas pelo usuário. [Resultados e matriz a executar](../tests/manual/T-03-captura-real.md).

T-04 implementa a extensão experimental de nove identidades, limite concorrente, saída/repetição, TURN/TLS opt-in e diagnóstico ICE. **Validação parcial; G2 não homologado.** Homologação adiada pelo usuário em 27/10/2026. [Comandos, resultados desta execução e matriz pendente](../tests/manual/T-04-multiespectador-turn.md). Resultados históricos da T-02 e relatos manuais sem matriz/duração/som audível não são atribuídos à T-04.

T-06 implementada com validação parcial: PostgreSQL 16 real e concorrência da primitiva de capacidade aprovados; PostgreSQL 18 e AC-24 integral com fluxos/backup permanecem pendentes. [Evidências](../tests/manual/T-06-persistencia-memoria.md).

## Portões de avanço
- G0: stack/escopo e contratos consistentes. Atual entrega prepara isso.
- G1 / POC-01: duas máquinas Windows, Chrome e Edge, tela inteira + áudio de sistema real. 15min por combinação; som de teste audível no receptor, sem microfone; cancelamento e encerramento funcionando.
- G2 / POC-02: nove espectadores, limites, TURN e isolamento da geração de mídia; sala apagada não pode ser recriada por JWT antigo, inclusive token renovado. Validar SDK JVM e room.auto_create=false na versão escolhida.
- G3: jornada Discord -> ticket privado -> captura -> anúncio -> assistir -> encerrar, com autorização backend.
- G4: teste de duas horas e capacidade/custo aprovados antes de convidar grupo para uso regular.

G1/G2 falharam: registrar causa e pedir decisão se precisar app auxiliar, fornecedor, orçamento ou alterar compatibilidade. Não prosseguir com premissa comprovadamente falsa.

## Catálogo de aceite e rastreabilidade
| Teste | Critério objetivo | Requisitos |
|---|---|---|
| AC-01 | Instalação/configuração permitidas só a administrador; canal e permissões corretos | RF-01, RF-02 |
| AC-02 | Comando cria uma reserva e link privado de dono, sem live pública antecipada | RF-03 |
| AC-03 | Ticket privado válido abre o site sem login, com papel/servidor/sessão derivados da interaction | RF-04, RF-27 |
| AC-04 | URL pública sem ticket/contexto, outro servidor e guild não vinculada não liberam conteúdo/token | RF-04, RF-10, RF-24 |
| AC-05 | Espectador/admin não obtêm token de publicação da sessão alheia | RF-05 |
| AC-06 | Captura pede seleção só após clique e para ao cancelar/encerrar | RF-06 |
| AC-07 | Vídeo e áudio real chegam em Chrome/Edge Windows nos ambientes registrados | RF-07 |
| AC-08 | Ausência de áudio/cancelamento não gera LIVE nem anúncio | RF-08, RF-16 |
| AC-09 | Apenas fatos verificados da SFU confirmam ambas as tracks e anúncio | RF-09 |
| AC-10 | Botão abre sessão correta; player tem som liberável, volume/tela cheia/sair | RF-10, RF-11 |
| AC-11 | Corrida de criação/join não excede 1 sessão/servidor ou 9 espectadores | RF-12 |
| AC-12 | Contador exclui apresentador, reservas e abas antigas; reconcilia após falha | RF-13 |
| AC-13 | Queda breve recupera; queda >30s encerra e libera tracks localmente | RF-14 |
| AC-14 | Parar local/navegador/bot derruba mídia e atualiza mensagem; repetir é seguro | RF-15 |
| AC-15 | Vídeo/áudio terminados e travamento de fonte geram estado coerente | RF-16 |
| AC-16 | Saída do servidor e verificação indisponível retiram acesso no prazo | RF-17 |
| AC-17 | Status e encerrar respeitam papel e servidor; repetição é idempotente | RF-18 |
| AC-18 | Assinaturas inválidas/replay, eventos fora de ordem, 429/5xx não corrompem estado | RF-19 |
| AC-19 | HTTPS/CSRF/grants/segredos/troca de ticket e ausência de gravação verificados | RF-20 |
| AC-20 | Reserva expira em 10min; reinício invalida runtime e exclui salas antigas antes de readiness | RF-21 |
| AC-21 | Métricas permitem aferir consumo/capacidade sem mídia ou credenciais | RF-22 |
| AC-22 | Fluxos de captura, erro e player utilizáveis por teclado e em pt-BR | RF-23 |
| AC-23 | Sala/geração aposentada não recebe mídia futura nem permite recriação com token antigo | RF-25 |
| AC-24 | PostgreSQL contém somente bot/vínculo; iniciar/assistir/sair não cria registros de usuário, sessão, acesso ou fila | RF-26 |
| AC-25 | Ticket expira em 60s e só uma troca concorrente tem sucesso; papel/servidor/destino não são alteráveis | RF-27 |

Exemplos executáveis a implementar em [tests/aceite.feature](tests/aceite.feature). O arquivo Gherkin descreve comportamento; ainda não há step definitions.

## Automação adequada
- Backend/JUnit: transições e autorização, relógio controlado para prazos, idempotência e falhas de integrações.
- Spring Boot Test + Testcontainers PostgreSQL real: PK/FK e unicidade de bot/vínculo, migrations e ausência de tabelas de usuários/sessões/filas. Concorrência de sala, vaga, ticket e fila é testada em services de memória com relógio controlado.
- API/integrações: Ed25519 com corpo original, webhook SDK, interaction/ticket simulados somente em ambiente de teste, CSRF SPA e política de erros sem vazamento.
- Arquitetura: ArchUnit impede controller acessando repository e retornando entity.
- Angular: estados de captura/erro, atualização de sessão, liberação de tracks e volume; não testar somente detalhes de DOM sem significado.
- Playwright: jornada de UI com mídia sintética. Mock de getDisplayMedia não comprova captura/áudio de sistema; G1 continua manual real.
- Proibir endpoint de token livre, login falso ou allowlist ignorada na configuração de produção.

## Homologação e medição
Matriz: Windows 11 + Chrome estável e Edge estável; Windows 10 somente se houver máquina disponível e for incluída explicitamente na lista homologada. Windows sem versão específica foi aprovado; não alegar cobertura de todas as versões.
Registrar OS/build, navegador/build, fonte (monitor/janela/aba), opção de áudio, resultado, hardware, rede, versão SFU/SDK, data e operador.
Teste de carga: um apresentador e nove clientes com identidades distintas, backend/banco/SFU juntos; observar recursos por duas horas. Testar décimo espectador simultâneo e duas chamadas concorrentes para última vaga.
Rede: duas conexões externas distintas; repetir com UDP bloqueado para comprovar TURN. Registrar quando portas 5349/443 ou NAT impedirem fallback.
Métricas: no mínimo 20 entradas para tempo inicial; 100 amostras de atraso usando relógio de teste na tela e máquinas sincronizadas, declarando erro da medição; p95, sem excluir falhas. Metas em 01.
Carga registra CPU, heap/RSS, memória PostgreSQL/SFU, bitrate, egress e perdas. Simular congestionamento e confirmar que som não some silenciosamente.
Som de sistema: testar com Discord desktop aberto, avisar duplicação de voz, usar som sintético e não registrar conversa dos amigos.

## Evidência de entrega futura
Relatório em tests/manual ou tests/performance com resultados e limitações, sem segredos. Checklists de deploy/restore e custo real. Documentar testes não executados separadamente dos aprovados.

## Regressão da base de segurança T-05

Evidência e limitações em [T-05 — segurança do backend](../tests/manual/T-05-seguranca-backend.md). Testes com servidores Spring reais em loopback verificam liveness pública, gestão separada, negação sem contexto, ausência de login/Basic, bootstrap CSRF ligado à sessão, Origin exata, ausência de CORS externo, envelope dos erros e requestId próprio. Cabeçalhos recebidos de identidade/papel e cookies arbitrários não podem produzir autorização.

Executar os três builds Java e verificar ausência das classes auxiliares no JAR normal. Nos artefatos experimentais, testar opt-in ausente, profiles prod/production, rotas/métodos extras e as barreiras existentes; T-03 deve continuar negando administração ao espectador. JSON malformado/entrada inválida e falha SFU devem retornar erros sanitizados. Nenhum endpoint de diagnóstico/autorização fictícia pode ser incluído no artefato.

CSRF técnico e negação sem contexto são verificáveis antes de tickets. Troca/rotação/expiração do acesso e associação Discord permanecem AC-03/25 nas tarefas próprias; esses resultados não podem ser inferidos da T-05. Cabeçalhos da API também não comprovam proteção da página Angular ou HTTPS de produção. G1/POC-01 e G2 continuam pendentes.

## Verificações adicionais desta revisão
AC-24 inspeciona schema e SQL emitido: somente discord_bot/bot_guild_binding para produto e flyway_schema_history técnico. Nenhum INSERT/UPDATE de usuários, transmissão, contexto, ticket ou fila. Backup contém apenas esses vínculos.
AC-25 inclui ticket repassado: documentar que o primeiro portador pode consumi-lo; não afirmar prova de identidade do navegador. Checar expiração, uso concorrente, escopo e boot anterior. Link comum não gera interaction: testar botão custom_id + resposta privada separadamente.
Reinício: vínculos sobrevivem; todos os tickets/cookies antigos falham e salas antigas são apagadas antes de acesso novo. Mensagem velha pode ficar com status antigo, mas clicar nunca reabre a transmissão.

## Evidência T-07

A evidência executada e suas limitações são registradas em [T-07 — ticket e acesso](../tests/manual/T-07-ticket-acesso.md). A emissão a partir de uma interaction assinada e a verificação real de vínculo e associação continuam condicionadas às T-08/T-09; nenhum teste isolado substitui a jornada AC-03 nem confirma mídia real. A reconciliação SFU antes de readiness permanece T-19.
