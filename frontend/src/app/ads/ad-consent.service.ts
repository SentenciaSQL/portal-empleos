import { Injectable, inject, signal } from '@angular/core';
import { AdSenseLoaderService } from './adsense-loader.service';

const STORAGE_KEY = 'empleord.ad-consent';

export type AdConsentChoice = 'granted' | 'denied';

@Injectable({ providedIn: 'root' })
export class AdConsentService {
  private readonly loader = inject(AdSenseLoaderService);
  readonly choice = signal<AdConsentChoice | null>(this.read());

  grant(): void {
    localStorage.setItem(STORAGE_KEY, 'granted');
    this.choice.set('granted');
  }

  deny(): void {
    localStorage.setItem(STORAGE_KEY, 'denied');
    this.choice.set('denied');
    if (this.loader.started()) window.location.reload();
  }

  private read(): AdConsentChoice | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value === 'granted' || value === 'denied' ? value : null;
    } catch {
      return null;
    }
  }
}
