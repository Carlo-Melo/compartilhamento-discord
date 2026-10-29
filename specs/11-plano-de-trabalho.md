# 11 — Plano de implementação por entregas
**T-01 e T-02 CONCLUÍDAS em 26/10/2026**, com [evidência de bootstrap](../tests/manual/T-01-bootstrap.md) e [integração LiveKit real](../tests/manual/T-02-livekit.md). **T-03 IMPLEMENTADA, HOMOLOGAÇÃO PENDENTE**: [resultado e preparação externa](../tests/manual/T-03-captura-real.md). **T-05 CONCLUÍDA:** segurança, validação e erros com builds e HTTP real aprovados; [evidência e limitações](../tests/manual/T-05-seguranca-backend.md). **T-04 IMPLEMENTADA, VALIDAÇÃO PARCIAL:** [resultados e roteiro G2](../tests/manual/T-04-multiespectador-turn.md). T-06 **IMPLEMENTADA, VALIDAÇÃO PARCIAL**; T-07 em implementação e validação; T-08 a T-24 continuam **NÃO INICIADAS**. Revisão 1.2 substitui login OAuth/persistência operacional por acesso emitido pelo bot e memória.

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

## Resultado da T-04 — implementada, validação parcial

- Evolução do auxiliar `capture-proof`, aproveitando captura, player e barreiras existentes; sem nova API de produto. Nove correlações de espectador por geração recebem identidades aleatórias do backend. Repetição reutiliza a identidade; limite inclui o apresentador e é atômico.
- Saída remove a conexão SFU, mas não recicla a vaga naquela geração: tokens originais/renovados self-hosted podem continuar válidos. Encerrar e criar outra sala irrepetível inicia outro grupo. Não constitui identidade Discord, ticket, contexto ou `/join` definitivo.
- TURN/TLS self-hosted em configuração separada opt-in; cenário direto preservado. SDK deixa de suprimir servidores ICE fornecidos pela SFU. Diagnóstico expõe somente tipos e transportes do par selecionado, sem endereços, SDP ou credenciais.
- **G2 pendente:** executar recepção real de vídeo/áudio por nove identidades, relay comprovado e isolamento de tokens originais e renovados na SFU real. Usuário adiou homologação em 27/10/2026. Builds/doubles não substituem esses resultados; ensaio de duas horas continua T-23.

## Fatias de entrega
1. T-01–04: provar mídia real antes do bot completo.
2. T-05–10: vínculo persistido, bot e acesso web por ticket, sem cadastro.
3. T-11–16: fluxo mínimo completo com vídeo/som e encerramento.
4. T-17–24: robustez, reinício, capacidade e operação.

## Resultado da T-05 — base de segurança concluída

- Spring Security com negação por padrão, política por método/caminho, liveness pública e readiness somente na gestão em loopback. Sem login/Basic, OAuth2 Client, Spring Session JDBC ou autorização provisória.
- Bootstrap CSRF em sessão técnica exclusivamente em memória, Origin exata, CORS fechado e cabeçalhos de segurança da API. Cookie de bootstrap não cria contexto autorizado. Proteção do documento Angular depende do servidor/proxy que entrega esse documento.
- Envelope de erro MVC/Security com mensagens sanitizadas, UUID próprio e retryable; validação estrutural com Jakarta Bean Validation. Contratos auxiliares de erro atualizados explicitamente.
- Auxiliares preservados por fontes/artefatos/profiles/opt-in e proteções de origem/transporte/papel. Exceções CSRF limitadas aos métodos/caminhos experimentais, sem liberação ampla de API.
- Builds dos três artefatos aprovados, zero falhas/erros: normal 19 testes aprovados/4 pulados; T-02 20 aprovados/3 pulados; T-03 28 aprovados/3 pulados. Dez testes HTTP reais de segurança por artefato e cinco testes MVC; consumidores Angular com 45 testes e três builds aprovados. SFU real opt-in e mídia Windows não executados nesta rodada.
- Após a T-05, T-06 implementou entidades/JPA/Flyway de bot/vínculo e reserva de capacidade em memória; T-07 introduziu a base de tickets, troca e contexto, ainda dependente de integrações confiáveis. T-19 continua responsável por limpar salas antigas antes de readiness. Nenhuma homologação de mídia foi inferida da segurança básica.

Resultados executados e itens não executados constam no [relatório T-05](../tests/manual/T-05-seguranca-backend.md).

## Resultado da T-06 — implementada, validação parcial

- Flyway/JPA para identificação de bot e vínculos; capacidade volátil limitada e locks em ordem fixa, sem fluxos posteriores. PostgreSQL 16 real verificou migração, restrições, repositórios e reinicialização do contexto; testes de concorrência aprovados.
- **Não concluída:** falta repetir em PostgreSQL 18 e comprovar AC-24 integral com os fluxos futuros e backup. Limpeza SFU antes de readiness continua na T-19. [Evidências e reprodução](../tests/manual/T-06-persistencia-memoria.md).

## Entradas externas
Antes de integração real: usuário escolhe aplicação/bot, servidor e canal; credenciais de bot por variável/gerenciador seguro. Configurar interaction URL e instalação; nenhum callback OAuth de usuário.
Antes de contratar: cotação concreta de domínio/VPS/região dentro do teto. Nada contratado nesta tarefa.
Falha na POC que exija app, orçamento ou mudança de suporte é apresentada ao usuário.
Activity e múltiplas telas simultâneas permanecem evolução sem autorização de implementação nesta revisão.

## Pronto por tarefa
Comportamento funcional, contratos/cenários atualizados, testes de risco e evidência real; sem credenciais ou dados operacionais persistidos. Não chamar pasta, mockup ou especificação de implementação.

## T-07 — entrega e evidências

O andamento, os resultados verificáveis e as limitações desta etapa estão registrados em [T-07 — ticket e acesso](../tests/manual/T-07-ticket-acesso.md). O fluxo real depende da interaction assinada T-09 e da verificação de vínculo e associação T-08; a limpeza de salas antes de readiness permanece na T-19. AC-03 e AC-25 não são declarados integralmente aprovados antes da evidência da jornada aplicável.

## Acompanhamento de auditorias

Os achados técnicos, seu escopo e os critérios para correção e verificação são acompanhados no [registro de auditorias](16-registro-auditorias.md). As datas e os estados das entregas acima são registros de evidências anteriores, não uma declaração de que os achados posteriores foram corrigidos. Novas auditorias podem acrescentar achados ao registro; priorização e momento da correção devem considerar a severidade e a exposição efetiva.
