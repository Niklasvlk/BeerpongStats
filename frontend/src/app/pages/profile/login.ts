import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthApi } from '../../api/auth-api';
import { errorMessage } from '../../shared/error-message';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) return;
    const { name, password } = this.form.getRawValue();

    this.auth.login(name, password).subscribe({
      next: () => this.router.navigateByUrl('/profile'),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
