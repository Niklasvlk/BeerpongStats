import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthApi } from './api/auth-api';

@Component({
  imports: [RouterOutlet, RouterLink],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthApi);
  protected readonly navOpen = signal(false);

  protected toggleNav(): void {
    this.navOpen.update((open) => !open);
  }
}
