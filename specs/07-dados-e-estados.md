# 07 — Persistência mínima, memória e estados
## Limite do PostgreSQL
O banco existe exclusivamente para identificar bots e relacioná-los aos servidores Discord. Não possui usuários, perfis, transmissões, participantes, credenciais web, tickets, mensagens, filas ou histórico.
IDs Discord são TEXT/string para preservar snowflakes. Metadados técnicos de Flyway são permitidos apenas para controle de schema, sem dados operacionais do produto.

| Tabela | Campos e restrições |
|---|---|
| discord_bot | id UUID PK; discord_application_id TEXT UNIQUE NOT NULL; discord_bot_user_id TEXT UNIQUE NOT NULL |
| bot_guild_binding | bot_id UUID FK discord_bot(id); discord_guild_id TEXT NOT NULL; PK(bot_id, discord_guild_id) |

A presença do vínculo representa integração ativa; desvincular remove a relação. Um bot pode estar em vários servidores; não criar uma instância nova por guild. Piloto usa uma aplicação por deployment. Sem tabela adicional de servidor para repetir apenas seu ID.
Não incluir token, segredo, nome/avatar de usuário, configurador, canal de anúncio ou histórico no schema.
Cadastro de bot sincronizado a partir da identidade configurada e confirmada pelo operador/Discord. Criação/remoção de vínculo precisa de interação administrativa válida e bot acessível naquela guild; não há endpoint público de cadastro de bots.
Canal e allowlist ficam em configuração de ambiente; ajuste de canal via comando vive só em memória.

## Estruturas voláteis
| Estrutura em memória | Conteúdo mínimo | Expiração/limpeza |
|---|---|---|
| StreamSession | UUID, botId, guildId, ownerDiscordId, título, estado, geração, perfil, deadlines, message/channel IDs | Terminal remove acesso; metadados terminal até 10min para UX |
| AccessTicket | Digest do código, escopo, papel, discordUserId, bootId, expiresAt, consumed | 60s; registro consumido até vencimento |
| WebAccessContext | ID opaco, escopo de uma sessão, papel, origem da interação, validade | Idle 30min, absoluta 8h, encerramento/revogação |
| MediaGeneration | Nome de sala, geração, tracks e identidades autorizadas | Nunca reutilizar nome; limpar ao terminar |
| ParticipantLease | Usuário/papel/geração, reserva 30s, SID ativo, última verificação | Saída/timeout/fim |
| IntegrationDedup | Provider/eventId, horário, descriptor mínimo | Bounded TTL; interaction até 15min, webhook 1h |
| PendingOperationQueue | Atualização Discord/limpeza SFU, tentativas, próxima execução | Até sucesso/deadline; não sobrevive reinício |
| IdempotencyStore | Contexto/operação/chave/hash/descriptor sem token | Até contexto expirar, no máximo 8h |
| CapacityState | Uma sala global e uma por guild; locks/reservas | Mesma vida do processo |

Implementar mapas/locks encapsulados em services de runtime, não em repositories JPA nem tabelas genéricas JSON. Não usar arquivo, SQLite ou Redis para esconder persistência. Limitar tamanho/TTL para evitar crescimento indefinido. Sem coordenação entre réplicas no MVP.

## Concorrência
- Vínculo: transação PostgreSQL curta, FK e PK composta impedem duplicata. Chamadas Discord fora da transação; identidade conferida antes do commit.
- Sessão: lock global de capacidade e por guild em ordem fixa; uma aberta por guild e uma no piloto.
- Ticket: validar e marcar consumido atomicamente; duas trocas concorrentes têm no máximo um sucesso. Código expirado/usado não se torna válido de novo.
- Contexto: consumo de novo ticket substitui acesso anterior do mesmo usuário/sessão; nenhuma posse é comprovada só pelo userId.
- Início: compare-and-set CREATED->STARTING com versão em memória; provisionar sala fora do lock e compensar se perder corrida para end.
- Última vaga: até nove leases reservadas/conectadas; reserva 30s; SFU max_participants=10 é barreira adicional.
- Nova aba com mesma identidade substitui a conexão, sem vaga extra; eventos de saída com SID antigo não liberam SID atual.
- Migração: lease da geração anterior pode migrar por 30s, mesmo contexto ainda autorizado e papel invariável.
- Idempotency-Key: mesma chave/corpo reaproveita descriptor; credencial nova sempre reautoriza. Corpo diferente ->409. Não garante deduplicação após reinício; contexto antigo já estará inválido.

## Máquina de estados
~~~mermaid
stateDiagram-v2
 [*] --> CREATED: comando autorizado
 CREATED --> STARTING: iniciar e reservar sala
 CREATED --> EXPIRED: 10min sem iniciar
 CREATED --> ENDING: cancelar
 STARTING --> LIVE: SFU confirma video e audio
 STARTING --> ENDING: falha ou prazo 30s
 LIVE --> RECONNECTING: queda ou rotacao
 RECONNECTING --> LIVE: tracks e autorizacao validas
 RECONNECTING --> ENDING: prazo 30s ou acesso perdido
 LIVE --> ENDING: parar ou fonte encerrada
 ENDING --> ENDED: limpeza normal confirmada
 ENDING --> FAILED: limpeza de falha confirmada
 ENDING --> EXPIRED: expiracao confirmada
~~~
Guardar terminalTarget/reason apenas em memória. Estado terminal irreversível; nova transmissão tem novo UUID. Sem sala criada, CREATED pode expirar diretamente.
Razões: OWNER_STOPPED, ADMIN_STOPPED, SOURCE_ENDED, AUDIO_LOST, START_TIMEOUT, RECONNECT_TIMEOUT, MEMBERSHIP_LOST, ACCESS_EXPIRED, BINDING_UNAVAILABLE, AUTH_UNAVAILABLE, PREPARATION_EXPIRED, MEDIA_UNAVAILABLE, SERVER_RESTARTED.
Falha de DeleteRoom mantém ENDING e impede liberar capacidade até limpeza.

## Reinício e reconciliação
A cada boot gerar bootId e reconstruir somente vínculos do banco. Não recuperar acessos, tickets, mensagens, transmissão ou filas do PostgreSQL.
Antes de readiness, ListRooms e DeleteRoom de todo namespace exclusivo desta aplicação/bot na SFU; confirmar ausência. Não apagar salas de outro sistema. Enquanto SFU não responder/limpeza falhar, readiness=503 e nenhuma emissão de ticket/mídia.
auto_create=false impede token antigo recriar sala apagada. Contexto antigo ->401; ticket anterior ->401; botão antigo -> resposta privada Sessão encerrada, inicie novamente.
Mensagens antigas podem manter título/status anterior porque seus IDs não são persistidos. Não prometer reparo automático sem ampliar persistência.
Processos: 5s deadlines/fila; 10s reconciliação; 30s associação/vínculo. Ao encerrar, invalidar tickets/contextos e parar tracks. Reinício administrativo deve drenar/parar salas antes de desligar quando possível.
