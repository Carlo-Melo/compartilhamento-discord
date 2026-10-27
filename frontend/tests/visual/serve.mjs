// Isolated, loopback-only static preview. Never forwards requests to the backend.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../dist/design-qa/browser');
const port = 4217;
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml' };
createServer(async (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Permissions-Policy', 'display-capture=(), camera=(), microphone=()');
  try {
    if (request.method !== 'GET' || request.headers.host !== `127.0.0.1:${port}`) {
      response.writeHead(403).end(); return;
    }
    const pathname = decodeURIComponent(new URL(request.url, `http://127.0.0.1:${port}`).pathname);
    if (pathname.startsWith('/api/')) { response.writeHead(404).end(); return; }
    const target = resolve(root, '.' + pathname);
    if (target !== root && !target.startsWith(root + sep)) { response.writeHead(403).end(); return; }
    let body;
    let extension = extname(target);
    try { body = await readFile(target); }
    catch {
      if (extension) { response.writeHead(404).end(); return; }
      body = await readFile(resolve(root, 'index.html'));
      extension = '.html';
    }
    response.setHeader('Content-Type', `${types[extension] ?? 'application/octet-stream'}; charset=utf-8`);
    response.writeHead(200).end(body);
  } catch { response.writeHead(400).end(); }
}).listen(port, '127.0.0.1', () => console.log(`Prévia fictícia: http://127.0.0.1:${port}/health`));
