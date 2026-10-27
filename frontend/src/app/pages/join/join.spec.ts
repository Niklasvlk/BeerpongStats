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

  const open = async (token = 'abc') => {
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
    expect(links).toContain('/register?returnUrl=%2Fjoin%2Fabc');
    expect(links).toContain('/login?returnUrl=%2Fjoin%2Fabc');
    expect(root().querySelector('form')).toBeNull();
  });

  it('is reachable without a login', async () => {
    await open();

    expect(url()).toBe('/join/abc');
  });

  it('a signed-in user joins with the chosen name and lands in the group', async () => {
    signIn();
    const component = await open();

    component.form.patchValue({ displayName: ' Maxi ' });
    component.join();
    const req = http.expectOne({ method: 'POST', url: '/api/invitations/abc/accept' });
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
    const req = http.expectOne('/api/invitations/abc/accept');
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
    http.expectOne('/api/invitations/abc/accept').flush(
      { status: 409, title: 'Conflict', detail: "Der Name 'Max' ist in dieser Gruppe schon vergeben. Wähle einen anderen." },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text()).toContain('schon vergeben');
    expect(url()).toBe('/join/abc');
    expect(root().querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });

  it('says so when the invitation is invalid and offers no way to join', async () => {
    signIn();
    await harness.navigateByUrl('/join/alt');
    http.expectOne('/api/invitations/alt').flush(
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
    http.expectOne('/api/invitations/abc/accept').flush(
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

    http.expectNone('/api/invitations/abc/accept');
  });
});
