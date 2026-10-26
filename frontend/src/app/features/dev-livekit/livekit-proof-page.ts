import { Component, ElementRef, inject, OnDestroy, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LiveKitProofApi } from './livekit-proof-api';
import { LiveKitProofService } from './livekit-proof-service';

@Component({
  selector: 'app-livekit-proof-page',
  imports: [RouterLink],
  providers: [LiveKitProofApi, LiveKitProofService],
  templateUrl: './livekit-proof-page.html',
  styles: [`
    main { max-width: 68rem; margin: 1.5rem auto; }
    .notice { padding: 1rem; border-left: 4px solid #845700; background: #fff5d8; }
    .actions { display: flex; flex-wrap: wrap; gap: .6rem; margin: 1rem 0; }
    video { display: block; width: min(100%, 640px); min-height: 180px; background: #16324f; }
    audio { width: min(100%, 640px); }
    li { margin: .8rem 0; }
    li p { margin: .2rem 0; }
    .passed { color: #14613a; } .failed { color: #9c2626; }
  `],
})
export class LiveKitProofPage implements OnDestroy {
  readonly proof = inject(LiveKitProofService);
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private readonly audio = viewChild.required<ElementRef<HTMLAudioElement>>('audio');

  run(): void {
    void this.proof.run(this.video().nativeElement, this.audio().nativeElement);
  }

  ngOnDestroy(): void {
    this.proof.cancel();
  }
}
