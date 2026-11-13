# Background Loading And Timers Process

## Status

Completed

## Linked Fix

- `docs/fixes/background-loading-and-timers.md`

## Scope

Batch 4 of the 2026-11-13 UX review.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. World size off the EDT | Single daemon executor plus sequence check in `PanelMundo`. |
| DONE | 2. Port label | Event driven, 3 s modification-time check. |
| DONE | 3. Server list status | `estadoServidor` listener, 5 s fallback, no-op when unchanged. |
| DONE | 4. Console switch | Cheap `contains` checks before the regular expressions. |
| DONE | 5. Tests and manual check | `PanelPrevisualizacionPuertoTest`; EDT watchdog timings. |
