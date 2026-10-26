import { Component, ElementRef, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CaptureProofApi } from './capture-api';
import { CaptureSession, CaptureView } from './capture-session';
import { CaptureQuality, DisplayCapture } from './display-capture';
import { LiveKitCaptureConnection } from './livekit-capture-connection';

@Component({
  selector: 'app-capture-page', imports: [RouterLink], templateUrl: './capture-page.html',
  styles: ['video { width: 100%; max-height: 28rem; background: #111; object-fit: contain; } label { display: block; margin: 1rem 0; } input, select { font: inherit; padding: .4rem; max-width: 100%; } .actions { display: flex; flex-wrap: wrap; gap: .5rem; }'],
})
export class CapturePage implements OnDestroy {
  readonly role = inject(ActivatedRoute).snapshot.data['role'] as 'PRESENTER' | 'VIEWER';
  readonly presenter = this.role === 'PRESENTER';
  readonly view = signal<CaptureView>({ phase: 'idle', message: 'Pronto para iniciar.', busy: false, cleanupPending: false });
  readonly audioBlocked = signal(false);
  readonly reception = signal('Aguardando mídia do apresentador.');
  readonly secure = globalThis.isSecureContext;
  readonly phases = {
    idle: 'Pronto', selecting: 'Selecionando fonte', connecting: 'Conectando', publishing: 'Publicando',
    waiting: 'Aguardando mídia', live: 'Transmitindo', reconnecting: 'Reconectando', ended: 'Encerrado', error: 'Erro',
  };
  key = '';
  quality: CaptureQuality = '720p';
  private session?: CaptureSession;
  @ViewChild('video', { static: true }) private video!: ElementRef<HTMLVideoElement>;
  @ViewChild('audio', { static: true }) private audio!: ElementRef<HTMLAudioElement>;

  start(): void {
    if (!this.key.trim() || this.active()) return;
    const api = new CaptureProofApi(this.key.trim());
    this.key = '';
    this.audioBlocked.set(false);
    this.session = new CaptureSession(api, new DisplayCapture(),
      (events) => new LiveKitCaptureConnection(this.video.nativeElement, this.audio.nativeElement,
        events, (blocked) => this.audioBlocked.set(blocked), (message) => this.reception.set(message)),
      (view) => this.view.set(view),
      (stream) => {
        if (!this.presenter) return;
        const video = this.video.nativeElement;
        video.muted = true;
        video.srcObject = stream;
        if (stream) void video.play().catch(() => undefined);
      });
    void this.session.start(this.role, this.quality);
  }
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
  @HostListener('window:pagehide') onPageHide(): void { void this.session?.stop(); }
  ngOnDestroy(): void { void this.session?.stop(); }
}
