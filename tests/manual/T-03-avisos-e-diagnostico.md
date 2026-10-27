# T-03 — avisos, validação de chave e diagnóstico

26/10/2026. Melhoria funcional da prova experimental; não homologa captura/áudio Windows.

## Comportamento entregue

- Colar a chave em qualquer página agenda validação após 500 ms. Formato inválido não envia requisição; editar/limpar invalida autorização imediatamente. Respostas antigas são ignoradas.
- `GET /api/dev/capture/session/access` valida somente a capacidade e retorna o papel real, sem SFU, sala ou JWT. Chave válida para outra página gera orientação; início depende do papel correto. Cada operação continua autorizada no servidor.
- A validação termina antes do clique de captura. O seletor continua abrindo exclusivamente por gesto explícito, sem esperar uma chamada HTTP dentro desse gesto. Ao iniciar, campo e autorização antecipada são consumidos; nova tentativa requer colagem.
- Avisos distinguem formato, papel incorreto, recusa, timeout, backend indisponível, API ausente, publicação, confirmação SFU e falha de conexão. Erro HTTP preserva apenas código permitido e UUID, nunca mensagem/corpo bruto do servidor.
- Histórico por página: últimos 40 avisos em memória, com cópia por gesto. Sem chaves, JWTs, mídia ou persistência. Falha de limpeza tem aviso separado e não apaga a causa original.
- Backend registra somente ação fixa, status HTTP e requestId. Consultas de estado bem-sucedidas não geram registros repetitivos.
- Prévia vazia não é mostrada como mídia quebrada antes da autorização. Chrome/Edge são a matriz prevista; outros navegadores seguem sem homologação.

## Defeito encontrado durante a verificação

O navegador recebia 404 no proxy HTTPS enquanto a mesma rota por HTTP/1 retornava 200. Reproduzido com HTTP/2 real: o servidor entregava `:authority` sem Host e o proxy rejeitava a requisição antes do backend. A correção valida ambos, quando presentes, normaliza para HTTP/1 e preserva método/caminho antes de remover pseudoheaders. A mesma prova HTTP/2 passou a retornar 200. Não se afirma que esta foi a única causa da tentativa anterior do usuário no Opera.

## Verificações executadas

| Verificação | Resultado |
|---|---|
| `npm run test:capture` | 57 aprovados, zero falhas; inclui race de chave, papel, saneamento e preservação do erro ao falhar limpeza |
| `node --test proxy.capture.test.cjs` | 8 aprovados; regressão HTTP/2, Host/Origin, caminhos restritos e logs sanitizados |
| Angular capture-proof e production | Builds aprovados; production mantém bundle sem a ferramenta |
| Maven capture-proof verify | 32 testes: 29 aprovados e 3 pulados, zero falhas/erros |
| Testes HTTP Spring | Validação devolve somente papel, funciona com SFU indisponível, recusa chave/origem inválidas e permanece ausente nos artefatos/profiles previstos |
| HTTPS local real | Apresentador e espectador retornaram 200 com papel correspondente; chave inválida retornou 403 com UUID |
| HTTP/2 HTTPS real | Rota validada retornou 200 após correção, sem ignorar o certificado |
| UI no navegador integrado | Aviso imediato de formato, validação automática e recusa com UUID observados nas duas páginas; início bloqueado sem validação |
| Cópia de diagnóstico na UI | Confirmada; texto copiado não contém chave de teste nem padrão JWT |
| OpenAPI e diff | JSON válido e git diff --check aprovado |

Um teste antigo de filtro precisou de getServletPath/getStatus na fixture após a inclusão do log; a execução final passou. Os três testes pulados são dois de SFU real opt-in e um do auxiliar T-02 ausente no artefato T-03.

Não executados nesta revisão: seletor nativo, mídia real, áudio audível, duas máquinas Windows, nove espectadores/TURN e jornada Discord. Nenhuma captura foi autorizada pela automação. HTTPS e serviços foram reiniciados no endereço experimental existente; não houve liberação de firewall, conta ou serviço pago.

## Retomar manualmente

Recarregar a página no navegador de teste. Colar somente o valor da chave do papel; aguardar confirmação e clicar para iniciar. Na falha, usar **Copiar diagnóstico** e informar o relatório, sem copiar a chave ou URLs de sinalização. Uma chave validada confirma acesso; transmissão exige vídeo e áudio confirmados pela SFU e recepção humana.
