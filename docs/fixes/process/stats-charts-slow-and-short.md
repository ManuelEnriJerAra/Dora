# Stats Charts Slow and Short Process

## Status

Completed

## Linked Fix

- `docs/fixes/stats-charts-slow-and-short.md`

## Scope

Batch 3 of the 2026-11-17 UI responsiveness review: fast charts and longer ranges.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Time windows | Snapshot by time with at most 720 points; no full-timeline rebuild. |
| DONE | 2. Ranges | Added 6 h, 24 h, 7 días, 30 días. |
| DONE | 3. Labels | Relative when live, clock time or date when scrolled or stopped. |
| DONE | 4. Tests and manual check | `GraficasVentanaTiempoTest`; freeze detector with a 17 MB history. |
