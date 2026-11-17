# Slow Startup and Map Preview

## Status

Fixed

## Original Issue

Found in the 2026-11-17 UI responsiveness review. The window took about 1.6–1.8 s to appear (about 2.2 s on Windows), all of it on the UI thread. Opening Mundo froze the window for about 0.45 s, plus about 0.3 s on the first paint of a large map preview.

## Root Cause

- Startup did everything in series on the UI thread: Jackson set-up and reading the settings, theme and window class loading, an HTTP client (TLS) for the modpack and extension services that were not needed yet, and platform detection that read every server's jars twice, one server at a time.
- Each server row's status was an HTML label, so 30 rows meant starting Swing's HTML engine and parsing 30 snippets.
- The first bold font load on Windows happened while building the Home buttons.
- The map preview PNG (6144x6144) was decoded on the UI thread, and drawing it scaled made Java2D upload the whole 150 MB image to the graphics card on the first paint.

## Solution

- `PrecargaArranque`: background threads read the settings (warming Jackson), load the fonts and pre-load the heavy theme and window classes while the UI thread starts.
- `ExtensionHttpClient` and `ExtensionArtifactDownloader` build their `HttpClient` on first use.
- Server validation detects each server's platform once, in parallel.
- Server row status is plain text with a coloured dot icon.
- The map preview is read, decoded and converted to a fast-to-paint format in the background; only the latest request is applied. The panel paints the visible part into a panel-sized buffer and draws that, so the full image never goes to the screen pipeline.

## Files Changed

- `src/main/java/controlador/PrecargaArranque.java`
- `src/main/java/controlador/Main.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/extensions/ExtensionHttpClient.java`
- `src/main/java/controlador/extensions/ExtensionArtifactDownloader.java`
- `src/main/java/vista/PanelServidores.java`
- `src/main/java/vista/PanelMundo.java`
- `src/test/java/vista/ArranqueRapidoTest.java`

## Verification

- `ArranqueRapidoTest`: preview conversion keeps pixels and alpha, status dot icon.
- Manual (33 servers, 6144x6144 preview): first window 1.6–1.8 s down to 1.1 s; opening Mundo from a 444 ms + 288 ms freeze to about 0.1 s; the preview is pixel-identical.
