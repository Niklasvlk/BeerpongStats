import { isInvitationToken } from './invitation-token';

describe('isInvitationToken', () => {
  it('accepts a token in the form the backend creates', () => {
    expect(isInvitationToken('abcdefghijklmnopqrstuvwxyz0123456789_-ABCDE')).toBe(true);
    expect(isInvitationToken('CeIgo-D8N0cVFWRfYu-uLX4_7boy__99sMrvorvfEnk')).toBe(true);
    expect(isInvitationToken('-'.repeat(43))).toBe(true);
    expect(isInvitationToken('_'.repeat(43))).toBe(true);
  });

  it('rejects a wrong length', () => {
    expect(isInvitationToken('x'.repeat(42))).toBe(false);
    expect(isInvitationToken('x'.repeat(44))).toBe(false);
    expect(isInvitationToken('')).toBe(false);
  });

  it('rejects everything that could change the path of a request', () => {
    const base = 'x'.repeat(40);
    for (const bad of ['/..', '%2F.', '?a=', '#ab', '/ab', '../', ' ab', 'ab ', 'a\nb', 'ä'.repeat(3), '.'.repeat(3)]) {
      expect(isInvitationToken(base + bad.padEnd(3, 'x').slice(0, 3)), bad).toBe(false);
    }
    expect(isInvitationToken('x/../../auth/logout?')).toBe(false);
    expect(isInvitationToken('x%2F..%2F..%2Fauth%2Flogout%3F')).toBe(false);
  });

  it('rejects a trailing newline, which some patterns let through', () => {
    expect(isInvitationToken('x'.repeat(43) + '\n')).toBe(false);
  });

  it('rejects things that are no strings', () => {
    expect(isInvitationToken(undefined)).toBe(false);
    expect(isInvitationToken(null)).toBe(false);
  });
});
