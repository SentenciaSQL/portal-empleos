import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthPayload, Envelope, UserSession } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly userState = signal<UserSession | null>(null);
  readonly user = this.userState.asReadonly();
  private accessToken: string | null = null;
  private pending: Promise<boolean> | null = null;

  token(): string | null {
    return this.accessToken;
  }

  async ensure(): Promise<boolean> {
    if (this.accessToken && this.userState()) return true;
    if (!this.pending) {
      this.pending = this.refresh().finally(() => { this.pending = null; });
    }
    return this.pending;
  }

  async refresh(): Promise<boolean> {
    try {
      const response = await firstValueFrom(this.http.post<Envelope<AuthPayload>>('/api/auth/refresh', {}, { withCredentials: true }));
      this.apply(response.data);
      return true;
    } catch {
      this.accessToken = null;
      this.userState.set(null);
      return false;
    }
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(this.http.post<Envelope<AuthPayload>>('/api/auth/login', { email, password }, { withCredentials: true }));
    this.apply(response.data);
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.http.post('/api/auth/logout', {}, { withCredentials: true })).catch(() => undefined);
    this.accessToken = null;
    this.userState.set(null);
  }

  private apply(payload: AuthPayload): void {
    this.accessToken = payload.accessToken;
    this.userState.set(payload.user);
  }
}
