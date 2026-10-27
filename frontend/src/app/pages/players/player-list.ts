import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { PlayerApi } from '../../api/player-api';
import { PlayerSort } from '../../api/models';
import { listValue } from '../../shared/list-value';
import { winRateColor } from '../../shared/win-rate-color';

@Component({
  selector: 'app-player-list',
  imports: [RouterLink, PercentPipe, DecimalPipe],
  templateUrl: './player-list.html',
})
export class PlayerList {
  private readonly api = inject(PlayerApi);
  private readonly router = inject(Router);

  // Route-Parameter :groupId der Eltern-Route
  readonly groupId = input.required<string>();
  protected readonly group = computed(() => Number(this.groupId()));

  // Kommen aus den Query-Parametern (?sort=winrate bzw. ?name=max)
  readonly sort = input<PlayerSort>();
  readonly name = input<string>();

  protected readonly players = rxResource({
    params: () => ({ group: this.group(), sort: this.sort(), name: this.name() }),
    stream: ({ params }) =>
      params.name ? this.api.search(params.group, params.name) : this.api.list(params.group, params.sort),
  });
  protected readonly list = listValue(this.players);
  protected readonly winRateColor = winRateColor;

  protected search(name: string): void {
    this.router.navigate(['/g', this.group(), 'players'], { queryParams: { name: name.trim() || null } });
  }
}
