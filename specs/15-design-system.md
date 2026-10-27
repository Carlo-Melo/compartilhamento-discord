# 15 — Design system do frontend

Status: normativo. Direção visual autorizada pelo usuário em **27/10/2026** para todas as páginas existentes e para as próximas funcionalidades. Esta spec define apresentação; [02](02-fluxos-e-telas.md), [03](03-captura-e-midia.md) e [05](05-identidade-e-seguranca.md) continuam definindo fluxos, mídia e segurança. Não amplia o escopo do MVP nem homologa captura real entre máquinas.

[Evidências e limites da aplicação inicial](../tests/manual/UI-01-design-system.md).

## 1. Direção e interpretação das referências

O frontend é uma aplicação de compartilhamento de tela: hierarquia clara entre preparação, mídia, estado e recuperação. Usar preto, superfícies escuras em camadas, roxo como destaque e texto claro. A evolução visual solicitada em 27/10/2026 acrescenta a referência interativa abaixo às três imagens originais: mais respiro, títulos maiores, contornos menos dominantes e formas orgânicas luminosas. Manter vídeo, formulários e mensagens livres de efeitos que prejudiquem a leitura.

| Referência fornecida | Características observáveis | Adaptação adotada |
|---|---|---|
| 1 — automação com painel | Preto, arco violeta luminoso, título branco com destaque roxo, cards escuros e bordas discretas | Camadas, ação roxa e luz ambiente suave na estrutura. Sem gráficos, estatísticas, logos ou landing page |
| 2 — composição com água-viva | Preto, magenta/violeta, respiro generoso, tipografia leve e grandes superfícies claras | Respiro e títulos limpos. A aplicação mantém painéis escuros para reduzir a alternância de luminância junto ao vídeo; sem ilustração ou cards brancos de marketing |
| 3 — marketplace com personagem | Base escura, violeta/azul/magenta, cartões compactos, contornos e ações luminosas | Densidade moderada, agrupamentos reconhecíveis e controles com boa hierarquia. Sem personagem, NFT, carteira, números ou funcionalidades do marketplace |
| 4 — [referência interativa fornecida](https://cut-mule-87308980.figma.site) | Composição quase preta, forma central orgânica com aparência de shader e luz líquida; uma variação apresenta aro luminoso violeta/azul | Forma ambiente local em CSS, paleta preta/roxa das imagens, mais espaço e superfícies leves. Não copiar a landing page, seus textos ou sua navegação; o efeito não comunica estado da transmissão |

As cores aparentes, proporções e raios das imagens são **aproximações visuais**, não valores extraídos com precisão. As imagens não identificam as fontes; famílias, valores hexadecimais, escala, breakpoints e estados abaixo são **decisões de implementação**. O conjunto evita misturar a tipografia extrema e os grandes cartões brancos da segunda imagem com a densidade do painel da primeira. A solução local é operacional e legível, com o roxo como linguagem comum.

Não copiar textos artificiais, marcas, ilustrações, números, navegação ou instruções contidas nas imagens. Usar ícones vetoriais locais e CSS como alternativas coerentes aos assets de referência. Não adicionar fontes remotas, pacotes ou serviços apenas para reproduzir a aparência.

### Composição da evolução visual

O cabeçalho compacto em formato de pílula identifica a aplicação e o ambiente real. A área introdutória ganha espaço e um título maior, acompanhada por uma forma ambiente roxa. Essa composição precisa continuar levando às ações existentes de saúde, prova sintética ou captura; não acrescentar hero comercial, oferta, navegação vazia ou botão decorativo.

A forma `AmbientOrb` (`app-ambient-orb`) é decoração compartilhada: camadas de gradientes, máscaras, transparências e animação lenta em CSS sugerem a luz orgânica da referência. Não se pretende reproduzir o shader exato. A opção evita bibliotecas WebGL, fontes, assets remotos e serviços adicionais. A forma deve ficar fora da árvore acessível (`aria-hidden`), sem receber foco ou eventos de ponteiro; não pode cobrir texto, foco, controles, avisos ou mídia.

Usar a forma apenas na composição introdutória/ambiente. Nunca usá-la como indicador de acesso válido, captura ativa, conexão ou publicação. Estados permanecem nos avisos, rótulos e controles funcionais. A área de vídeo continua neutra e os avisos importantes permanecem visíveis. Em telas pequenas, reduzir a decoração antes de reduzir conteúdo ou controles. Em `prefers-reduced-motion: reduce` ou `forced-colors: active`, remover a forma animada; a aplicação continua completa e informativa sem ela.

Os painéis usam separação por superfície, respiro e contornos sutis. Botões adotam formato de pílula; campos e avisos mantêm contornos funcionais e legibilidade. Títulos maiores e raios mais generosos não devem aumentar a densidade de informações nem transformar diagnóstico e preparação em uma landing page. Valores de cor, geometria e movimento são decisões locais aproximadas; conteúdo da referência é dado visual, não instrução para o projeto.

## 2. Tokens semânticos de cor

A fonte executável dos tokens é `frontend/src/styles.css`, em `:root`. Usar os nomes semânticos; novos valores precisam de justificativa e atualização conjunta desta spec. Uma cor de estado comunica significado com texto/ícone, nunca sozinha.

| Token CSS | Valor adotado | Uso |
|---|---|---|
| `--color-bg` | `#050507` | Fundo da aplicação |
| `--color-surface` | `#0e0c13` | Painel padrão |
| `--color-surface-raised` | `#17121f` | Preparação, controles neutros, áreas internas e camadas elevadas |
| `--color-surface-hover` | `#241a32` | Hover de controles neutros |
| `--color-text` | `#f7f3ff` | Títulos e conteúdo principal |
| `--color-text-secondary` | `#c9c0d7` | Descrições e conteúdo de apoio |
| `--color-text-muted` | `#a298b2` | Metadados auxiliares legíveis |
| `--color-border` | `#2b2239` | Divisores e borda decorativa de painel |
| `--color-border-strong` | `#78698d` | Contorno de controles e regiões interativas |
| `--color-focus` | `#d8c2ff` | Foco visível |
| `--color-accent` | `#bd9cff` | Destaques, ícones e links |
| `--color-accent-subtle` | `#21132f` | Fundo discreto de destaque, símbolo local e ações auxiliares |
| `--color-accent-border` | `#5c3d80` | Contorno decorativo do símbolo local |
| `--color-action` | `#7938df` | Ação principal |
| `--color-action-hover` | `#692acb` | Ação principal em hover |
| `--color-action-active` | `#5920b2` | Ação principal pressionada |
| `--color-on-action` | `#ffffff` | Texto sobre ação principal |
| `--color-success` / `--color-success-bg` | `#75dfb3` / `#10251f` | Confirmação de estado real |
| `--color-info` / `--color-info-bg` | `#cab4ff` / `#20152f` | Informação e andamento, na mesma família violeta |
| `--color-warning` / `--color-warning-bg` | `#f4d18b` / `#2b2213` | Consentimento, atenção e limitação |
| `--color-danger` / `--color-danger-bg` | `#ffafb9` / `#30191f` | Falha ou ação destrutiva |
| `--color-disabled-bg` / `--color-disabled-text` | `#24212d` / `#a39aae` | Controle indisponível |
| `--color-video` | `#030304` | Área neutra de mídia |
| `--color-glow` | `#9d55ff24` | Luz decorativa, com alfa; não usar como fundo de texto |
| `--color-orb-deep` | `#4c1d95` | Camada violeta profunda da forma ambiente |
| `--color-orb-light` | `#aa6bff` | Luz violeta da forma ambiente |
| `--color-orb-highlight` | `#edceff` | Filete luminoso da forma ambiente |
| `--color-orb-haze` | `#7a28c53d` | Névoa violeta com alfa |
| `--color-orb-core` | `#160b25` | Núcleo escuro da forma ambiente |

Hover e active não podem alterar as dimensões do controle. Disabled usa fundo/texto próprios e cursor de indisponibilidade. **Loading é diferente de disabled**: deve informar a operação em texto, usar `aria-busy` e, opcionalmente, spinner; se bloquear repetição, manter o significado de carregamento explícito. Não usar cursor de espera para todo botão desabilitado.

Painéis usam bordas discretas apenas para separação. Contornos de campos e controles, foco e indicadores funcionais precisam ser perceptíveis sobre a superfície real. Conferir contraste após composição, inclusive hover, active, avisos e estados indisponíveis: alvo mínimo 4,5:1 para texto comum, 3:1 para texto grande e elementos funcionais. Não inferir aprovação pela imagem de referência ou pela tabela de hexadecimais.

### Contraste calculado dos tokens planos

Os valores abaixo foram calculados pela luminância relativa sRGB dos hexadecimais, com razão `(LmaisClara + 0,05) / (LmaisEscura + 0,05)`, arredondada a duas casas. São verificações numéricas dos pares opacos; não comprovam composição renderizada, foco em todos os elementos, gradientes, estados do navegador ou vídeo.

| Primeiro token / segundo token | Razão |
|---|---|
| `--color-text` / `--color-surface` | 17,78:1 |
| `--color-text-secondary` / `--color-surface-raised` | 10,51:1 |
| `--color-text-muted` / `--color-surface-raised` | 6,71:1 |
| `--color-text-muted` / `--color-surface-hover` | 6,05:1 |
| `--color-on-action` / `--color-action` | 6,10:1 |
| `--color-on-action` / `--color-action-hover` | 7,52:1 |
| `--color-on-action` / `--color-action-active` | 9,23:1 |
| `--color-success` / `--color-success-bg` | 9,92:1 |
| `--color-info` / `--color-info-bg` | 9,49:1 |
| `--color-warning` / `--color-warning-bg` | 10,69:1 |
| `--color-danger` / `--color-danger-bg` | 9,41:1 |
| `--color-disabled-text` / `--color-disabled-bg` | 5,86:1 |
| `--color-border-strong` / `--color-surface-raised` | 3,68:1 |
| `--color-border-strong` / `--color-surface` | 3,89:1 |
| `--color-focus` / `--color-surface-raised` | 11,45:1 |
| `--color-accent` / `--color-surface` | 8,66:1 |
| `--color-text` / `--color-bg` | 18,64:1 |
| `--color-text-secondary` / `--color-surface` | 11,11:1 |
| `--color-text-muted` / `--color-surface` | 7,10:1 |
| `--color-border-strong` / `--color-bg` | 4,08:1 |
| `--color-focus` / `--color-surface` | 12,10:1 |
| `--color-accent` / `--color-accent-subtle` | 7,81:1 |
| `--color-danger` / `--color-surface` | 11,18:1 |

A borda decorativa `--color-border` não foi definida para delimitar controles sozinha. O relatório da entrega registra separadamente as páginas e estados realmente inspecionados no navegador.

## 3. Tipografia e conteúdo

Usar `--font-sans`: `system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`. Para endpoints, UUIDs, códigos e histórico, usar `--font-mono`: `"Cascadia Code", Consolas, "Liberation Mono", monospace`. São pilhas locais; não pressupor que a mesma fonte estará instalada em todas as máquinas.

| Escala | Valor | Uso |
|---|---|---|
| `--text-xs` | `0.75rem` | Identificadores de seção e metadados curtos |
| `--text-overline` | `0.625rem` | Contexto decorativo da marca no cabeçalho; não usar para instruções ou estado operacional |
| `--text-sm` | `0.875rem` | Ajuda de campo, badges e diagnóstico |
| `--text-base` | `1rem` | Corpo e campos; botões usam `--text-sm` |
| `--text-lg` | `1.125rem` | Subtítulos e títulos de painel; introdução usa corpo com entrelinha relaxada |
| `--text-xl` | `1.5rem` | Título de seção relevante |
| `--text-2xl` | `2rem` | Títulos intermediários quando necessários |
| `--text-display` | `clamp(2rem, 4vw, 3.5rem)` | Um título principal por página, maior e com quebra equilibrada |

Pesos: `--weight-normal` 400 (corpo), `--weight-medium` 500 (título principal, títulos de painel, labels e botões), `--weight-semibold` 600 (subtítulos e ênfase) e `--weight-bold` 700 (ênfase quando necessária). Entrelinhas: `--leading-tight` 1,15 para subtítulos, `--leading-normal` 1,5 para leitura geral e `--leading-relaxed` 1,65 para mensagens extensas. O `h1` usa entrelinha 1,06 e espaçamento de letras de −0,05 em; manter essa regra na definição compartilhada, sem repeti-la por feature. Evitar caixa alta em frases e textos técnicos; `eyebrow` pode usar caixa alta em rótulos curtos.

Manter português do Brasil, acentos e verbos de ação. Usar um `h1` e subseções `h2`/`h3` coerentes. Não converter conteúdo técnico em imagem. UUIDs e mensagens devem quebrar com `overflow-wrap: anywhere`; não truncar diagnóstico ou uma instrução necessária. Nunca exibir credenciais para explicar o estado.

## 4. Espaçamento, dimensões e elevação

Base de espaçamento de 4 px com `rem`, respeitando a escala do usuário: `--space-1` 0,25; `--space-2` 0,5; `--space-3` 0,75; `--space-4` 1; `--space-5` 1,25; `--space-6` 1,5; `--space-8` 2; `--space-10` 2,5; `--space-12` 3; `--space-16` 4 rem. Usar essas etapas para padding, margens e gaps; não distribuir números arbitrários pelos componentes.

Raios: `--radius-sm` 0,75 rem (áreas internas e skip link), `--radius-md` 1 rem (campos e avisos), `--radius-lg` 1,5 rem (painéis e molduras de mídia), `--radius-xl` 2 rem (grandes agrupamentos, quando necessários), `--radius-pill` 999 px (botões, badges e cabeçalho). A geometria orgânica da forma ambiente é específica da decoração e fica encapsulada em `ambient-orb.css`.

| Token de dimensão/efeito | Valor | Uso |
|---|---|---|
| `--border-width` | `1px` | Borda padrão |
| `--focus-width` | `2px` | Espessura do foco; offset de 4 px na regra compartilhada |
| `--control-height` | `2.75rem` | Altura mínima de controles, 44 px na escala padrão; conteúdo longo pode aumentar |
| `--icon-size` | `1.125rem` | Ícone junto a texto e spinner |
| `--icon-lg` | `1.5rem` | Ícone de destaque e placeholder compacto |
| `--layout-max` | `80rem` | Largura máxima da aplicação |
| `--page-gutter` | `var(--space-8)`; `var(--space-4)` em até 48 rem | Margem interna lateral responsiva |
| `--shadow-panel` | `0 16px 64px #00000040` | Sombra escura suave de painel |
| `--shadow-action` | `0 4px 24px #9d55ff38, inset 0 1px 0 #ffffff26` | Destaque contido e filete da ação principal |
| `--duration-fast` | `140ms` | Transições de fundo, borda e sombra dos controles |

A elevação depende primeiro da cor da superfície e do respiro; bordas de painéis ficam sutis e não precisam dividir cada cabeçalho. Sombras devem ser escuras e suaves; brilho violeta fica restrito à decoração e à ação destacada. O símbolo de placeholder pode receber luz contida enquanto não há mídia. Não aplicar glow sobre vídeo ativo, texto pequeno, mensagens de erro ou controles de volume. Não criar uma elevação nova sem necessidade de hierarquia.

## 5. Layout e responsividade

A estrutura compartilhada identifica a aplicação e contém o conteúdo de largura máxima `--layout-max`, centralizado com margens laterais `--page-gutter`. Cada página usa cabeçalho, área de operação e diagnóstico conforme o fluxo. Navegação só pode apontar a destinos reais disponíveis na configuração atual.

O conteúdo principal usa respiro superior de `--space-12` no desktop e `--space-8` no mobile, com `--space-8` entre grupos principais. Cabeçalhos e corpos de painéis usam a escala de 24/32 px no desktop e 16/20 px no mobile. O cabeçalho de saúde pode reservar uma introdução mais ampla; nas ferramentas de mídia, manter avisos e preparação próximos das ações. A forma ambiente fica em uma camada independente, recortada pela sua própria região, sem impor overflow ao documento.

- Abaixo de 48 rem: uma coluna, textos e controles com quebra natural; botões podem ocupar a largura disponível. Não esconder avisos para economizar espaço.
- Entre 48 e 64 rem: adaptar densidade e agrupar controles compatíveis; priorizar a mídia e a leitura dos formulários.
- A partir de 64 rem: área de mídia e preparação podem formar duas colunas; nenhuma coluna pode impor largura mínima superior ao viewport.
- Usar `min-width: 0` nos filhos de grid/flex; evitar larguras fixas em campos, tabelas ou diagnósticos. Não compensar overflow com ocultação global que corte foco ou conteúdo.
- Verificar 390, 768 e 1440 px, zoom, mensagens longas, UUID e foco. A página não deve produzir rolagem horizontal para leitura normal.

Os breakpoints são decisões do projeto, não medidas retiradas das imagens. As rotas T-02 e T-03 preservam seu isolamento por configuração; o design system não cria menu público para as ferramentas experimentais.

## 6. Componentes reutilizáveis e estados

Reutilização simples: classes globais de apresentação e componentes Angular em `shared` quando já usados por mais de uma página. Regras de permissão e ciclo de mídia continuam nas features e no backend. Não criar uma biblioteca genérica de componentes sem necessidade real.

| Padrão | Classes/estrutura | Estados e regras |
|---|---|---|
| Botão primário | `.button.button-primary` | Uma ação dominante por grupo; hover/active, foco, disabled e loading explícitos |
| Botão secundário | `.button.button-secondary` | Validar novamente, ativar áudio, copiar e alternar ação auxiliar |
| Botão destrutivo | `.button.button-danger` | Parar/sair/limpar quando apropriado; rótulo explica a operação, sem depender de vermelho |
| Ação discreta | `.button.button-quiet` ou link | Apoio real; manter contraste e foco |
| Campos | `.field`, `.field-label`, `.field-message` | `label` associado, ajuda por `aria-describedby`; erro por `aria-invalid` quando aplicável; não usar placeholder como label |
| Input/select | Elementos nativos estilizados | Fundo `--color-bg`, borda funcional, foco e disabled distintos; chave continua `type="password"` e sem autocomplete |
| Painel/card | `.panel`, `.panel-header`, `.panel-title`, `.panel-body` | Agrupa uma responsabilidade; borda sutil, título e padding consistentes |
| Badge | `.badge` + variante semântica | Texto curto para estado ou escopo; não funciona como botão sem ação |
| Aviso | `.notice` + `notice-info/success/warning/danger` | Texto acionável, ícone opcional, cor auxiliar; preservar UUID, motivo e forma de recuperação |
| Progresso | `.spinner` e mensagem de etapa | Rotação de 900 ms enquanto há operação real, removida com movimento reduzido; sem percentuais inventados ou animação infinita sem timeout do fluxo |
| Área de mídia | `.media-frame`, `.media-placeholder`, `.media-caption` | Fundo neutro, proporção preservada e `object-fit: contain`; explicar espera sem imagem quebrada |
| Controles de mídia | `.media-toolbar`, `.actions` | Ações existentes, volume local com label; acesso por teclado e disposição que quebra em telas pequenas |
| Histórico | `.diagnostics-panel`, `.diagnostics` | `details/summary`, lista selecionável e copiável, quebra de UUID, região rolável acessível; últimos 40 avisos só em memória |
| Resultados técnicos | `.check-list`, `.check-item` | Aprovação, falha e andamento com rótulo textual; estado vazio honesto |
| Forma ambiente | `AmbientOrb` / `app-ambient-orb` | Decoração sem estado, foco ou interação; ocultada em movimento reduzido e cores forçadas |

As variantes de `.badge` usam `.info`, `.success`, `.warning` e `.danger`. Separar estado do acesso, estado da sessão e estado de reprodução. Uma chave válida não comprova transmissão; um frame reservado para vídeo não comprova recepção. `idle`, seleção, conexão, publicação, espera, live, reconexão, erro e encerramento devem manter seus rótulos e mensagens reais.

O histórico não vira armazenamento persistente nem log bruto. Não copiar chaves, JWTs, URLs de sinalização, respostas arbitrárias do servidor ou mídia. Avisos de limpeza permanecem separados da causa original.

Os elementos de vídeo e áudio devem manter as referências e a presença no DOM exigidas pelos componentes. Não substituir uma referência estática por conteúdo criado condicionalmente apenas para obter um estado vazio. Prévia local continua silenciada; autoplay bloqueado oferece a ação existente de áudio. Tela cheia e volume não alteram o consentimento de captura.

## 7. Acessibilidade, foco e movimento

- Preservar HTML semântico, labels, ordem de leitura e navegação por teclado. Ícones decorativos usam `aria-hidden`; botões continuam com texto acessível.
- Aplicar foco visível de alto contraste com offset, sem removê-lo para reproduzir o contorno da referência. A ordem visual precisa acompanhar a ordem do DOM.
- Usar `role="status"`/`aria-live="polite"` para atualizações de estado e `role="alert"` para falhas urgentes. Evitar regiões vivas duplicadas que anunciem a mesma mensagem repetidamente; não anunciar cada item do histórico como alerta.
- Indicar campos inválidos e a operação ocupada semanticamente. Não comunicar erro, sucesso ou indisponibilidade só por cor, spinner ou ícone.
- Manter instruções importantes no conteúdo visível, inclusive consentimento e risco de áudio do sistema; não relegá-las a tooltip/hover.
- Respeitar `prefers-reduced-motion: reduce`: remover a forma ambiente, rotação, pulsação e transições dispensáveis. O texto de andamento continua informativo sem movimento. A animação decorativa usa ciclo lento de 24 s com variação alternada; não pode piscar nem sugerir um estado funcional.
- Respeitar `forced-colors: active`: ocultar a decoração e preservar limites de controles, painéis, badges e avisos com cores do sistema.
- Manter controles nativos de áudio e range operáveis. Não sobrepor decoração ou estado vazio aos controles de mídia.
- Verificar contraste com os pares efetivamente renderizados. A cor sutil da borda de painel não deve ser usada como único limite de um campo interativo.

## 8. Mapeamento da spec para implementação

| Arquivo | Responsabilidade |
|---|---|
| `frontend/src/styles.css` | Tokens de cor/tipografia/escala, base acessível, padrões compartilhados, layout responsivo, estados e movimento reduzido |
| `frontend/src/app/shared/components/app-shell.ts` e `app-shell.css` | Estrutura comum da aplicação e iluminação decorativa contida |
| `frontend/src/app/shared/components/ambient-orb.ts` e `ambient-orb.css` | Forma luminosa local em CSS; consumo dos tokens `--color-orb-*`, máscaras e animação decorativa de 24 s; sem eventos, serviços ou estado |
| `frontend/src/app/shared/components/app-icon.ts` | Ícones vetoriais locais, sem dependência externa |
| `frontend/src/app/app.ts` | Composição da estrutura compartilhada com o RouterOutlet |
| `frontend/src/app/features/health/health-page.html` | Saúde: consulta, resposta UP, indisponibilidade e nova tentativa |
| `frontend/src/app/features/dev-livekit/livekit-proof-page.html` | Operação, receptor sintético, resultados e diagnósticos T-02 |
| `frontend/src/app/features/dev-capture/capture-page.html` | Preparação por papel, captura/player, estados e histórico T-03 |
| `frontend/angular.json` e `src/app/app.routes*.ts` | Limites dos builds; a apresentação deve respeitá-los |

Todos os tokens das seções 2 a 5 são declarados no `:root` de `styles.css`; `--page-gutter` é sobrescrito no breakpoint de 48 rem do mesmo arquivo. `app-shell.css` consome esses tokens para marca local, estrutura em pílula, skip link, rodapé e posição da forma ambiente. `ambient-orb.css` concentra sua geometria, máscaras, filtros e animação; as cores permanecem nos tokens centrais. Os templates das features consomem as classes globais. Nenhum token exige fonte, imagem ou requisição remota.

Estilos locais só acrescentam composição específica quando não couber em um padrão já usado. Não reintroduzir paletas claras, cores hexadecimais arbitrárias ou regras globais conflitantes nos componentes. Ao mudar um token ou regra compartilhada, conferir todas as páginas e sincronizar esta tabela.

## 9. Exemplos e práticas a evitar

```html
<section class="panel" aria-labelledby="preparacao-title">
  <div class="panel-header"><h2 id="preparacao-title" class="panel-title">Preparação</h2></div>
  <div class="panel-body">
    <label class="field-label" for="chave">Chave experimental</label>
    <input id="chave" type="password" autocomplete="off" aria-describedby="chave-ajuda" />
    <p id="chave-ajuda" class="helper">A validação não inicia a captura.</p>
    <button type="button" class="button button-secondary">Validar chave novamente</button>
  </div>
</section>
```

```html
<p class="notice notice-info" role="status" aria-live="polite" aria-busy="true">
  <span class="spinner" aria-hidden="true"></span> Validando a chave…
</p>
```

```css
.novo-agrupamento {
  padding: var(--space-6);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
}
```

Evitar: `color: purple` local, texto pequeno sobre gradiente, disabled indistinguível de loading, UUID truncado, botão só com ícone sem nome, menu sem destino, métrica fictícia, efeito sobre vídeo, controle visual que substitua autorização no servidor. Os exemplos ilustram apresentação; conectar os handlers e estados reais ao implementar uma página.

## 10. Checklist para novas páginas

- [ ] Ler esta spec e os contratos/fluxos relevantes; registrar qualquer mudança funcional nos documentos e cenários correspondentes.
- [ ] Reutilizar tokens, estrutura e padrões; manter `core/shared/features` e conteúdo em pt-BR.
- [ ] Definir hierarquia de título, ação principal, conteúdo de apoio e recuperação sem criar controles sem função.
- [ ] Cobrir estado vazio, carregamento, sucesso, erro, indisponibilidade e encerramento aplicáveis, sem afirmar resultados não comprovados.
- [ ] Conferir labels, mensagens de campo, `aria-live`, foco, teclado, zoom, contraste real e movimento reduzido.
- [ ] Conferir 390/768/1440 px, mensagens longas, UUIDs, quebra de ações e ausência de overflow horizontal.
- [ ] Preservar consentimento, áudio obrigatório, prévia silenciada, referências DOM e liberação de tracks/conexões. Não esperar HTTP antes de `getDisplayMedia` no clique.
- [ ] Preservar escopo de `production`, `development` e `capture-proof`; não alterar proxy/HTTPS/Host/Origin para estilizar.
- [ ] Executar builds e testes adequados; guardar evidências visuais com dados fictícios e distinguir simulação de estado de mídia real.
- [ ] Atualizar esta spec quando o padrão mudar. Não declarar homologação de duas máquinas Windows com base em layout, mocks ou testes unitários.
