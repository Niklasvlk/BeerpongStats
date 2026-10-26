import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { PlayerApi } from '../../api/player-api';
import { PlayerSort } from '../../api/models';
import { winRateColor } from '../../shared/win-rate-color';

@Component({
  selector: 'app-player-list',
  imports: [RouterLink, PercentPipe, DecimalPipe],
  templateUrl: './player-list.html',
})
export class PlayerList {
  private readonly api = inject(PlayerApi);
  private readonly router = inject(Router);

  // Kommen aus den Query-Parametern (?sort=winrate bzw. ?name=max)
  readonly sort = input<PlayerSort>();
  readonly name = input<string>();

  protected readonly players = rxResource({
    params: () => ({ sort: this.sort(), name: this.name() }),
    stream: ({ params }) => (params.name ? this.api.search(params.name) : this.api.list(params.sort)),
  });
  // value() wirft im Fehlerzustand, daher erst hasValue() prüfen
  protected readonly list = computed(() => (this.players.hasValue() ? this.players.value() : []));
  protected readonly winRateColor = winRateColor;

  protected search(name: string): void {
    this.router.navigate(['/players'], { queryParams: { name: name.trim() || null } });
  }
}
