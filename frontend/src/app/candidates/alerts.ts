import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { I18nService } from '../core/i18n.service';
import { Envelope, JobCard, Named } from '../core/models';
import { JobCardView } from '../shared/job-card';

@Component({
  selector: 'app-job-alerts',
  imports: [ReactiveFormsModule, JobCardView],
  template: `
    <section class="wrap grid max-w-3xl gap-6 py-8">
      <div>
        <h1 class="text-4xl">{{ i18n.t('Mis alertas de empleo') }}</h1>
        <p class="mt-2 text-muted">{{ i18n.t('Crea una alerta con los filtros que te interesan. Los criterios que dejes vacíos no se aplican.') }}</p>
      </div>
      @if (loading()) { <p aria-live="polite">{{ i18n.t('Cargando alertas…') }}</p> }
      @if (error()) { <p class="alert" role="alert">{{ i18n.live(error()) }}</p> }
      @if (message()) { <p class="ok" role="status">{{ i18n.live(message()) }}</p> }
      <form class="card grid gap-3 p-4" [formGroup]="form" (ngSubmit)="save()">
        <h2 class="text-2xl">{{ editing() ? i18n.t('Editar alerta') : i18n.t('Nueva alerta') }}</h2>
        <label class="field"><span>{{ i18n.t('Palabra clave') }}</span><input formControlName="keyword"></label>
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="field"><span>{{ i18n.t('Provincia') }}</span>
            <select formControlName="provinceId" (change)="onProvince()"><option value="">{{ i18n.t('Todas') }}</option>@for (item of provinces(); track item.id) { <option [value]="item.id">{{ item.name }}</option> }</select>
          </label>
          <label class="field"><span>{{ i18n.t('Municipio') }}</span>
            <select formControlName="municipalityId"><option value="">{{ i18n.t('Todos') }}</option>@for (item of municipalities(); track item.id) { <option [value]="item.id">{{ item.name }}</option> }</select>
          </label>
          <label class="field"><span>{{ i18n.t('Categoría') }}</span>
            <select formControlName="categoryId"><option value="">{{ i18n.t('Todas') }}</option>@for (item of categories(); track item.id) { <option [value]="item.id">{{ item.name }}</option> }</select>
          </label>
          <label class="field"><span>{{ i18n.t('Modalidad') }}</span>
            <select formControlName="workMode"><option value="">{{ i18n.t('Todas') }}</option><option value="ONSITE">{{ i18n.label('ONSITE') }}</option><option value="HYBRID">{{ i18n.label('HYBRID') }}</option><option value="REMOTE">{{ i18n.label('REMOTE') }}</option></select>
          </label>
          <label class="field"><span>{{ i18n.t('Contrato') }}</span>
            <select formControlName="contractType"><option value="">{{ i18n.t('Todos') }}</option><option value="INDEFINITE">{{ i18n.label('INDEFINITE') }}</option><option value="TEMPORARY">{{ i18n.label('TEMPORARY') }}</option><option value="PROJECT">{{ i18n.label('PROJECT') }}</option><option value="INTERNSHIP">{{ i18n.label('INTERNSHIP') }}</option><option value="FREELANCE">{{ i18n.label('FREELANCE') }}</option></select>
          </label>
          <label class="field"><span>{{ i18n.t('Frecuencia') }}</span>
            <select formControlName="frequency"><option value="IMMEDIATE">{{ i18n.label('IMMEDIATE') }}</option><option value="DAILY">{{ i18n.label('DAILY') }}</option><option value="WEEKLY">{{ i18n.label('WEEKLY') }}</option></select>
          </label>
          <label class="field"><span>{{ i18n.t('Salario mínimo') }}</span><input type="number" min="0" formControlName="salaryMin"></label>
          <label class="field"><span>{{ i18n.t('Salario máximo') }}</span><input type="number" min="0" formControlName="salaryMax"></label>
        </div>
        <label class="flex gap-2"><input type="checkbox" formControlName="active"> {{ i18n.t('Activa') }}</label>
        <div class="flex flex-wrap gap-2">
          <button class="btn btn-primary" type="submit">{{ i18n.t('Guardar alerta') }}</button>
          @if (editing()) { <button class="btn btn-ghost" type="button" (click)="reset()">{{ i18n.t('Cancelar edición') }}</button> }
        </div>
      </form>
      @if (!loading() && alerts().length === 0) { <p class="card p-4">{{ i18n.t('No tienes alertas.') }}</p> }
      <div class="grid gap-3">
        @for (alert of alerts(); track alert.id) {
          <article class="card grid gap-2 p-4">
            <h2 class="text-xl">{{ alert.keyword || i18n.t('Alerta') }}</h2>
            <p class="text-sm text-muted">{{ summary(alert) }}</p>
            <p class="text-sm">{{ alert.active ? i18n.t('Activa') : i18n.t('Inactiva') }} · {{ i18n.label(alert.frequency) }}</p>
            <p class="text-sm">{{ i18n.t('Última notificación:') }} {{ alert.lastNotifiedAt ? i18n.date(alert.lastNotifiedAt) : i18n.t('Todavía no se ha enviado') }}</p>
            <div class="flex flex-wrap gap-2">
              <button class="btn btn-ghost" type="button" (click)="edit(alert)">{{ i18n.t('Editar') }}</button>
              <button class="btn btn-ghost" type="button" (click)="toggle(alert)">{{ alert.active ? i18n.t('Desactivar') : i18n.t('Activar') }}</button>
              <button class="btn btn-ghost" type="button" (click)="matches(alert)">{{ openId() === alert.id ? i18n.t('Ocultar coincidencias') : i18n.t('Ver coincidencias') }}</button>
              <button class="btn btn-ghost" type="button" (click)="remove(alert)">{{ i18n.t('Eliminar alerta') }}</button>
            </div>
            @if (openId() === alert.id) {
              @if (matchesLoading()) { <p>{{ i18n.t('Cargando ofertas…') }}</p> }
              @else if (matched().length === 0) { <p>{{ i18n.t('No hay ofertas que coincidan ahora.') }}</p> }
              @else { <div class="grid gap-2">@for (job of matched(); track job.id) { <app-job-card [job]="job" /> }</div> }
            }
          </article>
        }
      </div>
    </section>
  `,
})
export class JobAlertsPage {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly i18n = inject(I18nService);
  readonly alerts = signal<AlertItem[]>([]);
  readonly provinces = signal<Named[]>([]);
  readonly municipalities = signal<Named[]>([]);
  readonly categories = signal<Named[]>([]);
  readonly matched = signal<JobCard[]>([]);
  readonly loading = signal(true);
  readonly matchesLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly editing = signal<string | null>(null);
  readonly openId = signal<string | null>(null);
  readonly form = inject(FormBuilder).nonNullable.group({
    keyword: [''], provinceId: [''], municipalityId: [''], categoryId: [''], workMode: [''], contractType: [''],
    salaryMin: [''], salaryMax: [''], frequency: ['DAILY'], active: [true],
  });

  constructor() {
    this.http.get<Envelope<Named[]>>('/api/catalog/provinces').subscribe((res) => this.provinces.set(res.data));
    this.http.get<Envelope<Named[]>>('/api/catalog/categories').subscribe((res) => this.categories.set(res.data));
    this.load();
  }

  onProvince(): void {
    const id = this.form.controls.provinceId.value;
    this.form.controls.municipalityId.setValue('');
    if (!id) { this.municipalities.set([]); return; }
    this.http.get<Envelope<Named[]>>(`/api/catalog/provinces/${id}/municipalities`).subscribe((res) => this.municipalities.set(res.data));
  }

  save(): void {
    const raw = this.form.getRawValue();
    const body = {
      keyword: raw.keyword || null,
      provinceId: num(raw.provinceId),
      municipalityId: num(raw.municipalityId),
      categoryId: num(raw.categoryId),
      workMode: raw.workMode || null,
      contractType: raw.contractType || null,
      salaryMin: num(raw.salaryMin),
      salaryMax: num(raw.salaryMax),
      frequency: raw.frequency,
      active: raw.active,
    };
    if (!body.keyword && body.provinceId == null && body.municipalityId == null && body.categoryId == null && !body.workMode && !body.contractType && body.salaryMin == null && body.salaryMax == null) {
      this.error.set(this.i18n.t('Indica al menos un criterio.'));
      return;
    }
    this.error.set(null);
    const request = this.editing()
      ? this.http.put<Envelope<AlertItem>>(`/api/candidate/alerts/${this.editing()}`, body)
      : this.http.post<Envelope<AlertItem>>('/api/candidate/alerts', body);
    request.subscribe({
      next: () => { this.message.set(this.i18n.t('Alerta guardada.')); this.reset(); this.load(); },
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  edit(alert: AlertItem): void {
    this.editing.set(alert.id);
    this.form.patchValue({
      keyword: alert.keyword ?? '',
      provinceId: alert.provinceId ? String(alert.provinceId) : '',
      municipalityId: alert.municipalityId ? String(alert.municipalityId) : '',
      categoryId: alert.categoryId ? String(alert.categoryId) : '',
      workMode: alert.workMode ?? '',
      contractType: alert.contractType ?? '',
      salaryMin: alert.salaryMin ?? '',
      salaryMax: alert.salaryMax ?? '',
      frequency: alert.frequency,
      active: alert.active,
    } as never);
    if (alert.provinceId) this.http.get<Envelope<Named[]>>(`/api/catalog/provinces/${alert.provinceId}/municipalities`).subscribe((res) => this.municipalities.set(res.data));
  }

  reset(): void {
    this.editing.set(null);
    this.form.reset({ keyword: '', provinceId: '', municipalityId: '', categoryId: '', workMode: '', contractType: '', salaryMin: '', salaryMax: '', frequency: 'DAILY', active: true });
  }

  toggle(alert: AlertItem): void {
    this.http.patch(`/api/candidate/alerts/${alert.id}`, { active: !alert.active }).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  remove(alert: AlertItem): void {
    this.http.delete(`/api/candidate/alerts/${alert.id}`).subscribe({
      next: () => { if (this.openId() === alert.id) this.openId.set(null); this.load(); },
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  matches(alert: AlertItem): void {
    if (this.openId() === alert.id) { this.openId.set(null); return; }
    this.openId.set(alert.id);
    this.matchesLoading.set(true);
    this.matched.set([]);
    this.http.get<Envelope<JobCard[]>>(`/api/candidate/alerts/${alert.id}/jobs`).subscribe({
      next: (res) => { this.matched.set(res.data); this.matchesLoading.set(false); },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.matchesLoading.set(false); },
    });
  }

  summary(alert: AlertItem): string {
    return [alert.provinceName, alert.municipalityName, alert.categoryName, alert.workMode ? this.i18n.label(alert.workMode) : '', alert.contractType ? this.i18n.label(alert.contractType) : '']
      .filter(Boolean).join(' · ');
  }

  private load(): void {
    this.loading.set(true);
    this.http.get<Envelope<AlertItem[]>>('/api/candidate/alerts').subscribe({
      next: (res) => {
        this.alerts.set(res.data);
        this.loading.set(false);
        const selected = this.route.snapshot.queryParamMap.get('alerta');
        const alert = res.data.find((item) => item.id === selected);
        if (alert) this.matches(alert);
      },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.loading.set(false); },
    });
  }
}

function num(value: unknown): number | null { return value === '' || value == null ? null : Number(value); }

interface AlertItem {
  id: string; keyword: string | null; provinceId: number | null; provinceName: string | null; municipalityId: number | null; municipalityName: string | null;
  categoryId: number | null; categoryName: string | null; workMode: string | null; contractType: string | null; salaryMin: number | null; salaryMax: number | null;
  frequency: string; active: boolean; lastNotifiedAt: string | null;
}
