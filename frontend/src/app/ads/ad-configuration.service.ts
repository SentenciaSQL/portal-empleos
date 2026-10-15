import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Meta } from '@angular/platform-browser';
import { Envelope } from '../core/models';

export interface AdSlotConfig {
  key: string;
  page: string;
  enabled: boolean;
  adUnitId: string;
}

export interface AdPublicConfig {
  enabled: boolean;
  publisherId: string | null;
  consentRequired: boolean;
  slots: AdSlotConfig[];
}

@Injectable({ providedIn: 'root' })
export class AdConfigurationService {
  private readonly http = inject(HttpClient);
  private readonly meta = inject(Meta);
  readonly config = signal<AdPublicConfig | null>(null);

  constructor() {
    this.http.get<Envelope<AdPublicConfig>>('/api/ads/config').subscribe({
      next: (res) => {
        this.config.set(res.data);
        if (res.data.enabled && res.data.publisherId) {
          this.meta.updateTag({ name: 'google-adsense-account', content: res.data.publisherId });
        }
      },
      error: () => this.config.set({ enabled: false, publisherId: null, consentRequired: true, slots: [] }),
    });
  }

  slot(key: string): AdSlotConfig | null {
    const current = this.config();
    if (!current?.enabled || !current.publisherId) return null;
    return current.slots.find((item) => item.key === key && item.enabled && item.adUnitId) ?? null;
  }
}
