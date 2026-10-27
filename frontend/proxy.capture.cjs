'use strict';

// Isolated T-03 dev-server proxy. The SFU administrative API is never proxied.
const origin = process.env.CAPTURE_PROOF_ORIGIN;
let expected;
try { expected = new URL(origin); } catch { throw new Error('Importe .env.capture antes de iniciar o proxy T-03.'); }
const octets = expected.hostname.split('.').map(Number);
const privateIpv4 = octets.length === 4 && octets.every((n) => Number.isInteger(n) && n >= 0 && n <= 255)
  && (octets[0] === 10 || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
    || (octets[0] === 192 && octets[1] === 168));
if (!privateIpv4 || expected.protocol !== 'https:' || expected.port !== '4443'
  || expected.origin !== origin || expected.username || expected.password || expected.search || expected.hash) {
  throw new Error('Origem T-03 deve ser HTTPS em IPv4 privado explicito, porta 4443.');
}

function restrictedRequest(req, _res, options) {
  // The dev server may accept arbitrary Hosts under HTTPS. Enforce the exact
  // address for every forwarded request; never trust forwarded headers.
  // Browsers negotiate HTTP/2 on this HTTPS dev server. HTTP/2 sends :authority,
  // not Host; rejecting it produced a silent 404 before the backend saw the request.
  // Validate both when present, then normalize only for the HTTP/1 upstream.
  const host = req.headers.host;
  const authority = req.headers[':authority'];
  if ((!host && !authority) || (host && host !== expected.host)
      || (authority && authority !== expected.host)) return false;
  const mutation = req.method !== 'GET' && req.method !== 'HEAD';
  if (req.headers.origin ? req.headers.origin !== origin : mutation) return false;
  const requestUrl = req.url ?? req.headers[':path'];
  const requestMethod = req.method ?? req.headers[':method'];
  req.headers.host = expected.host;
  if (authority) {
    // Http2ServerRequest.url/method are getters backed by these pseudoheaders.
    // Preserve their values before removing headers forbidden by the HTTP/1 client.
    Object.defineProperty(req, 'url', { value: requestUrl, writable: true, configurable: true });
    Object.defineProperty(req, 'method', { value: requestMethod, writable: true, configurable: true });
    for (const name of Object.keys(req.headers)) if (name.startsWith(':')) delete req.headers[name];
  }
  return undefined;
}

function sanitizeErrors(proxy) {
  // Angular's pinned Vite adds its own error listener after configure(). It
  // logs res.req.url, including the SDK JWT query, unless sanitized first.
  proxy.on('error', (error, req, response) => {
    if (req) req.url = '/redacted';
    if (response?.req) response.req.url = '/redacted';
    error.message = 'Proxy T-03 indisponivel.';
    error.stack = 'Proxy T-03 indisponivel.';
  });
  proxy.on('proxyReqWs', (_proxyReq, _req, socket) => {
    socket.prependListener('error', (error) => {
      error.message = 'Conexao de sinalizacao T-03 interrompida.';
      error.stack = 'Conexao de sinalizacao T-03 interrompida.';
    });
  });
}

module.exports = {
  '^/api/dev/capture/session(?:/viewer|/access)?(?:\\?.*)?$': {
    target: 'http://127.0.0.1:8080',
    changeOrigin: false,
    bypass: restrictedRequest,
    configure: sanitizeErrors,
  },
  '^/rtc(?:/v1)?(?:/validate)?(?:\\?.*)?$': {
    target: 'http://127.0.0.1:7890',
    changeOrigin: false,
    ws: true,
    bypass: restrictedRequest,
    configure: sanitizeErrors,
  },
};
