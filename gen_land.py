import wave
import struct
import math
import os

def create_land_wav(filename):
    nchannels = 1
    sampwidth = 2
    framerate = 44100
    duration = 0.08 # 80ms fast tap
    nframes = int(framerate * duration)
    
    with wave.open(filename, 'w') as wav_file:
        wav_file.setnchannels(nchannels)
        wav_file.setsampwidth(sampwidth)
        wav_file.setframerate(framerate)
        
        for i in range(nframes):
            t = float(i) / framerate
            # landing 'tap': low frequency quick hit
            freq = 200 - (100 * (t / duration))
            volume = 15000 * (1 - (t / duration)) # quick decay
            value = int(volume * math.sin(2 * math.pi * freq * t))
            data = struct.pack('<h', value)
            wav_file.writeframesraw(data)

out_dir = r"C:\Users\Shubham\Desktop\ScoobyJumpAppNew\app\src\main\res\raw"
os.makedirs(out_dir, exist_ok=True)
create_land_wav(os.path.join(out_dir, "land.wav"))
print("Done writing land.wav")
