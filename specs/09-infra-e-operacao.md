# 09 — Infraestrutura, orçamento e operação
## Piloto proposto
Uma VPS Linux com IP público, 2 vCPUs/4GB RAM como ponto inicial a testar; Angular estático, Spring Boot Java 21, PostgreSQL e LiveKit single-node com TURN. Sem banco gerenciado, broker, CDN de vídeo ou GPU.
Spring recebe somente APIs e integrações. Distribuição de mídia é WebRTC na SFU. Node serve para build Angular, não é serviço de negócio de produção. PostgreSQL persiste somente bot e vínculo com guild; runtime em memória local e um processo Spring. Não adicionar sessões web, filas ou histórico ao banco.
Não executar PostgreSQL a partir da pasta OneDrive como volume de dados; usar volume Docker/local adequado fora de sincronização. Código/specs podem permanecer nesta pasta.

## Topologia
- app.DOMINIO: Angular + /api + /integrations, HTTPS 443.
- rtc.DOMINIO: sinalização WSS da SFU, 443.
- turn.DOMINIO: TURN/TLS com endpoint de transporte adequado.
- Backend 8080, PostgreSQL 5432 e APIs administrativas da SFU somente em rede interna.
- Abrir portas UDP de mídia conforme configuração homologada, por exemplo faixa 50000–50100, e ICE TCP 7881 quando adotado.
- TURN/UDP 3478 e TURN/TLS 5349 são proposta inicial. Para redes que só permitem 443, prever segundo IP ou roteamento L4/SNI comprovado. Não colocar HTTPS e TURN/TLS na mesma porta/IP sem solução explícita.
- Proxy público da SFU expõe apenas caminhos de sinalização necessários; bloquear /twirp e endpoints administrativos para visitantes. Tokens de frontend sem grants administrativos.
- room.auto_create=false, limite 10 participantes e exclusão de nomes antigos verificados na POC.
- TLS válido e renovação automática. PostgreSQL sem acesso público.

## Preparação experimental T-04
Preparação experimental T-04: overlay separado para o TURN/TLS integrado da imagem LiveKit fixada, mantendo o Compose T-03 sem TURN. A prova segue IPv4 privado com rota já preparada pelo operador; não autoriza exposição pública nem implementa o deploy acima. TLS TURN usa domínio próprio e certificado confiável; TLS HTTPS da origem privada continua obrigatório. Política ICE `relay` exige candidato relay, e o relatório registra o par selecionado e transporte sem IPs, SDP ou credenciais. [Configuração e portas](../infra/README.md), [roteiro e limites G2](../tests/manual/T-04-multiespectador-turn.md). Nenhuma regra de firewall, DNS ou confiança TLS é alterada automaticamente.

## Orçamento aprovado e cenário de cálculo
Teto inicial R$200/mês de infraestrutura para um grupo. Referência de uso: 60h/mês, uma fonte e nove espectadores.
Saída aproximada de vídeo em GB decimais = Mbps × espectadores × horas × 0,45.
A 4 Mbps: 4 × 9 × 60 × 0,45 = 972 GB/mês, antes de áudio, protocolos e retransmissões. Reservar 25% inicial para planejamento (~1,22 TB), medir de fato. Tráfego TURN no mesmo nó e áudio devem ser considerados na leitura de cobrança do provedor.
Proposta de alocação: servidor até R$150; domínio amortizado até R$10; reserva de R$40. São envelope de compra, não cotação nem promessa de capacidade.
Fornecedor/região/domínio ainda pendentes. Comparar preço total, franquia de saída, IPv4, impostos/câmbio e rota até os amigos no Brasil. Nenhum serviço contratado.
Combinado Java+PostgreSQL+SFU exige teste de memória/CPU; se 4GB não suportar dentro do teto, apresentar opções ao usuário, não reduzir áudio nem assumir gasto extra.
Alertas em 70% e 90% da franquia/budget. No limite seguro de franquia, bloquear novas sessões e orientar; não aceitar excedente de custo ilimitado. Uso por mais horas/bitrate precisa recalcular orçamento.

## Configuração e segredos previstos
APP_ORIGIN; DISCORD_APPLICATION_ID; DISCORD_BOT_TOKEN; DISCORD_PUBLIC_KEY; DISCORD_ALLOWED_GUILD_IDS; DISCORD_ANNOUNCEMENT_CHANNEL_ID (piloto, ou mapa por guild em configuração); DATABASE_URL; DATABASE_USERNAME; DATABASE_PASSWORD; LIVEKIT_INTERNAL_URL; LIVEKIT_PUBLIC_URL; LIVEKIT_API_KEY; LIVEKIT_API_SECRET.
Segredos injetados fora do Git; .env.example poderá conter nomes e valores fictícios. Fail-fast em produção se segredo/origem faltar. Configuração tipada com validação.
Versões de imagens fixadas; sem latest. Migrations Flyway como único fluxo de schema, limitadas às tabelas de bot/vínculo e metadados Flyway. Não instalar Spring Session JDBC nem OAuth2 Client. Backup não substitui migration reversível.

## Observabilidade e retenção
Logs técnicos sem IDs de participantes, títulos, tickets, cookies, conteúdo ou tokens; requestId aleatório/status/duração/errorCode. Proxy deve ocultar fragmentos/corpos de troca e minimizar IPs; não gravar access log pessoal indefinidamente.
Métricas agregadas de salas/bitrate/egress/perdas, falhas de ticket/assinatura/Discord, heap/CPU/memória e fila em memória. Sem tabelas de histórico no PostgreSQL.
Contextos operacionais expiram em memória conforme 07. Logs técnicos minimizados por até 7 dias; não usar logs como substituto de banco de usuários/transmissões.

## Runbooks mínimos
- Deploy: backup de banco, migrations compatíveis, iniciar serviços, readiness, validar ticket/assinatura/TURN e isolamento do schema bot–guild, smoke privado, liberar piloto.
- Rollback: versão anterior compatível com schema de vínculos; não fazer downgrade cego do banco. Se migration incompatível, restaurar backup com serviço parado e informar perda máxima.
- Backup: pg_dump diário cifrado somente do cadastro/vínculos e metadados de schema, fora da VPS + retenção de 7 cópias, custo incluído no teto; testar restore antes de aceitar piloto. RPO proposto 24h, RTO proposto 2h, a comprovar.
- SFU indisponível: negar novas conexões, sinalizar estado, tentar reconciliação e encerrar após janela de 30s.
- Discord indisponível: não criar sucesso falso; sessões existentes obedecem limite de autorização de 60s. Fila em memória retoma mensagens sem flood enquanto o processo existir; reinício perde a fila.
- Backend indisponível: cliente deixa de receber lease, para publicação após 30s sem confirmação; operação reinicia com bootId novo, invalida acessos/tickets e exclui salas do namespace antes de readiness. Não restaura usuários, mensagens ou transmissões do banco.
- Orçamento/rede saturados: bloquear novas sessões, reduzir perfil dentro do aprovado, preservar áudio; pedir decisão se necessário ampliar custo.
- Banco indisponível: readiness falha, nenhuma emissão de ticket/vínculo novo; encerrar acessos ativos sem vínculo confirmado após 60s. O banco não pode ser usado para reconstruir runtime.
