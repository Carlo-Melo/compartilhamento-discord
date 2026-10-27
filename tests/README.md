# Validação do sistema
Pastas reservadas para integration, e2e, performance e manual.
Critérios: specs/10-validacao.md. Cenários: specs/tests/aceite.feature.
Resultados reais devem indicar data, versão, ambiente e status aprovado/reprovado. O [bootstrap T-01](manual/T-01-bootstrap.md) foi validado com PostgreSQL e backend reais; os testes de produto (bot/acesso/mídia) continuam não executados.

[T-02 LiveKit](manual/T-02-livekit.md): JUnit e prova Angular contra SFU real aprovados, com administração/grants, recepção sintética, isolamento e exclusão/reuso verificados. Os cenários documentais não são apresentados como automação executada; captura real e áudio do Windows ficam na T-03.
Não persistir capturas/áudio pessoais como fixtures; usar tela e som sintéticos de teste.

[T-04 — nove espectadores, TURN e tokens](manual/T-04-multiespectador-turn.md): implementação experimental e validação parcial. Distingue doubles, integração SFU, mídia sintética, captura real e itens pendentes. A homologação entre máquinas/redes foi adiada pelo usuário; G1/G2 continuam pendentes e o ensaio de duas horas pertence à T-23. [Cenários T-04](../specs/tests/multiespectador-turn.feature) são documentais.

[UI-01 — Design system](manual/UI-01-design-system.md): quatro páginas reais, builds dos três escopos, 57 testes de captura/feedback e oito do proxy aprovados. Evidências em 390/768/1440 px e prévia de estados fictícios; não homologa mídia real.

[T-03 captura real](manual/T-03-captura-real.md): implementação experimental entregue; homologação Windows entre máquinas pendente. Testes de captura/sessão usam doubles para validar falhas e limpeza; não comprovam som real. O relatório separa resultados aprovados, limitações e matriz humana a executar. `frontend/npm run test:capture`, testes Node do proxy e `scripts/Test-CaptureProofSetup.ps1` não abrem nem autorizam captura nativa.

[T-05 segurança do backend](manual/T-05-seguranca-backend.md): concluída com builds e HTTP Spring real aprovados, política de negação por padrão, bootstrap CSRF/Origin, erros sanitizados e regressão do isolamento dos três artefatos. A sessão técnica de CSRF não concede acesso ao produto; tickets e contexto autorizado continuam na T-07. O relatório separa verificações executadas de integrações/mídia não executadas.
