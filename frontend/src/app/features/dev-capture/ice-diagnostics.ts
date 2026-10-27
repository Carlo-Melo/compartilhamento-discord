/** Experimental diagnostics: copy only allowlisted fields, never the RTCStatsReport. */
export type ProofIcePolicy = 'all' | 'relay';
export interface SelectedIcePair {
  localType: string; remoteType: string; localProtocol: string; remoteProtocol: string;
  relayProtocol: string; relay: boolean;
}
const candidateType = (value: unknown): string => ['host', 'srflx', 'prflx', 'relay'].includes(String(value)) ? String(value) : 'indisponível';
const protocol = (value: unknown): string => ['udp', 'tcp', 'tls'].includes(String(value)) ? String(value) : 'indisponível';

export function proofConnectOptions(policy: ProofIcePolicy): { autoSubscribe: false; rtcConfig?: RTCConfiguration } {
  // SDK 2.22.3 only copies SFU ICE servers when rtcConfig.iceServers is absent.
  return policy === 'relay' ? { autoSubscribe: false, rtcConfig: { iceTransportPolicy: 'relay' } }
    : { autoSubscribe: false };
}

export function selectedIcePair(report?: RTCStatsReport): SelectedIcePair | undefined {
  if (!report) return undefined;
  const stats = new Map<string, Record<string, unknown>>();
  report.forEach((value) => { stats.set(value.id, value); });
  const values = [...stats.values()];
  const selectedId = values.find((value) => value['type'] === 'transport' && value['selectedCandidatePairId'])?.['selectedCandidatePairId'];
  const pair = typeof selectedId === 'string' ? stats.get(selectedId)
    : values.find((value) => value['type'] === 'candidate-pair' && value['selected'] === true);
  if (!pair || pair['state'] !== 'succeeded') return undefined;
  const local = stats.get(String(pair['localCandidateId']));
  const remote = stats.get(String(pair['remoteCandidateId']));
  if (!local || !remote) return undefined;
  return {
    localType: candidateType(local['candidateType']), remoteType: candidateType(remote['candidateType']),
    localProtocol: protocol(local['protocol']), remoteProtocol: protocol(remote['protocol']),
    relayProtocol: protocol(local['relayProtocol']),
    relay: local['candidateType'] === 'relay' || remote['candidateType'] === 'relay',
  };
}

export function icePairMessage(direction: 'envio' | 'recepção', pair: SelectedIcePair): string {
  return `ICE selecionado (${direction}): local ${pair.localType}/${pair.localProtocol}; remoto ${pair.remoteType}/${pair.remoteProtocol}; relay ${pair.relay ? 'sim' : 'não'}; transporte cliente–TURN ${pair.relayProtocol}.`;
}
