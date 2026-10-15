import { Component, inject } from '@angular/core';
import { I18nService } from '../core/i18n.service';
import { AdConfigurationService } from '../ads/ad-configuration.service';
import { AdConsentService } from '../ads/ad-consent.service';
import { AdSlotComponent } from '../ads/ad-slot';

@Component({
  selector: 'app-privacy',
  imports: [AdSlotComponent],
  template: `
    <article class="wrap max-w-3xl py-10">
      <h1 class="text-4xl">{{ i18n.t('Privacidad y datos personales') }}</h1>
      <div class="mt-6 grid gap-4 leading-7">
        <p>{{ i18n.t('EmpleoRD guarda los datos que escribes en tu cuenta para conectar postulaciones con ofertas. El currículum en PDF no es público: solo tú, la empresa a la que te postulaste y el equipo administrador pueden abrirlo mediante un enlace temporal.') }}</p>
        <p>{{ i18n.t('Puedes editar tu perfil cuando quieras. Si eliminas tu cuenta de candidato, el nombre, el correo, el teléfono, el currículum y la carta de presentación se retiran o se anonimizan. Las empresas conservan el historial de que hubo una postulación, identificado como cuenta eliminada, y sus notas internas.') }}</p>
        <p>{{ i18n.t('Las cuentas que terminan en demo.empleord.do son de demostración. No representan personas ni empresas reales y no traen ofertas precargadas.') }}</p>
        <p>{{ i18n.t('Los precios de los planes se guardan en la base de datos. El cobro, cuando PayPal está configurado, ocurre en la página de PayPal. EmpleoRD no guarda números de tarjeta ni códigos de seguridad. Sin credenciales de PayPal no hay cobro.') }}</p>
        <h2 class="text-2xl">{{ i18n.t('Publicidad') }}</h2>
        @if (ads.config()?.enabled) {
          <p>{{ i18n.t('Si aceptas la publicidad, el portal carga el script público de Google AdSense y puede mostrar anuncios marcados como publicidad. Si la rechazas, ese script no se carga. No pedimos que hagas clic en los anuncios. Los ingresos y los clics se consultan en AdSense, no en este portal.') }}</p>
          <div class="flex flex-wrap gap-2">
            <button class="btn btn-primary" type="button" (click)="consent.grant()">{{ i18n.t('Aceptar publicidad') }}</button>
            <button class="btn btn-ghost" type="button" (click)="consent.deny()">{{ i18n.t('Rechazar publicidad') }}</button>
          </div>
          <p class="text-sm text-muted">{{ consent.choice() === 'granted' ? i18n.t('Aceptaste la publicidad en este navegador.') : consent.choice() === 'denied' ? i18n.t('Rechazaste la publicidad en este navegador.') : i18n.t('Todavía no elegiste si aceptas la publicidad.') }}</p>
        } @else {
          <p>{{ i18n.t('La publicidad está apagada. No se carga el script de AdSense mientras no haya un Publisher ID configurado y la opción activa.') }}</p>
        }
      </div>
      <app-ad-slot slotKey="info-content" />
    </article>
  `,
})
export class PrivacyPage {
  readonly i18n = inject(I18nService);
  readonly ads = inject(AdConfigurationService);
  readonly consent = inject(AdConsentService);
}
