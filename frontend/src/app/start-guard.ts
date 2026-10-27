import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthApi } from './api/auth-api';
import { GroupApi } from './api/group-api';

// Die Startseite ist nur für Besucher ohne Login. Wer angemeldet ist, landet direkt in seiner Gruppe.
export const startGuard: CanActivateFn = () => {
  if (!inject(AuthApi).currentUser()) {
    return true;
  }
  const groups = inject(GroupApi);
  const all = groups.groups();
  const target = all.find((group) => group.id === groups.lastOpenedId()) ?? (all.length === 1 ? all[0] : null);
  return inject(Router).createUrlTree(target ? ['/g', target.id] : ['/groups']);
};
