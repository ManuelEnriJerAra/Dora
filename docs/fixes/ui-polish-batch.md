# UI Polish Batch

## Status

Fixed

## Original Issue

Smaller problems from the 2026-11-13 UX review:

- White text on the green "Iniciar" button (1.35:1) and the yellow "Reiniciar" button (1.4:1) was hard to read.
- Without internet the home page said "(sin conexión)" and gave no address at all, not even for the local network.
- Importing a folder named "Survival amigos" created a server called "Servidor 1.21.4".
- Modpacks could only be imported from Extensiones on a server that already had the right loader; the Importar button only took server folders.
- Automatización used the CPU icon.
- The welcome screen (`NoServerFrame`) cut off the logo at the top and the LinkedIn and Reddit links at the bottom at its default 920x620 size.

## Solution

- `AppTheme.getReadableForegroundOn(...)` picks white when it reaches 3:1 (large bold text) and dark text otherwise; `applyLargeControlButtonStyle` and the control button icons use it. Green and yellow buttons now use dark text, red stays white.
- `controlador.IPLocal` finds the LAN IPv4 in the background, preferring physical adapters over VirtualBox, Docker, VPN and similar. Without a public IP the label shows `192.168.x.y:puerto (red local)` (or `localhost:puerto`) and copies it on click.
- Folder imports use the folder name (`GestorServidores.construirNombreDesdeCarpeta`), with " (1)", " (2)" for repeats. Generic folder names such as `server` or `minecraft` still give "Servidor <versión>".
- The Importar button opens a menu: "Carpeta de servidor..." and "Modpack (.mrpack o .zip)...". The modpack option installs into the selected server when it is a compatible mod server; otherwise it says which Minecraft and loader versions the pack needs and offers to create a server, then imports into it (`VentanaPrincipal.importarModpackDesdeLista`, `PanelExtensiones.importarModpackDesdeArchivo`).
- New `clock.svg` and `clock-unselected.svg` icons for Automatización.
- Welcome screen: smaller logo and gaps, links in a 2x2 grid. Fits at 920x620 and at the 760x540 minimum.

## Verification

- `PulidoInterfazTest`, `IPLocalTest`, updated `GestorServidoresTest` import naming tests.
- Checked in the app: control buttons, address label, Importar menu, modpack requirements dialog, welcome screen at both sizes.
