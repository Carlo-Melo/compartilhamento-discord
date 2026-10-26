import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

// Compile the same production capture module with the pinned TypeScript version.
// No fake browser capture is claimed as Windows system-audio homologation.
const source = await readFile(new URL('../src/app/features/dev-capture/display-capture.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
});
const { DisplayCapture, CaptureFailure } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);

class FakeTrack extends EventTarget {
  readyState = 'live';
  stopped = 0;
  listeners = 0;
  constructor(kind) { super(); this.kind = kind; }
  addEventListener(...args) { this.listeners += 1; super.addEventListener(...args); }
  removeEventListener(...args) { this.listeners -= 1; super.removeEventListener(...args); }
  stop() { this.readyState = 'ended'; this.stopped += 1; }
  end() { this.readyState = 'ended'; this.dispatchEvent(new Event('ended')); }
}
class FakeStream {
  constructor(tracks = [new FakeTrack('video'), new FakeTrack('audio')]) { this.tracks = tracks; }
  getTracks() { return [...this.tracks]; }
  getVideoTracks() { return this.tracks.filter((t) => t.kind === 'video'); }
  getAudioTracks() { return this.tracks.filter((t) => t.kind === 'audio'); }
  removeTrack(track) { this.tracks = this.tracks.filter((t) => t !== track); }
}
const captureFor = (stream) => new DisplayCapture({ getDisplayMedia: async () => stream }, true);
const deferred = () => {
  let resolve;
  const promise = new Promise((r) => { resolve = r; });
  return { promise, resolve };
};

test('select solicita somente display, áudio e hints, antes de qualquer espera', async () => {
  const stream = new FakeStream();
  let options;
  const capture = new DisplayCapture({ getDisplayMedia: (value) => { options = value; return Promise.resolve(stream); } }, true);
  const selection = capture.select('720p', () => {});
  assert.equal(options.audio, true);
  assert.deepEqual(options.video, { width: { ideal: 1280 }, height: { ideal: 720 },
    frameRate: { ideal: 30, max: 30 }, displaySurface: 'monitor' });
  assert.equal(options.systemAudio, 'include');
  assert.equal(options.windowAudio, 'system');
  assert.equal(options.selfBrowserSurface, 'exclude');
  assert.equal(options.monitorTypeSurfaces, 'include');
  assert.equal(options.preferCurrentTab, false);
  assert.doesNotMatch(JSON.stringify(options), /"(?:min|exact)"/);
  assert.equal(await selection, stream);
  capture.stop();
});

test('1080p usa 1920x1080 a 30 fps e não exige nível de áudio', async () => {
  const stream = new FakeStream();
  // A live track has no samples or volume level in this contract. Silence passes.
  let options;
  const capture = new DisplayCapture({ getDisplayMedia: async (value) => { options = value; return stream; } }, true);
  assert.equal(await capture.select('1080p', () => {}), stream);
  assert.deepEqual(options.video.width, { ideal: 1920 });
  assert.deepEqual(options.video.height, { ideal: 1080 });
  capture.stop();
});

for (const [label, tracks, code] of [
  ['sem vídeo', [new FakeTrack('audio')], 'no-video'],
  ['sem áudio', [new FakeTrack('video')], 'no-audio'],
  ['vídeo encerrado', [Object.assign(new FakeTrack('video'), { readyState: 'ended' }), new FakeTrack('audio')], 'ended'],
  ['áudio encerrado', [new FakeTrack('video'), Object.assign(new FakeTrack('audio'), { readyState: 'ended' })], 'ended'],
]) {
  test(`${label} recusa captura e libera todas as tracks`, async () => {
    const capture = captureFor(new FakeStream(tracks));
    await assert.rejects(capture.select('720p', () => {}), (error) => error instanceof CaptureFailure && error.code === code);
    assert.ok(tracks.every((track) => track.stopped === 1 && track.listeners === 0));
  });
}

for (const name of ['NotAllowedError', 'AbortError', 'NotReadableError', 'InvalidStateError', 'NotFoundError', 'Error']) {
  test(`${name} é convertido em erro acionável sem expor mensagem de origem`, async () => {
    const capture = new DisplayCapture({ getDisplayMedia: async () => { throw { name, message: 'sensitive-provider-detail' }; } }, true);
    await assert.rejects(capture.select('720p', () => {}), (error) => {
      assert.equal(error.code, name === 'NotAllowedError' || name === 'AbortError' ? 'cancelled' : 'capture-failed');
      assert.doesNotMatch(error.message, /sensitive-provider-detail/);
      assert.ok(error.message.length > 20);
      return true;
    });
  });
}

test('HTTPS inseguro ou API ausente não abre seletor', async () => {
  let called = false;
  const unsafe = new DisplayCapture({ getDisplayMedia: async () => { called = true; } }, false);
  await assert.rejects(unsafe.select('720p', () => {}), { code: 'unsupported' });
  await assert.rejects(new DisplayCapture(undefined, true).select('720p', () => {}), { code: 'unsupported' });
  assert.equal(called, false);
});

for (const kind of ['video', 'audio']) {
  test(`ended de ${kind} libera o par e avisa uma vez`, async () => {
    const stream = new FakeStream();
    const capture = captureFor(stream);
    let ended = 0;
    await capture.select('720p', () => { ended += 1; });
    const track = stream.getTracks().find((t) => t.kind === kind);
    track.end();
    track.end();
    capture.stop();
    assert.equal(ended, 1);
    assert.ok(stream.getTracks().every((t) => t.stopped === 1 && t.listeners === 0));
  });
}

test('stop idempotente remove handlers antes de liberar tracks', async () => {
  const stream = new FakeStream();
  const capture = captureFor(stream);
  let ended = 0;
  await capture.select('720p', () => { ended += 1; });
  capture.stop(); capture.stop();
  for (const track of stream.getTracks()) track.end();
  assert.equal(ended, 0);
  assert.ok(stream.getTracks().every((t) => t.stopped === 1 && t.listeners === 0));
});

test('stop durante seletor libera um resultado tardio e não o reativa', async () => {
  const pending = deferred();
  const stream = new FakeStream();
  const capture = new DisplayCapture({ getDisplayMedia: () => pending.promise }, true);
  const selection = capture.select('720p', () => assert.fail('must not notify'));
  capture.stop();
  pending.resolve(stream);
  await assert.rejects(selection, { code: 'cancelled' });
  assert.ok(stream.getTracks().every((t) => t.stopped === 1 && t.listeners === 0));
});

test('nova seleção para a anterior antes de chamar getDisplayMedia', async () => {
  const first = new FakeStream();
  const second = new FakeStream();
  let calls = 0;
  const capture = new DisplayCapture({ getDisplayMedia: async () => {
    calls += 1;
    if (calls === 1) return first;
    assert.ok(first.getTracks().every((t) => t.stopped === 1 && t.listeners === 0));
    return second;
  } }, true);
  await capture.select('720p', () => {});
  await capture.select('720p', () => {});
  assert.ok(second.getTracks().every((t) => t.readyState === 'live'));
  capture.stop();
});

test('seleções concorrentes não permitem resultado antigo substituir captura nova', async () => {
  const pending = deferred();
  const oldStream = new FakeStream();
  const currentStream = new FakeStream();
  let calls = 0;
  const capture = new DisplayCapture({ getDisplayMedia: () => ++calls === 1 ? pending.promise : Promise.resolve(currentStream) }, true);
  const oldSelection = capture.select('720p', () => assert.fail('old ended callback'));
  assert.equal(await capture.select('720p', () => {}), currentStream);
  pending.resolve(oldStream);
  await assert.rejects(oldSelection, { code: 'cancelled' });
  assert.ok(oldStream.getTracks().every((t) => t.stopped === 1 && t.listeners === 0));
  assert.ok(currentStream.getTracks().every((t) => t.readyState === 'live' && t.listeners === 1));
  capture.stop();
});

test('tracks extras são encerradas e removidas sem serem publicadas', async () => {
  const tracks = [new FakeTrack('video'), new FakeTrack('audio'), new FakeTrack('audio')];
  const stream = new FakeStream(tracks);
  const capture = captureFor(stream);
  await capture.select('720p', () => {});
  assert.equal(tracks[2].stopped, 1);
  assert.equal(stream.getTracks().length, 2);
  capture.stop();
  assert.ok(tracks.every((t) => t.stopped === 1));
});
