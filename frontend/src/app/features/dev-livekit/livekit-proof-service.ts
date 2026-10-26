import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import {
  ConnectionError, ConnectionErrorReason, ConnectionState, LocalVideoTrack, LogLevel, RemoteAudioTrack, RemoteTrack,
  RemoteVideoTrack, Room, RoomEvent, Track, setLogLevel,
} from 'livekit-client';
import { LiveKitProofApi } from './livekit-proof-api';
import { ProofCheck, ProofToken } from './livekit-proof-models';
import { SyntheticMedia } from './synthetic-media';

class ProofFailure extends Error {}

@Injectable()
export class LiveKitProofService {
  private readonly api = inject(LiveKitProofApi);
  readonly running = signal(false);
  readonly checks = signal<ProofCheck[]>([]);
  readonly status = signal('Pronto para executar a prova local.');
  readonly media = signal('Ainda não há mídia recebida.');
  readonly playback = signal('O teste emite um tom sintético baixo de 440 Hz.');
  readonly environment = navigator.userAgent;
  readonly transport = signal('Transporte RTC ainda não iniciado.');
  private readonly rooms = new Set<Room>();
  private readonly createdRooms = new Set<string>();
  private sender?: SyntheticMedia;
  private context?: AudioContext;
  private analyser?: AnalyserNode;
  private analyserSource?: MediaStreamAudioSourceNode;
  private viewer?: Room;
  private presenter?: Room;
  private remoteVideo?: RemoteVideoTrack;
  private remoteAudio?: RemoteAudioTrack;
  private videoElement?: HTMLVideoElement;
  private audioElement?: HTMLAudioElement;
  private aborted = false;
  private connectionStage = 'sinalização não confirmada';

  constructor() {
    // Never forward SDK contexts (which can contain credentials) to application logs.
    setLogLevel(LogLevel.silent);
  }

  async run(video: HTMLVideoElement, audio: HTMLAudioElement): Promise<void> {
    if (this.running()) return;
    this.running.set(true);
    this.aborted = false;
    this.checks.set([]);
    this.videoElement = video;
    this.audioElement = audio;
    this.status.set('Executando contra o backend e a SFU locais.');

    // Resume in the click gesture, before HTTP awaits. No media device is requested.
    try {
      this.context = new AudioContext();
      await this.context.resume();
      await this.execute();
      this.status.set('Prova concluída. Resultados reais abaixo; recursos locais liberados.');
    } catch (error: unknown) {
      this.status.set(this.aborted ? 'Prova interrompida.' : `Prova não concluída. ${this.safeFailure(error)}`);
    } finally {
      await this.cleanup();
      this.running.set(false);
    }
  }

  async enableAudio(): Promise<void> {
    try {
      await this.context?.resume();
      await this.viewer?.startAudio();
      if (this.audioElement?.srcObject) await this.audioElement.play();
      this.playback.set('Reprodução de áudio liberada por gesto local.');
    } catch {
      this.playback.set('O navegador bloqueou a reprodução. Clique novamente em Ouvir áudio.');
    }
  }

  cancel(): void {
    this.aborted = true;
    this.status.set('Interrompendo e limpando os recursos desta prova...');
    this.sender?.stop();
    this.sender = undefined;
    for (const room of this.rooms) void room.disconnect();
    // Outstanding HTTP calls are bounded by 8s. Their results are registered before
    // checking cancellation, so the run finally block can remove an in-flight room.
  }

  private async execute(): Promise<void> {
    let roomName = '';
    let presenterToken!: ProofToken;
    let viewerToken!: ProofToken;
    await this.step('Criar e consultar sala real', async () => {
      const created = await this.createRoom();
      roomName = created;
      const room = await this.api.query(roomName);
      this.require(room.exists && room.maxParticipants === 10 && room.participants.length === 0,
        'A SFU não confirmou sala vazia com limite de dez participantes.');
      return `Sala criada pelo Java e consultada na SFU; limite ${room.maxParticipants}.`;
    });
    await this.step('Conectar duas identidades distintas', async () => {
      presenterToken = await this.api.issue(roomName, 'PRESENTER');
      viewerToken = await this.api.issue(roomName, 'VIEWER');
      this.require(presenterToken.identity !== viewerToken.identity, 'Identidades não são distintas.');
      this.presenter = this.newRoom();
      this.viewer = this.newRoom();
      this.viewer.on(RoomEvent.TrackSubscribed, (track) => this.attach(track));
      await this.connect(this.presenter, presenterToken);
      await this.connect(this.viewer, viewerToken);
      const state = await this.api.query(roomName);
      this.require(state.participants.some((p) => p.identity === presenterToken.identity)
        && state.participants.some((p) => p.identity === viewerToken.identity),
      'A SFU não confirmou os dois participantes.');
      this.require(this.presenter.name === roomName && this.viewer.name === roomName,
        'Sala confirmada diferente da sala autorizada.');
      return 'Apresentador e espectador conectados; identidades confirmadas por consulta administrativa Java.';
    });
    await this.step('Publicar vídeo e áudio sintéticos', async () => {
      this.sender = new SyntheticMedia(this.context!);
      await this.presenter!.localParticipant.publishTrack(this.sender.video, {
        source: Track.Source.ScreenShare, simulcast: false, videoCodec: 'vp8',
        videoEncoding: { maxBitrate: 800_000, maxFramerate: 20 },
      });
      await this.presenter!.localParticipant.publishTrack(this.sender.audio, {
        source: Track.Source.ScreenShareAudio, audioPreset: { maxBitrate: 64_000 }, dtx: false,
      });
      await this.waitFor(async () => {
        const state = await this.api.query(roomName);
        return state.participants.find((p) => p.identity === presenterToken.identity)?.tracks.length === 2;
      }, 10_000);
      return 'A SFU confirmou duas tracks do apresentador; fontes screen_share e screen_share_audio.';
    });
    await this.step('Receber e decodificar mídia no espectador', async () => {
      let videoBytes = 0;
      let audioBytes = 0;
      let frames = 0;
      let rms = 0;
      await this.waitFor(async () => {
        const [video, audio] = await Promise.all([
          this.remoteVideo?.getReceiverStats(), this.remoteAudio?.getReceiverStats(),
        ]);
        videoBytes = video?.bytesReceived ?? 0;
        audioBytes = audio?.bytesReceived ?? 0;
        frames = video?.framesDecoded ?? 0;
        rms = this.audioRms();
        this.media.set(`RTP vídeo: ${videoBytes} bytes; áudio: ${audioBytes} bytes; quadros decodificados: ${frames}; RMS recebido: ${rms.toFixed(4)}.`);
        return videoBytes > 0 && audioBytes > 0 && frames >= 5 && rms > 0.001
          && (this.videoElement?.currentTime ?? 0) > 0.1;
      }, 18_000);
      const beforeVideo = videoBytes;
      const beforeAudio = audioBytes;
      await this.waitFor(async () => {
        const [video, audio] = await Promise.all([
          this.remoteVideo!.getReceiverStats(), this.remoteAudio!.getReceiverStats(),
        ]);
        return (video?.bytesReceived ?? 0) > beforeVideo && (audio?.bytesReceived ?? 0) > beforeAudio;
      }, 6_000);
      return `RTP recebido e crescente, ${frames} quadros decodificados, RMS ${rms.toFixed(4)}; vídeo ${this.videoElement!.currentTime.toFixed(2)}s / áudio ${(this.audioElement?.currentTime ?? 0).toFixed(2)}s. Não homologa som do Windows.`;
    });
    await this.step('Espectador: barreira local do SDK', async () => {
      const track = this.probeVideo();
      try {
        let denied = false;
        try {
          await this.viewer!.localParticipant.publishTrack(track, { source: Track.Source.ScreenShare });
        } catch {
          denied = true;
        }
        this.require(denied, 'O SDK permitiu publicação do espectador.');
      } finally {
        track.stop();
      }
      return 'SDK rejeitou publishTrack com as permissões recebidas da SFU. Esta etapa é uma barreira local.';
    });
    await this.step('Espectador: tentativa real de publicação na SFU', async () => {
      const permissions = this.viewer!.localParticipant.permissions;
      this.require(permissions?.canPublish === false, 'Grant do espectador não confirmou canPublish=false.');
      const track = this.probeVideo();
      const originalSources = permissions!.canPublishSources;
      const client = this.viewer!.engine.client;
      const originalSendAddTrack = client.sendAddTrack;
      let sentToSfu = false;
      client.sendAddTrack = async (request) => {
        await originalSendAddTrack.call(client, request);
        sentToSfu = true;
      };
      let rejected = false;
      let responseReason: number | undefined;
      const response = (event: { reason: number; request?: { case?: string } }): void => {
        if (event.request?.case === 'addTrack') responseReason = event.reason;
      };
      this.viewer!.engine.on('signalRequestResponse', response);
      try {
        // Adversarial DEVELOPMENT probe: bypass only the SDK's local guard. The
        // signed JWT and the SFU permissions stay unchanged. Fixed SDK 2.22.3.
        permissions!.canPublish = true;
        permissions!.canPublishSources = [];
        try {
          await this.viewer!.localParticipant.publishTrack(track, { source: Track.Source.ScreenShare });
        } catch {
          rejected = true;
        }
      } finally {
        permissions!.canPublish = false;
        permissions!.canPublishSources = originalSources;
        this.viewer!.engine.off('signalRequestResponse', response);
        client.sendAddTrack = originalSendAddTrack;
        track.stop();
      }
      const participant = (await this.api.query(roomName)).participants.find((p) => p.identity === viewerToken.identity);
      // RequestResponse.NOT_ALLOWED = 2 in the protocol bundled with pinned SDK 2.22.3.
      this.require(sentToSfu && rejected && responseReason === 2
        && participant !== undefined && participant.tracks.length === 0,
        'A tentativa real não comprovou a barreira da SFU.');
      return 'JWT inalterado: SFU respondeu NOT_ALLOWED ao AddTrack; consulta administrativa confirmou zero tracks do espectador. O SDK encerrou a tentativa sem publicar.';
    });
    await this.proveRoomScope(roomName);
    await this.step('Excluir sala e desconectar participantes', async () => {
      await this.api.delete(roomName);
      await this.waitFor(async () => this.presenter!.state === ConnectionState.Disconnected
        && this.viewer!.state === ConnectionState.Disconnected, 10_000);
      this.require(await this.api.isAbsent(roomName), 'A SFU ainda informa sala existente após exclusão.');
      this.createdRooms.delete(roomName);
      return 'DeleteRoom confirmado; os dois clientes receberam desconexão e a sala está ausente.';
    });
    await this.step('Token antigo não recria a sala', async () => {
      this.require(Date.parse(presenterToken.expiresAt) > Date.now(), 'Token expirou antes da prova de reutilização.');
      const oldClient = this.newRoom();
      let rejected = false;
      try {
        await this.connect(oldClient, presenterToken);
      } catch {
        rejected = true;
      } finally {
        await oldClient.disconnect();
      }
      this.require(rejected && await this.api.isAbsent(roomName), 'Token antigo acessou ou recriou a sala.');
      return 'Mesmo JWT ainda dentro do TTL foi recusado após DeleteRoom; consulta Java confirmou ausência, com auto_create=false.';
    });
  }

  private async proveRoomScope(roomName: string): Promise<void> {
    let otherRoom = '';
    let token!: ProofToken;
    await this.step('Token válido permanece na sala autorizada', async () => {
      otherRoom = await this.createRoom();
      token = await this.api.issue(roomName, 'VIEWER');
      const probe = this.newRoom();
      try {
        // A query parameter is not an SDK room selector. The signed room grant is
        // authoritative; this records the actual SDK result instead of assuming it.
        const url = new URL(token.serverUrl);
        url.searchParams.set('room', otherRoom);
        await this.connect(probe, { ...token, serverUrl: url.toString() });
        const other = await this.api.query(otherRoom);
        this.require(probe.name === roomName && other.participants.every((p) => p.identity !== token.identity),
          'O token válido alcançou a sala diferente da autorizada.');
      } finally {
        await probe.disconnect();
      }
      return 'Query room=B com JWT válido de A conectou somente em A; consulta administrativa confirmou B sem aquela identidade.';
    });
    await this.step('Alterar a sala do JWT invalida a assinatura', async () => {
      const probe = this.newRoom();
      let rejected = false;
      try {
        await this.connect(probe, { ...token, token: this.withTamperedRoom(token.token, otherRoom) });
      } catch (error: unknown) {
        // Network failures do not prove authorization. Require an explicit denial.
        rejected = error instanceof ConnectionError && error.status === 401;
      } finally {
        await probe.disconnect();
      }
      const other = await this.api.query(otherRoom);
      this.require(rejected && other.participants.length === 0,
        'Não houve rejeição HTTP 401 da assinatura alterada ou a outra sala recebeu participante.');
      await this.api.delete(otherRoom);
      this.createdRooms.delete(otherRoom);
      return 'Somente claim video.room alterado para B, assinatura original preservada: SFU negou com 401; B continuou vazia.';
    });
  }

  private newRoom(): Room {
    const room = new Room({ adaptiveStream: false, dynacast: false, reconnectPolicy: { nextRetryDelayInMs: () => null } });
    this.rooms.add(room);
    return room;
  }

  private async connect(room: Room, token: ProofToken): Promise<void> {
    this.connectionStage = 'sinalização não confirmada';
    room.once(RoomEvent.SignalConnected, () => { this.connectionStage = 'sinalização aceita; RTC ainda não confirmado'; });
    const diagnostic = setInterval(() => {
      void this.transportDiagnostic(room).catch(() => undefined);
    }, 500);
    try {
      await room.connect(token.serverUrl, token.token, { autoSubscribe: token.role === 'VIEWER',
        rtcConfig: { iceServers: [] } });
      this.connectionStage = 'sinalização e RTC confirmados';
      this.transport.set('Conexão RTC confirmada nesta execução; o encerramento é verificado ao excluir a sala.');
    } finally {
      clearInterval(diagnostic);
    }
    this.ensureActive();
  }

  private async transportDiagnostic(room: Room): Promise<void> {
    const manager = room.engine.pcManager;
    if (!manager) return;
    const pub = manager.publisher;
    const sub = manager.subscriber;
    const stats = await pub.getStats();
    let local = 0;
    let remote = 0;
    const protocols = new Set<string>();
    const addressClasses = new Set<string>();
    const pairs = new Set<string>();
    stats?.forEach((entry) => {
      if (entry.type === 'local-candidate') local += 1;
      if (entry.type === 'remote-candidate') {
        remote += 1;
        if (entry.protocol === 'tcp' || entry.protocol === 'udp') protocols.add(entry.protocol);
        addressClasses.add(typeof entry.address !== 'string' ? 'não-informado' :
          (entry.address.startsWith('127.') || entry.address === '::1') ? 'loopback' : 'fora-do-loopback');
      }
      if (entry.type === 'candidate-pair' && typeof entry.state === 'string') pairs.add(entry.state);
    });
    this.transport.set(`RTC publicador: ${pub.getConnectionState()}/${pub.getICEConnectionState()}; receptor: ${sub?.getConnectionState() ?? 'ausente'}/${sub?.getICEConnectionState() ?? 'ausente'}; candidatos locais/remotos: ${local}/${remote}; protocolo remoto: ${[...protocols].join(',') || 'ausente'}; destino remoto: ${[...addressClasses].join(',') || 'ausente'}; pares ICE: ${[...pairs].join(',') || 'ausentes'}.`);
  }

  private async createRoom(): Promise<string> {
    const created = await this.api.create();
    this.createdRooms.add(created.roomName);
    this.ensureActive();
    return created.roomName;
  }

  private attach(track: RemoteTrack): void {
    if (track instanceof RemoteVideoTrack && this.videoElement) {
      this.remoteVideo = track;
      track.attach(this.videoElement);
      void this.videoElement.play().catch(() => this.playback.set('Clique em Ouvir áudio para liberar a reprodução.'));
    } else if (track instanceof RemoteAudioTrack && this.audioElement && this.context) {
      this.remoteAudio = track;
      track.attach(this.audioElement);
      this.analyser = this.context.createAnalyser();
      this.analyser.fftSize = 2048;
      this.analyserSource = this.context.createMediaStreamSource(new MediaStream([track.mediaStreamTrack]));
      this.analyserSource.connect(this.analyser);
      void this.audioElement.play().catch(() => this.playback.set('Áudio recebido; clique em Ouvir áudio se o navegador bloquear som.'));
    }
  }

  private audioRms(): number {
    if (!this.analyser) return 0;
    const samples = new Float32Array(this.analyser.fftSize);
    this.analyser.getFloatTimeDomainData(samples);
    return Math.sqrt(samples.reduce((sum, sample) => sum + sample * sample, 0) / samples.length);
  }

  private probeVideo(): LocalVideoTrack {
    const canvas = document.createElement('canvas');
    canvas.width = 160;
    canvas.height = 90;
    canvas.getContext('2d')?.fillRect(0, 0, 160, 90);
    const track = new LocalVideoTrack(canvas.captureStream(1).getVideoTracks()[0], undefined, true);
    track.source = Track.Source.ScreenShare;
    return track;
  }

  private withTamperedRoom(token: string, roomName: string): string {
    const parts = token.split('.');
    const claims = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))) as { video: { room: string } };
    claims.video.room = roomName;
    parts[1] = btoa(JSON.stringify(claims)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    return parts.join('.');
  }

  private async step(label: string, action: () => Promise<string>): Promise<void> {
    this.ensureActive();
    const index = this.checks().length;
    this.checks.update((checks) => [...checks, { label, status: 'running', detail: 'Executando...' }]);
    try {
      const detail = await action();
      this.ensureActive();
      this.replaceCheck(index, { label, status: 'passed', detail });
    } catch (error: unknown) {
      this.replaceCheck(index, { label, status: 'failed', detail: this.safeFailure(error) });
      throw error;
    }
  }

  private replaceCheck(index: number, check: ProofCheck): void {
    this.checks.update((checks) => checks.map((old, current) => current === index ? check : old));
  }

  private async waitFor(predicate: () => Promise<boolean>, milliseconds: number): Promise<void> {
    const deadline = Date.now() + milliseconds;
    while (Date.now() < deadline) {
      this.ensureActive();
      if (await predicate()) return;
      await new Promise<void>((resolve) => setTimeout(resolve, 300));
    }
    throw new ProofFailure('Prazo esgotado sem confirmação real. Verifique os serviços locais e tente novamente.');
  }

  private ensureActive(): void {
    if (this.aborted) throw new ProofFailure('Prova interrompida pelo operador.');
  }

  private require(condition: boolean, detail: string): void {
    if (!condition) throw new ProofFailure(detail);
  }

  private safeFailure(error: unknown): string {
    if (error instanceof ProofFailure) return error.message;
    if (error instanceof HttpErrorResponse) return `API local respondeu HTTP ${error.status}. Confira o perfil de prova, a origem e os serviços.`;
    if (error instanceof ConnectionError) return `Conexão SFU não confirmada${typeof error.status === 'number' ? ` (HTTP ${error.status})` : ''}; motivo ${ConnectionErrorReason[error.reason]}; ${this.connectionStage}. Confira o LiveKit local.`;
    return 'Operação não confirmada. Confira os pré-requisitos e serviços locais; nenhum detalhe de credencial foi exibido.';
  }

  private async cleanup(): Promise<void> {
    for (const room of this.rooms) await room.disconnect().catch(() => undefined);
    this.rooms.clear();
    this.remoteAudio?.detach();
    this.remoteVideo?.detach();
    this.remoteAudio = undefined;
    this.remoteVideo = undefined;
    this.analyserSource?.disconnect();
    this.analyser?.disconnect();
    this.analyserSource = undefined;
    this.analyser = undefined;
    this.sender?.stop();
    this.sender = undefined;
    await this.context?.close().catch(() => undefined);
    this.context = undefined;
    for (const roomName of this.createdRooms) {
      try {
        await this.api.delete(roomName);
        this.createdRooms.delete(roomName);
      } catch {
        this.status.set('Limpeza de sala não confirmada. Mantenha o backend de prova ativo e execute novamente antes de encerrá-lo.');
      }
    }
    this.presenter = undefined;
    this.viewer = undefined;
  }
}
