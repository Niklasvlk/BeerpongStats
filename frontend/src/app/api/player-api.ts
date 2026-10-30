import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Player, PlayerSort } from './models';

// Alle Spieler und Statistiken gehören zu einer Gruppe
@Injectable({ providedIn: 'root' })
export class PlayerApi {
  private readonly http = inject(HttpClient);

  leaderboard(groupId: number): Observable<Player[]> {
    return this.http.get<Player[]>(`/api/groups/${groupId}/leaderboard`);
  }

  list(groupId: number, sort?: PlayerSort): Observable<Player[]> {
    const params = sort ? new HttpParams().set('sort', sort) : undefined;
    return this.http.get<Player[]>(`/api/groups/${groupId}/players`, { params });
  }

  search(groupId: number, name: string): Observable<Player[]> {
    return this.http.get<Player[]>(`/api/groups/${groupId}/players/search`, {
      params: new HttpParams().set('name', name),
    });
  }

  get(groupId: number, id: number): Observable<Player> {
    return this.http.get<Player>(`/api/groups/${groupId}/players/${id}`);
  }
}
