package vista;

import modelo.Server;
import modelo.ServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PanelEstadisticasSamplingTest {

    @TempDir
    Path tempDir;

    private Process wrapper;

    @AfterEach
    void cleanUp() {
        if (wrapper != null) {
            wrapper.descendants().forEach(ProcessHandle::destroyForcibly);
            wrapper.destroyForcibly();
        }
    }

    @Test
    void resolveServerJvm_encuentraLaJvmHijaDeRunSh() throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path sleeper = tempDir.resolve("Sleeper.java");
        Files.writeString(sleeper, "class Sleeper { public static void main(String[] a) throws Exception { Thread.sleep(60_000); } }");
        String java = ProcessHandle.current().info().command().orElseThrow();
        // Igual que run.sh de Forge: un intérprete de comandos que lanza la JVM como hijo.
        wrapper = new ProcessBuilder("sh", "-c", "'" + java + "' '" + sleeper + "' & wait").start();

        ProcessHandle jvm = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jvm = PanelEstadisticas.resolveServerJvm(wrapper);
            if (jvm.pid() != wrapper.pid()) {
                break;
            }
            Thread.sleep(50);
        }

        assertThat(jvm).isNotNull();
        assertThat(jvm.pid()).isNotEqualTo(wrapper.pid());
        assertThat(jvm.parent().map(ProcessHandle::pid)).contains(wrapper.pid());
    }

    @Test
    void getLiveResourceSnapshot_vuelveACeroCuandoElServidorEstaApagado() throws Exception {
        Server server = new Server();
        server.setServerConfig(new ServerConfig(1024, 4096, 25565));

        Method getOrCreateHistory = PanelEstadisticas.class.getDeclaredMethod("getOrCreateHistory", Server.class);
        getOrCreateHistory.setAccessible(true);
        Object history = getOrCreateHistory.invoke(null, server);
        Method addRamSample = history.getClass().getDeclaredMethod("addRamSample", long.class, long.class, int.class, int.class);
        addRamSample.setAccessible(true);
        addRamSample.invoke(history, 3000L, System.currentTimeMillis(), 60, 60);

        ServerResourceSnapshot snapshot = PanelEstadisticas.getLiveResourceSnapshot(server);

        assertThat(snapshot.running()).isFalse();
        assertThat(snapshot.ramUsedMb()).isZero();
        assertThat(snapshot.cpuPercent()).isZero();
    }
}
