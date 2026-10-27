import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthApi } from '../../api/auth-api';
import { GroupApi } from '../../api/group-api';
import { Group } from '../../api/models';
import { routes } from '../../app.routes';

// Ein Token hat genau das Format, das das Backend erzeugt: 43 Zeichen aus A-Z, a-z, 0-9, "-" und "_"
const TOKEN = 'abcdefghijklmnopqrstuvwxyz0123456789_-ABCDE';
const OTHER_TOKEN = 'x'.repeat(43);

const kumpels: Group = {
  id: 7,
  name: 'Kumpels',
  role: 'MEMBER',
  playerId: 4,
  wins: 0,
  losses: 0,
  winRate: 0,
  avgCups: 0,
  memberCount: 3,
};

describe('join page', () => {
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
  const root = () => harness.routeNativeElement as HTMLElement;
  const text = () => root().textContent ?? '';
  const url = () => TestBed.inject(Router).url;

  const signIn = () => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([]);
  };

  const open = async (token = TOKEN) => {
    const component = (await harness.navigateByUrl(`/join/${token}`)) as unknown as { form: FormGroup; join(): void };
    http.expectOne(`/api/invitations/${token}`).flush({ groupName: 'Kumpels', memberCount: 3 });
    await settle();
    return component;
  };

  it('shows who invites and asks visitors to register or log in, coming back here afterwards', async () => {
    await open();

    expect(text()).toContain('Kumpels');
    expect(text()).toContain('3 Mitglieder');
    expect(text()).toContain('brauchst du einen Account');
    const links = Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(links).toContain(`/register?returnUrl=%2Fjoin%2F${TOKEN}`);
    expect(links).toContain(`/login?returnUrl=%2Fjoin%2F${TOKEN}`);
    expect(root().querySelector('form')).toBeNull();
  });

  it('is reachable without a login', async () => {
    await open();

    expect(url()).toBe(`/join/${TOKEN}`);
  });

  it('a signed-in user joins with the chosen name and lands in the group', async () => {
    signIn();
    const component = await open();

    component.form.patchValue({ displayName: ' Maxi ' });
    component.join();
    const req = http.expectOne({ method: 'POST', url: `/api/invitations/${TOKEN}/accept` });
    expect(req.request.body).toEqual({ displayName: 'Maxi' });
    req.flush(kumpels);

    await vi.waitFor(() => expect(url()).toBe('/g/7'));
    http.expectOne('/api/groups/7/leaderboard').flush([]);
    http.expectOne('/api/groups/7/matches/recent').flush([]);
    expect(TestBed.inject(GroupApi).groups().map((g) => g.id)).toEqual([7]);
  });

  it('joins without a name when none is given', async () => {
    signIn();
    const component = await open();

    component.join();
    const req = http.expectOne(`/api/invitations/${TOKEN}/accept`);
    expect(req.request.body).toEqual({});
    req.flush(kumpels);

    await vi.waitFor(() => expect(url()).toBe('/g/7'));
    http.expectOne('/api/groups/7/leaderboard').flush([]);
    http.expectOne('/api/groups/7/matches/recent').flush([]);
  });

  it('shows the reason when joining is refused and stays on the page', async () => {
    signIn();
    const component = await open();

    component.join();
    http.expectOne(`/api/invitations/${TOKEN}/accept`).flush(
      { status: 409, title: 'Conflict', detail: "Der Name 'Max' ist in dieser Gruppe schon vergeben. Wähle einen anderen." },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text()).toContain('schon vergeben');
    expect(url()).toBe(`/join/${TOKEN}`);
    expect(root().querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });

  it('says so when the invitation is invalid and offers no way to join', async () => {
    signIn();
    await harness.navigateByUrl(`/join/${OTHER_TOKEN}`);
    http.expectOne(`/api/invitations/${OTHER_TOKEN}`).flush(
      { status: 410, title: 'Gone', detail: 'Diese Einladung ist ungültig oder abgelaufen.' },
      { status: 410, statusText: 'Gone' },
    );
    await settle();

    expect(text()).toContain('Diese Einladung ist ungültig oder abgelaufen.');
    expect(root().querySelector('form')).toBeNull();
  });

  it('shows a refusal that appears only when joining', async () => {
    signIn();
    const component = await open();

    component.join();
    http.expectOne(`/api/invitations/${TOKEN}/accept`).flush(
      { status: 410, title: 'Gone', detail: 'Diese Einladung ist ungültig oder abgelaufen.' },
      { status: 410, statusText: 'Gone' },
    );
    await settle();

    expect(text()).toContain('ungültig oder abgelaufen');
  });

  it('a long display name is not sent', async () => {
    signIn();
    const component = await open();

    component.form.patchValue({ displayName: 'a'.repeat(31) });
    component.join();

    http.expectNone(`/api/invitations/${TOKEN}/accept`);
  });

  // --- Präparierte Links ---

  // Angular dekodiert %2F im Routenparameter; der Wert unten wäre als Token "x/../../auth/logout?" im Pfad gelandet
  const CRAFTED = 'x%2F..%2F..%2Fauth%2Flogout%3F';

  it('a crafted link is refused without any request to the API', async () => {
    signIn();
    await harness.navigateByUrl(`/join/${CRAFTED}`);
    await settle();

    expect(text()).toContain('Diese Einladung ist ungültig oder abgelaufen.');
    expect(root().querySelector('form')).toBeNull();
    http.expectNone((req) => req.url.includes('invitations') || req.url.includes('logout'));
  });

  it('a crafted link cannot be used to join even if the form is called directly', async () => {
    signIn();
    const component = (await harness.navigateByUrl(`/join/${CRAFTED}`)) as unknown as { join(): void };
    await settle();

    component.join();

    http.expectNone((req) => req.method === 'POST');
  });

  it('refuses tokens of the wrong length or with other characters', async () => {
    for (const bad of ['abc', 'x'.repeat(42), 'x'.repeat(44), 'x'.repeat(42) + '.', 'x'.repeat(42) + '%25']) {
      await harness.navigateByUrl(`/join/${bad}`);
      await settle();

      expect(text(), bad).toContain('Diese Einladung ist ungültig oder abgelaufen.');
    }
    http.expectNone((req) => req.url.includes('invitations'));
  });

  it('a link with a broken percent sequence never reaches the API either', async () => {
    // Der Router verwirft eine solche Adresse selbst und fällt auf die Startseite zurück
    await harness.navigateByUrl(`/join/${'x'.repeat(42)}%`);
    await settle();

    expect(root().querySelector('form')).toBeNull();
    http.expectNone((req) => req.url.includes('invitations'));
  });

  it('a link of the right form still goes to the API as before', async () => {
    await harness.navigateByUrl(`/join/${OTHER_TOKEN}`);

    http.expectOne(`/api/invitations/${OTHER_TOKEN}`).flush({ groupName: 'Kumpels', memberCount: 3 });
  });
});
