import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../api/models';

// Das Backend liefert Fehler als ProblemDetail, die Meldung steht in "detail"
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as ProblemDetail | null;
    return problem?.detail ?? 'Ein unerwarteter Fehler ist aufgetreten.';
  }
  return 'Ein unerwarteter Fehler ist aufgetreten.';
}
