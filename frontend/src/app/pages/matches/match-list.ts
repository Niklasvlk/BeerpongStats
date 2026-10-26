import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatchApi } from '../../api/match-api';
import { MatchCard } from '../../shared/match-card';
import { listValue } from '../../shared/list-value';

@Component({
  selector: 'app-match-list',
  imports: [RouterLink, MatchCard],
  templateUrl: './match-list.html',
})
export class MatchList {
  private readonly api = inject(MatchApi);

  protected readonly allMatches = rxResource({ stream: () => this.api.list() });
  protected readonly matches = listValue(this.allMatches);
}
