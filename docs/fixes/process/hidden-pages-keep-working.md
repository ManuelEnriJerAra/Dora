# Hidden Pages Keep Working Process

## Status

Completed

## Linked Fix

- `docs/fixes/hidden-pages-keep-working.md`

## Scope

Batch 1 of the 2026-11-17 UI responsiveness review: stop hidden pages from using the UI thread.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Visibility helper | Added `PausaSiOculto` (`SHOWING_CHANGED` listener). |
| DONE | 2. Console | Buffer rendered lines while hidden; append or rebuild once on show. |
| DONE | 3. Stats | Run the refresh timer only while showing. |
| DONE | 4. Tests and manual check | Added `PausaPaginasOcultasTest`; checked with the freeze detector and a flooding stand-in server. |
