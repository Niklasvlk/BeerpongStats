// Der angemeldete Account
export interface User {
  id: number;
  username: string;
}

export type Role = 'ADMIN' | 'MEMBER';

// Eine Gruppe samt Rolle und Statistik des angemeldeten Benutzers darin
export interface Group {
  id: number;
  name: string;
  role: Role;
  playerId: number;
  wins: number;
  losses: number;
  winRate: number;
  avgCups: number;
  memberCount: number;
}

export interface Player {
  id: number;
  name: string;
  wins: number;
  losses: number;
  winRate: number;
  avgCups: number;
  createdAt: string;
}

export interface PlayerRef {
  id: number;
  name: string;
}

export interface Match {
  id: number;
  winner1: PlayerRef;
  winner2: PlayerRef | null;
  loser1: PlayerRef;
  loser2: PlayerRef | null;
  winner1Cups: number | null;
  winner2Cups: number | null;
  loser1Cups: number | null;
  loser2Cups: number | null;
  mvpCups: number | null;
  playedAt: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface CreateGroupRequest {
  name: string;
  displayName?: string;
}

export interface CreateMatchRequest {
  winner1: string;
  winner2?: string;
  loser1: string;
  loser2?: string;
  winner1Cups?: number;
  winner2Cups?: number;
  loser1Cups?: number;
  loser2Cups?: number;
}

export type PlayerSort = 'winrate' | 'avgCups' | 'name';

// RFC 9457, so antwortet das Backend bei Fehlern unter /api
export interface ProblemDetail {
  status: number;
  title: string;
  detail?: string;
}
