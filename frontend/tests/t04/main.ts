import '../../src/styles.css';
import {
  ConnectionError, ConnectionState, LogLevel, RemoteAudioTrack, RemoteVideoTrack,
  Room, RoomEvent, Track, setLogLevel,
} from 'livekit-client';
import { CaptureProofApi } from '../../src/app/features/dev-capture/capture-api';
import { CaptureAccess, CaptureApiFailure } from '../../src/app/features/dev-capture/capture-session';
import { ProofIcePolicy, proofConnectOptions, selectedIcePair } from '../../src/app/features/dev-capture/ice-diagnostics';

// Deliberately outside app routes/build entrypoints. Never log provider errors, tokens or reports.
setLogLevel(LogLevel.silent);
const input = (id: string): HTMLInputElement => document.getElementById(id) as HTMLInputElement;
const button = (id: string): HTMLButtonElement => document.getElementById(id) as HTMLButtonElement;
const status = document.getElementById('status')!;
const list = document.getElementById('results')!;
const videos = document.getElementById('videos')!;
const results: string[] = [];
const rooms = new Set<Room>();
let generation = 0;
let owner: CaptureProofApi | undefined;
let ownsRoom = false;
let context: AudioContext | undefined;
let source: SyntheticSource | undefined;
let pending: Promise<void> | undefined;

function result(value: string): void {
  results.push(value); const item = document.createElement('li'); item.textContent = value; list.append(item);
  button('copy').disabled = false;
}
class ProofFailure extends Error {}
function requireProof(condition: unknown, message: string): asserts condition { if (!condition) throw new ProofFailure(message); }
const pause = (ms: number): Promise<void> => new Promise(resolve => setTimeout(resolve, ms));
async function until(probe: () => boolean | Promise<boolean>, timeout = 20_000): Promise<boolean> {
  const deadline = Date.now() + timeout;
  do { if (await probe()) return true; await pause(300); } while (Date.now() < deadline);
  return false;
}
function ensure(id: number): void { requireProof(id === generation, 'Ensaio interrompido pelo operador.'); }
function newRoom(): Room {
  const room = new Room({ adaptiveStream: false, dynacast: false, reconnectPolicy: { nextRetryDelayInMs: () => null } });
  rooms.add(room); return room;
}
function tokenClaims(token: string): { exp?: number; video?: { room?: string } } {
  return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number; video?: { room?: string } };
}

class SyntheticSource {
  readonly stream: MediaStream;
  private readonly timer: ReturnType<typeof setInterval>;
  private readonly oscillator: OscillatorNode;
  private readonly gain: GainNode;
  private stopped = false;
  constructor(audioContext: AudioContext) {
    const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 360;
    const drawing = canvas.getContext('2d')!;
    let frame = 0;
    const draw = (): void => {
      drawing.fillStyle = '#050507'; drawing.fillRect(0, 0, 640, 360);
      drawing.fillStyle = '#bd9cff'; drawing.fillRect((++frame * 7) % 580, 180, 60, 60);
      drawing.font = '24px system-ui'; drawing.fillText('T-04 · VÍDEO E TOM SINTÉTICOS', 20, 60);
      drawing.fillText(`Quadro ${frame}`, 20, 110);
    };
    draw(); this.timer = setInterval(draw, 100);
    this.stream = canvas.captureStream(10);
    const destination = audioContext.createMediaStreamDestination();
    this.oscillator = audioContext.createOscillator(); this.oscillator.frequency.value = 440;
    this.gain = audioContext.createGain(); this.gain.gain.value = 0.04;
    this.oscillator.connect(this.gain).connect(destination); this.oscillator.start();
    this.stream.addTrack(destination.stream.getAudioTracks()[0]);
  }
  stop(): void {
    if (this.stopped) return; this.stopped = true;
    clearInterval(this.timer); this.stream.getTracks().forEach(track => track.stop());
    this.oscillator.stop(); this.oscillator.disconnect(); this.gain.disconnect();
  }
}

interface Receiver {
  api: CaptureProofApi; access: CaptureAccess; room: Room; video: HTMLVideoElement;
  videoTrack?: RemoteVideoTrack; audioTrack?: RemoteAudioTrack; analyser?: AnalyserNode; audioSource?: MediaStreamAudioSourceNode;
  renewedToken?: string;
}
async function join(api: CaptureProofApi, access: CaptureAccess, policy: ProofIcePolicy): Promise<Receiver> {
  const room = newRoom();
  const video = document.createElement('video'); video.autoplay = true; video.muted = true; video.playsInline = true;
  video.className = 'media-frame'; video.setAttribute('aria-label', `Receptor sintético ${videos.children.length + 1}`); videos.append(video);
  const receiver: Receiver = { api, access, room, video };
  // With this pinned SDK, a changed token during the initial connection originates
  // in the SFU token-refresh signal. No reconnect occurs during this measurement.
  room.engine.on('tokenRefreshed', (token: string) => {
    try {
      if (token !== access.token && tokenClaims(token).video?.room === access.roomName
        && (tokenClaims(token).exp ?? 0) > (tokenClaims(access.token).exp ?? 0)) receiver.renewedToken = token;
    } catch { /* Malformed/unrelated signal is never evidence. */ }
  });
  const subscribe = (): void => {
    for (const participant of room.remoteParticipants.values()) {
      for (const publication of participant.trackPublications.values()) {
        publication.setSubscribed(participant.identity === access.presenterIdentity
          && (publication.source === Track.Source.ScreenShare || publication.source === Track.Source.ScreenShareAudio));
      }
    }
  };
  room.on(RoomEvent.TrackPublished, subscribe);
  room.on(RoomEvent.TrackSubscribed, (track, publication, participant) => {
    if (participant.identity !== access.presenterIdentity) { publication.setSubscribed(false); return; }
    if (track instanceof RemoteVideoTrack && publication.source === Track.Source.ScreenShare) {
      receiver.videoTrack = track; track.attach(video); void video.play().catch(() => undefined);
    } else if (track instanceof RemoteAudioTrack && publication.source === Track.Source.ScreenShareAudio) {
      receiver.audioTrack = track;
      receiver.audioSource = context!.createMediaStreamSource(new MediaStream([track.mediaStreamTrack]));
      receiver.analyser = context!.createAnalyser(); receiver.analyser.fftSize = 2048;
      receiver.audioSource.connect(receiver.analyser);
      // Pull decoding through the analyser while keeping synthetic reception silent.
      const mute = context!.createGain(); mute.gain.value = 0;
      receiver.analyser.connect(mute).connect(context!.destination);
    }
  });
  await room.connect(access.serverUrl, access.token, proofConnectOptions(policy)); subscribe();
  return receiver;
}
async function publish(room: Room): Promise<void> {
  await room.localParticipant.publishTrack(source!.stream.getVideoTracks()[0], { source: Track.Source.ScreenShare,
    videoCodec: 'vp8', backupCodec: false, simulcast: false, screenShareEncoding: { maxBitrate: 500_000, maxFramerate: 10 } });
  await room.localParticipant.publishTrack(source!.stream.getAudioTracks()[0], { source: Track.Source.ScreenShareAudio,
    audioPreset: { maxBitrate: 64_000 }, dtx: false });
}
async function received(receiver: Receiver): Promise<{ video: number; audio: number; frames: number; rms: number }> {
  const [video, audio] = await Promise.all([receiver.videoTrack?.getReceiverStats(), receiver.audioTrack?.getReceiverStats()]);
  const waveform = new Float32Array(receiver.analyser?.fftSize ?? 2048);
  receiver.analyser?.getFloatTimeDomainData(waveform);
  const rms = Math.sqrt(waveform.reduce((sum, sample) => sum + sample * sample, 0) / waveform.length);
  return { video: video?.bytesReceived ?? 0, audio: audio?.bytesReceived ?? 0, frames: video?.framesDecoded ?? 0, rms };
}

async function deniedPublish(receiver: Receiver): Promise<boolean> {
  const permissions = receiver.room.localParticipant.permissions;
  if (!permissions || permissions.canPublish) return false;
  const client = receiver.room.engine.client;
  const send = client.sendAddTrack;
  let sent = false; let reason: number | undefined; let rejected = false;
  const response = (event: { reason: number; request?: { case?: string } }): void => {
    if (event.request?.case === 'addTrack') reason = event.reason;
  };
  const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 16;
  const track = canvas.captureStream(1).getVideoTracks()[0];
  const originalSources = permissions.canPublishSources;
  client.sendAddTrack = async request => { await send.call(client, request); sent = true; };
  receiver.room.engine.on('signalRequestResponse', response);
  try {
    permissions.canPublish = true; permissions.canPublishSources = [];
    try { await receiver.room.localParticipant.publishTrack(track, { source: Track.Source.ScreenShare }); } catch { rejected = true; }
  } finally {
    permissions.canPublish = false; permissions.canPublishSources = originalSources;
    client.sendAddTrack = send; receiver.room.engine.off('signalRequestResponse', response); track.stop();
  }
  // SDK guard alone or an unclassified timeout does not satisfy this assertion.
  return sent && rejected && reason === 2 && receiver.room.localParticipant.trackPublications.size === 0;
}
async function rejectToken(access: CaptureAccess, token: string, targetRoom: string, statuses = [401, 404]): Promise<boolean> {
  const room = newRoom(); let denied = false;
  const serverUrl = new URL(access.serverUrl); serverUrl.searchParams.set('room', targetRoom);
  try { await room.connect(serverUrl.toString(), token, proofConnectOptions('all')); }
  catch (error: unknown) { denied = error instanceof ConnectionError && statuses.includes(error.status ?? 0); }
  finally { await room.disconnect(); }
  return denied && room.state === ConnectionState.Disconnected;
}

async function execute(id: number, policy: ProofIcePolicy, viewerKey: string): Promise<void> {
  ownsRoom = true;
  let access: CaptureAccess;
  try { access = await owner!.create(); }
  catch (error: unknown) { if (error instanceof CaptureApiFailure && error.status >= 400 && error.status < 500) ownsRoom = false; throw error; }
  ensure(id);
  const publisher = newRoom(); await publisher.connect(access.serverUrl, access.token, proofConnectOptions(policy)); ensure(id);
  await publish(publisher); ensure(id);
  requireProof(await until(async () => (await owner!.status()).live), 'SFU não confirmou ambas as fontes sintéticas.'); ensure(id);
  const receivers: Receiver[] = [];
  for (let i = 0; i < 8; i++) {
    const api = new CaptureProofApi(viewerKey); const token = await api.viewer(); ensure(id);
    receivers.push(await join(api, token, policy)); ensure(id);
  }
  const contenders = [new CaptureProofApi(viewerKey), new CaptureProofApi(viewerKey)];
  const race = await Promise.allSettled(contenders.map(api => api.viewer())); ensure(id);
  const winners = race.flatMap((entry, index) => entry.status === 'fulfilled' ? [{ api: contenders[index], access: entry.value }] : []);
  const losers = race.filter(entry => entry.status === 'rejected') as PromiseRejectedResult[];
  requireProof(winners.length === 1 && losers.length === 1 && losers[0].reason instanceof CaptureApiFailure
    && losers[0].reason.code === 'CAPTURE_PROOF_CAPACITY', 'Corrida da última vaga não confirmou exatamente uma admissão e uma recusa de capacidade.');
  receivers.push(await join(winners[0].api, winners[0].access, policy)); ensure(id);
  requireProof(new Set(receivers.map(receiver => receiver.access.identity)).size === 9
    && receivers.every(receiver => receiver.access.identity !== access.identity), 'Identidades distintas não confirmadas.');
  const state = await owner!.status(); ensure(id);
  requireProof(state.viewerCount === 9 && state.viewerSlotsUsed === 9, 'SFU/backend não confirmaram nove espectadores.');
  result('APROVADO · 1 apresentador + 9 identidades backend distintas; SFU confirmou 9 espectadores; corrida da última vaga recusou o décimo.');
  requireProof(await until(() => receivers.every(receiver => !!receiver.videoTrack && !!receiver.audioTrack)), 'Nem todos os nove clientes receberam as duas tracks.'); ensure(id);
  const before = await Promise.all(receivers.map(received));
  await pause(3_000); ensure(id);
  const after = await Promise.all(receivers.map(received));
  requireProof(after.every((sample, i) => sample.video > before[i].video && sample.audio > before[i].audio
    && sample.frames > before[i].frames && sample.rms > 0.001), 'RTP crescente, quadros decodificados ou RMS não comprovados para todos os nove receptores.');
  after.forEach((sample, i) => result(`APROVADO · receptor ${i + 1}: vídeo ${sample.video} bytes; áudio ${sample.audio} bytes; ${sample.frames} quadros; RMS ${sample.rms.toFixed(4)}. Conteúdo sintético.`));
  const all = [publisher, ...receivers.map(receiver => receiver.room)];
  for (let i = 0; i < all.length; i++) {
    const manager = all[i].engine.pcManager;
    const report = await (i === 0 ? manager?.publisher.getStats() : (manager?.subscriber ?? manager?.publisher)?.getStats());
    const pair = selectedIcePair(report);
    requireProof(pair && pair.relay === (policy === 'relay'), 'Par ICE selecionado não comprova o modo de transporte solicitado em todos os participantes.');
    result(`APROVADO · ICE ${i === 0 ? 'apresentador' : `receptor ${i}`}: ${JSON.stringify(pair)}.`);
  }
  ensure(id);
  const repeated = await receivers[0].api.viewer(); ensure(id);
  requireProof(repeated.identity === receivers[0].access.identity && (await owner!.status()).viewerSlotsUsed === 9, 'Repetição consumiu outra identidade/vaga.');
  result('APROVADO · repetir entrada com o nonce em memória devolveu a mesma identidade, sem consumir nova vaga.');
  const leaving = receivers[0];
  await leaving.room.disconnect(); leaving.video.remove(); leaving.audioSource?.disconnect(); leaving.analyser?.disconnect();
  await leaving.api.leave(); ensure(id);
  requireProof(await until(async () => (await owner!.status()).viewerCount === 8), 'Saída não removeu o espectador da SFU.');
  const returning = await leaving.api.viewer();
  requireProof(returning.identity === leaving.access.identity && (await owner!.status()).viewerSlotsUsed === 9, 'Retorno não preservou identidade/reserva.');
  receivers[0] = await join(leaving.api, returning, policy); ensure(id);
  requireProof(await until(async () => (await owner!.status()).viewerCount === 9), 'Retorno não recuperou os nove espectadores.');
  result('APROVADO · saída removeu participante sem liberar reserva a terceiro; retorno na mesma aba recuperou a identidade e a nona conexão.');
  requireProof(await deniedPublish(receivers[0]), 'Publicação do espectador não foi explicitamente negada pela SFU.'); ensure(id);
  result('APROVADO · JWT inalterado: AddTrack enviado; SFU respondeu NOT_ALLOWED; nenhuma publicação local.');
  const refreshed = receivers.find(receiver => receiver.renewedToken);
  if (refreshed) result('OBSERVADO · token diferente do JWT inicial entregue por tokenRefreshed real do SDK/SFU; valor mantido somente em memória.');
  else result('NÃO EXECUTADO · nenhum token renovado real foi observado; o critério de isolamento de token renovado permanece pendente.');

  await owner!.delete(); ownsRoom = false; ensure(id);
  requireProof(await until(() => [publisher, ...receivers.map(receiver => receiver.room)].every(room => room.state === ConnectionState.Disconnected), 10_000), 'DeleteRoom não desconectou todos os dez participantes.');
  requireProof(!(await owner!.status()).exists, 'Sala A permanece após exclusão.');
  result('APROVADO · exclusão de A confirmou ausência e desconectou os dez participantes.');
  requireProof(Date.parse(receivers[0].access.expiresAt) > Date.now(), 'JWT inicial do receptor expirou antes da prova de reuso; repita a execução.');
  requireProof(await rejectToken(receivers[0].access, receivers[0].access.token, access.roomName)
    && !(await owner!.status()).exists, 'JWT inicial ainda válido não recebeu recusa explícita ou recriou sala A.');
  result('APROVADO · JWT inicial ainda válido foi explicitamente recusado e não recriou A.'); ensure(id);

  ownsRoom = true;
  const nextAccess = await owner!.create(); ensure(id);
  requireProof(nextAccess.roomName !== access.roomName && nextAccess.identity !== access.identity, 'Geração reutilizou sala/identidade anterior.');
  const nextPublisher = newRoom(); await nextPublisher.connect(nextAccess.serverUrl, nextAccess.token, proofConnectOptions(policy)); ensure(id);
  source?.stop(); source = new SyntheticSource(context!);
  await publish(nextPublisher); ensure(id);
  requireProof(await until(async () => (await owner!.status()).live), 'Geração B não confirmou mídia nova.'); ensure(id);
  requireProof(await rejectToken(receivers[0].access, receivers[0].access.token, nextAccess.roomName), 'Token A não foi explicitamente recusado com B ativa.');
  if (refreshed?.renewedToken) {
    requireProof((tokenClaims(refreshed.renewedToken).exp ?? 0) * 1_000 > Date.now(), 'Token renovado expirou antes da prova.');
    requireProof(await rejectToken(refreshed.access, refreshed.renewedToken, nextAccess.roomName), 'Token renovado de A não recebeu recusa explícita com B ativa.');
    result('APROVADO · token renovado real ainda válido de A foi explicitamente recusado com mídia de B ativa; não recebeu mídia futura.');
  }
  const parts = receivers[0].access.token.split('.');
  const tampered = tokenClaims(receivers[0].access.token); tampered.video = { ...tampered.video, room: nextAccess.roomName };
  parts[1] = btoa(JSON.stringify(tampered)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  requireProof(await rejectToken(receivers[0].access, parts.join('.'), nextAccess.roomName, [401]), 'Alterar room mantendo assinatura não recebeu recusa HTTP401 explícita.');
  const nextState = await owner!.status();
  requireProof(nextState.roomName === nextAccess.roomName && nextState.live && nextState.viewerCount === 0, 'Geração B recebeu espectador indevido ou perdeu mídia.');
  result('APROVADO · A e B usam nomes distintos; token inicial de A e assinatura adulterada foram recusados; B continua LIVE com zero espectadores antigos.');
  result('LIMITE · resultados sintéticos de curta duração, sem homologar redes distintas, UDP bloqueado, captura Windows, som audível ou T-23 de duas horas.');
}

async function cleanup(): Promise<void> {
  source?.stop(); source = undefined;
  await Promise.allSettled([...rooms].map(room => room.disconnect())); rooms.clear();
  videos.replaceChildren();
  await context?.close().catch(() => undefined); context = undefined;
  if (ownsRoom) { await owner!.delete(); ownsRoom = false; }
  owner = undefined;
}
button('run').onclick = (): void => {
  if (pending || ownsRoom) return;
  const presenterKey = input('presenter').value.trim();
  const viewerKey = input('viewer').value.trim();
  if (![presenterKey, viewerKey].every(key => /^[A-Za-z0-9_-]{32,128}$/.test(key))) { status.textContent = 'Cole somente as duas chaves experimentais válidas; não a linha de variável inteira.'; return; }
  input('presenter').value = ''; input('viewer').value = '';
  results.length = 0; list.replaceChildren(); button('run').disabled = true; button('stop').disabled = false;
  button('policy').disabled = true;
  const id = ++generation;
  owner = new CaptureProofApi(presenterKey);
  context = new AudioContext(); void context.resume(); source = new SyntheticSource(context);
  const policy = input('policy').value === 'relay' ? 'relay' : 'all';
  status.textContent = 'Executando conexões e recepção sintéticas. Aguarde os resultados reais.';
  pending = (async () => {
    try { await execute(id, policy, viewerKey); status.textContent = 'Ensaio sintético terminou. Limpeza em andamento.'; }
    catch (error: unknown) {
      // Only our own assertion text or allowlisted API diagnostics are displayed.
      const safe = error instanceof CaptureApiFailure ? `API: ${error.code}${error.requestId ? `; requisição ${error.requestId}` : ''}`
        : error instanceof ProofFailure ? error.message : 'Falha de transporte não classificada. Confira SFU/TLS/ICE e repita.';
      result(`NÃO APROVADO · ${safe}`); status.textContent = 'Ensaio incompleto; confira os resultados e a limpeza.';
    } finally {
      try { await cleanup(); result('LIMPEZA CONFIRMADA · conexões/tracks/contexto liberados; sala desta execução ausente.'); }
      catch { button('cleanup').disabled = false; result('LIMPEZA PENDENTE · mantenha o backend ativo e use Repetir limpeza.'); }
      pending = undefined; button('run').disabled = ownsRoom; button('stop').disabled = true; button('policy').disabled = false;
    }
  })();
};
button('stop').onclick = (): void => {
  generation++; source?.stop(); source = undefined;
  for (const room of rooms) void room.disconnect();
  status.textContent = 'Interrompendo. Aguardando operações pendentes antes de confirmar a limpeza.';
};
button('cleanup').onclick = (): void => {
  button('cleanup').disabled = true;
  void cleanup().then(() => { result('LIMPEZA CONFIRMADA · sala ausente.'); button('run').disabled = false; })
    .catch(() => { button('cleanup').disabled = false; status.textContent = 'Limpeza ainda não confirmada. Confira backend/SFU e repita.'; });
};
button('copy').onclick = (): void => {
  void navigator.clipboard.writeText(['T-04 · resultados desta execução · mídia sintética', ...results].join('\n'))
    .then(() => { status.textContent = 'Resultados sanitizados copiados.'; }).catch(() => { status.textContent = 'Cópia não permitida. Selecione a lista de resultados.'; });
};
window.addEventListener('pagehide', () => { generation++; void cleanup().catch(() => undefined); });
