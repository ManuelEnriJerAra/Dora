package controlador.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compara la versión de Java que se usará para arrancar un servidor (el {@code java} del PATH)
 * con la que necesita su versión de Minecraft.
 *
 * <p>Solo avisa: no cambia qué versiones se pueden instalar ni qué Java se usa. Elegir o descargar
 * un Java por servidor es una funcionalidad aparte ({@code docs/pending-features/server-managed-java-runtimes.md}).</p>
 */
public final class JavaRuntimeCompatibility {

    /** Versión de Java recomendada para una versión de Minecraft. {@code maxJava} 0 significa sin límite conocido. */
    public record Requirement(int minJava, int maxJava) {
    }

    private static final Pattern RELEASE = Pattern.compile("^1\\.(\\d+)(?:\\.(\\d+))?(?:[^0-9].*)?$");
    private static final Pattern YEAR_RELEASE = Pattern.compile("^(\\d{2})\\.(\\d+)(?:\\.\\d+)?(?:[^0-9].*)?$");
    private static final Pattern JAVA_VERSION = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?[^\"]*\"");

    private static volatile Integer cachedPathJavaMajor;
    private static volatile boolean pathJavaDetected;

    private JavaRuntimeCompatibility() {
    }

    /**
     * @return el requisito para la versión indicada, o {@code null} si la versión no se reconoce
     * (snapshots con formato semanal, nombres raros...), en cuyo caso no se avisa.
     */
    public static Requirement requirementFor(String minecraftVersion) {
        return requirementFor(minecraftVersion, false);
    }

    /**
     * @param forge {@code true} para Forge: hasta 1.16.5 solo funciona de forma fiable con Java 8.
     */
    public static Requirement requirementFor(String minecraftVersion, boolean forge) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            return null;
        }
        String version = minecraftVersion.trim();
        Matcher release = RELEASE.matcher(version);
        if (release.matches()) {
            int minor = Integer.parseInt(release.group(1));
            int patch = release.group(2) == null ? 0 : Integer.parseInt(release.group(2));
            if (minor <= 11 || (forge && minor <= 16)) {
                // Las versiones muy antiguas (y Forge hasta 1.16) fallan con Java moderno.
                return new Requirement(8, 8);
            }
            if (minor <= 16) {
                return new Requirement(8, 16);
            }
            if (minor == 17) {
                return new Requirement(16, 0);
            }
            if (minor < 20 || (minor == 20 && patch <= 4)) {
                return new Requirement(17, 0);
            }
            return new Requirement(21, 0);
        }
        Matcher yearRelease = YEAR_RELEASE.matcher(version);
        if (yearRelease.matches() && Integer.parseInt(yearRelease.group(1)) >= 26) {
            return new Requirement(25, 0);
        }
        return null;
    }

    /**
     * Mensaje de aviso en español, o {@code null} si la combinación es correcta o no se puede saber.
     */
    public static String warningFor(String minecraftVersion, boolean forge, Integer javaMajor) {
        Requirement requirement = requirementFor(minecraftVersion, forge);
        if (requirement == null || javaMajor == null) {
            return null;
        }
        if (javaMajor < requirement.minJava()) {
            return "Minecraft " + minecraftVersion.trim() + " necesita Java " + requirement.minJava()
                    + " o superior, pero el Java del sistema es Java " + javaMajor
                    + ". Lo más probable es que el servidor no arranque.";
        }
        if (requirement.maxJava() > 0 && javaMajor > requirement.maxJava()) {
            return "Minecraft " + minecraftVersion.trim() + " está pensado para Java " + requirement.maxJava()
                    + " o anterior, pero el Java del sistema es Java " + javaMajor
                    + ". Las versiones antiguas pueden fallar o comportarse mal con un Java tan nuevo.";
        }
        return null;
    }

    /**
     * Versión principal del {@code java} del PATH (el que usan los arranques), cacheada para la sesión.
     *
     * @return la versión, o {@code null} si no se ha podido detectar.
     */
    public static Integer detectPathJavaMajor() {
        if (pathJavaDetected) {
            return cachedPathJavaMajor;
        }
        synchronized (JavaRuntimeCompatibility.class) {
            if (!pathJavaDetected) {
                cachedPathJavaMajor = runJavaVersion();
                pathJavaDetected = true;
            }
            return cachedPathJavaMajor;
        }
    }

    static Integer parseJavaMajor(String javaVersionOutput) {
        if (javaVersionOutput == null) {
            return null;
        }
        Matcher matcher = JAVA_VERSION.matcher(javaVersionOutput);
        if (!matcher.find()) {
            return null;
        }
        int first = Integer.parseInt(matcher.group(1));
        if (first == 1 && matcher.group(2) != null) {
            // Formato antiguo: 1.8.0_392 -> 8
            return Integer.parseInt(matcher.group(2));
        }
        return first;
    }

    private static Integer runJavaVersion() {
        Process process = null;
        try {
            process = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
            byte[] output;
            try (InputStream in = process.getInputStream()) {
                output = in.readNBytes(8192);
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                return null;
            }
            return parseJavaMajor(new String(output, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
