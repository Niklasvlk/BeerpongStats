// Einladungs-Token sind 32 zufällige Bytes, URL-sicher als Base64 ohne Füllzeichen kodiert: genau 43 Zeichen aus
// A-Z, a-z, 0-9, "-" und "_". Alles andere kann kein echter Token sein und wird gar nicht erst an die API geschickt.
const TOKEN_FORMAT = /^[A-Za-z0-9_-]{43}$/;

export function isInvitationToken(value: string | null | undefined): value is string {
  return typeof value === 'string' && TOKEN_FORMAT.test(value);
}
