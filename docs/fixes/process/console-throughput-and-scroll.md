# Console Throughput And Scroll Process

## Status

Completed

## Linked Fix

- `docs/fixes/console-throughput-and-scroll.md`

## Scope

Console problems found in the 2026-11-13 UX review.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Batch inserts | `appendLineas` groups lines by style, trims once and scrolls once per drain. |
| DONE | 2. Smart auto-scroll | `NEVER_UPDATE` caret plus `debeSeguirAlFinal`. |
| DONE | 3. Command history | `HistorialComandos` bound to Up/Down. |
| DONE | 4. Layout | Toggle moved to the command row; scrollbar always shown; pane always tracks viewport width. |
| DONE | 5. Tests and manual check | `PanelConsolaTest`; flood, scroll and history checked in the app. |
