import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { JobCard } from '../core/models';
import { I18nService } from '../core/i18n.service';

@Component({
  selector: 'app-job-card',
  imports: [RouterLink],
  template: `
    <article class="card flex gap-4 border-l-4 border-l-accent p-4">
      <div class="grid h-12 w-12 shrink-0 place-items-center rounded-lg bg-navy font-serif text-lg text-white">
        {{ job().companyName.slice(0, 1) }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2">
          @if (job().featured) { <span class="chip">{{ i18n.t('Destacada') }}</span> }
          @if (job().companyVerified) { <span class="chip">{{ i18n.t('Empresa verificada') }}</span> }
          @if (!job().open) { <span class="chip">{{ i18n.t('Cerrada') }}</span> }
        </div>
        <h3 class="mt-1 text-xl"><a class="hover:text-accent" [routerLink]="['/empleos', job().slug]">{{ job().title }}</a></h3>
        <p class="text-sm"><a [routerLink]="['/empresas', job().companySlug]">{{ job().companyName }}</a></p>
        <p class="mt-2 text-sm text-muted">{{ i18n.place(job()) }} · {{ i18n.label(job().workMode) }} · {{ i18n.label(job().contractType) }}</p>
        <p class="mt-1 text-sm font-semibold">{{ i18n.salary(job()) }}</p>
        <p class="mt-2 text-xs text-muted">{{ job().category }} · {{ i18n.date(job().publishedAt) || i18n.t('Sin fecha de publicación') }}</p>
      </div>
    </article>
  `,
})
export class JobCardView {
  readonly job = input.required<JobCard>();
  readonly i18n = inject(I18nService);
}
