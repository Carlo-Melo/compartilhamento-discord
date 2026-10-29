import { Routes } from '@angular/router';
import { HomePage } from './features/home/home-page';

// Included exclusively by the explicit capture-proof configuration.
export const routes: Routes = [
  { path: '', component: HomePage, data: { configuration: 'capture-proof' }, title: 'Início — Compartilhamento Discord' },
  { path: 'dev/capture/broadcast', data: { role: 'PRESENTER' },
    loadComponent: () => import('./features/dev-capture/capture-page').then((m) => m.CapturePage), title: 'T-03 — Apresentador' },
  { path: 'dev/capture/watch', data: { role: 'VIEWER' },
    loadComponent: () => import('./features/dev-capture/capture-page').then((m) => m.CapturePage), title: 'T-03 — Espectador' },
  { path: '**', redirectTo: '' },
];
