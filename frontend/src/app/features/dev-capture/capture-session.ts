import { CaptureFailure, CaptureQuality, DisplayCapture } from './display-capture';

/** Carries only an HTTP status; provider bodies and credentials are never exposed. */
export class CaptureApiFailure extends Error {
  readonly requestId?: string;
  readonly code: string;
  constructor(readonly status: number, code = 'HTTP_ERROR', requestId?: string) {
    super('A API recusou ou não concluiu a operação de captura.');
    this.name = 'CaptureApiFailure';
    this.code = ['HTTP_ERROR', 'REQUEST_TIMEOUT', 'NETWORK_ERROR', 'INVALID_RESPONSE',
      'CAPTURE_PROOF_REJECTED', 'SFU_UNAVAILABLE', 'MEDIA_UNAVAILABLE', 'INTERNAL_ERROR',
      'CAPTURE_VIEWERS_FULL', 'CAPTURE_PROOF_CAPACITY', 'CAPTURE_PROOF_EXPIRED', 'CAPTURE_PROOF_VIEWER_INVALID',
      'NOT_FOUND', 'INVALID_REQUEST', 'FORBIDDEN', 'ORIGIN_INVALID', 'CSRF_INVALID'].includes(code) ? code : 'HTTP_ERROR';
    this.requestId = requestId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(requestId)
      ? requestId : undefined;
  }
}

export function captureApiMessage(error: CaptureApiFailure, viewer = false): string {
  if (viewer && ['CAPTURE_VIEWERS_FULL', 'CAPTURE_PROOF_CAPACITY'].includes(error.code)) return 'As nove vagas desta geração já foram reservadas. Reentre sem recarregar esta página para reconectar; para um novo grupo, o apresentador precisa encerrar e iniciar outra prova.';
  if (error.code === 'REQUEST_TIMEOUT') return 'O backend não respondeu em 8 segundos. Confira os serviços e valide a chave novamente.';
  if (error.status === 0) return 'Não foi possível alcançar o backend. Confira a conexão HTTPS e se o servidor está rodando.';
  if (error.status === 401 || error.status === 403) return 'Acesso recusado. Confira a chave do papel, o endereço desta página e valide novamente.';
  if (error.status === 404) return 'API de captura não encontrada. Confirme que o backend está no modo capture-proof e reinicie o frontend se necessário.';
  if (error.status === 409) return viewer
    ? 'Ainda não há transmissão disponível. Peça ao apresentador para iniciar e tente entrar novamente.'
    : 'Já existe uma sala ou operação pendente. Encerre a tentativa anterior antes de iniciar outra.';
  if (error.status === 429) return 'Limite de tentativas desta execução atingido. Encerre a prova e reinicie o backend.';
  if (error.status === 503) return 'O backend respondeu, mas o serviço de mídia está indisponível. Confira o LiveKit no Docker.';
  if (error.code === 'INVALID_RESPONSE') return 'O servidor retornou uma resposta inesperada. Confira se o proxy aponta para o backend da captura.';
  return 'O backend não concluiu a operação. Confira os serviços e use o código de diagnóstico para investigar.';
}

export interface CaptureAccess {
  roomName: string; identity: string; presenterIdentity: string; role: 'PRESENTER' | 'VIEWER';
  serverUrl: string; token: string; expiresAt: string;
}
export interface CaptureStatus {
  exists: boolean; live: boolean; roomName: string | null;
  presenterIdentity: string | null; viewerCount: number; viewerSlotsUsed: number; maxViewers: number;
}
export interface CaptureApi {
  create(): Promise<CaptureAccess>;
  viewer(): Promise<CaptureAccess>;
  status(): Promise<CaptureStatus>;
  delete(): Promise<void>;
  leave(): Promise<void>;
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
  errorCode?: string; requestId?: string; cleanupMessage?: string;
  viewerCount?: number; viewerSlotsUsed?: number;
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
  private viewerReserved = false;
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
    if (this.view.busy || this.connection || this.ownsRoom || this.viewerReserved || this.cleaning) return;
    const id = ++this.generation;
    this.update({ busy: true, phase: role === 'PRESENTER' ? 'selecting' : 'connecting',
      errorCode: undefined, requestId: undefined, cleanupMessage: undefined,
      message: role === 'PRESENTER' ? 'Selecione a tela e habilite o compartilhamento de áudio no navegador.' : 'Conectando como espectador...' });
    // execute() invokes getDisplayMedia synchronously, still inside the click gesture.
    const task = this.execute(id, role, quality);
    this.pending = task;
    try {
      await task;
    } catch (error: unknown) {
      if (this.current(id)) {
        const phase = this.view.phase;
        const message = error instanceof CaptureFailure ? error.message
          : error instanceof CaptureApiFailure ? captureApiMessage(error, role === 'VIEWER')
          : phase === 'connecting' ? 'A conexão WebRTC não foi concluída. Confira a sinalização HTTPS/WSS e a conectividade de mídia do LiveKit.'
          : phase === 'publishing' ? 'A conexão abriu, mas não foi possível publicar vídeo e áudio. Verifique se a fonte continua compartilhada.'
          : 'A SFU não confirmou vídeo e áudio publicados. A tentativa foi encerrada; confira o LiveKit e tente novamente.';
        this.release('error', message);
        this.update({ errorCode: error instanceof CaptureFailure ? `CAPTURE_${error.code.replace(/-/g, '_').toUpperCase()}`
          : error instanceof CaptureApiFailure ? error.code : `MEDIA_${phase.toUpperCase()}_FAILED`,
          requestId: error instanceof CaptureApiFailure ? error.requestId : undefined });
      }
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
      this.viewerReserved = true; // Also remove a participant after an uncertain HTTP result.
      try { this.access = await this.api.viewer(); }
      catch (error: unknown) {
        if (error instanceof CaptureApiFailure && error.status >= 400 && error.status < 500) this.viewerReserved = false;
        throw error;
      }
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
      this.update({ viewerCount: status.viewerCount, viewerSlotsUsed: status.viewerSlotsUsed });
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
          this.update({ cleanupPending: false, cleanupMessage: undefined });
        } catch {
          this.update({ cleanupPending: true,
            cleanupMessage: 'Recursos locais liberados; exclusão da sala ainda não confirmada. Mantenha o backend ativo e clique em Repetir limpeza.' });
        }
      } else if (this.viewerReserved) {
        try {
          await this.api.leave();
          this.viewerReserved = false;
          this.update({ cleanupPending: false, cleanupMessage: undefined });
        } catch {
          this.update({ cleanupPending: true,
            cleanupMessage: 'Recursos locais liberados; saída da SFU ainda não confirmada. Mantenha o backend ativo e clique em Repetir limpeza. A vaga permanece reservada para esta aba até o fim da geração.' });
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
