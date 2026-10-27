import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { AuthApi } from './api/auth-api';
import { GroupApi } from './api/group-api';
import { Group } from './api/models';

const group = (id: number, name: string): Group => ({
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

describe('App', () => {
  let http: HttpTestingController;
  const root = (fixture: { nativeElement: unknown }) => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  const signIn = (groups: Group[]) => {
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush({ id: 1, username: 'Max' });
    http.expectOne('/api/groups').flush(groups);
  };

  it('offers login and registration while nobody is signed in', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const links = Array.from(root(fixture).querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['Login', 'Register']);
    expect(root(fixture).querySelector('.group-select')).toBeNull();
  });

  it('shows the username after login', async () => {
    signIn([]);

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(root(fixture).querySelector('.btn-primary')?.textContent).toContain('Max');
    expect(root(fixture).querySelector('nav a[href="/groups"]')).not.toBeNull();
  });

  it('shows no group navigation until a group was opened', async () => {
    signIn([group(7, 'Kumpels')]);

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect(root(fixture).querySelector('.group-select')).toBeNull();
    expect(root(fixture).querySelector('nav a[href="/g/7/players"]')).toBeNull();
  });

  it('links to the opened group and lets the user switch', async () => {
    signIn([group(7, 'Kumpels'), group(8, 'Uni')]);
    TestBed.inject(GroupApi).open(8);
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const select = root(fixture).querySelector<HTMLSelectElement>('.group-select')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual(['Kumpels', 'Uni']);
    expect(select.value).toBe('8');
    for (const path of ['newMatch', 'players', 'matches']) {
      expect(root(fixture).querySelector(`nav a[href="/g/8/${path}"]`)).not.toBeNull();
    }

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    select.value = '7';
    select.dispatchEvent(new Event('change'));
    expect(navigate).toHaveBeenCalledWith(['/g', '7']);
  });
});
