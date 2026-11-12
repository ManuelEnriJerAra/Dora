# Missing Accents, Raw Offline Error And Preview Cache In World Exports Process

## Status

Fixed

## Linked Fix

- `docs/fixes/ui-polish-accents-offline-error-world-export.md`

## Scope

Three small user-facing issues found in review.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Find unaccented labels | Searched string literals for common Spanish words written without accents and fixed the user-facing ones. |
| DONE | 2. Reproduce the offline error | Ran every creatable adapter's `listCreationOptions` with the network blocked: only Vanilla returned a raw `RuntimeException`. |
| DONE | 3. Friendly offline error | Wrapped `MojangAPI` failures in `VanillaServerPlatformAdapter`. |
| DONE | 4. Clean world exports | Added `GestorMundos.quitarArchivosDePrevisualizacion` after the export copy. |
| DONE | 5. Verify behavior | Added tests in `ServerPlatformAdaptersTest` and `GestorMundosTest`. |

## Verification Notes

- `mvn -q -Dtest=ServerPlatformAdaptersTest,GestorMundosTest test`
- `mvn test` on Java 25.
