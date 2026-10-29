# Frontend — Angular
Padrão normativo em [specs/15-design-system.md](../specs/15-design-system.md). A [validação UI-01](../tests/manual/UI-01-design-system.md) reúne imagens e limites. A [prévia isolada de estados](tests/visual/README.md) usa dados fictícios e ações inertes, sem executar captura ou transmissão.
Aplicação standalone para navegação e testes locais. A home reúne as telas em um carrossel manual com indicadores de página e rolagem horizontal, mostra os links disponíveis na configuração atual e orienta o acesso às provas isoladas. A página de saúde consulta a API real e mostra carregamento, resposta `UP` ou indisponibilidade, com nova tentativa manual e timeout de 8 segundos. O endpoint de liveness indica que o processo backend responde; não comprova conexão com PostgreSQL ou prontidão das integrações futuras.

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

Abra a home em [http://127.0.0.1:4200](http://127.0.0.1:4200) e a verificação em [http://127.0.0.1:4200/health](http://127.0.0.1:4200/health). O servidor de desenvolvimento escuta apenas em `127.0.0.1`, porta **4200**. `npm.cmd` evita depender da política de execução de scripts PowerShell. Após alterar a configuração de proxy, reinicie o servidor.

Não há variáveis de ambiente obrigatórias no frontend nesta etapa. A URL local do backend está em [proxy.conf.json](proxy.conf.json): chamadas `/api/**` são encaminhadas para `http://127.0.0.1:8080`. Somente o prefixo `/api/health` é reescrito para `/health`, preservando o caminho público `/health/live` das specs e o prefixo `/api` do consumidor Angular. Portanto, a página pede `GET /api/health/live` e o backend recebe `GET /health/live`. Outras rotas futuras `/api` mantêm seu prefixo.

## Home e configurações

| Configuração | Home e telas disponíveis | Como iniciar |
|---|---|---|
| `production` (build normal) | `/` e `/health`; provas experimentais não são rotas deste build | `npm.cmd run build`; sirva o build com fallback para rotas Angular e proxy de `/api` na mesma origem |
| `development` | `/`, `/health` e `/dev/livekit` | `npm.cmd start` e abra `http://127.0.0.1:4200/` |
| `capture-proof` | `/`, `/dev/capture/broadcast` e `/dev/capture/watch` | Prepare a prova e use o script HTTPS descrito em T-03 abaixo |

As orientações da home para configurações diferentes são texto, não links ativos. A prova sintética exige PostgreSQL, LiveKit e backend auxiliar; a prova de captura exige também sua configuração de rede, HTTPS confiável e chaves por papel. As instruções detalhadas estão nas seções T-02 e T-03. Navegar pelas telas não comprova captura real, áudio, TURN ou transmissão entre máquinas.

## Verificar e encerrar

```powershell
# Em outro terminal, dentro de frontend, com backend e Angular em execução:
Invoke-RestMethod http://127.0.0.1:4200/api/health/live

# Build otimizado e checagem dos templates/TypeScript:
npm.cmd run build
```

O pedido HTTP deve retornar `status: UP`; no navegador, a página deve exibir **Backend disponível. Resposta recebida: UP.** Para verificar falha e recuperação, encerre o backend e clique em **Verificar novamente**: a página deve exibir indisponibilidade e liberar **Tentar novamente**. Reinicie o backend e tente de novo. O carregamento termina em até 8 segundos; não há retry automático nem respostas simuladas.

O build fica em `dist/compartilhamento-discord/browser`. O proxy pertence ao servidor de desenvolvimento; o build estático precisa de roteamento de mesma origem para `/api` no ambiente em que for servido. Nenhuma hospedagem é configurada nesta etapa.

A base T-05 do backend mantém essa consulta pública e o uso de proxy, sem CORS externo. `GET /api/csrf` já fornece bootstrap técnico, mas as páginas/fluxo de acesso definitivo continuam na T-07/T-11. As provas T-02/T-03 continuam usando seus contratos e headers específicos, sem cookies como autorização; seus erros agora incluem também `requestId` e `retryable`, preservando `code`/`message`. Cabeçalhos da API não protegem este documento Angular: o servidor/proxy que entrega o HTML deve aplicar CSP restrita à aplicação/SFU e Permissions-Policy `display-capture=(self), camera=(), microphone=()` sem bloquear captura, WSS e blobs de mídia necessários às provas autorizadas. Política final do documento em produção permanece na T-20.

Encerre `npm.cmd start` com **Ctrl+C** no terminal correspondente. O build termina sozinho. Não há teste unitário artificial do endpoint; a evidência de integração é o pedido real através do proxy e a renderização no navegador.

## Organização preservada

Angular standalone com TypeScript, HttpClient e Router.
- core/services: cliente HTTP de saúde; core/models: contrato da resposta.
- features/home: navegação conforme a configuração Angular ativa.
- features/health: página funcional de saúde.
- core/auth: estado da autorização temporária recebida por ticket; guards e interceptors para experiência HTTP/CSRF.
- core/models: contratos compartilhados; não duplicar regras de autorização.
- shared/components: `AppShell` (estrutura, skip link e rodapé), `AppIcon` (ícones vetoriais locais) e `AmbientOrb` (decoração CSS sem estado ou interação), reutilizados nas páginas existentes.
- features/auth/pages: troca de ticket, acesso inválido e retorno ao bot; sem formulário de login.
- features/transmission/pages/broadcast: seletor nativo, prévia, iniciar/parar.
- features/transmission/pages/watch: reprodução, volume, tela cheia e estado.
- features/transmission/services: API da sessão e cliente de mídia.
- features/transmission/models: estado de apresentação da sessão.

As pastas de acesso e transmissão definitiva continuam reservadas para suas tarefas; a prova T-03 usa a feature experimental `dev-capture`. `shared/components` reúne a estrutura visual comum e os ícones locais efetivamente reutilizados.

## Padrão visual do frontend

A [spec 15 — Design system](../specs/15-design-system.md) é a fonte normativa para novas páginas e alterações de interface. A evolução visual foi autorizada em 27/10/2026 e combina a linguagem preta/roxa das três imagens com o respiro, as formas orgânicas luminosas e a composição da [referência interativa fornecida](https://cut-mule-87308980.figma.site). A identidade usa uma marca própria local em preto fosco que combina tela e áudio, junto ao nome em texto, além de títulos maiores, cabeçalho e botões em pílula, superfícies discretas e uma forma ambiente local em CSS, sem fontes remotas, biblioteca visual, WebGL ou serviços adicionais. A mídia continua neutra e os avisos operacionais permanecem visíveis.

`public/marca-preto-fosco.png` contém o símbolo gerado para o cabeçalho e o ícone das abas do navegador; o nome continua como texto acessível. `src/styles.css` centraliza os tokens semânticos e as classes de botões, campos, painéis, avisos, mídia e diagnóstico; `src/app/shared/components/app-shell.ts` e `app-shell.css` implementam a estrutura comum, `app-icon.ts` fornece ícones locais, e `ambient-orb.ts`/`ambient-orb.css` encapsulam a decoração. A forma não indica acesso, captura ou conexão e é removida em movimento reduzido e cores forçadas. Reutilizar essas definições e preservar a organização `core/shared/features`. Estados de espera, recusa, erro e encerramento também seguem o padrão. Ícones complementam texto; informações e ações precisam continuar legíveis por teclado e sem animação. A spec registra o cálculo sRGB dos pares de tokens opacos; isso não substitui conferir os pares e estados realmente renderizados.

Para verificar uma alteração visual, executar os três builds e os testes de captura/feedback e proxy descritos abaixo, conferir a exclusão das provas no bundle de produção e inspecionar as páginas em 390, 768 e 1440 px. Registrar evidências com dados fictícios e indicar quais estados foram simulados. Verificação de layout não homologa captura ou áudio Windows; não selecionar uma tela, alterar certificados ou abrir serviços/rede para obter uma imagem de teste.

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

A rota e o import lazy da ferramenta entram somente pelo `fileReplacements` da configuração Angular `development`. `npm.cmd run build` usa **production** e não inclui a página, o SDK de mídia ou seu endpoint auxiliar nos bundles. A home fica na raiz e a página de saúde da T-01 fica em `/health`. Para checar ambos os builds:

```powershell
npm.cmd run build
npm.cmd run build -- --configuration development
```

Nenhum teste unitário que simule sucesso de mídia foi adicionado. A evidência real fica em [tests/manual/T-02-livekit.md](../tests/manual/T-02-livekit.md). Encerre o Angular com **Ctrl+C**; o procedimento de encerramento da SFU e do backend está nos respectivos READMEs.

API por /api na mesma origem. Tokens Discord nunca passam pelo Angular. Token de mídia somente em memória. Sem SSR necessário no MVP.
getDisplayMedia exige clique na página; não abrir seletor automaticamente ao abrir pelo ticket. O navegador decide fontes de áudio disponíveis.
O fragmento /acesso#ticket=... é removido imediatamente e trocado por POST com CSRF. Ticket, contexto e token de mídia não vão para localStorage. Reinício/expiração exige novo acesso pelo bot.

## T-03 — captura real experimental

Avisos para testes manuais: ao colar a chave, aguarde a validação automática (500 ms de pausa; timeout HTTP de 8 s). O aviso distingue formato inválido, validação em andamento, chave válida para esta página, chave de outro papel, recusa e indisponibilidade. A validação não captura nem cria sala; o botão de início só fica habilitado após papel confirmado e ainda exige um novo clique. Ao iniciar, o campo é limpo; para nova tentativa cole a chave novamente. Cada operação continua validando o acesso no backend.

O histórico local mostra até 40 avisos por página e pode ser copiado pelo botão **Copiar diagnóstico**, sem chaves, JWTs ou mídia. Erros HTTP mostram o UUID de requisição, quando recebido, para buscar nos logs técnicos do backend. O histórico só existe em memória; não habilitar logs verbosos do SDK/proxy. Falhas de limpeza têm aviso separado e preservam a causa original. Outros navegadores, como Opera e navegadores integrados, continuam sem homologação.

**Implementada; homologação Windows entre duas máquinas pendente.** Configuração Angular **capture-proof** separada da `development` usada pela T-02. Nenhuma dependência npm foi adicionada. O build `production` exclui as duas ferramentas e o SDK de mídia.

Depois de preparar SFU, backend auxiliar, rede e HTTPS confiável conforme [infra](../infra/README.md#t-03--prova-restrita-em-duas-máquinas), inicie o frontend **na máquina da SFU**, a partir da raiz:

```powershell
.\scripts\Start-CaptureProofFrontend.ps1 -CertificatePath 'C:\certificados\capture-cert.pem' -PrivateKeyPath 'C:\certificados\capture-key.pem'
```

A home fica em `https://<IPv4-privado>:4443/` e oferece os links da prova. O apresentador abre `https://<IPv4-privado>:4443/dev/capture/broadcast`; o outro computador abre `/dev/capture/watch` na mesma origem. A saúde da API não está disponível pelo proxy restrito de captura; use o servidor local normal e `/health`. Cada pessoa informa apenas a chave experimental do seu papel no campo protegido. Chaves vêm do arquivo local privado gerado pelo operador, nunca de URL, bundle ou armazenamento web; API key/secret SFU não entram no navegador.

No apresentador, o clique **Selecionar tela e iniciar** chama `getDisplayMedia` enquanto o gesto ainda está ativo. O usuário escolhe **Tela inteira** e habilita áudio do sistema no seletor nativo. A prévia fica silenciada. Ausência de vídeo/áudio vivos recusa a tentativa e libera todas as tracks; cancelamento não cria sala. O áudio inteiro pode incluir Discord e notificações; não se pede microfone/câmera. Qualidade inicial 720p/30 fps/2 Mbps ou 1080p/30 fps/4 Mbps, VP8 sem simulcast e Opus até 128 kbps; são alvos de captura/negociação.

O estado de transmissão só aparece depois da publicação das duas tracks e consulta SFU no backend. O som audível no receptor continua sendo verificação humana. No espectador, use **Ouvir áudio** se autoplay bloquear; há volume local, tela cheia e **Sair**. Assinatura exige identidade do apresentador e fontes autorizadas. Não há captura no espectador.

**Parar transmissão**, o controle nativo ou término de uma track obrigatória liberam captura/conexão e pedem exclusão da sala. Reconexão mantém captura por até 30s e exige nova confirmação SFU; falha encerra. Falha de limpeza fica visível com **Repetir limpeza**, bloqueando nova prova até confirmar exclusão. Fechar abruptamente a página não garante HTTP; siga a limpeza do [backend](../backend/README.md#t-03--auxiliar-restrito-para-captura-real). Recrie a SFU T-03 após reinício abrupto do backend.

Verificações independentes, dentro de `frontend`, com Node24:

```powershell
npm.cmd run test:capture
node --test proxy.capture.test.cjs
npm.cmd run build -- --configuration capture-proof
npm.cmd run build -- --configuration development
npm.cmd run build
```

Os testes de ciclo de vida usam doubles, e os testes do proxy usam upstream local falso; nenhum autoriza captura ou comprova áudio Windows. A matriz Chrome/Edge, preparo e pendências estão em [tests/manual/T-03-captura-real.md](../tests/manual/T-03-captura-real.md). O [contrato T-03](../specs/api/capture-proof.openapi.json) continua separado da API definitiva.

## T-04 — nove identidades e diagnóstico ICE

A prova `capture-proof` foi estendida, preservando as páginas e o design system. Cada aba de espectador gera um UUID v4 volátil (`X-Capture-Viewer`), que o backend associa a uma identidade de mídia aleatória própria. Novas tentativas na mesma página e reconexão reutilizam a reserva; o navegador não escolhe sala, identidade SFU ou papel. Até nove reservas por geração, além do apresentador; a décima é recusada. O diagnóstico diferencia espectadores confirmados pela SFU de vagas reservadas, sem confundir reserva com recepção.

**Sair** desconecta e solicita `DELETE /session/viewer`. A reserva continua vinculada à mesma instância da página até o fim da geração, pois um JWT inicial/renovado ainda válido não pode ganhar acesso a uma vaga reatribuída. Reentre sem navegar ou recarregar para reconectar. Navegar, recarregar ou fechar a página perde o nonce; outra página/aba reserva outra vaga. Para substituir o grupo ou recuperar vagas perdidas, o apresentador precisa encerrar e criar uma geração nova. A geração experimental expira em uma hora; limpeza remota não confirmada continua bloqueando novas tentativas. Isso não implementa identidade Discord, tickets ou `/join` do produto.

O seletor **Conectividade da prova** oferece padrão SDK/SFU e **Exigir relay**, sem campo para ICE URLs/credenciais. O override `iceServers: []` foi removido: o [código oficial do SDK2.22.3](https://github.com/livekit/client-sdk-js/blob/v2.22.3/src/room/RTCEngine.ts) só aplica servidores ICE recebidos da SFU quando esse campo está ausente. O modo relay altera somente `iceTransportPolicy`. TURN precisa ser preparado pelo operador conforme o [README de infra](../infra/README.md); o seletor sozinho não configura nem comprova TURN.

O diagnóstico consulta `transport.selectedCandidatePairId`, com fallback somente para `candidate-pair.selected`, e exige estado `succeeded`. Mostra tipos dos candidatos, protocolos e `relayProtocol`, sem endereço, porta, SDP, URL, nonce ou credencial. `protocol: udp` no candidato pode coexistir com `relayProtocol: tls`, que identifica o transporte cliente–TURN. Contadores de vídeo/áudio continuam separados; par ICE ou sinalização não comprovam recepção nem som audível. Logs do SDK permanecem desativados; cópia limitada aos últimos40 avisos em memória.

O [harness T-04 separado](tests/t04/README.md) prepara ensaio opt-in com canvas/tom, nove clientes distintos, concorrência/capacidade, RTP/quadros/RMS, ICE direto/relay selecionado e isolamento de tokens inicial/renovado real observado. Não entra nos entrypoints Angular nem nos bundles padrão. Executá-lo exige SFU/backend experimentais e HTTPS já preparados pelo operador; sua implementação e seus builds não comprovam execução SFU real. [Relatório e limites T-04](../tests/manual/T-04-multiespectador-turn.md).
