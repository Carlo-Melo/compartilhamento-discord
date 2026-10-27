'use strict';
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const http = require('node:http');
const { mkdtemp, rm } = require('node:fs/promises');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const test = require('node:test');
const configPath = require.resolve('./proxy.capture.cjs');

function load(origin) {
  process.env.CAPTURE_PROOF_ORIGIN = origin;
  delete require.cache[configPath];
  return require(configPath);
}

test('T-03 proxy only forwards the bounded capture API and LiveKit signalling endpoints', () => {
  const config = load('https://192.168.1.50:4443');
  const matches = (url) => Object.keys(config).some((pattern) => new RegExp(pattern).test(url));
  for (const path of ['/rtc', '/rtc?access_token=redacted', '/rtc/validate', '/rtc/v1', '/rtc/v1/validate',
    '/api/dev/capture/session', '/api/dev/capture/session/viewer', '/api/dev/capture/session/access']) assert.equal(matches(path), true, path);
  for (const path of ['/twirp/livekit.RoomService/CreateRoom', '/rtc/twirp', '/rtc/v1/twirp',
    '/api/dev/livekit/rooms', '/api/dev/capture/session/tokens', '/api/health/live', '/health/ready']) {
    assert.equal(matches(path), false, path);
  }
});

test('T-03 proxy rejects foreign Host, Origin and missing mutation Origin', () => {
  const rule = Object.values(load('https://192.168.1.50:4443'))[0];
  const req = { method: 'POST', headers: { host: '192.168.1.50:4443', origin: 'https://192.168.1.50:4443' } };
  assert.equal(rule.bypass(req), undefined);
  assert.equal(rule.bypass({ ...req, headers: { ...req.headers, host: 'evil.example' } }), false);
  assert.equal(rule.bypass({ ...req, headers: { ...req.headers, origin: 'https://evil.example' } }), false);
  assert.equal(rule.bypass({ ...req, headers: { host: req.headers.host } }), false);
  assert.equal(rule.bypass({ method: 'GET', headers: { host: req.headers.host } }), undefined);
});

test('T-03 proxy sanitizes failed signalling requests before Vite logging', () => {
  const rule = Object.values(load('https://192.168.1.50:4443'))[1];
  const proxy = new EventEmitter();
  rule.configure(proxy);
  const request = { url: '/rtc/v1/validate?access_token=fake-sensitive-value' };
  const error = new Error('fake-sensitive-value');
  const response = { req: request };
  proxy.on('error', (err, req, res) => {
    assert.equal(JSON.stringify([err.message, err.stack, req.url, res.req.url]).includes('fake-sensitive-value'), false);
  });
  proxy.emit('error', error, request, response);
});

test('T-03 HTTP/2 authority is checked strictly and normalized for the HTTP/1 backend', () => {
  const rule = Object.values(load('https://192.168.1.50:4443'))[0];
  const request = { get method() { return this.headers[':method']; }, get url() { return this.headers[':path']; },
    headers: { ':authority': '192.168.1.50:4443',
    ':method': 'GET', ':path': '/api/dev/capture/session/access', ':scheme': 'https' } };
  assert.equal(rule.bypass(request), undefined);
  assert.equal(request.headers.host, '192.168.1.50:4443');
  assert.equal(Object.keys(request.headers).some(name => name.startsWith(':')), false);
  assert.equal(request.url, '/api/dev/capture/session/access');
  assert.equal(request.method, 'GET');
  for (const headers of [{ ':authority': 'evil.example' },
    { host: 'evil.example', ':authority': '192.168.1.50:4443' },
    { host: '192.168.1.50:4443', ':authority': 'evil.example' },
    { ':authority': '192.168.1.50:4443', origin: 'https://evil.example' }]) {
    assert.equal(rule.bypass({ method: 'GET', headers }), false);
  }
  assert.equal(rule.bypass({ method: 'POST', headers: { ':authority': '192.168.1.50:4443' } }), false);
});

test('T-03 proxy refuses loopback, public, non-HTTPS and non-canonical origins', () => {
  for (const origin of ['http://192.168.1.50:4443', 'https://127.0.0.1:4443', 'https://8.8.8.8:4443',
    'https://192.168.1.50:4443/', 'https://192.168.1.50:4443/path', 'https://192.168.1.50:4443?token=x',
    'https://user:password@192.168.1.50:4443', 'https://192.168.1.50:4444']) {
    assert.throws(() => load(origin), undefined, origin);
  }
});

function httpRequest(port, path, headers = {}, upgrade = false, method = 'GET') {
  return new Promise((resolve, reject) => {
    const request = http.request({ host: '127.0.0.1', port, path, agent: false, method,
      headers: { host: '192.168.1.50:4443', ...headers }, timeout: 5_000 }, (response) => {
      let body = '';
      response.on('data', (chunk) => { body += chunk; });
      response.on('end', () => resolve({ status: response.statusCode, body }));
    });
    request.on('upgrade', (_response, socket) => { socket.destroy(); resolve({ status: 101 }); });
    request.on('timeout', () => request.destroy(new Error('Loopback test timed out')));
    request.on('error', (error) => upgrade ? resolve({ status: 0 }) : reject(error));
    request.end();
  });
}

async function loopbackFixture(run) {
  const { createServer } = await import('vite');
  const upstreamPaths = [];
  const upstreamRequests = [];
  const upstream = http.createServer((request, response) => {
    upstreamPaths.push(request.url);
    upstreamRequests.push({ method: request.method, headers: request.headers });
    response.writeHead(200, { 'content-type': 'text/plain' }).end('fake-upstream');
  });
  await new Promise((resolve) => upstream.listen(0, '127.0.0.1', resolve));
  const upstreamPort = upstream.address().port;
  const config = load('https://192.168.1.50:4443');
  for (const rule of Object.values(config)) rule.target = `http://127.0.0.1:${upstreamPort}`;
  const logs = [];
  const root = await mkdtemp(join(tmpdir(), 'capture-proxy-test-'));
  const dev = await createServer({ configFile: false, root, logLevel: 'silent',
    customLogger: { info() {}, warn() {}, warnOnce() {}, clearScreen() {}, hasErrorLogged() { return false; },
      hasWarned: false, error(message) { logs.push(message); } },
    server: { host: '127.0.0.1', port: 0, strictPort: true, proxy: config } });
  try {
    await dev.listen();
    await run({ port: dev.httpServer.address().port, upstream, upstreamPaths, upstreamRequests, logs });
  } finally {
    await dev.close();
    await new Promise((resolve) => upstream.close(resolve));
    await rm(root, { recursive: true, force: true });
  }
}

test('T-03 real Vite loopback proxy forwards signalling but never administrative paths', async () => {
  await loopbackFixture(async ({ port, upstreamPaths }) => {
    assert.equal((await httpRequest(port, '/rtc/v1/validate?access_token=fake-sensitive-value')).status, 200);
    assert.equal((await httpRequest(port, '/api/dev/capture/session')).status, 200);
    assert.equal((await httpRequest(port, '/api/dev/capture/session/access')).status, 200);
    assert.equal(upstreamPaths.length, 3);
    for (const path of ['/twirp/livekit.RoomService/ListRooms', '/rtc/twirp', '/rtc/%2e%2e/twirp',
      '/api/dev/livekit/rooms', '/health/ready']) await httpRequest(port, path);
    assert.equal(upstreamPaths.length, 3);
  });
});

test('T-03 real Vite loopback proxy rejects foreign Host and Origin before upstream', async () => {
  await loopbackFixture(async ({ port, upstreamPaths }) => {
    // Plain HTTP Vite may reject Host before the proxy; HTTPS uses the explicit
    // proxy check covered above. Neither outcome may reach the upstream.
    assert.ok([403, 404].includes((await httpRequest(port, '/rtc/v1', { host: 'other.example' })).status));
    assert.equal((await httpRequest(port, '/rtc/v1', { origin: 'https://other.example' })).status, 404);
    assert.equal(upstreamPaths.length, 0);
  });
});

test('T-04 viewer leave keeps exact Origin and volatile nonce when forwarded without exposing administration', async () => {
  await loopbackFixture(async ({ port, upstreamRequests }) => {
    const nonce = '12345678-1234-4123-8123-123456789abc';
    assert.equal((await httpRequest(port, '/api/dev/capture/session/viewer', {
      origin: 'https://192.168.1.50:4443', 'x-capture-viewer': nonce,
      'x-capture-key': 'fictitious-test-key', 'x-capture-proof': 'restricted-development',
    }, false, 'DELETE')).status, 200);
    assert.equal(upstreamRequests.length, 1);
    assert.equal(upstreamRequests[0].method, 'DELETE');
    assert.equal(upstreamRequests[0].headers.origin, 'https://192.168.1.50:4443');
    assert.equal(upstreamRequests[0].headers['x-capture-viewer'], nonce);
    assert.equal((await httpRequest(port, '/api/dev/capture/session/viewer', {}, false, 'DELETE')).status, 404);
    assert.equal(upstreamRequests.length, 1);
  });
});

test('T-03 real Vite loopback failure logger never receives the signalling credential', async () => {
  await loopbackFixture(async ({ port, upstream, logs }) => {
    await new Promise((resolve) => upstream.close(resolve));
    const queryPath = '/rtc/v1/validate?access_token=fake-sensitive-value';
    assert.equal((await httpRequest(port, queryPath)).status, 502);
    await httpRequest(port, '/rtc/v1?access_token=fake-sensitive-value', {
      connection: 'Upgrade', upgrade: 'websocket', origin: 'https://192.168.1.50:4443',
      'sec-websocket-key': 'dGhlIHNhbXBsZSBub25jZQ==', 'sec-websocket-version': '13',
    }, true);
    assert.ok(logs.length >= 2, 'HTTP and WebSocket failure both reached the real Vite error logger');
    assert.equal(logs.some((entry) => entry.includes('fake-sensitive-value') || entry.includes('access_token')), false);
  });
});
