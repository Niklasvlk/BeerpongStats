import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthApi } from '../../api/auth-api';
import { PlayerApi } from '../../api/player-api';
import { errorMessage } from '../../shared/error-message';

@Component({
  selector: 'app-profile',
  imports: [PercentPipe, DecimalPipe],
  templateUrl: './profile.html',
})
export class Profile {
  private readonly auth = inject(AuthApi);
  private readonly players = inject(PlayerApi);
  private readonly router = inject(Router);

  protected readonly player = this.auth.currentUser;
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Statistiken können sich seit dem Login geändert haben
    this.auth.restoreSession().subscribe();
  }

  protected logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/'));
  }

  protected deleteProfile(): void {
    const player = this.player();
    if (!player || !confirm('Willst du dein Profil wirklich löschen?')) return;

    this.players.delete(player.id).subscribe({
      // Die Session überlebt das Löschen serverseitig, deshalb danach ausloggen
      next: () => this.logout(),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
