import { Routes } from '@angular/router';

// Included exclusively by the explicit capture-proof configuration.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dev/capture/broadcast' },
  { path: 'dev/capture/broadcast', data: { role: 'PRESENTER' },
    loadComponent: () => import('./features/dev-capture/capture-page').then((m) => m.CapturePage), title: 'T-03 — Apresentador' },
  { path: 'dev/capture/watch', data: { role: 'VIEWER' },
    loadComponent: () => import('./features/dev-capture/capture-page').then((m) => m.CapturePage), title: 'T-03 — Espectador' },
  { path: '**', redirectTo: '' },
];
