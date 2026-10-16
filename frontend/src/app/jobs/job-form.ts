import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { I18nService } from '../core/i18n.service';
import { Country, Envelope, Named } from '../core/models';
import { RichTextEditor, richTextRequired } from './rich-text-editor';
import { SkillPicker } from './skill-picker';

@Component({
  selector: 'app-job-form',
  imports: [ReactiveFormsModule, RouterLink, RichTextEditor, SkillPicker],
  templateUrl: './job-form.html',
})
export class JobFormPage {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly categories = signal<Named[]>([]);
  readonly provinces = signal<Named[]>([]);
  readonly municipalities = signal<Named[]>([]);
  readonly countries = signal<Country[]>([]);
  readonly error = signal<string | null>(null);
  readonly i18n = inject(I18nService);
  readonly editing = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', Validators.required], description: ['', richTextRequired], requirements: [''], responsibilities: [''],
    categoryId: ['', Validators.required], contractType: ['INDEFINITE', Validators.required], workMode: ['ONSITE', Validators.required],
    workSchedule: ['FULL_TIME', Validators.required], experienceLevel: ['NONE', Validators.required], provinceId: [''], municipalityId: [''],
    countryCode: ['DO'], locationLabel: [''], remoteScope: ['ONSITE_ONLY'], geographicRestriction: [''], salaryMin: [''], salaryMax: [''],
    salaryCurrency: ['DOP'], salaryPeriod: ['MONTH'], salaryNegotiable: [false], acceptsNoExperience: [true], expiresAt: [''], skillIds: [[] as number[]], publish: [true],
  });

  constructor() {
    this.http.get<Envelope<Named[]>>('/api/catalog/categories').subscribe((res) => this.categories.set(res.data));
    this.http.get<Envelope<Named[]>>('/api/catalog/provinces').subscribe((res) => this.provinces.set(res.data));
    this.http.get<Envelope<Country[]>>('/api/catalog/countries').subscribe((res) => this.countries.set(res.data));
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'nueva') {
      this.editing.set(true);
      this.http.get<Envelope<Record<string, unknown>>>(`/api/company/jobs/${id}`).subscribe((res) => {
        const job = res.data;
        this.form.patchValue({
          ...job,
          categoryId: String(job['categoryId'] ?? ''),
          provinceId: job['provinceId'] ? String(job['provinceId']) : '',
          municipalityId: job['municipalityId'] ? String(job['municipalityId']) : '',
          salaryMin: job['salaryMin'] ?? '',
          salaryMax: job['salaryMax'] ?? '',
          expiresAt: typeof job['expiresAt'] === 'string' ? job['expiresAt'].slice(0, 16) : '',
          skillIds: (job['skillIds'] as number[]) ?? [],
          publish: job['status'] === 'PUBLISHED',
        } as never);
        if (job['provinceId']) this.loadMunicipalities(String(job['provinceId']));
      });
    }
  }

  onProvince(): void {
    this.form.controls.municipalityId.setValue('');
    this.loadMunicipalities(this.form.controls.provinceId.value);
  }

  submit(): void {
    if (this.form.controls.description.invalid) {
      this.form.controls.description.markAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body = {
      ...raw,
      categoryId: Number(raw.categoryId),
      provinceId: raw.provinceId ? Number(raw.provinceId) : null,
      municipalityId: raw.municipalityId ? Number(raw.municipalityId) : null,
      salaryMin: raw.salaryMin === '' ? null : Number(raw.salaryMin),
      salaryMax: raw.salaryMax === '' ? null : Number(raw.salaryMax),
      expiresAt: raw.expiresAt ? new Date(raw.expiresAt).toISOString() : null,
      acceptsNoExperience: raw.acceptsNoExperience || raw.experienceLevel === 'NONE',
    };
    const id = this.route.snapshot.paramMap.get('id');
    const request = this.editing()
      ? this.http.put<Envelope<{ id: string }>>(`/api/company/jobs/${id}`, body)
      : this.http.post<Envelope<{ id: string }>>('/api/company/jobs', body);
    request.subscribe({
      next: () => void this.router.navigateByUrl('/empresa/ofertas'),
      error: (err) => this.error.set(this.i18n.apiError(err)),
    });
  }

  private loadMunicipalities(provinceId: string): void {
    if (!provinceId) return;
    this.http.get<Envelope<Named[]>>(`/api/catalog/provinces/${provinceId}/municipalities`).subscribe((res) => this.municipalities.set(res.data));
  }
}
