import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { GroupApi } from '../../api/group-api';
import { Group } from '../../api/models';
import { errorMessage } from '../../shared/error-message';

// Umbenennen, verlassen und löschen
@Component({
  selector: 'app-group-section',
  imports: [ReactiveFormsModule],
  templateUrl: './group-section.html',
})
export class GroupSection implements OnInit {
  private readonly groups = inject(GroupApi);
  private readonly router = inject(Router);

  readonly group = input.required<Group>();
  readonly isAdmin = input.required<boolean>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly renamed = signal(false);

  ngOnInit(): void {
    this.form.setValue({ name: this.group().name });
  }

  protected rename(): void {
    if (this.form.invalid) return;
    this.error.set(null);
    this.renamed.set(false);

    this.groups.rename(this.group().id, this.form.getRawValue().name.trim()).subscribe({
      next: () => {
        this.renamed.set(true);
        this.form.markAsPristine();
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected leave(): void {
    const confirmed = confirm(
      `Willst du die Gruppe „${this.group().name}“ verlassen? Dein Spieler und seine Matches bleiben in der Gruppe erhalten.`,
    );
    if (confirmed) {
      this.finish(this.groups.leave(this.group().id));
    }
  }

  protected deleteGroup(): void {
    const confirmed = confirm(
      `Die Gruppe „${this.group().name}“ wird mit allen Matches, Spielern und Einladungen unwiderruflich gelöscht. Wirklich löschen?`,
    );
    if (confirmed) {
      this.finish(this.groups.delete(this.group().id));
    }
  }

  private finish(request: ReturnType<GroupApi['delete']>): void {
    this.error.set(null);
    request.subscribe({
      next: () => this.router.navigateByUrl('/groups'),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
