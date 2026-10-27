import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateMatchRequest, Match } from './models';

// Alle Matches gehören zu einer Gruppe
@Injectable({ providedIn: 'root' })
export class MatchApi {
  private readonly http = inject(HttpClient);

  list(groupId: number): Observable<Match[]> {
    return this.http.get<Match[]>(`/api/groups/${groupId}/matches`);
  }

  recent(groupId: number): Observable<Match[]> {
    return this.http.get<Match[]>(`/api/groups/${groupId}/matches/recent`);
  }

  get(groupId: number, id: number): Observable<Match> {
    return this.http.get<Match>(`/api/groups/${groupId}/matches/${id}`);
  }

  create(groupId: number, request: CreateMatchRequest): Observable<Match> {
    return this.http.post<Match>(`/api/groups/${groupId}/matches`, request);
  }
}
