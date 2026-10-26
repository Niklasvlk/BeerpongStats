import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthApi } from '../api/auth-api';
import { Match } from '../api/models';
import { routes } from '../app.routes';
import { withComponentInputBinding } from '@angular/router';

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

describe('pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
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

  it('match details marks the MVP and shows missing cups', async () => {
    await harness.navigateByUrl('/matches/5');
    http.expectOne('/api/matches/5').flush(match);
    await settle();

    expect(text()).toContain('Max');
    expect(text()).toContain('👑 MVP');
    expect(text().match(/MVP/g)?.length).toBe(1);
  });

  it('match details shows an error for an unknown match', async () => {
    await harness.navigateByUrl('/matches/99');
    http.expectOne('/api/matches/99').flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text()).toContain('Match nicht gefunden');
  });

  it('player list searches when a name query parameter is set', async () => {
    await harness.navigateByUrl('/players?name=max');
    http.expectOne('/api/players/search?name=max').flush([]);
  });

  it('player list sorts by the sort query parameter', async () => {
    await harness.navigateByUrl('/players?sort=avgCups');
    http.expectOne('/api/players?sort=avgCups').flush([]);
  });

  it('new match omits blank optional fields from the request', async () => {
    const component = await harness.navigateByUrl('/newMatch');
    const form = (component as unknown as { form: import('@angular/forms').FormGroup }).form;
    form.patchValue({ winner1: ' Max ', loser1: 'Tom', winner1Cups: 6, winner2: '  ' });
    (component as unknown as { submit(): void }).submit();

    const req = http.expectOne('/api/matches');
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
  });

  it('new match shows the backend error message', async () => {
    const component = (await harness.navigateByUrl('/newMatch')) as unknown as {
      form: import('@angular/forms').FormGroup;
      submit(): void;
    };
    component.form.patchValue({ winner1: 'Max', loser1: 'Max' });
    component.submit();
    http.expectOne('/api/matches').flush(
      { status: 400, title: 'Bad Request', detail: 'Ein Spieler darf nicht doppelt vorkommen.' },
      { status: 400, statusText: 'Bad Request' },
    );
    harness.detectChanges();

    expect(text()).toContain('Ein Spieler darf nicht doppelt vorkommen.');
  });

  it('profile redirects to login while logged out', async () => {
    await harness.navigateByUrl('/profile');
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('profile is reachable after login', async () => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, name: 'Max', wins: 0, losses: 0, winRate: 0, avgCups: 0 });

    await harness.navigateByUrl('/profile');
    http.expectOne('/api/auth/me').flush({ id: 1, name: 'Max', wins: 0, losses: 0, winRate: 0, avgCups: 0 });
    expect(TestBed.inject(Router).url).toBe('/profile');
  });
});
