import { Routes } from '@angular/router';
import { authGuard } from './auth-guard';
import { groupGuard } from './group-guard';
import { startGuard } from './start-guard';
import { NewMatch } from './pages/forms/new-match';
import { GroupList } from './pages/groups/group-list';
import { Home } from './pages/home/home';
import { Landing } from './pages/landing/landing';
import { MatchDetails } from './pages/matches/match-details';
import { MatchList } from './pages/matches/match-list';
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
    ],
  },
  { path: 'profile', component: Profile, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
