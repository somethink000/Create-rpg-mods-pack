#!/bin/sh
# One release, one version everywhere: the players' pack (a GitHub release with the .mrpack) and the VPS server.
#   ./release.sh 0.2.0 "что изменилось"
#   ./release.sh 0.2.0 "что изменилось" modrinth    also upload the version to Modrinth
# Fixes are made here (instance/ for the pack, testserver/ for the server), never on the VPS: deploy-vps.sh
# overwrites the server's mods, config and kubejs with testserver/ (world, server.properties and the pregen stay).
set -e
[ -n "$1" ] || { echo "usage: ./release.sh <version> \"<changelog>\" [modrinth]"; exit 1; }
cd "$(dirname "$0")"
VERSION=$1 NOTES=${2:-}
if [ "$3" = modrinth ]; then
    ./release-modrinth.py "$VERSION" --publish "$NOTES"
else
    ./release-modrinth.py "$VERSION"
fi
git add -A
git commit -q -m "Release $VERSION" -m "$NOTES" || true
git tag "v$VERSION"
git push -q origin main "v$VERSION"
cp "dist/Expedition-$VERSION.mrpack" dist/Expedition.mrpack  # a fixed name: releases/latest/download/Expedition.mrpack
gh release create "v$VERSION" dist/Expedition.mrpack --title "Expedition $VERSION" --notes "$NOTES"
./deploy-vps.sh
