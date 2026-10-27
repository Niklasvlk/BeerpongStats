import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateInvitationRequest, CreatedInvitation, Invitation, InvitationPreview } from './models';

@Injectable({ providedIn: 'root' })
export class InvitationApi {
  private readonly http = inject(HttpClient);

  // Ohne Login erreichbar: zeigt, wozu man mit dem Link eingeladen wird
  preview(token: string): Observable<InvitationPreview> {
    return this.http.get<InvitationPreview>(`/api/invitations/${encodeURIComponent(token)}`);
  }

  // Die folgenden Aufrufe sind nur für Admins der Gruppe
  list(groupId: number): Observable<Invitation[]> {
    return this.http.get<Invitation[]>(`/api/groups/${groupId}/invitations`);
  }

  // Der Token kommt nur in dieser Antwort vor und wird nicht gespeichert
  create(groupId: number, request: CreateInvitationRequest): Observable<CreatedInvitation> {
    return this.http.post<CreatedInvitation>(`/api/groups/${groupId}/invitations`, request);
  }

  revoke(groupId: number, invitationId: number): Observable<void> {
    return this.http.delete<void>(`/api/groups/${groupId}/invitations/${invitationId}`);
  }
}
