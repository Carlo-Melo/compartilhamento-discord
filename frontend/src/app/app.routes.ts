import { Routes } from '@angular/router';
import { HealthPage } from './features/health/health-page';
import { HomePage } from './features/home/home-page';

export const routes: Routes = [
  { path: '', component: HomePage, data: { configuration: 'production' }, title: 'Início — Compartilhamento Discord' },
  { path: 'health', component: HealthPage, title: 'Saúde da API — Compartilhamento Discord' },
  { path: '**', redirectTo: '' },
];
