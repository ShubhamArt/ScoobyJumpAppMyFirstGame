import wave
import struct
import math
import random
import os

SAMPLE_RATE = 44100
DURATION_FAIL = 1.5
DURATION_LOOP = 10.0

def save_wav(filename, samples):
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(SAMPLE_RATE)
        
        # Batch write for performance
        data = bytearray()
        for s in samples:
            s = max(-1.0, min(1.0, s))
            val = int(s * 32767.0)
            data.extend(struct.pack('<h', val))
        wav_file.writeframes(data)

def generate_fail_sound():
    samples = []
    num_samples = int(SAMPLE_RATE * DURATION_FAIL)
    
    # Precompute phase
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        # Descending pitch from 400Hz to 50Hz linearly
        freq = 400.0 - (350.0 * (t / DURATION_FAIL))
        phase += (freq / SAMPLE_RATE) * 2.0 * math.pi
        
        # 8-bit square wave style
        wave_val = 1.0 if math.sin(phase) > 0 else -1.0
        
        # Add a little noise for rough impact at the end
        noise = random.uniform(-0.5, 0.5)
        
        # Volume envelope that decays
        envelope = math.exp(-t * 2.5)
        
        # Combine
        audio = wave_val * envelope + noise * (1.0 - envelope) * 0.3 * math.exp(-t * 4.0)
        samples.append(audio * 0.8) # 80% volume
        
    return samples

def generate_loop_sound():
    samples = []
    num_samples = int(SAMPLE_RATE * DURATION_LOOP)
    
    # 4-chord progression over 10 seconds: Fm, Eb, Db, Eb
    notes = [174.61, 155.56, 138.59, 155.56] # F3, Eb3, Db3, Eb3
    segment_len = num_samples // 4
    
    kick_phase = 0.0
    
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        
        # Identify current chord
        chord_index = min(i // segment_len, 3)
        freq = notes[chord_index]
        
        # Compute time within the current segment
        t_seg = t - (chord_index * (DURATION_LOOP / 4.0))
        
        # Add harmonics
        base_phase = 2.0 * math.pi * freq * t_seg
        wave_val = (math.sin(base_phase) + 
                    0.5 * math.sin(base_phase * 1.5) +
                    0.2 * math.sin(base_phase * 2.0))
                    
        # Pad envelope (slow attack, slow release)
        env = 1.0
        attack_time = 0.5
        release_time = 0.5
        segment_duration = DURATION_LOOP / 4.0
        
        if t_seg < attack_time:
            env = t_seg / attack_time
        elif t_seg > (segment_duration - release_time):
            env = (segment_duration - t_seg) / release_time
            
        pad_audio = wave_val * env * 0.3 # Reduce volume of pad
        
        # Add light beat every 0.5s (120 BPM)
        beat_interval = 0.5
        t_kick = t % beat_interval
        kick_audio = 0.0
        
        if t_kick < 0.1:
            kick_env = math.exp(-t_kick * 30.0)
            kick_freq = 120.0 - (80.0 * (t_kick / 0.1))
            kick_phase += (kick_freq / SAMPLE_RATE) * 2.0 * math.pi
            kick_audio = math.sin(kick_phase) * kick_env * 0.5
        else:
            kick_phase = 0.0
            
        combined = pad_audio + kick_audio
        samples.append(combined * 0.6) # Main volume reduction

    return samples

if __name__ == "__main__":
    out_dir = r"app\src\main\res\raw"
    os.makedirs(out_dir, exist_ok=True)
    
    print("Generating Fail sound...")
    save_wav(os.path.join(out_dir, "gameover_fail.wav"), generate_fail_sound())
    
    print("Generating Loop sound...")
    save_wav(os.path.join(out_dir, "gameover_loop.wav"), generate_loop_sound())
    
    print("Game Over audio generated successfully.")
