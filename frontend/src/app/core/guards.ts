import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const ok = await auth.ensure();
  return ok ? true : router.createUrlTree(['/entrar'], { queryParams: { returnUrl: state.url } });
};

export const roleGuard = (role: 'CANDIDATE' | 'EMPLOYER' | 'ADMIN'): CanActivateFn => async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const ok = await auth.ensure();
  if (!ok) return router.createUrlTree(['/entrar']);
  return auth.user()?.role === role ? true : router.createUrlTree(['/']);
};
