import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthApi } from './api/auth-api';

export const authGuard: CanActivateFn = () => {
  const loggedIn = inject(AuthApi).currentUser() !== null;
  return loggedIn || inject(Router).createUrlTree(['/login']);
};
