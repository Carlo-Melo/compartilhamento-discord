import { Component, ElementRef, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CaptureProofApi } from './capture-api';
import { CaptureSession, CaptureView } from './capture-session';
import { CaptureQuality, DisplayCapture } from './display-capture';
import { LiveKitCaptureConnection } from './livekit-capture-connection';
import { CaptureCredential, CredentialView } from './capture-credential';
import { AppIcon } from '../../shared/components/app-icon';
import { ProofIcePolicy } from './ice-diagnostics';

@Component({
  selector: 'app-capture-page', imports: [RouterLink, AppIcon], templateUrl: './capture-page.html',
})
export class CapturePage implements OnDestroy {
  readonly role = inject(ActivatedRoute).snapshot.data['role'] as 'PRESENTER' | 'VIEWER';
  readonly presenter = this.role === 'PRESENTER';
  readonly view = signal<CaptureView>({ phase: 'idle', message: 'Pronto para iniciar.', busy: false, cleanupPending: false });
  readonly audioBlocked = signal(false);
  readonly reception = signal('Aguardando mídia do apresentador.');
  readonly transport = signal('Par ICE ainda não observado. Sinalização não comprova relay.');
  readonly secure = globalThis.isSecureContext;
  readonly credentialView = signal<CredentialView>({ status: 'empty', message: 'Cole a chave do seu papel. A validação é automática e não inicia a captura.' });
  readonly previewVisible = signal(false);
  readonly events = signal<string[]>([]);
  readonly copyStatus = signal('');
  readonly displayAvailable = typeof navigator.mediaDevices?.getDisplayMedia === 'function';
  readonly phases = {
    idle: 'Pronto', selecting: 'Selecionando fonte', connecting: 'Conectando', publishing: 'Publicando',
    waiting: 'Aguardando mídia', live: 'Transmitindo', reconnecting: 'Reconectando', ended: 'Encerrado', error: 'Erro',
  };
  key = '';
  quality: CaptureQuality = '720p';
  icePolicy: ProofIcePolicy = 'all';
  // Render HTTPS guidance even where randomUUID is unavailable in an insecure context.
  private readonly viewerNonce = globalThis.crypto?.randomUUID?.();
  private session?: CaptureSession;
  private readonly credential = new CaptureCredential(this.role, (key) => new CaptureProofApi(key, this.viewerNonce), (view) => {
    this.credentialView.set(view);
    this.record(`Acesso: ${view.message}${view.requestId ? ` [requisição ${view.requestId}]` : ''}`);
  });
  @ViewChild('video', { static: true }) private video!: ElementRef<HTMLVideoElement>;
  @ViewChild('audio', { static: true }) private audio!: ElementRef<HTMLAudioElement>;

  start(): void {
    if (this.active()) return;
    const api = this.credential.take();
    if (!api) return;
    this.key = '';
    this.audioBlocked.set(false);
    this.transport.set('Par ICE ainda não observado. Sinalização não comprova relay.');
    this.session = new CaptureSession(api, new DisplayCapture(),
      (events) => new LiveKitCaptureConnection(this.video.nativeElement, this.audio.nativeElement,
        events, (blocked) => this.audioBlocked.set(blocked), (message) => this.reception.set(message),
        this.icePolicy, (message) => { this.transport.set(message); this.record(message); }),
      (view) => {
        this.view.set(view);
        this.record(`${this.phases[view.phase]}: ${view.message}${view.errorCode ? ` [${view.errorCode}]` : ''}${view.requestId ? ` [requisição ${view.requestId}]` : ''}`);
        if (view.cleanupMessage) this.record(view.cleanupMessage);
      },
      (stream) => {
        if (!this.presenter) return;
        this.previewVisible.set(stream !== null);
        const video = this.video.nativeElement;
        video.muted = true;
        video.srcObject = stream;
        if (stream) void video.play().catch(() => undefined);
      });
    void this.session.start(this.role, this.quality);
  }
  keyChanged(value: string): void { this.key = value; this.credential.change(value); }
  validateKey(): void { this.credential.change(this.key, true); }
  ready(): boolean { return this.credentialView().status === 'valid' && this.credential.ready(); }
  credentialError(): boolean { return ['invalid', 'wrong-role', 'unavailable'].includes(this.credentialView().status); }
  active(): boolean {
    return this.view().busy || this.view().cleanupPending
      || !['idle', 'ended', 'error'].includes(this.view().phase);
  }
  stop(): void { void this.session?.stop(); }
  cleanup(): void { void this.session?.retryCleanup(); }
  enableAudio(): void {
    void this.session?.enableAudio().catch(() => this.audioBlocked.set(true));
  }
  volume(value: string): void { this.audio.nativeElement.volume = Number(value) / 100; }
  fullscreen(): void { void this.video.nativeElement.requestFullscreen().catch(() => undefined); }
  async copyDiagnostics(): Promise<void> {
    const report = [`Diagnóstico local de captura — ${this.presenter ? 'apresentador' : 'espectador'}`,
      `HTTPS seguro: ${this.secure ? 'sim' : 'não'}; API de captura disponível: ${this.displayAvailable ? 'sim' : 'não'}`,
      `Política ICE: ${this.icePolicy === 'relay' ? 'exigir relay' : 'padrão SDK/SFU'}`, this.transport(),
      ...this.events()].join('\n');
    try { await navigator.clipboard.writeText(report); this.copyStatus.set('Diagnóstico copiado. Não contém chaves, tokens ou conteúdo da tela.'); }
    catch { this.copyStatus.set('O navegador não permitiu copiar. Selecione o texto do histórico abaixo.'); }
  }
  private record(message: string): void {
    // Only locally composed status messages, allowlisted codes and UUIDs enter this volatile log.
    const previous = this.events().at(-1)?.slice(11);
    if (previous === message) return;
    const time = new Date().toLocaleTimeString('pt-BR', { hour12: false });
    this.events.update((events) => [...events, `[${time}] ${message}`].slice(-40));
  }
  @HostListener('window:pagehide') onPageHide(): void { void this.session?.stop(); }
  ngOnDestroy(): void { this.credential.dispose(); void this.session?.stop(); }
}
