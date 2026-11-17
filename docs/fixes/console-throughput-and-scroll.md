# Console Throughput And Scroll

## Status

Fixed

## Original Issue

On a 30,000-line burst the console used more than 10 s of EDT time in ~120 ms chunks and was still 9,000 lines behind 12 s later. It always jumped to the bottom, so you could not scroll up to read while the server logged. The "Vista Simple" toggle floated over the log and hid the end of long lines, Up/Down did nothing in the command field, and one long token (such as a path) in the log stopped line wrapping for the whole console.

## Root Cause

`PanelConsola` inserted each line separately, removed overflow lines one by one, and called `setCaretPosition` after every line. The text pane used the default `getScrollableTracksViewportWidth`, which turns off wrapping once any token is wider than the view.

## Solution

- Drain up to 500 lines per EDT event, insert them with one `insertString` per style run, trim with one `remove` and scroll once (`PanelConsola.appendLineas`).
- Caret update policy `NEVER_UPDATE`; auto-scroll only when the view was already at the bottom (`debeSeguirAlFinal`). Sending a command still scrolls down.
- Command history with Up/Down (`HistorialComandos`, last 100 commands).
- "Vista simple" moved next to the command field; vertical scrollbar always visible.
- The console text pane always tracks the viewport width.

## Files Changed

- `src/main/java/vista/PanelConsola.java`
- `src/test/java/vista/PanelConsolaTest.java`

## Verification

- `mvn test`
- Manual: 30,000-line burst used 1.9 s of EDT time in total (was 10.8 s); scrolled-up view stayed in place while new lines arrived; Up recalled the last command; long lines wrap.

## Detailed Process

- `docs/fixes/process/console-throughput-and-scroll.md`

## Regression Notes

Keep all document changes batched per drain. Anything that reads `rawLineas` to rebuild the document should go through `appendLineas`.

## Related Docs

- `docs/pipelines/console-and-players-pipeline.md`
