import { Component, ElementRef, HostListener, ViewChild, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AppIcon } from '../../shared/components/app-icon';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, AppIcon],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage {
  readonly currentCard = signal(0);
  readonly cardCount = 3;
  @ViewChild('carousel') private carousel?: ElementRef<HTMLElement>;

  @HostListener('window:resize')
  onResize(): void {
    if (this.carousel) this.showCard(this.currentCard(), this.carousel.nativeElement);
  }

  showCard(index: number, carousel: HTMLElement): void {
    const card = carousel.children.item(index) as HTMLElement | null;
    if (!card) return;
    const left = carousel.scrollLeft + card.getBoundingClientRect().left - carousel.getBoundingClientRect().left
      - (carousel.clientWidth - card.clientWidth) / 2;
    carousel.scrollTo({ left });
    this.currentCard.set(index);
  }

  updateCurrentCard(carousel: HTMLElement): void {
    const center = carousel.getBoundingClientRect().left + carousel.clientWidth / 2;
    let nearest = 0;
    let distance = Infinity;
    Array.from(carousel.children).forEach((child, index) => {
      const rect = child.getBoundingClientRect();
      const candidate = Math.abs(rect.left + rect.width / 2 - center);
      if (candidate < distance) { nearest = index; distance = candidate; }
    });
    this.currentCard.set(nearest);
  }

  readonly configuration = inject(ActivatedRoute).snapshot.data['configuration'] as 'production' | 'development' | 'capture-proof';
}
