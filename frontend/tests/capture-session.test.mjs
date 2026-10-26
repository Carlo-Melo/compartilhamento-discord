import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const compile = (source) => ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText;
const dataModule = (source) => `data:text/javascript;base64,${Buffer.from(compile(source)).toString('base64')}`;
const displayModule = dataModule(await readFile(new URL('../src/app/features/dev-capture/display-capture.ts', import.meta.url), 'utf8'));
const source = await readFile(new URL('../src/app/features/dev-capture/capture-session.ts', import.meta.url), 'utf8');
const { CaptureSession, CaptureApiFailure } = await import(dataModule(source.replace("'./display-capture'", JSON.stringify(displayModule))));
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const flush = async () => { for (let i = 0; i < 30; i++) await Promise.resolve(); };
const presenterAccess = { roomName: 'test-room', identity: 'presenter', presenterIdentity: 'presenter', role: 'PRESENTER',
  serverUrl: 'wss://test.invalid', token: 'not-a-real-token', expiresAt: '2099-01-01T00:00:00Z' };
const live = { exists: true, live: true, roomName: 'test-room', presenterIdentity: 'presenter', viewerIdentity: 'viewer' };

function setup(overrides = {}) {
  const calls = { select: 0, captureStop: 0, create: 0, viewer: 0, status: 0, delete: 0, connect: 0, publish: 0, disconnect: 0 };
  const views = [], previews = [], events = [], endedCallbacks = [];
  const tracks = [{ kind: 'video', readyState: 'live' }, { kind: 'audio', readyState: 'live' }];
  const stream = { getVideoTracks: () => tracks.filter((t) => t.kind === 'video'),
    getAudioTracks: () => tracks.filter((t) => t.kind === 'audio') };
  const api = {
    create: async () => { calls.create++; return overrides.create ? overrides.create() : presenterAccess; },
    viewer: async () => { calls.viewer++; return { ...presenterAccess, identity: 'viewer', role: 'VIEWER' }; },
    status: async () => { calls.status++; return overrides.status ? overrides.status() : live; },
    delete: async () => { calls.delete++; if (overrides.delete) await overrides.delete(); },
  };
  const capture = {
    select: (_quality, ended) => {
      calls.select++; endedCallbacks.push(ended);
      tracks.forEach((t) => { t.readyState = 'live'; });
      return overrides.select ? overrides.select() : Promise.resolve(stream);
    },
    stop: () => { calls.captureStop++; tracks.forEach((t) => { t.readyState = 'ended'; }); },
  };
  const factory = (event) => {
    events.push(event);
    return {
      connect: async () => { calls.connect++; if (overrides.connect) await overrides.connect(); },
      publish: async () => { calls.publish++; if (overrides.publish) await overrides.publish(); },
      disconnect: async () => { calls.disconnect++; if (overrides.disconnect) await overrides.disconnect(); },
      enableAudio: async () => {},
    };
  };
  const session = new CaptureSession(api, capture, factory, (view) => views.push(view), (value) => previews.push(value), async () => {});
  return { session, calls, views, previews, events, endedCallbacks, tracks, stream, latest: () => views.at(-1) };
}

test('apresentador só anuncia live após consulta SFU confirmar ambas as fontes', async () => {
  const status = deferred();
  const h = setup({ status: () => status.promise });
  const start = h.session.start('PRESENTER');
  await flush();
  assert.equal(h.calls.select, 1);
  assert.equal(h.calls.publish, 1);
  assert.equal(h.latest().phase, 'waiting');
  assert.ok(h.views.every((view) => view.phase !== 'live'));
  status.resolve(live);
  await start;
  assert.equal(h.latest().phase, 'live');
  await h.session.stop();
});

test('falha na publicação de áudio após vídeo libera captura, conexão e sala', async () => {
  let videoPublished = false;
  const h = setup({ publish: async () => { videoPublished = true; throw new Error('sensitive-media-provider-detail'); } });
  await h.session.start('PRESENTER');
  assert.equal(videoPublished, true);
  assert.equal(h.calls.status, 0);
  assert.equal(h.calls.delete, 1);
  assert.ok(h.calls.disconnect >= 1);
  assert.ok(h.tracks.every((t) => t.readyState === 'ended'));
  assert.equal(h.previews.at(-1), null);
  assert.equal(h.latest().phase, 'error');
  assert.doesNotMatch(h.latest().message, /sensitive-media-provider-detail/);
});

test('SFU sem confirmação de ambas as tracks nunca anuncia sucesso e libera recursos', async () => {
  const h = setup({ status: async () => ({ ...live, live: false }) });
  await h.session.start('PRESENTER');
  assert.equal(h.calls.status, 10);
  assert.ok(h.views.every((view) => view.phase !== 'live'));
  assert.equal(h.latest().phase, 'error');
  assert.equal(h.latest().busy, false);
  assert.equal(h.calls.delete, 1);
  assert.ok(h.tracks.every((t) => t.readyState === 'ended'));
});

test('seleção recusada não cria sala e preserva erro de captura acionável', async () => {
  const { CaptureFailure } = await import(displayModule);
  const h = setup({ select: async () => { throw new CaptureFailure('no-audio', 'Selecione Tela inteira com áudio.'); } });
  await h.session.start('PRESENTER');
  assert.equal(h.calls.create, 0);
  assert.equal(h.calls.delete, 0);
  assert.equal(h.latest().phase, 'error');
  assert.equal(h.latest().message, 'Selecione Tela inteira com áudio.');
});

test('stop durante seletor ignora resultado tardio sem criar sala', async () => {
  const selection = deferred();
  const h = setup({ select: () => selection.promise });
  const start = h.session.start('PRESENTER');
  const stop = h.session.stop();
  assert.equal(h.latest().phase, 'ended');
  selection.resolve(h.stream);
  await Promise.all([start, stop]);
  assert.equal(h.calls.create, 0);
  assert.equal(h.calls.connect, 0);
  assert.equal(h.calls.delete, 0);
  assert.equal(h.latest().busy, false);
  assert.equal(h.previews.at(-1), null);
});

test('stop durante criação limpa a sala retornada tarde sem conectar', async () => {
  const creation = deferred();
  const h = setup({ create: () => creation.promise });
  const start = h.session.start('PRESENTER');
  await flush();
  assert.equal(h.calls.create, 1);
  const stop = h.session.stop();
  creation.resolve(presenterAccess);
  await Promise.all([start, stop]);
  assert.equal(h.calls.connect, 0);
  assert.equal(h.calls.delete, 1);
  assert.equal(h.latest().phase, 'ended');
});

test('criação de resultado incerto também solicita limpeza', async () => {
  const h = setup({ create: async () => { throw new Error('request timeout'); } });
  await h.session.start('PRESENTER');
  assert.equal(h.calls.connect, 0);
  assert.equal(h.calls.delete, 1);
  assert.equal(h.latest().phase, 'error');
});

for (const status of [403, 409, 429]) {
  test(`criação recusada com ${status} não exclui sala alheia e permite nova tentativa`, async () => {
    let attempts = 0;
    const h = setup({ create: async () => {
      if (++attempts === 1) throw new CaptureApiFailure(status);
      return presenterAccess;
    } });
    await h.session.start('PRESENTER');
    assert.equal(h.calls.delete, 0);
    assert.equal(h.calls.connect, 0);
    assert.equal(h.latest().phase, 'error');
    assert.equal(h.latest().cleanupPending, false);
    assert.equal(h.latest().busy, false);
    assert.ok(h.tracks.every((track) => track.readyState === 'ended'));
    await h.session.start('PRESENTER');
    assert.equal(h.calls.create, 2);
    assert.equal(h.latest().phase, 'live');
    await h.session.stop();
    assert.equal(h.calls.delete, 1);
  });
}

test('criação com 500 permanece incerta e solicita limpeza da tentativa', async () => {
  const h = setup({ create: async () => { throw new CaptureApiFailure(500); } });
  await h.session.start('PRESENTER');
  assert.equal(h.calls.delete, 1);
  assert.equal(h.calls.connect, 0);
  assert.equal(h.latest().phase, 'error');
});

test('stop durante connect desconecta imediatamente e novamente após retorno tardio', async () => {
  const connection = deferred();
  const h = setup({ connect: () => connection.promise });
  const start = h.session.start('PRESENTER');
  await flush();
  assert.equal(h.calls.connect, 1);
  const stop = h.session.stop();
  assert.ok(h.calls.disconnect >= 1);
  connection.resolve();
  await Promise.all([start, stop]);
  assert.ok(h.calls.disconnect >= 2);
  assert.equal(h.calls.publish, 0);
  assert.equal(h.calls.delete, 1);
});

test('stop durante publicação não aceita confirmação tardia ou retoma mídia', async () => {
  const publication = deferred();
  const h = setup({ publish: () => publication.promise });
  const start = h.session.start('PRESENTER');
  await flush();
  assert.equal(h.latest().phase, 'publishing');
  const stop = h.session.stop();
  publication.resolve();
  await Promise.all([start, stop]);
  assert.equal(h.calls.status, 0);
  assert.equal(h.calls.delete, 1);
  assert.ok(h.views.every((view) => view.phase !== 'live'));
  assert.ok(h.tracks.every((track) => track.readyState === 'ended'));
});

test('encerramentos e limpeza concorrentes compartilham exclusão pendente', async () => {
  const deletion = deferred();
  const h = setup({ delete: () => deletion.promise });
  await h.session.start('PRESENTER');
  const firstStop = h.session.stop();
  await flush();
  assert.equal(h.calls.delete, 1);
  const secondStop = h.session.stop();
  const cleanup = h.session.retryCleanup();
  await h.session.start('PRESENTER');
  assert.equal(h.calls.create, 1);
  assert.equal(h.calls.delete, 1);
  deletion.resolve();
  await Promise.all([firstStop, secondStop, cleanup]);
  assert.equal(h.calls.delete, 1);
  assert.equal(h.latest().cleanupPending, false);
});

test('stop de live mantém busy até confirmar exclusão e desconexão', async () => {
  const deletion = deferred();
  const h = setup({ delete: () => deletion.promise });
  await h.session.start('PRESENTER');
  assert.equal(h.latest().busy, false);
  const stop = h.session.stop();
  assert.equal(h.latest().phase, 'ended');
  assert.equal(h.latest().busy, true);
  await flush();
  assert.equal(h.calls.delete, 1);
  assert.equal(h.latest().busy, true);
  await h.session.start('PRESENTER');
  assert.equal(h.calls.create, 1);
  deletion.resolve();
  await stop;
  assert.equal(h.latest().busy, false);
});

test('start finalmente não limpa busy de stop concorrente com exclusão pendente', async () => {
  const creation = deferred(), deletion = deferred();
  const h = setup({ create: () => creation.promise, delete: () => deletion.promise });
  const start = h.session.start('PRESENTER');
  await flush();
  const stop = h.session.stop();
  creation.resolve(presenterAccess);
  await flush();
  assert.equal(h.latest().busy, true);
  assert.equal(h.calls.delete, 1);
  const closingViews = h.views.filter((view) => view.phase === 'ended');
  assert.ok(closingViews.every((view) => view.busy));
  deletion.resolve();
  await Promise.all([start, stop]);
  assert.equal(h.latest().busy, false);
});

test('retry de limpeza mantém busy inclusive ao retirar cleanupPending', async () => {
  const deletion = deferred();
  let attempts = 0;
  const h = setup({ delete: async () => {
    if (++attempts === 1) throw new Error('unavailable');
    await deletion.promise;
  } });
  await h.session.start('PRESENTER');
  await h.session.stop();
  assert.equal(h.latest().cleanupPending, true);
  assert.equal(h.latest().busy, false);
  const retry = h.session.retryCleanup();
  assert.equal(h.latest().busy, true);
  await flush();
  assert.equal(h.latest().busy, true);
  deletion.resolve();
  await retry;
  assert.equal(h.latest().busy, false);
  assert.equal(h.latest().cleanupPending, false);
  const cleared = h.views.findLastIndex((view) => view.cleanupPending);
  assert.ok(h.views.slice(cleared + 1, -1).every((view) => view.busy));
});

test('falha de exclusão bloqueia nova sessão até retry confirmado', async () => {
  let fail = true;
  const h = setup({ delete: async () => { if (fail) throw new Error('network'); } });
  await h.session.start('PRESENTER');
  await h.session.stop();
  assert.equal(h.latest().cleanupPending, true);
  assert.ok(h.tracks.every((t) => t.readyState === 'ended'));
  await h.session.start('PRESENTER');
  assert.equal(h.calls.create, 1);
  fail = false;
  await h.session.retryCleanup();
  assert.equal(h.latest().cleanupPending, false);
  await h.session.start('PRESENTER');
  assert.equal(h.calls.create, 2);
  await h.session.stop();
});

test('espectador espera confirmação e sai sem capturar ou excluir sala', async () => {
  const h = setup({ status: async () => ({ ...live, live: false }) });
  await h.session.start('VIEWER');
  assert.equal(h.latest().phase, 'waiting');
  assert.equal(h.calls.select, 0);
  assert.equal(h.calls.publish, 0);
  assert.equal(h.calls.viewer, 1);
  await h.session.stop();
  assert.equal(h.calls.delete, 0);
  assert.ok(h.calls.disconnect >= 1);
});

test('ended de fonte obrigatória encerra publicação e apaga sala', async () => {
  const h = setup();
  await h.session.start('PRESENTER');
  h.endedCallbacks[0]();
  await flush();
  assert.equal(h.latest().phase, 'ended');
  assert.equal(h.calls.delete, 1);
  assert.ok(h.tracks.every((t) => t.readyState === 'ended'));
});

test('callbacks de uma sessão encerrada não afetam a sessão seguinte', async () => {
  const h = setup();
  await h.session.start('PRESENTER');
  const oldEvents = h.events[0], oldEnded = h.endedCallbacks[0];
  await h.session.stop();
  await h.session.start('PRESENTER');
  oldEnded(); oldEvents.disconnected(); oldEvents.reconnecting(); oldEvents.reconnected();
  await flush();
  assert.equal(h.latest().phase, 'live');
  assert.equal(h.calls.delete, 1);
  assert.ok(h.tracks.every((t) => t.readyState === 'live'));
  await h.session.stop();
});

test('monitor sem live retira sucesso sem encerrar por silêncio de uma track viva', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let status = live;
  const h = setup({ status: async () => status });
  await h.session.start('PRESENTER');
  status = { ...live, live: false };
  t.mock.timers.tick(5_000);
  await flush();
  assert.equal(h.latest().phase, 'waiting');
  assert.equal(h.calls.delete, 0);
  assert.ok(h.tracks.every((track) => track.readyState === 'live'));
  status = live;
  t.mock.timers.tick(5_000);
  await flush();
  assert.equal(h.latest().phase, 'live');
  await h.session.stop();
});

test('resposta SFU anterior à perda de transporte não limpa estado reconectando', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const stale = deferred();
  let calls = 0;
  const h = setup({ status: async () => ++calls === 1 ? live : stale.promise });
  await h.session.start('PRESENTER');
  t.mock.timers.tick(5_000);
  await flush();
  h.events[0].reconnecting();
  stale.resolve(live);
  await flush();
  assert.equal(h.latest().phase, 'reconnecting');
  assert.ok(h.tracks.every((track) => track.readyState === 'live'));
  await h.session.stop();
});

test('reconexão confirmada recupera e cancela limite de 30s', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const h = setup();
  await h.session.start('PRESENTER');
  h.events[0].reconnecting();
  assert.equal(h.latest().phase, 'reconnecting');
  h.events[0].reconnected();
  await flush();
  assert.equal(h.latest().phase, 'live');
  t.mock.timers.tick(30_000);
  await flush();
  assert.equal(h.latest().phase, 'live');
  assert.equal(h.calls.delete, 0);
  await h.session.stop();
});

test('reconexão sem recuperação por 30s libera captura e sala', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const h = setup();
  await h.session.start('PRESENTER');
  h.events[0].reconnecting();
  t.mock.timers.tick(30_000);
  await flush();
  assert.equal(h.latest().phase, 'error');
  assert.equal(h.calls.delete, 1);
  assert.ok(h.tracks.every((track) => track.readyState === 'ended'));
});
