import { Routes } from '@angular/router';
import { authGuard } from './auth-guard';
import { groupGuard } from './group-guard';
import { startGuard } from './start-guard';
import { NewMatch } from './pages/forms/new-match';
import { GroupList } from './pages/groups/group-list';
import { Home } from './pages/home/home';
import { Datenschutz } from './pages/legal/datenschutz';
import { Impressum } from './pages/legal/impressum';
import { Join } from './pages/join/join';
import { Landing } from './pages/landing/landing';
import { MatchDetails } from './pages/matches/match-details';
import { MatchList } from './pages/matches/match-list';
import { GroupSettings } from './pages/settings/group-settings';
import { PlayerDetails } from './pages/players/player-details';
import { PlayerList } from './pages/players/player-list';
import { Login } from './pages/profile/login';
import { Profile } from './pages/profile/profile';
import { Register } from './pages/profile/register';

export const routes: Routes = [
  { path: '', component: Landing, canActivate: [startGuard] },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'groups', component: GroupList, canActivate: [authGuard] },
  // Der Einladungslink ist ohne Login erreichbar, damit man vor dem Beitreten sehen kann, wozu man eingeladen wird
  { path: 'join/:token', component: Join },
  // Alles einer Gruppe liegt unter /g/:groupId; die Eltern-Route hat keine Komponente, ihr Parameter gilt also für
  // alle Kinder und kommt per Input-Binding in die Seiten
  {
    path: 'g/:groupId',
    canActivate: [authGuard, groupGuard],
    children: [
      { path: '', component: Home },
      { path: 'players', component: PlayerList },
      { path: 'players/:id', component: PlayerDetails },
      { path: 'matches', component: MatchList },
      { path: 'matches/:id', component: MatchDetails },
      { path: 'newMatch', component: NewMatch },
      { path: 'settings', component: GroupSettings },
    ],
  },
  { path: 'profile', component: Profile, canActivate: [authGuard] },
  // Müssen ohne Login erreichbar sein, auch von der Registrierung aus
  { path: 'impressum', component: Impressum },
  { path: 'datenschutz', component: Datenschutz },
  { path: '**', redirectTo: '' },
];
