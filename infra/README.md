# Infraestrutura
Pastas reservadas; não há deploy ou arquivo de containers executável neste estágio.
- docker: futura composição local de backend, banco, SFU e proxy.
- postgres: inicialização e operação; schema somente de bot/vínculo, versionado no backend/Flyway; sem dados operacionais de usuários.
- livekit: configuração da SFU e TURN, sem chaves reais.
- proxy: TLS, rotas HTTP/WebSocket e política de origens.

Piloto em uma VPS, sem exposição pública do PostgreSQL ou APIs administrativas de mídia.
Não encaminhar áudio/vídeo WebRTC através do Spring Boot. Detalhes e portas em specs/09-infra-e-operacao.md.
