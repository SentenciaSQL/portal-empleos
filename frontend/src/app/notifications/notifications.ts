import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { I18nService } from '../core/i18n.service';
import { Envelope } from '../core/models';

@Component({
  selector: 'app-notifications',
  imports: [RouterLink],
  template: `
    <section class="wrap max-w-2xl py-8">
      <div class="flex items-center justify-between"><h1 class="text-4xl">{{ i18n.t('Avisos') }}</h1><button class="btn btn-ghost" type="button" (click)="readAll()">{{ i18n.t('Marcar leídos') }}</button></div>
      <div class="mt-4 grid gap-3">
        @for (item of items(); track item.id) {
          <article class="card p-4" [class.opacity-60]="item.readAt">
            <h2 class="text-lg">{{ item.title }}</h2>
            <p>{{ item.body }}</p>
            <p class="text-sm text-muted">{{ i18n.date(item.createdAt) }}</p>
            @if (item.link) { <a [routerLink]="item.link">{{ i18n.t('Abrir') }}</a> }
            @if (!item.readAt) { <button class="btn btn-ghost mt-2" type="button" (click)="read(item.id)">{{ i18n.t('Marcar leído') }}</button> }
          </article>
        } @empty { <p>{{ i18n.t('No tienes avisos.') }}</p> }
      </div>
    </section>
  `,
})
export class NotificationsPage {
  private readonly http = inject(HttpClient);
  readonly i18n = inject(I18nService);
  readonly items = signal<Notice[]>([]);
  constructor() { this.load(); }
  read(id: string): void { this.http.post(`/api/notifications/${id}/read`, {}).subscribe(() => this.load()); }
  readAll(): void { this.http.post('/api/notifications/read-all', {}).subscribe(() => this.load()); }
  private load(): void { this.http.get<Envelope<{ items: Notice[] }>>('/api/notifications').subscribe((res) => this.items.set(res.data.items)); }
}
interface Notice { id: string; title: string; body: string; link: string | null; readAt: string | null; createdAt: string; }
