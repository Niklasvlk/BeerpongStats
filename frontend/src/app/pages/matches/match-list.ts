import { Component, computed, inject, input } from '@angular/core';
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

  // Route-Parameter :groupId der Eltern-Route
  readonly groupId = input.required<string>();
  protected readonly group = computed(() => Number(this.groupId()));

  protected readonly allMatches = rxResource({
    params: () => this.group(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly matches = listValue(this.allMatches);
}
