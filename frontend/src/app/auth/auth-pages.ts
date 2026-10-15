import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { apiError } from '../core/format';
import { Envelope } from '../core/models';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <form class="card mx-auto mt-10 grid w-full max-w-md gap-3 p-6" [formGroup]="form" (ngSubmit)="submit()">
      <h1 class="text-3xl">Entrar</h1>
      <p class="text-sm text-muted">Cuentas de demostración: candidato@demo.empleord.do, reclutador@demo.empleord.do y admin@demo.empleord.do. Contraseña: Demo-EmpleoRD-2026!</p>
      @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
      <label class="field"><span>Correo</span><input type="email" formControlName="email" autocomplete="username"></label>
      <label class="field"><span>Contraseña</span><input type="password" formControlName="password" autocomplete="current-password"></label>
      <button class="btn btn-primary" type="submit" [disabled]="form.invalid">Entrar</button>
      <a routerLink="/recuperar">Olvidé mi contraseña</a>
      <a routerLink="/registro">Crear cuenta</a>
    </form>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email]], password: ['', Validators.required] });
  async submit(): Promise<void> {
    this.error.set(null);
    try {
      const value = this.form.getRawValue();
      await this.auth.login(value.email, value.password);
      const role = this.auth.user()?.role;
      const fallback = role === 'ADMIN' ? '/admin' : role === 'EMPLOYER' ? '/empresa' : '/candidato';
      await this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('returnUrl') || fallback);
    } catch (err) { this.error.set(apiError(err)); }
  }
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <form class="card mx-auto mt-10 grid w-full max-w-lg gap-3 p-6" [formGroup]="form" (ngSubmit)="submit()">
      <h1 class="text-3xl">Crear cuenta</h1>
      @if (message()) { <p class="ok" role="status">{{ message() }}</p> }
      @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
      <fieldset class="grid gap-2"><legend class="font-bold">Tipo de cuenta</legend>
        <label><input type="radio" formControlName="accountType" value="CANDIDATE"> Busco empleo</label>
        <label><input type="radio" formControlName="accountType" value="EMPLOYER"> Represento una empresa</label>
      </fieldset>
      <label class="field"><span>Nombre</span><input formControlName="firstName"></label>
      <label class="field"><span>Apellido</span><input formControlName="lastName"></label>
      <label class="field"><span>Correo</span><input type="email" formControlName="email"></label>
      <label class="field"><span>Teléfono</span><input formControlName="phone"></label>
      <label class="field"><span>Contraseña</span><input type="password" formControlName="password"></label>
      @if (form.controls.accountType.value === 'EMPLOYER') { <label class="field"><span>Nombre de la empresa</span><input formControlName="companyName"></label> }
      <button class="btn btn-primary" type="submit">Registrarme</button>
      <a routerLink="/entrar">Ya tengo cuenta</a>
    </form>
  `,
})
export class RegisterPage {
  private readonly http = inject(HttpClient);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    accountType: ['CANDIDATE', Validators.required], firstName: ['', Validators.required], lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]], phone: [''], password: ['', Validators.required], companyName: [''],
  });
  submit(): void {
    this.http.post<Envelope<unknown>>('/api/auth/register', this.form.getRawValue()).subscribe({
      next: (res) => this.message.set(res.message),
      error: (err) => this.error.set(apiError(err)),
    });
  }
}

@Component({
  selector: 'app-verify',
  template: `<section class="wrap max-w-lg py-10"><h1 class="text-3xl">Verificación de correo</h1><p class="mt-4" role="status">{{ message() }}</p><a class="btn btn-primary mt-4" routerLink="/entrar">Ir a entrar</a></section>`,
  imports: [RouterLink],
})
export class VerifyPage {
  readonly message = signal('Confirmando tu correo…');
  constructor() {
    const token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
    if (!token) { this.message.set('Falta el enlace de verificación.'); return; }
    inject(HttpClient).post<Envelope<unknown>>('/api/auth/verify-email', { token }).subscribe({
      next: (res) => this.message.set(res.message ?? 'Correo confirmado.'),
      error: (err) => this.message.set(apiError(err)),
    });
  }
}

@Component({
  selector: 'app-forgot',
  imports: [ReactiveFormsModule],
  template: `
    <form class="card mx-auto mt-10 grid max-w-md gap-3 p-6" [formGroup]="form" (ngSubmit)="submit()">
      <h1 class="text-3xl">Recuperar contraseña</h1>
      @if (message()) { <p class="ok">{{ message() }}</p> }
      <label class="field"><span>Correo</span><input type="email" formControlName="email"></label>
      <button class="btn btn-primary" type="submit">Enviar enlace</button>
    </form>
  `,
})
export class ForgotPage {
  private readonly http = inject(HttpClient);
  readonly message = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  submit(): void {
    this.http.post<Envelope<unknown>>('/api/auth/forgot-password', this.form.getRawValue()).subscribe((res) => this.message.set(res.message));
  }
}

@Component({
  selector: 'app-reset',
  imports: [ReactiveFormsModule],
  template: `
    <form class="card mx-auto mt-10 grid max-w-md gap-3 p-6" [formGroup]="form" (ngSubmit)="submit()">
      <h1 class="text-3xl">Nueva contraseña</h1>
      @if (message()) { <p class="ok">{{ message() }}</p> }
      @if (error()) { <p class="alert">{{ error() }}</p> }
      <label class="field"><span>Contraseña</span><input type="password" formControlName="password"></label>
      <button class="btn btn-primary" type="submit">Guardar</button>
    </form>
  `,
})
export class ResetPage {
  private readonly http = inject(HttpClient);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({ password: ['', Validators.required] });
  submit(): void {
    this.http.post<Envelope<unknown>>('/api/auth/reset-password', { token: this.token, password: this.form.controls.password.value }).subscribe({
      next: (res) => this.message.set(res.message),
      error: (err) => this.error.set(apiError(err)),
    });
  }
}
