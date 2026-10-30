import { PercentPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GroupApi } from '../../api/group-api';
import { errorMessage } from '../../shared/error-message';
import { winRateColor } from '../../shared/win-rate-color';

@Component({
  selector: 'app-group-list',
  imports: [ReactiveFormsModule, RouterLink, PercentPipe],
  templateUrl: './group-list.html',
})
export class GroupList {
  private readonly api = inject(GroupApi);
  private readonly router = inject(Router);

  protected readonly groups = this.api.groups;
  protected readonly winRateColor = winRateColor;
  protected readonly loadFailed = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    displayName: ['', Validators.maxLength(30)],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly submitting = signal(false);

  constructor() {
    // Die Liste kann sich seit dem Login geändert haben, z. B. durch einen Beitritt in einem anderen Tab
    this.api.load().subscribe({ error: () => this.loadFailed.set(true) });
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    const { name, displayName } = this.form.getRawValue();

    this.api.create({ name: name.trim(), displayName: displayName.trim() || undefined }).subscribe({
      next: (group) => this.router.navigate(['/g', group.id]),
      error: (err) => {
        this.error.set(errorMessage(err));
        this.submitting.set(false);
      },
    });
  }
}
