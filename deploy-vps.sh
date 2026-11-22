#!/bin/sh
# Pushes the server side of the pack (testserver/: mods, config, kubejs, libraries) to the VPS and restarts it.
# Worlds, server.properties, logs and the pregen's progress on the VPS are left alone.
#   ./deploy-vps.sh            sync and restart
#   ./deploy-vps.sh norestart  only sync (applied on the next restart)
set -e
HOST=root@212.22.85.153
SSH="ssh -i $HOME/.ssh/expedition_vps -o IdentitiesOnly=yes"
cd "$(dirname "$0")/testserver"
# Chunky's saved task (the pregen's progress, see pregen.sh) is the VPS's own: --delete leaves excluded files alone.
rsync -az --delete --exclude config/chunky/tasks -e "$SSH" config defaultconfigs kubejs libraries mods tools run.sh user_jvm_args.txt pregen.sh $HOST:/opt/expedition/
$SSH $HOST "chown -R minecraft:minecraft /opt/expedition"
[ "$1" = norestart ] || $SSH $HOST "systemctl restart expedition && echo restarted"
