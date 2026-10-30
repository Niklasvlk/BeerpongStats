import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthApi } from './api/auth-api';

// Ohne Login geht es zur Anmeldung und danach dorthin zurück, wo man hinwollte
export const authGuard: CanActivateFn = (_route, state) => {
  const loggedIn = inject(AuthApi).currentUser() !== null;
  return loggedIn || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
