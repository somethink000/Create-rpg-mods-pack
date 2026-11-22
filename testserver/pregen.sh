#!/bin/sh
# Pre-generates the whole map with Chunky, from the middle outwards, so players never wait for (or lag the server with)
# new terrain, and Distant Horizons can show the far land (on a server it builds that view only from existing chunks).
# The map is one dimension (the realms lie in the Overworld): the square inside the world border (+-15000), ~3.5 million
# chunks. Run it while nobody plays and stop it when someone joins; the progress is kept between runs and restarts.
#   ./pregen.sh start    begin, or carry on where it stopped
#   ./pregen.sh stop     pause (progress saved)
#   ./pregen.sh status   how far it is
# Speed: ~90 chunks/s with 16 threads (~11 h in all), ~20-30 chunks/s on the 3-core VPS (~35-50 h in all).
# Generate only with the final world generation (core jar): chunks made now never change later.
set -e
cd "$(dirname "$0")"
R="python3 tools/rcon.py"
TASK=config/chunky/tasks/minecraft/overworld.properties

case "$1" in
start)
    # Carry on a paused task first; a new one never overwrites a task (no "chunky confirm").
    if ! $R "chunky continue" | tee /dev/stderr | grep -q "No tasks to continue"; then
        exit 0
    fi
    # The whole square is (30000 / 16)^2 = 3.5 million chunks.
    if [ -f "$TASK" ] && grep -q "^shape=square" "$TASK" && [ "$(sed -n 's/^chunks=//p' "$TASK")" -ge 3515000 ]; then
        echo "The whole map is already generated."
        exit 0
    fi
    $R "chunky world minecraft:overworld" "chunky worldborder" "chunky shape square" "chunky pattern concentric" \
       "chunky quiet 60" "chunky start"
    ;;
stop)
    $R "chunky pause"
    ;;
status)
    $R "chunky progress"
    ;;
*)
    sed -n '2,10p' "$0"
    exit 1
    ;;
esac
