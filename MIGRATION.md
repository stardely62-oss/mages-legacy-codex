# NeoForge 1.21.1 port

Target: Minecraft 1.21.1, NeoForge 21.1.265+, Java 21, ModDevGradle 2.0.148.
The original repositories and Claude working trees are not part of this port.

Production code uses Mojang mappings, native NeoForge registries/events and
negotiated payload codecs. Item-bound data uses data components. SavedData and
block entities receive registry lookups. Data-pack directories use 1.21 names.

CI builds only on GitHub-hosted runners. The native GameTest server and actual
client under Xvfb must succeed before the `neoforge-dev` release is published.
The harness loads the same pinned NeoForge JourneyMap as the pack installer.
Test sources and JourneyMap compile stubs are excluded from shipped jars.

The previous Fabric `dev` releases and pack `modpack.json` remain unchanged.
Native pack metadata is `modpack-neoforge-1.21.1.json`. Installation is confined
to `%APPDATA%\.minecraft-codex-neoforge-1.21.1` with link-directory rejection.
See CODEX.md for launcher setup.
