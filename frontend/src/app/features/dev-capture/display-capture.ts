export type CaptureQuality = '720p' | '1080p';
export type CaptureFailureCode =
  | 'unsupported' | 'cancelled' | 'no-video' | 'no-audio' | 'ended' | 'capture-failed';

export class CaptureFailure extends Error {
  constructor(readonly code: CaptureFailureCode, message: string) {
    super(message);
    this.name = 'CaptureFailure';
  }
}

// Chromium options are hints. Only the user's native selection and the returned
// tracks determine the source and whether system audio is actually available.
interface SystemDisplayOptions extends DisplayMediaStreamOptions {
  systemAudio: 'include';
  windowAudio: 'system';
  monitorTypeSurfaces: 'include';
  selfBrowserSurface: 'exclude';
  preferCurrentTab: false;
}

/** Owns only browser display capture; it never requests microphone or camera. */
export class DisplayCapture {
  private revision = 0;
  private stream?: MediaStream;
  private removeListeners: (() => void)[] = [];

  constructor(
    private readonly devices: Pick<MediaDevices, 'getDisplayMedia'> | undefined = globalThis.navigator?.mediaDevices,
    private readonly secure: boolean = globalThis.isSecureContext,
  ) {}

  async select(quality: CaptureQuality, onEnded: () => void): Promise<MediaStream> {
    // Calling another selection also invalidates any unresolved native selector.
    // A selector cannot be dismissed programmatically; its late result is stopped.
    this.stop();
    const revision = this.revision;
    if (!this.secure || !this.devices?.getDisplayMedia) {
      throw new CaptureFailure('unsupported',
        'Captura indisponível. Abra o endereço HTTPS com certificado confiável no Chrome ou Edge do Windows.');
    }
    const [width, height] = quality === '1080p' ? [1920, 1080] : [1280, 720];
    const options: SystemDisplayOptions = {
      video: { width: { ideal: width }, height: { ideal: height },
        frameRate: { ideal: 30, max: 30 }, displaySurface: 'monitor' },
      audio: true,
      systemAudio: 'include', windowAudio: 'system', monitorTypeSurfaces: 'include',
      selfBrowserSurface: 'exclude', preferCurrentTab: false,
    };

    let stream: MediaStream;
    try {
      // This call occurs before any await unrelated to native capture, preserving
      // the click's transient user activation. No retry opens a selector itself.
      stream = await this.devices.getDisplayMedia(options);
    } catch (error: unknown) {
      throw captureFailure(error);
    }
    if (revision !== this.revision) {
      stopStream(stream);
      throw new CaptureFailure('cancelled', 'Seleção interrompida. Clique em iniciar para escolher novamente.');
    }

    try {
      const video = stream.getVideoTracks()[0];
      const audio = stream.getAudioTracks()[0];
      if (!video) {
        throw new CaptureFailure('no-video', 'Nenhum vídeo retornou. Escolha uma tela no seletor do navegador.');
      }
      if (!audio) {
        throw new CaptureFailure('no-audio',
          'Nenhum áudio retornou. Escolha Tela inteira e habilite compartilhar áudio do sistema. Se a opção não existir, registre a falha de compatibilidade.');
      }
      if (video.readyState !== 'live' || audio.readyState !== 'live') {
        throw new CaptureFailure('ended', 'A fonte de vídeo ou áudio já terminou. Inicie uma nova seleção de tela com áudio.');
      }
      // Publish only the mandatory pair, while retaining ownership of every
      // returned track until it is stopped or explicitly discarded here.
      for (const track of stream.getTracks()) {
        if (track !== video && track !== audio) {
          track.stop();
          stream.removeTrack(track);
        }
      }
      this.stream = stream;
      const ended = (): void => {
        if (revision !== this.revision || this.stream !== stream) return;
        this.stop();
        onEnded();
      };
      for (const track of [video, audio]) {
        track.addEventListener('ended', ended);
        this.removeListeners.push(() => track.removeEventListener('ended', ended));
      }
      // Handle an ended track even if it ended while listeners were installed.
      if (video.readyState !== 'live' || audio.readyState !== 'live') {
        this.stop();
        throw new CaptureFailure('ended', 'A fonte terminou durante a seleção. Escolha a tela e habilite o áudio novamente.');
      }
      return stream;
    } catch (error: unknown) {
      if (this.stream === stream) this.stop();
      else stopStream(stream);
      throw error;
    }
  }

  stop(): void {
    this.revision += 1;
    for (const remove of this.removeListeners) remove();
    this.removeListeners = [];
    const stream = this.stream;
    this.stream = undefined;
    if (stream) stopStream(stream);
  }
}

function stopStream(stream: MediaStream): void {
  for (const track of stream.getTracks()) track.stop();
}

function captureFailure(error: unknown): CaptureFailure {
  if (error instanceof CaptureFailure) return error;
  const name = error && typeof error === 'object' && 'name' in error ? error.name : undefined;
  if (name === 'NotAllowedError' || name === 'AbortError') {
    return new CaptureFailure('cancelled',
      'Captura cancelada ou permissão negada. Clique em iniciar e autorize uma tela com áudio no seletor.');
  }
  if (name === 'NotFoundError') {
    return new CaptureFailure('capture-failed', 'Nenhuma fonte de tela está disponível. Verifique os monitores e tente novamente.');
  }
  if (name === 'NotReadableError') {
    return new CaptureFailure('capture-failed',
      'O Windows não permitiu ler a fonte. Verifique as permissões de captura e tente selecionar outra tela.');
  }
  if (name === 'InvalidStateError') {
    return new CaptureFailure('capture-failed', 'A seleção exige um clique com a página ativa. Volte à página e clique em iniciar.');
  }
  return new CaptureFailure('capture-failed',
    'Não foi possível capturar a tela com áudio. Verifique HTTPS, permissões e a fonte escolhida e tente novamente.');
}
