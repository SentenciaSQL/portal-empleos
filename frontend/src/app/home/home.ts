import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Envelope, JobCard, Named, Page } from '../core/models';
import { I18nService } from '../core/i18n.service';
import { JobCardView } from '../shared/job-card';
import { AdSlotComponent } from '../ads/ad-slot';

@Component({
  selector: 'app-home',
  imports: [ReactiveFormsModule, RouterLink, JobCardView, AdSlotComponent],
  templateUrl: './home.html',
})
export class HomePage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  readonly i18n = inject(I18nService);
  readonly categories = signal<Named[]>([]);
  readonly provinces = signal<Named[]>([]);
  readonly jobs = signal<JobCard[]>([]);
  readonly form = this.fb.nonNullable.group({ q: '', provinceId: '', categoryId: '' });

  constructor() {
    this.http.get<Envelope<Named[]>>('/api/catalog/categories').subscribe((res) => this.categories.set(res.data));
    this.http.get<Envelope<Named[]>>('/api/catalog/provinces').subscribe((res) => this.provinces.set(res.data));
    this.http.get<Envelope<Page<JobCard>>>('/api/jobs', { params: { size: 6 } }).subscribe((res) => this.jobs.set(res.data.content));
  }

  search(): void {
    const value = this.form.getRawValue();
    void this.router.navigate(['/empleos'], { queryParams: { q: value.q || null, provinceId: value.provinceId || null, categoryId: value.categoryId || null } });
  }
}
