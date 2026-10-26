import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HealthService } from '../../core/services/health-service';

type HealthState = 'loading' | 'available' | 'unavailable';

@Component({
  selector: 'app-health-page',
  templateUrl: './health-page.html',
})
export class HealthPage implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly state = signal<HealthState>('loading');

  ngOnInit(): void {
    this.checkHealth();
  }

  checkHealth(): void {
    this.state.set('loading');
    this.healthService.check().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => this.state.set(response?.status === 'UP' ? 'available' : 'unavailable'),
      error: () => this.state.set('unavailable'),
    });
  }
}
