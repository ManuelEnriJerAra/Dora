package controlador;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cierra un proceso junto con todos sus descendientes.
 *
 * <p>Los servidores Forge/NeoForge se arrancan a traves de {@code run.bat}/{@code run.sh}, de modo que
 * el proceso que guarda Dora es {@code cmd.exe}/{@code sh} y el servidor Java real es un hijo. Destruir solo
 * el proceso raiz deja el servidor vivo y huerfano, asi que hay que cerrar el arbol completo.</p>
 */
public final class ProcessTreeTerminator {

    private ProcessTreeTerminator() {
    }

    /**
     * Pide el cierre del arbol (hijos primero) y, si no termina dentro de {@code gracefulWait},
     * lo fuerza y espera hasta {@code forceWait}.
     *
     * @return {@code true} si al final no queda ningun proceso del arbol vivo.
     */
    public static boolean terminate(ProcessHandle root, Duration gracefulWait, Duration forceWait) throws InterruptedException {
        if (root == null) {
            return true;
        }
        // Capturamos los descendientes antes de tocar la raiz: si la raiz muere primero, los hijos se
        // reasignan a otro padre y ya no aparecen en descendants().
        List<ProcessHandle> tree = snapshotTree(root);
        if (tree.stream().noneMatch(ProcessHandle::isAlive)) {
            return true;
        }

        for (ProcessHandle handle : tree) {
            if (handle.isAlive()) {
                handle.destroy();
            }
        }
        if (waitForAll(tree, gracefulWait)) {
            return true;
        }

        // Puede haber nacido algun hijo nuevo mientras esperabamos.
        for (ProcessHandle handle : mergeTree(tree, root)) {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        }
        return waitForAll(tree, forceWait);
    }

    public static boolean terminate(Process process, Duration gracefulWait, Duration forceWait) throws InterruptedException {
        return process == null || terminate(process.toHandle(), gracefulWait, forceWait);
    }

    /** {@code true} si el proceso o alguno de sus descendientes sigue vivo. */
    public static boolean isTreeAlive(Process process) {
        if (process == null) {
            return false;
        }
        if (process.isAlive()) {
            return true;
        }
        return process.toHandle().descendants().anyMatch(ProcessHandle::isAlive);
    }

    static List<ProcessHandle> snapshotTree(ProcessHandle root) {
        List<ProcessHandle> tree = new ArrayList<>();
        // Hijos mas profundos primero, raiz al final.
        List<ProcessHandle> descendants = root.descendants().toList();
        for (int i = descendants.size() - 1; i >= 0; i--) {
            tree.add(descendants.get(i));
        }
        tree.add(root);
        return tree;
    }

    private static List<ProcessHandle> mergeTree(List<ProcessHandle> tree, ProcessHandle root) {
        List<ProcessHandle> merged = new ArrayList<>(tree);
        if (root.isAlive()) {
            for (ProcessHandle handle : snapshotTree(root)) {
                if (!merged.contains(handle)) {
                    merged.add(0, handle);
                }
            }
        }
        return merged;
    }

    private static boolean waitForAll(List<ProcessHandle> handles, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + Math.max(0L, timeout.toNanos());
        for (ProcessHandle handle : handles) {
            long remaining = deadline - System.nanoTime();
            if (!handle.isAlive()) {
                continue;
            }
            if (remaining <= 0) {
                return false;
            }
            try {
                handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                return false;
            } catch (java.util.concurrent.ExecutionException e) {
                // onExit no falla en la practica; tratamos el proceso como no confirmado.
                if (handle.isAlive()) {
                    return false;
                }
            }
        }
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }
}
