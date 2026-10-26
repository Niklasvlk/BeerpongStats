import { PercentPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatchApi } from '../../api/match-api';
import { PlayerApi } from '../../api/player-api';
import { MatchCard } from '../../shared/match-card';
import { winRateColor } from '../../shared/win-rate-color';

@Component({
  selector: 'app-home',
  imports: [RouterLink, PercentPipe, MatchCard],
  templateUrl: './home.html',
})
export class Home {
  protected readonly players = toSignal(inject(PlayerApi).leaderboard(), { initialValue: [] });
  protected readonly matches = toSignal(inject(MatchApi).recent(), { initialValue: [] });
  protected readonly winRateColor = winRateColor;
}
