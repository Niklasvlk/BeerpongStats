import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CreateMatchRequest } from '../../api/models';
import { MatchApi } from '../../api/match-api';
import { errorMessage } from '../../shared/error-message';

@Component({
  selector: 'app-new-match',
  imports: [ReactiveFormsModule],
  templateUrl: './new-match.html',
})
export class NewMatch {
  private readonly api = inject(MatchApi);
  private readonly router = inject(Router);

  private readonly cups = () => new FormControl<number | null>(null, [Validators.min(0), Validators.max(10)]);

  protected readonly form = new FormGroup({
    winner1: new FormControl('', { nonNullable: true, validators: Validators.required }),
    winner1Cups: this.cups(),
    winner2: new FormControl('', { nonNullable: true }),
    winner2Cups: this.cups(),
    loser1: new FormControl('', { nonNullable: true, validators: Validators.required }),
    loser1Cups: this.cups(),
    loser2: new FormControl('', { nonNullable: true }),
    loser2Cups: this.cups(),
  });
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);

    this.api.create(this.toRequest()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.submitting.set(false);
      },
    });
  }

  // Leere optionale Felder weglassen, damit das Backend sie als "nicht angegeben" behandelt
  private toRequest(): CreateMatchRequest {
    const v = this.form.getRawValue();
    return {
      winner1: v.winner1.trim(),
      winner2: v.winner2.trim() || undefined,
      loser1: v.loser1.trim(),
      loser2: v.loser2.trim() || undefined,
      winner1Cups: v.winner1Cups ?? undefined,
      winner2Cups: v.winner2Cups ?? undefined,
      loser1Cups: v.loser1Cups ?? undefined,
      loser2Cups: v.loser2Cups ?? undefined,
    };
  }
}
