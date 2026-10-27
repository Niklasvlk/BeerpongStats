import { PercentPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { GroupApi } from '../../api/group-api';
import { MemberApi } from '../../api/member-api';
import { Member, Role } from '../../api/models';
import { errorMessage } from '../../shared/error-message';

// Die Spieler der Gruppe. Admins können Rollen ändern und Mitglieder entfernen.
@Component({
  selector: 'app-members-section',
  imports: [PercentPipe],
  templateUrl: './members-section.html',
})
export class MembersSection {
  private readonly api = inject(MemberApi);
  private readonly groups = inject(GroupApi);

  readonly groupId = input.required<number>();
  readonly isAdmin = input.required<boolean>();

  protected readonly members = rxResource({
    params: () => this.groupId(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly list = computed(() => (this.members.hasValue() ? this.members.value() : []));
  protected readonly error = signal<string | null>(null);

  // Der eigene Spieler in dieser Gruppe
  protected readonly myPlayerId = computed(
    () => this.groups.groups().find((group) => group.id === this.groupId())?.playerId ?? null,
  );

  protected setRole(member: Member, role: Role): void {
    this.run(this.api.changeRole(this.groupId(), member.id, role));
  }

  protected remove(member: Member): void {
    const confirmed = confirm(
      `Willst du ${member.name} aus der Gruppe entfernen? Der Spieler bleibt mit seinen Matches erhalten.`,
    );
    if (confirmed) {
      this.run(this.api.remove(this.groupId(), member.id));
    }
  }

  private run(request: Observable<unknown>): void {
    this.error.set(null);
    request.subscribe({
      next: () => this.members.reload(),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
