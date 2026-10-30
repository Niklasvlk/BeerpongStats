import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OPERATOR, isPlaceholder } from '../../shared/operator';
import { DraftBanner } from './draft-banner';

@Component({
  selector: 'app-impressum',
  imports: [RouterLink, DraftBanner],
  templateUrl: './impressum.html',
})
export class Impressum {
  protected readonly operator = OPERATOR;
  protected readonly isPlaceholder = isPlaceholder;
}
