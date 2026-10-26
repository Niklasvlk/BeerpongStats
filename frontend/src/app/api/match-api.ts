import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateMatchRequest, Match } from './models';

@Injectable({ providedIn: 'root' })
export class MatchApi {
  private readonly http = inject(HttpClient);

  list(): Observable<Match[]> {
    return this.http.get<Match[]>('/api/matches');
  }

  recent(): Observable<Match[]> {
    return this.http.get<Match[]>('/api/matches/recent');
  }

  get(id: number): Observable<Match> {
    return this.http.get<Match>(`/api/matches/${id}`);
  }

  create(request: CreateMatchRequest): Observable<Match> {
    return this.http.post<Match>('/api/matches', request);
  }
}
