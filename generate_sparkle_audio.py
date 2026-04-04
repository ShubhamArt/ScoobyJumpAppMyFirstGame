import wave
import struct
import math
import os
import random

raw_dir = 'app/src/main/res/raw'
os.makedirs(raw_dir, exist_ok=True)

def create_ambient_sparkle():
    path = os.path.join(raw_dir, 'ambient_sparkle.wav')
    file = wave.open(path, 'w')
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(44100)
    
    # 3 second ambient loop
    duration = 3.0
    
    # frequencies for a "magical sparkle" pentatonic
    chime_freqs = [1046.50, 1174.66, 1318.51, 1567.98, 1760.00, 2093.00, 2349.32, 2637.02, 3135.96]
    
    # Create some random chime trigger points
    chimes = []
    for _ in range(12):
        start_time = random.uniform(0, duration)
        freq = random.choice(chime_freqs)
        chimes.append((start_time, freq))
        
    frames = []
    for i in range(int(44100 * duration)):
        t = i / 44100.0
        sample = 0
        
        # very soft ethereal pad base
        pad_freq = 261.63 # C4
        pad_val = math.sin(2.0 * math.pi * pad_freq * t) + math.sin(2.0 * math.pi * (pad_freq * 1.5) * t)
        sample += int(1000 * pad_val)
        
        # chimes
        for start_t, freq in chimes:
            if t >= start_t:
                dt = t - start_t
                if dt < 0.5:
                    env = max(0, 1.0 - (dt / 0.5))
                    env = env ** 2
                    chime_val = math.sin(2.0 * math.pi * freq * t)
                    sample += int(4000 * env * chime_val)
                    
        # wrap loop smoothly (fade in/out ends slightly to avoid clicks)
        fade_env = 1.0
        if t < 0.1:
            fade_env = t / 0.1
        elif t > duration - 0.1:
            fade_env = (duration - t) / 0.1
            
        sample = int(sample * fade_env)
        sample = max(-32768, min(32767, sample))
        
        frames.append(struct.pack('<h', sample))
        
        if len(frames) >= 44100 * 2:
            file.writeframes(b''.join(frames))
            frames.clear()
            
    if len(frames) > 0:
        file.writeframes(b''.join(frames))
        
    file.close()

print("Generating ambient sparkle...")
create_ambient_sparkle()
print("Done.")
