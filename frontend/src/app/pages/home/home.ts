import { PercentPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatchApi } from '../../api/match-api';
import { PlayerApi } from '../../api/player-api';
import { MatchCard } from '../../shared/match-card';
import { listValue } from '../../shared/list-value';
import { winRateColor } from '../../shared/win-rate-color';

@Component({
  selector: 'app-home',
  imports: [RouterLink, PercentPipe, MatchCard],
  templateUrl: './home.html',
})
export class Home {
  private readonly playerApi = inject(PlayerApi);
  private readonly matchApi = inject(MatchApi);

  // Route-Parameter :groupId der Eltern-Route
  readonly groupId = input.required<string>();
  protected readonly group = computed(() => Number(this.groupId()));

  protected readonly leaderboard = rxResource({
    params: () => this.group(),
    stream: ({ params }) => this.playerApi.leaderboard(params),
  });
  protected readonly recentMatches = rxResource({
    params: () => this.group(),
    stream: ({ params }) => this.matchApi.recent(params),
  });
  protected readonly players = listValue(this.leaderboard);
  protected readonly matches = listValue(this.recentMatches);
  protected readonly winRateColor = winRateColor;
}
