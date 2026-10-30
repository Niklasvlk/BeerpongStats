import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OPERATOR, isPlaceholder } from '../../shared/operator';
import { DraftBanner } from './draft-banner';

@Component({
  selector: 'app-datenschutz',
  imports: [RouterLink, DraftBanner],
  templateUrl: './datenschutz.html',
})
export class Datenschutz {
  protected readonly operator = OPERATOR;
  protected readonly isPlaceholder = isPlaceholder;
}
