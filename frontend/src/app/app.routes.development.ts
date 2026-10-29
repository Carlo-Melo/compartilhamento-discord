import { Routes } from '@angular/router';
import { HealthPage } from './features/health/health-page';
import { HomePage } from './features/home/home-page';

// This file replaces app.routes.ts only in the explicit development build.
export const routes: Routes = [
  { path: '', component: HomePage, data: { configuration: 'development' }, title: 'Início — Compartilhamento Discord' },
  { path: 'health', component: HealthPage, title: 'Saúde da API — Compartilhamento Discord' },
  {
    path: 'dev/livekit',
    loadComponent: () => import('./features/dev-livekit/livekit-proof-page').then((m) => m.LiveKitProofPage),
    title: 'T-02 — Prova local LiveKit',
  },
  { path: '**', redirectTo: '' },
];
