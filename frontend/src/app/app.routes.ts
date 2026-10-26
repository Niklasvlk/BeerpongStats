import { Routes } from '@angular/router';
import { authGuard } from './auth-guard';
import { NewMatch } from './pages/forms/new-match';
import { NewPlayer } from './pages/forms/new-player';
import { Login } from './pages/profile/login';
import { Profile } from './pages/profile/profile';
import { Home } from './pages/home/home';
import { MatchDetails } from './pages/matches/match-details';
import { MatchList } from './pages/matches/match-list';
import { PlayerDetails } from './pages/players/player-details';
import { PlayerList } from './pages/players/player-list';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'players', component: PlayerList },
  { path: 'players/:id', component: PlayerDetails },
  { path: 'matches', component: MatchList },
  { path: 'matches/:id', component: MatchDetails },
  { path: 'newPlayer', component: NewPlayer },
  { path: 'newMatch', component: NewMatch },
  { path: 'login', component: Login },
  { path: 'profile', component: Profile, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
