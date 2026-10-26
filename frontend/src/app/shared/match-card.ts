import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Match } from '../api/models';

@Component({
  selector: 'app-match-card',
  imports: [RouterLink, DatePipe],
  template: `
    <a class="match-card" [routerLink]="['/matches', match().id]">
      <div class="match-date">{{ (match().playedAt | date: 'dd.MM.yyyy HH:mm') ?? 'kein Datum' }}</div>
      <div class="match-teams">
        <div class="team winners">
          <span>{{ match().winner1.name }}</span>
          @if (match().winner2; as partner) {
            <span> / {{ partner.name }}</span>
          }
        </div>
        <div class="vs">vs</div>
        <div class="team losers">
          <span>{{ match().loser1.name }}</span>
          @if (match().loser2; as partner) {
            <span> / {{ partner.name }}</span>
          }
        </div>
      </div>
    </a>
  `,
})
export class MatchCard {
  readonly match = input.required<Match>();
}
