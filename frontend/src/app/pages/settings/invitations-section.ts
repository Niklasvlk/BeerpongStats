import { DatePipe } from '@angular/common';
import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { InvitationApi } from '../../api/invitation-api';
import { CreatedInvitation, Invitation, InvitationStatus } from '../../api/models';
import { errorMessage } from '../../shared/error-message';

const STATUS_LABELS: Record<InvitationStatus, string> = {
  ACTIVE: 'aktiv',
  EXPIRED: 'abgelaufen',
  REVOKED: 'widerrufen',
  EXHAUSTED: 'aufgebraucht',
};

// Einladungslinks erstellen und widerrufen, nur für Admins
@Component({
  selector: 'app-invitations-section',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './invitations-section.html',
})
export class InvitationsSection {
  private readonly api = inject(InvitationApi);
  private readonly document = inject(DOCUMENT);

  readonly groupId = input.required<number>();

  protected readonly invitations = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly list = computed(() => (this.invitations.hasValue() ? this.invitations.value() : []));

  protected readonly validDays = [1, 3, 7, 14, 30];
  protected readonly form = inject(FormBuilder).group({
    expiresInDays: [7],
    maxUses: [null as number | null, [Validators.min(1), Validators.max(100)]],
  });

  // Der frisch erzeugte Link: Den Token gibt es nur in dieser Antwort, danach nie wieder
  protected readonly created = signal<CreatedInvitation | null>(null);
  protected readonly copyHint = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  protected link(token: string): string {
    return `${this.document.location.origin}/join/${token}`;
  }

  protected statusLabel(invitation: Invitation): string {
    return STATUS_LABELS[invitation.status];
  }

  protected create(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.copyHint.set(null);
    const { expiresInDays, maxUses } = this.form.getRawValue();

    this.api.create(this.groupId(), { expiresInDays: expiresInDays ?? undefined, maxUses: maxUses ?? undefined }).subscribe({
      next: (created) => {
        this.created.set(created);
        this.submitting.set(false);
        this.invitations.reload();
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.submitting.set(false);
      },
    });
  }

  protected async copy(token: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.link(token));
      this.copyHint.set('Link kopiert.');
    } catch {
      this.copyHint.set('Kopieren geht hier nicht. Markiere den Link und kopiere ihn von Hand.');
    }
  }

  protected revoke(invitation: Invitation): void {
    this.error.set(null);
    this.api.revoke(this.groupId(), invitation.id).subscribe({
      next: () => {
        if (this.created()?.id === invitation.id) {
          this.created.set(null);
        }
        this.invitations.reload();
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
