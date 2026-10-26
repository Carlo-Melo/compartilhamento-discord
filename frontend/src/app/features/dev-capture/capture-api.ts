import { CaptureAccess, CaptureApi, CaptureApiFailure, CaptureStatus } from './capture-session';

/** A role key is supplied by the local operator, kept in memory, never in a URL. */
export class CaptureProofApi implements CaptureApi {
  constructor(private readonly key: string) {}
  create(): Promise<CaptureAccess> { return this.request('', 'POST'); }
  viewer(): Promise<CaptureAccess> { return this.request('/viewer', 'POST'); }
  status(): Promise<CaptureStatus> { return this.request('', 'GET'); }
  async delete(): Promise<void> { await this.request('', 'DELETE'); }

  private async request<T>(path: string, method: string): Promise<T> {
    const response = await fetch(`/api/dev/capture/session${path}`, {
      method, credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(8_000),
      headers: { 'X-Capture-Key': this.key, 'X-Capture-Proof': 'restricted-development' },
    });
    // Never surface provider bodies, tokens or server diagnostics to UI/logs.
    if (!response.ok) throw new CaptureApiFailure(response.status);
    if (response.status === 204) return undefined as T;
    return await response.json() as T;
  }
}
