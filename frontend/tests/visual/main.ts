/** Isolated render fixtures. Never imported by application routes or production. */
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, Router, RouterLink, RouterOutlet, Routes } from '@angular/router';
import { NEVER } from 'rxjs';
import { HealthService } from '../../src/app/core/services/health-service';
import { CapturePage } from '../../src/app/features/dev-capture/capture-page';
import { CapturePhase } from '../../src/app/features/dev-capture/capture-session';
import { LiveKitProofPage } from '../../src/app/features/dev-livekit/livekit-proof-page';
import { HealthPage } from '../../src/app/features/health/health-page';
import { AppShell } from '../../src/app/shared/components/app-shell';

const states = [
  ['empty', 'Vazio'], ['checking', 'Validando'], ['valid', 'Chave válida'],
  ['wrong-role', 'Papel incorreto'], ['refused', 'Chave recusada'],
  ['unavailable', 'Indisponível'], ['connecting', 'Conectando'],
  ['waiting', 'Aguardando mídia'], ['reconnecting', 'Reconectando'],
  ['live', 'Transmitindo'], ['error', 'Erro'], ['ended', 'Encerrado'],
  ['cleanup', 'Limpeza pendente'],
] as const;
type FixtureState = typeof states[number][0];
type FixturePage = HealthPage | CapturePage | LiveKitProofPage;
const requestId = '00000000-0000-4000-8000-000000000015';
const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'health' },
  { path: 'health', component: HealthPage },
  { path: 'broadcast', data: { role: 'PRESENTER' }, component: CapturePage },
  { path: 'watch', data: { role: 'VIEWER' }, component: CapturePage },
  { path: 'livekit', component: LiveKitProofPage },
  { path: '**', redirectTo: 'health' },
];

@Component({
  selector: 'app-root',
  imports: [AppShell, RouterOutlet, RouterLink],
  styles: [`
    .fixture-toolbar { padding: var(--space-4) var(--page-gutter); background: var(--color-warning-bg); border-bottom: var(--border-width) solid var(--color-warning); }
    .fixture-toolbar > strong { display: block; color: var(--color-warning); margin-bottom: var(--space-3); }
    .fixture-controls { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4); }
    nav { display: flex; flex-wrap: wrap; gap: var(--space-3); }
    label { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
    select { width: auto; min-width: 0; }
    .references-passed { color: var(--color-success); }
    .references-failed { color: var(--color-danger); }
  `],
  template: `
    <aside class="fixture-toolbar" aria-label="Controles de evidência visual">
      <strong>Prévia visual · dados fictícios · sem mídia real</strong>
      <div class="fixture-controls">
        <nav aria-label="Página da prévia">
          <a routerLink="/health" [queryParams]="{ state: selected() }">Saúde</a>
          <a routerLink="/broadcast" [queryParams]="{ state: selected() }">Apresentador</a>
          <a routerLink="/watch" [queryParams]="{ state: selected() }">Espectador</a>
          <a routerLink="/livekit" [queryParams]="{ state: selected() }">LiveKit sintético</a>
        </nav>
        <label>Estado fictício
          <select [value]="selected()" (change)="choose($any($event.target).value)">
            @for (state of states; track state[0]) { <option [value]="state[0]">{{ state[1] }}</option> }
          </select>
        </label>
      </div>
      <p class="helper">Ações da aplicação estão inertes. Esta prévia verifica somente apresentação e não comprova captura, autorização ou transmissão.</p>
      <p class="helper" [class.references-passed]="referencesPreserved() === true" [class.references-failed]="referencesPreserved() === false" role="status" aria-live="polite">{{ referenceStatus() }}</p>
    </aside>
    <app-shell>
      <div inert><router-outlet (activate)="activate($event)" /></div>
    </app-shell>
  `,
})
class VisualFixtures {
  private readonly router = inject(Router);
  readonly states = states;
  readonly selected = signal<FixtureState>('empty');
  readonly referenceStatus = signal('Verificando referências de mídia…');
  readonly referencesPreserved = signal<boolean | null>(null);
  private page?: FixturePage;
  private mediaReferences?: { page: FixturePage; video: HTMLVideoElement; audio: HTMLAudioElement };

  activate(page: FixturePage): void {
    this.page = page;
    this.mediaReferences = undefined;
    this.referencesPreserved.set(null);
    this.referenceStatus.set('Verificando referências de mídia…');
    const state = new URLSearchParams(location.search).get('state');
    if (states.some(([value]) => value === state)) this.selected.set(state as FixtureState);
    // HealthPage.ngOnInit sets loading; apply the fixture after initial component creation.
    queueMicrotask(() => { if (this.page === page) this.apply(); });
  }

  choose(state: FixtureState): void {
    this.selected.set(state);
    void this.router.navigate([], { queryParams: { state }, queryParamsHandling: 'merge', replaceUrl: true });
    this.apply();
  }

  private apply(): void {
    const page = this.page;
    const state = this.selected();
    // Angular renders signal updates before this task checks the real DOM identities.
    setTimeout(() => { if (page && this.page === page) this.checkReferences(page); }, 0);
    if (page instanceof HealthPage) {
      page.state.set(['empty', 'checking', 'connecting', 'waiting', 'reconnecting'].includes(state)
        ? 'loading' : ['valid', 'live', 'ended'].includes(state) ? 'available' : 'unavailable');
      return;
    }
    if (page instanceof CapturePage) {
      this.capture(page, state);
      return;
    }
    if (page instanceof LiveKitProofPage) this.proof(page, state);
  }

  private checkReferences(page: FixturePage): void {
    if (page instanceof HealthPage) {
      this.referenceStatus.set('Página de saúde: sem elementos de vídeo/áudio.');
      this.referencesPreserved.set(null);
      return;
    }
    const host = document.querySelector(page instanceof CapturePage ? 'app-capture-page' : 'app-livekit-proof-page');
    const video = host?.querySelector('video');
    const audio = host?.querySelector('audio');
    if (!video || !audio) {
      this.referencesPreserved.set(false);
      this.referenceStatus.set('Falha: referência de vídeo/áudio ausente no DOM.');
      return;
    }
    if (!this.mediaReferences) this.mediaReferences = { page, video, audio };
    const preserved = this.mediaReferences.page === page
      && this.mediaReferences.video === video && this.mediaReferences.audio === audio;
    this.referencesPreserved.set(preserved);
    this.referenceStatus.set(preserved ? 'Referências de vídeo/áudio preservadas.' : 'Falha: referências de vídeo/áudio substituídas ao mudar o estado.');
  }

  private capture(page: CapturePage, state: FixtureState): void {
    page.key = '';
    page.previewVisible.set(false);
    page.audioBlocked.set(state === 'waiting');
    page.reception.set('Prévia fictícia: aguardando vídeo e áudio do apresentador. Nenhuma mídia foi recebida.');
    page.copyStatus.set('');
    page.events.set(state === 'empty' ? [] : [
      '[14:30:00] Acesso: mensagem fictícia de validação, sem credenciais.',
      '[14:30:01] Diagnóstico visual: nenhuma conexão ou captura foi iniciada.',
      `[14:30:02] Requisição fictícia: ${requestId}`,
    ]);
    page.credentialView.set({ status: 'empty', message: 'Cole a chave do seu papel. A validação é automática e não inicia a captura.' });
    const credentials: Partial<Record<FixtureState, Parameters<typeof page.credentialView.set>[0]>> = {
      checking: { status: 'checking', message: 'Validando a chave no backend…' },
      valid: { status: 'valid', message: `Chave de ${page.presenter ? 'apresentador' : 'espectador'} válida. Prévia visual com dados fictícios; início permanece bloqueado.` },
      'wrong-role': { status: 'wrong-role', message: 'A chave é válida para o outro papel. Troque a chave ou abra a página correta.' },
      refused: { status: 'invalid', message: 'Chave não aceita. Confira o valor no arquivo local e o endereço desta página.', requestId },
      unavailable: { status: 'unavailable', message: 'O backend não respondeu em 8 segundos. Confira os serviços e valide a chave novamente.', requestId },
    };
    if (credentials[state]) page.credentialView.set(credentials[state]);
    const phases: Partial<Record<FixtureState, CapturePhase>> = {
      connecting: 'connecting', waiting: 'waiting', reconnecting: 'reconnecting',
      live: 'live', error: 'error', ended: 'ended', cleanup: 'error',
    };
    const messages: Partial<Record<FixtureState, string>> = {
      connecting: 'Conectando à sala autorizada. Aguarde a confirmação da conexão.',
      waiting: 'Conexão preparada. Aguardando vídeo e áudio do apresentador.',
      reconnecting: 'Conexão interrompida. Tentando restabelecer a transmissão por até 30 segundos.',
      live: 'Exemplo visual de publicação confirmada. Nenhuma transmissão real está em execução.',
      error: 'O serviço de mídia está indisponível. Confira os serviços e tente novamente após a limpeza.',
      ended: 'Transmissão encerrada. As tracks e conexões foram liberadas neste exemplo fictício.',
      cleanup: 'A tentativa falhou. A limpeza remota ainda não foi confirmada; repita a limpeza antes de iniciar outra prova.',
    };
    page.view.set({
      phase: phases[state] ?? 'idle', message: messages[state] ?? 'Pronto para iniciar.',
      busy: ['connecting', 'waiting', 'reconnecting'].includes(state),
      cleanupPending: state === 'cleanup',
      errorCode: ['error', 'cleanup'].includes(state) ? 'SFU_UNAVAILABLE' : undefined,
      requestId: ['error', 'cleanup'].includes(state) ? requestId : undefined,
      cleanupMessage: state === 'cleanup' ? `Limpeza não confirmada. Mantenha o backend disponível e use Repetir limpeza. Requisição fictícia: ${requestId}` : undefined,
    });
  }

  private proof(page: LiveKitProofPage, state: FixtureState): void {
    const running = ['checking', 'connecting', 'waiting', 'reconnecting'].includes(state);
    const failed = ['error', 'refused', 'unavailable', 'wrong-role'].includes(state);
    page.proof.running.set(running);
    page.proof.status.set(state === 'empty' ? 'Pronto para executar a prova local.'
      : state === 'cleanup' ? 'Prévia fictícia: limpeza não confirmada. Mantenha o backend auxiliar ativo.'
      : running ? 'Prévia fictícia: verificações em execução. Nenhuma operação real iniciada.'
      : failed ? `Prévia fictícia: prova não concluída. Confira o serviço de mídia. Requisição ${requestId}.`
      : 'Prévia fictícia: resultados de exemplo. Não constituem prova de transmissão.');
    page.proof.checks.set(state === 'empty' ? [] : [
      { label: 'Criar e consultar sala', status: 'passed', detail: 'Dado fictício para avaliar a apresentação de sucesso.' },
      { label: 'Receber vídeo e áudio', status: running ? 'running' : failed ? 'failed' : 'passed',
        detail: failed ? `Dado fictício: mídia indisponível. Código da requisição: ${requestId}. Confira os serviços antes de repetir.` : 'Dado fictício para avaliar o estado da verificação. Nenhum byte de mídia foi recebido.' },
    ]);
    page.proof.playback.set(state === 'waiting' ? 'Prévia fictícia: o navegador bloqueou a reprodução. Clique em Ouvir áudio.' : 'Sem mídia real nesta prévia visual.');
    page.proof.media.set('Nenhuma mídia recebida. Os resultados exibidos são dados fictícios de apresentação.');
    page.proof.transport.set('Nenhum transporte RTC iniciado. SFU e API não são acessados pelo harness visual.');
  }
}

bootstrapApplication(VisualFixtures, {
  providers: [
    // Fail closed: no HttpClient request in this isolated entrypoint reaches a backend.
    provideHttpClient(withInterceptors([() => NEVER])),
    { provide: HealthService, useValue: { check: () => NEVER } },
    provideRouter(routes),
  ],
}).catch((error: unknown) => console.error(error));
