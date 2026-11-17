# Stats Charts Slow and Short

## Status

Fixed

## Original Issue

Found in the 2026-11-17 UI responsiveness review. With a server that has a long history, the Estadísticas page froze for 100–500 ms about once a second, hovering a chart lagged, the axis labels still said "-5m" after scrolling back in time, and the longest range was 1 hour even though weeks of history are kept.

## Root Cause

- Every refresh rebuilt the whole history (all recent 1 s samples plus the archive) into a list, four times per chart, just to cut out the visible window and to compute the scroll limits.
- The chart drew one point per sample (3,600 for 1 h).
- The window was measured in samples, not time, so archived (coarser) samples and scrolled windows got wrong time labels.

## Solution

- `StatsHistory.buildWindowSnapshot` reads only the samples inside the time window (a `TreeMap.subMap` of the archive and a reverse walk of the recent samples) and averages them into at most 720 points.
- Scroll limits and the window end come from the first and last timestamps, without building the timeline.
- New ranges: 6 h, 24 h, 7 días and 30 días.
- Axis labels are relative ("-10m", "-4h", "-5d") for the live window and real clock times or dates when scrolled back or when the server is stopped. Scrolling steps scale with the range.

## Files Changed

- `src/main/java/vista/PanelEstadisticas.java`
- `src/test/java/vista/GraficasVentanaTiempoTest.java`

## Verification

- `GraficasVentanaTiempoTest`: point cap, 1 point per second for 60 s, scrolled window end and limits, 30-day gaps, empty history, live detection.
- Manual with a 17 MB stats history: before, 1 freeze of 100–500 ms per second on the page; after, none while idle, hovering, switching to 30 días or scrolling back.
