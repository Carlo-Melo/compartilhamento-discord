import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/app/features/dev-capture/ice-diagnostics.ts', import.meta.url), 'utf8');
const output = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText;
const { selectedIcePair, proofConnectOptions, icePairMessage } = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);
const report = (items) => new Map(items.map(item => [item.id, item]));
const fixture = (overrides = {}) => [
  { id: 'transport', type: 'transport', selectedCandidatePairId: 'selected' },
  { id: 'unselected', type: 'candidate-pair', state: 'succeeded', nominated: true, localCandidateId: 'host', remoteCandidateId: 'remote' },
  { id: 'selected', type: 'candidate-pair', state: 'succeeded', localCandidateId: 'local', remoteCandidateId: 'remote', ...overrides },
  { id: 'host', type: 'local-candidate', candidateType: 'host', protocol: 'udp' },
  { id: 'local', type: 'local-candidate', candidateType: 'relay', protocol: 'udp', relayProtocol: 'tls', address: 'sensitive-ip', url: 'turns:sensitive-url', username: 'sensitive-user' },
  { id: 'remote', type: 'remote-candidate', candidateType: 'host', protocol: 'udp', address: 'sensitive-remote', port: 12345 },
];

test('ICE padrão e relay-only preservam os servidores oferecidos pela SFU, sem override vazio', () => {
  assert.deepEqual(proofConnectOptions('all'), { autoSubscribe: false });
  assert.deepEqual(proofConnectOptions('relay'), { autoSubscribe: false, rtcConfig: { iceTransportPolicy: 'relay' } });
  for (const policy of ['all', 'relay']) assert.doesNotMatch(JSON.stringify(proofConnectOptions(policy)), /iceServers/);
});
test('diagnóstico usa o par selecionado efetivo e distingue protocolo ICE de cliente para TURN', () => {
  const selected = selectedIcePair(report(fixture()));
  assert.deepEqual(selected, { localType: 'relay', remoteType: 'host', localProtocol: 'udp', remoteProtocol: 'udp', relayProtocol: 'tls', relay: true });
  const text = icePairMessage('recepção', selected);
  assert.match(text, /relay\/udp/);
  assert.match(text, /cliente–TURN tls/);
  assert.doesNotMatch(JSON.stringify(selected) + text, /sensitive|12345|url|username|address/);
});
test('sinalização, candidato apenas nominated ou par sem sucesso não comprovam seleção', () => {
  assert.equal(selectedIcePair(undefined), undefined);
  assert.equal(selectedIcePair(report(fixture({ state: 'in-progress' }))), undefined);
  assert.equal(selectedIcePair(report(fixture().filter(s => s.type !== 'transport'))), undefined);
});
test('fallback selected de navegador é aceito, mas campos arbitrários são descartados', () => {
  const values = fixture({ selected: true }).filter(s => s.type !== 'transport');
  values.find(s => s.id === 'local').protocol = 'sensitive-protocol';
  const selected = selectedIcePair(report(values));
  assert.equal(selected.localProtocol, 'indisponível');
  assert.doesNotMatch(JSON.stringify(selected), /sensitive/);
});
