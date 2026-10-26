export type ProofRole = 'PRESENTER' | 'VIEWER';

export interface ProofToken {
  roomName: string;
  identity: string;
  role: ProofRole;
  serverUrl: string;
  token: string;
  expiresAt: string;
}

export interface ProofRoom {
  roomName: string;
  exists: boolean;
  maxParticipants: number;
  participants: Array<{
    identity: string;
    sid: string;
    tracks: Array<{ sid: string; kind: string; source: string; muted: boolean }>;
  }>;
}

export interface ProofCheck {
  label: string;
  status: 'running' | 'passed' | 'failed';
  detail: string;
}
