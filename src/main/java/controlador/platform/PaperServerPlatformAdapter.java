package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class PaperServerPlatformAdapter extends AbstractServerPlatformAdapter {
    private final PaperDownloadsClient downloadsClient;

    public PaperServerPlatformAdapter() {
        this(new PaperDownloadsClient());
    }

    PaperServerPlatformAdapter(PaperDownloadsClient downloadsClient) {
        this.downloadsClient = downloadsClient == null ? new PaperDownloadsClient() : downloadsClient;
    }

    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.PAPER;
    }

    @Override
    public int getDetectionPriority() {
        return 120;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasPaperFiles = exists(serverDir, "paper.yml")
                || exists(serverDir, "paper-global.yml")
                || exists(serverDir, "paper-world-defaults.yml");
        boolean hasPaperJarMarkers = MinecraftServerJarInspector.looksLikePaperServerJar(executableJar);
        if (!hasPaperFiles && !hasPaperJarMarkers) {
            return null;
        }
        ServerValidationResult validation = validate(serverDir);
        return validation.valid() ? buildProfile(serverDir, null) : null;
    }

    @Override
    public List<Path> getExtensionDirectories(Path serverDir) {
        return serverDir == null ? List.of() : List.of(serverDir.resolve("plugins"));
    }

    @Override
    public Set<ServerCapability> getCapabilities() {
        return ServerPlatform.PAPER.defaultCapabilities();
    }

    @Override
    public boolean supportsAutomatedCreation() {
        return true;
    }

    @Override
    public boolean supportsUnstableCreationOptions() {
        return true;
    }

    @Override
    public String getCreationDisplayName() {
        return "Paper";
    }

    @Override
    public List<ServerCreationOption> listCreationOptions() throws IOException {
        return downloadsClient.listCreationOptions();
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        ServerPlatformInstallSupport.requireServerAndRequest(server, request, "Paper");
        Files.createDirectories(request.targetDirectory());
        PaperDownloadsClient.PaperBuild build = downloadsClient.resolveBuild(request.minecraftVersion(), request.platformVersion());
        Path destinationJar = request.targetDirectory().resolve("paper-" + request.minecraftVersion() + "-" + build.id() + ".jar");
        if (!Files.isRegularFile(destinationJar)) {
            request.downloader().download(build.downloadUrl(), destinationJar.toFile());
            DownloadChecksum.verifyOrDelete(destinationJar, "SHA-256", build.sha256(), "Paper " + request.minecraftVersion());
        }
        ServerPlatformInstallSupport.prepareCommonFiles(
                server,
                request,
                ServerPlatform.PAPER,
                build.id(),
                "Dora Paper " + request.minecraftVersion(),
                getExtensionDirectories(request.targetDirectory())
        );
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (RuntimeException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
