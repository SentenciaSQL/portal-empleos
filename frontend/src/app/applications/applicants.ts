import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { apiError, formatDate, label } from '../core/format';
import { Envelope, Named } from '../core/models';

@Component({
  selector: 'app-applicants',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="wrap py-8">
      <a routerLink="/empresa/ofertas">Volver a ofertas</a>
      <h1 class="text-4xl">Candidatos</h1>
      @if (error()) { <p class="alert mt-3">{{ error() }}</p> }
      <form class="mt-4 flex flex-wrap gap-2" [formGroup]="filters" (ngSubmit)="load()">
        <input class="rounded-full border border-line px-3" type="number" min="0" placeholder="Años mínimos" formControlName="minYears" aria-label="Años mínimos">
        <select class="rounded-full border border-line px-3" formControlName="skillId" aria-label="Habilidad"><option value="">Habilidad</option>@for (skill of skills(); track skill.id) { <option [value]="skill.id">{{ skill.name }}</option> }</select>
        <input class="rounded-full border border-line px-3" placeholder="Formación" formControlName="education">
        <label class="flex items-center gap-2"><input type="checkbox" formControlName="favoriteOnly"> Favoritos</label>
        <button class="btn btn-ghost" type="submit">Filtrar</button>
      </form>
      <div class="mt-4 grid gap-3">
        @for (item of items(); track item.id) {
          <article class="card grid gap-2 p-4">
            <h2 class="text-xl">{{ item.candidateName }} @if (item.favorite) { <span class="chip">Favorito</span> }</h2>
            <p>{{ item.headline }} · {{ item.province }} · {{ item.yearsExperience }} años de experiencia registrada</p>
            <p class="text-sm">{{ item.email }} · {{ item.phone }}</p>
            <p class="text-sm">{{ item.skills.join(', ') }}</p>
            <p class="text-sm">{{ item.education.join(' · ') }}</p>
            @if (item.coverLetter) { <p class="whitespace-pre-line text-sm">{{ item.coverLetter }}</p> }
            <p>Estado: {{ label(item.status) }} · {{ formatDate(item.appliedAt) }}</p>
            <div class="flex flex-wrap gap-2">
              <select [value]="item.status" (change)="update(item, { status: $any($event.target).value })" aria-label="Estado">
                <option value="RECEIVED">Recibida</option><option value="IN_REVIEW">En revisión</option><option value="SHORTLISTED">Preseleccionada</option><option value="INTERVIEW">Entrevista</option><option value="OFFER">Oferta laboral</option><option value="HIRED">Contratada</option><option value="REJECTED">Rechazada</option>
              </select>
              <button class="btn btn-ghost" type="button" (click)="update(item, { favorite: !item.favorite })">{{ item.favorite ? 'Quitar favorito' : 'Marcar favorito' }}</button>
              @if (item.resumeAvailable) { <button class="btn btn-ghost" type="button" (click)="resume(item.id)">Ver currículum</button> }
            </div>
            <label class="field"><span>Nota interna</span><textarea [value]="item.internalNote ?? ''" (change)="update(item, { internalNote: $any($event.target).value })"></textarea></label>
          </article>
        } @empty { <p>No hay postulaciones con este filtro.</p> }
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
  readonly label = label;
  readonly formatDate = formatDate;
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
      error: (err) => this.error.set(apiError(err)),
    });
  }
  update(item: Applicant, body: Record<string, unknown>): void {
    this.http.patch(`/api/company/applications/${item.id}`, body).subscribe({ next: () => this.load(), error: (err) => this.error.set(apiError(err)) });
  }
  resume(id: string): void {
    this.http.get<Envelope<{ url: string }>>(`/api/company/applications/${id}/resume-link`).subscribe((res) => window.open(res.data.url, '_blank', 'noopener'));
  }
}
interface Applicant { id: string; candidateName: string; headline: string | null; province: string | null; email: string | null; phone: string | null; yearsExperience: number; skills: string[]; education: string[]; status: string; coverLetter: string | null; internalNote: string | null; favorite: boolean; resumeAvailable: boolean; appliedAt: string; }
