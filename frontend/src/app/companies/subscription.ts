import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { I18nService } from '../core/i18n.service';
import { Envelope } from '../core/models';

@Component({
  selector: 'app-subscription',
  imports: [RouterLink],
  template: `
    <section class="wrap max-w-3xl py-8">
      <a routerLink="/empresa">{{ i18n.t('Volver') }}</a>
      <h1 class="mt-3 text-4xl">{{ i18n.t('Suscripción y planes') }}</h1>
      <p class="mt-2 text-muted">{{ i18n.t('El precio sale de la configuración del portal. Volver de PayPal no activa el plan: hace falta la confirmación del webhook.') }}</p>
      @if (loading()) { <p class="mt-4" aria-live="polite">{{ i18n.t('Cargando planes…') }}</p> }
      @if (error()) { <p class="alert mt-4" role="alert">{{ i18n.live(error()) }}</p> }
      @if (notice()) { <p class="ok mt-4" role="status">{{ i18n.live(notice()) }}</p> }
      @if (view(); as current) {
        <article class="card mt-4 grid gap-1 p-4">
          <p>{{ i18n.t('Plan en uso:') }} {{ current.effectivePlanName }}</p>
          <p>{{ i18n.t('Estado de la suscripción:') }} {{ i18n.label(current.status) }}</p>
          <p>{{ i18n.t('Vigente hasta:') }} {{ current.endsAt ? i18n.date(current.endsAt) : i18n.t('Sin fecha de vencimiento') }}</p>
          @if (current.latestPaymentStatus) { <p>{{ i18n.t('Último evento de pago:') }} {{ i18n.label(current.latestPaymentStatus) }}</p> }
        </article>
        @if (!current.gatewayConfigured) {
          <p class="alert mt-4">{{ i18n.t('PayPal no está configurado en este entorno. No se iniciará ningún cobro ni se marcará un pago como exitoso. Un administrador puede asignar el plan desde el panel.') }}</p>
        }
        <div class="mt-4 grid gap-3">
          @for (plan of current.plans; track plan.code) {
            <article class="card grid gap-2 p-4">
              <h2 class="text-2xl">{{ plan.name }}</h2>
              @if (plan.description) { <p>{{ plan.description }}</p> }
              <p class="font-semibold">{{ i18n.money(plan.priceAmount, plan.currency) }} <span class="text-sm font-normal text-muted">/ {{ i18n.t('mes') }}</span></p>
              <p class="text-sm text-muted">{{ plan.maxActiveJobs }} {{ i18n.t('ofertas activas') }}@if (plan.candidateFilters) { · {{ i18n.t('filtros de candidatos') }} }@if (plan.advancedStats) { · {{ i18n.t('estadísticas') }} }@if (plan.premiumProfile) { · {{ i18n.t('perfil destacado') }} }</p>
              @if (plan.priceAmount > 0 && current.memberRole === 'OWNER' && !(current.effectivePlanCode === plan.code && current.status === 'ACTIVE')) {
                <button class="btn btn-primary w-fit" type="button" [disabled]="busy()" (click)="checkout(plan.code)">{{ i18n.t('Contratar') }}</button>
              }
            </article>
          }
        </div>
        @if (current.memberRole === 'OWNER' && (current.effectivePlanCode !== 'FREE' || current.status === 'PENDING')) {
          <button class="btn btn-ghost mt-4" type="button" [disabled]="busy()" (click)="cancel()">{{ current.status === 'PENDING' ? i18n.t('Cancelar contratación pendiente') : i18n.t('Cancelar suscripción') }}</button>
        } @else if (current.memberRole !== 'OWNER') {
          <p class="mt-4 text-sm text-muted">{{ i18n.t('Solo quien administra la empresa puede contratar o cancelar.') }}</p>
        }
      }
    </section>
  `,
})
export class SubscriptionPage {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly i18n = inject(I18nService);
  readonly view = signal<SubscriptionView | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  constructor() {
    const result = this.route.snapshot.queryParamMap.get('resultado');
    if (result === 'retorno') this.notice.set(this.i18n.t('Volviste desde PayPal. El plan sigue igual hasta que PayPal confirme el pago.'));
    if (result === 'cancelado') this.notice.set(this.i18n.t('Cancelaste el proceso en PayPal. No se registró un pago.'));
    this.load(result === 'retorno');
  }

  checkout(planCode: string): void {
    this.busy.set(true);
    this.error.set(null);
    const origin = window.location.origin;
    this.http.post<Envelope<{ redirectUrl: string; message: string }>>('/api/company/subscription/checkout', {
      planCode,
      returnUrl: `${origin}/empresa/suscripcion?resultado=retorno`,
      cancelUrl: `${origin}/empresa/suscripcion?resultado=cancelado`,
    }).subscribe({
      next: (res) => {
        this.notice.set(res.data.message || this.i18n.t('Redirigiendo a PayPal…'));
        if (res.data.redirectUrl) window.location.assign(res.data.redirectUrl);
        else this.busy.set(false);
      },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.busy.set(false); },
    });
  }

  cancel(): void {
    this.busy.set(true);
    this.http.post<Envelope<unknown>>('/api/company/subscription/cancel', {}).subscribe({
      next: (res) => { this.notice.set(res.message); this.busy.set(false); this.load(false); },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.busy.set(false); },
    });
  }

  private load(poll: boolean): void {
    this.http.get<Envelope<SubscriptionView>>('/api/company/subscription').subscribe({
      next: (res) => {
        this.view.set(res.data);
        this.loading.set(false);
        if (res.data.latestPaymentStatus === 'PAID' || res.data.latestPaymentStatus === 'ACTIVATED') {
          if (res.data.effectivePlanCode !== 'FREE' && res.data.status === 'ACTIVE') this.notice.set(this.i18n.t('PayPal confirmó el pago y el plan ya está activo.'));
        } else if (res.data.latestPaymentStatus === 'FAILED') {
          this.notice.set(this.i18n.t('El último intento de pago fue rechazado. El plan de pago no se activó por ese intento.'));
        }
        if (poll && res.data.latestPaymentStatus === 'CHECKOUT') {
          window.setTimeout(() => this.load(false), 4000);
        }
      },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.loading.set(false); },
    });
  }
}

interface SubscriptionView {
  effectivePlanCode: string; effectivePlanName: string; status: string; endsAt: string | null; gatewayConfigured: boolean;
  memberRole: string; latestPaymentStatus: string | null;
  plans: { code: string; name: string; description: string | null; priceAmount: number; currency: string; maxActiveJobs: number; candidateFilters: boolean; advancedStats: boolean; premiumProfile: boolean }[];
}
