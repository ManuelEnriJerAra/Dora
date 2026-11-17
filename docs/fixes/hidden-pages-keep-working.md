# Hidden Pages Keep Working

## Status

Fixed

## Original Issue

Found in the 2026-11-17 UI responsiveness review. With a server writing a lot of log lines, Mundo, Configuración and Estadísticas froze for about 0.2 s every second (10 freezes in 10 s at 3,000 lines/s), while Home itself stayed smooth. After visiting Estadísticas once, a server with a long history also froze the window for about 0.1 s every few seconds on any other page.

## Root Cause

The right-hand pages live in a `CardLayout`. Switching page hides the old one but keeps it attached, so `addNotify`/`removeNotify` never run:

- `PanelConsola` kept inserting lines into its `JTextPane`. Each insert invalidated the hidden pane, and the next layout pass re-measured thousands of wrapped lines.
- `PanelEstadisticas` only stopped its 1 s refresh timer in `removeNotify`, so it kept rebuilding the timeline from the whole history while hidden.

## Solution

- New `PausaSiOculto` helper: a `HierarchyListener` on `SHOWING_CHANGED` that runs one callback when a component starts showing and another when it stops.
- `PanelConsola` keeps its line bookkeeping (raw lines, players) while hidden but parks rendered lines in a buffer instead of the document. On show it appends the buffer in one batch, or rebuilds the document once if more than 5,000 lines arrived.
- `PanelEstadisticas` starts its timer only while it is showing and stops it when hidden.

## Files Changed

- `src/main/java/vista/PausaSiOculto.java`
- `src/main/java/vista/PanelConsola.java`
- `src/main/java/vista/PanelEstadisticas.java`
- `src/test/java/vista/PausaPaginasOcultasTest.java`

## Verification

- `PausaPaginasOcultasTest`: hidden console buffers lines without touching the document, switches to a single rebuild past 5,000 lines; hidden stats panel does not start its timer.
- Manual, with the UI-freeze detector and a server writing 3,000 lines/s: 0 freezes over 10 s on Configuración and on Estadísticas (was 10 on Configuración). Returning to Home costs one catch-up of about 0.4 s and shows the latest lines.
