/*
 * Fichero: ArchivoListaServidores.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción:
 * Lee y guarda la lista de servidores en dora-server-list.json. Si el archivo no se puede leer, deja una copia
 * de seguridad antes de que el siguiente guardado lo sobrescriba y prepara un aviso para el usuario.
 * */

package controlador;

import modelo.Server;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ArchivoListaServidores {
    static final String NOMBRE_ARCHIVO = "dora-server-list.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final File archivo;
    private String avisoArchivoIlegible;

    ArchivoListaServidores(File archivo) {
        this.archivo = archivo == null ? rutaPorDefecto() : archivo;
    }

    private static File rutaPorDefecto() {
        return AppPaths.configDirectory().resolve(NOMBRE_ARCHIVO).toFile();
    }

    // Aviso para el usuario si la última carga encontró el archivo ilegible; null si se leyó bien.
    String getAvisoArchivoIlegible() {
        return avisoArchivoIlegible;
    }

    // cargamos todos los servidores del JSON
    List<Server> cargar(){
        File file = archivo;
        if (!file.exists()) {
            List<Server> servidores = new ArrayList<>();
            try{
                File parent = file.getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                Utilidades.escribirArchivoAtomico(
                        file.toPath(),
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(servidores)
                );
            } catch (Exception e) {
                System.err.println("Error al inicializar servidores: " + e.getMessage());
            }
            return servidores;
        }
        try{
            List<Server> servidores = mapper.readValue(file, new TypeReference<>(){});
            return servidores == null ? new ArrayList<>() : new ArrayList<>(servidores);
        } catch (JacksonException e) {
            System.err.println("Error al cargar servidores: " + e.getMessage());
            // El siguiente guardado sobrescribiría el archivo ilegible, así que antes dejamos una copia recuperable.
            Path copia = Utilidades.respaldarArchivoIlegible(file.toPath());
            avisoArchivoIlegible = "No se ha podido leer " + NOMBRE_ARCHIVO + " porque su contenido no es válido."
                    + "\nDora empezará con la lista de servidores vacía."
                    + (copia == null
                    ? "\nNo se ha podido crear una copia de seguridad del archivo."
                    : "\nSe ha guardado una copia del archivo original en:\n" + copia);
            AppErrorReporter.report("No se ha podido leer " + NOMBRE_ARCHIVO + ".", e);
            return new ArrayList<>();
        }
    }

    // Guarda la lista de servidores en el JSON
    void guardar(List<Server> servidores) {
        try{
            File parent = archivo.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Utilidades.escribirArchivoAtomico(
                    archivo.toPath(),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(servidores)
            );
        } catch (Exception e) {
            System.err.println("Error al guardar servidores: " + e.getMessage());
            AppErrorReporter.report("No se ha podido guardar " + NOMBRE_ARCHIVO + ".", e);
        }
    }
}
