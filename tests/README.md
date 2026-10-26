# Validação do sistema
Pastas reservadas para integration, e2e, performance e manual.
Critérios: specs/10-validacao.md. Cenários: specs/tests/aceite.feature.
Resultados reais devem indicar data, versão, ambiente e status aprovado/reprovado. O [bootstrap T-01](manual/T-01-bootstrap.md) foi validado com PostgreSQL e backend reais; os testes de produto (bot/acesso/mídia) continuam não executados.

[T-02 LiveKit](manual/T-02-livekit.md): JUnit e prova Angular contra SFU real aprovados, com administração/grants, recepção sintética, isolamento e exclusão/reuso verificados. Os cenários documentais não são apresentados como automação executada; captura real e áudio do Windows ficam na T-03.
Não persistir capturas/áudio pessoais como fixtures; usar tela e som sintéticos de teste.
