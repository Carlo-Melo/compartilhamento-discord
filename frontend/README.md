# Frontend — Angular
Estrutura reservada, sem package.json, angular.json ou aplicação executável neste estágio.

Angular standalone com TypeScript, HttpClient e Router.
- core/auth: estado da autorização temporária recebida por ticket; guards e interceptors para experiência HTTP/CSRF.
- core/models: contratos compartilhados; não duplicar regras de autorização.
- shared/components: componentes mínimos realmente reutilizados.
- features/auth/pages: troca de ticket, acesso inválido e retorno ao bot; sem formulário de login.
- features/transmission/pages/broadcast: seletor nativo, prévia, iniciar/parar.
- features/transmission/pages/watch: reprodução, volume, tela cheia e estado.
- features/transmission/services: API da sessão e cliente de mídia.
- features/transmission/models: estado de apresentação da sessão.

API por /api na mesma origem. Tokens Discord nunca passam pelo Angular. Token de mídia somente em memória. Sem SSR necessário no MVP.
getDisplayMedia exige clique na página; não abrir seletor automaticamente ao abrir pelo ticket. O navegador decide fontes de áudio disponíveis.
O fragmento /acesso#ticket=... é removido imediatamente e trocado por POST com CSRF. Ticket, contexto e token de mídia não vão para localStorage. Reinício/expiração exige novo acesso pelo bot.
