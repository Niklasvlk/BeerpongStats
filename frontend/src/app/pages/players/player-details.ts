import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { PlayerApi } from '../../api/player-api';

@Component({
  selector: 'app-player-details',
  imports: [RouterLink, PercentPipe, DecimalPipe],
  templateUrl: './player-details.html',
})
export class PlayerDetails {
  private readonly api = inject(PlayerApi);

  // Route-Parameter :id
  readonly id = input.required<string>();

  protected readonly player = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.get(params),
  });
}
