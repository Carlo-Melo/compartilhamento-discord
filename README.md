# Compartilhamento de tela integrado ao Discord
Projeto em fase de especificação. Estrutura de código preparada; aplicações ainda não inicializadas nem executáveis.

## Stack
- Backend: Java 21 + Spring Boot; arquitetura em camadas.
- Frontend: Angular; interface mínima funcional no MVP.
- Banco: PostgreSQL exclusivamente para bot e vínculo com servidor; migrations Flyway propostas.
- Vídeo e áudio: WebRTC, com SFU LiveKit self-hosted proposta e prova de conceito obrigatória.

## Estrutura
- [backend](backend/README.md): API, bot, tickets temporários e persistência mínima de vínculos.
- [frontend](frontend/README.md): captura no navegador e player de transmissão.
- [infra](infra/README.md): PostgreSQL, SFU, TURN, proxy e deploy.
- [specs](specs/README.md): requisitos, contratos, decisões, testes e plano.
- [tests](tests/README.md): evidências e validação ponta a ponta.
- [scripts](scripts/README.md): espaço para futuros comandos de desenvolvimento e operação.

## Escopo inicial aprovado
Um servidor piloto, uma transmissão por servidor, um apresentador e nove espectadores. Windows + Chrome/Edge. Áudio do computador obrigatório e permitido pelo usuário. Acesso pelo bot sem cadastro/login no site: interaction no Discord gera link privado temporário de uso único. Banco somente para identificação bot–servidor; transmissões e acessos em memória. Espectadores clicam Assistir e recebem botão privado para o site. Teto inicial de infraestrutura de R$200/mês.

## Começar
Ler [specs/README.md](specs/README.md), depois executar as provas de conceito definidas em [plano de trabalho](specs/11-plano-de-trabalho.md).
Este commit de arquivos não instala dependências, não registra bot e não contrata hospedagem.
Versões de bibliotecas, wrappers e lockfiles serão fixadas na tarefa de bootstrap; as versões de referência estão em [arquitetura](specs/06-arquitetura.md).

## Revisão de acesso e persistência
Sem tabelas de usuários, sessões web, transmissões ou filas. Reinício mantém apenas o vínculo bot–servidor e encerra/invalida o restante; usuários retornam ao bot para novo acesso. Link privado é uma credencial bearer temporária, não comprovação automática da identidade no navegador.
