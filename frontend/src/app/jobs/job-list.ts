import { Component, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { Envelope, JobCard, Named, Page } from '../core/models';
import { JobCardView } from '../shared/job-card';
import { I18nService } from '../core/i18n.service';

@Component({
  selector: 'app-job-list',
  imports: [ReactiveFormsModule, JobCardView],
  templateUrl: './job-list.html',
})
export class JobListPage {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  readonly i18n = inject(I18nService);
  readonly provinces = signal<Named[]>([]);
  readonly municipalities = signal<Named[]>([]);
  readonly categories = signal<Named[]>([]);
  readonly result = signal<Page<JobCard> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    q: '', provinceId: '', municipalityId: '', categoryId: '', contractType: '', workMode: '', workSchedule: '',
    experienceLevel: '', salaryMin: '', salaryMax: '', publishedWithinDays: '', acceptsNoExperience: false, sort: 'recent',
  });

  constructor() {
    const title = inject(Title);
    effect(() => title.setTitle(this.i18n.t('Empleos en República Dominicana | EmpleoRD')));
    this.http.get<Envelope<Named[]>>('/api/catalog/provinces').subscribe((res) => this.provinces.set(res.data));
    this.http.get<Envelope<Named[]>>('/api/catalog/categories').subscribe((res) => this.categories.set(res.data));
    this.route.queryParamMap.subscribe((params) => {
      this.form.patchValue({
        q: params.get('q') ?? '',
        provinceId: params.get('provinceId') ?? '',
        municipalityId: params.get('municipalityId') ?? '',
        categoryId: params.get('categoryId') ?? '',
        contractType: params.get('contractType') ?? '',
        workMode: params.get('workMode') ?? '',
        workSchedule: params.get('workSchedule') ?? '',
        experienceLevel: params.get('experienceLevel') ?? '',
        salaryMin: params.get('salaryMin') ?? '',
        salaryMax: params.get('salaryMax') ?? '',
        publishedWithinDays: params.get('publishedWithinDays') ?? '',
        acceptsNoExperience: params.get('acceptsNoExperience') === 'true',
        sort: params.get('sort') ?? 'recent',
      }, { emitEvent: false });
      if (this.form.controls.provinceId.value) this.loadMunicipalities(this.form.controls.provinceId.value);
      this.load(Number(params.get('page') ?? 0));
    });
  }

  onProvince(): void {
    this.form.controls.municipalityId.setValue('');
    this.loadMunicipalities(this.form.controls.provinceId.value);
  }

  apply(page = 0): void {
    const value = this.form.getRawValue();
    const queryParams: Record<string, string | null> = { page: page ? String(page) : null };
    Object.entries(value).forEach(([key, item]) => {
      queryParams[key] = item === '' || item === false ? null : String(item);
    });
    void this.router.navigate(['/empleos'], { queryParams });
  }

  private loadMunicipalities(provinceId: string): void {
    if (!provinceId) { this.municipalities.set([]); return; }
    this.http.get<Envelope<Named[]>>(`/api/catalog/provinces/${provinceId}/municipalities`).subscribe((res) => this.municipalities.set(res.data));
  }

  private load(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    let params = new HttpParams().set('page', page).set('size', 12);
    Object.entries(this.form.getRawValue()).forEach(([key, value]) => {
      if (value !== '' && value !== false) params = params.set(key, String(value));
    });
    this.http.get<Envelope<Page<JobCard>>>('/api/jobs', { params }).subscribe({
      next: (res) => { this.result.set(res.data); this.loading.set(false); },
      error: (err) => { this.error.set(this.i18n.apiError(err)); this.loading.set(false); },
    });
  }
}
