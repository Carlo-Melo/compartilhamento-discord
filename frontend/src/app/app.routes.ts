import { Routes } from '@angular/router';
import { HealthPage } from './features/health/health-page';

export const routes: Routes = [
  { path: '', component: HealthPage, title: 'Ambiente local — Compartilhamento Discord' },
  { path: '**', redirectTo: '' },
];
