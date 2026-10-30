import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { OPERATOR, Operator, hasPlaceholders, isPlaceholder } from '../../shared/operator';

const FILLED: Operator = {
  name: 'Max Mustermann',
  street: 'Musterstraße 1',
  city: '12345 Musterstadt',
  email: 'max@example.org',
  hosting: 'Beispiel Hosting GmbH',
  hostingAddress: 'Hostweg 2, 10115 Berlin',
  hostingRegion: 'Frankfurt am Main',
  updated: '01.11.2026',
};

// Die Tests dürfen nicht von den echten Angaben in shared/operator.ts abhängen: Sie würden sonst rot, sobald der Betreiber
// sie ausfüllt. Deshalb setzen sie die Daten selbst und stellen sie danach wieder her.
const OPEN: Operator = {
  name: '[Vor- und Nachname]',
  street: '[Straße und Hausnummer]',
  city: '[Postleitzahl und Ort]',
  email: '[E-Mail-Adresse]',
  hosting: '[Name des Hosting-Anbieters]',
  hostingAddress: '[Anschrift des Hosting-Anbieters]',
  hostingRegion: '[Serverstandort, z. B. Frankfurt am Main]',
  updated: '[Datum]',
};

describe('legal pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const original = { ...OPERATOR };

  beforeEach(async () => {
    localStorage.clear();
    Object.assign(OPERATOR, OPEN);
    TestBed.configureTestingModule({
      providers: [provideRouter(routes, withComponentInputBinding()), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    Object.assign(OPERATOR, original);
    http.verify();
  });

  const root = () => harness.routeNativeElement as HTMLElement;
  const text = () => root().textContent ?? '';

  // --- Erreichbarkeit ---

  it('the imprint and the privacy policy are reachable without a login', async () => {
    await harness.navigateByUrl('/impressum');
    expect(TestBed.inject(Router).url).toBe('/impressum');
    expect(text()).toContain('Impressum');

    await harness.navigateByUrl('/datenschutz');
    expect(TestBed.inject(Router).url).toBe('/datenschutz');
    expect(text()).toContain('Datenschutzerklärung');
    http.expectNone(() => true);
  });

  // --- Platzhalter ---

  it('marks every open placeholder and warns that the page is a draft', async () => {
    await harness.navigateByUrl('/impressum');

    expect(text()).toContain('Entwurf');
    expect(root().querySelectorAll('.placeholder').length).toBeGreaterThanOrEqual(4);
    expect(text()).toContain('[Vor- und Nachname]');
  });

  it('the privacy policy marks its placeholders too', async () => {
    await harness.navigateByUrl('/datenschutz');

    expect(text()).toContain('Entwurf');
    expect(root().querySelectorAll('.placeholder').length).toBeGreaterThanOrEqual(8);
    expect(text()).toContain('[Name des Hosting-Anbieters]');
    expect(text()).toContain('[Serverstandort');
  });

  it('shows no warning and no marking once everything is filled in', async () => {
    Object.assign(OPERATOR, FILLED);

    await harness.navigateByUrl('/impressum');
    expect(text()).toContain('Max Mustermann');
    expect(text()).toContain('Musterstraße 1');
    expect(text()).toContain('max@example.org');
    expect(text()).not.toContain('Entwurf');
    expect(root().querySelectorAll('.placeholder')).toHaveLength(0);

    await harness.navigateByUrl('/datenschutz');
    expect(text()).toContain('Beispiel Hosting GmbH');
    expect(text()).toContain('Frankfurt am Main');
    expect(text()).toContain('01.11.2026');
    expect(text()).not.toContain('Entwurf');
    expect(root().querySelectorAll('.placeholder')).toHaveLength(0);
  });

  it('one open placeholder is enough to keep the warning', async () => {
    Object.assign(OPERATOR, FILLED, { hostingRegion: '[Serverstandort]' });

    await harness.navigateByUrl('/datenschutz');

    expect(text()).toContain('Entwurf');
    expect(root().querySelectorAll('.placeholder')).toHaveLength(1);
  });

  // --- Inhalt der Datenschutzerklärung ---

  it('the privacy policy describes what the app actually stores and sets', async () => {
    await harness.navigateByUrl('/datenschutz');

    for (const expected of [
      'Benutzernamen',
      'BCrypt',
      'JSESSIONID',
      'XSRF-TOKEN',
      'beerpong.lastGroupId',
      'ehemalig',
      'Hash des Links',
      'Rechte',
      'Aufsichtsbehörde',
    ]) {
      expect(text(), expected).toContain(expected);
    }
  });

  it('the privacy policy says nothing is loaded from other servers', async () => {
    await harness.navigateByUrl('/datenschutz');

    expect(text()).toContain('Schrift wird von dieser Seite selbst ausgeliefert');
  });

  it('the imprint and the privacy policy link to each other', async () => {
    await harness.navigateByUrl('/impressum');
    expect(Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'))).toContain('/datenschutz');

    await harness.navigateByUrl('/datenschutz');
    expect(Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'))).toContain('/impressum');
  });

  it('the registration points to the privacy policy', async () => {
    await harness.navigateByUrl('/register');

    expect(Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'))).toContain('/datenschutz');
  });
});

describe('operator data', () => {
  it('recognises placeholders in square brackets only', () => {
    expect(isPlaceholder('[Vor- und Nachname]')).toBe(true);
    expect(isPlaceholder('  [Straße]  ')).toBe(true);
    expect(isPlaceholder('Max Mustermann')).toBe(false);
    expect(isPlaceholder('Hostweg [2]')).toBe(false);
    expect(isPlaceholder('')).toBe(false);
  });

  it('hasPlaceholders is true while anything is open and false once everything is filled in', () => {
    expect(hasPlaceholders(OPEN)).toBe(true);
    expect(hasPlaceholders(FILLED)).toBe(false);
    expect(hasPlaceholders({ ...FILLED, email: '[E-Mail]' })).toBe(true);
  });
});
