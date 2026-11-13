# Missing Accents, Raw Offline Error And Preview Cache In World Exports

## Status

Fixed

## Original Issue

- Several user-facing labels were missing accents ("Ningun servidor activo", "Anadir", "Actualizacion disponible", "Despues de encendido", "Segun servidor", "Conversion completada"...) and one had a wrong one ("obligatoríos").
- Clicking "Crear servidor" and choosing Vanilla without internet showed a raw Java error ("java.net.ConnectException: Connection refused") instead of the friendly message the other platforms already use.
- Exporting a world copied Dora's map preview cache (`preview.png` and `dora-preview-overlay.properties`) into the exported folder.

## Root Cause

- The labels were typed without accents.
- `MojangAPI` wraps network failures in `RuntimeException`; `VanillaServerPlatformAdapter.listCreationOptions` and `install` let them through, so the dialog printed the wrapped exception text.
- `GestorMundos.exportarMundo` copies the whole world folder, and the preview cache lives inside it.

## Solution

- Fixed the accents in `VentanaPrincipal`, `PanelExtensiones`, `InstalledExtensionStatus`, `PanelJugadores`, `PanelAutomatizacion`, `ExtensionMarketplaceDialog`, `ExtensionHttpClient`, `ExtensionArtifactDownloader` and `GestorServidores`.
- The Vanilla adapter converts `MojangAPI` runtime failures into an `IOException` with `PlatformRemoteLookupPolicy`'s "Revisa la conexión" message.
- `GestorMundos.quitarArchivosDePrevisualizacion` removes the preview cache from the exported copy. `dora-world-config.properties` is kept so the world keeps its Dora settings when imported again.

## Files Changed

- `src/main/java/controlador/platform/VanillaServerPlatformAdapter.java`
- `src/main/java/controlador/platform/PlatformRemoteLookupPolicy.java`
- `src/main/java/controlador/GestorMundos.java`
- Label files listed above
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `src/test/java/controlador/GestorMundosTest.java`

## Verification

- `mvn -q -Dtest=ServerPlatformAdaptersTest,GestorMundosTest test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/ui-polish-accents-offline-error-world-export.md`

## Regression Notes

Any adapter that calls `MojangAPI` must convert its `RuntimeException` into an `IOException` with a user-facing message.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
