import { AfterViewInit, Component, ElementRef, inject, NgZone, OnDestroy, ViewChild } from '@angular/core';

const vertexSource = `
attribute vec2 a_position;
void main() { gl_Position = vec4(a_position, 0.0, 1.0); }
`;

/** Original procedural artwork. The output cap protects text over every pixel. */
const fragmentSource = `
precision mediump float;
uniform vec2 u_resolution;
uniform float u_time;

void main() {
  vec2 uv = gl_FragCoord.xy / u_resolution;
  float aspect = u_resolution.x / u_resolution.y;
  float t = u_time;
  vec2 center = vec2(0.70 + 0.065 * sin(t * 0.24), 0.53 + 0.08 * cos(t * 0.19));
  vec2 p = (uv - center) * vec2(aspect, 1.0);
  p.x += 0.055 * sin(p.y * 5.0 + t * 0.62);
  p.y += 0.045 * sin(p.x * 4.0 - t * 0.53);
  float angle = atan(p.y, p.x);
  float radius = length(p * vec2(0.90, 1.0));

  // Several moving folds form a soft, asymmetric violet membrane.
  float fold = 0.075 * sin(angle * 3.0 + t * 0.51)
    + 0.035 * sin(angle * 5.0 - t * 0.67)
    + 0.020 * sin(angle * 8.0 + t * 0.32);
  float shell = 0.37 + 0.025 * sin(t * 0.39) + fold;
  float distanceToShell = abs(radius - shell);
  float membrane = exp(-distanceToShell * 17.0);
  float edge = exp(-distanceToShell * 100.0);
  float fibers = pow(0.5 + 0.5 * sin(radius * 85.0 + angle * 4.0
    + t * 1.35 + 2.5 * sin(angle * 3.0 - t * 0.47)), 5.0);
  float facing = 0.56 + 0.44 * sin(angle - t * 0.30);
  float core = smoothstep(0.12, 0.34, radius + fold * 0.65);

  // Broad drifting ribbons remain visible beyond the membrane in open space.
  float wave = p.y + 0.14 * sin(p.x * 3.2 + t * 0.43)
    + 0.075 * sin(p.x * 6.1 - t * 0.58);
  float ribbon = exp(-abs(wave + 0.19) * 20.0)
    * (0.5 + 0.5 * sin(p.x * 4.0 + t * 0.48));
  float haze = exp(-radius * radius * 2.0) * 0.14;
  float energy = clamp((membrane * (0.34 + fibers * 0.32) + edge * 0.46)
    * facing * core + ribbon * 0.21 + haze, 0.0, 1.0);

  vec3 black = vec3(0.0196, 0.0196, 0.0274);
  vec3 violet = vec3(0.28, 0.10, 0.42);
  gl_FragColor = vec4(mix(black, violet, energy), 1.0);
}
`;

/** Decorative light only. Never represents capture, connection or progress. */
@Component({
  selector: 'app-ambient-orb',
  host: { 'aria-hidden': 'true' },
  styleUrl: './ambient-orb.css',
  template: `
    <canvas #canvas class="orb-canvas" aria-hidden="true" hidden></canvas>
    <div class="orb-fallback" aria-hidden="true">
      <div class="orb"><span class="orb-light"></span><span class="orb-ribbon"></span><span class="orb-core"></span></div>
    </div>
  `,
})
export class AmbientOrb implements AfterViewInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  private readonly forcedColors = matchMedia('(forced-colors: active)');
  @ViewChild('canvas', { static: true }) private canvasRef!: ElementRef<HTMLCanvasElement>;
  private gl?: WebGLRenderingContext;
  private program?: WebGLProgram;
  private buffer?: WebGLBuffer;
  private resolution?: WebGLUniformLocation | null;
  private time?: WebGLUniformLocation | null;
  private resizeObserver?: ResizeObserver;
  private frame?: number;
  private previousFrame = 0;
  private elapsed = 0;
  private destroyed = false;
  private contextFailed = false;

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.host.nativeElement.dataset['renderer'] = 'css';
      this.host.nativeElement.dataset['motion'] = 'paused';
      document.addEventListener('visibilitychange', this.syncMotion);
      this.reducedMotion.addEventListener('change', this.syncMotion);
      this.forcedColors.addEventListener('change', this.syncMotion);
      const canvas = this.canvasRef.nativeElement;
      canvas.addEventListener('webglcontextlost', this.contextLost);
      canvas.addEventListener('webglcontextrestored', this.contextRestored);
      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.host.nativeElement);
      this.syncMotion();
    });
  }

  private readonly syncMotion = (): void => {
    if (this.destroyed) return;
    const allowed = !document.hidden && !this.reducedMotion.matches && !this.forcedColors.matches;
    this.host.nativeElement.dataset['motion'] = allowed ? 'running' : 'paused';
    if (!allowed) { this.stop(); return; }
    if (!this.gl && !this.contextFailed) this.initialize();
    if (this.gl && this.frame === undefined) {
      this.resize();
      this.previousFrame = 0;
      this.frame = requestAnimationFrame(this.animate);
    }
  };

  private initialize(): void {
    const canvas = this.canvasRef.nativeElement;
    const gl = canvas.getContext('webgl', {
      alpha: false, antialias: false, depth: false, stencil: false,
      preserveDrawingBuffer: false, powerPreference: 'low-power',
    });
    if (!gl) { this.contextFailed = true; return; }
    const shaders: WebGLShader[] = [];
    let program: WebGLProgram | null = null;
    let buffer: WebGLBuffer | null = null;
    try {
      for (const [type, source] of [[gl.VERTEX_SHADER, vertexSource], [gl.FRAGMENT_SHADER, fragmentSource]] as const) {
        const shader = gl.createShader(type);
        if (!shader) throw new Error('Shader unavailable');
        shaders.push(shader);
        const precision = gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT)?.precision;
        gl.shaderSource(shader, type === gl.FRAGMENT_SHADER && precision
          ? source.replace('precision mediump float;', 'precision highp float;') : source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error('Shader compilation failed');
      }
      program = gl.createProgram();
      if (!program) throw new Error('Program unavailable');
      for (const shader of shaders) gl.attachShader(program, shader);
      gl.linkProgram(program);
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('Program linking failed');
      buffer = gl.createBuffer();
      if (!buffer) throw new Error('Buffer unavailable');
      gl.useProgram(program);
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
      const position = gl.getAttribLocation(program, 'a_position');
      gl.enableVertexAttribArray(position);
      gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
      this.resolution = gl.getUniformLocation(program, 'u_resolution');
      this.time = gl.getUniformLocation(program, 'u_time');
      this.gl = gl;
      this.program = program;
      this.buffer = buffer;
      canvas.hidden = false;
      this.host.nativeElement.dataset['renderer'] = 'webgl';
    } catch {
      if (buffer) gl.deleteBuffer(buffer);
      if (program) gl.deleteProgram(program);
      this.contextFailed = true;
      this.host.nativeElement.dataset['renderer'] = 'css';
      canvas.hidden = true;
    } finally {
      // Linked shaders can be deleted immediately; the program retains its executable.
      for (const shader of shaders) gl.deleteShader(shader);
      if (this.contextFailed) gl.getExtension('WEBGL_lose_context')?.loseContext();
    }
  }

  private resize(): void {
    if (!this.gl || this.destroyed) return;
    const bounds = this.host.nativeElement.getBoundingClientRect();
    if (bounds.width <= 0 || bounds.height <= 0) return;
    const dpr = Math.min(devicePixelRatio || 1, 1);
    const scale = Math.min(dpr, Math.sqrt(1_500_000 / (bounds.width * bounds.height)));
    const width = Math.max(1, Math.floor(bounds.width * scale));
    const height = Math.max(1, Math.floor(bounds.height * scale));
    const canvas = this.canvasRef.nativeElement;
    if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; }
    this.gl.viewport(0, 0, width, height);
  }

  private readonly animate = (now: number): void => {
    if (this.destroyed || !this.gl || document.hidden || this.reducedMotion.matches || this.forcedColors.matches) {
      this.frame = undefined;
      this.syncMotion();
      return;
    }
    this.frame = requestAnimationFrame(this.animate);
    const interval = 1000 / 30;
    const delta = this.previousFrame ? now - this.previousFrame : interval;
    if (delta < interval) return;
    this.previousFrame = now - delta % interval;
    this.elapsed = (this.elapsed + Math.min(delta, 100) / 1000) % 4096;
    this.gl.uniform2f(this.resolution ?? null, this.canvasRef.nativeElement.width, this.canvasRef.nativeElement.height);
    this.gl.uniform1f(this.time ?? null, this.elapsed);
    this.gl.drawArrays(this.gl.TRIANGLES, 0, 3);
  };

  private stop(): void {
    if (this.frame !== undefined) cancelAnimationFrame(this.frame);
    this.frame = undefined;
    this.previousFrame = 0;
  }

  private readonly contextLost = (event: Event): void => {
    event.preventDefault();
    this.stop();
    this.gl = undefined;
    this.program = undefined;
    this.buffer = undefined;
    this.canvasRef.nativeElement.hidden = true;
    this.host.nativeElement.dataset['renderer'] = 'css';
  };

  private readonly contextRestored = (): void => {
    if (this.destroyed) return;
    this.contextFailed = false;
    this.syncMotion();
  };

  ngOnDestroy(): void {
    this.destroyed = true;
    this.stop();
    document.removeEventListener('visibilitychange', this.syncMotion);
    this.reducedMotion.removeEventListener('change', this.syncMotion);
    this.forcedColors.removeEventListener('change', this.syncMotion);
    this.resizeObserver?.disconnect();
    const canvas = this.canvasRef.nativeElement;
    canvas.removeEventListener('webglcontextlost', this.contextLost);
    canvas.removeEventListener('webglcontextrestored', this.contextRestored);
    if (this.gl) {
      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, null);
      this.gl.useProgram(null);
      if (this.buffer) this.gl.deleteBuffer(this.buffer);
      if (this.program) this.gl.deleteProgram(this.program);
      this.gl.getExtension('WEBGL_lose_context')?.loseContext();
    }
    this.gl = undefined;
    this.program = undefined;
    this.buffer = undefined;
  }
}
