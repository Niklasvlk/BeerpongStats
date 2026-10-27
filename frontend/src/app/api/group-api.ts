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

  // Tritt über einen Einladungslink bei; die Gruppe ist danach geöffnet
  join(token: string, displayName?: string): Observable<Group> {
    return this.http.post<Group>(`/api/invitations/${token}/accept`, { displayName }).pipe(
      tap((group) => {
        this.list.update((all) => [...all.filter((g) => g.id !== group.id), group]);
        this.open(group.id);
      }),
    );
  }

  rename(groupId: number, name: string): Observable<Group> {
    return this.http
      .patch<Group>(`/api/groups/${groupId}`, { name })
      .pipe(tap((group) => this.list.update((all) => all.map((g) => (g.id === group.id ? group : g)))));
  }

  // Löscht die Gruppe samt Matches, Spielern und Einladungen
  delete(groupId: number): Observable<void> {
    return this.http.delete<void>(`/api/groups/${groupId}`).pipe(tap(() => this.forget(groupId)));
  }

  leave(groupId: number): Observable<void> {
    return this.http.delete<void>(`/api/groups/${groupId}/members/me`).pipe(tap(() => this.forget(groupId)));
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

  private forget(groupId: number): void {
    this.list.update((all) => all.filter((group) => group.id !== groupId));
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
