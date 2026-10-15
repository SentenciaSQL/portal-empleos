import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { JobCard } from '../core/models';
import { formatDate, label, locationText, salaryText } from '../core/format';

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
          @if (job().featured) { <span class="chip">Destacada</span> }
          @if (job().companyVerified) { <span class="chip">Empresa verificada</span> }
          @if (!job().open) { <span class="chip">Cerrada</span> }
        </div>
        <h3 class="mt-1 text-xl"><a class="hover:text-accent" [routerLink]="['/empleos', job().slug]">{{ job().title }}</a></h3>
        <p class="text-sm"><a [routerLink]="['/empresas', job().companySlug]">{{ job().companyName }}</a></p>
        <p class="mt-2 text-sm text-muted">{{ place() }} · {{ label(job().workMode) }} · {{ label(job().contractType) }}</p>
        <p class="mt-1 text-sm font-semibold">{{ pay() }}</p>
        <p class="mt-2 text-xs text-muted">{{ job().category }} · {{ formatDate(job().publishedAt) || 'Sin fecha de publicación' }}</p>
      </div>
    </article>
  `,
})
export class JobCardView {
  readonly job = input.required<JobCard>();
  readonly label = label;
  readonly formatDate = formatDate;
  place = () => locationText(this.job());
  pay = () => salaryText(this.job());
}
