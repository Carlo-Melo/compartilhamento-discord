import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const moduleSource = async (file) => readFile(new URL(`../src/app/features/dev-capture/${file}.ts`, import.meta.url), 'utf8');
const dataModule = (source) => `data:text/javascript;base64,${Buffer.from(ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText).toString('base64')}`;
const displayModule = dataModule(await moduleSource('display-capture'));
const sessionModule = dataModule((await moduleSource('capture-session')).replace("'./display-capture'", JSON.stringify(displayModule)));
const { CaptureApiFailure } = await import(sessionModule);
const { CaptureCredential } = await import(dataModule((await moduleSource('capture-credential')).replace("'./capture-session'", JSON.stringify(sessionModule))));
const { CaptureProofApi } = await import(dataModule((await moduleSource('capture-api')).replace("'./capture-session'", JSON.stringify(sessionModule))));
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
const fakeKey = 'test-credential-'.repeat(4);
const requestId = '12345678-1234-4321-abcd-123456789abc';

function credential(role = 'PRESENTER', probe = async () => ({ role })) {
  const views = [], keys = [];
  const api = { validate: probe, create() { throw new Error('must not allocate'); } };
  const value = new CaptureCredential(role, key => { keys.push(key); return api; }, view => views.push(view), 5);
  return { value, api, views, keys, latest: () => views.at(-1) };
}

test('valida ambos os papéis no backend antes de liberar a tentativa e não cria sala', async () => {
  for (const role of ['PRESENTER', 'VIEWER']) {
    const h = credential(role);
    h.value.change(fakeKey, true);
    assert.equal(h.latest().status, 'checking');
    assert.equal(h.value.ready(), false);
    await flush();
    assert.equal(h.latest().status, 'valid');
    assert.equal(h.value.take(), h.api);
    assert.equal(h.latest().status, 'accepted');
    assert.equal(h.value.ready(), false);
    assert.doesNotMatch(JSON.stringify(h.views), new RegExp(fakeKey));
  }
});

test('chave válida do outro papel recebe orientação e não libera início', async () => {
  const h = credential('PRESENTER', async () => ({ role: 'VIEWER' }));
  h.value.change(fakeKey, true);
  await flush();
  assert.equal(h.latest().status, 'wrong-role');
  assert.match(h.latest().message, /espectador/);
  assert.equal(h.value.take(), undefined);
});

test('campo vazio ou linha de variável inteira não consulta backend', () => {
  const h = credential();
  h.value.change('');
  assert.equal(h.latest().status, 'empty');
  h.value.change(`CAPTURE_PROOF_PRESENTER_KEY=${fakeKey}`);
  assert.equal(h.latest().status, 'invalid');
  assert.equal(h.keys.length, 0);
});

test('resposta antiga não valida uma chave nova ou um campo limpo', async () => {
  const old = deferred();
  const h = credential('PRESENTER', () => old.promise);
  h.value.change(fakeKey, true);
  h.value.change('');
  old.resolve({ role: 'PRESENTER' });
  await flush();
  assert.equal(h.latest().status, 'empty');
  assert.equal(h.value.ready(), false);
});

test('editar chave válida retira a autorização imediatamente; debounce cancelado não consulta', async () => {
  const h = credential();
  h.value.change(fakeKey, true);
  await flush();
  assert.equal(h.value.ready(), true);
  h.value.change('another-valid-key-'.repeat(3));
  assert.equal(h.value.ready(), false);
  h.value.dispose();
  await new Promise(resolve => setTimeout(resolve, 15));
  assert.equal(h.keys.length, 1);
});

test('colagem valida automaticamente após pausa, sem iniciar captura', async () => {
  const h = credential();
  h.value.change(fakeKey);
  assert.equal(h.keys.length, 0);
  await new Promise(resolve => setTimeout(resolve, 20));
  assert.equal(h.latest().status, 'valid');
  assert.equal(h.keys.length, 1);
  h.value.dispose();
});

test('403 é chave recusada; timeout é indisponibilidade e nenhum erro bruto é refletido', async () => {
  for (const [error, status] of [[new CaptureApiFailure(403, 'CAPTURE_PROOF_REJECTED', requestId), 'invalid'],
    [new CaptureApiFailure(0, 'REQUEST_TIMEOUT'), 'unavailable'], [new Error(fakeKey), 'unavailable']]) {
    const h = credential('VIEWER', async () => { throw error; });
    h.value.change(fakeKey, true);
    await flush();
    assert.equal(h.latest().status, status);
    assert.equal(h.value.ready(), false);
    assert.doesNotMatch(JSON.stringify(h.views), new RegExp(fakeKey));
  }
});

test('validação exige papel reconhecido; resposta estranha não libera a tentativa', async () => {
  const h = credential('VIEWER', async () => ({ role: 'ADMIN' }));
  h.value.change(fakeKey, true);
  await flush();
  assert.equal(h.latest().status, 'unavailable');
  assert.equal(h.value.ready(), false);
});

test('API de validação usa GET limitado, sem cookies, corpo ou credencial na URL', async (t) => {
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    assert.equal(path, '/api/dev/capture/session/access');
    assert.equal(options.method, 'GET');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.headers['X-Capture-Key'], fakeKey);
    assert.equal(options.body, undefined);
    return new Response(JSON.stringify({ role: 'VIEWER' }), { status: 200 });
  });
  assert.deepEqual(await new CaptureProofApi(fakeKey).validate(), { role: 'VIEWER' });
});

test('entrada, repetição e saída usam o mesmo nonce experimental por aba sem sala/papel no corpo', async (t) => {
  const nonce = '12345678-1234-4123-8123-123456789abc';
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options });
    return options.method === 'DELETE' ? new Response(null, { status: 204 }) : new Response('{}', { status: 200 });
  });
  const api = new CaptureProofApi(fakeKey, nonce);
  await api.viewer(); await api.viewer(); await api.leave();
  assert.equal(calls.length, 3);
  for (const { path, options } of calls) {
    assert.equal(path, '/api/dev/capture/session/viewer');
    assert.equal(options.headers['X-Capture-Viewer'], nonce);
    assert.equal(options.headers['X-Capture-Key'], fakeKey);
    assert.equal(options.credentials, 'omit');
    assert.equal(options.body, undefined);
    assert.equal(path.includes(nonce), false);
  }
});

test('validação da chave não aloca nonce; primeira entrada gera uma vez e reutiliza na saída', async (t) => {
  let generated = 0;
  const nonce = '12345678-1234-4123-8123-123456789abc';
  t.mock.method(globalThis.crypto, 'randomUUID', () => { generated++; return nonce; });
  const headers = [];
  t.mock.method(globalThis, 'fetch', async (_path, options) => {
    headers.push(options.headers);
    return options.method === 'DELETE' ? new Response(null, { status: 204 }) : new Response('{}', { status: 200 });
  });
  const api = new CaptureProofApi(fakeKey);
  await api.validate(); assert.equal(generated, 0);
  await api.viewer(); await api.viewer(); await api.leave();
  assert.equal(generated, 1);
  assert.equal(headers[0]['X-Capture-Viewer'], undefined);
  assert.ok(headers.slice(1).every(value => value['X-Capture-Viewer'] === nonce));
});

test('erro da API conserva somente status, código permitido e UUID de correlação', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    code: fakeKey, message: fakeKey, requestId,
  }), { status: 403 }));
  const api = new CaptureProofApi(fakeKey);
  await assert.rejects(api.validate(), error => {
    assert.equal(error.status, 403);
    assert.equal(error.code, 'HTTP_ERROR');
    assert.equal(error.requestId, requestId);
    assert.equal(JSON.stringify(error).includes(fakeKey), false);
    assert.equal(error.message.includes(fakeKey), false);
    return true;
  });
  assert.equal(new CaptureApiFailure(403, 'FORBIDDEN', fakeKey).requestId, undefined);
});

test('falha de rede e resposta não JSON têm diagnóstico próprio sem corpo bruto', async (t) => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => { throw new TypeError(fakeKey); });
  await assert.rejects(new CaptureProofApi(fakeKey).validate(), error => error.code === 'NETWORK_ERROR' && !error.message.includes(fakeKey));
  fetch.mock.mockImplementation(async () => new Response(fakeKey, { status: 200 }));
  await assert.rejects(new CaptureProofApi(fakeKey).validate(), error => error.code === 'INVALID_RESPONSE' && !error.message.includes(fakeKey));
});
