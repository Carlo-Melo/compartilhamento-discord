import { Component, computed, ElementRef, inject, OnDestroy, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LiveKitProofApi } from './livekit-proof-api';
import { LiveKitProofService } from './livekit-proof-service';
import { AppIcon } from '../../shared/components/app-icon';

@Component({
  selector: 'app-livekit-proof-page',
  imports: [RouterLink, AppIcon],
  providers: [LiveKitProofApi, LiveKitProofService],
  templateUrl: './livekit-proof-page.html',
})
export class LiveKitProofPage implements OnDestroy {
  readonly proof = inject(LiveKitProofService);
  // Presentation of check results only: cleanup can still fail after every check passed.
  readonly outcome = computed(() => {
    if (this.proof.running()) return 'running';
    const checks = this.proof.checks();
    if (checks.length === 0) return 'idle';
    if (checks.some((check) => check.status === 'failed')) return 'failed';
    return checks.every((check) => check.status === 'passed') ? 'passed' : 'idle';
  });
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private readonly audio = viewChild.required<ElementRef<HTMLAudioElement>>('audio');

  run(): void {
    void this.proof.run(this.video().nativeElement, this.audio().nativeElement);
  }

  ngOnDestroy(): void {
    this.proof.cancel();
  }
}
