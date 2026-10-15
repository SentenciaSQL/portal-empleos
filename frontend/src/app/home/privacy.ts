import { Component } from '@angular/core';

@Component({
  selector: 'app-privacy',
  template: `
    <article class="wrap max-w-3xl py-10">
      <h1 class="text-4xl">Privacidad y datos personales</h1>
      <div class="mt-6 grid gap-4 leading-7">
        <p>EmpleoRD guarda los datos que escribes en tu cuenta para conectar postulaciones con ofertas. El currículum en PDF no es público: solo tú, la empresa a la que te postulaste y el equipo administrador pueden abrirlo mediante un enlace temporal.</p>
        <p>Puedes editar tu perfil cuando quieras. Si eliminas tu cuenta de candidato, el nombre, el correo, el teléfono, el currículum y la carta de presentación se retiran o se anonimizan. Las empresas conservan el historial de que hubo una postulación, identificado como cuenta eliminada, y sus notas internas.</p>
        <p>Las cuentas que terminan en demo.empleord.do son de demostración. No representan personas ni empresas reales y no traen ofertas precargadas.</p>
        <p>Los precios de los planes empresariales se guardan en la base de datos y se cambian desde el panel de administración. Esta versión no cobra pagos.</p>
      </div>
    </article>
  `,
})
export class PrivacyPage {}
