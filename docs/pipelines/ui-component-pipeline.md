# UI Component Pipeline

The UI is Swing with FlatLaf. Most custom UI helpers are in `src/main/java/vista`.

## Theme Helpers

Use `AppTheme` for:

- foreground/background colors
- muted foreground
- rounded borders
- action button styles
- header icon button styles
- configured header/debug icon buttons
- fixed-size row/action icon buttons
- large server control button styling
- refresh icon behavior
- scroll speed/style helpers where present

Avoid hard-coded colors unless the surrounding code already uses them or the value is clearly temporary/local.

## Icons

Use SVG assets from `src/main/resources/doraicons` through `SvgIconFactory`.

Prefer existing icon assets:

- `plus.svg`, `minus.svg`
- `reset.svg`, `refresh.svg`
- `rocket.svg` for final execute/create/import actions in wizard flows
- navigation arrows
- user/shield/folder/shop/settings icons

Set button tooltips, especially for icon-only controls.

## Cards And Layout

Common components:

- `CardPanel`: titled cards, header actions, footer actions, content panel.
- `BoxCategory`: compact boolean, field, and info rows.
- `ProcessWizardDialog`: reusable shell for multi-step processes with left/right arrow navigation, per-step validation, and final action handling. The last right-navigation button must switch to `rocket.svg` inside the wizard shell.
- `WizardDialog`: deprecated compatibility facade for old compiled references. Do not use it for new process flows.
- `TextEllipsizer`: shared pixel-width right and left ellipsis helpers for labels, buttons, rows, and path editors.
- `WrapLayout`: wrapping action/player layouts.
- `RoundedBackgroundPanel`: rounded surfaces.

Preserve established layouts. For narrow fixes, adjust sizing/layout inside the affected component instead of redesigning surrounding panels.

## Swing Threading

Use the EDT for UI mutations.

Common patterns:

- `SwingUtilities.invokeLater(...)` from listeners/background callbacks.
- `SwingWorker` for background work with UI updates in `process(...)` or `done()`.

Remove long-lived listeners when components become undisplayable.

Prefer `GestorServidores` property change events (`estadoServidor`, `configuracionServidor`) over polling timers. When a timer is still needed as a fallback, keep it slow (seconds), make it cheap (compare a modification time or the current text before doing work) and avoid reading files on the EDT.

## Text And Localization

User-facing text is mostly Spanish. Keep new strings Spanish unless adding internal diagnostics or comments. Standard Swing dialog texts come from `vista.TextosSwingEspanol`.

Preserve accents where the file already uses UTF-8 correctly. Some older text may appear mojibaked; avoid broad encoding churn unless explicitly fixing that file.
