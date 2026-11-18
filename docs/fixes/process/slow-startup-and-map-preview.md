# Slow Startup and Map Preview Process

## Status

Completed

## Linked Fix

- `docs/fixes/slow-startup-and-map-preview.md`

## Scope

Batch 4 of the 2026-11-17 UI responsiveness review: faster startup and map preview.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Profile | JFR on startup with full stacks to split the 1.5 s of UI-thread work. |
| DONE | 2. Startup | Background pre-load, lazy HTTP clients, single parallel platform detection, plain status labels. |
| DONE | 3. Map preview | Background decode and conversion; panel-sized paint buffer. |
| DONE | 4. Tests and manual check | `ArranqueRapidoTest`; freeze detector and startup timing on 33 servers. |
