// Dedicated opt-in TLS harness. Uses the SAME restricted proxy as T-03/T-04.
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { createServer } from 'vite';
const require = createRequire(import.meta.url);
const args = process.argv.slice(2);
if (args.length !== 3 || args[0] !== '--opt-in-t04') throw new Error('Uso: node tests/t04/serve.mjs --opt-in-t04 CERTIFICADO_PEM CHAVE_PEM; importe .env.capture antes.');
if (process.env.DEBUG || process.env.NODE_DEBUG) throw new Error('Desabilite DEBUG e NODE_DEBUG antes da prova; podem revelar URLs com credenciais.');
// Loading the proxy validates HTTPS/private IPv4/canonical origin/port 4443.
const proxy = require('../../proxy.capture.cjs');
const origin = new URL(process.env.CAPTURE_PROOF_ORIGIN);
const root = dirname(fileURLToPath(import.meta.url));
const frontend = resolve(root, '../..');
const cert = await readFile(resolve(args[1]));
const key = await readFile(resolve(args[2]));
const logger = { info() {}, warn() {}, warnOnce() {}, clearScreen() {}, hasErrorLogged() { return false; }, hasWarned: false,
  error() { process.stderr.write('Auxiliar T-04: operação do servidor falhou; confira serviços e configuração, sem logs verbosos.\n'); } };
const server = await createServer({ configFile: false, root, envDir: false, envPrefix: [], customLogger: logger,
  plugins: [{ name: 't04-host-origin', configureServer(dev) { dev.middlewares.use((req, res, next) => {
    if (req.headers.host !== origin.host || (req.headers.origin && req.headers.origin !== origin.origin)) { res.writeHead(403).end(); return; }
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Permissions-Policy', 'display-capture=(), camera=(), microphone=()');
    res.setHeader('Content-Security-Policy', `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' ${origin.origin.replace('https:', 'wss:')}; media-src 'self' blob:; img-src 'self' data:; worker-src 'self' blob:; frame-ancestors 'none'; base-uri 'none'`);
    next();
  }); } }],
  server: { host: origin.hostname, port: 4443, strictPort: true, https: { cert, key },
    allowedHosts: [origin.hostname], hmr: false, watch: null,
    fs: { allow: [frontend], deny: ['.env', '.env.*', '*.{pem,key,crt}', '**/.git/**'] }, proxy } });
try { await server.listen(); process.stdout.write('Auxiliar T-04 pronto na origem HTTPS privada configurada, porta 4443. Abra / e clique para executar.\n'); }
catch { await server.close(); process.stderr.write('Auxiliar T-04 não iniciou. Confira certificado, IPv4 privado e porta 4443 livre.\n'); process.exitCode = 1; }
