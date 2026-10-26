import { LocalAudioTrack, LocalVideoTrack, Track } from 'livekit-client';

// Development signal only. No camera, microphone or display capture is requested.
export class SyntheticMedia {
  readonly video: LocalVideoTrack;
  readonly audio: LocalAudioTrack;
  private readonly oscillator: OscillatorNode;
  private readonly gain: GainNode;
  private readonly stream: MediaStream;
  private readonly timer: ReturnType<typeof setInterval>;

  constructor(context: AudioContext) {
    const canvas = document.createElement('canvas');
    canvas.width = 640;
    canvas.height = 360;
    const drawing = canvas.getContext('2d');
    if (!drawing) throw new Error('CANVAS_UNAVAILABLE');
    let frame = 0;
    const draw = (): void => {
      frame += 1;
      drawing.fillStyle = '#16324f';
      drawing.fillRect(0, 0, canvas.width, canvas.height);
      drawing.fillStyle = '#66e0b8';
      drawing.fillRect((frame * 5) % 580, 200, 60, 60);
      drawing.fillStyle = 'white';
      drawing.font = '28px system-ui';
      drawing.fillText('T-02 · VÍDEO SINTÉTICO', 30, 65);
      drawing.font = '20px system-ui';
      drawing.fillText(`Quadro ${frame} · ${new Date().toISOString()}`, 30, 115);
      drawing.fillText('Sem captura de tela ou microfone', 30, 155);
    };
    draw();
    this.timer = setInterval(draw, 50);
    this.stream = canvas.captureStream(20);
    this.video = new LocalVideoTrack(this.stream.getVideoTracks()[0], undefined, true);
    this.video.source = Track.Source.ScreenShare;

    const destination = context.createMediaStreamDestination();
    this.oscillator = context.createOscillator();
    this.oscillator.frequency.value = 440;
    this.gain = context.createGain();
    this.gain.gain.value = 0.06;
    this.oscillator.connect(this.gain).connect(destination);
    this.oscillator.start();
    this.audio = new LocalAudioTrack(destination.stream.getAudioTracks()[0], undefined, true, context);
    this.audio.source = Track.Source.ScreenShareAudio;
  }

  stop(): void {
    clearInterval(this.timer);
    this.video.stop();
    this.audio.stop();
    this.stream.getTracks().forEach((track) => track.stop());
    this.oscillator.stop();
    this.oscillator.disconnect();
    this.gain.disconnect();
  }
}
