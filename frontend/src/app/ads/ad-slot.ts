import { HttpClient } from '@angular/common/http';
import { Component, ElementRef, afterRenderEffect, computed, inject, input, signal } from '@angular/core';
import { I18nService } from '../core/i18n.service';
import { AdConfigurationService } from './ad-configuration.service';
import { AdConsentService } from './ad-consent.service';
import { AdSenseLoaderService } from './adsense-loader.service';

@Component({
  selector: 'app-ad-slot',
  template: `
    @if (placement() && consent.choice() === 'granted' && !failed()) {
      <aside class="ad-slot" [attr.aria-label]="i18n.t('Publicidad')">
        <p class="ad-label">{{ i18n.t('Publicidad') }}</p>
        <div class="ad-frame">
          <ins class="adsbygoogle"
               style="display:block"
               [attr.data-ad-client]="config.config()?.publisherId"
               [attr.data-ad-slot]="placement()?.adUnitId"
               data-ad-format="auto"
               data-full-width-responsive="true"></ins>
        </div>
      </aside>
    }
  `,
})
export class AdSlotComponent {
  readonly slotKey = input.required<string>();
  readonly i18n = inject(I18nService);
  readonly config = inject(AdConfigurationService);
  readonly consent = inject(AdConsentService);
  private readonly loader = inject(AdSenseLoaderService);
  private readonly http = inject(HttpClient);
  private readonly element = inject(ElementRef<HTMLElement>);
  readonly failed = signal(false);
  readonly placement = computed(() => this.config.slot(this.slotKey()));
  private mounted = false;

  constructor() {
    afterRenderEffect(() => {
      const slot = this.placement();
      const publisher = this.config.config()?.publisherId;
      if (!slot || this.consent.choice() !== 'granted' || !publisher || this.mounted || this.failed()) return;
      const ins = this.element.nativeElement.querySelector('ins.adsbygoogle') as HTMLElement | null;
      if (!ins || ins.dataset['empleordPushed'] === '1') return;
      this.mounted = true;
      void this.loader.load(publisher).then(() => {
        try {
          const win = window as Window & { adsbygoogle?: Record<string, unknown>[] };
          win.adsbygoogle = win.adsbygoogle || [];
          win.adsbygoogle.push({});
          ins.dataset['empleordPushed'] = '1';
          this.watch(slot.key);
        } catch {
          this.failed.set(true);
        }
      }).catch(() => this.failed.set(true));
    });
  }

  private watch(key: string): void {
    const storageKey = `empleord.ad-view.${key}`;
    try {
      if (sessionStorage.getItem(storageKey)) return;
    } catch {
      return;
    }
    const target = this.element.nativeElement.querySelector('.ad-slot');
    if (!target || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver((entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return;
      observer.disconnect();
      try {
        sessionStorage.setItem(storageKey, '1');
      } catch {
        return;
      }
      this.http.post('/api/ads/impressions', { key }).subscribe({ error: () => undefined });
    }, { threshold: 0.5 });
    observer.observe(target);
  }
}
