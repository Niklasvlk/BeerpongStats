import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CreateGroupRequest, Group } from './models';

const LAST_GROUP_KEY = 'beerpong.lastGroupId';

// Die Gruppen des angemeldeten Benutzers und die Gruppe, in der er gerade unterwegs ist
@Injectable({ providedIn: 'root' })
export class GroupApi {
  private readonly http = inject(HttpClient);
  private readonly list = signal<Group[]>([]);
  private readonly currentId = signal<number | null>(readLastGroupId());

  readonly groups = this.list.asReadonly();
  // Die zuletzt geöffnete Gruppe, solange der Benutzer noch Mitglied ist
  readonly current = computed(() => this.list().find((group) => group.id === this.currentId()) ?? null);

  load(): Observable<Group[]> {
    return this.http.get<Group[]>('/api/groups').pipe(tap((groups) => this.list.set(groups)));
  }

  create(request: CreateGroupRequest): Observable<Group> {
    return this.http.post<Group>('/api/groups', request).pipe(tap((group) => this.list.update((all) => [...all, group])));
  }

  // Merkt sich die geöffnete Gruppe für die Navigation und für den nächsten Besuch
  open(groupId: number): void {
    this.currentId.set(groupId);
    try {
      localStorage.setItem(LAST_GROUP_KEY, String(groupId));
    } catch {
      // Ohne localStorage (privates Fenster, blockiert) geht nur das Merken verloren
    }
  }

  // Beim Abmelden: die Gruppen gehören zum Benutzer, die zuletzt geöffnete bleibt für den nächsten Login erhalten
  clear(): void {
    this.list.set([]);
  }

  lastOpenedId(): number | null {
    return this.currentId();
  }
}

function readLastGroupId(): number | null {
  try {
    const value = Number(localStorage.getItem(LAST_GROUP_KEY));
    return Number.isInteger(value) && value > 0 ? value : null;
  } catch {
    return null;
  }
}
