# Frontend — Angular
Aplicação standalone executável da T-01. A página inicial consulta a API real e mostra carregamento, resposta `UP` ou indisponibilidade, com nova tentativa manual e timeout de 8 segundos. O endpoint de liveness indica que o processo backend responde; não comprova conexão com PostgreSQL ou prontidão das integrações futuras.

## Pré-requisitos e versões

Windows com Chrome ou Edge, Node.js **24.22.1 LTS** e npm **11.19.1**. O frontend fixa Angular **22.2.3**, CLI/build **22.2.4**, TypeScript **6.0.3**, RxJS **7.8.2** e tslib **2.8.1**, com `package-lock.json`. A [.nvmrc](.nvmrc) registra o Node usado na validação; nenhum CLI global é necessário.

A [matriz oficial do Angular](https://angular.dev/reference/versions) exige Node 24.15+ na linha 24, TypeScript 6.0.x e RxJS compatível. `.npmrc` e `engines` exigem Node 24 e npm 11 para este projeto. Node 22.22.0, encontrado no ambiente inicial, não atende à versão mínima do Angular 22. O runtime pode ser obtido no [download oficial do Node](https://nodejs.org/en/download).

O tooling aplica um override restrito a `@angular/build -> vite` **8.3.5**, patch da mesma linha 8.3. A versão 8.3.0 declarada pelo builder gerava alertas de segurança; [o mantenedor registra a correção a partir de 8.3.3](https://github.com/vitejs/vite/security/advisories/GHSA-9jrq-w75r-8gcw). Reavaliar esse override quando o builder incorporar o patch.

## Instalar e iniciar no PowerShell

Com o backend já iniciado na porta 8080 (ver [backend](../backend/README.md)), abra outro terminal na raiz do repositório:

```powershell
Set-Location .\frontend
node --version
npm.cmd --version
npm.cmd ci
npm.cmd start
```

Abra [http://127.0.0.1:4200](http://127.0.0.1:4200). O servidor de desenvolvimento escuta apenas em `127.0.0.1`, porta **4200**. `npm.cmd` evita depender da política de execução de scripts PowerShell. Após alterar a configuração de proxy, reinicie o servidor.

Não há variáveis de ambiente obrigatórias no frontend nesta etapa. A URL local do backend está em [proxy.conf.json](proxy.conf.json): chamadas `/api/**` são encaminhadas para `http://127.0.0.1:8080`. Somente o prefixo `/api/health` é reescrito para `/health`, preservando o caminho público `/health/live` das specs e o prefixo `/api` do consumidor Angular. Portanto, a página pede `GET /api/health/live` e o backend recebe `GET /health/live`. Outras rotas futuras `/api` mantêm seu prefixo.

## Verificar e encerrar

```powershell
# Em outro terminal, dentro de frontend, com backend e Angular em execução:
Invoke-RestMethod http://127.0.0.1:4200/api/health/live

# Build otimizado e checagem dos templates/TypeScript:
npm.cmd run build
```

O pedido HTTP deve retornar `status: UP`; no navegador, a página deve exibir **Backend disponível. Resposta recebida: UP.** Para verificar falha e recuperação, encerre o backend e clique em **Verificar novamente**: a página deve exibir indisponibilidade e liberar **Tentar novamente**. Reinicie o backend e tente de novo. O carregamento termina em até 8 segundos; não há retry automático nem respostas simuladas.

O build fica em `dist/compartilhamento-discord/browser`. O proxy pertence ao servidor de desenvolvimento; o build estático precisa de roteamento de mesma origem para `/api` no ambiente em que for servido. Nenhuma hospedagem é configurada nesta etapa.

Encerre `npm.cmd start` com **Ctrl+C** no terminal correspondente. O build termina sozinho. Não há teste unitário artificial do endpoint; a evidência de integração é o pedido real através do proxy e a renderização no navegador.

## Organização preservada

Angular standalone com TypeScript, HttpClient e Router.
- core/services: cliente HTTP de saúde; core/models: contrato da resposta.
- features/health: página funcional desta etapa.
- core/auth: estado da autorização temporária recebida por ticket; guards e interceptors para experiência HTTP/CSRF.
- core/models: contratos compartilhados; não duplicar regras de autorização.
- shared/components: componentes mínimos realmente reutilizados.
- features/auth/pages: troca de ticket, acesso inválido e retorno ao bot; sem formulário de login.
- features/transmission/pages/broadcast: seletor nativo, prévia, iniciar/parar.
- features/transmission/pages/watch: reprodução, volume, tela cheia e estado.
- features/transmission/services: API da sessão e cliente de mídia.
- features/transmission/models: estado de apresentação da sessão.

As pastas de acesso, componentes compartilhados e transmissão continuam reservadas para suas tarefas; a T-01 não implementa esses fluxos.

## T-02 — Prova local LiveKit

**Situação atual: T-02 concluída.** A prova real no navegador integrado recebeu vídeo/áudio sintéticos e aprovou as dez verificações. Usa retry/timeouts padrão do SDK; overrides locais mais curtos foram removidos após falha inicial de RTC. A ferramenta expõe estados e contagens sanitizadas para diagnóstico, sem SDP, endereços de candidates ou credenciais. [Evidência e limites](../tests/manual/T-02-livekit.md).

O SDK browser oficial independente de framework é **`livekit-client` 2.22.3**, com versão exata e árvore fixa no lockfile. A [release oficial](https://github.com/livekit/client-sdk-js/releases/tag/v2.22.3) e a [referência do SDK](https://docs.livekit.io/reference/client-sdk-js/) documentam o cliente JavaScript/TypeScript; nenhum frontend React foi adicionado. As versões Angular/Node da T-01 foram preservadas.

Com o PostgreSQL, o LiveKit e o **backend auxiliar de prova** iniciados explicitamente conforme os READMEs de [infra](../infra/README.md) e [backend](../backend/README.md), execute:

```powershell
Set-Location .\frontend
npm.cmd ci
npm.cmd start
```

Abra [http://127.0.0.1:4200/dev/livekit](http://127.0.0.1:4200/dev/livekit) e clique **Executar prova local**. Se o navegador bloquear som, clique **Ouvir áudio** enquanto o teste executa. O tom de 440 Hz tem volume baixo; ajuste o volume local se necessário. Não há seletor de tela, pedido de microfone ou câmera. O teste leva alguns segundos e pode aguardar o timeout de publicação da SFU na prova negativa.

A ferramenta conecta dois clientes LiveKit com identidades distintas emitidas pelo backend. Publica um canvas animado e áudio de `OscillatorNode` nas fontes `screen_share` e `screen_share_audio`. O receptor mede bytes RTP crescentes, quadros decodificados, avanço do vídeo e RMS do áudio recebido. Esses resultados comprovam transporte e recepção sintéticos; **não homologam áudio do Windows**, duas máquinas ou Chrome/Edge. Essa homologação pertence à T-03.

As verificações negativas são explícitas:

- O SDK primeiro recusa a tentativa normal de publicar com papel espectador.
- Uma segunda tentativa altera somente a cópia local das permissões do SDK para ultrapassar sua barreira de interface. O JWT fica inalterado. A ferramenta exige envio real de `AddTrack`, resposta explícita **NOT_ALLOWED** da SFU e zero tracks desse espectador por consulta administrativa Java; timeout isolado não aprova. Essa alteração adversarial existe apenas nesta prova de desenvolvimento.
- Um JWT válido da sala A, mesmo com query `room=B`, conecta somente em A, conferindo também que B não recebeu aquela identidade. A query não é apresentada como seletor suportado pelo SDK.
- Alterar apenas `video.room` do JWT para B, mantendo a assinatura original, exige rejeição HTTP 401 pela SFU. Falhas genéricas de rede não contam como aprovação desse teste.
- Excluir A precisa desconectar apresentador e espectador. A tentativa com o mesmo JWT, ainda dentro do TTL, deve falhar; a consulta Java deve confirmar que A permanece ausente.

Somente salas criadas por esta execução são removidas. Ao finalizar, interromper ou navegar para outra página, os clientes desconectam e as tracks, timers e `AudioContext` são liberados. Se a API ficar indisponível durante a limpeza, a página informa que a remoção não foi confirmada; mantenha o backend auxiliar ativo e execute novamente para repetir a limpeza dessas salas. Fechar abruptamente o navegador não garante a chamada HTTP de exclusão; o backend de prova deve ser encerrado com sua limpeza explícita.

O cliente chama somente `/api/dev/livekit/**` na mesma origem, com o marcador **não secreto** `X-LiveKit-Proof: local-development`. O backend valida também origem exata e loopback; seus endpoints ficam desativados por padrão, fora do artefato normal e indisponíveis em produção. A página não recebe API key/secret. JWTs são usados apenas em memória para a conexão do SDK; não são exibidos, persistidos ou enviados aos logs da aplicação. Os logs do SDK ficam desativados. Não copie URLs de sinalização ou dados da aba de rede: o transporte do SDK inclui a credencial de mídia.

A rota e o import lazy da ferramenta entram somente pelo `fileReplacements` da configuração Angular `development`. `npm.cmd run build` usa **production** e não inclui a página, o SDK de mídia ou seu endpoint auxiliar nos bundles. A página de saúde da T-01 continua disponível na raiz. Para checar ambos os builds:

```powershell
npm.cmd run build
npm.cmd run build -- --configuration development
```

Nenhum teste unitário que simule sucesso de mídia foi adicionado. A evidência real fica em [tests/manual/T-02-livekit.md](../tests/manual/T-02-livekit.md). Encerre o Angular com **Ctrl+C**; o procedimento de encerramento da SFU e do backend está nos respectivos READMEs.

API por /api na mesma origem. Tokens Discord nunca passam pelo Angular. Token de mídia somente em memória. Sem SSR necessário no MVP.
getDisplayMedia exige clique na página; não abrir seletor automaticamente ao abrir pelo ticket. O navegador decide fontes de áudio disponíveis.
O fragmento /acesso#ticket=... é removido imediatamente e trocado por POST com CSRF. Ticket, contexto e token de mídia não vão para localStorage. Reinício/expiração exige novo acesso pelo bot.
