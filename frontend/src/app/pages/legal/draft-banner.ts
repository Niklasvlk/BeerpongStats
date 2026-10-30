import { Component } from '@angular/core';
import { hasPlaceholders } from '../../shared/operator';

// Erscheint auf den rechtlichen Seiten, solange in shared/operator.ts noch Platzhalter stehen. Sobald alles ausgefüllt
// ist, verschwindet der Hinweis von selbst.
@Component({
  selector: 'app-draft-banner',
  template: `
    @if (draft) {
      <p class="draft-banner">
        <strong>Entwurf:</strong> Diese Seite enthält noch Platzhalter (gelb markiert). Sie müssen in
        <code>frontend/src/app/shared/operator.ts</code> ersetzt werden, bevor die Seite öffentlich ist.
      </p>
    }
  `,
})
export class DraftBanner {
  protected readonly draft = hasPlaceholders();
}
