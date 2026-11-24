PvP Bot (Fabric, Minecraft 1.21.11)

COMMANDS
  /pvpbot spawn <mace|elytra_mace|spear_mace> <level 1-10> [kit]
  /pvpbot clear
  /pvpbot reload

CUSTOMIZE
  First launch creates config/pvpbot.json. Edit kits and levels, then /pvpbot reload.
  Item spec format: "minecraft:mace|minecraft:density=5,minecraft:breach=4"
  Add your own kit names and pass them as the last argument of /pvpbot spawn.

BUILD ON GITHUB (no setup needed)
  1. Create a new GitHub repo and upload everything from this zip (keep the .github folder).
  2. Open the Actions tab. The "Build mod" workflow runs on every push.
  3. Open the finished run and download the "pvpbot-jar" artifact. Unzip it to get the mod jar.
  Optional: push a tag like v1.0.0 and the jar is attached to a GitHub Release.

BUILD LOCALLY
  Easiest: generate a blank Fabric 1.21.11 project at https://fabricmc.net/develop/template,
  then copy src/ from this zip over it and run: ./gradlew build
  Jar appears in build/libs/ (use the one without -sources).

INSTALL ON DAWN
  Put the jar (and Fabric API) in Dawn's mods folder. Dawn is Fabric-based on 1.21.11.
