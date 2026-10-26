import {
  LogLevel, RemoteAudioTrack, RemoteParticipant, RemoteTrack, RemoteTrackPublication,
  RemoteVideoTrack, Room, RoomEvent, Track, setLogLevel,
} from 'livekit-client';
import { CaptureAccess, CaptureConnection, ConnectionEvents } from './capture-session';
import { CaptureQuality } from './display-capture';

export class LiveKitCaptureConnection implements CaptureConnection {
  private readonly room: Room;
  private access?: CaptureAccess;
  private readonly attached = new Set<RemoteTrack>();
  private stats?: ReturnType<typeof setInterval>;
  private videoTrack?: RemoteVideoTrack;
  private audioTrack?: RemoteAudioTrack;

  constructor(
    private readonly video: HTMLVideoElement,
    private readonly audio: HTMLAudioElement,
    private readonly events: ConnectionEvents,
    private readonly playbackBlocked: (blocked: boolean) => void,
    private readonly reception: (message: string) => void,
  ) {
    setLogLevel(LogLevel.silent);
    this.room = new Room({ adaptiveStream: false, dynacast: false,
      reconnectPolicy: { nextRetryDelayInMs: (context) => context.elapsedMs >= 30_000 ? null
        : Math.min(1_000 * 2 ** context.retryCount, 5_000) } });
    this.room.on(RoomEvent.Disconnected, events.disconnected);
    this.room.on(RoomEvent.Reconnecting, events.reconnecting);
    this.room.on(RoomEvent.SignalReconnecting, events.reconnecting);
    this.room.on(RoomEvent.Reconnected, events.reconnected);
    this.room.on(RoomEvent.AudioPlaybackStatusChanged, (playing) => playbackBlocked(!playing));
    this.room.on(RoomEvent.TrackPublished, (publication, participant) => this.subscribe(publication, participant));
    this.room.on(RoomEvent.TrackSubscribed, (track, publication, participant) => this.attach(track, publication, participant));
    this.room.on(RoomEvent.TrackUnsubscribed, (track) => this.detach(track));
    this.room.on(RoomEvent.TrackSubscriptionFailed, () => reception('A assinatura de mídia falhou. Confira a rede e entre novamente.'));
  }

  async connect(access: CaptureAccess): Promise<void> {
    this.access = access;
    await this.room.connect(access.serverUrl, access.token, { autoSubscribe: false, rtcConfig: { iceServers: [] } });
    if (access.role === 'VIEWER') {
      for (const participant of this.room.remoteParticipants.values()) {
        for (const publication of participant.trackPublications.values()) this.subscribe(publication, participant);
      }
      this.stats = setInterval(() => { void this.sampleReception(); }, 3_000);
    }
  }

  async publish(stream: MediaStream, quality: CaptureQuality): Promise<void> {
    const video = stream.getVideoTracks()[0];
    const audio = stream.getAudioTracks()[0];
    if (video?.readyState !== 'live' || audio?.readyState !== 'live') throw new Error('Capture ended');
    await this.room.localParticipant.publishTrack(video, {
      source: Track.Source.ScreenShare, videoCodec: 'vp8', backupCodec: false, simulcast: false,
      screenShareEncoding: { maxBitrate: quality === '1080p' ? 4_000_000 : 2_000_000, maxFramerate: 30 },
      degradationPreference: 'maintain-resolution',
    });
    if (video.readyState !== 'live' || audio.readyState !== 'live') throw new Error('Capture ended');
    await this.room.localParticipant.publishTrack(audio, {
      source: Track.Source.ScreenShareAudio, audioPreset: { maxBitrate: 128_000 }, dtx: false,
    });
  }

  async enableAudio(): Promise<void> {
    await this.room.startAudio();
    if (this.audio.srcObject) await this.audio.play();
    if (this.video.srcObject) await this.video.play();
    this.playbackBlocked(false);
  }

  async disconnect(): Promise<void> {
    clearInterval(this.stats);
    this.room.removeAllListeners();
    for (const track of [...this.attached]) this.detach(track);
    this.video.pause();
    this.audio.pause();
    this.video.srcObject = null;
    this.audio.srcObject = null;
    await this.room.disconnect();
    this.access = undefined;
  }

  private allowed(publication: RemoteTrackPublication, participant: RemoteParticipant): boolean {
    return this.access?.role === 'VIEWER' && participant.identity === this.access.presenterIdentity
      && ((publication.source === Track.Source.ScreenShare && publication.kind === Track.Kind.Video)
        || (publication.source === Track.Source.ScreenShareAudio && publication.kind === Track.Kind.Audio));
  }
  private subscribe(publication: RemoteTrackPublication, participant: RemoteParticipant): void {
    publication.setSubscribed(this.allowed(publication, participant));
  }
  private attach(track: RemoteTrack, publication: RemoteTrackPublication, participant: RemoteParticipant): void {
    if (!this.allowed(publication, participant)) { publication.setSubscribed(false); return; }
    this.attached.add(track);
    if (track instanceof RemoteVideoTrack) {
      this.videoTrack = track;
      track.attach(this.video);
      void this.video.play().catch(() => this.playbackBlocked(true));
    } else if (track instanceof RemoteAudioTrack) {
      this.audioTrack = track;
      track.attach(this.audio);
      void this.audio.play().catch(() => this.playbackBlocked(true));
    }
    this.reception(`Recepção: vídeo ${this.videoTrack ? 'conectado' : 'aguardando'}; áudio ${this.audioTrack ? 'conectado' : 'aguardando'}.`);
  }
  private detach(track: RemoteTrack): void {
    track.detach();
    this.attached.delete(track);
    if (track === this.videoTrack) this.videoTrack = undefined;
    if (track === this.audioTrack) this.audioTrack = undefined;
  }
  private async sampleReception(): Promise<void> {
    try {
      const [video, audio] = await Promise.all([this.videoTrack?.getReceiverStats(), this.audioTrack?.getReceiverStats()]);
      if (!this.access) return;
      this.reception(`Recebidos: vídeo ${video?.bytesReceived ?? 0} bytes, ${video?.framesDecoded ?? 0} quadros; áudio ${audio?.bytesReceived ?? 0} bytes. Confirme o som audível localmente.`);
    } catch { /* Optional counters are not evidence of audible Windows audio. */ }
  }
}
