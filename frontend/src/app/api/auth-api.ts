import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { GroupApi } from './group-api';
import { User } from './models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly groups = inject(GroupApi);
  private readonly user = signal<User | null>(null);

  readonly currentUser = this.user.asReadonly();

  // Beim App-Start: besteht noch eine Session? (401 heißt: nicht eingeloggt)
  restoreSession(): Observable<User | null> {
    return this.http.get<User>('/api/auth/me').pipe(
      catchError(() => of(null)),
      tap((user) => this.user.set(user)),
      switchMap((user) => (user ? this.loadGroups(user) : of(null))),
    );
  }

  login(username: string, password: string): Observable<User> {
    return this.http.post<User>('/api/auth/login', { username, password }).pipe(
      tap((user) => this.user.set(user)),
      switchMap((user) => this.loadGroups(user)),
    );
  }

  // Legt den Account an und meldet gleich an
  register(username: string, password: string): Observable<User> {
    return this.http
      .post<User>('/api/auth/register', { username, password })
      .pipe(switchMap(() => this.login(username, password)));
  }

  // Der Server hat die Session beendet (z. B. nach dem Löschen des Accounts), hier muss nur der Zustand geleert werden
  forgetUser(): void {
    this.user.set(null);
    this.groups.clear();
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(tap(() => this.forgetUser()));
  }

  // Das Backend beendet die Session beim Löschen selbst und entfernt dabei das CSRF-Cookie,
  // ein zusätzliches POST /logout wäre ohne Token ein 403
  deleteAccount(): Observable<void> {
    return this.http.delete<void>('/api/auth/me').pipe(tap(() => this.forgetUser()));
  }

  // Ohne Gruppenliste ist die App eingeschränkt, aber der Login selbst hat geklappt
  private loadGroups(user: User): Observable<User> {
    return this.groups.load().pipe(
      catchError(() => of([])),
      map(() => user),
    );
  }
}
