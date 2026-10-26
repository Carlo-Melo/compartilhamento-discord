# Compartilhamento de tela + Discord — pacote de especificações
Versão 1.2 • 26/10/2026 • idioma do produto: português do Brasil.

**Estado:** T-01 e T-02 concluídas: base local executável e integração SDKs/SFU com administração, grants e recepção sintética Angular validados. T-03 implementada como prova restrita, com captura real solicitada ao navegador, publicação e player; **homologação entre duas máquinas Windows pendente**. Bot e acesso definitivo não implementados; T-04 em diante pendentes. Nenhuma hospedagem ou conta externa foi criada. Decisões de produto abaixo foram respondidas pelo usuário. Metas técnicas são critérios de aceitação propostos, sujeitos à prova de conceito indicada. [Implementação, testes e pendências T-03](../tests/manual/T-03-captura-real.md).

## Escopo aprovado
- Site no MVP, sem instalar aplicativo. Bot organiza a sessão no servidor.
- Botão interativo Assistir identifica a interação no Discord e entrega um acesso privado para abrir o site.
- Sem cadastro, senha ou login OAuth de usuários no site. O backend concede acesso temporário a partir do bot.
- PostgreSQL persiste somente identificação dos bots e seus vínculos com os servidores Discord.
- Contextos de acesso, tickets, transmissões, participantes e fila de operações existem apenas em memória.
- Após reiniciar o backend, os acessos expiram e as salas antigas são encerradas; o usuário retorna ao bot para novo acesso.
- Windows com Chrome ou Edge para transmitir e assistir.
- Vídeo e áudio da fonte obrigatórios. Usuário aceita áudio inteiro do computador.
- Uma transmissão por servidor, um apresentador + até nove espectadores.
- Orçamento inicial de infraestrutura até R$200/mês para um grupo.
- Interface mínima funcional; identidade visual e perfumaria ficam para depois.
- Assistir em Activity dentro do Discord é evolução condicionada à plataforma.

## Stack definida
Java 21 + Spring Boot no backend, Angular no frontend, PostgreSQL apenas para o cadastro de bots e seus vínculos com servidores. Arquitetura em camadas. O bot faz parte do backend; a distribuição de mídia fica em serviço WebRTC/SFU separado. Detalhes em 06.

## Caminho de leitura
| Documento | Para que serve |
|---|---|
| [00 — Princípios](00-principios.md) | Regras que orientam a implementação |
| [01 — Produto](01-produto.md) | Requisitos, prioridades e exclusões |
| [02 — Fluxos e telas](02-fluxos-e-telas.md) | Jornada real, estados e conteúdo mínimo |
| [03 — Captura e mídia](03-captura-e-midia.md) | Vídeo, áudio, publicação e reprodução |
| [04 — Bot Discord](04-bot-discord.md) | Comandos, mensagens e permissões |
| [05 — Identidade e segurança](05-identidade-e-seguranca.md) | Acesso pelo bot, tickets temporários e autorização |
| [06 — Arquitetura](06-arquitetura.md) | Componentes e decisões técnicas |
| [07 — Dados e estados](07-dados-e-estados.md) | Modelo, concorrência e ciclo da sessão |
| [08 — API](08-api.md) | Contratos e semântica HTTP |
| [OpenAPI 3.1](api/openapi.json) | Contrato estrutural para implementação |
| [09 — Infra e operação](09-infra-e-operacao.md) | Deploy, custo, backup e incidentes |
| [10 — Validação](10-validacao.md) | Critérios mensuráveis e testes |
| [Cenários de aceite](tests/aceite.feature) | Exemplos verificáveis em Gherkin |
| [11 — Plano de trabalho](11-plano-de-trabalho.md) | Ordem, dependências e entregas |
| [12 — Decisões](12-decisoes-e-riscos.md) | Aprovado, proposto e pendente |
| [13 — Fontes](13-fontes.md) | Documentação externa consultada |
| [14 — Inventário](14-bootstrap-inventario.md) | Versões fixadas, compatibilidade e dependências T-01/T-02 |

## Como desenvolver com estas specs
1. Executar POC-01: captura real de vídeo e som de sistema no Windows, entre duas máquinas. Registrar versões e resultados.
2. Executar POC-02: distribuição com nove espectadores, TURN e teste de isolamento/reuso de tokens.
3. Implementar as fatias de 11, seguindo os contratos de 08 e o OpenAPI.
4. Atualizar requisito, contrato e cenário quando mudar comportamento; registrar a decisão em 12.
5. Considerar pronto somente o que tem evidência em 10. Não marcar prova manual como teste automatizado.

## Limites importantes
O comando no Discord abre o fluxo; a captura começa apenas após clique e autorização no seletor do navegador. Não é possível prometer um único comando que já capture a tela sem interação no site.
O áudio inteiro pode incluir notificações e as vozes da call. Orientar o usuário antes da captura e testar com fones.
A captura não inclui microfone ou câmera por padrão, e não há gravação.
O vínculo persistido usa IDs da aplicação/bot e do servidor; tokens do bot ficam em segredos externos. Um bot pode estar vinculado a vários servidores, sem criar um bot novo para cada servidor.
O link privado temporário é uma credencial de acesso de uso único. A identidade fornecida pelo Discord identifica a interação original, mas não comprova quem abre o navegador se esse link for repassado.
Este pacote não inclui Activity como caminho de reprodução no MVP nem mecanismos para reativar recursos suspensos do Discord.

## Próximas decisões externas, no momento adequado
Antes do deploy real: escolher fornecedor/região, domínio e servidor/canal piloto; o usuário registra a aplicação Discord e fornece as credenciais pelo gerenciador de segredos. Não solicitar senhas ou tokens no chat.
Antes de contratar: apresentar cotação total dentro do teto, incluindo tráfego e encargos.
Se a captura web falhar no cenário aprovado: apresentar evidência e pedir decisão sobre app auxiliar ou mudança de compatibilidade. Não remover áudio silenciosamente.
