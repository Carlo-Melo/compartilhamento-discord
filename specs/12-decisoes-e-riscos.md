# 12 — Decisões e riscos
Revisão 1.2 • 26/10/2026.

## Decisões aprovadas atuais
| ID | Decisão |
|---|---|
| D-01 | MVP em site; app auxiliar apenas por nova decisão |
| D-02 | Assistir pelo site; Activity é evolução |
| D-03 | Acesso emitido pelo bot dentro do servidor, sem cadastro ou login separado no site |
| D-04 | Windows + Chrome/Edge |
| D-05 | Vídeo e áudio obrigatórios |
| D-06 | Áudio inteiro do computador aceito, inclusive possibilidade de capturar a call |
| D-07 | Uma transmissão por servidor, um apresentador + nove espectadores |
| D-08 | Infra até R$200/mês; nenhum serviço contratado agora |
| D-09 | Funcionalidade antes de perfumaria |
| D-10 | Java 21/Spring Boot, Angular, PostgreSQL |
| D-11 | Backend em camadas |
| D-12 | Banco somente para identificar bots e relacioná-los aos servidores Discord |
| D-13 | Operação em memória: sem persistir usuários, tickets, acessos, transmissões, mensagens ou filas |

D-03/D-12 substituem as hipóteses da versão 1.1 de login OAuth de usuários, Spring Session JDBC e persistência operacional. Não implementar essas hipóteses antigas.
O questionamento sobre duas telas simultâneas foi esclarecimento, não alteração de escopo; D-07 continua válida.

## Decisões técnicas decorrentes
- Publicação de botões custom_id identifica a interação; depois o bot entrega um link privado /acesso#ticket=... para o site.
- Ticket opaco de 256 bits, uso único, TTL60s; contexto web por cookie em memória, escopo restrito.
- Vínculo persistido não autentica o visitante. Link privado é bearer e pode ser repassado antes do consumo; não prometer prova da identidade física no navegador.
- Identidade Discord é metadado temporário da interaction/contexto; token pessoal de usuário não é solicitado.
- Um bot pode estar em vários servidores; não é necessário um bot novo para cada guild. Duas tabelas de produto: discord_bot e bot_guild_binding.
- Canal de anúncio por ambiente/configuração em memória, fora do banco. Ajuste em memória precisa ser refeito após reinício.
- Sem fila durável, deduplicação persistida, histórico de acesso, Redis ou múltiplas réplicas no piloto.
- Boot novo invalida runtime e exclui salas antigas de namespace próprio antes de readiness.

## Pendências no momento adequado
| ID | Entrada | Quando |
|---|---|---|
| P-01 | Aplicação/bot, servidor piloto e canal | Integração real |
| P-02 | Domínio, fornecedor/região e cotação total | Antes de contratar/deploy |
| P-03 | Homologação de captura/áudio no hardware real | POC-01 |
| P-04 | App auxiliar se web falhar no requisito | Só após evidência da POC |
| P-05 | Disponibilidade/autorização de Activity no Brasil | Antes dessa evolução |
| P-06 | Versões exatas de Windows/navegadores homologados | POC-01 |

## Riscos e respostas
| Risco | Resposta |
|---|---|
| Repasse do ticket antes do consumo | Expiração curta, uso único, entrega privada e transparência sobre limite de identidade |
| URL comum não identifica quem clicou | Botão custom_id público + resposta privada com link; dois cliques possíveis |
| Replay de interaction após reinício | Verificar assinatura/freshness e rejeitar interaction_id criado antes do boot |
| Crash perde filas, mensagens, contextos e transmissões | Invalidar tudo, limpar SFU antes de readiness e pedir nova ação no bot |
| Texto de anúncio antigo fica Ao vivo após crash | Clique sempre confere runtime; responder Sessão encerrada sem restaurar sessão |
| Bot sem acesso ao servidor ou banco indisponível | Não emitir tickets; cortar acesso sem validação após janela de 60s |
| Áudio de sistema ausente ou incluindo Discord | POC real, bloquear sem track, aviso e prévia sem som; fones não isolam áudio digital |
| JWT antigo em self-hosted | DeleteRoom, auto_create=false, geração/nome nunca reutilizados |
| Memória/CPU/tráfego excedem orçamento | Teste de carga, métricas e decisão antes de ampliar gasto |
| Dados pessoais em logs apesar de banco mínimo | Saneamento e minimização; banco restrito não implica isenção legal |

Nenhum mecanismo reativa o compartilhamento nativo suspenso do Discord. Evoluções futuras (Activity, app, mobile, várias telas) exigem spec/revisão de custo e nova decisão.
