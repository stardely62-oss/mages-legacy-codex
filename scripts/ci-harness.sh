#!/usr/bin/env bash
# Runs the scripted client scenario (CI, Linux with Xvfb): generate a world with the dedicated
# server, copy it into the client's saves, play the scenario, then demand a PASS and a fresh screenshot.
set -euo pipefail
cd "$(dirname "$0")/.."

SERVER=build/harness-server
CLIENT=build/harness-client

rm -rf "$SERVER" "$CLIENT/saves/harness" "$CLIENT/screenshots" "$CLIENT/harness-result.txt"
mkdir -p "$SERVER" "$CLIENT/saves"

echo 'eula=true' > "$SERVER/eula.txt"
cat > "$SERVER/server.properties" <<'EOF'
level-name=world
level-type=minecraft\:normal
level-seed=20261025
generate-structures=true
online-mode=false
spawn-protection=0
spawn-monsters=false
gamemode=survival
spawn-animals=false
EOF

timeout 300 ./gradlew runGenworld --stacktrace
test -f "$SERVER/world/level.dat" || { echo 'genworld did not create a world'; exit 1; }
cp -r "$SERVER/world" "$CLIENT/saves/harness"

# JourneyMap, as players have it: our keys must not clash with it out of the box
JM_URL=https://cdn.modrinth.com/data/lfHFW1mp/versions/CtFE7hwT/journeymap-neoforge-1.21.1-6.0.9.jar
JM_SHA512=8900ee4cdb7d1ffb6559decdeec84ad479d04b2e7b01a60be9f6e328809af2bfd7a80077b7da0e49bc96261fe8fe274ba33c62f6c9f65302c783ed823c4ff746
mkdir -p "$CLIENT/mods"
rm -f "$CLIENT"/mods/journeymap-*.jar
curl -sSfL -o "$CLIENT/mods/journeymap-neoforge-1.21.1-6.0.9.jar" "$JM_URL"
echo "$JM_SHA512  $CLIENT/mods/journeymap-neoforge-1.21.1-6.0.9.jar" | sha512sum -c -

# Skip first-launch screens and keep running without window focus. The third ability sits on G, as it did before
# JourneyMap's G was known: the mod has to move it back to its free default by itself.
# (version: 1.20.1's data version, or the options datafixer chokes on modern key names and drops the whole file)
cat > "$CLIENT/options.txt" <<'EOF'
version:3465
key_key.mages_legacy.ability_3:key.keyboard.g
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
pauseOnLostFocus:false
soundCategory_master:0.0
lang:ru_ru
renderDistance:8
guiScale:2
EOF

timeout 600 xvfb-run -a -s '-screen 0 1280x720x24' ./gradlew runHarness --stacktrace || true

echo '--- harness result ---'
cat "$CLIENT/harness-result.txt" 2>/dev/null || echo '(no result file: the client crashed or never reached the world)'
for shot in lore choose_mycelid choose_voidborn hud talents spore_jump spore_cloud node node_arrived; do
  test -s "$CLIENT/screenshots/$shot.png" || { echo "screenshot $shot.png was not written"; exit 1; }
done
grep -qx 'RESULT PASS' "$CLIENT/harness-result.txt"
