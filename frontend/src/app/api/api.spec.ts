import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi } from './auth-api';
import { MatchApi } from './match-api';
import { PlayerApi } from './player-api';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('PlayerApi.list passes the sort parameter', () => {
    TestBed.inject(PlayerApi).list('winrate').subscribe();
    const req = http.expectOne('/api/players?sort=winrate');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('PlayerApi.search encodes the name', () => {
    TestBed.inject(PlayerApi).search('a b').subscribe();
    http.expectOne('/api/players/search?name=a%20b').flush([]);
  });

  it('MatchApi.create posts the match', () => {
    TestBed.inject(MatchApi).create({ winner1: 'Max', loser1: 'Tom' }).subscribe();
    const req = http.expectOne('/api/matches');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ winner1: 'Max', loser1: 'Tom' });
    req.flush({});
  });

  it('AuthApi.restoreSession treats 401 as logged out', () => {
    const auth = TestBed.inject(AuthApi);
    auth.restoreSession().subscribe();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.currentUser()).toBeNull();
  });

  it('AuthApi.logout clears the current user', () => {
    const auth = TestBed.inject(AuthApi);
    auth.login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, name: 'Max' });
    expect(auth.currentUser()?.name).toBe('Max');

    auth.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null);
    expect(auth.currentUser()).toBeNull();
  });
});
