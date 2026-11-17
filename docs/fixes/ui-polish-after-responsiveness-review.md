# UI Polish After the Responsiveness Review

## Status

Fixed

## Original Issue

Small problems found during the 2026-11-17 UI responsiveness review and its Windows test runs:

- Card titles lost their accents ("Estadisticas", "Ultimas conexiones").
- The stats footer was cut off ("Ventana desplazada hasta 17/11/2026 16:37:2").
- The Home console had a white scrollbar on its dark background.
- The players panel still listed players after the server stopped, and while it was stopping.
- Notch's head (and other old 64x32 skins) showed as a black square.
- Closing the create-server wizard on its first step asked for confirmation.
- Configuración had a large empty gap under "Acceso y red".
- The Extensiones hint for Vanilla servers was cut off.
- "Superposicion" and "Graficas" were missing accents.

## Root Cause and Solution

- `CardTitleLabel` sized titles to the tight height of their glyphs and JLabel then clipped what stuck out above the font box. It now measures with accented capitals and draws the text at a baseline taken from the real glyph bounds.
- The footer drops the year from the date.
- The console scrollbar gets console-coloured track and thumb through `FlatLaf.style`, refreshed with the theme.
- `PanelJugadores` only shows players for a server that is running and not stopping.
- `MojangAPI.extraerCabezaDesdeSkin` skips a fully opaque hat layer on 64x32 skins, as Minecraft does.
- The wizard closes without asking on its first step.
- Config sections cap their maximum height at their preferred height.
- The Vanilla hint is shorter, with the full text as a tooltip.

## Files Changed

- `src/main/java/vista/CardTitleLabel.java`
- `src/main/java/vista/PanelEstadisticas.java`
- `src/main/java/vista/PanelConsola.java`
- `src/main/java/vista/PanelJugadores.java`
- `src/main/java/controlador/MojangAPI.java`
- `src/main/java/vista/ProcessWizardDialog.java`
- `src/main/java/vista/PanelConfigServidor.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/PanelMundo.java`
- `src/test/java/controlador/CabezaSkinAntiguaTest.java`
- `src/test/java/vista/JugadoresServidorParadoTest.java`

## Verification

- New tests for the skin rule and the players rule. Full suite 393 tests, 0 failures.
- Screenshots of Home, Mundo, Configuración, Extensiones and Estadísticas checked after the change.
