# Console And Players Pipeline

Use this guide before editing live console rendering, command input, connected-player tracking, known users, whitelist/ban/op UI, or player debug controls.

## Console Pipeline

Core classes:

- `controlador.ServerLogReader`
- `modelo.Server`
- `vista.PanelConsola`

Flow:

1. Server process starts.
2. `ServerLogReader` reads stdout/stderr lines.
3. Lines are forwarded to server console listeners.
4. `PanelConsola.escribirLinea(...)` stores raw lines and appends styled output.
5. Simple view can translate/simplify noisy Minecraft log lines.

`PanelConsola` also tracks join/left/chat patterns to update connected player state.

Lines are queued and drained on the EDT in batches of up to 500. Each batch is inserted with one `insertString` per run of lines sharing a style, trimmed with a single `remove`, and scrolled once. Do not go back to per-line inserts or `setCaretPosition` per line: a 30,000-line burst took over 10 s of EDT time that way. The caret update policy is `NEVER_UPDATE`; `appendLineas` only scrolls to the bottom when the view was already at the bottom, so users can scroll up to read while the server logs. The text pane always tracks the viewport width so one long token (a path) cannot stop line wrapping.

## Command Input

Commands typed in `PanelConsola` are sent to the running server process through the server manager/runtime path.

Up/Down walk the last 100 sent commands (`PanelConsola.HistorialComandos`). Sending a command scrolls the console to the bottom. The "Vista simple" toggle lives at the right of the command row so it never covers log text.

Keep command sending disabled or harmless when no server process is alive.

## Player Panel

Main class: `vista.PanelJugadores`.

Responsibilities:

- Render connected players.
- Track real players from logs.
- Merge debug fake players while Debug mode is enabled.
- Open and edit lists such as whitelist, banned players, banned IPs, and OPs.
- Sync known users and remote Mojang profile data where available.

List counters and known-user synchronization are non-urgent page metadata. Keep those reads off the page-construction path when possible and apply results back on the EDT.

## List Files

Player management touches server list files such as:

- `whitelist.json`
- `ops.json`
- `banned-players.json`
- `banned-ips.json`

Be careful when editing JSON list structures. Preserve unrelated fields where possible.

## Known User Data

`GestorUsuariosConocidos` maintains known user identities. Use it instead of inventing separate username caches.

## Debug Players

`PanelJugadores` is the example debug implementation:

- debug plus/minus buttons appear in header actions
- fake players are in-memory only
- fake players clear when Debug mode is disabled

See `debug-mode-pipeline.md`.

## Tests

Relevant tests are lighter here. Always compile after UI changes. Add focused tests if moving parsing or file-writing behavior out of UI code.
