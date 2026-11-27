# Bundled build target

This directory holds `lib/repository/`, a file-based Maven repository declared in `pom.xml` as `mypet-local-libraries`. It is the only source of the Spigot snapshot used for compilation:

| File | Coordinate | Use | Packaging |
|---|---|---|---|
| `repository/org/spigotmc/spigot/26.3-R0.1-SNAPSHOT/` | `org.spigotmc:spigot:26.3-R0.1-SNAPSHOT` (+ `spigot-parent:dev-SNAPSHOT`, `spigot-api:26.3-R0.1-SNAPSHOT`) | NMS compile target for the v26_3_R1 compat module | `provided`; excluded from the plugin JAR |

The artifact is not published to any configured remote repository, so the build needs this directory. To update it, replace the JARs in their versioned directories (with matching minimal POMs) and update the version in `pom.xml`.

## Vendored sources

Two dependencies that used to live here are now part of the project sources and no longer resolved from `lib/`:

- **KNBT** (`de.keyle:knbt:0.0.6`) — sources in `src/main/java/de/Keyle/MyPet/util/nbt/`, using the same package the shaded JAR always used. The KNBT license is kept at `src/main/resources/licences/KNBT.txt`.
- **MyPet premium APIs** (`de.keyle:mypet-premium-apis:1.0-SNAPSHOT`) — compile-time stubs for the BossShopPro, FabledSkyBlock and GangsPlus hooks, in `src/main/java/de/Keyle/MyPet/lib/`. At build time they are shaded back to the real plugin packages and excluded from the JAR, so the hooks resolve against the installed premium plugins at runtime.
