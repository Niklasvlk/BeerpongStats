import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { Player } from './models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly user = signal<Player | null>(null);

  readonly currentUser = this.user.asReadonly();

  // Beim App-Start: besteht noch eine Session? (401 heißt: nicht eingeloggt)
  restoreSession(): Observable<Player | null> {
    return this.http.get<Player>('/api/auth/me').pipe(
      catchError(() => of(null)),
      tap((player) => this.user.set(player)),
    );
  }

  login(name: string, password: string): Observable<Player> {
    return this.http
      .post<Player>('/api/auth/login', { name, password })
      .pipe(tap((player) => this.user.set(player)));
  }

  // Der Server hat die Session beendet (z. B. nach dem Löschen des Profils), hier muss nur der Zustand geleert werden
  forgetUser(): void {
    this.user.set(null);
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(tap(() => this.user.set(null)));
  }
}
