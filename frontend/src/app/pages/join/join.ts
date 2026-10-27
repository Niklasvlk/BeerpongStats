import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../api/auth-api';
import { GroupApi } from '../../api/group-api';
import { InvitationApi } from '../../api/invitation-api';
import { errorMessage } from '../../shared/error-message';
import { isInvitationToken } from '../../shared/invitation-token';

// Das Ziel eines Einladungslinks. Die Vorschau sieht jeder, Beitreten geht nur mit Account.
@Component({
  selector: 'app-join',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './join.html',
})
export class Join {
  private readonly invitations = inject(InvitationApi);
  private readonly groups = inject(GroupApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthApi);

  // Route-Parameter :token
  readonly token = input.required<string>();
  // Angular dekodiert %2F & Co. im Routenparameter. Was kein Token sein kann (z. B. ein präparierter Link mit
  // "..%2F..%2F"), geht nicht an die API, sonst landete es im Pfad der Anfrage.
  protected readonly validToken = computed(() => isInvitationToken(this.token()));
  // Nach dem Login oder der Registrierung geht es genau hierher zurück
  protected readonly returnUrl = computed(() => `/join/${this.token()}`);

  protected readonly preview = rxResource({
    params: () => (this.validToken() ? this.token() : undefined),
    stream: ({ params }) => this.invitations.preview(params),
  });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    displayName: ['', Validators.maxLength(30)],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected errorText(): string {
    return errorMessage(this.preview.error());
  }

  protected join(): void {
    if (!this.validToken() || this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);

    this.groups.join(this.token(), this.form.getRawValue().displayName.trim() || undefined).subscribe({
      next: (group) => this.router.navigate(['/g', group.id]),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.submitting.set(false);
      },
    });
  }
}
