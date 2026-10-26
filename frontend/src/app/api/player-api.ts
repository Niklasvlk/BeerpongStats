import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreatePlayerRequest, Player, PlayerSort } from './models';

@Injectable({ providedIn: 'root' })
export class PlayerApi {
  private readonly http = inject(HttpClient);

  leaderboard(): Observable<Player[]> {
    return this.http.get<Player[]>('/api/leaderboard');
  }

  list(sort?: PlayerSort): Observable<Player[]> {
    const params = sort ? new HttpParams().set('sort', sort) : undefined;
    return this.http.get<Player[]>('/api/players', { params });
  }

  search(name: string): Observable<Player[]> {
    return this.http.get<Player[]>('/api/players/search', { params: new HttpParams().set('name', name) });
  }

  get(id: number): Observable<Player> {
    return this.http.get<Player>(`/api/players/${id}`);
  }

  create(request: CreatePlayerRequest): Observable<Player> {
    return this.http.post<Player>('/api/players', request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/players/${id}`);
  }
}
