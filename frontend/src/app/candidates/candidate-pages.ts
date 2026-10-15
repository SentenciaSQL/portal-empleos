import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { apiError, formatDate, label } from '../core/format';
import { Envelope, JobCard, Named } from '../core/models';
import { JobCardView } from '../shared/job-card';

@Component({
  selector: 'app-candidate-home',
  imports: [RouterLink],
  template: `
    <section class="wrap py-8">
      <h1 class="text-4xl">Hola, {{ auth.user()?.firstName }}</h1>
      <div class="mt-6 grid gap-3 sm:grid-cols-4">
        @for (item of cards(); track item.label) {
          <article class="card p-4"><p class="text-sm text-muted">{{ item.label }}</p><p class="font-serif text-3xl">{{ item.value }}</p></article>
        }
      </div>
      <div class="mt-6 flex flex-wrap gap-3">
        <a class="btn btn-primary" routerLink="/empleos">Buscar empleos</a>
        <a class="btn btn-ghost" routerLink="/candidato/perfil">Editar perfil</a>
        <a class="btn btn-ghost" routerLink="/candidato/postulaciones">Postulaciones</a>
        <a class="btn btn-ghost" routerLink="/candidato/guardados">Guardadas</a>
      </div>
    </section>
  `,
})
export class CandidateHome {
  readonly auth = inject(AuthService);
  readonly cards = signal<{ label: string; value: number }[]>([]);
  constructor() {
    inject(HttpClient).get<Envelope<{ applications: number; inProgress: number; savedJobs: number; activeAlerts: number }>>('/api/candidate/stats').subscribe((res) => {
      this.cards.set([
        { label: 'Postulaciones enviadas', value: res.data.applications },
        { label: 'En proceso', value: res.data.inProgress },
        { label: 'Ofertas guardadas', value: res.data.savedJobs },
        { label: 'Alertas activas', value: res.data.activeAlerts },
      ]);
    });
  }
}

@Component({
  selector: 'app-candidate-profile',
  imports: [ReactiveFormsModule],
  templateUrl: './profile.html',
})
export class CandidateProfilePage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly provinces = signal<Named[]>([]);
  readonly municipalities = signal<Named[]>([]);
  readonly skills = signal<Named[]>([]);
  readonly profile = signal<Profile | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly label = label;
  readonly profileForm = inject(FormBuilder).nonNullable.group({
    headline: [''], summary: [''], provinceId: [''], municipalityId: [''], linkedinUrl: [''], portfolioUrl: [''],
    salaryMin: [''], salaryMax: [''], salaryCurrency: ['DOP'], salaryPeriod: ['MONTH'], openToWork: [true],
  });
  readonly experienceForm = inject(FormBuilder).nonNullable.group({ jobTitle: ['', Validators.required], companyName: ['', Validators.required], location: [''], startDate: ['', Validators.required], endDate: [''], currentJob: [false], description: [''] });
  readonly educationForm = inject(FormBuilder).nonNullable.group({ institution: ['', Validators.required], degree: ['', Validators.required], fieldOfStudy: [''], startYear: [''], endYear: [''] });
  readonly certForm = inject(FormBuilder).nonNullable.group({ name: ['', Validators.required], issuer: [''], issueYear: [''], credentialUrl: [''] });
  readonly languageForm = inject(FormBuilder).nonNullable.group({ language: ['', Validators.required], proficiency: ['INTERMEDIATE', Validators.required] });

  constructor() {
    this.http.get<Envelope<Named[]>>('/api/catalog/provinces').subscribe((res) => this.provinces.set(res.data));
    this.http.get<Envelope<Named[]>>('/api/catalog/skills').subscribe((res) => this.skills.set(res.data));
    this.reload();
  }

  reload(): void {
    this.http.get<Envelope<Profile>>('/api/candidate/profile').subscribe((res) => {
      this.profile.set(res.data);
      this.profileForm.patchValue({
        ...res.data,
        provinceId: res.data.provinceId ? String(res.data.provinceId) : '',
        municipalityId: res.data.municipalityId ? String(res.data.municipalityId) : '',
        salaryMin: res.data.salaryMin ?? '',
        salaryMax: res.data.salaryMax ?? '',
        salaryPeriod: res.data.salaryPeriod ?? 'MONTH',
      } as never);
      if (res.data.provinceId) this.loadMunicipalities(String(res.data.provinceId));
    });
  }

  onProvince(): void { this.loadMunicipalities(this.profileForm.controls.provinceId.value); }
  saveProfile(): void {
    const raw = this.profileForm.getRawValue();
    this.http.put('/api/candidate/profile', { ...raw, provinceId: num(raw.provinceId), municipalityId: num(raw.municipalityId), salaryMin: num(raw.salaryMin), salaryMax: num(raw.salaryMax) }).subscribe({
      next: () => { this.message.set('Perfil guardado.'); this.reload(); },
      error: (err) => this.error.set(apiError(err)),
    });
  }
  addExperience(): void { this.post('/api/candidate/experiences', blank(this.experienceForm.getRawValue())); }
  addEducation(): void {
    const raw = this.educationForm.getRawValue();
    this.post('/api/candidate/education', { ...blank(raw), startYear: num(raw.startYear), endYear: num(raw.endYear) });
  }
  addCert(): void { this.post('/api/candidate/certifications', { ...blank(this.certForm.getRawValue()), issueYear: num(this.certForm.controls.issueYear.value) }); }
  addLanguage(): void { this.post('/api/candidate/languages', this.languageForm.getRawValue()); }
  remove(path: string): void { this.http.delete(path).subscribe({ next: () => this.reload(), error: (err) => this.error.set(apiError(err)) }); }
  toggleSkill(id: number, checked: boolean): void {
    const current = new Set(this.profile()?.skills.map((skill) => skill.id) ?? []);
    if (checked) current.add(id); else current.delete(id);
    this.http.put('/api/candidate/skills', { skillIds: [...current] }).subscribe({ next: () => this.reload(), error: (err) => this.error.set(apiError(err)) });
  }
  upload(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const data = new FormData();
    data.set('file', file);
    this.http.post('/api/candidate/resumes', data).subscribe({ next: () => this.reload(), error: (err) => this.error.set(apiError(err)) });
  }
  primary(id: string): void { this.http.post(`/api/candidate/resumes/${id}/primary`, {}).subscribe(() => this.reload()); }
  download(id: string): void {
    this.http.get<Envelope<{ url: string }>>(`/api/candidate/resumes/${id}/link`).subscribe((res) => window.open(res.data.url, '_blank', 'noopener'));
  }
  deleteAccount(): void {
    if (!confirm('Esto anonimiza tu cuenta y elimina tu currículum. ¿Continuar?')) return;
    this.http.delete<Envelope<unknown>>('/api/candidate/account').subscribe({
      next: async () => { await this.auth.logout(); await this.router.navigateByUrl('/'); },
      error: (err) => this.error.set(apiError(err)),
    });
  }
  private post(url: string, body: unknown): void { this.http.post(url, body).subscribe({ next: () => this.reload(), error: (err) => this.error.set(apiError(err)) }); }
  private loadMunicipalities(id: string): void {
    if (!id) return;
    this.http.get<Envelope<Named[]>>(`/api/catalog/provinces/${id}/municipalities`).subscribe((res) => this.municipalities.set(res.data));
  }
}

@Component({
  selector: 'app-candidate-applications',
  imports: [RouterLink],
  template: `
    <section class="wrap py-8">
      <h1 class="text-4xl">Mis postulaciones</h1>
      <div class="mt-4 grid gap-3">
        @for (item of items(); track item.id) {
          <article class="card p-4">
            <h2 class="text-xl"><a [routerLink]="['/empleos', item.jobSlug]">{{ item.jobTitle }}</a></h2>
            <p>{{ item.companyName }} · {{ label(item.status) }} · {{ formatDate(item.appliedAt) }}</p>
            @if (item.status !== 'WITHDRAWN' && item.status !== 'HIRED' && item.status !== 'REJECTED') {
              <button class="btn btn-ghost mt-3" type="button" (click)="withdraw(item.id)">Retirar postulación</button>
            }
          </article>
        } @empty { <p class="card p-4">Todavía no te has postulado.</p> }
      </div>
    </section>
  `,
})
export class CandidateApplicationsPage {
  private readonly http = inject(HttpClient);
  readonly items = signal<ApplicationItem[]>([]);
  readonly label = label;
  readonly formatDate = formatDate;
  constructor() { this.load(); }
  withdraw(id: string): void { this.http.post(`/api/candidate/applications/${id}/withdraw`, {}).subscribe(() => this.load()); }
  private load(): void { this.http.get<Envelope<ApplicationItem[]>>('/api/candidate/applications').subscribe((res) => this.items.set(res.data)); }
}

@Component({
  selector: 'app-saved-jobs',
  imports: [JobCardView],
  template: `<section class="wrap py-8"><h1 class="text-4xl">Ofertas guardadas</h1><div class="mt-4 grid gap-3">@for (job of jobs(); track job.id) { <app-job-card [job]="job" /> } @empty { <p>No has guardado ofertas.</p> }</div></section>`,
})
export class SavedJobsPage {
  readonly jobs = signal<JobCard[]>([]);
  constructor() { inject(HttpClient).get<Envelope<JobCard[]>>('/api/candidate/saved-jobs').subscribe((res) => this.jobs.set(res.data)); }
}

function num(value: unknown): number | null { return value === '' || value == null ? null : Number(value); }
function blank(value: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, item === '' ? null : item]));
}

interface Profile {
  headline: string; summary: string; provinceId: number | null; municipalityId: number | null; linkedinUrl: string; portfolioUrl: string;
  salaryMin: number | null; salaryMax: number | null; salaryCurrency: string; salaryPeriod: string | null; openToWork: boolean;
  experiences: { id: string; jobTitle: string; companyName: string; startDate: string; endDate: string | null; currentJob: boolean }[];
  education: { id: string; institution: string; degree: string }[];
  certifications: { id: string; name: string; issuer: string }[];
  languages: { id: string; language: string; proficiency: string }[];
  skills: { id: number; name: string }[];
  resumes: { id: string; filename: string; primary: boolean }[];
}
interface ApplicationItem { id: string; jobSlug: string; jobTitle: string; companyName: string; status: string; appliedAt: string; }
