# 13 — Fontes e fatos externos
Consulta original: 25/10/2026; revisão de acesso por bot: 26/10/2026. As escolhas normativas do projeto são propostas nossas ou decisões do usuário; não são promessas feitas pelos fornecedores. Revalidar preços, versões e políticas antes do deploy.

## Stack
- [Spring Boot — requisitos](https://docs.spring.io/spring-boot/system-requirements.html): documentação consultada 4.1.2, compatível com Java 21.
- [Angular — matriz de versões](https://angular.dev/reference/versions): Angular 22 requer versões compatíveis de Node/TypeScript/RxJS; manter core/CLI alinhados.
- [PostgreSQL — política de versões](https://www.postgresql.org/support/versioning/): linha 18 estável suportada; usar patch atual da linha escolhida, sem adotar RC como baseline.

## Navegador
- [MDN — getDisplayMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia): captura depende de permissão/gesto, contexto seguro e suporte das fontes. Solicitar audio não garante receber track; opções de áudio são condicionadas ao ambiente.
- [Chrome — controles de captura](https://developer.chrome.com/docs/web-platform/screen-sharing-controls): orientações de seleção e som de sistema; captura geral pode incluir áudio remoto. Homologação real prevalece sobre inferência de compatibilidade.

## Discord
- [Get Guild Member](https://docs.discord.com/developers/resources/guild#get-guild-member): consulta individual de associação via bot; não importar lista de membros.
- [Interactions](https://docs.discord.com/developers/interactions/receiving-and-responding): entrada assinada e identificação de member/guild na ação. O navegador não recebe essa identidade automaticamente.
- [Botões](https://docs.discord.com/developers/components/reference#button): custom_id gera interação; botões de link não enviam interação à aplicação. Isso fundamenta a passagem por resposta privada.
- [Application commands](https://docs.discord.com/developers/interactions/application-commands): comandos/contextos/instalação.
- [Activities](https://docs.discord.com/developers/activities/how-activities-work): aplicação web embutida, fora do MVP.
- [Situação dos recursos de vídeo no Brasil](https://support.discord.com/hc/pt-br/articles/42704051358359): comunicado da suspensão de Go Live/vídeo. Uma futura Activity depende de confirmação específica; o MVP não tenta reativar esses recursos.

## Mídia
- [LiveKit — deploy self-hosted](https://docs.livekit.io/transport/self-hosting/deployment/): TLS, conectividade e TURN.
- [SDK oficial Java/Kotlin](https://github.com/livekit/server-sdk-kotlin): SDK JVM com exemplos Java, tokens e APIs de sala; artefato io.livekit:livekit-server.
- [Captura de tela](https://docs.livekit.io/transport/media/screenshare/): integração de tracks de tela/áudio.
- [Tokens e grants](https://docs.livekit.io/frontends/reference/tokens-grants/): renovação automática e limites de revogação em self-hosted; remoção não implica invalidar JWT já emitido.
- [Configuração oficial do servidor](https://github.com/livekit/livekit/blob/master/config-sample.yaml): room.auto_create e limites de sala; confirmar na versão fixada.
- [API de salas](https://docs.livekit.io/reference/other/roomservice-api/): criação, participantes e exclusão.

## Orçamento e método
- [Preços DigitalOcean](https://www.digitalocean.com/pricing/droplets): referência pública consultada de VPS/franquia; não há fornecedor escolhido. Cotação real incluindo câmbio/impostos é necessária.
- [GitHub Spec Kit](https://github.com/github/spec-kit): referência para fluxo especificação -> plano -> tarefas -> validação. Este pacote não instala nem exige Spec Kit.
