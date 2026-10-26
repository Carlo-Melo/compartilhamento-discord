import { CaptureFailure, CaptureQuality, DisplayCapture } from './display-capture';

/** Carries only an HTTP status; provider bodies and credentials are never exposed. */
export class CaptureApiFailure extends Error {
  constructor(readonly status: number) {
    super('A API recusou ou não concluiu a operação de captura.');
    this.name = 'CaptureApiFailure';
  }
}

export interface CaptureAccess {
  roomName: string; identity: string; presenterIdentity: string; role: 'PRESENTER' | 'VIEWER';
  serverUrl: string; token: string; expiresAt: string;
}
export interface CaptureStatus {
  exists: boolean; live: boolean; roomName: string | null;
  presenterIdentity: string | null; viewerIdentity: string | null;
}
export interface CaptureApi {
  create(): Promise<CaptureAccess>;
  viewer(): Promise<CaptureAccess>;
  status(): Promise<CaptureStatus>;
  delete(): Promise<void>;
}
export interface CaptureConnection {
  connect(access: CaptureAccess): Promise<void>;
  publish(stream: MediaStream, quality: CaptureQuality): Promise<void>;
  disconnect(): Promise<void>;
  enableAudio(): Promise<void>;
}
export type CapturePhase = 'idle' | 'selecting' | 'connecting' | 'publishing' | 'waiting'
  | 'live' | 'reconnecting' | 'ended' | 'error';
export interface CaptureView {
  phase: CapturePhase; message: string; busy: boolean; cleanupPending: boolean;
}
export interface ConnectionEvents {
  disconnected(): void; reconnecting(): void; reconnected(): void;
}

/** Experimental orchestration only. No bot, product session, cookies or persisted credentials. */
export class CaptureSession {
  private generation = 0;
  private pending?: Promise<void>;
  private connection?: CaptureConnection;
  private ownsRoom = false;
  private cleaning?: Promise<void>;
  private closingOperations = 0;
  private monitor?: ReturnType<typeof setTimeout>;
  private reconnectDeadline?: ReturnType<typeof setTimeout>;
  private transportRevision = 0;
  private access?: CaptureAccess;
  private view: CaptureView = { phase: 'idle', message: 'Pronto para iniciar.', busy: false, cleanupPending: false };

  constructor(
    private readonly api: CaptureApi,
    private readonly capture: Pick<DisplayCapture, 'select' | 'stop'>,
    private readonly connectionFactory: (events: ConnectionEvents) => CaptureConnection,
    private readonly changed: (view: CaptureView) => void,
    private readonly preview: (stream: MediaStream | null) => void,
    private readonly delay: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  ) {}

  async start(role: 'PRESENTER' | 'VIEWER', quality: CaptureQuality = '720p'): Promise<void> {
    if (this.view.busy || this.connection || this.ownsRoom || this.cleaning) return;
    const id = ++this.generation;
    this.update({ busy: true, phase: role === 'PRESENTER' ? 'selecting' : 'connecting',
      message: role === 'PRESENTER' ? 'Selecione a tela e habilite o compartilhamento de áudio no navegador.' : 'Conectando como espectador...' });
    // execute() invokes getDisplayMedia synchronously, still inside the click gesture.
    const task = this.execute(id, role, quality);
    this.pending = task;
    try {
      await task;
    } catch (error: unknown) {
      if (this.current(id)) this.release('error', error instanceof CaptureFailure ? error.message
        : 'Não foi possível confirmar a transmissão. Confira a chave do papel, backend, SFU e conectividade de mídia; tente novamente.');
    } finally {
      if (this.pending === task) this.pending = undefined;
      if (!this.current(id)) await this.cleanup();
      this.update({ busy: this.closingOperations > 0 });
    }
  }

  async stop(message = 'Conexão encerrada. Os recursos locais foram liberados.', failure = false): Promise<void> {
    ++this.closingOperations;
    this.update({ busy: true });
    try {
      this.release(failure ? 'error' : 'ended', message);
      // A late selector/HTTP/connect result must finish before cleanup/new attempts.
      await this.pending?.catch(() => undefined);
      await this.cleanup();
    } finally {
      --this.closingOperations;
      this.update({ busy: this.closingOperations > 0 || !!this.pending || !!this.cleaning });
    }
  }

  async retryCleanup(): Promise<void> {
    ++this.closingOperations;
    this.update({ busy: true });
    try {
      await this.pending?.catch(() => undefined);
      await this.cleanup();
    } finally {
      --this.closingOperations;
      this.update({ busy: this.closingOperations > 0 || !!this.pending || !!this.cleaning });
    }
  }
  async enableAudio(): Promise<void> { await this.connection?.enableAudio(); }

  private async execute(id: number, role: 'PRESENTER' | 'VIEWER', quality: CaptureQuality): Promise<void> {
    let stream: MediaStream | undefined;
    if (role === 'PRESENTER') {
      stream = await this.capture.select(quality, () => {
        if (this.current(id)) void this.stop('A captura de vídeo ou áudio terminou no navegador. Inicie novamente e autorize as duas fontes.');
      });
      this.ensure(id);
      this.preview(stream);
      this.update({ phase: 'connecting', message: 'Captura válida. Criando a sala restrita e conectando...' });
      this.ownsRoom = true; // Also clean up an uncertain create/HTTP timeout.
      try {
        this.access = await this.api.create();
      } catch (error: unknown) {
        // The auxiliary contract rejects 4xx before allocating a room. In
        // particular, a 409 refers to somebody else's active attempt: never
        // delete that room just because this attempt was rejected.
        if (error instanceof CaptureApiFailure && error.status >= 400 && error.status < 500) {
          this.ownsRoom = false;
        }
        throw error;
      }
    } else {
      this.access = await this.api.viewer();
    }
    this.ensure(id);
    const connection = this.connectionFactory({
      disconnected: () => { if (this.current(id)) void this.stop('Conexão com a SFU encerrada. Entre novamente para retomar.'); },
      reconnecting: () => this.reconnecting(id),
      reconnected: () => { if (this.current(id)) void this.confirmRecovery(id); },
    });
    this.connection = connection;
    await connection.connect(this.access);
    this.ensure(id);
    if (stream) {
      this.ensureTracks(stream);
      this.update({ phase: 'publishing', message: 'Publicando vídeo e áudio da tela...' });
      await connection.publish(stream, quality);
      this.ensure(id);
      this.ensureTracks(stream);
      this.update({ phase: 'waiting', message: 'Aguardando a confirmação das duas tracks pela SFU...' });
      await this.confirm(id, true);
    } else {
      this.update({ phase: 'waiting', message: 'Conectado. Aguardando vídeo e áudio do apresentador confirmados na SFU.' });
      await this.confirm(id, false);
    }
    this.scheduleMonitor(id);
  }

  private async confirm(id: number, required: boolean, recovering = false): Promise<boolean> {
    const transport = this.transportRevision;
    for (let attempt = 0; attempt < (required ? 10 : 1); attempt++) {
      this.ensure(id);
      if (!recovering && this.view.phase === 'reconnecting') return false;
      const status = await this.api.status();
      this.ensure(id);
      // A status requested before transport loss must never clear Reconectando
      // or overwrite facts from a later connection with a stale HTTP response.
      if (transport !== this.transportRevision) return false;
      if (!status.exists || status.roomName !== this.access?.roomName) throw new Error('Room closed');
      if (status.live) {
        this.update({ phase: 'live', message: 'Vídeo e áudio publicados, confirmados pela SFU. Verifique o som audível no computador receptor.' });
        return true;
      }
      if (!recovering) this.update({ phase: 'waiting',
        message: 'Aguardando vídeo e áudio do apresentador confirmados na SFU. Uma track viva em silêncio continua válida.' });
      if (attempt < 9 && required) await this.delay(1_000);
    }
    if (required) throw new Error('Publication not confirmed');
    return true;
  }

  private scheduleMonitor(id: number): void {
    if (!this.current(id) || this.view.phase === 'reconnecting') return;
    this.monitor = setTimeout(() => {
      void (async () => {
        try {
          await this.confirm(id, false);
          this.scheduleMonitor(id);
        } catch {
          if (this.current(id)) void this.stop('Não foi possível confirmar a sala na SFU. Recursos locais liberados; verifique o backend e tente novamente.', true);
        }
      })();
    }, 5_000);
  }

  private reconnecting(id: number): void {
    if (!this.current(id) || this.reconnectDeadline) return;
    this.transportRevision += 1;
    clearTimeout(this.monitor);
    this.update({ phase: 'reconnecting', message: 'Reconectando à SFU por até 30 segundos...' });
    this.reconnectDeadline = setTimeout(() => {
      if (this.current(id)) void this.stop('A reconexão excedeu 30 segundos. Captura e conexão encerradas.', true);
    }, 30_000);
  }

  private async confirmRecovery(id: number): Promise<void> {
    try {
      if (!await this.confirm(id, true, true)) return;
      this.ensure(id);
      clearTimeout(this.reconnectDeadline);
      this.reconnectDeadline = undefined;
      this.scheduleMonitor(id);
    } catch {
      if (this.current(id)) void this.stop('A SFU não confirmou vídeo e áudio após a reconexão. Tente novamente.', true);
    }
  }

  private release(phase: CapturePhase, message: string): void {
    ++this.generation;
    clearTimeout(this.monitor);
    clearTimeout(this.reconnectDeadline);
    this.reconnectDeadline = undefined;
    this.capture.stop();
    this.preview(null);
    // Immediate local disconnect, followed by another after any in-flight connect.
    void this.connection?.disconnect().catch(() => undefined);
    this.update({ phase, message });
  }

  private async cleanup(): Promise<void> {
    if (this.cleaning) return this.cleaning;
    const cleanup = async (): Promise<void> => {
      await this.connection?.disconnect().catch(() => undefined);
      this.connection = undefined;
      this.access = undefined;
      if (this.ownsRoom) {
        try {
          await this.api.delete();
          this.ownsRoom = false;
          this.update({ cleanupPending: false });
        } catch {
          this.update({ cleanupPending: true,
            message: 'Recursos locais liberados; exclusão da sala ainda não confirmada. Mantenha o backend ativo e clique em Repetir limpeza.' });
        }
      }
    };
    this.cleaning = cleanup();
    try { await this.cleaning; } finally { this.cleaning = undefined; }
  }

  private current(id: number): boolean { return id === this.generation; }
  private ensure(id: number): void { if (!this.current(id)) throw new Error('Stopped'); }
  private ensureTracks(stream: MediaStream): void {
    if (!stream.getVideoTracks().some((track) => track.readyState === 'live')
      || !stream.getAudioTracks().some((track) => track.readyState === 'live')) {
      throw new Error('Required track ended');
    }
  }
  private update(value: Partial<CaptureView>): void { this.view = { ...this.view, ...value }; this.changed({ ...this.view }); }
}
