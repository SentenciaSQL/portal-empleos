import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Meta, Title } from '@angular/platform-browser';
import { I18nService } from '../core/i18n.service';
import { AdSlotComponent } from '../ads/ad-slot';
import { Envelope } from '../core/models';

@Component({
  selector: 'app-company-public',
  imports: [RouterLink, AdSlotComponent],
  template: `
    @if (company(); as item) {
      <article class="wrap max-w-3xl py-10">
        <p class="text-sm text-muted">{{ item.countryName }}</p>
        <h1 class="text-4xl">{{ item.name }}</h1>
        <p class="mt-2">@if (item.verified) { <span class="chip">{{ i18n.t('Empresa verificada') }}</span> } @else { <span class="chip">{{ i18n.t('Sin verificar') }}</span> }</p>
        <p class="mt-4 whitespace-pre-line leading-7">{{ item.description }}</p>
        <p class="mt-3 text-sm">{{ item.industry }} · {{ item.municipality }} {{ item.province }}</p>
        @if (item.website) { <p class="mt-2"><a [href]="item.website" target="_blank" rel="noopener">{{ item.website }}</a></p> }
        <a class="btn btn-primary mt-6" routerLink="/empleos">{{ i18n.t('Ver empleos') }}</a>
        <app-ad-slot slotKey="info-content" />
      </article>
    }
  `,
})
export class CompanyPublicPage {
  readonly company = signal<PublicCompany | null>(null);
  readonly i18n = inject(I18nService);
  constructor() {
    const title = inject(Title);
    const meta = inject(Meta);
    const http = inject(HttpClient);
    inject(ActivatedRoute).paramMap.subscribe((params) => {
      http.get<Envelope<PublicCompany>>(`/api/companies/${params.get('slug')}`).subscribe((res) => {
        this.company.set(res.data);
        title.setTitle(`${res.data.name} | EmpleoRD`);
        meta.updateTag({ name: 'description', content: (res.data.description || res.data.name).slice(0, 160) });
      });
    });
  }
}
interface PublicCompany { name: string; description: string; website: string | null; industry: string | null; province: string | null; municipality: string | null; countryName: string; verified: boolean; }
