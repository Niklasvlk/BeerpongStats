import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
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

  // Route-Parameter :groupId der Eltern-Route und :id
  readonly groupId = input.required<string>();
  readonly id = input.required<string>();
  protected readonly group = computed(() => Number(this.groupId()));

  protected readonly player = rxResource({
    params: () => ({ group: this.group(), id: Number(this.id()) }),
    stream: ({ params }) => this.api.get(params.group, params.id),
  });
}
