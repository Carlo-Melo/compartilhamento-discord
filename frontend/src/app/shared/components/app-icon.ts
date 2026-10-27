import { Component, input } from '@angular/core';

const paths = {
  screen: ['M4 4h16v12H4z', 'M8 21h8M12 16v5'],
  play: ['m9 5 11 7-11 7z'],
  stop: ['M6 6h12v12H6z'],
  volume: ['m11 4-6 5H2v6h3l6 5z', 'M15 8a6 6 0 0 1 0 8M18 5a10 10 0 0 1 0 14'],
  expand: ['M8 3H3v5M16 3h5v5M21 16v5h-5M8 21H3v-5'],
  copy: ['M8 8h13v13H8z', 'M16 4V2H2v14h2'],
  shield: ['m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z', 'm8 12 3 3 5-6'],
  check: ['m5 12 4 4L19 6'],
  alert: ['m12 3 10 18H2z', 'M12 9v5M12 17h.01'],
  'arrow-right': ['M4 12h16m-6-6 6 6-6 6'],
  'arrow-left': ['M20 12H4m6-6-6 6 6 6'],
  activity: ['M2 12h4l3-8 6 16 3-8h4'],
  signal: ['M4 20v-4M9 20v-8M14 20V8M19 20V4'],
  key: ['M14 5a5 5 0 1 1-3 9l-7 7H2v-3l7-7a5 5 0 0 1 5-6z', 'M16 8h.01'],
  'chevron-down': ['m6 9 6 6 6-6'],
  link: ['m10 14 4-4', 'M8 16l-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0M16 8l1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0'],
  headphones: ['M3 14v-3a9 9 0 0 1 18 0v3', 'M3 12h4v8H3zM17 12h4v8h-4z'],
  loader: ['M21 12a9 9 0 1 1-9-9'],
} as const;

@Component({
  selector: 'app-icon',
  host: { 'aria-hidden': 'true' },
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false">
    @for (path of icons[name()]; track $index) { <path [attr.d]="path" /> }
  </svg>`,
})
export class AppIcon {
  readonly name = input<keyof typeof paths>('screen');
  readonly icons = paths;
}
