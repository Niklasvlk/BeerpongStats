import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../api/auth-api';
import { errorMessage } from '../../shared/error-message';
import { safeReturnUrl } from '../../shared/return-url';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  // Kommt aus dem Query-Parameter ?returnUrl, z. B. wenn man über einen Einladungslink hier landet
  readonly returnUrl = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(30)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();

    this.auth.register(username.trim(), password).subscribe({
      next: () => this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/'),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.submitting.set(false);
      },
    });
  }
}
