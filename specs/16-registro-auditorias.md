# 16 — Registro de auditorias e inconsistências

Estado: **registro de acompanhamento**, criado em 29/10/2026. Este documento reúne os achados para correção e validação futuras e aceita novos registros de auditorias posteriores. Não significa que qualquer correção aqui descrita já tenha sido implementada. As especificações de produto, segurança e contratos continuam em vigor.

## Como manter este registro

- Preservar o identificador e o histórico de cada achado. Registrar novas auditorias com data, escopo, estado do código examinado e evidências; acrescentar achados novos sem apagar os anteriores.
- Para cada achado, registrar severidade, estado, comportamento observado ou hipótese, condições, impacto, arquivos e linhas examinados, correção proposta, critérios de verificação e limitações. Referências a linhas retratam a revisão e podem mudar; conferir o código ao trabalhar no item.
- Usar estados **aberto**, **em correção**, **corrigido, aguardando validação**, **validado** ou **descartado**, com data, justificativa e evidência em cada transição. Não marcar como validado somente porque um build passou; apontar os testes pertinentes e os resultados reais.
- Registrar a revisão ou commit que introduziu a correção, quando existir, e vincular requisitos, OpenAPI e cenários atualizados quando houver mudança de comportamento. Não substituir prova real por teste simulado.
- Achados sobre código experimental devem indicar essa fronteira. Uma funcionalidade planejada e ainda não implementada não é, por si só, defeito; uma barreira ausente em código já exposto deve ser avaliada.
- Não registrar credenciais, tickets reais, mídia ou dados sensíveis nas evidências. Não iniciar serviços pagos, publicar ou registrar contas para auditar.

## Auditoria AUD-2026-10-29

**Escopo:** estado do repositório em 29/10/2026, incluindo alterações rastreadas e arquivos ainda não commitados, com revisão de segurança, persistência, memória, integrações e provas de mídia, frontend, contratos e testes. A T-07 estava em implementação; emissão confiável por interaction Discord e verificação real ainda dependiam de T-08/T-09. Os auxiliares de mídia eram experimentais e separados do produto.

**Verificações executadas:** `verify` do backend com Java 21 em cópia temporária, com acesso local ao loopback: padrão, 43 testes no total e 5 ignorados; `livekit-proof`, 43 e 4 ignorados; `capture-proof`, 58 e 4 ignorados. Zero falhas ou erros nas três execuções. Suíte frontend de captura: 67 aprovados; proxy de captura: 9 aprovados, incluindo Vite e servidores locais temporários. A primeira tentativa Java no sandbox não abriu loopback; a repetição com acesso local passou. A inspeção da migração, entidades e repositórios encontrou as tabelas de produto `discord_bot` e `bot_guild_binding`, mais metadados técnicos Flyway.

**Limitações:** Docker indisponível; não foram executados PostgreSQL 18, LiveKit real, Discord real, limpeza SFU após reinício, captura entre máquinas, áudio real, TURN ou nove espectadores. Os testes opt-in correspondentes foram ignorados. Os cenários Gherkin ainda não tinham step definitions. A evidência simulada não homologa mídia ou rede. As condições abaixo foram identificadas por inspeção; os cenários específicos de falha ou concorrência não foram reproduzidos como parte desta auditoria, salvo indicação expressa.

### AUD-01 — Sessões de bootstrap CSRF sem limite de criação

- **Severidade:** alta. **Estado:** aberto.
- **Confirmação e condições:** `GET /api/csrf` público materializa o token e cria sessão técnica; repetir a requisição sem conservar cookies cria novos registros. O limitador existente age sobre a troca de ticket, não sobre esse bootstrap. A sessão expira após 30 minutos de inatividade. Confirmado por inspeção, sem teste de exaustão nesta auditoria.
- **Impacto:** um cliente que alcance o endpoint pode acumular sessões e consumir memória até degradar ou interromper o backend. A configuração examinada escuta em loopback; alcance remoto depende de proxy ou exposição posterior.
- **Referências:** [CsrfController.java](../backend/src/main/java/br/com/compartilhamentodiscord/controller/CsrfController.java), linhas 21–27; [SecurityConfiguration.java](../backend/src/main/java/br/com/compartilhamentodiscord/config/SecurityConfiguration.java), linhas 52–55 e 69–75; [ExchangeRateFilter.java](../backend/src/main/java/br/com/compartilhamentodiscord/security/ExchangeRateFilter.java), linhas 24–32; [application.yml](../backend/src/main/resources/application.yml), linhas 18–30. Contrasta com limites de ações de [05](05-identidade-e-seguranca.md) e limites de estruturas em memória de [07](07-dados-e-estados.md).
- **Correção proposta:** limitar criação de sessões antes da alocação, combinando controle de taxa e capacidade global de pré-acesso com expiração apropriada. Considerar o endereço de origem e a configuração efetiva de proxy confiável sem confiar em cabeçalhos arbitrários; preservar CSRF, negação por padrão e funcionamento de usuários legítimos.
- **Critérios de validação:** testes HTTP com muitos GETs sem cookie e concorrência devem comprovar limite de criação e uso limitado de capacidade; após expiração ou liberação, novas sessões legítimas devem funcionar. Verificar também que o token continua vinculado à sessão e que o controle não depende de um único IP.

### AUD-02 — Trocas concorrentes de tickets distintos podem inverter a substituição

- **Severidade:** média. **Estado:** aberto.
- **Confirmação e condições:** para o mesmo usuário e sessão, A é consumido antes de B; a verificação externa de A atrasa, B é verificado e ativado, e A conclui depois. A ativação de A remove o contexto de B porque o código ordena a substituição pela conclusão da verificação. Confirmado pela ordem das operações; não reproduzido por teste específico. A configuração padrão atual retorna `UNAVAILABLE`, de modo que ativações bem-sucedidas dependem de verificador simulado ou de integração futura confiável.
- **Impacto:** o contexto derivado do ticket anterior pode substituir o acesso mais recente, contrariando a regra de substituição pelo novo ticket consumido. O teste de concorrência de um mesmo ticket não cobre duas credenciais distintas com verificações fora de ordem.
- **Referências:** [AccessService.java](../backend/src/main/java/br/com/compartilhamentodiscord/service/access/AccessService.java), linhas 18–31; [AccessStore.java](../backend/src/main/java/br/com/compartilhamentodiscord/service/access/AccessStore.java), linhas 105–134; [05 — Contexto de acesso web](05-identidade-e-seguranca.md).
- **Correção proposta:** associar uma sequência ou geração ao consumo por usuário e sessão e impedir que uma ativação mais antiga substitua uma mais recente. Preservar uso único, falha fechada e regra de não restaurar ticket após falha de verificação.
- **Critérios de validação:** teste determinístico com dois tickets, sessões de navegador e barreira que atrase a verificação de A até B ativar; A não pode substituir B. Cobrir também falha de verificação, expiração e consumo concorrente do mesmo ticket. A jornada Discord real permanece dependente das integrações próprias.

### AUD-03 — Limpeza de criação incerta pode excluir outra sala na prova de captura

- **Severidade:** média. **Estado:** aberto. **Fronteira:** auxiliar experimental `capture-proof`.
- **Confirmação e condições:** duas abas autorizadas usam a capacidade de apresentador. A criação de A atrasa ou tem resposta perdida; B cria a sala ativa; A recebe timeout ou erro de resultado incerto e executa limpeza. O `DELETE` não identifica a tentativa, e o backend seleciona a sala ativa. Confirmado por inspeção; a intercalação específica não foi executada.
- **Impacto:** a limpeza de A pode encerrar a sala de B, interrompendo sua prova. O [contrato auxiliar](api/capture-proof.openapi.json) descreve exclusão da sala própria.
- **Referências:** [capture-session.ts](../frontend/src/app/features/dev-capture/capture-session.ts), linhas 157–167 e 273–282; [CaptureProofService.java](../backend/src/capture-proof/java/br/com/compartilhamentodiscord/service/CaptureProofService.java), linhas 143 e 158–168; contrato auxiliar, linha 131.
- **Correção proposta:** identificar tentativa ou geração de criação e restringir a limpeza à mesma identidade, inclusive quando a resposta da criação for incerta; manter operação idempotente e limpeza recuperável.
- **Critérios de validação:** simular A atrasada, B criada e limpeza de A, comprovando que B continua ativa. Cobrir resposta perdida após alocação, repetição de limpeza, 409 e falha de exclusão; manter visível limpeza não confirmada.

### AUD-04 — Prova de captura pode aguardar indefinidamente publicação perdida

- **Severidade:** baixa. **Estado:** aberto. **Fronteira:** auxiliar experimental de captura.
- **Confirmação e condições:** após estado `LIVE`, se a SFU deixa de confirmar vídeo ou áudio enquanto a track local permanece viva e não ocorre evento de reconexão, o monitor muda para `waiting` e consulta novamente sem prazo, mantendo captura e sala. O fluxo está confirmado por inspeção e comportamento de teste com doubles; ocorrência com SFU real ainda precisa de validação. Uma publicação ausente na SFU não prova, por si, que a track local terminou.
- **Impacto:** a prova pode ficar sem mídia confirmada e sem recuperação automática, mantendo recursos de captura ativos. Contrasta com C-11 em [00](00-principios.md) e com a necessidade de estado e tratamento coerentes em [03](03-captura-e-midia.md).
- **Referências:** [capture-session.ts](../frontend/src/app/features/dev-capture/capture-session.ts), linhas 201–231; [capture-session.test.mjs](../frontend/tests/capture-session.test.mjs), teste de monitor sem `live`.
- **Correção proposta:** estabelecer prazo de reconciliação e encerrar a prova com liberação de recursos se a ausência de publicação persistir. Diferenciar ausência ou silenciamento da publicação de conteúdo sonoro silencioso com track válida.
- **Critérios de validação:** simular perda de uma publicação com track local viva e comprovar encerramento após o prazo e limpeza; testar recuperação dentro do prazo e áudio silencioso com track válida. Validar separadamente com SFU real quando houver ambiente apropriado.

### AUD-05 — Criação incerta na prova LiveKit deixa sala sem limpeza pela interface

- **Severidade:** baixa. **Estado:** aberto. **Fronteira:** auxiliar experimental `livekit-proof`.
- **Confirmação e condições:** se a sala for alocada no backend/SFU mas a resposta da criação for perdida ou expirar, o backend conserva o nome para limpeza; o frontend registra o nome somente depois da resposta e não o inclui na sua limpeza. Confirmado por inspeção; o cenário específico não foi reproduzido.
- **Impacto:** a interface pode encerrar com erro genérico sem indicar limpeza incerta, e a vaga contada no processo permanece ocupada até limpeza administrativa. Repetições podem esgotar o limite de quatro salas ativas da prova. Isso limita a recuperação descrita em [frontend/README.md](../frontend/README.md).
- **Referências:** [LiveKitProofService.java](../backend/src/livekit-proof/java/br/com/compartilhamentodiscord/service/LiveKitProofService.java), linhas 30–48 e 81–100; [livekit-proof-service.ts](../frontend/src/app/features/dev-livekit/livekit-proof-service.ts), linhas 328–332 e 419–440.
- **Correção proposta:** correlacionar a tentativa de criação para reconciliação e limpeza segura após resultado incerto; informar a pendência até exclusão confirmada, sem excluir salas de outras tentativas.
- **Critérios de validação:** simular sala alocada com resposta perdida e verificar que a interface informa a incerteza e consegue confirmar a limpeza e liberar a vaga; conferir isolamento de outras salas e repetição idempotente.

## Modelo para auditorias futuras

Para cada nova auditoria, incluir data, escopo e estado do repositório; verificações executadas com resultados e testes ignorados; limitações; e achados identificados por IDs estáveis. Cada achado deve conter severidade e estado, evidência ou hipótese, condições de reprodução, impacto, referências, regra ou contrato aplicável, correção proposta e critérios de validação. Registrar riscos ainda não comprovados separadamente dos defeitos confirmados. Manter pendências de PostgreSQL, Discord e mídia real explícitas até que sejam efetivamente verificadas.
