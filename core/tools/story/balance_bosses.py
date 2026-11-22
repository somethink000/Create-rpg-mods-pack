"""Sets every story boss's health from a trial (/expedition trial -> testserver/trial.tsv): the chapter's kill time with
its sword (route/Trial.targetSeconds: 30 s in chapter I, 15 s more each chapter after) at the damage a swing really did
(the best of front and back; a boss hurt only by its mechanic - the Slider by a pickaxe, the Harbinger once woken - by
the sword through its armour). Writes the health into GUARDIANS in story_text.py; run gen_story.py after.

    python3 balance_bosses.py [trial.tsv]
"""
import csv
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TRIAL = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', '..', '..', 'testserver', 'trial.tsv')
SWINGS_PER_SECOND = 1.6


def target_seconds(act):
    return 30 + 15 * (act - 1)


# Bosses whose mod runs their health itself (the Sephirot: their arena's spawner sets it): left alone.
OWN_HEALTH = ('fdbosses:',)


# Bosses hurt only in a window the trial seldom hits (the Wroughtnaut: from behind, while its axe is stuck): measured
# by the sword through its armour, as a player striking in the window does.
WINDOWED = ('03_wrought_chamber',)
# Health set by hand where a trial's swings scatter (the Ur-Ghast flies through them): step -> health.
FIXED = {'54_dark_tower': 120}


def hit_of(row):
    """The damage a swing does: as measured (the boss's own damage cuts too), unless its mechanic held every swing off
    (then through its armour)."""
    measured = max(float(row['front_hit'] or 0), float(row['back_hit'] or 0))
    formula = float(row.get('formula_hit') or 0)
    return formula if measured < 0.5 or row['step'] in WINDOWED else measured


def health(row):
    if row['boss'].startswith(OWN_HEALTH):
        return None
    if row['step'] in FIXED:
        return FIXED[row['step']]
    hit = hit_of(row)
    if hit < 0.05:
        return None
    raw = target_seconds(int(row['act'])) * SWINGS_PER_SECOND * hit
    return max(40, int(round(raw / 10.0)) * 10)


def main():
    rows = {row['step']: row for row in csv.DictReader(open(TRIAL, encoding='utf-8'), delimiter='\t') if row.get('front_hit')}
    path = os.path.join(HERE, 'story_text.py')
    text = open(path, encoding='utf-8').read()
    start = text.index('GUARDIANS = {')
    end = text.index('\n}\n', start)
    block = text[start:end]
    changed = 0
    for step, row in rows.items():
        hp = health(row)
        if hp is None:
            print(f'{step}: left as it is')
            continue
        # The second field of the step's tuple is its health.
        pattern = re.compile(r"(\n    '" + re.escape(step) + r"': \('[^']+', )(\d+)(, )")
        block, count = pattern.subn(lambda m: m.group(1) + str(hp) + m.group(3), block)
        if count:
            changed += 1
            print(f'{step}: act {row["act"]}, {row["boss"]}, hit {hit_of(row):.1f} -> health {hp}')
        else:
            print(f'{step}: not in GUARDIANS')
    open(path, 'w', encoding='utf-8').write(text[:start] + block + text[end:])
    print(f'{changed} bosses balanced')


main()
