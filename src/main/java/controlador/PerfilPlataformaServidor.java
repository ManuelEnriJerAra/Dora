/*
 * Fichero: PerfilPlataformaServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Detecta la plataforma de la carpeta de un servidor y copia el perfil detectado (plataforma, loader, versión...)
 * al modelo del servidor.
 * */

package controlador;

import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import modelo.Server;

import java.nio.file.Path;
import java.util.regex.Pattern;

final class PerfilPlataformaServidor {
    private static final Pattern MINECRAFT_VERSION_PATTERN = Pattern.compile("(?i)(?:1\\.(?:0|[1-9][0-9]*)(?:\\.\\d+)?(?:-(?:snapshot|pre|rc)-?\\d+)?|[2-9]\\d*\\.\\d+(?:\\.\\d+)?(?:-(?:snapshot|pre|rc)-?\\d+)?|\\d{2}w\\d{2}[a-z]|[ab]\\d+\\.\\d+(?:\\.\\d+)?)");

    private PerfilPlataformaServidor() {
    }

    static ServerPlatformProfile detectar(Path serverDir) {
        return ServerPlatformAdapters.detect(serverDir);
    }

    static void aplicar(Server server, ServerPlatformProfile profile) {
        if (server == null || profile == null) return;
        server.setPlatform(profile.platform());
        server.setLoader(profile.loader());
        server.setLoaderVersion(profile.loaderVersion());
        server.setEcosystemType(profile.ecosystemType());
        server.setCapabilities(profile.capabilities());
        if (profile.minecraftVersion() != null
                && MINECRAFT_VERSION_PATTERN.matcher(profile.minecraftVersion().trim()).matches()) {
            server.setVersion(profile.minecraftVersion());
        }
    }
}
