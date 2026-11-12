# Modpack Replace Loses Mods On Failure Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-modpack-replace-loses-mods-on-failure.md`

## Scope

Importing a modpack with "replace existing" deleted every managed mod jar and only then started downloading. A network error, a CurseForge API failure or a hash mismatch partway through left the server with a partial modpack and the original mods gone.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the extensions pipeline and the earlier existing-mods import fix. |
| DONE | 2. Move instead of delete | Managed jars go to a temporary backup folder under the server directory. |
| DONE | 3. Roll back on failure | Partial installs are removed and previous jars and metadata restored. |
| DONE | 4. Clean up on success | Backup folder removed after the last install; same warnings as before. |
| DONE | 5. Verify behavior | Added a failing-download rollback test; existing replace tests still pass. |

## Implementation Notes

The jars are now moved to a temporary `.dora-backup/mods-*` folder inside the server directory (`apartarJarsGestionadosParaReemplazoModpack`). If any install fails, `restaurarModsTrasFalloDeModpack` deletes the partially installed jars, moves the previous jars back, restores the previous extension metadata and rethrows with "no se ha cambiado nada". The backup is deleted only after every entry installed (`confirmarReemplazoModpack`), so a successful import leaves no extra folder. Applies to both Modrinth and CurseForge imports.

## Verification Notes

- `mvn -q "-Dtest=UtilidadesTest,GestorServidoresTest" test`
- `mvn test`

Run in a Java 21 environment with a stubbed NBT dependency because Java 25 and JitPack were not reachable there: 310 tests passed; the 19 world/MCA tests that need the real NBT library could not run. Re-run `mvn test` on Java 25 before release.
