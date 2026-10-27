// Nach dem Login geht es dorthin zurück, wo man hergekommen ist. Nur Pfade innerhalb der App sind erlaubt,
// sonst ließe sich über ?returnUrl=https://... auf fremde Seiten weiterleiten.
export function safeReturnUrl(url: string | null | undefined): string | null {
  if (!url || !url.startsWith('/') || url.startsWith('//') || url.startsWith('/\\')) {
    return null;
  }
  return url;
}
