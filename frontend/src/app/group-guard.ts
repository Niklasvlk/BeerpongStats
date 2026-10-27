import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { GroupApi } from './api/group-api';

// Lässt nur Mitglieder in /g/:groupId und merkt sich die geöffnete Gruppe für die Navigation
export const groupGuard: CanActivateFn = (route) => {
  const groups = inject(GroupApi);
  const router = inject(Router);
  const groupId = Number(route.paramMap.get('groupId'));
  const toGroups = router.createUrlTree(['/groups']);

  const enter = (): boolean => {
    if (!groups.groups().some((group) => group.id === groupId)) {
      return false;
    }
    groups.open(groupId);
    return true;
  };

  if (enter()) {
    return true;
  }
  // Die Liste kann veraltet sein (z. B. gerade in einem anderen Tab beigetreten): einmal neu laden
  return groups.load().pipe(
    map(() => enter() || toGroups),
    catchError(() => of(toGroups)),
  );
};
