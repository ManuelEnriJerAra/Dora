# Legacy Server Java Runtime Compatibility Process

## Status

Fixed (warning only)

## Linked Fix

- `docs/fixes/legacy-server-java-runtime-compatibility.md`

## Scope

Warn before starting a server whose Minecraft version does not match the system Java. Per-server Java selection stays in `docs/pending-features/server-managed-java-runtimes.md`.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Read the pending fix, the managed-runtimes feature request, and every place that launches `java`. |
| DONE | 2. Map versions to Java | Added `JavaRuntimeCompatibility.requirementFor` with a Forge-specific rule for old versions. |
| DONE | 3. Detect system Java | `detectPathJavaMajor` runs `java -version` once per session and parses both `1.8.0_x` and `21.0.x` formats. |
| DONE | 4. Warn before start | `confirmarCompatibilidadJava` in `iniciarServidor`: console warning always, dialog for manual starts once per server per session. |
| DONE | 5. Verify behavior | Added `JavaRuntimeCompatibilityTest`. |
| TO DO | 6. Per-server Java executable | Out of scope here; tracked as the managed runtimes feature. |

## Verification Notes

- `mvn -q -Dtest=JavaRuntimeCompatibilityTest test`
- `mvn test` on Java 25.

The dialog was not exercised in a desktop session in this change. Manual check: start a Paper 1.8.8 server with only Java 25 installed and confirm the warning appears; start a 1.21 server and confirm it does not.
