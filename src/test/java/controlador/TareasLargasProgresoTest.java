package controlador;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TareasLargasProgresoTest {

    @TempDir
    Path tempDir;

    @Test
    void copiaDeMundo_informaDelAvanceHastaEl100PorCien() throws Exception {
        Path origen = crearMundo("world", 3, 1000);
        Files.createDirectories(tempDir.resolve("world_nether"));
        Files.write(tempDir.resolve("world_nether").resolve("n.dat"), new byte[500]);
        Path destino = tempDir.resolve("copia").resolve("world");
        Files.createDirectories(destino.getParent());
        List<Double> fracciones = new ArrayList<>();

        GestorMundos.copiarConDimensiones(origen, destino, progreso(fracciones, false));

        assertThat(destino.resolve("r2.mca")).exists();
        assertThat(tempDir.resolve("copia").resolve("world_nether").resolve("n.dat")).exists();
        assertThat(fracciones).isNotEmpty();
        assertThat(fracciones.get(fracciones.size() - 1)).isEqualTo(1d);
        assertThat(fracciones.stream().filter(f -> f >= 0).toList()).isSorted();
    }

    @Test
    void copiaDeMundo_cancelarParaLaCopiaConInterruptedIOException() throws Exception {
        Path origen = crearMundo("world", 5, 1000);
        Path destino = tempDir.resolve("copia").resolve("world");

        assertThatThrownBy(() -> GestorMundos.copiarConDimensiones(origen, destino, progreso(new ArrayList<>(), true)))
                .isInstanceOf(InterruptedIOException.class);
    }

    @Test
    void tamanoDeDirectorio_sumaSoloArchivos() throws Exception {
        Path origen = crearMundo("world", 4, 250);
        assertThat(FileSystemSafety.directorySize(origen)).isEqualTo(1000L);
        assertThat(FileSystemSafety.directorySize(tempDir.resolve("no-existe"))).isZero();
    }

    @Test
    void sinDialogo_laTareaSeEjecutaDirectamenteYDescargaNoFalla() throws IOException {
        String resultado = DialogoTareaEnCurso.ejecutarConProgreso("t", "d", true, 0, progreso -> {
            DialogoTareaEnCurso.listenerDescargaActual("server.jar").onProgress(10, 100);
            return "ok";
        });
        assertThat(resultado).isEqualTo("ok");
        assertThat(DialogoTareaEnCurso.progresoActual().cancelado()).isFalse();
    }

    @Test
    void textos_deDescargaYCopia() {
        assertThat(DialogoTareaEnCurso.textoDescarga("server.jar", 5L * 1024 * 1024, 50L * 1024 * 1024))
                .isEqualTo("Descargando server.jar: 5.0 de 50.0 MB");
        assertThat(DialogoTareaEnCurso.textoDescarga("server.jar", 1024 * 1024, -1)).isEqualTo("Descargando server.jar: 1.0 MB");
        assertThat(GestorMundos.textoCopia(300L * 1024 * 1024, 1200L * 1024 * 1024)).isEqualTo("300 de 1200 MB copiados");
    }

    private Path crearMundo(String nombre, int archivos, int bytes) throws IOException {
        Path dir = tempDir.resolve(nombre);
        Files.createDirectories(dir.resolve("region"));
        for (int i = 0; i < archivos; i++) {
            Files.write(dir.resolve("r" + i + ".mca"), new byte[bytes]);
        }
        return dir;
    }

    private static DialogoTareaEnCurso.Progreso progreso(List<Double> fracciones, boolean cancelado) {
        return new DialogoTareaEnCurso.Progreso() {
            @Override
            public void actualizar(double fraccion, String detalle) {
                fracciones.add(fraccion);
            }

            @Override
            public boolean cancelado() {
                return cancelado;
            }
        };
    }
}
