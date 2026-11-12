package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class PurpurServerPlatformAdapter extends AbstractServerPlatformAdapter {
    private final PurpurDownloadsClient downloadsClient;

    public PurpurServerPlatformAdapter() {
        this(new PurpurDownloadsClient());
    }

    PurpurServerPlatformAdapter(PurpurDownloadsClient downloadsClient) {
        this.downloadsClient = downloadsClient == null ? new PurpurDownloadsClient() : downloadsClient;
    }

    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.PURPUR;
    }

    @Override
    public int getDetectionPriority() {
        return 150;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasPurpurFiles = exists(serverDir, "purpur.yml");
        boolean hasPurpurJarMarkers = MinecraftServerJarInspector.looksLikePurpurServerJar(executableJar);
        if (!hasPurpurFiles && !hasPurpurJarMarkers) {
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
        return ServerPlatform.PURPUR.defaultCapabilities();
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
        return "Purpur";
    }

    @Override
    public List<ServerCreationOption> listCreationOptions() throws IOException {
        return downloadsClient.listCreationOptions();
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        ServerPlatformInstallSupport.requireServerAndRequest(server, request, "Purpur");
        Files.createDirectories(request.targetDirectory());
        String downloadUrl = downloadsClient.downloadUrl(request.minecraftVersion(), request.platformVersion());
        Path destinationJar = request.targetDirectory().resolve("purpur-" + request.minecraftVersion() + "-" + request.platformVersion() + ".jar");
        if (!Files.isRegularFile(destinationJar)) {
            request.downloader().download(downloadUrl, destinationJar.toFile());
            DownloadChecksum.verifyOrDelete(
                    destinationJar,
                    "MD5",
                    downloadsClient.md5(request.minecraftVersion(), request.platformVersion()),
                    "Purpur " + request.minecraftVersion()
            );
        }
        ServerPlatformInstallSupport.prepareCommonFiles(
                server,
                request,
                ServerPlatform.PURPUR,
                request.platformVersion(),
                "Dora Purpur " + request.minecraftVersion(),
                getExtensionDirectories(request.targetDirectory())
        );
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (Exception e) {
            return null;
        }
    }
}
