# 01 — Especificação do produto
## Problema e resultado
Amigos reunidos em um servidor Discord precisam iniciar e encontrar uma transmissão sem organizar salas e copiar links manualmente. O bot cria o contexto e publica acesso; o site captura e distribui vídeo/áudio. A conversa de voz continua no Discord.

## Pessoas e permissões
- Apresentador: membro autorizado pelo bot que executou /compartilhar; pode capturar, iniciar e encerrar sua sessão.
- Espectador: membro do mesmo servidor com acesso temporário emitido pelo bot; assiste sem permissão de publicar.
- Administrador do servidor: usuário com Manage Guild; configura o canal e pode encerrar sessões.
- Operador: mantém infraestrutura e allowlist do piloto; não recebe acesso ao conteúdo por uma tela administrativa.
Não exigir permanência no canal de voz, cadastro, senha ou login separado no site. IDs e nome recebidos do Discord são usados somente em memória durante o acesso; não exigir email. Assinatura paga e catálogo de servidores não fazem parte do MVP.

## Restrições de implementação aprovadas
Java 21, Spring Boot, Angular e PostgreSQL, com backend em camadas. Integração Discord no mesmo backend. Interface funcional mínima primeiro. SFU é infraestrutura de mídia, não um backend de aplicação alternativo. PostgreSQL armazena somente bots e relações bot–servidor; todos os contextos operacionais são voláteis.

## Requisitos funcionais
| ID | Requisito obrigatório | Evidência principal |
|---|---|---|
| RF-01 | Instalar aplicação com bot e comandos no servidor piloto | AC-01 |
| RF-02 | Configurar um canal de texto de anúncios por servidor | AC-01 |
| RF-03 | Criar sessão com /compartilhar [titulo], resposta privada e link do apresentador | AC-02 |
| RF-04 | Conceder acesso privado por ticket emitido pelo bot, sem login separado, e validar associação ao servidor no backend | AC-03, AC-04 |
| RF-05 | Apenas criador pode obter permissão de publicação; admin pode encerrar, não assumir a captura | AC-05 |
| RF-06 | Exibir escolha de tela pelo navegador após clique local | AC-06 |
| RF-07 | Capturar e publicar vídeo + áudio de sistema nos ambientes homologados | AC-07 |
| RF-08 | Bloquear início se não houver áudio; explicar como escolher fonte com som | AC-08 |
| RF-09 | Anunciar sessão pública somente após confirmação de ambas as tracks pela SFU | AC-09 |
| RF-10 | Botão interativo Assistir entrega acesso privado de uso único; o site troca esse ticket por autorização temporária | AC-04, AC-10 |
| RF-11 | Reproduzir vídeo e som; volume, ativar áudio, tela cheia e sair | AC-10 |
| RF-12 | Uma sessão aberta por servidor e limite de 1 apresentador + 9 espectadores | AC-11 |
| RF-13 | Mostrar estado e quantidade de espectadores conectados sem contar apresentador/reservas | AC-12 |
| RF-14 | Reconectar automaticamente com limites e revalidar autorização | AC-13 |
| RF-15 | Encerrar pelo site, navegador ou bot e atualizar mensagem Discord | AC-14 |
| RF-16 | Tratar cancelamento, fonte encerrada, falha de áudio e ausência de rede | AC-08, AC-15 |
| RF-17 | Negar novos acessos e remover acesso a mídia quando associação ao servidor se perder | AC-16 |
| RF-18 | Oferecer /transmissao status e /transmissao encerrar | AC-17 |
| RF-19 | Tratar indisponibilidade, rate limits e eventos duplicados do Discord/SFU | AC-18 |
| RF-20 | Operar por HTTPS, com segredos no servidor e sem armazenar mídia | AC-19 |
| RF-21 | Expirar contextos abandonados e encerrar salas antigas após reinício, sem recuperar usuários ou transmissões | AC-20 |
| RF-22 | Registrar métricas de custo, tráfego, erro e capacidade sem conteúdo privado | AC-21 |
| RF-23 | Adotar UI mínima utilizável por teclado, com estados e erros em português | AC-22 |
| RF-24 | Só habilitar servidores aprovados na allowlist do piloto e vinculados ao bot no banco | AC-04 |
| RF-25 | Revogar publicação da sala antiga ao encerrar ou trocar sua geração; nunca reutilizar o nome de mídia | AC-23 |
| RF-26 | Persistir somente identificação de bot e vínculo bot–servidor; operação não grava dados dos usuários ou transmissões | AC-24 |
| RF-27 | Emitir tickets privados com expiração de 60s, consumo atômico único e papel/servidor/sessão fixados pelo bot | AC-03, AC-25 |

## Requisitos não funcionais e metas do piloto
Estas metas não são garantias já medidas.
- RNF-01: uma sala, 10 participantes, sessão real de duas horas sem falha bloqueante.
- RNF-02: ACK de comandos sempre antes do limite externo de 3s; meta interna p95 <= 1s.
- RNF-03: primeiro quadro e som disponível em até 5s p95 após conexão/autorização e liberação de autoplay, em redes homologadas.
- RNF-04: atraso de vídeo ponta a ponta p95 <= 1,5s em rede de teste; registrar método e localização.
- RNF-05: mídia 720p/30 padrão; 1080p/30 opcional sujeito a rede/CPU; redução automática em congestionamento. Nunca mostrar resolução negociada como promessa fixa.
- RNF-06: teto de infraestrutura R$200/mês no cenário de referência: 60 horas/mês, nove espectadores simultâneos, até ~4 Mbps de vídeo por espectador.
- RNF-07: remover usuário sem associação em até 60s após perda observável de associação, mais falha externa documentada; cortar sessões com verificação indisponível além de 60s. Ver 05 para limitações de tokens e rotação.
- RNF-08: mudanças de estado refletem no site em até 5s e no Discord em até 15s em operação normal.
- RNF-09: servidor inicial com 2 vCPUs/4GB é hipótese de dimensionamento; capacidade depende de POC-02.
- RNF-10: nenhuma regressão de acesso entre servidores, de papel de espectador ou de encerramento.
- RNF-11: reinício invalida contextos e tickets; readiness fica bloqueada até limpar as salas antigas do namespace da aplicação.
- RNF-12: piloto em processo único; não iniciar réplicas compartilhando a mesma SFU sem novo desenho de coordenação em memória/distribuída.

## Fora do MVP
Cadastro/login de usuários no site, histórico persistido de usuários/transmissões, app desktop, extensão, mobile, múltiplas transmissões simultâneas, Activity, transmissão dentro da mensagem, gravação/replay, webcam, microfone, chat próprio, controle remoto, resolução 4K/60, pagamentos, busca pública, uploads de vídeos e visual elaborado.

## Definição de pronto
Todos RF têm evidência; testes de autorização, estados e concorrência passam; vídeo e áudio reais homologados; orçamento aprovado; passos de execução/reversão documentados; zero defeito bloqueante. Interface pode ser simples, mas não pode esconder falhas ou requisitos.
