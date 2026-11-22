"""Voices the story in English (every game language hears it under its own subtitles; the Russian voice was poor): every spoken line of a lang file -> assets/expedition/sounds/voice/<lang>/<key>.ogg,
its sound event voice.<lang>.<key> in sounds.json and its length in voice/durations_<lang>.json (the client plays the
game language's recording and times the line by it; CutsceneClient).

With Coqui XTTS-v2 (one built-in voice for the Tree, on the CPU; the model in
~/.local/share/tts/tts_models--multilingual--multi-dataset--xtts_v2), run with uv on Python 3.11.
Lines are re-voiced only when their text or voice changed (voice_cache_<lang>.json); a full run takes 2-3 hours.

    python3 gen_voice.py          # after gen_story.py
"""
import hashlib
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CORE = os.path.abspath(os.path.join(HERE, '..', '..'))
MODEL = os.path.expanduser('~/.local/share/tts/tts_models--multilingual--multi-dataset--xtts_v2')
UV = ['uv', 'run', '--python', '3.11', '--index', 'https://download.pytorch.org/whl/cpu', '--index-strategy', 'unsafe-best-match',
      '--with', 'coqui-tts==0.24.3', '--with', 'torch==2.5.1', '--with', 'torchaudio==2.5.1', '--with', 'transformers==4.46.3', '--with', 'click']

# Every voice to the same loudness (EBU R128), no clipping. Elian talks over the radio; the Tree echoes.
LOUD = 'loudnorm=I=-16:TP=-1.5:LRA=11'
RADIO = 'highpass=f=280,lowpass=f=3400,acompressor=threshold=-18dB:ratio=3,' + LOUD
ECHO = 'aecho=0.8:0.7:90|180:0.35|0.2,' + LOUD
# The Tree: a woman's voice, cold and proud. Under it a quiet layer a fourth lower (rubberband, formants kept, so the voice
# itself is never pitch-shifted - that made it metallic), a slow chorus and a hollow multi-tap echo, top end softened: the
# effect is the Tree's, and it hides the engine's small artefacts.
TREE = ('highpass=f=70,asplit[v][d];[d]rubberband=pitch=0.75:formant=preserved,lowpass=f=2200,volume=0.3[low];'
        '[v][low]amix=inputs=2:normalize=0,deesser,chorus=0.7:0.9:45|60:0.22|0.18:0.25|0.4:1.4|1.9,'
        'aecho=0.8:0.75:80|170|290:0.28|0.16|0.08,lowpass=f=9500,acompressor=threshold=-20dB:ratio=2.5:attack=5:release=150,' + LOUD)
TREE_VOICE = os.environ.get('TREE_VOICE', 'Tammie Ema')
# Speaker letter -> (XTTS voice, speed, ffmpeg filter); the story has only the Tree now (T).
VOICES = {
    'en': {'T': (TREE_VOICE, 0.9, TREE)},
}
ASSETS = os.path.join(CORE, 'src/main/resources/assets/expedition')
DATA = os.path.join(CORE, 'src/main/resources/data/expedition')


def spoken_lines():
    """(lang key, speaker letter) of every scene line: chapter openings, keystone outros, the finale, step scenes."""
    acts = open(os.path.join(CORE, 'src/main/java/net/expedition/route/Acts.java'), encoding='utf-8').read()
    lines = []
    for act, intro, keystones in re.findall(r'new Act\((\d+), "[^"]*", "([A-Z]+)", Map\.of\(([^)]*)\)\)', acts):
        lines += [(f'expedition.story.act.{act}.{i + 1}', c) for i, c in enumerate(intro)]
        for step, speakers in re.findall(r'"([^"]+)", "([A-Z]+)"', keystones):
            lines += [(f'expedition.story.outro.{step}.{i + 1}', c) for i, c in enumerate(speakers)]
    prologue = open(os.path.join(CORE, 'src/main/java/net/expedition/route/Prologue.java'), encoding='utf-8').read()
    for place, speakers in re.findall(r'new Shot\("(\w+)", "([A-Z]+)"', prologue):
        lines += [(f'expedition.story.prologue.{place}.{i + 1}', c) for i, c in enumerate(speakers)]
    finale = re.search(r'FINALE = "([A-Z]+)"', acts).group(1)
    lines += [(f'expedition.story.finale.{i + 1}', c) for i, c in enumerate(finale)]
    wrath = open(os.path.join(CORE, 'src/main/java/net/expedition/route/TreeWrath.java'), encoding='utf-8').read()
    lines += [(f'expedition.story.wrath.{i}.1', 'T') for i in range(1, int(re.search(r'LINES = (\d+)', wrath).group(1)) + 1)]
    route = os.path.join(DATA, 'expedition_route')
    for name in sorted(os.listdir(route)):
        step = name[:-5]
        arrive, _, fall = json.load(open(os.path.join(route, name), encoding='utf-8')).get('scenes', '').partition('|')
        lines += [(f'expedition.story.arrive.{step}.{i + 1}', c) for i, c in enumerate(arrive)]
        lines += [(f'expedition.story.fall.{step}.{i + 1}', c) for i, c in enumerate(fall)]
    return lines


def clean(text, lang):
    """What the engine reads: ellipses to dots, dashes to pauses, no quotes or symbols it can't voice."""
    text = text.replace('…', '... ').replace('—', ', ').replace(' - ', ', ').replace('«', '').replace('»', '')
    if lang == 'ru':
        text = re.sub(r'[^0-9а-яА-ЯёЁ+\s.,!?:;()-]', '', text)
    return re.sub(r'\s+', ' ', text).strip(' ,')


class Xtts:
    def __init__(self):
        os.environ['COQUI_TOS_AGREED'] = '1'
        import torch
        torch.set_num_threads(os.cpu_count() or 8)
        from TTS.api import TTS
        self.tts = TTS(model_path=MODEL, config_path=os.path.join(MODEL, 'config.json'))

    def wav(self, text, voice, speed, path, lang):
        """One sentence at a time (XTTS babbles past the end of long inputs), each re-voiced if it runs far longer than its
        text should, trimmed of a stray syllable after its end; joined with short pauses."""
        import numpy as np
        import wave
        parts = []
        for sentence in re.split(r'(?<=[.!?])\s+', text):
            limit = 0.11 * len(sentence) / speed + 1.2
            for attempt in range(4):
                # Straight to the synthesizer: TTS.tts() takes speed but drops it (coqui-tts 0.24.3).
                audio = trim(np.array(self.tts.synthesizer.tts(text=sentence, speaker_name=voice, language_name=lang, split_sentences=False,
                                                               speed=speed, temperature=0.6, top_p=0.8, repetition_penalty=8.0), dtype=np.float32))
                if len(audio) / RATE <= limit:
                    break
            parts += [audio, np.zeros(int(0.4 * RATE), np.float32)]
        audio = np.concatenate(parts[:-1])
        with wave.open(path, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((np.clip(audio, -1, 1) * 32767).astype('<i2').tobytes())


RATE = 24000


def trim(audio):
    """Cuts the silence around a sentence and a short burst after a pause at its end (XTTS's stray "monte" after the last
    word)."""
    import numpy as np
    frame = RATE // 50
    rms = np.sqrt(np.mean(audio[:len(audio) // frame * frame].reshape(-1, frame) ** 2, axis=1))
    loud = rms > max(0.08 * rms.max(), 1e-4)
    runs, start = [], None
    for i, on in enumerate(list(loud) + [False]):
        if on and start is None:
            start = i
        elif not on and start is not None:
            if runs and start - runs[-1][1] < 6:  # pauses under 120 ms are inside a word
                runs[-1] = (runs[-1][0], i)
            else:
                runs.append((start, i))
            start = None
    if not runs:
        return audio
    # A burst under 450 ms after a pause of 200 ms or more, at the very end, is not speech.
    while len(runs) > 1 and runs[-1][1] - runs[-1][0] < 23 and runs[-1][0] - runs[-2][1] >= 10:
        runs.pop()
    return audio[max(0, runs[0][0] - 2) * frame:min(len(rms), runs[-1][1] + 6) * frame]


ENGINE = None


def voice_language(lang):
    """Voices every line of one language with its engine; updates its files only."""
    import wave
    texts = json.load(open(os.path.join(ASSETS, f'lang/{"ru_ru" if lang == "ru" else "en_us"}.json'), encoding='utf-8'))
    cache_path = os.path.join(CORE, f'tools/story/voice_cache_{lang}.json')
    cache = json.load(open(cache_path, encoding='utf-8')) if os.path.exists(cache_path) else {}
    out_dir = os.path.join(ASSETS, 'sounds/voice', lang)
    os.makedirs(out_dir, exist_ok=True)
    durations, wanted, made = {}, set(), 0
    for key, letter in spoken_lines():
        if key not in texts:
            print('no text for', key)
            continue
        name = key[len('expedition.story.'):]
        voice, speed, effect = VOICES[lang][letter]
        text = clean(texts[key], lang)
        digest = hashlib.sha1(f'{voice}|{speed}|{effect}|{text}'.encode()).hexdigest()
        ogg = os.path.join(out_dir, name + '.ogg')
        wanted.add(name + '.ogg')
        if cache.get(key, {}).get('hash') != digest or not os.path.exists(ogg):
            global ENGINE
            ENGINE = ENGINE or Xtts()
            raw = f'/tmp/expedition-voice-{lang}.wav'
            ENGINE.wav(text, voice, speed, raw, lang)
            with wave.open(raw) as w:
                ms = int(w.getnframes() * 1000 / w.getframerate())
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', raw, '-af', effect, '-ar', '48000', '-ac', '1', '-c:a', 'libvorbis',
                            '-q:a', '5', ogg], check=True)
            cache[key] = {'hash': digest, 'ms': ms}
            made += 1
            # After every line: a run that is stopped (it takes hours) resumes where it stopped.
            json.dump(cache, open(cache_path, 'w', encoding='utf-8'), indent=1, sort_keys=True)
        durations[key] = cache[key]['ms']
    for old in os.listdir(out_dir):
        if old not in wanted:
            os.remove(os.path.join(out_dir, old))
    json.dump({k: v for k, v in cache.items() if k in durations}, open(cache_path, 'w', encoding='utf-8'), indent=1, sort_keys=True)
    os.makedirs(os.path.join(ASSETS, 'voice'), exist_ok=True)
    json.dump(durations, open(os.path.join(ASSETS, f'voice/durations_{lang}.json'), 'w', encoding='utf-8'), indent=1, sort_keys=True)
    print(f'voice {lang}: {len(durations)} lines, {made} voiced now, {sum(durations.values()) // 1000} s in all')
    return [key[len('expedition.story.'):] for key in durations]


def write_sounds(names_by_lang):
    path = os.path.join(ASSETS, 'sounds.json')
    sounds = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    sounds = {k: v for k, v in sounds.items() if not k.startswith('voice.')}
    for lang, names in names_by_lang.items():
        for name in names:
            sounds[f'voice.{lang}.{name}'] = {'sounds': [{'name': f'expedition:voice/{lang}/{name}', 'stream': True}]}
    json.dump(sounds, open(path, 'w', encoding='utf-8'), indent=2, ensure_ascii=False, sort_keys=True)


if __name__ == '__main__':
    if os.environ.get('VOICE_RUN'):
        # Inside the XTTS environment: every voiced language with one loaded model; the line names last.
        print(json.dumps({lang: voice_language(lang) for lang in VOICES}))
        sys.exit(0)
    result = subprocess.run(UV + ['python', os.path.abspath(__file__)], env={**os.environ, 'VOICE_RUN': '1'}, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f'voice failed:\n{result.stderr[-3000:]}')
    out = result.stdout.strip().splitlines()
    print('\n'.join(line for line in out[:-1] if line.startswith('voice ')))
    write_sounds(json.loads(out[-1]))
    # The single-language files of the first version.
    for old in ('voice/durations.json',):
        if os.path.exists(os.path.join(ASSETS, old)):
            os.remove(os.path.join(ASSETS, old))
    for old in os.listdir(os.path.join(ASSETS, 'sounds/voice')):
        if old.endswith('.ogg'):
            os.remove(os.path.join(ASSETS, 'sounds/voice', old))
