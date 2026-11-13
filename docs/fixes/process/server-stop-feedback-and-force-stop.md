# Server Stop Feedback And Force Stop Process

## Status

Completed

## Linked Fix

- `docs/fixes/server-stop-feedback-and-force-stop.md`

## Scope

Show a stopping state and expose the existing force stop, found in the 2026-11-13 UX review.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Track the stopping state | Added `Server.deteniendo`/`paradaSolicitadaMillis`, set in `mandarComando`, cleared on exit and force stop. |
| DONE | 2. Stopping controls | Added the "Deteniendo..." panel and the delayed "Forzar cierre" button with confirmation, run off the EDT. |
| DONE | 3. Server list state | Added "Deteniendo" to `PanelServidores.actualizarEstadoLabel`. |
| DONE | 4. Tests and manual check | Added `ParadaServidorEstadoTest`; checked the flow with a slow-stopping stand-in server. |
