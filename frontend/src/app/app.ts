import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { I18nService } from './core/i18n.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);
  readonly user = this.auth.user;
  readonly menuOpen = signal(false);
  readonly panelLink = computed(() => {
    const role = this.user()?.role;
    if (role === 'EMPLOYER') return '/empresa';
    if (role === 'ADMIN') return '/admin';
    return '/candidato';
  });

  constructor() {
    void this.auth.ensure().then(() => this.i18n.restore(this.auth.user()?.locale));
  }

  setLang(lang: 'es' | 'en'): void {
    this.i18n.use(lang);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  async logout(): Promise<void> {
    await this.auth.logout();
    this.closeMenu();
    await this.router.navigateByUrl('/');
  }
}
