# Compartilhamento de tela integrado ao Discord
T-01 implementada: backend Spring Boot e frontend Angular executáveis, PostgreSQL local por Docker Compose e página que consulta a saúde real da API. Bot e acesso definitivo continuam nas próximas tarefas.

T-02 concluída: SDKs LiveKit JVM/browser e SFU local com administração, grants, vídeo/áudio sintéticos recebidos, isolamento de sala e exclusão/reuso de token validados contra servidor real. [Como executar a prova local](backend/README.md#t-02--sdk-oficial-e-prova-local-isolada) e [evidências e limites](tests/manual/T-02-livekit.md).

**T-03 implementada, homologação pendente:** captura de tela com áudio obrigatório, publicação LiveKit e página separada de espectador em uma prova HTTPS restrita. [Como preparar e iniciar nas duas máquinas](infra/README.md#t-03--prova-restrita-em-duas-máquinas); [testes e decisões externas para resolver depois](tests/manual/T-03-captura-real.md). O som real do Windows e a matriz Chrome/Edge por 15 minutos ainda não foram homologados.

**T-05 concluída — base de segurança:** Spring Security com negação por padrão, bootstrap CSRF em memória, Origin exata, validação e erros sanitizados. Builds e HTTP real aprovados; auxiliares T-02/T-03 preservam isolamento e papéis. [Política, testes e limites](tests/manual/T-05-seguranca-backend.md). Bot, tickets e contexto autorizado continuam nas próximas tarefas; bootstrap não concede acesso.

**T-04 implementada, validação parcial:** auxiliar de captura estendido para nove identidades distintas, limite concorrente, TURN/TLS opt-in e diagnóstico do par ICE selecionado. [Implementação, comandos e pendências G2](tests/manual/T-04-multiespectador-turn.md). A homologação entre máquinas/redes foi adiada pelo usuário em 27/10/2026; não há aprovação de G1/G2 por builds.

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

## Executar localmente no Windows / PowerShell
Pré-requisitos: **JDK 21**, **Node 24.15+ da linha 24** (validado com 24.22.1 / npm 11.19.1), Git e Docker Desktop em modo containers Linux, com Compose v2 que suporte `up --wait`. Maven global e PostgreSQL nativo não são necessários. A primeira instalação exige internet. Não use Java 17 ou Node 22 neste bootstrap.

Nesta máquina, a validação usou runtimes portáteis oficiais em `work/tools/`, sem alterar instalações globais. Para reutilizá-los, execute este bloco em cada novo terminal, a partir da raiz (em uma máquina nova, instale os pré-requisitos pelos fornecedores oficiais indicados no inventário):

```powershell
$env:JAVA_HOME = (Resolve-Path '.\work\tools\jdk-21.0.12.1+1').Path
$nodeDir = (Resolve-Path '.\work\tools\node-v24.22.1-win-x64').Path
$env:Path = "$env:JAVA_HOME\bin;$nodeDir;$env:Path"
```

Abra o PowerShell na raiz do projeto:

```powershell
java -version
node --version
npm.cmd --version
docker compose version
Copy-Item .env.example .env
notepad .env
```

Copie o exemplo somente na primeira configuração, preservando um `.env` já existente. Substitua a senha fictícia por uma senha local aleatória. Não envie credenciais ao chat nem as adicione ao Git. Se `5432` estiver ocupada, ajuste `POSTGRES_PORT` e a porta da `DATABASE_URL` juntos; nesta máquina foi usada `5433`. O banco usa volume Docker fora do diretório OneDrive. Flyway cria somente as duas tabelas de bot/vínculo e seus próprios metadados técnicos.

Inicie o banco explicitamente:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml config --quiet
docker compose --env-file .env -f infra/docker/compose.yaml up -d --wait
```

Em um terminal na raiz, execute o backend (configure `JAVA_HOME` para seu JDK 21 se necessário):

```powershell
. .\scripts\Import-DevEnvironment.ps1
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Em outro terminal na raiz, execute o frontend:

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
npm.cmd start
```

Abra [http://127.0.0.1:4200](http://127.0.0.1:4200). A página consulta `/api/health/live`; o proxy de desenvolvimento encaminha para `/health/live` do backend. Deve mostrar **Backend disponível** após resposta real `UP`; falha ou timeout oferece tentar novamente.

| Componente | Endereço local |
|---|---|
| Angular | `http://127.0.0.1:4200` |
| API / liveness | `http://127.0.0.1:8080/health/live` |
| Readiness com JDBC, somente loopback | `http://127.0.0.1:8081/health/ready` |
| PostgreSQL | `127.0.0.1:5432` por padrão; configurável por `POSTGRES_PORT` |

Verifique em outro PowerShell:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/health/live
Invoke-RestMethod http://127.0.0.1:8081/health/ready
Invoke-RestMethod http://127.0.0.1:4200/api/health/live
```

Liveness testa o processo; readiness testa a aplicação e a conexão real com o banco. Ainda não atesta Discord, mídia ou limpeza de salas (T-19). Para encerrar, use `Ctrl+C` nos terminais Angular/backend e, na raiz:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml down
```

O volume é preservado. Não há inicialização automática de produção. Consulte os READMEs de [backend](backend/README.md), [frontend](frontend/README.md) e [infra](infra/README.md) para variáveis, ferramentas e comandos detalhados. Versões e fontes: [inventário](specs/14-bootstrap-inventario.md). Resultados reais: [validação T-01](tests/manual/T-01-bootstrap.md).

Leia [specs/README.md](specs/README.md) e siga a ordem do [plano de trabalho](specs/11-plano-de-trabalho.md); a T-03 aguarda homologação real antes de avançar seu portão G1. Nenhum bot, serviço externo ou hospedagem foi registrado ou contratado.

## Revisão de acesso e persistência
Sem tabelas de usuários, sessões web, transmissões ou filas. A arquitetura prevê manter apenas o vínculo bot–servidor após reinício; invalidação dos acessos e limpeza das salas antes de readiness pertencem às tarefas futuras, incluindo T-19. Link privado é uma credencial bearer temporária, não comprovação automática da identidade no navegador.
