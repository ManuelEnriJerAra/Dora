# Stats History Retention Size Process

## Status

Completed

## Linked Fix

- `docs/fixes/stats-history-retention-size.md`

## Scope

Bound the per-second stats history, found in the 2026-11-13 UX review.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. New window limits | Added the default/max constants and the 1 h to 3 day options, shown in hours or days. |
| DONE | 2. Migrate old settings | Values above the max reset to the default in server metadata, global config and the stats panel. |
| DONE | 3. Shrink old files | `compactAll` runs after loading a history. |
| DONE | 4. Cheaper writes | Compact JSON, 60 s flush, temp file plus atomic move. |
| DONE | 5. Tests | `PanelEstadisticasRetencionTest`. |
