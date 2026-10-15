import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { I18nService } from '../core/i18n.service';
import { AdConfigurationService } from './ad-configuration.service';
import { AdConsentService } from './ad-consent.service';

@Component({
  selector: 'app-ad-consent',
  template: `
    @if (visible()) {
      <section class="border-b border-line" role="region" [attr.aria-label]="i18n.t('Publicidad')">
        <div class="wrap flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
          <p class="max-w-3xl text-sm">{{ i18n.t('Este sitio puede mostrar anuncios de Google. Si aceptas, se carga el script de AdSense. Si rechazas, no se carga y el portal sigue igual.') }}</p>
          <div class="flex shrink-0 gap-2">
            <button class="btn btn-primary" type="button" (click)="consent.grant()">{{ i18n.t('Aceptar publicidad') }}</button>
            <button class="btn btn-ghost" type="button" (click)="consent.deny()">{{ i18n.t('Rechazar publicidad') }}</button>
          </div>
        </div>
      </section>
    }
  `,
})
export class AdConsentBanner {
  readonly i18n = inject(I18nService);
  readonly consent = inject(AdConsentService);
  private readonly ads = inject(AdConfigurationService);
  private readonly path = signal(inject(Router).url);

  constructor() {
    const router = inject(Router);
    router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => this.path.set(router.url));
  }

  readonly visible = computed(() => {
    const config = this.ads.config();
    if (!config?.enabled || this.consent.choice()) return false;
    return !/^\/(entrar|registro|verificar-correo|recuperar|restablecer|admin|candidato|empresa)(\/|$)/.test(this.path().split('?')[0]);
  });
}
