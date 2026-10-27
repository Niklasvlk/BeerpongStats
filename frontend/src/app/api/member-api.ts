import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Member, Role } from './models';

// Die Spieler einer Gruppe samt Rolle. Rollen ändern und entfernen dürfen nur Admins.
@Injectable({ providedIn: 'root' })
export class MemberApi {
  private readonly http = inject(HttpClient);

  list(groupId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`/api/groups/${groupId}/members`);
  }

  changeRole(groupId: number, playerId: number, role: Role): Observable<Member> {
    return this.http.patch<Member>(`/api/groups/${groupId}/members/${playerId}`, { role });
  }

  // Der Spieler bleibt mit seinen Matches in der Gruppe, gehört aber zu keinem Account mehr
  remove(groupId: number, playerId: number): Observable<void> {
    return this.http.delete<void>(`/api/groups/${groupId}/members/${playerId}`);
  }
}
