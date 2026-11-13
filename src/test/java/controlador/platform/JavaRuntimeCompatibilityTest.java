package controlador.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaRuntimeCompatibilityTest {

    @Test
    void requirementFor_cubreLosRangosDeMinecraft() {
        assertThat(JavaRuntimeCompatibility.requirementFor("1.8.8")).isEqualTo(new JavaRuntimeCompatibility.Requirement(8, 8));
        assertThat(JavaRuntimeCompatibility.requirementFor("1.12.2")).isEqualTo(new JavaRuntimeCompatibility.Requirement(8, 16));
        assertThat(JavaRuntimeCompatibility.requirementFor("1.12.2", true)).isEqualTo(new JavaRuntimeCompatibility.Requirement(8, 8));
        assertThat(JavaRuntimeCompatibility.requirementFor("1.17.1")).isEqualTo(new JavaRuntimeCompatibility.Requirement(16, 0));
        assertThat(JavaRuntimeCompatibility.requirementFor("1.20.4")).isEqualTo(new JavaRuntimeCompatibility.Requirement(17, 0));
        assertThat(JavaRuntimeCompatibility.requirementFor("1.20.5")).isEqualTo(new JavaRuntimeCompatibility.Requirement(21, 0));
        assertThat(JavaRuntimeCompatibility.requirementFor("1.21.10")).isEqualTo(new JavaRuntimeCompatibility.Requirement(21, 0));
        assertThat(JavaRuntimeCompatibility.requirementFor("26.1")).isEqualTo(new JavaRuntimeCompatibility.Requirement(25, 0));
        assertThat(JavaRuntimeCompatibility.requirementFor("26.2-snapshot-7")).isEqualTo(new JavaRuntimeCompatibility.Requirement(25, 0));
    }

    @Test
    void requirementFor_noAvisaDeVersionesDesconocidas() {
        assertThat(JavaRuntimeCompatibility.requirementFor(null)).isNull();
        assertThat(JavaRuntimeCompatibility.requirementFor("24w14a")).isNull();
        assertThat(JavaRuntimeCompatibility.requirementFor("desconocida")).isNull();
    }

    @Test
    void warningFor_avisaSiJavaEsDemasiadoNuevoOViejo() {
        assertThat(JavaRuntimeCompatibility.warningFor("1.8.8", false, 25)).contains("Java 8").contains("Java 25");
        assertThat(JavaRuntimeCompatibility.warningFor("1.21.1", false, 17)).contains("Java 21 o superior");
        assertThat(JavaRuntimeCompatibility.warningFor("1.21.1", false, 25)).isNull();
        assertThat(JavaRuntimeCompatibility.warningFor("1.12.2", false, 11)).isNull();
        assertThat(JavaRuntimeCompatibility.warningFor("1.12.2", true, 11)).isNotNull();
        assertThat(JavaRuntimeCompatibility.warningFor("1.8.8", false, null)).isNull();
    }

    @Test
    void parseJavaMajor_entiendeFormatosNuevosYAntiguos() {
        assertThat(JavaRuntimeCompatibility.parseJavaMajor("openjdk version \"25.0.1\" 2025-10-21")).isEqualTo(25);
        assertThat(JavaRuntimeCompatibility.parseJavaMajor("Picked up JAVA_TOOL_OPTIONS: -Dx=y\nopenjdk version \"21\" 2023-09-19")).isEqualTo(21);
        assertThat(JavaRuntimeCompatibility.parseJavaMajor("java version \"1.8.0_392\"")).isEqualTo(8);
        assertThat(JavaRuntimeCompatibility.parseJavaMajor("no java here")).isNull();
    }
}
