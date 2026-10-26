import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatchApi } from '../../api/match-api';
import { MatchCard } from '../../shared/match-card';

@Component({
  selector: 'app-match-list',
  imports: [RouterLink, MatchCard],
  templateUrl: './match-list.html',
})
export class MatchList {
  protected readonly matches = toSignal(inject(MatchApi).list(), { initialValue: [] });
}
