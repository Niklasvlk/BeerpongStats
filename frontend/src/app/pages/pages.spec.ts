import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthApi } from '../api/auth-api';
import { Group, Match, Player } from '../api/models';
import { routes } from '../app.routes';

const match: Match = {
  id: 5,
  winner1: { id: 1, name: 'Max' },
  winner2: null,
  loser1: { id: 2, name: 'Tom' },
  loser2: null,
  winner1Cups: 6,
  winner2Cups: null,
  loser1Cups: 3,
  loser2Cups: null,
  mvpCups: 6,
  playedAt: '2026-01-01T12:00:00',
};

const max: Player = { id: 1, name: 'Max', wins: 0, losses: 0, winRate: 0, avgCups: 0, createdAt: '2026-01-01T12:00:00Z' };
const tom: Player = { ...max, id: 2, name: 'Tom' };

const kumpels: Group = {
  id: 7,
  name: 'Kumpels',
  role: 'ADMIN',
  playerId: 1,
  wins: 3,
  losses: 1,
  winRate: 0.75,
  avgCups: 4.5,
  memberCount: 3,
};
const uni: Group = { ...kumpels, id: 8, name: 'Uni', role: 'MEMBER' };

const serverError = { status: 500, statusText: 'Server Error' };

describe('pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  const text = () => (harness.routeNativeElement as HTMLElement).textContent ?? '';
  const url = () => TestBed.inject(Router).url;

  const signIn = (groups: Group[] = [kumpels]) => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush(groups);
  };

  // Die Startseite einer Gruppe lädt Leaderboard und letzte Matches
  const flushGroupHome = (groupId: number) => {
    http.expectOne(`/api/groups/${groupId}/leaderboard`).flush([]);
    http.expectOne(`/api/groups/${groupId}/matches/recent`).flush([]);
  };

  // --- Seiten einer Gruppe ---

  it('match details marks the MVP and shows missing cups', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/matches/5');
    http.expectOne('/api/groups/7/matches/5').flush(match);
    await settle();

    expect(text()).toContain('Max');
    expect(text()).toContain('👑 MVP');
    expect(text().match(/MVP/g)?.length).toBe(1);
  });

  it('match details shows an error for an unknown match', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/matches/99');
    http.expectOne('/api/groups/7/matches/99').flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text()).toContain('Match nicht gefunden');
  });

  it('player list searches when a name query parameter is set', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/players?name=max');
    http.expectOne('/api/groups/7/players/search?name=max').flush([]);
  });

  it('player list sorts by the sort query parameter', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/players?sort=avgCups');
    http.expectOne('/api/groups/7/players?sort=avgCups').flush([]);
  });

  it('player list links the players inside the group', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/players');
    http.expectOne('/api/groups/7/players').flush([max]);
    await settle();

    const link = (harness.routeNativeElement as HTMLElement).querySelector('.player-card')!;
    expect(link.getAttribute('href')).toBe('/g/7/players/1');
  });

  it('player details load the player of the group', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/players/1');
    http.expectOne('/api/groups/7/players/1').flush(max);
    await settle();

    expect(text()).toContain('Max');
  });

  it('home renders the leaderboard and recent matches of the group', async () => {
    signIn();
    await harness.navigateByUrl('/g/7');
    http.expectOne('/api/groups/7/leaderboard').flush([{ ...max, winRate: 0.5 }]);
    http.expectOne('/api/groups/7/matches/recent').flush([match]);
    await settle();

    expect(text()).toContain('Max');
    expect(text()).toContain('50.00%');
    expect(text()).toContain('Tom');
    const card = (harness.routeNativeElement as HTMLElement).querySelector('.match-card')!;
    expect(card.getAttribute('href')).toBe('/g/7/matches/5');
  });

  it('home survives failing API calls and says so', async () => {
    signIn();
    await harness.navigateByUrl('/g/7');
    http.expectOne('/api/groups/7/leaderboard').flush(null, serverError);
    http.expectOne('/api/groups/7/matches/recent').flush(null, serverError);
    await settle();

    expect(text()).toContain('Leaderboard');
    expect(text()).toContain('Das Leaderboard konnte nicht geladen werden.');
    expect(text()).toContain('Die letzten Matches konnten nicht geladen werden.');
  });

  it('match list survives a failing API call and says so', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/matches');
    http.expectOne('/api/groups/7/matches').flush(null, serverError);
    await settle();

    expect(text()).toContain('All Matches');
    expect(text()).toContain('Die Matches konnten nicht geladen werden.');
  });

  it('player list survives a failing API call and says so', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/players');
    http.expectOne('/api/groups/7/players').flush(null, serverError);
    await settle();

    expect(text()).toContain('Die Spieler konnten nicht geladen werden.');
  });

  it('switching to another group loads that group', async () => {
    signIn([kumpels, uni]);
    await harness.navigateByUrl('/g/7');
    flushGroupHome(7);

    await harness.navigateByUrl('/g/8');
    flushGroupHome(8);
  });

  // --- Neues Match ---

  it('new match suggests the players of the group', async () => {
    signIn();
    await harness.navigateByUrl('/g/7/newMatch');
    http.expectOne('/api/groups/7/players?sort=name').flush([max, tom]);
    await settle();

    const options = Array.from((harness.routeNativeElement as HTMLElement).querySelectorAll('datalist option'));
    expect(options.map((o) => o.getAttribute('value'))).toEqual(['Max', 'Tom']);
  });

  it('new match still works when the suggestions cannot be loaded', async () => {
    signIn();
    const component = (await harness.navigateByUrl('/g/7/newMatch')) as unknown as { form: FormGroup; submit(): void };
    http.expectOne('/api/groups/7/players?sort=name').flush(null, serverError);
    await settle();

    component.form.patchValue({ winner1: 'Max', loser1: 'Tom' });
    component.submit();
    http.expectOne('/api/groups/7/matches').flush(match);
  });

  it('new match omits blank optional fields and returns to the group', async () => {
    signIn();
    const component = (await harness.navigateByUrl('/g/7/newMatch')) as unknown as { form: FormGroup; submit(): void };
    http.expectOne('/api/groups/7/players?sort=name').flush([]);
    component.form.patchValue({ winner1: ' Max ', loser1: 'Tom', winner1Cups: 6, winner2: '  ' });
    component.submit();

    const req = http.expectOne('/api/groups/7/matches');
    expect(req.request.body).toEqual({
      winner1: 'Max',
      winner2: undefined,
      loser1: 'Tom',
      loser2: undefined,
      winner1Cups: 6,
      winner2Cups: undefined,
      loser1Cups: undefined,
      loser2Cups: undefined,
    });
    req.flush(match);

    await vi.waitFor(() => expect(url()).toBe('/g/7'));
    flushGroupHome(7);
  });

  it('new match shows the backend error message', async () => {
    signIn();
    const component = (await harness.navigateByUrl('/g/7/newMatch')) as unknown as { form: FormGroup; submit(): void };
    http.expectOne('/api/groups/7/players?sort=name').flush([]);
    component.form.patchValue({ winner1: 'Max', loser1: 'Max' });
    component.submit();
    http.expectOne('/api/groups/7/matches').flush(
      { status: 400, title: 'Bad Request', detail: 'Ein Spieler darf nicht doppelt vorkommen.' },
      { status: 400, statusText: 'Bad Request' },
    );
    harness.detectChanges();

    expect(text()).toContain('Ein Spieler darf nicht doppelt vorkommen.');
  });

  // --- Zugriff ---

  it('a non-member is sent to the group list after a fresh look at the memberships', async () => {
    signIn([kumpels]);

    const navigation = harness.navigateByUrl('/g/99');
    (await vi.waitFor(() => http.expectOne('/api/groups'))).flush([kumpels]);
    await navigation;
    // Die Gruppenliste lädt sich selbst nochmal
    http.expectOne('/api/groups').flush([kumpels]);
    await settle();

    expect(url()).toBe('/groups');
  });

  it('a group joined elsewhere is found by the fresh look', async () => {
    signIn([]);

    const navigation = harness.navigateByUrl('/g/7');
    (await vi.waitFor(() => http.expectOne('/api/groups'))).flush([kumpels]);
    await navigation;
    flushGroupHome(7);

    expect(url()).toBe('/g/7');
  });

  it('a failing membership check sends the user to the group list', async () => {
    signIn([]);

    const navigation = harness.navigateByUrl('/g/7');
    (await vi.waitFor(() => http.expectOne('/api/groups'))).flush(null, serverError);
    await navigation;
    http.expectOne('/api/groups').flush(null, serverError);
    await settle();

    expect(url()).toBe('/groups');
    expect(text()).toContain('Die Gruppen konnten nicht geladen werden.');
  });

  it('group pages redirect to the login with the way back while logged out', async () => {
    await harness.navigateByUrl('/g/7/matches');

    expect(url()).toBe('/login?returnUrl=%2Fg%2F7%2Fmatches');
  });

  it('profile and group list redirect to the login while logged out', async () => {
    await harness.navigateByUrl('/profile');
    expect(url()).toBe('/login?returnUrl=%2Fprofile');

    await harness.navigateByUrl('/groups');
    expect(url()).toBe('/login?returnUrl=%2Fgroups');
  });

  // --- Startseite ---

  it('the start page greets visitors who are not signed in', async () => {
    await harness.navigateByUrl('/');

    expect(url()).toBe('/');
    expect(text()).toContain('Register');
    expect(text()).toContain('Login');
  });

  it('the start page opens the only group of a signed-in user', async () => {
    signIn([kumpels]);

    await harness.navigateByUrl('/');
    flushGroupHome(7);

    expect(url()).toBe('/g/7');
  });

  it('the start page opens the group used last when there are several', async () => {
    localStorage.setItem('beerpong.lastGroupId', '8');
    signIn([kumpels, uni]);

    await harness.navigateByUrl('/');
    flushGroupHome(8);

    expect(url()).toBe('/g/8');
  });

  it('the start page asks which group when there are several and none was used last', async () => {
    signIn([kumpels, uni]);

    await harness.navigateByUrl('/');
    http.expectOne('/api/groups').flush([kumpels, uni]);
    await settle();

    expect(url()).toBe('/groups');
  });

  it('the start page leads a user without groups to the group list', async () => {
    signIn([]);

    await harness.navigateByUrl('/');
    http.expectOne('/api/groups').flush([]);
    await settle();

    expect(url()).toBe('/groups');
    expect(text()).toContain('Du bist noch in keiner Gruppe');
  });

  // --- Gruppenliste ---

  it('the group list shows role, members and stats', async () => {
    signIn([kumpels, uni]);
    await harness.navigateByUrl('/groups');
    http.expectOne('/api/groups').flush([kumpels, uni]);
    await settle();

    expect(text()).toContain('Kumpels');
    expect(text()).toContain('3 Mitglieder');
    expect(text()).toContain('75.00%');
    expect(text().match(/Admin/g)?.length).toBe(1);
  });

  it('creating a group opens it', async () => {
    signIn([]);
    const component = (await harness.navigateByUrl('/groups')) as unknown as { form: FormGroup; submit(): void };
    http.expectOne('/api/groups').flush([]);

    component.form.patchValue({ name: ' Kumpels ', displayName: '  ' });
    component.submit();
    const req = http.expectOne({ method: 'POST', url: '/api/groups' });
    expect(req.request.body).toEqual({ name: 'Kumpels', displayName: undefined });
    req.flush(kumpels);

    await vi.waitFor(() => expect(url()).toBe('/g/7'));
    flushGroupHome(7);
  });

  it('creating a group shows the reason when it is refused', async () => {
    signIn([]);
    const component = (await harness.navigateByUrl('/groups')) as unknown as { form: FormGroup; submit(): void };
    http.expectOne('/api/groups').flush([]);

    component.form.patchValue({ name: 'Eins zu viel' });
    component.submit();
    http.expectOne({ method: 'POST', url: '/api/groups' }).flush(
      { status: 409, title: 'Conflict', detail: 'Du kannst höchstens in 20 Gruppen Mitglied sein.' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text()).toContain('höchstens in 20 Gruppen');
    expect(url()).toBe('/groups');
  });

  // --- Login und Registrierung ---

  const fillAndSubmit = (component: unknown, value: Record<string, string>) => {
    const page = component as { form: FormGroup; submit(): void };
    page.form.patchValue(value);
    page.submit();
  };

  it('login returns to the page the user came from', async () => {
    const component = await harness.navigateByUrl('/login?returnUrl=%2Fg%2F7%2Fmatches');
    fillAndSubmit(component, { username: ' Max ', password: '1234' });

    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ username: 'Max', password: '1234' });
    login.flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([kumpels]);

    await vi.waitFor(() => expect(url()).toBe('/g/7/matches'));
    http.expectOne('/api/groups/7/matches').flush([]);
  });

  it('login ignores a return address that leaves the app', async () => {
    const component = await harness.navigateByUrl('/login?returnUrl=https%3A%2F%2Fevil.example');
    fillAndSubmit(component, { username: 'Max', password: '1234' });
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([kumpels]);

    await vi.waitFor(() => expect(url()).toBe('/g/7'));
    flushGroupHome(7);
  });

  it('login shows the reason for a failed attempt', async () => {
    const component = await harness.navigateByUrl('/login');
    fillAndSubmit(component, { username: 'Max', password: 'falsch' });
    http.expectOne('/api/auth/login').flush(
      { status: 401, title: 'Unauthorized', detail: 'Benutzername oder Passwort falsch' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await settle();

    expect(text()).toContain('Benutzername oder Passwort falsch');
    expect(TestBed.inject(AuthApi).currentUser()).toBeNull();
  });

  it('registration signs in and leads a new user to the group list', async () => {
    const component = await harness.navigateByUrl('/register');
    fillAndSubmit(component, { username: 'Max', password: '1234' });

    http.expectOne('/api/auth/register').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([]);

    await vi.waitFor(() => expect(url()).toBe('/groups'));
    http.expectOne('/api/groups').flush([]);
  });

  it('registration through a link continues where the link pointed', async () => {
    const component = await harness.navigateByUrl('/register?returnUrl=%2Fg%2F7');
    fillAndSubmit(component, { username: 'Max', password: '1234' });

    http.expectOne('/api/auth/register').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([kumpels]);

    await vi.waitFor(() => expect(url()).toBe('/g/7'));
    flushGroupHome(7);
  });

  it('registration shows the reason when the name is taken', async () => {
    const component = await harness.navigateByUrl('/register');
    fillAndSubmit(component, { username: 'Max', password: '1234' });
    http.expectOne('/api/auth/register').flush(
      { status: 409, title: 'Conflict', detail: "Ein Benutzer mit dem Namen 'Max' existiert bereits." },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text()).toContain('existiert bereits');
    http.expectNone('/api/auth/login');
  });

  it('registration needs a password of at least four characters', async () => {
    const component = (await harness.navigateByUrl('/register')) as unknown as { form: FormGroup; submit(): void };
    component.form.patchValue({ username: 'Max', password: '123' });
    component.submit();

    http.expectNone('/api/auth/register');
  });

  // --- Profil ---

  const openProfile = async () => {
    signIn();
    await harness.navigateByUrl('/profile');
    // Die Statistiken werden beim Öffnen neu geladen
    http.expectOne('/api/auth/me').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([kumpels]);
    await settle();
  };

  it('profile shows the account and the stats per group', async () => {
    await openProfile();

    expect(text()).toContain('Max');
    expect(text()).toContain('Kumpels');
    expect(text()).toContain('Wins: 3');
    expect(text()).toContain('75.00%');
  });

  it('deleting the account ends locally without another logout request', async () => {
    await openProfile();
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    (harness.routeNativeElement as HTMLElement).querySelector<HTMLButtonElement>('.btn-danger')!.click();
    // Das Backend beendet die Session beim Löschen selbst; ein POST /api/auth/logout hätte kein CSRF-Token mehr
    http.expectOne({ method: 'DELETE', url: '/api/auth/me' }).flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    expect(TestBed.inject(AuthApi).currentUser()).toBeNull();
    await vi.waitFor(() => expect(url()).toBe('/'));
    http.expectNone('/api/auth/logout');
  });

  it('a refused account deletion keeps the user signed in and shows the reason', async () => {
    await openProfile();
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    (harness.routeNativeElement as HTMLElement).querySelector<HTMLButtonElement>('.btn-danger')!.click();
    http.expectOne({ method: 'DELETE', url: '/api/auth/me' }).flush(
      { status: 409, title: 'Conflict', detail: "In der Gruppe 'Kumpels' bist du der letzte Admin." },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(TestBed.inject(AuthApi).currentUser()?.username).toBe('Max');
    expect(url()).toBe('/profile');
    expect(text()).toContain('der letzte Admin');
  });

  it('cancelling the confirmation deletes nothing', async () => {
    await openProfile();
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    (harness.routeNativeElement as HTMLElement).querySelector<HTMLButtonElement>('.btn-danger')!.click();

    http.expectNone('/api/auth/me');
    expect(TestBed.inject(AuthApi).currentUser()?.username).toBe('Max');
  });

  it('logout leads back to the start page', async () => {
    await openProfile();

    (harness.routeNativeElement as HTMLElement).querySelector<HTMLButtonElement>('.btn-primary')!.click();
    http.expectOne({ method: 'POST', url: '/api/auth/logout' }).flush(null);
    await settle();

    expect(TestBed.inject(AuthApi).currentUser()).toBeNull();
    await vi.waitFor(() => expect(url()).toBe('/'));
  });
});
