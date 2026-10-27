# UI-01 — Design system aplicado ao frontend

27/10/2026. Evolução visual autorizada, orientada pela [spec 15](../../specs/15-design-system.md). Alterações anteriores preservadas. Sem commit, push, publicação, conta, dependência nova ou fonte remota.

## Entrega

Saúde, prova sintética LiveKit, apresentador e espectador compartilham estrutura, tokens CSS, ícones locais, painéis, campos, ações e avisos. As três referências orientaram a base quase preta e o destaque violeta; ilustrações, marcas, números e composição comercial não foram reproduzidos. O vídeo usa fundo neutro, 16:9 e `object-fit: contain`.

Foram removidas paletas claras e regras inline conflitantes. `AppShell` e `AppIcon` ficam em `shared/components`; as features mantêm handlers e referências de mídia. A derivação nova de estado apresenta os resultados LiveKit, sem anunciar limpeza confirmada apenas porque os checks passaram. O skip link foca a rota atual, sem navegar à raiz por causa de `<base href="/">`.

Contratos, autorização, seletor, áudio obrigatório, timeouts, cópia e ciclo de mídia não foram alterados nesta entrega. Alterações já existentes em backend, proxy e captura foram preservadas e não são atribuídas a esta tarefa.

## Ambiente e comandos

Windows; Node **24.22.1**, npm **11.19.1**, Angular **22.2.3** e CLI **22.2.4** já fixados. O Node global era 22.22.0; os comandos usaram o runtime existente em `work/tools/node-v24.22.1-win-x64`, sem instalação. Navegador integrado com UA Chrome **156.0.0.0**, Windows NT 10.0; isso não o inclui na matriz homologada de captura.

Dentro de `frontend`, com Node 24 no PATH:

```powershell
npm.cmd run test:capture
node --test proxy.capture.test.cjs
npm.cmd run build -- --configuration production --output-path dist/design-production
npm.cmd run build -- --configuration development --output-path dist/design-development
npm.cmd run build -- --configuration capture-proof --output-path dist/design-capture
```

Saídas separadas preservam outros builds locais. O [harness visual](../../frontend/tests/visual/README.md) usa entrypoint próprio, sem alterar `angular.json`, rotas ou scripts de produção.

| Verificação | Resultado |
|---|---|
| Captura e feedback existentes | **57 aprovados**, zero falhas: gesto antes de HTTP, tracks obrigatórias, races, reconexão, papel, UUID e saneamento |
| Proxy existente | **8 aprovados**, zero falhas: HTTP/2, Host/Origin, caminhos e logs sanitizados |
| Production | **Aprovado**, inicial 249,25 kB; dentro dos budgets |
| Development | **Aprovado**, com T-02 lazy |
| Capture-proof | **Aprovado**, com T-03 lazy |
| Bundle de produção | Um JS emitido; zero marcadores experimentais ou do harness listados abaixo |
| Build isolado de QA | **Aprovado**; conteúdo inerte, sem executar mídia/API/SFU |
| `git diff --check` | **Aprovado** |

Na primeira execução do proxy, cinco testes passaram e três falharam com `EACCES` ao conectar em loopback pelo sandbox. A repetição com permissão de loopback passou nos oito, sem editar proxy, certificados, firewall ou exposição de rede.

A inspeção do JS de produção procurou `getDisplayMedia`, `livekit-client`, `/api/dev/`, `dev/capture`, `dev/livekit`, `CapturePage`, `LiveKitProofPage`, `RTCPeerConnection`, `VisualFixtures`, `Estado fictício` e `Prévia visual`. A produção preserva somente a página de saúde. Rotas, configurações e dependências não foram alteradas nesta tarefa.

## Páginas reais no navegador

Servidores somente em loopback: saúde/LiveKit em 4215, apresentador/espectador em 4216. Saúde recebeu `UP` da API real pelo proxy. Captura mostrou formato inválido, validação automática e recusa HTTP com UUID usando exclusivamente chave fictícia; início permaneceu bloqueado. Nenhuma credencial local foi lida.

| Página | 390 px | 768 px | 1440 px |
|---|---|---|---|
| Saúde | [Imagem](evidencias/design-system/health-390.jpg) | [Imagem](evidencias/design-system/health-768.jpg) | [Imagem](evidencias/design-system/health-1440.jpg) |
| Apresentador | [Imagem](evidencias/design-system/broadcast-390.jpg) | [Imagem](evidencias/design-system/broadcast-768.jpg) | [Imagem](evidencias/design-system/broadcast-1440.jpg) |
| Espectador | [Imagem](evidencias/design-system/watch-390.jpg) | [Imagem](evidencias/design-system/watch-768.jpg) | [Imagem](evidencias/design-system/watch-1440.jpg) |
| LiveKit | [Imagem](evidencias/design-system/livekit-390.jpg) | [Imagem](evidencias/design-system/livekit-768.jpg) | [Imagem](evidencias/design-system/livekit-1440.jpg) |

As 12 combinações carregaram os componentes reais sem overflow horizontal. Frames medidos preservaram 16:9; cada página de mídia manteve um `video` e um `audio`. [Medições](evidencias/design-system/layout-checks.json).

Teclado: Tab exibiu o skip link com foco de 2 px; Enter focou `main-content` preservando `/dev/capture/watch`. Range respondeu a Home/End com valores 0/100; `details` abriu/fechou por Espaço. Campos, summary e controles mantiveram foco visível; controles indisponíveis foram pulados. Isso não mede som audível. [Registro](evidencias/design-system/keyboard-checks.json).

O botão de diagnóstico copiou o relatório real. A conferência encontrou o UUID, sem chave fictícia e sem padrão JWT. Não foram salvos o clipboard nem o UUID real nas evidências. Histórico continua limitado a 40 eventos em memória.

Contraste calculado a partir das cores computadas dos textos e fundos opacos ancestrais: 25 amostras na saúde, 40 no apresentador, 37 no espectador e 39 no LiveKit. Menor razão: **6,03:1** na saúde e **5,86:1** nas demais; nenhuma amostra abaixo de 4,5:1. [Pares medidos](evidencias/design-system/contrast-checks.json). O JSON identifica textos sobre luz decorativa: o cálculo usa a base sólida, sem rasterizar o gradiente. O mínimo ocorre em superfícies opacas. Hover/active, bordas e foco têm cálculos planos na spec; não é auditoria integral de todas as combinações de navegador/zoom.

Revisadas no código as regras de `prefers-reduced-motion` e `forced-colors`: removem animação/transição, mantendo texto de progresso. As preferências do sistema e alto contraste não foram alteradas nem emuladas nesta execução.

## Estados fictícios, separados de mídia real

O harness usa componentes/templates reais com signals preenchidos por dados fictícios. A faixa de identificação permanece visível; a aplicação fica `inert`, o documento de QA bloqueia captura/câmera/microfone e o servidor 4217 não encaminha `/api`. Nenhuma ação `start`/`run`, seletor ou conexão SFU foi acionada.

Conferidas **34 combinações** em 390 px: 13 estados por papel de captura, cinco LiveKit e três saúde. Incluem vazio, validando, válido, papel incorreto, recusado, indisponível, conexão, espera, reconexão, live fictício, erro, encerramento e limpeza pendente. Sem overflow/UUID cortado. Nos 31 estados com mídia, os mesmos nós `video` e `audio` foram preservados ao alternar estados. [Registro](evidencias/design-system/fixture-checks.json).

Evidências com faixa explícita de simulação:

- [Validando](evidencias/design-system/fixture-watch-checking-1440.jpg)
- [Chave recusada e UUID fictício](evidencias/design-system/fixture-watch-refused-1440.jpg)
- [Papel incorreto](evidencias/design-system/fixture-broadcast-wrong-role-1440.jpg)
- [Erro](evidencias/design-system/fixture-watch-error-1440.jpg)
- [Encerrado](evidencias/design-system/fixture-watch-ended-1440.jpg)
- [Limpeza pendente](evidencias/design-system/fixture-watch-cleanup-1440.jpg)
- [Saúde indisponível](evidencias/design-system/fixture-health-unavailable-1440.jpg)
- [Falha LiveKit](evidencias/design-system/fixture-livekit-error-1440.jpg)

Imagens de estado salvas em 1440 px; overflow e identidade das referências desses estados verificados em 390 px. O harness comprova apresentação, não autorização/publicação/limpeza/recepção reais. Sua região inerte não serve como prova de teclado; teclado foi testado na página real.

## Limites e retomada

Não executados: seletor nativo, captura real, som audível, prova SFU sintética completa, fullscreen ativo, duas máquinas Windows, nove espectadores/TURN ou jornada Discord. Homologação T-03 continua pendente. Sem alteração de HTTPS/certificados/firewall, liberação de rede, backend, banco, contratos ou proxy para obter evidências.

Para revisar páginas reais: `npm.cmd start` (saúde e `/dev/livekit`) ou `npm.cmd start -- --configuration capture-proof` (apresentador/espectador), com os serviços e ambiente dos READMEs. Para inspecionar estados sem mídia, seguir o README do harness. A homologação real segue o [roteiro T-03](T-03-captura-real.md), com consentimento do usuário.
