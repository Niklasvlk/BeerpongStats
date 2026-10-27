import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthApi } from '../../api/auth-api';
import { GroupApi } from '../../api/group-api';
import { Group, Invitation, Member } from '../../api/models';
import { routes } from '../../app.routes';

const admin: Group = {
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
const plainMember: Group = { ...admin, role: 'MEMBER', playerId: 2 };

const member = (id: number, name: string, role: Member['role'], active = true): Member => ({
  id,
  name,
  role,
  active,
  wins: 2,
  losses: 1,
  winRate: 0.5,
  avgCups: 4,
  createdAt: '2026-01-01T12:00:00Z',
});
const alice = member(1, 'Alice', 'ADMIN');
const bob = member(2, 'Bob', 'MEMBER');
const carl = member(3, 'Carl', 'MEMBER', false);

const invitation = (id: number, status: Invitation['status'], uses = 0, maxUses: number | null = null): Invitation => ({
  id,
  createdAt: '2026-01-01T12:00:00Z',
  expiresAt: '2026-01-08T12:00:00Z',
  maxUses,
  uses,
  status,
});

const noContent = { status: 204, statusText: 'No Content' };

describe('group settings', () => {
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

  afterEach(() => {
    vi.restoreAllMocks();
    http.verify();
  });

  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  const root = () => harness.routeNativeElement as HTMLElement;
  const text = () => root().textContent ?? '';
  const url = () => TestBed.inject(Router).url;
  const button = (label: string) =>
    Array.from(root().querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement | undefined;
  const click = (label: string) => {
    const target = button(label);
    expect(target, `Button "${label}"`).toBeDefined();
    target!.click();
  };

  // Ein reload() der Resource stößt die Anfrage erst beim nächsten Change-Detection-Lauf an
  const expectReload = (reloadUrl: string) =>
    vi.waitFor(() => {
      harness.detectChanges();
      return http.expectOne(reloadUrl);
    });

  // Öffnet die Settings-Seite; Admins laden zusätzlich die Einladungen
  const open = async (group: Group, members: Member[], invitations: Invitation[] = []) => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([group]);

    await harness.navigateByUrl('/g/7/settings');
    http.expectOne('/api/groups/7/members').flush(members);
    if (group.role === 'ADMIN') {
      http.expectOne('/api/groups/7/invitations').flush(invitations);
    }
    await settle();
  };

  // --- Mitglieder ---

  it('an admin sees the members with their roles and the buttons to manage them', async () => {
    await open(admin, [alice, bob, carl]);

    expect(text()).toContain('Alice');
    expect(text()).toContain('Bob');
    expect(text()).toContain('ehemalig');
    expect(text()).toContain('Du');
    // Bob (Mitglied): befördern und entfernen; Alice (Admin): herabstufen; Carl (ohne Account): nichts
    expect(root().querySelectorAll('button:not([type="submit"])').length).toBeGreaterThan(0);
    expect(Array.from(root().querySelectorAll('button')).filter((b) => b.textContent?.includes('Zum Admin machen'))).toHaveLength(1);
    expect(Array.from(root().querySelectorAll('button')).filter((b) => b.textContent?.includes('Entfernen'))).toHaveLength(1);
    expect(Array.from(root().querySelectorAll('button')).filter((b) => b.textContent?.includes('Zum Mitglied machen'))).toHaveLength(1);
  });

  it('a plain member sees the members but cannot manage anything', async () => {
    await open(plainMember, [alice, bob]);

    expect(text()).toContain('Alice');
    expect(button('Zum Admin machen')).toBeUndefined();
    expect(button('Entfernen')).toBeUndefined();
    expect(text()).not.toContain('Invitations');
    expect(button('Gruppe löschen')).toBeUndefined();
    expect(button('Gruppe verlassen')).toBeDefined();
    http.expectNone('/api/groups/7/invitations');
  });

  it('promoting a member changes the role and reloads the list', async () => {
    await open(admin, [alice, bob]);

    click('Zum Admin machen');
    const req = http.expectOne({ method: 'PATCH', url: '/api/groups/7/members/2' });
    expect(req.request.body).toEqual({ role: 'ADMIN' });
    req.flush({ ...bob, role: 'ADMIN' });
    (await expectReload('/api/groups/7/members')).flush([alice, { ...bob, role: 'ADMIN' }]);
    await settle();

    expect(Array.from(root().querySelectorAll('button')).filter((b) => b.textContent?.includes('Zum Mitglied machen'))).toHaveLength(2);
  });

  it('demoting an admin sends the member role', async () => {
    await open(admin, [alice, { ...bob, role: 'ADMIN' }]);

    click('Zum Mitglied machen');
    expect(http.expectOne({ method: 'PATCH', url: '/api/groups/7/members/1' }).request.body).toEqual({ role: 'MEMBER' });
    http.match('/api/groups/7/members');
  });

  it('removing a member asks first and then reloads the list', async () => {
    await open(admin, [alice, bob]);
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);

    click('Entfernen');
    expect(confirmation).toHaveBeenCalled();
    http.expectOne({ method: 'DELETE', url: '/api/groups/7/members/2' }).flush(null, noContent);
    (await expectReload('/api/groups/7/members')).flush([alice, { ...bob, active: false }]);
    await settle();

    expect(button('Entfernen')).toBeUndefined();
    expect(text()).toContain('ehemalig');
  });

  it('cancelling the confirmation removes nobody', async () => {
    await open(admin, [alice, bob]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    click('Entfernen');

    http.expectNone('/api/groups/7/members/2');
  });

  it('shows the reason when a role change is refused', async () => {
    await open(admin, [alice]);

    click('Zum Mitglied machen');
    http.expectOne('/api/groups/7/members/1').flush(
      { status: 409, title: 'Conflict', detail: 'Die Gruppe braucht mindestens einen Admin.' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text()).toContain('mindestens einen Admin');
  });

  it('says so when the members cannot be loaded', async () => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([plainMember]);

    await harness.navigateByUrl('/g/7/settings');
    http.expectOne('/api/groups/7/members').flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(text()).toContain('Die Mitglieder konnten nicht geladen werden.');
  });

  // --- Einladungen ---

  it('lists the invitations with their state but never a link', async () => {
    await open(admin, [alice], [invitation(1, 'ACTIVE', 1, 5), invitation(2, 'REVOKED'), invitation(3, 'EXHAUSTED', 2, 2)]);

    expect(text()).toContain('aktiv');
    expect(text()).toContain('widerrufen');
    expect(text()).toContain('aufgebraucht');
    expect(text()).toContain('1 von 5 Nutzungen');
    // Nur aktive Einladungen lassen sich widerrufen
    expect(Array.from(root().querySelectorAll('button')).filter((b) => b.textContent?.includes('Widerrufen'))).toHaveLength(1);
    expect(root().querySelector('input[readonly]')).toBeNull();
  });

  it('creating an invitation shows the link once', async () => {
    await open(admin, [alice]);

    click('Create Invitation Link');
    const req = http.expectOne({ method: 'POST', url: '/api/groups/7/invitations' });
    expect(req.request.body).toEqual({ expiresInDays: 7, maxUses: undefined });
    req.flush({ id: 9, token: 'geheim-token', expiresAt: '2026-01-08T12:00:00Z', maxUses: null });
    (await expectReload('/api/groups/7/invitations')).flush([invitation(9, 'ACTIVE')]);
    await settle();

    const link = root().querySelector<HTMLInputElement>('input[readonly]')!;
    expect(link.value).toBe(`${document.location.origin}/join/geheim-token`);
    expect(text()).toContain('nur jetzt angezeigt');
    expect(text()).toContain('aktiv');
  });

  it('passes the chosen duration and limit', async () => {
    await open(admin, [alice]);
    const days = root().querySelector<HTMLSelectElement>('select')!;
    days.selectedIndex = 1;
    days.dispatchEvent(new Event('change'));
    const uses = root().querySelector<HTMLInputElement>('input[type="number"]')!;
    uses.value = '5';
    uses.dispatchEvent(new Event('input'));

    click('Create Invitation Link');

    const req = http.expectOne({ method: 'POST', url: '/api/groups/7/invitations' });
    expect(req.request.body).toEqual({ expiresInDays: 3, maxUses: 5 });
    req.flush({ id: 9, token: 't', expiresAt: '2026-01-08T12:00:00Z', maxUses: 5 });
    (await expectReload('/api/groups/7/invitations')).flush([]);
  });

  it('copies the link to the clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await open(admin, [alice]);
    click('Create Invitation Link');
    http.expectOne({ method: 'POST', url: '/api/groups/7/invitations' }).flush({ id: 9, token: 'abc', expiresAt: '2026-01-08T12:00:00Z', maxUses: null });
    (await expectReload('/api/groups/7/invitations')).flush([]);
    await settle();

    click('Kopieren');
    await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(`${document.location.origin}/join/abc`));
    await settle();

    expect(text()).toContain('Link kopiert.');
  });

  it('tells the user to copy by hand when the clipboard is not available', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    await open(admin, [alice]);
    click('Create Invitation Link');
    http.expectOne({ method: 'POST', url: '/api/groups/7/invitations' }).flush({ id: 9, token: 'abc', expiresAt: '2026-01-08T12:00:00Z', maxUses: null });
    (await expectReload('/api/groups/7/invitations')).flush([]);
    await settle();

    click('Kopieren');
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text()).toContain('Kopieren geht hier nicht');
    });
  });

  it('revoking an invitation reloads the list', async () => {
    await open(admin, [alice], [invitation(1, 'ACTIVE')]);

    click('Widerrufen');
    http.expectOne({ method: 'DELETE', url: '/api/groups/7/invitations/1' }).flush(null, noContent);
    (await expectReload('/api/groups/7/invitations')).flush([invitation(1, 'REVOKED')]);
    await settle();

    expect(text()).toContain('widerrufen');
    expect(button('Widerrufen')).toBeUndefined();
  });

  it('shows the reason when creating an invitation is refused', async () => {
    await open(admin, [alice]);

    click('Create Invitation Link');
    http.expectOne({ method: 'POST', url: '/api/groups/7/invitations' }).flush(
      { status: 403, title: 'Forbidden', detail: 'Das dürfen nur Admins der Gruppe.' },
      { status: 403, statusText: 'Forbidden' },
    );
    await settle();

    expect(text()).toContain('Das dürfen nur Admins der Gruppe.');
    expect(root().querySelector('input[readonly]')).toBeNull();
  });

  // --- Gruppe ---

  it('renaming sends the new name and updates the group everywhere', async () => {
    await open(admin, [alice]);
    const name = root().querySelector<HTMLInputElement>('app-group-section input[type="text"]')!;
    expect(name.value).toBe('Kumpels');
    expect(button('Umbenennen')!.disabled).toBe(true);

    name.value = '  Neue Crew ';
    name.dispatchEvent(new Event('input'));
    harness.detectChanges();
    click('Umbenennen');
    const req = http.expectOne({ method: 'PATCH', url: '/api/groups/7' });
    expect(req.request.body).toEqual({ name: 'Neue Crew' });
    req.flush({ ...admin, name: 'Neue Crew' });
    await settle();

    expect(TestBed.inject(GroupApi).groups()[0].name).toBe('Neue Crew');
    expect(text()).toContain('Gespeichert.');
    expect(text()).toContain('Neue Crew');
  });

  it('shows the reason when renaming is refused', async () => {
    await open(admin, [alice]);
    const name = root().querySelector<HTMLInputElement>('app-group-section input[type="text"]')!;
    name.value = 'Neu';
    name.dispatchEvent(new Event('input'));
    harness.detectChanges();

    click('Umbenennen');
    http.expectOne({ method: 'PATCH', url: '/api/groups/7' }).flush(
      { status: 403, title: 'Forbidden', detail: 'Das dürfen nur Admins der Gruppe.' },
      { status: 403, statusText: 'Forbidden' },
    );
    await settle();

    expect(text()).toContain('Das dürfen nur Admins der Gruppe.');
  });

  it('leaving the group goes back to the group list', async () => {
    await open(plainMember, [alice, bob]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    click('Gruppe verlassen');
    http.expectOne({ method: 'DELETE', url: '/api/groups/7/members/me' }).flush(null, noContent);
    await vi.waitFor(() => expect(url()).toBe('/groups'));
    http.expectOne('/api/groups').flush([]);

    expect(TestBed.inject(GroupApi).groups()).toEqual([]);
  });

  it('the last admin is told to hand over before leaving and stays in the group', async () => {
    await open(admin, [alice, bob]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    click('Gruppe verlassen');
    http.expectOne('/api/groups/7/members/me').flush(
      { status: 409, title: 'Conflict', detail: "In der Gruppe 'Kumpels' bist du der letzte Admin. Mache zuerst jemand anderen zum Admin." },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(text()).toContain('der letzte Admin');
    expect(url()).toBe('/g/7/settings');
    expect(TestBed.inject(GroupApi).groups()).toHaveLength(1);
  });

  it('deleting the group asks first, then goes back to the group list', async () => {
    await open(admin, [alice]);
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(true);

    click('Gruppe löschen');
    expect(confirmation.mock.calls[0][0]).toContain('unwiderruflich');
    http.expectOne({ method: 'DELETE', url: '/api/groups/7' }).flush(null, noContent);
    await vi.waitFor(() => expect(url()).toBe('/groups'));
    http.expectOne('/api/groups').flush([]);

    expect(TestBed.inject(GroupApi).groups()).toEqual([]);
  });

  it('cancelling the confirmation deletes nothing', async () => {
    await open(admin, [alice]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    click('Gruppe löschen');
    click('Gruppe verlassen');

    http.expectNone('/api/groups/7');
    http.expectNone('/api/groups/7/members/me');
  });

  it('is only for members: a stranger is sent to the group list', async () => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush([]);

    const navigation = harness.navigateByUrl('/g/7/settings');
    (await vi.waitFor(() => http.expectOne('/api/groups'))).flush([]);
    await navigation;
    http.expectOne('/api/groups').flush([]);
    await settle();

    expect(url()).toBe('/groups');
  });
});
