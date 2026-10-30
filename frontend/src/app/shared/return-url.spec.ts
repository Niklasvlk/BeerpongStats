import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it('accepts paths inside the app', () => {
    expect(safeReturnUrl('/join/abc')).toBe('/join/abc');
    expect(safeReturnUrl('/g/7/matches?sort=name')).toBe('/g/7/matches?sort=name');
  });

  it('rejects everything that could leave the app', () => {
    expect(safeReturnUrl('https://evil.example')).toBeNull();
    expect(safeReturnUrl('//evil.example')).toBeNull();
    expect(safeReturnUrl('/\\evil.example')).toBeNull();
    expect(safeReturnUrl('javascript:alert(1)')).toBeNull();
    expect(safeReturnUrl('relative/path')).toBeNull();
  });

  it('treats a missing value as none', () => {
    expect(safeReturnUrl(undefined)).toBeNull();
    expect(safeReturnUrl(null)).toBeNull();
    expect(safeReturnUrl('')).toBeNull();
  });
});
