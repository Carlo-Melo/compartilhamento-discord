import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppShell } from './shared/components/app-shell';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppShell],
  template: '<app-shell><router-outlet /></app-shell>',
})
export class App {}
