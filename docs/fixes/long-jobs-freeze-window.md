# Long Jobs Freeze the Window

## Status

Fixed

## Original Issue

Found in the 2026-11-17 UI responsiveness review. Some long jobs ran on the UI thread or gave no feedback:

- Importing or exporting a world copied the whole folder on the UI thread. A 750 MB world froze the window until the copy finished, with no progress and no way to cancel.
- Adding a player to the whitelist, ops or bans of a stopped server asked Mojang for the UUID on the UI thread. With a slow or missing connection the window hung, and offline-mode servers needed a lookup they never use.
- Importing a server folder scanned its jars on the UI thread.
- The shared "task in progress" dialog only showed an endless bar, even for downloads that know their size.

## Root Cause

- `GestorMundos` called `FileSystemSafety.copyDirectoryTree` directly from the button handler.
- `PanelJugadores.resolverUuidJugador` called `MojangAPI.obtenerUuidJugador` directly, with no offline-mode path.
- `GestorServidores.importarServidorDesdeDirectorio` ran platform detection before any background step.
- `DialogoTareaEnCurso` had no way to report progress, and the downloaders were given a `null` progress listener.

## Solution

- `DialogoTareaEnCurso.ejecutarConProgreso` runs a task in the background behind a modal dialog with a real progress bar, a detail line, an optional Cancelar button, and a short delay so quick tasks never flash a dialog.
- World import and export copy in the background, report "X de Y MB copiados" with a percentage (world, nether and end together), and can be cancelled. On error or cancel, folders they created are removed.
- Downloads (server jars, platform conversion) report "Descargando archivo: X de Y MB".
- Players on an offline-mode server get their offline UUID without asking Mojang. Online-mode lookups run in the background with a clear error if the name is not found.
- Server import analyses the folder in the background; saving stays on the UI thread.

## Files Changed

- `src/main/java/controlador/DialogoTareaEnCurso.java`
- `src/main/java/controlador/FileSystemSafety.java`
- `src/main/java/controlador/GestorMundos.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/ConversorPlataformaServidor.java`
- `src/main/java/vista/PanelJugadores.java`
- `src/test/java/controlador/TareasLargasProgresoTest.java`
- `src/test/java/vista/PanelJugadoresUuidTest.java`

## Verification

- `TareasLargasProgresoTest`: copy progress reaches 100 %, cancel stops the copy, folder size, headless run, progress texts.
- `PanelJugadoresUuidTest`: offline UUID matches the vanilla one; `online-mode` is read from `server.properties`.
- Manual, exporting a 750 MB world: the dialog shows "667 de 749 MB copiados" with the bar moving and Cancelar, and the window keeps repainting behind it.
