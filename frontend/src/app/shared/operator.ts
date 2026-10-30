// Die Angaben zum Betreiber, die Impressum und Datenschutzerklärung brauchen. Das ist die EINZIGE Stelle, an der sie
// stehen: Alle Platzhalter in eckigen Klammern durch die echten Angaben ersetzen. Solange noch einer übrig ist, zeigen
// beide Seiten oben einen Hinweis, dass sie ein Entwurf sind, und markieren die offenen Stellen gelb.
export const OPERATOR = {
  name: 'Niklas Völker',
  street: 'Heidekrautweg 48',
  city: '47807 Krefeld',
  email: 'niklas.vlk200@gmail.com',
  // Der Anbieter, bei dem die App läuft (z. B. Render), sein Sitz und der Standort der Server
  hosting: 'Render',
  hostingAddress: 'Render Services, Inc.\n' +
                  '525 Brannan Street, Suite 300\n' +
                  'San Francisco, CA 94107\n' +
                  'Vereinigte Staaten (USA)',
  hostingRegion: 'Frankfurt am Main',
  // Datum, an dem der Text zuletzt geprüft wurde
  updated: '30.10.2026',
};

export type Operator = typeof OPERATOR;

// Ein Platzhalter ist alles, was noch in eckigen Klammern steht
export function isPlaceholder(value: string): boolean {
  return /^\[.*\]$/.test(value.trim());
}

export function hasPlaceholders(operator: Operator = OPERATOR): boolean {
  return Object.values(operator).some(isPlaceholder);
}
