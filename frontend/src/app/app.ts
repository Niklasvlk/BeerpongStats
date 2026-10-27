import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthApi } from './api/auth-api';
import { GroupApi } from './api/group-api';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthApi);
  protected readonly groups = inject(GroupApi);
  private readonly router = inject(Router);
  protected readonly navOpen = signal(false);

  protected toggleNav(): void {
    this.navOpen.update((open) => !open);
  }

  protected switchGroup(event: Event): void {
    const groupId = (event.target as HTMLSelectElement).value;
    this.router.navigate(['/g', groupId]);
  }
}
