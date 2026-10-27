import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatchApi } from '../../api/match-api';
import { Match, PlayerRef } from '../../api/models';

interface Participant {
  player: PlayerRef;
  cups: number | null;
}

@Component({
  selector: 'app-match-details',
  imports: [RouterLink, DatePipe],
  templateUrl: './match-details.html',
})
export class MatchDetails {
  private readonly api = inject(MatchApi);

  // Route-Parameter :groupId der Eltern-Route und :id
  readonly groupId = input.required<string>();
  readonly id = input.required<string>();
  protected readonly group = computed(() => Number(this.groupId()));

  protected readonly match = rxResource({
    params: () => ({ group: this.group(), id: Number(this.id()) }),
    stream: ({ params }) => this.api.get(params.group, params.id),
  });

  protected winners(match: Match): Participant[] {
    return this.participants([match.winner1, match.winner1Cups], [match.winner2, match.winner2Cups]);
  }

  protected losers(match: Match): Participant[] {
    return this.participants([match.loser1, match.loser1Cups], [match.loser2, match.loser2Cups]);
  }

  private participants(...entries: [PlayerRef | null, number | null][]): Participant[] {
    return entries.flatMap(([player, cups]) => (player ? [{ player, cups }] : []));
  }
}
