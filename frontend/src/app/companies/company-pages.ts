import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { I18nService } from '../core/i18n.service';
import { Country, Envelope, Named } from '../core/models';

@Component({
  selector: 'app-company-home',
  imports: [RouterLink],
  template: `
    <section class="wrap py-8">
      <h1 class="text-4xl">{{ company()?.profile?.name }}</h1>
      <p class="mt-2 text-muted">{{ i18n.t('Plan') }} {{ company()?.planName }} · {{ i18n.t('hasta') }} {{ company()?.maxActiveJobs }} {{ i18n.t('ofertas activas') }}</p>
      <div class="mt-6 grid gap-3 sm:grid-cols-4">
        <article class="card p-4"><p class="text-sm text-muted">{{ i18n.t('Ofertas activas') }}</p><p class="font-serif text-3xl">{{ stats()?.activeJobs }}</p></article>
        <article class="card p-4"><p class="text-sm text-muted">{{ i18n.t('Postulaciones') }}</p><p class="font-serif text-3xl">{{ stats()?.applications }}</p></article>
        <article class="card p-4"><p class="text-sm text-muted">{{ i18n.t('Visualizaciones') }}</p><p class="font-serif text-3xl">{{ stats()?.views }}</p></article>
        <article class="card p-4"><p class="text-sm text-muted">{{ i18n.t('Estados') }}</p><p class="text-sm">@for (entry of statusEntries(); track entry[0]) { <span class="block">{{ i18n.label(entry[0]) }}: {{ entry[1] }}</span> }</p></article>
      </div>
      @if (stats()?.advancedStats) {
        <h2 class="mt-8 text-2xl">{{ i18n.t('Rendimiento por oferta') }}</h2>
        <div class="mt-3 grid gap-2">@for (job of stats()?.jobs ?? []; track job.id) { <p class="card p-3">{{ job.title }} · {{ job.views }} {{ i18n.t('vistas') }} · {{ job.applications }} {{ i18n.t('postulaciones') }}</p> }</div>
      } @else { <p class="mt-6 text-sm text-muted">{{ i18n.t('El detalle por oferta forma parte de un plan con estadísticas. Puedes contratarlo si la pasarela está configurada.') }}</p> }
      <div class="mt-6 flex flex-wrap gap-3">
        <a class="btn btn-primary" routerLink="/empresa/ofertas/nueva">{{ i18n.t('Nueva oferta') }}</a>
        <a class="btn btn-ghost" routerLink="/empresa/ofertas">{{ i18n.t('Ofertas') }}</a>
        <a class="btn btn-ghost" routerLink="/empresa/perfil">{{ i18n.t('Perfil') }}</a>
        <a class="btn btn-ghost" routerLink="/empresa/equipo">{{ i18n.t('Equipo') }}</a>
        <a class="btn btn-ghost" routerLink="/empresa/suscripcion">{{ i18n.t('Suscripción') }}</a>
      </div>
    </section>
  `,
})
export class CompanyHome {
  readonly company = signal<Managed | null>(null);
  readonly stats = signal<Stats | null>(null);
  readonly i18n = inject(I18nService);
  readonly statusEntries = signal<[string, number][]>([]);
  constructor() {
    const http = inject(HttpClient);
    http.get<Envelope<Managed>>('/api/company').subscribe((res) => this.company.set(res.data));
    http.get<Envelope<Stats>>('/api/company/stats').subscribe((res) => {
      this.stats.set(res.data);
      this.statusEntries.set(Object.entries(res.data.byStatus));
    });
  }
}

@Component({
  selector: 'app-company-profile',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <form class="wrap grid max-w-2xl gap-3 py-8" [formGroup]="form" (ngSubmit)="save()">
      <a routerLink="/empresa">{{ i18n.t('Volver') }}</a>
      <h1 class="text-4xl">{{ i18n.t('Perfil de la empresa') }}</h1>
      @if (message()) { <p class="ok">{{ i18n.live(message()) }}</p> }
      @if (error()) { <p class="alert">{{ i18n.live(error()) }}</p> }
      <label class="field"><span>{{ i18n.t('Nombre') }}</span><input formControlName="name"></label>
      <label class="field"><span>{{ i18n.t('Descripción') }}</span><textarea rows="5" formControlName="description"></textarea></label>
      <label class="field"><span>{{ i18n.t('Sitio web') }}</span><input formControlName="website"></label>
      <label class="field"><span>{{ i18n.t('Actividad') }}</span><input formControlName="industry"></label>
      <label class="field"><span>{{ i18n.t('País') }}</span><select formControlName="countryCode">@for (country of countries(); track country.code) { <option [value]="country.code">{{ country.name }}</option> }</select></label>
      <label class="field"><span>{{ i18n.t('Provincia') }}</span><select formControlName="provinceId" (change)="onProvince()"><option value="">{{ i18n.t('Selecciona') }}</option>@for (item of provinces(); track item.id) { <option [value]="item.id">{{ item.name }}</option> }</select></label>
      <label class="field"><span>{{ i18n.t('Municipio') }}</span><select formControlName="municipalityId"><option value="">{{ i18n.t('Selecciona') }}</option>@for (item of municipalities(); track item.id) { <option [value]="item.id">{{ item.name }}</option> }</select></label>
      <label class="field"><span>{{ i18n.t('Tu cargo') }}</span><input formControlName="recruiterTitle"></label>
      <label class="field"><span>{{ i18n.t('Teléfono') }}</span><input formControlName="phone"></label>
      <label class="field"><span>{{ i18n.t('Logotipo') }}</span><input type="file" accept="image/png,image/jpeg,image/webp" (change)="upload($event)"></label>
      <button class="btn btn-primary w-fit" type="submit">{{ i18n.t('Guardar') }}</button>
    </form>
  `,
})
export class CompanyProfilePage {
  private readonly http = inject(HttpClient);
  readonly countries = signal<Country[]>([]);
  readonly provinces = signal<Named[]>([]);
  readonly municipalities = signal<Named[]>([]);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly i18n = inject(I18nService);
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required], description: [''], website: [''], industry: [''], countryCode: ['DO'], provinceId: [''], municipalityId: [''], recruiterTitle: [''], phone: [''],
  });
  constructor() {
    this.http.get<Envelope<Country[]>>('/api/catalog/countries').subscribe((res) => this.countries.set(res.data));
    this.http.get<Envelope<Named[]>>('/api/catalog/provinces').subscribe((res) => this.provinces.set(res.data));
    this.http.get<Envelope<Managed>>('/api/company').subscribe((res) => {
      const profile = res.data.profile;
      this.form.patchValue({ name: profile.name, description: profile.description ?? '', website: profile.website ?? '', industry: profile.industry ?? '', countryCode: profile.countryCode, recruiterTitle: res.data.recruiterTitle ?? '' });
    });
  }
  onProvince(): void {
    const id = this.form.controls.provinceId.value;
    if (!id) return;
    this.http.get<Envelope<Named[]>>(`/api/catalog/provinces/${id}/municipalities`).subscribe((res) => this.municipalities.set(res.data));
  }
  save(): void {
    const raw = this.form.getRawValue();
    this.http.put('/api/company', { ...raw, provinceId: raw.provinceId ? Number(raw.provinceId) : null, municipalityId: raw.municipalityId ? Number(raw.municipalityId) : null }).subscribe({
      next: () => this.message.set(this.i18n.t('Perfil actualizado.')),
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }
  upload(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const data = new FormData();
    data.set('file', file);
    this.http.post('/api/company/logo', data).subscribe({ next: () => this.message.set(this.i18n.t('Logotipo actualizado.')), error: (err) => this.error.set(this.i18n.apiError(err)) });
  }
}

@Component({
  selector: 'app-company-jobs',
  imports: [RouterLink],
  template: `
    <section class="wrap py-8">
      <div class="flex items-center justify-between"><h1 class="text-4xl">{{ i18n.t('Ofertas') }}</h1><a class="btn btn-primary" routerLink="/empresa/ofertas/nueva">{{ i18n.t('Nueva') }}</a></div>
      <div class="mt-4 grid gap-3">
        @for (job of jobs(); track job.id) {
          <article class="card p-4">
            <h2 class="text-xl">{{ job.title }}</h2>
            <p class="text-sm">{{ i18n.label(job.status) }} · {{ job.applications }} {{ i18n.t('postulaciones') }} · {{ job.viewCount }} {{ i18n.t('vistas') }} @if (job.featured) { · {{ i18n.t('Destacada') }} }</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <a class="btn btn-ghost" [routerLink]="['/empresa/ofertas', job.id]">{{ i18n.t('Editar') }}</a>
              <a class="btn btn-ghost" [routerLink]="['/empresa/ofertas', job.id, 'candidatos']">{{ i18n.t('Candidatos') }}</a>
              <a class="btn btn-ghost" [routerLink]="['/empleos', job.slug]">{{ i18n.t('Ver pública') }}</a>
              @if (job.status === 'PUBLISHED') { <button class="btn btn-ghost" type="button" (click)="status(job.id, 'PAUSED')">{{ i18n.t('Pausar') }}</button> }
              @if (job.status === 'PAUSED' || job.status === 'DRAFT' || job.status === 'CLOSED') { <button class="btn btn-ghost" type="button" (click)="status(job.id, 'PUBLISHED')">{{ i18n.t('Publicar') }}</button> }
              @if (job.status !== 'CLOSED') { <button class="btn btn-ghost" type="button" (click)="status(job.id, 'CLOSED')">{{ i18n.t('Cerrar') }}</button> }
            </div>
          </article>
        } @empty { <p>{{ i18n.t('No has publicado ofertas.') }}</p> }
      </div>
    </section>
  `,
})
export class CompanyJobsPage {
  private readonly http = inject(HttpClient);
  readonly jobs = signal<ManagedJob[]>([]);
  readonly i18n = inject(I18nService);
  constructor() { this.load(); }
  status(id: string, status: string): void { this.http.patch(`/api/company/jobs/${id}/status`, { status }).subscribe(() => this.load()); }
  private load(): void { this.http.get<Envelope<ManagedJob[]>>('/api/company/jobs').subscribe((res) => this.jobs.set(res.data)); }
}

@Component({
  selector: 'app-company-team',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="wrap max-w-2xl py-8">
      <a routerLink="/empresa">{{ i18n.t('Volver') }}</a>
      <h1 class="text-4xl">{{ i18n.t('Equipo de reclutamiento') }}</h1>
      @if (message()) { <p class="ok mt-3">{{ i18n.live(message()) }}</p> }
      @if (error()) { <p class="alert mt-3">{{ i18n.live(error()) }}</p> }
      <ul class="mt-4 grid gap-2">@for (member of members(); track member.userId) { <li class="card p-3">{{ member.name }} · {{ member.email }} · {{ i18n.label(member.role) }}</li> }</ul>
      <form class="card mt-4 grid gap-3 p-4" [formGroup]="form" (ngSubmit)="add()">
        <h2 class="text-xl">{{ i18n.t('Añadir reclutador') }}</h2>
        <p class="text-sm text-muted">{{ i18n.t('La persona recibe un enlace para crear su contraseña. Debe usar un correo que aún no esté registrado.') }}</p>
        <label class="field"><span>{{ i18n.t('Nombre') }}</span><input formControlName="firstName"></label>
        <label class="field"><span>{{ i18n.t('Apellido') }}</span><input formControlName="lastName"></label>
        <label class="field"><span>{{ i18n.t('Correo') }}</span><input type="email" formControlName="email"></label>
        <button class="btn btn-primary w-fit" type="submit">{{ i18n.t('Enviar invitación') }}</button>
      </form>
    </section>
  `,
})
export class CompanyTeamPage {
  private readonly http = inject(HttpClient);
  readonly members = signal<{ userId: string; name: string; email: string; role: string }[]>([]);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly i18n = inject(I18nService);
  readonly form = inject(FormBuilder).nonNullable.group({ firstName: ['', Validators.required], lastName: ['', Validators.required], email: ['', [Validators.required, Validators.email]] });
  constructor() { this.load(); }
  add(): void {
    this.http.post('/api/company/members', this.form.getRawValue()).subscribe({
      next: () => { this.message.set(this.i18n.t('Enviamos el enlace para crear la contraseña.')); this.load(); },
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }
  private load(): void { this.http.get<Envelope<{ userId: string; name: string; email: string; role: string }[]>>('/api/company/members').subscribe((res) => this.members.set(res.data)); }
}

interface Managed { profile: { name: string; description: string | null; website: string | null; industry: string | null; countryCode: string }; planName: string; maxActiveJobs: number; recruiterTitle: string | null; }
interface Stats { activeJobs: number; applications: number; views: number; byStatus: Record<string, number>; jobs: { id: string; title: string; views: number; applications: number }[]; advancedStats: boolean; }
interface ManagedJob { id: string; slug: string; title: string; status: string; applications: number; viewCount: number; featured: boolean; }
