#!/bin/sh
# Builds the core mod and the Antique Atlas port and puts the jars into the pack and the test server.
#   ./deploy.sh          build both and deploy
#   ./deploy.sh core     only the core mod
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
if [ "$1" != "core" ]; then
  (cd "$ROOT/atlas" && ./gradlew build --console=plain -q)
  cp "$ROOT"/atlas/build/libs/antique_atlas-neoforge-*.jar "$ROOT/core/libs/"
  cp "$ROOT"/atlas/build/libs/antique_atlas-neoforge-*.jar "$ROOT/instance/mods/"
  cp "$ROOT"/atlas/build/libs/antique_atlas-neoforge-*.jar "$ROOT/testserver/mods/"
fi
(cd "$ROOT/core" && ./gradlew build --console=plain -q)
cp "$ROOT"/core/build/libs/expedition-*.jar "$ROOT/instance/mods/"
cp "$ROOT"/core/build/libs/expedition-*.jar "$ROOT/testserver/mods/"
echo "deployed to instance/mods and testserver/mods"
