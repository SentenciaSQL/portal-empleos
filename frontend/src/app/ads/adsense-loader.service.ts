import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AdSenseLoaderService {
  private task: Promise<void> | null = null;
  private client: string | null = null;
  readonly active = signal(false);

  started(): boolean {
    return this.active();
  }

  load(client: string): Promise<void> {
    if (!/^ca-pub-\d{16}$/.test(client) || /^ca-pub-0+$/.test(client)) {
      return Promise.reject(new Error('invalid-publisher'));
    }
    if (this.task && this.client === client) return this.task;
    this.client = client;
    this.task = new Promise((resolve, reject) => {
      if (document.getElementById('empleord-adsense')) {
        this.active.set(true);
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.id = 'empleord-adsense';
      script.async = true;
      script.crossOrigin = 'anonymous';
      script.src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(client)}`;
      script.onload = () => {
        this.active.set(true);
        resolve();
      };
      script.onerror = () => {
        this.task = null;
        script.remove();
        reject(new Error('adsense-unavailable'));
      };
      document.head.appendChild(script);
    });
    return this.task;
  }
}
