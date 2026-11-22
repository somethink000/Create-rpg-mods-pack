#!/bin/sh
# Sync the pack (mods, config, kubejs, defaultconfigs) from the launcher instance into this test server.
# Client-only mods are skipped: they crash or do nothing on a dedicated server.
set -e
SERVER="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$SERVER/../instance"
CLIENT_ONLY='^(sodium|iris|ImmediatelyFast|entityculling|moreculling|dynamic-fps|AeroPropBlur|better-advanced-tooltips|aeronautics_windsound|createbetterfps|enhanced_boss_bars|Mini-boss Boss Bars|sablemassview)'
mkdir -p "$SERVER/mods"
rm -f "$SERVER"/mods/*.jar
for jar in "$PACK"/mods/*.jar; do
  name=$(basename "$jar")
  echo "$name" | grep -qE "$CLIENT_ONLY" && continue
  cp "$jar" "$SERVER/mods/"
done
for dir in config defaultconfigs kubejs; do
  rm -rf "${SERVER:?}/$dir"
  cp -a "$PACK/$dir" "$SERVER/$dir"
done
# The pack's own core mod, if built.
cp "$SERVER"/../core/build/libs/expedition-*.jar "$SERVER/mods/" 2>/dev/null || true
echo "mods: $(ls "$SERVER/mods" | wc -l)"
