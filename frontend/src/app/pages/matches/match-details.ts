import { DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
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

  // Route-Parameter :id
  readonly id = input.required<string>();

  protected readonly match = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.get(params),
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
