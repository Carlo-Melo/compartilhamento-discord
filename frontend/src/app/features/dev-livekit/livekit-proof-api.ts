import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { ProofRole, ProofRoom, ProofToken } from './livekit-proof-models';

@Injectable()
export class LiveKitProofApi {
  private readonly http = inject(HttpClient);
  // A non-secret marker; the server must also enforce its development profile,
  // loopback binding and exact Origin allowlist. This is not user authorization.
  private readonly options = { headers: { 'X-LiveKit-Proof': 'local-development' } };
  private readonly base = '/api/dev/livekit/rooms';

  create(): Promise<Pick<ProofRoom, 'roomName' | 'maxParticipants'>> {
    return firstValueFrom(this.http.post<ProofRoom>(this.base, {}, this.options).pipe(timeout(8_000)));
  }

  query(roomName: string): Promise<ProofRoom> {
    return firstValueFrom(this.http.get<ProofRoom>(this.path(roomName), this.options).pipe(timeout(8_000)));
  }

  async delete(roomName: string): Promise<void> {
    await firstValueFrom(this.http.delete(this.path(roomName), this.options).pipe(timeout(8_000)));
  }

  issue(roomName: string, role: ProofRole): Promise<ProofToken> {
    return firstValueFrom(this.http.post<ProofToken>(`${this.path(roomName)}/tokens`, { role }, this.options)
      .pipe(timeout(8_000)));
  }

  async isAbsent(roomName: string): Promise<boolean> {
    try {
      return !(await this.query(roomName)).exists;
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 404) return true;
      throw error;
    }
  }

  private path(roomName: string): string {
    return `${this.base}/${encodeURIComponent(roomName)}`;
  }
}
