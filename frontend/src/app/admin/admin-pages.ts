import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiError, formatDate, label, money } from '../core/format';
import { Envelope, Page, Plan } from '../core/models';

@Component({
  selector: 'app-admin-home',
  imports: [RouterLink],
  template: `
    <section class="wrap py-8">
      <h1 class="text-4xl">Administración</h1>
      <div class="mt-6 grid gap-3 sm:grid-cols-4">
        @for (item of cards(); track item.label) { <article class="card p-4"><p class="text-sm text-muted">{{ item.label }}</p><p class="font-serif text-3xl">{{ item.value }}</p></article> }
      </div>
      <h2 class="mt-8 text-2xl">Registros por mes</h2>
      <ul class="mt-2">@for (point of points(); track point.month) { <li>{{ point.month }}: {{ point.users }} cuentas</li> }</ul>
      <nav class="mt-6 flex flex-wrap gap-3">
        <a routerLink="/admin/usuarios">Usuarios</a><a routerLink="/admin/empresas">Empresas</a><a routerLink="/admin/ofertas">Ofertas</a>
        <a routerLink="/admin/planes">Planes</a><a routerLink="/admin/reportes">Reportes</a><a routerLink="/admin/auditoria">Auditoría</a>
      </nav>
    </section>
  `,
})
export class AdminHome {
  readonly cards = signal<{ label: string; value: number }[]>([]);
  readonly points = signal<{ month: string; users: number }[]>([]);
  constructor() {
    inject(HttpClient).get<Envelope<Stats>>('/api/admin/stats').subscribe((res) => {
      const data = res.data;
      this.cards.set([
        { label: 'Usuarios', value: data.users }, { label: 'Candidatos', value: data.candidates }, { label: 'Empresas', value: data.companies },
        { label: 'Verificadas', value: data.verifiedCompanies }, { label: 'Ofertas activas', value: data.activeJobs }, { label: 'Postulaciones', value: data.applications },
        { label: 'Reportes abiertos', value: data.openReports },
      ]);
      this.points.set(data.registrations);
    });
  }
}

@Component({ selector: 'app-admin-users', template: `
  <section class="wrap py-8"><h1 class="text-3xl">Usuarios</h1>
    <form class="mt-3 flex gap-2" (ngSubmit)="load()"><input class="rounded-full border border-line px-3" placeholder="Buscar" [formControl]="q"><button class="btn btn-ghost" type="submit">Buscar</button></form>
    <div class="mt-4 grid gap-2">@for (user of users(); track user.id) { <article class="card flex flex-wrap items-center justify-between gap-2 p-3"><div><strong>{{ user.name }}</strong><p class="text-sm">{{ user.email }} · {{ user.role }} · {{ label(user.status) }}</p></div>
      @if (user.role !== 'ADMIN') { <button class="btn btn-ghost" type="button" (click)="toggle(user)">{{ user.status === 'SUSPENDED' ? 'Activar' : 'Suspender' }}</button> }
    </article> }</div></section>`, imports: [ReactiveFormsModule] })
export class AdminUsers {
  private readonly http = inject(HttpClient);
  readonly users = signal<UserItem[]>([]);
  readonly q = inject(FormBuilder).nonNullable.control('');
  readonly label = label;
  constructor() { this.load(); }
  load(): void { this.http.get<Envelope<Page<UserItem>>>('/api/admin/users', { params: { q: this.q.value } }).subscribe((res) => this.users.set(res.data.content)); }
  toggle(user: UserItem): void { this.http.patch(`/api/admin/users/${user.id}/status`, { status: user.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED' }).subscribe(() => this.load()); }
}

@Component({ selector: 'app-admin-companies', imports: [ReactiveFormsModule], template: `
  <section class="wrap py-8"><h1 class="text-3xl">Empresas</h1>@if (error()) { <p class="alert">{{ error() }}</p> }
    <div class="mt-4 grid gap-3">@for (company of companies(); track company.id) {
      <article class="card grid gap-2 p-4"><h2>{{ company.name }}</h2><p class="text-sm">{{ label(company.verificationStatus) }} · {{ company.countryCode }}</p>
        <div class="flex flex-wrap gap-2"><button class="btn btn-ghost" type="button" (click)="verify(company.id, 'VERIFIED')">Verificar</button><button class="btn btn-ghost" type="button" (click)="verify(company.id, 'REJECTED')">Rechazar verificación</button>
          <button class="btn btn-ghost" type="button" (click)="plan(company.id, 'FREE')">Plan gratuito</button><button class="btn btn-ghost" type="button" (click)="plan(company.id, 'PROFESSIONAL')">Plan profesional</button></div>
      </article> }</div></section>` })
export class AdminCompanies {
  private readonly http = inject(HttpClient);
  readonly companies = signal<CompanyItem[]>([]);
  readonly error = signal<string | null>(null);
  readonly label = label;
  constructor() { this.http.get<Envelope<Page<CompanyItem>>>('/api/admin/companies').subscribe((res) => this.companies.set(res.data.content)); }
  verify(id: string, status: string): void { this.http.patch(`/api/admin/companies/${id}/verification`, { status, notes: 'Revisión administrativa' }).subscribe({ error: (err) => this.error.set(apiError(err)), next: () => this.reload() }); }
  plan(id: string, planCode: string): void { this.http.patch(`/api/admin/companies/${id}/plan`, { planCode }).subscribe({ next: () => this.reload(), error: (err) => this.error.set(apiError(err)) }); }
  private reload(): void { this.http.get<Envelope<Page<CompanyItem>>>('/api/admin/companies').subscribe((res) => this.companies.set(res.data.content)); }
}

@Component({ selector: 'app-admin-jobs', template: `
  <section class="wrap py-8"><h1 class="text-3xl">Moderación de ofertas</h1>
    <div class="mt-4 grid gap-3">@for (job of jobs(); track job.id) {
      <article class="card p-4"><h2>{{ job.title }}</h2><p class="text-sm">{{ job.companyName }} · {{ label(job.status) }}</p>
        <div class="mt-2 flex flex-wrap gap-2"><button class="btn btn-ghost" type="button" (click)="set(job.id, 'REJECTED')">Rechazar</button><button class="btn btn-ghost" type="button" (click)="set(job.id, 'CLOSED')">Cerrar</button><button class="btn btn-ghost" type="button" (click)="set(job.id, 'PUBLISHED')">Publicar</button><button class="btn btn-ghost" type="button" (click)="feature(job.id)">Destacar 7 días</button></div>
      </article> } @empty { <p>No hay ofertas para moderar.</p> }</div></section>` })
export class AdminJobs {
  private readonly http = inject(HttpClient);
  readonly jobs = signal<JobItem[]>([]);
  readonly label = label;
  constructor() { this.load(); }
  set(id: string, status: string): void { this.http.patch(`/api/admin/jobs/${id}/moderation`, { status }).subscribe(() => this.load()); }
  feature(id: string): void { this.http.post(`/api/admin/jobs/${id}/feature`, { days: 7 }).subscribe(() => this.load()); }
  private load(): void { this.http.get<Envelope<Page<JobItem>>>('/api/admin/jobs').subscribe((res) => this.jobs.set(res.data.content)); }
}

@Component({ selector: 'app-admin-plans', imports: [ReactiveFormsModule], template: `
  <section class="wrap max-w-2xl py-8"><h1 class="text-3xl">Planes comerciales</h1><p class="mt-2 text-sm text-muted">Los precios viven en la base de datos. Esta pantalla los cambia; no ejecuta un cobro.</p>
    @for (plan of plans(); track plan.id) {
      <form class="card mt-4 grid gap-2 p-4" (ngSubmit)="save(plan)">
        <h2>{{ plan.name }} · {{ plan.code }}</h2>
        <label class="field"><span>Nombre</span><input [value]="plan.name" (input)="plan.name = $any($event.target).value"></label>
        <label class="field"><span>Descripción</span><textarea [value]="plan.description" (input)="plan.description = $any($event.target).value"></textarea></label>
        <label class="field"><span>Precio {{ plan.currency }}</span><input type="number" [value]="plan.priceAmount" (input)="plan.priceAmount = asNumber($event)"></label>
        <label class="field"><span>Ofertas activas</span><input type="number" [value]="plan.maxActiveJobs" (input)="plan.maxActiveJobs = asNumber($event)"></label>
        <label class="flex gap-2"><input type="checkbox" [checked]="plan.candidateFilters" (change)="plan.candidateFilters = $any($event.target).checked"> Filtros de candidatos</label>
        <label class="flex gap-2"><input type="checkbox" [checked]="plan.advancedStats" (change)="plan.advancedStats = $any($event.target).checked"> Estadísticas por oferta</label>
        <label class="flex gap-2"><input type="checkbox" [checked]="plan.active" (change)="plan.active = $any($event.target).checked"> Activo</label>
        <p class="text-sm">Precio visible: {{ money(plan.priceAmount, plan.currency) }}</p>
        <button class="btn btn-primary w-fit" type="submit">Guardar plan</button>
      </form>
    }</section>` })
export class AdminPlans {
  private readonly http = inject(HttpClient);
  readonly plans = signal<Plan[]>([]);
  readonly money = money;
  asNumber(event: Event): number {
    return Number((event.target as HTMLInputElement).value);
  }
  constructor() { this.http.get<Envelope<Plan[]>>('/api/admin/plans').subscribe((res) => this.plans.set(res.data)); }
  save(plan: Plan): void { this.http.put(`/api/admin/plans/${plan.id}`, { ...plan, featuredCredits: plan.featuredCredits, premiumProfile: plan.premiumProfile }).subscribe(); }
}

@Component({ selector: 'app-admin-reports', template: `
  <section class="wrap py-8"><h1 class="text-3xl">Reportes</h1>
    <div class="mt-4 grid gap-3">@for (report of reports(); track report.id) { <article class="card p-4"><p class="font-bold">{{ report.reason }}</p><p>{{ report.details }}</p><p class="text-sm">{{ report.reporterEmail }} · {{ formatDate(report.createdAt) }}</p>
      <div class="mt-2 flex gap-2"><button class="btn btn-ghost" type="button" (click)="set(report.id, 'REVIEWED')">Revisado</button><button class="btn btn-ghost" type="button" (click)="set(report.id, 'DISMISSED')">Descartar</button></div></article> } @empty { <p>No hay reportes abiertos.</p> }</div></section>` })
export class AdminReports {
  private readonly http = inject(HttpClient);
  readonly reports = signal<ReportItem[]>([]);
  readonly formatDate = formatDate;
  constructor() { this.load(); }
  set(id: string, status: string): void { this.http.patch(`/api/admin/reports/${id}`, { status }).subscribe(() => this.load()); }
  private load(): void { this.http.get<Envelope<ReportItem[]>>('/api/admin/reports').subscribe((res) => this.reports.set(res.data)); }
}

@Component({ selector: 'app-admin-audit', template: `
  <section class="wrap py-8"><h1 class="text-3xl">Auditoría</h1>
    <div class="mt-4 grid gap-2">@for (item of items(); track item.id) { <p class="card p-3 text-sm">{{ formatDate(item.createdAt) }} · {{ item.action }} · {{ item.entityType }} {{ item.entityId }} · {{ item.metadata }}</p> }</div></section>` })
export class AdminAudit {
  readonly items = signal<AuditItem[]>([]);
  readonly formatDate = formatDate;
  constructor() { inject(HttpClient).get<Envelope<Page<AuditItem>>>('/api/admin/audit').subscribe((res) => this.items.set(res.data.content)); }
}

interface Stats { users: number; candidates: number; companies: number; verifiedCompanies: number; activeJobs: number; applications: number; openReports: number; registrations: { month: string; users: number }[]; }
interface UserItem { id: string; name: string; email: string; role: string; status: string; }
interface CompanyItem { id: string; name: string; verificationStatus: string; countryCode: string; }
interface JobItem { id: string; title: string; companyName: string; status: string; }
interface ReportItem { id: string; reason: string; details: string; reporterEmail: string; createdAt: string; }
interface AuditItem { id: string; createdAt: string; action: string; entityType: string; entityId: string; metadata: string; }
