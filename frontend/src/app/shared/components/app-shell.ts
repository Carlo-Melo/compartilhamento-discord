import { Component, ElementRef, inject } from '@angular/core';
import { AppIcon } from './app-icon';
import { AmbientOrb } from './ambient-orb';

@Component({
  selector: 'app-shell',
  imports: [AppIcon, AmbientOrb],
  styleUrl: './app-shell.css',
  template: `
    <a class="skip-link" href="#main-content" (click)="skipToContent($event)">Ir para o conteúdo</a>
    <header class="app-header">
      <div class="app-header-inner">
        <div class="brand" aria-label="Compartilhamento Discord">
          <span class="brand-symbol"><img src="/marca-preto-fosco.png" alt="" width="44" height="44" /></span>
          <span>Compartilhamento<span class="brand-context">PARA SUA COMUNIDADE NO DISCORD</span></span>
        </div>
        <span class="environment-label"><span class="status-dot" aria-hidden="true"></span>Ambiente local</span>
      </div>
    </header>
    <div class="app-content">
      <div class="ambient-field"><app-ambient-orb /></div>
      <div class="page-content"><ng-content /></div>
    </div>
    <footer class="app-footer">
      <span>Compartilhamento de tela e áudio</span>
      <span><app-icon name="shield" />Sem gravação de mídia</span>
    </footer>
  `,
})
export class AppShell {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  skipToContent(event: Event): void {
    event.preventDefault();
    this.host.nativeElement.querySelector<HTMLElement>('#main-content')?.focus();
  }
}
