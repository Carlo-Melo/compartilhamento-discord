# Prévia visual isolada

Este entrypoint renderiza os componentes Angular reais de saúde, apresentador, espectador e LiveKit com os estilos da aplicação e estados públicos preenchidos com dados fictícios. Não faz parte das rotas ou dos scripts de produção. A faixa de identificação permanece visível nas evidências.

Na pasta `frontend`, com as dependências já instaladas:

```powershell
npm.cmd exec -- ng build --browser tests/visual/main.ts --ts-config tests/visual/tsconfig.json --configuration development --output-path dist/design-qa
```

Execute `node tests/visual/serve.mjs` e abra `http://127.0.0.1:4217/health`, `/broadcast`, `/watch` ou `/livekit`. O servidor escuta somente em loopback, serve o bundle separado com fallback de SPA, bloqueia `/api` e desabilita permissões de captura/câmera/microfone apenas neste documento de QA. Não altera os proxies da aplicação. `?state=error` define o estado inicial; a barra superior alterna os estados sem executar os handlers das páginas. Encerre com Ctrl+C.

O conteúdo da aplicação fica `inert`: nenhum botão, formulário, reprodução ou captura pode ser acionado. O HealthService retorna `NEVER` e um interceptor impede requisições HttpClient. O harness apenas altera signals públicos; não chama `start`, `run`, métodos da API, seletor nativo ou conexões SFU. Não há chave, JWT, mídia sintética ou credencial real. No estado fictício de chave válida, o início continua bloqueado porque nenhuma autorização foi criada.

Após a ativação de cada página de mídia, o harness guarda os elementos DOM `video` e `audio`. A cada troca de estado, compara suas identidades depois da renderização e informa na barra se as referências foram preservadas. Navegar para outra página estabelece uma nova referência inicial. Essa checagem verifica a estabilidade dos elementos no template, sem reproduzir ou capturar mídia.

Os estados correspondem à apresentação pertinente de cada página: saúde usa consulta, disponível ou indisponível; LiveKit usa vazio, execução e resultados; captura distingue acesso, conexão, erro, encerramento e limpeza. A mídia permanece vazia mesmo no exemplo visual de transmissão. Este recurso verifica layout e mensagens, e não comprova transmissão, áudio Windows, limpeza remota nem homologação entre máquinas. O conteúdo `inert` também não serve como prova de navegação por teclado da aplicação real; essa verificação deve usar suas rotas normais.
