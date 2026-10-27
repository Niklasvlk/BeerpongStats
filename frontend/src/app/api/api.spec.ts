import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi } from './auth-api';
import { GroupApi } from './group-api';
import { InvitationApi } from './invitation-api';
import { MemberApi } from './member-api';
import { Group } from './models';
import { MatchApi } from './match-api';
import { PlayerApi } from './player-api';

const group = (id: number, name = 'Kumpels'): Group => ({
  id,
  name,
  role: 'ADMIN',
  playerId: 1,
  wins: 3,
  losses: 1,
  winRate: 0.75,
  avgCups: 4.5,
  memberCount: 3,
});

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('PlayerApi', () => {
    it('scopes every call to the group', () => {
      const api = TestBed.inject(PlayerApi);
      api.leaderboard(7).subscribe();
      api.get(7, 3).subscribe();
      http.expectOne('/api/groups/7/leaderboard').flush([]);
      http.expectOne('/api/groups/7/players/3').flush({});
    });

    it('list passes the sort parameter', () => {
      TestBed.inject(PlayerApi).list(7, 'winrate').subscribe();
      const req = http.expectOne('/api/groups/7/players?sort=winrate');
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('list without a sort has no query', () => {
      TestBed.inject(PlayerApi).list(7).subscribe();
      http.expectOne('/api/groups/7/players').flush([]);
    });

    it('search encodes the name', () => {
      TestBed.inject(PlayerApi).search(7, 'a b').subscribe();
      http.expectOne('/api/groups/7/players/search?name=a%20b').flush([]);
    });
  });

  describe('MatchApi', () => {
    it('scopes every call to the group', () => {
      const api = TestBed.inject(MatchApi);
      api.list(7).subscribe();
      api.recent(7).subscribe();
      api.get(7, 5).subscribe();
      http.expectOne('/api/groups/7/matches').flush([]);
      http.expectOne('/api/groups/7/matches/recent').flush([]);
      http.expectOne('/api/groups/7/matches/5').flush({});
    });

    it('create posts the match to the group', () => {
      TestBed.inject(MatchApi).create(7, { winner1: 'Max', loser1: 'Tom' }).subscribe();
      const req = http.expectOne('/api/groups/7/matches');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ winner1: 'Max', loser1: 'Tom' });
      req.flush({});
    });
  });

  describe('GroupApi', () => {
    it('load stores the groups of the user', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7), group(8, 'Uni')]);

      expect(api.groups().map((g) => g.name)).toEqual(['Kumpels', 'Uni']);
    });

    it('create posts the request and adds the group to the list', () => {
      const api = TestBed.inject(GroupApi);
      api.create({ name: 'Kumpels', displayName: 'Maxi' }).subscribe();
      const req = http.expectOne('/api/groups');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ name: 'Kumpels', displayName: 'Maxi' });
      req.flush(group(7));

      expect(api.groups().map((g) => g.id)).toEqual([7]);
    });

    it('current is the opened group as long as the user is still a member', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7), group(8, 'Uni')]);
      expect(api.current()).toBeNull();

      api.open(8);
      expect(api.current()?.name).toBe('Uni');

      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7)]);
      expect(api.current()).toBeNull();
    });

    it('remembers the opened group across visits', () => {
      TestBed.inject(GroupApi).open(8);

      expect(localStorage.getItem('beerpong.lastGroupId')).toBe('8');
    });

    it('starts with the group opened last time', () => {
      localStorage.setItem('beerpong.lastGroupId', '8');

      expect(TestBed.inject(GroupApi).lastOpenedId()).toBe(8);
    });

    it('ignores a garbled remembered group', () => {
      localStorage.setItem('beerpong.lastGroupId', 'abc');

      expect(TestBed.inject(GroupApi).lastOpenedId()).toBeNull();
    });

    it('still works when the browser storage is not available', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('blocked');
      });
      const api = TestBed.inject(GroupApi);

      expect(() => api.open(7)).not.toThrow();
      expect(api.lastOpenedId()).toBe(7);
    });

    it('join accepts the invitation, adds the group and opens it', () => {
      const api = TestBed.inject(GroupApi);
      api.join('abc', 'Maxi').subscribe();
      const req = http.expectOne('/api/invitations/abc/accept');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ displayName: 'Maxi' });
      req.flush(group(7));

      expect(api.groups().map((g) => g.id)).toEqual([7]);
      expect(api.current()?.id).toBe(7);
    });

    it('join without a display name sends none', () => {
      TestBed.inject(GroupApi).join('abc').subscribe();
      const req = http.expectOne('/api/invitations/abc/accept');
      expect(req.request.body).toEqual({});
      req.flush(group(7));
    });

    it('join replaces a group that is already in the list', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7, 'Alt')]);

      api.join('abc').subscribe();
      http.expectOne('/api/invitations/abc/accept').flush(group(7, 'Neu'));

      expect(api.groups().map((g) => g.name)).toEqual(['Neu']);
    });

    it('rename patches the group and updates the list', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7), group(8, 'Uni')]);

      api.rename(7, 'Neue Crew').subscribe();
      const req = http.expectOne('/api/groups/7');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ name: 'Neue Crew' });
      req.flush(group(7, 'Neue Crew'));

      expect(api.groups().map((g) => g.name)).toEqual(['Neue Crew', 'Uni']);
    });

    it('delete and leave remove the group from the list', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7), group(8, 'Uni'), group(9, 'Dritte')]);

      api.delete(7).subscribe();
      const deletion = http.expectOne('/api/groups/7');
      expect(deletion.request.method).toBe('DELETE');
      deletion.flush(null, { status: 204, statusText: 'No Content' });
      api.leave(8).subscribe();
      const leaving = http.expectOne('/api/groups/8/members/me');
      expect(leaving.request.method).toBe('DELETE');
      leaving.flush(null, { status: 204, statusText: 'No Content' });

      expect(api.groups().map((g) => g.id)).toEqual([9]);
    });

    it('a refused delete keeps the group', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7)]);

      api.delete(7).subscribe({ error: () => undefined });
      http.expectOne('/api/groups/7').flush(null, { status: 403, statusText: 'Forbidden' });

      expect(api.groups()).toHaveLength(1);
    });

    it('clear empties the list but keeps the last opened group for the next login', () => {
      const api = TestBed.inject(GroupApi);
      api.load().subscribe();
      http.expectOne('/api/groups').flush([group(7)]);
      api.open(7);

      api.clear();

      expect(api.groups()).toEqual([]);
      expect(api.current()).toBeNull();
      expect(api.lastOpenedId()).toBe(7);
    });
  });

  describe('MemberApi', () => {
    it('lists the members of the group', () => {
      TestBed.inject(MemberApi).list(7).subscribe();
      http.expectOne('/api/groups/7/members').flush([]);
    });

    it('changeRole patches the role', () => {
      TestBed.inject(MemberApi).changeRole(7, 2, 'ADMIN').subscribe();
      const req = http.expectOne('/api/groups/7/members/2');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ role: 'ADMIN' });
      req.flush({});
    });

    it('remove deletes the member', () => {
      TestBed.inject(MemberApi).remove(7, 2).subscribe();
      const req = http.expectOne('/api/groups/7/members/2');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('tokens in paths', () => {
    // Auch wenn ein Aufrufer nicht prüft: Der Token darf den Pfad nicht verlassen
    it('preview encodes everything that is not a plain token', () => {
      TestBed.inject(InvitationApi).preview('a/../b?c#d').subscribe();
      http.expectOne('/api/invitations/a%2F..%2Fb%3Fc%23d').flush({ groupName: 'x', memberCount: 1 });
    });

    it('join encodes everything that is not a plain token', () => {
      TestBed.inject(GroupApi).join('x/../../auth/logout?').subscribe();
      const req = http.expectOne('/api/invitations/x%2F..%2F..%2Fauth%2Flogout%3F/accept');
      expect(req.request.method).toBe('POST');
      req.flush(group(7));
    });

    it('a real token passes through unchanged', () => {
      const token = 'abcdefghijklmnopqrstuvwxyz0123456789_-ABCDE';
      TestBed.inject(InvitationApi).preview(token).subscribe();
      http.expectOne(`/api/invitations/${token}`).flush({ groupName: 'x', memberCount: 1 });
    });
  });

  describe('InvitationApi', () => {
    it('preview asks for the invitation by its token', () => {
      TestBed.inject(InvitationApi).preview('abc').subscribe();
      const req = http.expectOne('/api/invitations/abc');
      expect(req.request.method).toBe('GET');
      req.flush({ groupName: 'Kumpels', memberCount: 3 });
    });

    it('list, create and revoke work on the group', () => {
      const api = TestBed.inject(InvitationApi);
      api.list(7).subscribe();
      http.expectOne('/api/groups/7/invitations').flush([]);

      api.create(7, { expiresInDays: 3, maxUses: 5 }).subscribe();
      const created = http.expectOne({ method: 'POST', url: '/api/groups/7/invitations' });
      expect(created.request.body).toEqual({ expiresInDays: 3, maxUses: 5 });
      created.flush({ id: 1, token: 't', expiresAt: '2026-01-01T12:00:00Z', maxUses: 5 });

      api.revoke(7, 1).subscribe();
      const revoked = http.expectOne({ method: 'DELETE', url: '/api/groups/7/invitations/1' });
      revoked.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('AuthApi', () => {
    it('restoreSession treats 401 as logged out and loads no groups', () => {
      const auth = TestBed.inject(AuthApi);
      auth.restoreSession().subscribe();
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.currentUser()).toBeNull();
    });

    it('restoreSession loads the groups of a signed-in user', () => {
      const auth = TestBed.inject(AuthApi);
      auth.restoreSession().subscribe();
      http.expectOne('/api/auth/me').flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush([group(7)]);

      expect(auth.currentUser()?.username).toBe('Max');
      expect(TestBed.inject(GroupApi).groups()).toHaveLength(1);
    });

    it('login sends the username and loads the groups', () => {
      const auth = TestBed.inject(AuthApi);
      auth.login('Max', '1234').subscribe();
      const req = http.expectOne('/api/auth/login');
      expect(req.request.body).toEqual({ username: 'Max', password: '1234' });
      req.flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush([group(7)]);

      expect(auth.currentUser()?.username).toBe('Max');
      expect(TestBed.inject(GroupApi).groups()).toHaveLength(1);
    });

    it('login stays signed in when the groups cannot be loaded', () => {
      const auth = TestBed.inject(AuthApi);
      let result: unknown;
      auth.login('Max', '1234').subscribe((user) => (result = user));
      http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush(null, { status: 500, statusText: 'Server Error' });

      expect(result).toEqual({ id: 1, username: 'Max' });
      expect(auth.currentUser()?.username).toBe('Max');
      expect(TestBed.inject(GroupApi).groups()).toEqual([]);
    });

    it('a failed login leaves nobody signed in', () => {
      const auth = TestBed.inject(AuthApi);
      auth.login('Max', 'falsch').subscribe({ error: () => undefined });
      http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.currentUser()).toBeNull();
    });

    it('register creates the account and then signs in', () => {
      const auth = TestBed.inject(AuthApi);
      auth.register('Max', '1234').subscribe();
      const register = http.expectOne('/api/auth/register');
      expect(register.request.body).toEqual({ username: 'Max', password: '1234' });
      register.flush({ id: 1, username: 'Max' });
      http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush([]);

      expect(auth.currentUser()?.username).toBe('Max');
    });

    it('a failed registration does not sign in', () => {
      const auth = TestBed.inject(AuthApi);
      auth.register('Max', '1234').subscribe({ error: () => undefined });
      http.expectOne('/api/auth/register').flush(null, { status: 409, statusText: 'Conflict' });

      http.expectNone('/api/auth/login');
      expect(auth.currentUser()).toBeNull();
    });

    it('logout clears the user and the groups', () => {
      const auth = TestBed.inject(AuthApi);
      auth.login('Max', '1234').subscribe();
      http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush([group(7)]);

      auth.logout().subscribe();
      http.expectOne('/api/auth/logout').flush(null);

      expect(auth.currentUser()).toBeNull();
      expect(TestBed.inject(GroupApi).groups()).toEqual([]);
    });

    it('deleteAccount calls the backend and forgets the user without a logout request', () => {
      const auth = TestBed.inject(AuthApi);
      auth.login('Max', '1234').subscribe();
      http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush([group(7)]);

      auth.deleteAccount().subscribe();
      const req = http.expectOne('/api/auth/me');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(auth.currentUser()).toBeNull();
      expect(TestBed.inject(GroupApi).groups()).toEqual([]);
      http.expectNone('/api/auth/logout');
    });

    it('a failed deleteAccount keeps the user', () => {
      const auth = TestBed.inject(AuthApi);
      auth.login('Max', '1234').subscribe();
      http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
      http.expectOne('/api/groups').flush([]);

      auth.deleteAccount().subscribe({ error: () => undefined });
      http.expectOne('/api/auth/me').flush(null, { status: 409, statusText: 'Conflict' });

      expect(auth.currentUser()?.username).toBe('Max');
    });
  });
});
