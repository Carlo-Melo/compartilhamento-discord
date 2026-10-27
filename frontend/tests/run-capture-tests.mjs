import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const result = spawnSync(process.execPath, ['--test',
  fileURLToPath(new URL('./display-capture.test.mjs', import.meta.url)),
  fileURLToPath(new URL('./capture-session.test.mjs', import.meta.url)),
  fileURLToPath(new URL('./capture-feedback.test.mjs', import.meta.url)),
  fileURLToPath(new URL('./ice-diagnostics.test.mjs', import.meta.url)),
], {
  stdio: 'inherit',
});
process.exitCode = result.status ?? 1;
