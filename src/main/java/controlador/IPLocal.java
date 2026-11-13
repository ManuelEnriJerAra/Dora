package controlador;

import javax.swing.SwingUtilities;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dirección IPv4 de la red local (LAN). Se usa cuando no hay IP pública, por ejemplo sin internet,
 * para que el usuario siga teniendo una dirección a la que conectarse desde su red.
 * Recorrer las interfaces puede tardar en Windows, así que se hace en segundo plano y se guarda.
 */
public final class IPLocal {

    public static final String LOCALHOST = "localhost";
    private static volatile String cache;

    private IPLocal() {
    }

    public static void getAsync(Consumer<String> callback) {
        Objects.requireNonNull(callback, "callback");
        String actual = cache;
        if (actual != null) {
            SwingUtilities.invokeLater(() -> callback.accept(actual));
            return;
        }
        Thread hilo = new Thread(() -> {
            String ip = buscarIpLocal();
            cache = ip;
            SwingUtilities.invokeLater(() -> callback.accept(ip));
        }, "buscar-ip-local");
        hilo.setDaemon(true);
        hilo.start();
    }

    static String buscarIpLocal() {
        List<Candidata> candidatas = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) continue;
                } catch (Exception ex) {
                    continue;
                }
                String nombre = (ni.getName() + " " + ni.getDisplayName()).toLowerCase(Locale.ROOT);
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        candidatas.add(new Candidata(address.getHostAddress(), nombre));
                    }
                }
            }
        } catch (Exception ignored) {
            // Sin acceso a las interfaces: se usa localhost.
        }
        return elegir(candidatas);
    }

    record Candidata(String ip, String nombreInterfaz) {
    }

    /** Prefiere interfaces físicas frente a adaptadores de máquinas virtuales, VPN o Docker. */
    static String elegir(List<Candidata> candidatas) {
        Candidata mejor = null;
        int mejorPuntos = Integer.MIN_VALUE;
        for (Candidata c : candidatas) {
            int puntos = 0;
            String n = c.nombreInterfaz() == null ? "" : c.nombreInterfaz();
            if (n.contains("virtual") || n.contains("vmware") || n.contains("vbox") || n.contains("hyper-v")
                    || n.contains("docker") || n.contains("wsl") || n.contains("vpn") || n.contains("tap")
                    || n.contains("tun") || n.contains("zerotier") || n.contains("tailscale") || n.contains("veth")
                    || n.startsWith("br-")) {
                puntos -= 10;
            }
            if (c.ip().startsWith("192.168.")) puntos += 2;
            else if (c.ip().startsWith("10.")) puntos += 1;
            if (puntos > mejorPuntos) {
                mejor = c;
                mejorPuntos = puntos;
            }
        }
        return mejor == null ? LOCALHOST : mejor.ip();
    }
}
