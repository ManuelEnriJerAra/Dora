package controlador;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IPLocalTest {

    @Test
    void prefiereLaRedFisicaFrenteAAdaptadoresVirtuales() {
        String ip = IPLocal.elegir(List.of(
                new IPLocal.Candidata("172.17.0.1", "docker0 docker0"),
                new IPLocal.Candidata("192.168.56.1", "vboxnet0 virtualbox host-only"),
                new IPLocal.Candidata("192.168.1.34", "wlan0 intel wi-fi")
        ));
        assertThat(ip).isEqualTo("192.168.1.34");
    }

    @Test
    void sinCandidatasUsaLocalhost() {
        assertThat(IPLocal.elegir(List.of())).isEqualTo(IPLocal.LOCALHOST);
    }
}
