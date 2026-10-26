import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthApi } from './api/auth-api';
import { Player } from './api/models';

const max: Player = { id: 1, name: 'Max', wins: 3, losses: 1, winRate: 0.75, avgCups: 4.5, createdAt: '2026-01-01T12:00:00' };

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows the login label while nobody is logged in', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const profile = (fixture.nativeElement as HTMLElement).querySelector('.btn-primary');
    expect(profile?.textContent).toContain('My Profile');
  });

  it('shows the player name after login', async () => {
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthApi).login('Max', '1234').subscribe();
    http.expectOne('/api/auth/login').flush(max);

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const profile = (fixture.nativeElement as HTMLElement).querySelector('.btn-primary');
    expect(profile?.textContent).toContain('Max');
  });
});
