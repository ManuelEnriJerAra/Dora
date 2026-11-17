# Background Loading And Timers

## Status

Fixed

## Original Issue

The 2026-11-13 UX review found work on the Swing event thread that grows with world size, log size or server count:

- Opening Mundo walked the whole world folder on the EDT to show "Peso mundo" (`WorldStorageAnalyzer.analyze`). On a large world or a slow disk the window froze.
- The address label on the home page re-read `server.properties` from disk every 500 ms.
- The server list rebuilt every row's HTML status label every second, even when nothing changed.
- Switching servers took about 230 ms with a 5,000-line console, most of it in the join/leave/chat regular expressions.

## Root Cause

Synchronous file walks and polling timers on the EDT, and regular expressions that try every position of every log line.

## Solution

- `PanelMundo.actualizarDatosAlmacenamiento` runs the size walk on a single daemon thread, shows "Calculando..." meanwhile and drops results for a world that is no longer selected (`almacenamientoSequence`).
- `PanelPrevisualizacion`: Dora's own changes arrive through the `configuracionServidor` event; the timer now runs every 3 s and only re-reads the file when its path or modification time changed (`PuertoPropertiesCache`).
- `VentanaPrincipal` refreshes the server list on `estadoServidor`; the timer is a 5 s fallback. `PanelServidores.actualizarEstadoLabel` only calls `setText` when the text changes. A failed start now also fires `estadoServidor`.
- `PanelConsola` checks `contains(" joined the game")`, `contains(" left the game")` and `'<'` before running the regular expressions.

## Verification

- `PanelPrevisualizacionPuertoTest`.
- EDT watchdog: switching to a server with 5,000 console lines went from about 230 ms to about 110 ms, the same as switching to an empty one.
