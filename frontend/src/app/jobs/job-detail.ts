import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../core/auth.service';
import { I18nService } from '../core/i18n.service';
import { Envelope, JobDetail } from '../core/models';
import { AdSlotComponent } from '../ads/ad-slot';

@Component({
  selector: 'app-job-detail',
  imports: [ReactiveFormsModule, RouterLink, AdSlotComponent],
  templateUrl: './job-detail.html',
})
export class JobDetailPage {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  readonly auth = inject(AuthService);
  readonly i18n = inject(I18nService);
  readonly job = signal<JobDetail | null>(null);
  readonly resumes = signal<{ id: string; filename: string; primary: boolean }[]>([]);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly reportForm = inject(FormBuilder).nonNullable.group({
    reason: ['Contenido sospechoso', Validators.required],
    details: [''],
    email: [''],
  });
  readonly applyForm = inject(FormBuilder).nonNullable.group({ resumeId: [''], coverLetter: [''] });

  constructor() {
    this.route.paramMap.subscribe((params) => this.load(params.get('slug') ?? ''));
  }

  share(network: 'whatsapp' | 'facebook' | 'linkedin'): string {
    const url = encodeURIComponent(location.href);
    const title = encodeURIComponent(this.job()?.card.title ?? 'Oferta de empleo');
    if (network === 'whatsapp') return `https://wa.me/?text=${title}%20${url}`;
    if (network === 'facebook') return `https://www.facebook.com/sharer/sharer.php?u=${url}`;
    return `https://www.linkedin.com/sharing/share-offsite/?url=${url}`;
  }

  save(): void {
    const id = this.job()?.card.id;
    if (!id) return;
    const request = this.job()?.saved
      ? this.http.delete<Envelope<unknown>>(`/api/candidate/saved-jobs/${id}`)
      : this.http.post<Envelope<unknown>>('/api/candidate/saved-jobs', { jobId: id });
    request.subscribe({
      next: () => this.load(this.job()!.card.slug),
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  apply(): void {
    const current = this.job();
    if (!current) return;
    const raw = this.applyForm.getRawValue();
    this.http.post<Envelope<unknown>>(`/api/jobs/${current.card.id}/applications`, {
      resumeId: raw.resumeId || null,
      coverLetter: raw.coverLetter,
    }).subscribe({
      next: () => { this.message.set(this.i18n.t('Postulación enviada.')); this.load(current.card.slug); },
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  report(): void {
    const id = this.job()?.card.id;
    if (!id) return;
    const raw = this.reportForm.getRawValue();
    this.http.post<Envelope<unknown>>(`/api/jobs/${id}/reports`, { ...raw, email: raw.email || null }).subscribe({
      next: (res) => this.message.set(res.message),
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  private load(slug: string): void {
    this.http.get<Envelope<JobDetail>>(`/api/jobs/${slug}`).subscribe({
      next: (res) => {
        this.job.set(res.data);
        const title = `${res.data.card.title} en ${res.data.card.companyName} | EmpleoRD`;
        this.title.setTitle(title);
        const description = res.data.description.slice(0, 160);
        this.meta.updateTag({ name: 'description', content: description });
        this.meta.updateTag({ property: 'og:title', content: title });
        this.meta.updateTag({ property: 'og:description', content: description });
        this.meta.updateTag({ name: 'robots', content: res.data.card.open ? 'index,follow' : 'noindex,follow' });
        if (this.auth.user()?.role === 'CANDIDATE') {
          this.http.get<Envelope<{ resumes: { id: string; filename: string; primary: boolean }[] }>>('/api/candidate/profile')
            .subscribe((profile) => this.resumes.set(profile.data.resumes));
        }
      },
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }
}
