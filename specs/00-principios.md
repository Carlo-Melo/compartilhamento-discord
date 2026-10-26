# 00 — Princípios de desenvolvimento
Status: normativo para o MVP.

- **C-01 Funcionalidade antes de aparência.** Cada entrega precisa executar um fluxo real. Sem landing page, animações, design system próprio ou dashboard comercial na primeira versão.
- **C-02 Decisão de produto explícita.** Dúvidas que mudam escopo, custo, acesso ou integrações externas exigem resposta do usuário. Escolhas internas reversíveis usam os ADRs propostos de 06.
- **C-03 Permissão de captura.** Nunca selecionar tela em nome do usuário, iniciar captura ao carregar a página ou manter captura após encerramento local.
- **C-04 Áudio obrigatório.** Não anunciar sucesso de transmissão sem vídeo e track de áudio publicados. Silêncio no conteúdo não equivale a falha; a ausência de track equivale.
- **C-05 Servidor como fronteira.** A interação assinada do Discord identifica usuário, bot e servidor. O backend emite ticket privado de uso único e valida associação via REST do bot; não há login separado no site. URL pública e estado do frontend não autorizam acesso.
- **C-06 Backend como autoridade.** Permissões de publicar/assinar mídia são emitidas pelo backend e aplicadas na SFU. Botões ocultos não são controle de acesso.
- **C-07 Sem mídia persistida.** Não gravar áudio, tela ou miniaturas por padrão. Diagnóstico usa metadados mínimos.
- **C-08 Simplicidade operacional.** Um nó, uma sala simultânea no piloto, sem Kubernetes, CDN de vídeo, transcodificação ou banco gerenciado obrigatório.
- **C-09 Evidência antes de promessa.** Compatibilidade, latência e capacidade precisam de resultados reais. Interface conceitual anterior não constitui suporte da plataforma.
- **C-10 Mudanças rastreáveis.** Toda tarefa cita requisitos e testes. Alteração de API altera OpenAPI, consumidor e cenários juntos.
- **C-11 Falhas visíveis e recuperáveis.** Mensagens acionáveis, sem spinner infinito; retries limitados e operações de encerramento idempotentes.
- **C-12 Segredos fora dos artefatos.** Tokens de bot e chaves de infraestrutura não entram em repositório, documentos, links, logs ou bundles. O ticket de acesso é exceção explícita: apenas em link privado, no fragmento, com uso único e TTL curto; removê-lo da URL ao abrir.
- **C-13 Custo controlado.** Proibir abertura pública de salas e cobrança variável ilimitada. Contratação depende da cotação concreta apresentada ao usuário.
- **C-14 Stack aprovada.** Código de aplicação em Java 21/Spring Boot e Angular; PostgreSQL restrito à identificação de bots e seus vínculos com servidores. Não substituir a stack nem adotar microsserviços/arquitetura hexagonal sem nova decisão do usuário.
- **C-15 Persistência mínima.** Nenhuma tabela de usuários, transmissão, participante, acesso web, token ou fila. Estado operacional em memória; reinício invalida o acesso e exige limpeza das salas antigas.
