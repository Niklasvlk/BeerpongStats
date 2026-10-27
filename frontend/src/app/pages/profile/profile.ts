import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../api/auth-api';
import { GroupApi } from '../../api/group-api';
import { errorMessage } from '../../shared/error-message';
import { winRateColor } from '../../shared/win-rate-color';

@Component({
  selector: 'app-profile',
  imports: [RouterLink, PercentPipe, DecimalPipe],
  templateUrl: './profile.html',
})
export class Profile {
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  protected readonly user = this.auth.currentUser;
  protected readonly groups = inject(GroupApi).groups;
  protected readonly winRateColor = winRateColor;
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Statistiken können sich seit dem Login geändert haben
    this.auth.restoreSession().subscribe();
  }

  protected logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/'));
  }

  protected deleteAccount(): void {
    const confirmed = confirm(
      'Willst du deinen Account wirklich löschen? Deine Spieler bleiben mit ihren Matches in den Gruppen erhalten, ' +
        'Gruppen ohne weitere Mitglieder werden gelöscht.',
    );
    if (!confirmed) return;

    this.auth.deleteAccount().subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
