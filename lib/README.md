# Bundled private dependencies

These artifacts are kept in `lib/repository/`, a file-based Maven repository declared in `pom.xml` as `mypet-local-libraries`, so the private `de.keyle` coordinates resolve locally without any remote Maven repository.

| File | Coordinate | Use | Packaging |
|---|---|---|---|
| `repository/de/keyle/knbt/0.0.6/knbt-0.0.6.jar` | `de.keyle:knbt:0.0.6` | NBT persistence and item data | Included and relocated to `de.Keyle.MyPet.util.nbt` in the plugin JAR |
| `repository/de/keyle/mypet-premium-apis/1.0-SNAPSHOT/mypet-premium-apis-1.0-SNAPSHOT.jar` | `de.keyle:mypet-premium-apis:1.0-SNAPSHOT` | Compile-time API types for optional premium-plugin hooks | `provided`; excluded from the plugin JAR |

To update an artifact, replace the JAR in its versioned directory (or add a new version directory with a matching minimal POM) and update the version in `pom.xml`.

The KNBT license is included at `src/main/resources/licences/KNBT.txt` and in the plugin resources. Keep it when updating KNBT. The premium API JAR contains compile-time stubs; the corresponding premium plugins provide their runtime implementations.
