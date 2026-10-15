import { Component, inject } from '@angular/core';
import { I18nService } from '../core/i18n.service';

@Component({
  selector: 'app-privacy',
  template: `
    <article class="wrap max-w-3xl py-10">
      <h1 class="text-4xl">{{ i18n.t('Privacidad y datos personales') }}</h1>
      <div class="mt-6 grid gap-4 leading-7">
        <p>{{ i18n.t('EmpleoRD guarda los datos que escribes en tu cuenta para conectar postulaciones con ofertas. El currículum en PDF no es público: solo tú, la empresa a la que te postulaste y el equipo administrador pueden abrirlo mediante un enlace temporal.') }}</p>
        <p>{{ i18n.t('Puedes editar tu perfil cuando quieras. Si eliminas tu cuenta de candidato, el nombre, el correo, el teléfono, el currículum y la carta de presentación se retiran o se anonimizan. Las empresas conservan el historial de que hubo una postulación, identificado como cuenta eliminada, y sus notas internas.') }}</p>
        <p>{{ i18n.t('Las cuentas que terminan en demo.empleord.do son de demostración. No representan personas ni empresas reales y no traen ofertas precargadas.') }}</p>
        <p>{{ i18n.t('Los precios de los planes se guardan en la base de datos. El cobro, cuando PayPal está configurado, ocurre en la página de PayPal. EmpleoRD no guarda números de tarjeta ni códigos de seguridad. Sin credenciales de PayPal no hay cobro.') }}</p>
      </div>
    </article>
  `,
})
export class PrivacyPage {
  readonly i18n = inject(I18nService);
}
