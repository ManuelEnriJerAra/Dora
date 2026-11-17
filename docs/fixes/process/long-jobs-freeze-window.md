# Long Jobs Freeze the Window Process

## Status

Completed

## Linked Fix

- `docs/fixes/long-jobs-freeze-window.md`

## Scope

Batch 2 of the 2026-11-17 UI responsiveness review: move long jobs off the UI thread and show real progress.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Progress dialog | `ejecutarConProgreso` with percentage, detail, cancel and show delay. |
| DONE | 2. World copy | Import and export in the background with MB progress and cancel. |
| DONE | 3. Downloads | Pass a real progress listener to the downloaders. |
| DONE | 4. Player UUID | Offline UUID for offline-mode servers; online lookup in the background. |
| DONE | 5. Server import | Folder analysis in the background. |
| DONE | 6. Tests and manual check | Two new test classes; 750 MB export checked in the real app. |
