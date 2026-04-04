import wave
import struct
import math
import os

# Create raw directory
raw_dir = 'app/src/main/res/raw'
os.makedirs(raw_dir, exist_ok=True)

# Jump: Pitch sliding up
def create_jump_sound():
    path = os.path.join(raw_dir, 'jump.wav')
    file = wave.open(path, 'w')
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(44100)
    duration = 0.2
    for i in range(int(44100 * duration)):
        t = i / 44100.0
        freq = 400 + (t / duration) * 400
        # apply quick fadeout
        env = max(0, 1 - (t / duration))
        value = int(16000.0 * env * math.sin(2.0 * math.pi * freq * t))
        file.writeframes(struct.pack('<h', value))
    file.close()

# Coin: high pitch beep
def create_coin_sound():
    path = os.path.join(raw_dir, 'coin.wav')
    file = wave.open(path, 'w')
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(44100)
    duration = 0.8
    for i in range(int(44100 * duration)):
        t = i / 44100.0
        
        # Silent infill
        if t < 0.05:
            value = 0
            file.writeframes(struct.pack('<h', value))
            continue
            
        # Arpeggio logic
        if t < 0.2:
            freq = 392.00 # G4
            local_t = t - 0.05
            note_dur = 0.15
        elif t < 0.35:
            freq = 523.25 # C5
            local_t = t - 0.2
            note_dur = 0.15
        elif t < 0.5:
            freq = 659.25 # E5
            local_t = t - 0.35
            note_dur = 0.15
        else:
            freq = 783.99 # G5
            local_t = t - 0.5
            note_dur = 0.3
            
        # 8-bit square wave texture
        wave_form = 1.0 if math.sin(2.0 * math.pi * freq * t) > 0 else -1.0
        
        # Short plucky decay per note
        env = max(0, 1.0 - (local_t / note_dur))
        env = env ** 3 # Sharp ping texture
        
        value = int(10000.0 * env * wave_form)
        file.writeframes(struct.pack('<h', value))
    file.close()

# Game Over (Crash): Pitch sliding down
def create_game_over_sound():
    path = os.path.join(raw_dir, 'gameover.wav')
    file = wave.open(path, 'w')
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(44100)
    duration = 0.6
    for i in range(int(44100 * duration)):
        t = i / 44100.0
        freq = 300 - (t / duration) * 200
        env = max(0, 1 - (t / duration))
        value = int(20000.0 * env * math.sin(2.0 * math.pi * freq * t))
        file.writeframes(struct.pack('<h', value))
    file.close()

# BGM: 1-minute upbeat melodic pop-step / chiptune EDM
def create_bgm():
    import random
    path = os.path.join(raw_dir, 'bgm.wav')
    file = wave.open(path, 'w')
    file.setnchannels(1)
    file.setsampwidth(2)
    file.setframerate(44100)
    
    bpm = 120.0
    beat_dur = 60.0 / bpm
    total_beats = 120 # 1 minute duration at 120 BPM
    duration = total_beats * beat_dur
    
    # 8-bit Melody Sequence (C minor pentatonic arpeggio)
    # C5, Eb5, F5, G5, Bb5, C6
    melody_notes = [523.25, 622.25, 698.46, 783.99, 523.25, 783.99, 932.33, 1046.50]
    bass_notes = [130.81, 130.81, 155.56, 174.61] # C3, C3, Eb3, F3
    
    frames = []
    
    for i in range(int(44100 * duration)):
        t = i / 44100.0
        beat_idx = int(t / beat_dur)
        beat_t = t % beat_dur
        
        sample = 0
        
        # 1. Kick Drum (Steady Four-on-the-Floor)
        if beat_t < 0.2:
            # sliding pitch kick
            kick_freq = 150 - (beat_t / 0.2) * 110
            kick_env = max(0, 1 - (beat_t / 0.2))
            sample += int(10000 * kick_env * math.sin(2.0 * math.pi * kick_freq * beat_t))
            
        # 2. Hi-Hat (Offbeat)
        offbeat_t = (t - (beat_dur / 2.0)) % beat_dur
        if offbeat_t < 0.05 and offbeat_t > 0:
            hat_env = max(0, 1 - (offbeat_t / 0.05))
            sample += int(3000 * hat_env * random.uniform(-1.0, 1.0))
            
        # 3. Quick Clap (Snare) on beats 2 and 4
        if beat_idx % 2 == 1:
            clap_t = beat_t
            if clap_t < 0.15:
                clap_env = max(0, 1 - (clap_t / 0.15))
                sample += int(5000 * clap_env * random.uniform(-1.0, 1.0))
                
        # 4. Driving Bouncy Bass (16th notes)
        sixteenth_dur = beat_dur / 4.0
        sixteenth_t = t % sixteenth_dur
        bass_idx = int((t % (beat_dur * 4)) / beat_dur) # Changes every beat
        bass_freq = bass_notes[bass_idx % len(bass_notes)]
        
        if sixteenth_t < sixteenth_dur * 0.8: # Play for 80% of the 16th note (staccato)
            bass_env = max(0, 1 - (sixteenth_t / (sixteenth_dur * 0.8)))
            # Sawtooth approximation
            bass_val = 2.0 * ((bass_freq * t) - math.floor(bass_freq * t + 0.5))
            sample += int(3500 * bass_env * bass_val)
            
        # 5. Playful Square-Wave Arpeggio (8th notes)
        eighth_dur = beat_dur / 2.0
        eighth_idx = int(t / eighth_dur)
        eighth_t = t % eighth_dur
        
        note = melody_notes[eighth_idx % len(melody_notes)]
        
        if eighth_t < eighth_dur * 0.9:
            arp_env = max(0, 1 - (eighth_t / (eighth_dur * 0.9)))
            arp_val = 1.0 if math.sin(2.0 * math.pi * note * t) > 0 else -1.0
            sample += int(2500 * arp_env * arp_val)

        # Hard clip to 16-bit bounds
        sample = max(-32768, min(32767, sample))
        
        frames.append(struct.pack('<h', sample))
        
        # Write chunks to memory efficiently
        if len(frames) >= 44100 * 2:
            file.writeframes(b''.join(frames))
            frames.clear()
            
    if len(frames) > 0:
        file.writeframes(b''.join(frames))
        
    file.close()

print("Generating audio assets...")
create_jump_sound()
create_coin_sound()
create_game_over_sound()
create_bgm()
print("Audio generation complete.")
