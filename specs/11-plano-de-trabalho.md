# 11 — Plano de implementação por entregas
**T-01 e T-02 CONCLUÍDAS em 26/10/2026**, com [evidência de bootstrap](../tests/manual/T-01-bootstrap.md) e [integração LiveKit real](../tests/manual/T-02-livekit.md). **T-03 IMPLEMENTADA, HOMOLOGAÇÃO PENDENTE**: [resultado e preparação externa](../tests/manual/T-03-captura-real.md). T-04 a T-24 continuam **NÃO INICIADAS**. Revisão 1.2 substitui login OAuth/persistência operacional por acesso emitido pelo bot e memória.

| ID | Entrega concreta | Depende de | Requisitos / evidência |
|---|---|---|---|
| T-01 | Bootstrap Java 21/Spring, Angular, PostgreSQL e versões fixas | Stack | Build reproduzível |
| T-02 | SDK LiveKit JVM/browser: sala, grants e exclusão | T-01 | RF-05/25; POC-02 parcial |
| T-03 | Captura real de vídeo e áudio de sistema entre duas máquinas | T-01/02 | RF-06/07/08; POC-01 |
| T-04 | Nove espectadores, TURN e JWT antigo sem recriar sala | T-02/03 | RF-12/25; AC-11/23 |
| T-05 | Camadas, segurança, erros e health; sem OAuth2 Client ou sessão JDBC | T-01 | RF-20/23; AC-19/22 |
| T-06 | Flyway/JPA somente discord_bot e bot_guild_binding; stores/locks de runtime em memória | T-05 | RF-26; AC-24 |
| T-07 | Ticket 256 bits/60s, troca atômica, contexto web/CSRF em memória | T-05/06 | RF-04/27; AC-03/25 |
| T-08 | Vínculo bot–guild, associação REST do bot, allowlist e papel por contexto | T-06/07 | RF-04/05/17/24; AC-04/05/16/24 |
| T-09 | Interactions/PING assinados, anti-replay por boot e comandos de teste | T-05/06 | RF-01/19; AC-01/18 |
| T-10 | /configurar persiste vínculo e configura canal sem tabela extra; /compartilhar cria runtime | T-08/09 | RF-02/03/12/26; AC-01/02/11/24 |
| T-11 | Página de entrada pelo ticket e apresentador: captura, prévia e áudio | T-03/07/10 | RF-06/07/08/23/27 |
| T-12 | /start, publicação exclusiva e confirmação das duas tracks | T-04/08/11 | RF-05/09; AC-05/09 |
| T-13 | Anúncio com botão custom_id Assistir; ticket privado e fila volátil | T-09/12 | RF-09/10/19/27; AC-09/10/18/25 |
| T-14 | /join, vagas e identidade por usuário/contexto em memória | T-06/08/12 | RF-12/13; AC-11/12 |
| T-15 | Player real com volume, autoplay, tela cheia e sair, sem tela de login | T-13/14 | RF-10/11/23; AC-10/22 |
| T-16 | Encerrar/revogar acesso pelo site, navegador ou bot, excluindo sala | T-12/15 | RF-15/18/25; AC-14/17/23 |
| T-17 | Heartbeat, deadlines, reconexão e falha de fonte | T-16 | RF-14/16; AC-13/15 |
| T-18 | Revalidar associação/vínculo e rotacionar geração para revogar mídia | T-08/17 | RF-17/25; AC-16/23 |
| T-19 | Reinício perde runtime: limpar namespace SFU antes de readiness; botões antigos negam acesso | T-13/18 | RF-19/21; AC-18/20 |
| T-20 | Containers/TLS/segredos/banco privado; apenas um processo | T-04/19 | RF-20/26; AC-19/24 |
| T-21 | Métricas técnicas, tráfego, teto e backup somente de vínculos | T-20 | RF-22/26; AC-21/24 |
| T-22 | Regressão + jornada real Discord -> acesso privado -> transmissão | T-15 a T-21 | AC-01 a AC-25; G3 |
| T-23 | Duas horas, nove espectadores e custo dentro de R$200 | T-22 | RNFs; G4 |
| T-24 | Runbooks: novo acesso pelo bot após reinício e limites do ticket bearer | T-23 | Definição de pronto |

## Resultado da T-01
- Backend Java 21/Spring Boot, Maven Wrapper, conexão JDBC por ambiente, liveness HTTP e readiness local do banco.
- Angular standalone com lockfile, estruturas core/shared/features, proxy e consulta real à API com estados de carregamento, sucesso e erro recuperável.
- Compose somente PostgreSQL de desenvolvimento, porta em loopback e volume Docker; `.env.example` fictício e importador PowerShell sem execução de conteúdo.
- Builds backend/frontend aprovados; PostgreSQL autenticado sem tabelas; readiness DOWN/UP ao parar/recuperar banco; página disponível/indisponível/recuperada ao parar/reiniciar backend.
- Documentação de operação e inventário atualizados. Nenhuma funcionalidade das próximas tarefas foi implementada; nenhum serviço externo foi contratado.

Próxima tarefa após a T-01: **T-02 — validar SDK LiveKit JVM/browser, sala, grants e exclusão**, andamento abaixo. As POCs de mídia e a limpeza SFU antes de readiness permanecem pendentes nas suas etapas.

## Resultado da T-02 — concluída
- Implementados Compose LiveKit local, configuração tipada/gateway SDK JVM e ferramenta Angular com tracks sintéticas, sem captura de dispositivos.
- Aprovados testes Java contra SFU real: criar/consultar/excluir, escopo do token, AddTrack de espectador negado pela SFU, desconexão e reuso negado antes do TTL. Assinatura/grants/TTL e erro sanitizado também verificados.
- Auxiliar fora do artefato padrão e bloqueado nos profiles prod/production; origem, marcador e loopback verificados. Sem tabelas novas, tickets ou integrações Discord.
- Prova Angular aprovada: duas identidades RTC, tracks sintéticas confirmadas na SFU, RTP crescente, quadros decodificados e RMS de áudio recebido; espectador negado pela SFU, isolamento entre salas, exclusão/desconexão e reuso negado antes do TTL.
- Conexão usa parâmetros padrão do SDK; overrides locais de retry/timeouts foram retirados após falha inicial. Diagnósticos de transporte não foram mantidos como configuração de produção.
- Próxima tarefa: **T-03 — captura real de vídeo e áudio de sistema entre duas máquinas**, implementação abaixo. Nove espectadores/TURN e limpeza de startup continuam nas suas etapas.

## Resultado da T-03 — implementada, aguardando homologação

- Captura nativa após clique com vídeo e áudio obrigatórios, prévia silenciada, estados, mensagens acionáveis e liberação em falhas/parada/track encerrada. Publicação VP8/Opus e confirmação de ambas fontes por consulta SFU.
- Página separada de espectador com assinatura restrita, vídeo/áudio, liberação por gesto, volume, tela cheia e saída.
- Auxiliar Java e build Angular exclusivos `capture-proof`, chaves experimentais por papel e origem HTTPS privada exata; configuração SFU/proxy/scripts separados da T-02. Sem persistência operacional, bot ou tickets antecipados.
- Testes independentes e runbook entregues. O usuário pediu registrar para depois suas decisões sobre segunda máquina/rede/TLS/firewall; nenhum listener LAN ou confiança de certificado foi configurado pelo agente.
- **T-03 não concluída:** executar G1/POC-01 (matriz Chrome/Edge Windows, 15 minutos por combinação, vídeo e som real audível) e registrar resultados antes de declarar homologação. [Roteiro completo](../tests/manual/T-03-captura-real.md).

## Fatias de entrega
1. T-01–04: provar mídia real antes do bot completo.
2. T-05–10: vínculo persistido, bot e acesso web por ticket, sem cadastro.
3. T-11–16: fluxo mínimo completo com vídeo/som e encerramento.
4. T-17–24: robustez, reinício, capacidade e operação.

## Entradas externas
Antes de integração real: usuário escolhe aplicação/bot, servidor e canal; credenciais de bot por variável/gerenciador seguro. Configurar interaction URL e instalação; nenhum callback OAuth de usuário.
Antes de contratar: cotação concreta de domínio/VPS/região dentro do teto. Nada contratado nesta tarefa.
Falha na POC que exija app, orçamento ou mudança de suporte é apresentada ao usuário.
Activity e múltiplas telas simultâneas permanecem evolução sem autorização de implementação nesta revisão.

## Pronto por tarefa
Comportamento funcional, contratos/cenários atualizados, testes de risco e evidência real; sem credenciais ou dados operacionais persistidos. Não chamar pasta, mockup ou especificação de implementação.
