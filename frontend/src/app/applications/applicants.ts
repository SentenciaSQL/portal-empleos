import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { I18nService } from '../core/i18n.service';
import { Envelope, Named } from '../core/models';

@Component({
  selector: 'app-applicants',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="wrap py-8">
      <a routerLink="/empresa/ofertas">{{ i18n.t('Volver a ofertas') }}</a>
      <h1 class="text-4xl">{{ i18n.t('Candidatos') }}</h1>
      @if (error()) { <p class="alert mt-3">{{ i18n.live(error()) }}</p> }
      <form class="mt-4 flex flex-wrap gap-2" [formGroup]="filters" (ngSubmit)="load()">
        <input class="rounded-full border border-line px-3" type="number" min="0" [placeholder]="i18n.t('Años mínimos')" formControlName="minYears" [attr.aria-label]="i18n.t('Años mínimos')">
        <select class="rounded-full border border-line px-3" formControlName="skillId" [attr.aria-label]="i18n.t('Habilidad')"><option value="">{{ i18n.t('Habilidad') }}</option>@for (skill of skills(); track skill.id) { <option [value]="skill.id">{{ skill.name }}</option> }</select>
        <input class="rounded-full border border-line px-3" [placeholder]="i18n.t('Formación')" formControlName="education">
        <label class="flex items-center gap-2"><input type="checkbox" formControlName="favoriteOnly"> {{ i18n.t('Favoritos') }}</label>
        <button class="btn btn-ghost" type="submit">{{ i18n.t('Filtrar') }}</button>
      </form>
      <div class="mt-4 grid gap-3">
        @for (item of items(); track item.id) {
          <article class="card grid gap-2 p-4">
            <h2 class="text-xl">{{ item.candidateName }} @if (item.favorite) { <span class="chip">{{ i18n.t('Favorito') }}</span> }</h2>
            <p>{{ item.headline }} · {{ item.province }} · {{ item.yearsExperience }} {{ i18n.t('años de experiencia registrada') }}</p>
            <p class="text-sm">{{ item.email }} · {{ item.phone }}</p>
            <p class="text-sm">{{ item.skills.join(', ') }}</p>
            <p class="text-sm">{{ item.education.join(' · ') }}</p>
            @if (item.coverLetter) { <p class="whitespace-pre-line text-sm">{{ item.coverLetter }}</p> }
            <p>{{ i18n.t('Estado:') }} {{ i18n.label(item.status) }} · {{ i18n.date(item.appliedAt) }}</p>
            <div class="flex flex-wrap gap-2">
              <select [value]="item.status" (change)="update(item, { status: $any($event.target).value })" [attr.aria-label]="i18n.t('Estado')">
                <option value="RECEIVED">{{ i18n.label('RECEIVED') }}</option><option value="IN_REVIEW">{{ i18n.label('IN_REVIEW') }}</option><option value="SHORTLISTED">{{ i18n.label('SHORTLISTED') }}</option><option value="INTERVIEW">{{ i18n.label('INTERVIEW') }}</option><option value="OFFER">{{ i18n.label('OFFER') }}</option><option value="HIRED">{{ i18n.label('HIRED') }}</option><option value="REJECTED">{{ i18n.label('REJECTED') }}</option>
              </select>
              <button class="btn btn-ghost" type="button" (click)="update(item, { favorite: !item.favorite })">{{ item.favorite ? i18n.t('Quitar favorito') : i18n.t('Marcar favorito') }}</button>
              @if (item.resumeAvailable) { <button class="btn btn-ghost" type="button" (click)="resume(item.id)">{{ i18n.t('Ver currículum') }}</button> }
            </div>
            <label class="field"><span>{{ i18n.t('Nota interna') }}</span><textarea [value]="item.internalNote ?? ''" (change)="update(item, { internalNote: $any($event.target).value })"></textarea></label>
          </article>
        } @empty { <p>{{ i18n.t('No hay postulaciones con este filtro.') }}</p> }
      </div>
    </section>
  `,
})
export class ApplicantsPage {
  private readonly http = inject(HttpClient);
  private readonly jobId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  readonly items = signal<Applicant[]>([]);
  readonly skills = signal<Named[]>([]);
  readonly error = signal<string | null>(null);
  readonly i18n = inject(I18nService);
  readonly filters = inject(FormBuilder).nonNullable.group({ minYears: [''], skillId: [''], education: [''], favoriteOnly: [false] });
  constructor() {
    this.http.get<Envelope<Named[]>>('/api/catalog/skills').subscribe((res) => this.skills.set(res.data));
    this.load();
  }
  load(): void {
    const raw = this.filters.getRawValue();
    const params: Record<string, string> = {};
    if (raw.minYears) params['minYears'] = raw.minYears;
    if (raw.skillId) params['skillId'] = raw.skillId;
    if (raw.education) params['education'] = raw.education;
    if (raw.favoriteOnly) params['favoriteOnly'] = 'true';
    this.http.get<Envelope<Applicant[]>>(`/api/company/jobs/${this.jobId}/applications`, { params }).subscribe({
      next: (res) => { this.items.set(res.data); this.error.set(null); },
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }
  update(item: Applicant, body: Record<string, unknown>): void {
    this.http.patch(`/api/company/applications/${item.id}`, body).subscribe({ next: () => this.load(), error: (err) => this.error.set(this.i18n.apiError(err)) });
  }
  resume(id: string): void {
    this.http.get<Envelope<{ url: string }>>(`/api/company/applications/${id}/resume-link`).subscribe((res) => window.open(res.data.url, '_blank', 'noopener'));
  }
}
interface Applicant { id: string; candidateName: string; headline: string | null; province: string | null; email: string | null; phone: string | null; yearsExperience: number; skills: string[]; education: string[]; status: string; coverLetter: string | null; internalNote: string | null; favorite: boolean; resumeAvailable: boolean; appliedAt: string; }
