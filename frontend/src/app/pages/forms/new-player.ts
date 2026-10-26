import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { PlayerApi } from '../../api/player-api';
import { errorMessage } from '../../shared/error-message';

@Component({
  selector: 'app-new-player',
  imports: [ReactiveFormsModule],
  templateUrl: './new-player.html',
})
export class NewPlayer {
  private readonly api = inject(PlayerApi);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(4)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);

    this.api.create(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.submitting.set(false);
      },
    });
  }
}
