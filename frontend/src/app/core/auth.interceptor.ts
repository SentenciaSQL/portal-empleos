import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { I18nService } from './i18n.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const lang = inject(I18nService).lang();
  const token = auth.token();
  const headers: Record<string, string> = { 'Accept-Language': lang };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const authed = req.clone({
    withCredentials: true,
    setHeaders: headers,
  });
  return next(authed).pipe(
    catchError((error) => {
      const refreshable = error.status === 401 && !req.url.includes('/api/auth/');
      if (!refreshable) return throwError(() => error);
      return from(auth.refresh()).pipe(
        switchMap((ok) => {
          if (!ok || !auth.token()) return throwError(() => error);
          return next(req.clone({ withCredentials: true, setHeaders: { Authorization: `Bearer ${auth.token()}`, 'Accept-Language': lang } }));
        }),
      );
    }),
  );
};
