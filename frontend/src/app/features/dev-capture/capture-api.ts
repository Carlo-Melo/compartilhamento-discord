import { CaptureAccess, CaptureApi, CaptureApiFailure, CaptureStatus } from './capture-session';

/** A role key is supplied by the local operator, kept in memory, never in a URL. */
export class CaptureProofApi implements CaptureApi {
  constructor(private readonly key: string, private viewerNonce?: string) {}
  validate(): Promise<{ role: 'PRESENTER' | 'VIEWER' }> { return this.request('/access', 'GET'); }
  create(): Promise<CaptureAccess> { return this.request('', 'POST'); }
  viewer(): Promise<CaptureAccess> { return this.request('/viewer', 'POST'); }
  status(): Promise<CaptureStatus> { return this.request('', 'GET'); }
  async delete(): Promise<void> { await this.request('', 'DELETE'); }
  async leave(): Promise<void> { await this.request('/viewer', 'DELETE'); }

  private async request<T>(path: string, method: string): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`/api/dev/capture/session${path}`, {
        method, credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(8_000),
        headers: { 'X-Capture-Key': this.key, 'X-Capture-Proof': 'restricted-development',
          ...(path === '/viewer' ? { 'X-Capture-Viewer': this.viewerNonce ??= crypto.randomUUID() } : {}) },
      });
    } catch (error: unknown) {
      const timeout = error instanceof DOMException && (error.name === 'TimeoutError' || error.name === 'AbortError');
      throw new CaptureApiFailure(0, timeout ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR');
    }
    // Never surface provider bodies, tokens or server diagnostics to UI/logs.
    if (!response.ok) {
      // Only allowlisted codes and UUIDs survive; never display/log the server message or raw body.
      let failure: { code?: string; requestId?: string } = {};
      try { failure = await response.json(); } catch { /* A proxy can return HTML on failure. */ }
      throw new CaptureApiFailure(response.status, failure?.code, response.headers.get('X-Request-Id') ?? failure?.requestId);
    }
    if (response.status === 204) return undefined as T;
    try { return await response.json() as T; }
    catch { throw new CaptureApiFailure(502, 'INVALID_RESPONSE'); }
  }
}
