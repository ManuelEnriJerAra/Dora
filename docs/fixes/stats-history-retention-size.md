# Stats History Retention Size

## Status

Fixed

## Original Issue

`dora-stats.json` kept one sample per second for at least 30 days (options went up to 360 days) and was rewritten whole, pretty-printed, every 15 s. A 3-day history was 107 MB, held about 1 M sample objects in memory, and made Dora write 195 MB to disk every 30 s while that server ran. At the default it reaches about 1 GB per server.

## Root Cause

The "Ventana reciente" (samples kept at 1 s before being aggregated) defaulted to 30 days in `Server`, `MetadatosServidor` and `PanelEstadisticas`, while charts only show up to 1 hour.

## Solution

- `Server.VENTANA_RECIENTE_POR_DEFECTO_SEGUNDOS` (6 h) and `VENTANA_RECIENTE_MAXIMA_SEGUNDOS` (72 h). Options are 1 h to 3 days; older values are reset to the default when loaded (`MetadatosServidor`, `GestorConfiguracion`, `PanelEstadisticas.normalizarVentanaReciente`).
- Loading a history compacts samples older than the window into the 60 s archive (`StatsHistory.compactAll`), so existing large files shrink on the next save.
- The history is written as compact JSON every 60 s, through a temp file and an atomic move.

## Files Changed

- `src/main/java/modelo/Server.java`
- `src/main/java/controlador/MetadatosServidor.java`
- `src/main/java/controlador/GestorConfiguracion.java`
- `src/main/java/vista/PanelEstadisticas.java`
- `src/test/java/vista/PanelEstadisticasRetencionTest.java`

## Verification

- `mvn test`

## Detailed Process

- `docs/fixes/process/stats-history-retention-size.md`

## Regression Notes

The archive (60 s buckets and coarser) is still kept indefinitely; it grows by about 1,440 buckets per series per day of uptime. If files grow again, cap the archive age rather than raising the 1 s window.

## Related Docs

- `docs/pipelines/configuration-pipeline.md`
