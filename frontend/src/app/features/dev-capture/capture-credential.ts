import { CaptureApi, CaptureApiFailure, captureApiMessage } from './capture-session';

export type CaptureRole = 'PRESENTER' | 'VIEWER';
export interface CredentialApi extends CaptureApi { validate(): Promise<{ role: CaptureRole }>; }
export interface CredentialView {
  status: 'empty' | 'invalid' | 'checking' | 'valid' | 'wrong-role' | 'unavailable' | 'accepted';
  message: string; requestId?: string;
}

/** Debounced read-only validation; an old response can never validate a newer key. */
export class CaptureCredential {
  private revision = 0;
  private timer?: ReturnType<typeof setTimeout>;
  private api?: CredentialApi;
  constructor(private readonly role: CaptureRole, private readonly factory: (key: string) => CredentialApi,
    private readonly changed: (view: CredentialView) => void, private readonly debounceMs = 500) {}

  change(value: string, immediate = false): void {
    clearTimeout(this.timer);
    const revision = ++this.revision;
    this.api = undefined;
    const key = value.trim();
    if (!key) { this.changed({ status: 'empty', message: 'Cole a chave do seu papel. A validação é automática e não inicia a captura.' }); return; }
    if (!/^[A-Za-z0-9_-]{32,128}$/.test(key)) {
      this.changed({ status: 'invalid', message: 'Formato inválido. Cole somente o valor da chave, sem o nome da variável, sinal de igual ou aspas.' });
      return;
    }
    this.changed({ status: 'checking', message: 'Validando a chave no backend...' });
    const validate = (): void => { void this.validate(key, revision); };
    if (immediate) validate(); else this.timer = setTimeout(validate, this.debounceMs);
  }

  ready(): boolean { return this.api !== undefined; }
  take(): CredentialApi | undefined {
    const api = this.api;
    if (api) {
      this.dispose();
      this.changed({ status: 'accepted', message: 'Chave validada para esta tentativa. Para iniciar uma nova tentativa, cole a chave novamente.' });
    }
    return api;
  }
  dispose(): void { ++this.revision; clearTimeout(this.timer); this.api = undefined; }

  private async validate(key: string, revision: number): Promise<void> {
    try {
      const api = this.factory(key);
      const result = await api.validate();
      if (revision !== this.revision) return;
      if (result?.role !== 'PRESENTER' && result?.role !== 'VIEWER') throw new CaptureApiFailure(502, 'INVALID_RESPONSE');
      const label = result.role === 'PRESENTER' ? 'apresentador' : 'espectador';
      if (result.role !== this.role) {
        this.changed({ status: 'wrong-role', message: `A chave é válida para ${label}, mas esta é a página do outro papel. Troque a chave ou abra a página correta.` });
        return;
      }
      this.api = api;
      this.changed({ status: 'valid', message: `Chave de ${label} válida. ${this.role === 'PRESENTER' ? 'Clique em Selecionar tela e iniciar para autorizar a captura.' : 'Clique em Entrar como espectador. O apresentador precisa ter iniciado a transmissão.'}` });
    } catch (error: unknown) {
      if (revision !== this.revision) return;
      const rejected = error instanceof CaptureApiFailure && (error.status === 401 || error.status === 403);
      this.changed({ status: rejected ? 'invalid' : 'unavailable', message: rejected
        ? 'Chave não aceita. Confira o valor no arquivo local e o endereço desta página.'
        : error instanceof CaptureApiFailure ? captureApiMessage(error) : 'Não foi possível validar a chave. Confira o backend e tente novamente.',
        requestId: error instanceof CaptureApiFailure ? error.requestId : undefined });
    }
  }
}
