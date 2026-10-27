# T-04 — ensaio sintético opt-in

Auxiliar separado dos entrypoints/rotas/bundles Angular. Usa o SDK fixado, a API `capture-proof` e o mesmo proxy restrito da T-03. Não solicita tela, câmera ou microfone; o documento bloqueia essas permissões. Não conclui G1/G2 nem substitui teste Windows/rede externa/UDP bloqueado.

Pré-requisitos: Node 24.22.1, dependências já instaladas, backend `capture-proof` opt-in e SFU exclusiva configurados pelos READMEs. Origem continua HTTPS em IPv4 privado explícito:4443. Certificado e chave já devem existir e ser confiáveis nos clientes por preparação do operador; este auxiliar não instala/confia certificados nem abre portas/firewall. Pare o Angular da porta4443 antes de usar este servidor. Nunca execute sobre uma prova de captura em andamento.

Na raiz, com o runtime fixado no PATH:

```powershell
.\scripts\Import-CaptureEnvironment.ps1
Set-Location frontend
node node_modules/typescript/bin/tsc -p tests/t04/tsconfig.json
node tests/t04/serve.mjs --opt-in-t04 'C:\certificados\capture-cert.pem' 'C:\certificados\capture-key.pem'
```

Abra `/` na origem privada configurada. Cole somente as chaves experimentais dos dois papéis nos campos protegidos; os valores são apagados dos campos no clique e usados somente em memória. Nunca coloque chaves na URL, argumentos, storage ou resultados. Desabilite `DEBUG`/`NODE_DEBUG`; o servidor recusa modo verboso e saneia mensagens. A API administrativa SFU não é encaminhada.

Execute primeiro com a SFU sem TURN e **Direto**, depois com o opt-in TURN preparado e **TURN**. O modo direto exige par selecionado sem candidato relay; o modo TURN aplica somente `iceTransportPolicy: relay` e exige relay selecionado para apresentador e nove receptores. ICE URLs/credenciais vêm da SFU. `protocol: udp` pode aparecer com `relayProtocol: tls`: o segundo identifica o transporte cliente–TURN. O relatório copia somente tipos/protocolos/booleanos/contadores, nunca endereço, porta, SDP, URL, nonce ou token.

O ensaio:

- Publica canvas dinâmico VP8 e tom440Hz/Opus, com confirmação das duas fontes na SFU.
- Conecta oito identidades; disputa a última vaga com duas entradas concorrentes. Exige exatamente uma admissão e uma recusa de capacidade; SFU confirma nove espectadores.
- Exige vídeo/áudio RTP crescentes, quadros decodificados e RMS em cada um dos nove receptores. O tom é analisado com ganho local zero. Som audível continua humano.
- Confere par ICE efetivamente selecionado para os dez participantes, conforme o modo escolhido.
- Repete uma entrada sem nova vaga; sai e retorna com o mesmo nonce em memória, preservando a identidade e a reserva da geração.
- Ultrapassa somente a barreira local de publicação do SDK, sem alterar JWT; exige AddTrack real, resposta SFU `NOT_ALLOWED` e zero publicações locais. Timeout genérico não aprova.
- Observa `tokenRefreshed` real do SDK/SFU durante a conexão, aceitando apenas token diferente, da mesma sala e com expiração estendida. Sem evento observado, registra lacuna, sem substituir por outro JWT do gateway.
- Exclui A, exige desconexão dos dez/ausência, e recusa explícita de JWT inicial ainda válido sem recriação. Cria B com nome/identidade novos e mídia sintética ativa; exige recusa dos tokens de A inicial e renovado observado, bem como HTTP401 para room adulterado com assinatura original. B permanece LIVE e sem espectadores antigos.

**Copiar resultados sanitizados** guarda apenas o texto composto localmente. Anexe ao relatório T-04 com data, navegador, versões, cenário e duração reais. Falha/rejeição inesperada interrompe o ensaio; não marque as etapas seguintes como aprovadas. Um passe curto não prova estabilidade por duas horas (T-23), throughput/custo ou nove máquinas físicas.

**Interromper e limpar** interrompe fontes/conexões e aguarda operações pendentes antes de excluir a sala. Falha de remoção mantém **Repetir limpeza** e bloqueia nova execução. Encerramento normal confirma ausência; fechar abruptamente pode impedir HTTP. Mantenha backend/SFU ativos até confirmar limpeza; após falha/reinício siga o procedimento de derrubar somente a composição experimental. Ctrl+C encerra o servidor do harness. Nenhum arquivo com estado operacional ou mídia é gravado.

Fontes oficiais compatíveis: [RTCEngine2.22.3](https://github.com/livekit/client-sdk-js/blob/v2.22.3/src/room/RTCEngine.ts) copia ICE da SFU apenas se `rtcConfig.iceServers` estiver ausente e atualiza token a partir da sinalização; [Room2.22.3](https://github.com/livekit/client-sdk-js/blob/v2.22.3/src/room/Room.ts) expõe engine/PC managers usados no diagnóstico experimental; [roommanager1.13.10](https://github.com/livekit/livekit/blob/v1.13.10/pkg/service/roommanager.go) envia o primeiro refresh no início do worker e repete a cada5min, com TTL10min. O harness preserva versões e não envia relatórios RTC brutos.
