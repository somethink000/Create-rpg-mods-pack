#!/bin/sh
# Undoes flatten_jarjar.py (and the unbundled Aeronautics / Gadgets and Gizmos) in a game folder: unflatten_jarjar.sh <instance or server dir>
cd "$1" || exit 1
while read -r f; do [ -n "$f" ] && rm -f "mods/$f"; done < mods-removed/flatten-extracted.txt
while read -r f; do [ -n "$f" ] && cp "mods-removed/$f" "mods/$f"; done < mods-removed/flatten-holders.txt
rm -f mods/*-bundled-*-shell.jar
for f in gadgets-and-gizmos-bundled-2.0.0.jar create-aeronautics-bundled-1.21.1-1.3.2.jar; do [ -f "mods-removed/$f" ] && cp "mods-removed/$f" mods/; done
echo "restored"
