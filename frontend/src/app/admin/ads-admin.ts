import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { I18nService } from '../core/i18n.service';
import { Envelope } from '../core/models';

interface AdSlotAdmin {
  key: string;
  page: string;
  enabled: boolean;
  adUnitId: string;
  unitLocked: boolean;
  effectiveUnitId: string;
  portalViews: number;
  served: boolean;
}

interface AdAdminView {
  environmentEnabled: boolean;
  publisherLocked: boolean;
  enabled: boolean;
  publisherId: string;
  effectivePublisherId: string;
  serving: boolean;
  slots: AdSlotAdmin[];
}

@Component({
  selector: 'app-admin-ads',
  imports: [RouterLink],
  template: `
    <section class="wrap max-w-3xl py-8">
      <a routerLink="/admin">{{ i18n.t('Volver') }}</a>
      <h1 class="mt-3 text-4xl">{{ i18n.t('Publicidad') }}</h1>
      <p class="mt-2 text-sm text-muted">{{ i18n.t('Estas cifras cuentan cuántas veces un espacio entró en la pantalla del portal. No son ingresos, clics facturables ni RPM de AdSense. Esos datos están en el informe del proveedor.') }}</p>
      @if (error()) { <p class="alert mt-4" role="alert">{{ i18n.live(error()) }}</p> }
      @if (message()) { <p class="ok mt-4" role="status">{{ i18n.live(message()) }}</p> }
      @if (view(); as current) {
        @if (!current.environmentEnabled) {
          <p class="alert mt-4">{{ i18n.t('ADSENSE_ENABLED está apagado. Los anuncios no se muestran aunque actives esta opción.') }}</p>
        } @else if (!current.serving) {
          <p class="alert mt-4">{{ i18n.t('Falta un Publisher ID válido o la publicidad global está apagada. No se carga el script ni se reserva espacio en el sitio.') }}</p>
        } @else {
          <p class="ok mt-4">{{ i18n.t('La publicidad puede mostrarse en los espacios activos, después de que la persona acepte.') }}</p>
        }
        <form class="mt-4 grid gap-4" (ngSubmit)="save()">
          <label class="flex items-center gap-2 font-semibold"><input type="checkbox" [checked]="current.enabled" (change)="patch({ enabled: checkbox($event) })"> {{ i18n.t('Publicidad global') }}</label>
          <label class="field"><span>{{ i18n.t('Identificador de editor') }}</span>
            <input [value]="current.publisherId" [disabled]="current.publisherLocked" (input)="patch({ publisherId: input($event) })" placeholder="ca-pub-" autocomplete="off">
          </label>
          @if (current.publisherLocked) { <p class="text-sm text-muted">{{ i18n.t('El Publisher ID lo define ADSENSE_PUBLISHER_ID.') }} {{ current.effectivePublisherId }}</p> }
          <div class="grid gap-3">
            @for (slot of current.slots; track slot.key) {
              <article class="card grid gap-2 p-4">
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <h2 class="text-xl">{{ pageName(slot.key) }}</h2>
                  <code class="text-sm">{{ slot.key }}</code>
                </div>
                @if (slot.page === 'resources') { <p class="text-sm text-muted">{{ i18n.t('Todavía no hay una sección editorial. Este espacio no se muestra.') }}</p> }
                <label class="flex items-center gap-2"><input type="checkbox" [checked]="slot.enabled" (change)="patchSlot(slot.key, { enabled: checkbox($event) })"> {{ i18n.t('Espacio activo') }}</label>
                <label class="field"><span>{{ i18n.t('Identificador del bloque') }}</span>
                  <input [value]="slot.adUnitId" [disabled]="slot.unitLocked" (input)="patchSlot(slot.key, { adUnitId: input($event) })" inputmode="numeric" autocomplete="off">
                </label>
                @if (slot.unitLocked) { <p class="text-sm text-muted">{{ i18n.t('El bloque lo define la variable de entorno.') }} {{ slot.effectiveUnitId }}</p> }
                <p class="text-sm text-muted">{{ i18n.t('Vistas del espacio en el portal') }}: {{ slot.portalViews }}</p>
              </article>
            }
          </div>
          <button class="btn btn-primary w-fit" type="submit" [disabled]="busy()">{{ i18n.t('Guardar publicidad') }}</button>
        </form>
      }
    </section>
  `,
})
export class AdminAdsPage {
  private readonly http = inject(HttpClient);
  readonly i18n = inject(I18nService);
  readonly view = signal<AdAdminView | null>(null);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() { this.load(); }

  pageName(key: string): string {
    const names: Record<string, string> = {
      'home-top': 'Inicio, entre secciones',
      'home-feed': 'Inicio, lista de ofertas',
      'job-search-inline': 'Resultados de búsqueda',
      'job-detail-bottom': 'Detalle de empleo',
      'category-content': 'Página de categoría',
      'info-content': 'Páginas informativas',
      'resource-content': 'Recursos profesionales',
    };
    return this.i18n.t(names[key] ?? key);
  }

  patch(change: Partial<AdAdminView>): void {
    this.view.update((current) => current ? { ...current, ...change } : current);
  }

  patchSlot(key: string, change: Partial<AdSlotAdmin>): void {
    this.view.update((current) => current ? {
      ...current,
      slots: current.slots.map((slot) => slot.key === key ? { ...slot, ...change } : slot),
    } : current);
  }

  checkbox(event: Event): boolean { return (event.target as HTMLInputElement).checked; }
  input(event: Event): string { return (event.target as HTMLInputElement).value.trim(); }

  save(): void {
    const current = this.view();
    if (!current) return;
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    this.http.put<Envelope<AdAdminView>>('/api/admin/ads', {
      enabled: current.enabled,
      publisherId: current.publisherId,
      slots: current.slots.map((slot) => ({ key: slot.key, enabled: slot.enabled, adUnitId: slot.adUnitId })),
    }).subscribe({
      next: (res) => { this.view.set(res.data); this.message.set('Publicidad guardada.'); this.busy.set(false); },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.busy.set(false); },
    });
  }

  private load(): void {
    this.http.get<Envelope<AdAdminView>>('/api/admin/ads').subscribe({
      next: (res) => this.view.set(res.data),
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }
}
