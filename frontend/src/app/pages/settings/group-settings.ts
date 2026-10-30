import { Component, computed, inject, input } from '@angular/core';
import { GroupApi } from '../../api/group-api';
import { GroupSection } from './group-section';
import { InvitationsSection } from './invitations-section';
import { MembersSection } from './members-section';

// Verwaltung einer Gruppe: Mitglieder für alle, Einladungen und Umbenennen/Löschen für Admins
@Component({
  selector: 'app-group-settings',
  imports: [MembersSection, InvitationsSection, GroupSection],
  templateUrl: './group-settings.html',
})
export class GroupSettings {
  private readonly groups = inject(GroupApi);

  // Route-Parameter :groupId der Eltern-Route
  readonly groupId = input.required<string>();
  protected readonly id = computed(() => Number(this.groupId()));

  protected readonly group = computed(() => this.groups.groups().find((group) => group.id === this.id()) ?? null);
  protected readonly isAdmin = computed(() => this.group()?.role === 'ADMIN');
}
