import wave
import struct
import math
import random
import os

SAMPLE_RATE = 44100
DURATION = 1.0 # 1 second

def save_wav(filename, samples):
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(SAMPLE_RATE)
        for s in samples:
            # clamp
            s = max(-1.0, min(1.0, s))
            val = int(s * 32767.0)
            wav_file.writeframes(struct.pack('<h', val))

def generate_shop_open():
    samples = []
    # Ascending major 7th chord for the chime
    notes = [523.25, 659.25, 783.99, 987.77, 1318.51] # C5, E5, G5, B5, C6
    
    for i in range(int(SAMPLE_RATE * DURATION)):
        t = i / SAMPLE_RATE
        
        # 1. The Whoosh
        # White noise filtered by an envelope. Swells up in first 0.3s, fades by 0.6s
        whoosh_envelope = 0.0
        if t < 0.3:
            whoosh_envelope = t / 0.3
        elif t < 0.6:
            whoosh_envelope = 1.0 - ((t - 0.3) / 0.3)
            
        noise = random.uniform(-1.0, 1.0)
        whoosh = noise * whoosh_envelope * 0.15 # Soft whoosh
        
        # 2. The Chime
        chime = 0.0
        if t >= 0.2:
            chime_t = t - 0.2
            for j, freq in enumerate(notes):
                note_start = j * 0.08
                if chime_t >= note_start:
                    note_t = chime_t - note_start
                    # FM Synthesis for a bell-like 'magical' tone
                    mod = math.sin(2.0 * math.pi * (freq * 1.5) * note_t) * math.exp(-note_t * 5)
                    carrier = math.sin(2.0 * math.pi * freq * note_t + mod * 2.5)
                    env = math.exp(-note_t * 6.0) # Sharp decay
                    chime += carrier * env * 0.3
        
        # Combine
        combined = whoosh + chime
        if t > 0.8:
            combined *= (1.0 - t) / 0.2 # Fade out
            
        samples.append(combined)
        
    out_dir = r"c:\Users\Shubham\Desktop\ScoobyJumpAppNew\app\src\main\res\raw"
    os.makedirs(out_dir, exist_ok=True)
    save_wav(os.path.join(out_dir, "shop_open.wav"), samples)
    print("shop_open.wav generated successfully!")

if __name__ == "__main__":
    generate_shop_open()
