# Legacy Server Java Runtime Compatibility

## Status

Fixed (warning only)

## Original Issue

Dora lists and installs very old server versions (for example Paper `1.7.10` and `1.8.8`), but every start uses plain `java` from the user's PATH. On a Java 25-only system those servers fail or misbehave, and the user got no hint that Java was the cause. The same happened the other way round: a modern server on an old Java.

## Root Cause

`AbstractServerPlatformAdapter.buildStartProcess(...)`, the Forge/NeoForge/Quilt adapters and `run.bat`/`run.sh` all start the server with the system `java`, and nothing compared its version with what the Minecraft version needs.

## Solution

- New `controlador.platform.JavaRuntimeCompatibility` maps Minecraft versions to Java ranges and detects the PATH `java` version once per session (`java -version`):
  - up to 1.11: Java 8 only; 1.12 to 1.16: Java 8 to 16 (Forge up to 1.16: Java 8 only)
  - 1.17: Java 16+; 1.18 to 1.20.4: Java 17+; 1.20.5 to 1.21.x: Java 21+; 26.x: Java 25+
  - unknown formats (weekly snapshots, custom names) never warn.
- `GestorServidores.iniciarServidor` checks before starting. On a mismatch it writes a `[WARN]` line in the console. For manual starts it also asks "Iniciar igualmente / Cancelar", once per server per session. Automated starts only get the console warning, so schedules never block on a dialog.

Version availability is unchanged. Choosing or downloading a Java per server is still open as `docs/pending-features/server-managed-java-runtimes.md`.

## Files Changed

- `src/main/java/controlador/platform/JavaRuntimeCompatibility.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/platform/JavaRuntimeCompatibilityTest.java`
- `docs/pending-fixes/legacy-server-java-runtime-compatibility.md` (removed)

## Verification

- `mvn -q -Dtest=JavaRuntimeCompatibilityTest test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/legacy-server-java-runtime-compatibility.md`

## Regression Notes

The check must stay outside `inicioLock` because it can show a dialog. If a per-server Java executable is added later, compare against that executable instead of the PATH one.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
