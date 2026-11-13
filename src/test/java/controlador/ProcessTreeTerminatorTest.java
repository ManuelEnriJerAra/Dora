package controlador;

import modelo.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ProcessTreeTerminatorTest {

    @TempDir
    Path tempDir;

    private final List<ProcessHandle> spawned = new ArrayList<>();

    @BeforeEach
    void requireUnixShell() {
        // Simulamos run.sh con "sh"; en Windows el equivalente (cmd.exe /c run.bat) se cubre igual
        // por ProcessHandle.descendants(), pero el test usa utilidades POSIX.
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
    }

    @AfterEach
    void cleanUp() {
        for (ProcessHandle handle : spawned) {
            handle.destroyForcibly();
        }
    }

    @Test
    void terminate_cierraElProcesoHijoAunqueElPadreSeaUnScript() throws Exception {
        Process wrapper = startWrapper("sleep 300 & wait");
        ProcessHandle child = awaitSingleChild(wrapper);

        boolean closed = ProcessTreeTerminator.terminate(wrapper, Duration.ofSeconds(3), Duration.ofSeconds(3));

        assertThat(closed).isTrue();
        assertThat(wrapper.isAlive()).isFalse();
        assertThat(child.isAlive()).isFalse();
    }

    @Test
    void terminate_fuerzaElCierreSiElArbolIgnoraLaSenalNormal() throws Exception {
        Process wrapper = startWrapper("trap '' TERM; sleep 300 & wait");
        ProcessHandle child = awaitSingleChild(wrapper);

        boolean closed = ProcessTreeTerminator.terminate(wrapper, Duration.ofMillis(300), Duration.ofSeconds(3));

        assertThat(closed).isTrue();
        assertThat(child.isAlive()).isFalse();
    }

    @Test
    void forzarPararServidor_noDejaVivoElServidorJavaLanzadoPorRunSh() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Server server = new Server();
        server.setDisplayName("Forge");
        Process wrapper = startWrapper("sleep 300 & wait");
        ProcessHandle child = awaitSingleChild(wrapper);
        server.setServerProcess(wrapper);

        gestor.forzarPararServidor(server);

        assertThat(child.isAlive()).isFalse();
        assertThat(server.getServerProcess()).isNull();
    }

    @Test
    void esperarParadaServidores_devuelveSoloLosQueSiguenVivosSinForzarlos() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Server lento = new Server();
        Process wrapperLento = startWrapper("sleep 300 & wait");
        ProcessHandle hijoLento = awaitSingleChild(wrapperLento);
        lento.setServerProcess(wrapperLento);
        Server rapido = new Server();
        Process wrapperRapido = startWrapper("exit 0");
        rapido.setServerProcess(wrapperRapido);

        List<Server> pendientes = gestor.esperarParadaServidores(List.of(lento, rapido), Duration.ofMillis(500));

        assertThat(pendientes).containsExactly(lento);
        assertThat(hijoLento.isAlive()).isTrue();

        gestor.forzarPararServidores(pendientes);
        assertThat(hijoLento.isAlive()).isFalse();
    }

    private Process startWrapper(String script) throws Exception {
        Process process = new ProcessBuilder("sh", "-c", script).start();
        spawned.add(process.toHandle());
        return process;
    }

    private ProcessHandle awaitSingleChild(Process wrapper) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<ProcessHandle> children = wrapper.toHandle().children().toList();
            if (!children.isEmpty()) {
                spawned.addAll(children);
                return children.getFirst();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("El proceso hijo no ha arrancado");
    }
}
