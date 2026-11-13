package controlador;

import controlador.extensions.ExtensionCompatibilityReport;
import controlador.extensions.ExtensionCompatibilityStatus;
import modelo.Server;
import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;
import controlador.extensions.ExtensionCatalogProviderDescriptor;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ModrinthModpackService;
import controlador.platform.ForgeServerPlatformAdapter;
import controlador.platform.ServerCreationOption;
import controlador.platform.ServerInstallationRequest;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformAdapters;
import controlador.platform.ServerPlatformProfile;
import controlador.platform.ServerValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestWorldFixtures;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestorServidoresTest {
    @TempDir
    Path tempDir;

    @Test
    void validarNombreCarpetaServidor_debeAceptarSoloNombresPortables() {
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("server-1.20.1")).isNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("Forge_Server")).isNull();

        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor(" server")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("server ")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor(".")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("..")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("server.")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("server/name")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("server\\name")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("server:name")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("CON")).isNotNull();
        assertThat(AsistenteCreacionServidor.validarNombreCarpetaServidor("com1.txt")).isNotNull();
    }

    @Test
    void filtrosCreacionVersiones_debenMostrarReleasesYSnapshotsCuandoAmbosEstanActivos() throws Exception {
        ServerCreationOption release = new ServerCreationOption(
                ServerPlatform.FABRIC,
                "1.21.2",
                "loader",
                "Minecraft 1.21.2",
                "fabric-1.21.2-server",
                ServerCreationOption.VERSION_TYPE_RELEASE
        );
        ServerCreationOption snapshot = new ServerCreationOption(
                ServerPlatform.FABRIC,
                "25w01a",
                "loader",
                "Minecraft 25w01a",
                "fabric-25w01a-server",
                ServerCreationOption.VERSION_TYPE_SNAPSHOT
        );

        assertThat(AsistenteCreacionServidor.debeMostrarOpcionCreacion(release, true, true)).isTrue();
        assertThat(AsistenteCreacionServidor.debeMostrarOpcionCreacion(snapshot, true, true)).isTrue();
        assertThat(AsistenteCreacionServidor.debeMostrarOpcionCreacion(release, true, false)).isFalse();
        assertThat(AsistenteCreacionServidor.debeMostrarOpcionCreacion(snapshot, false, true)).isFalse();
    }

    @Test
    void versionesMinecraftCanonicas_debenNormalizarSnapshotsPreYRcModernos() {
        assertThat(ServerCreationOption.canonicalMinecraftVersion("1.7.10_pre4")).isEqualTo("1.7.10-pre-4");
        assertThat(ServerCreationOption.canonicalMinecraftVersion("26.1.2_rc_1")).isEqualTo("26.1.2-rc-1");
        assertThat(ServerCreationOption.canonicalMinecraftVersion("26.1.2-rc1")).isEqualTo("26.1.2-rc-1");
        assertThat(ServerCreationOption.canonicalMinecraftVersion("26.2_snapshot_7")).isEqualTo("26.2-snapshot-7");
        assertThat(ServerCreationOption.isCompatibleMinecraftVersion("26.1-pre-3", "26.1_pre3")).isTrue();
        assertThat(ServerCreationOption.versionTypeFromText("26.1-pre-3")).isEqualTo(ServerCreationOption.VERSION_TYPE_SNAPSHOT);
        assertThat(ServerCreationOption.versionTypeFromText("26.1.2-rc-1")).isEqualTo(ServerCreationOption.VERSION_TYPE_SNAPSHOT);
    }

    @Test
    void cargarOpcionesConversion_debeEmparejarSnapshotsVanillaConVersionCanonicaDelLoader() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Server server = new Server();
        server.setVersion("1.7.10_pre4");
        ServerPlatformAdapter adapter = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.FORGE;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public List<ServerCreationOption> listCreationOptions() {
                return List.of(
                        new ServerCreationOption(
                                ServerPlatform.FORGE,
                                "1.7.10-pre4",
                                "1.7.10_pre4-10.12.2.1149-prerelease",
                                "Minecraft 1.7.10-pre4 (Forge 10.12.2.1149-prerelease)",
                                "forge-1.7.10-pre4-server",
                                ServerCreationOption.VERSION_TYPE_SNAPSHOT
                        ),
                        new ServerCreationOption(
                                ServerPlatform.FORGE,
                                "1.7.10",
                                "1.7.10-10.13.4.1614",
                                "Minecraft 1.7.10 (Forge 10.13.4.1614)",
                                "forge-1.7.10-server",
                                ServerCreationOption.VERSION_TYPE_RELEASE
                        )
                );
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server server, ServerInstallationRequest request) {
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("server.jar");
            }
        };
        List<ServerCreationOption> options = new ConversorPlataformaServidor(gestor).evaluarDisponibilidadConversion(server, List.of(adapter))
                .getFirst()
                .options();

        assertThat(options)
                .extracting(ServerCreationOption::platformVersion)
                .containsExactly("1.7.10_pre4-10.12.2.1149-prerelease");
    }

    @Test
    void cargarOpcionesConversion_debeEmparejarSnapshotModernoConFabric() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Server server = new Server();
        server.setVersion("26.2-snapshot-7");
        ServerPlatformAdapter adapter = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.FABRIC;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public List<ServerCreationOption> listCreationOptions() {
                return List.of(
                        new ServerCreationOption(
                                ServerPlatform.FABRIC,
                                "26.2-snapshot-7",
                                "0.19.2|1.1.1",
                                "Minecraft 26.2-snapshot-7 (Fabric Loader 0.19.2)",
                                "fabric-26.2-snapshot-7-server",
                                ServerCreationOption.VERSION_TYPE_SNAPSHOT
                        ),
                        new ServerCreationOption(
                                ServerPlatform.FABRIC,
                                "26.1.2",
                                "0.19.2|1.1.1",
                                "Minecraft 26.1.2 (Fabric Loader 0.19.2)",
                                "fabric-26.1.2-server",
                                ServerCreationOption.VERSION_TYPE_RELEASE
                        )
                );
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server server, ServerInstallationRequest request) {
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("server.jar");
            }
        };
        List<ServerCreationOption> options = new ConversorPlataformaServidor(gestor).evaluarDisponibilidadConversion(server, List.of(adapter))
                .getFirst()
                .options();

        assertThat(options)
                .extracting(ServerCreationOption::minecraftVersion)
                .containsExactly("26.2-snapshot-7");
    }

    @Test
    void evaluarDisponibilidadConversion_debeMarcarPlataformasSinVersionCompatibleComoNoDisponibles() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Server server = new Server();
        server.setVersion("26.2-snapshot-7");

        ServerPlatformAdapter forge = adapterConversionConOpciones(
                ServerPlatform.FORGE,
                List.of(new ServerCreationOption(
                        ServerPlatform.FORGE,
                        "1.7.10-pre4",
                        "1.7.10_pre4-10.12.2.1149-prerelease",
                        "Minecraft 1.7.10-pre4 (Forge 10.12.2.1149-prerelease)",
                        "forge-1.7.10-pre4-server",
                        ServerCreationOption.VERSION_TYPE_SNAPSHOT
                ))
        );
        ServerPlatformAdapter fabric = adapterConversionConOpciones(
                ServerPlatform.FABRIC,
                List.of(new ServerCreationOption(
                        ServerPlatform.FABRIC,
                        "26.2-snapshot-7",
                        "0.19.2|1.1.1",
                        "Minecraft 26.2-snapshot-7 (Fabric Loader 0.19.2)",
                        "fabric-26.2-snapshot-7-server",
                        ServerCreationOption.VERSION_TYPE_SNAPSHOT
                ))
        );

        List<ConversorPlataformaServidor.ConversionTargetAvailability> availability = new ConversorPlataformaServidor(gestor).evaluarDisponibilidadConversion(
                server,
                List.of(forge, fabric)
        );

        assertThat(availability)
                .filteredOn(entry -> entry.adapter().getPlatform() == ServerPlatform.FORGE)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.available()).isFalse();
                    assertThat(entry.unavailableReason()).contains("No disponible").contains("26.2-snapshot-7");
                });
        assertThat(availability)
                .filteredOn(entry -> entry.adapter().getPlatform() == ServerPlatform.FABRIC)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.available()).isTrue();
                    assertThat(entry.options())
                            .extracting(ServerCreationOption::minecraftVersion)
                            .containsExactly("26.2-snapshot-7");
                });
    }

    @Test
    void guardarServidor_debeCompletarMetadatosYPersistirEnJsonAislado() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Server server = new Server();
        server.setId(null);
        server.setDisplayName("Alpha");
        server.setServerConfig(null);

        gestor.guardarServidor(server);

        List<Server> persisted = new ObjectMapper().readValue(jsonPath.toFile(),
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().getId()).isNotBlank();
        assertThat(persisted.getFirst().getServerConfig()).isNotNull();
        assertThat(persisted.getFirst().getOrdenLista()).isZero();
        assertThat(persisted.getFirst().getPreviewRenderProfileId()).isEqualTo("quality");
        assertThat(persisted.getFirst().getPreviewRenderRealtime()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowSpawn()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowPlayers()).isFalse();
        assertThat(persisted.getFirst().getPreviewShowChunkGrid()).isFalse();
        assertThat(persisted.getFirst().getPreviewUseWholeMap()).isFalse();
        assertThat(persisted.getFirst().getPreviewRenderLimitPixels()).isEqualTo(256);
        assertThat(persisted.getFirst().getPreviewRenderCenterId()).isEqualTo("spawn");
        assertThat(persisted.getFirst().getEstadisticasCpuActiva()).isTrue();
        assertThat(persisted.getFirst().getEstadisticasCpuHistorial()).isTrue();
        assertThat(persisted.getFirst().getPlatform()).isEqualTo(ServerPlatform.UNKNOWN);
        assertThat(persisted.getFirst().getLoader()).isEqualTo(ServerLoader.UNKNOWN);
        assertThat(persisted.getFirst().getEcosystemType()).isEqualTo(ServerEcosystemType.UNKNOWN);
        assertThat(persisted.getFirst().getCapabilities()).contains(ServerCapability.CORE_SERVER);
        assertThat(persisted.getFirst().getExtensions()).isNotNull().isEmpty();
    }

    @Test
    void constructor_debeCompletarPreferenciasPreviewFaltantes() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        Path serverDir = tempDir.resolve("previewless-server");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar");
        Server server = new Server();
        server.setDisplayName("Previewless");
        server.setServerDir(serverDir.toString());
        server.setPreviewRenderProfileId(null);
        server.setPreviewRenderRealtime(null);
        server.setPreviewShowSpawn(null);
        server.setPreviewShowPlayers(null);
        server.setPreviewShowChunkGrid(null);
        server.setPreviewUseWholeMap(null);
        server.setPreviewRenderLimitPixels(null);
        server.setPreviewRenderCenterId(null);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(server));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        Server persisted = gestor.getListaServidores().getFirst();
        assertThat(persisted.getPreviewRenderProfileId()).isEqualTo("quality");
        assertThat(persisted.getPreviewRenderRealtime()).isFalse();
        assertThat(persisted.getPreviewShowSpawn()).isFalse();
        assertThat(persisted.getPreviewShowPlayers()).isFalse();
        assertThat(persisted.getPreviewShowChunkGrid()).isFalse();
        assertThat(persisted.getPreviewUseWholeMap()).isFalse();
        assertThat(persisted.getPreviewRenderLimitPixels()).isEqualTo(256);
        assertThat(persisted.getPreviewRenderCenterId()).isEqualTo("spawn");
        assertThat(persisted.getEstadisticasCpuActiva()).isTrue();
        assertThat(persisted.getEstadisticasCpuHistorial()).isTrue();
        assertThat(persisted.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(persisted.getLoader()).isEqualTo(ServerLoader.VANILLA);
        assertThat(persisted.getEcosystemType()).isEqualTo(ServerEcosystemType.NONE);
        assertThat(persisted.getExtensions()).isNotNull().isEmpty();
    }

    @Test
    void constructor_debeMigrarTipoLegacyAPlataformaLoaderYCapacidades() throws Exception {
        Path jsonPath = tempDir.resolve("ServerList.json");
        Path forgeDir = tempDir.resolve("legacy-forge-server");
        TestWorldFixtures.createValidServerJar(forgeDir, "server.jar");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        String legacyJson = """
                [
                  {
                    "id": "legacy-1",
                    "displayName": "Legacy Forge",
                    "tipo": "FORGE",
                    "serverDir": "%s"
                  }
                ]
                """.formatted(forgeDir.toString().replace("\\", "\\\\"));
        Files.writeString(jsonPath, legacyJson, StandardCharsets.UTF_8);

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        Server persisted = gestor.getListaServidores().getFirst();
        assertThat(persisted.getTipo()).isEqualTo("FORGE");
        assertThat(persisted.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(persisted.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(persisted.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(persisted.getCapabilities()).contains(
                ServerCapability.CORE_SERVER,
                ServerCapability.EXTENSIONS,
                ServerCapability.MOD_EXTENSIONS
        );
        assertThat(persisted.getExtensions()).isNotNull().isEmpty();

        List<Server> reloaded = new ObjectMapper().readValue(jsonPath.toFile(),
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(reloaded.getFirst().getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(reloaded.getFirst().getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(reloaded.getFirst().getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
    }

    @Test
    void importarServidorDesdeDirectorio_debeDetectarForgeYActivarExperienciaMods() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("import-forge");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "launch.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));

        Server imported = gestor.importarServidorDesdeDirectorio(forgeDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getTipo()).isEqualTo("FORGE");
        assertThat(imported.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(imported.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(imported.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(imported.getCapabilities()).contains(ServerCapability.MOD_EXTENSIONS);
    }

    @Test
    void importarServidorDesdeDirectorio_debeDetectarPaperYActivarExperienciaPlugins() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path paperDir = tempDir.resolve("import-paper");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "custom-runtime.jar",
                "{\"id\":\"1.21.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );

        Server imported = gestor.importarServidorDesdeDirectorio(paperDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getTipo()).isEqualTo("PAPER");
        assertThat(imported.getPlatform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(imported.getLoader()).isEqualTo(ServerLoader.PAPER);
        assertThat(imported.getEcosystemType()).isEqualTo(ServerEcosystemType.PLUGINS);
        assertThat(imported.getCapabilities()).contains(ServerCapability.PLUGIN_EXTENSIONS);
    }

    @Test
    void importarServidorDesdeDirectorio_usaElNombreDeLaCarpeta() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path firstDir = tempDir.resolve("Survival amigos");
        TestWorldFixtures.createValidServerJar(firstDir, "server.jar", "{\"id\":\"1.20.1\"}");

        Server first = gestor.importarServidorDesdeDirectorio(firstDir);

        assertThat(first).isNotNull();
        assertThat(first.getDisplayName()).isEqualTo("Survival amigos");

        Path secondDir = tempDir.resolve("otra").resolve("Survival amigos");
        TestWorldFixtures.createValidServerJar(secondDir, "server.jar", "{\"id\":\"1.20.1\"}");

        Server second = gestor.importarServidorDesdeDirectorio(secondDir);

        assertThat(second).isNotNull();
        assertThat(second.getDisplayName()).isEqualTo("Survival amigos (1)");
    }

    @Test
    void importarServidorDesdeDirectorio_conCarpetaGenericaUsaLaVersionSinSufijoCero() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path firstDir = tempDir.resolve("a").resolve("server");
        TestWorldFixtures.createValidServerJar(firstDir, "server.jar", "{\"id\":\"1.20.1\"}");

        Server first = gestor.importarServidorDesdeDirectorio(firstDir);

        assertThat(first).isNotNull();
        assertThat(first.getDisplayName()).isEqualTo("Servidor 1.20.1");

        Path secondDir = tempDir.resolve("b").resolve("minecraft");
        TestWorldFixtures.createValidServerJar(secondDir, "server.jar", "{\"id\":\"1.20.1\"}");

        Server second = gestor.importarServidorDesdeDirectorio(secondDir);

        assertThat(second).isNotNull();
        assertThat(second.getDisplayName()).isEqualTo("Servidor 1.20.1 (1)");
    }

    @Test
    void importarServidorDesdeDirectorio_debeCargarForgeModernoSinJarEnRaiz() throws Exception {
        Path jsonPath = tempDir.resolve("ServerList.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Path forgeDir = tempDir.resolve("modern-forge");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));
        Files.writeString(forgeDir.resolve("run.bat"), "@echo off");
        Files.writeString(forgeDir.resolve("run.sh"), "#!/bin/sh\n");

        Server imported = gestor.importarServidorDesdeDirectorio(forgeDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(imported.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(imported.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(imported.getCapabilities()).contains(ServerCapability.MOD_EXTENSIONS);

        GestorServidores reloaded = new GestorServidores(jsonPath.toFile());
        assertThat(reloaded.getListaServidores()).hasSize(1);
        assertThat(reloaded.getListaServidores().getFirst().getPlatform()).isEqualTo(ServerPlatform.FORGE);
    }

    @Test
    void forgeBuildStartProcess_debeUsarScriptAunqueNoHayaJarEjecutable() throws Exception {
        Path forgeDir = tempDir.resolve("forge-script-start");
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));
        Files.writeString(forgeDir.resolve("run.bat"), "@echo off");
        Files.writeString(forgeDir.resolve("run.sh"), "#!/bin/sh\n");
        Server server = new Server();
        server.setServerDir(forgeDir.toString());

        ProcessBuilder processBuilder = new ForgeServerPlatformAdapter().buildStartProcess(server, null);

        assertThat(processBuilder.command()).isNotEmpty();
        assertThat(String.join(" ", processBuilder.command())).contains(System.getProperty("os.name", "").toLowerCase().contains("win") ? "run.bat" : "run.sh");
        assertThat(processBuilder.directory()).isEqualTo(forgeDir.toFile());
    }

    @Test
    void instalarExtensionManual_debeCopiarJarAModsYRegistrarMetadataLocal() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("forge-with-mods");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setDisplayName("Forge Server");
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);
        gestor.guardarServidor(server);

        Path sourceJar = tempDir.resolve("example-mod.jar");
        TestWorldFixtures.createJar(
                sourceJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml"
                        loaderVersion="[47,)"
                        license="MIT"
                        [[mods]]
                        modId="examplemod"
                        version="1.0.0"
                        displayName="Example Mod"
                        authors="Dora"
                        description="Forge test mod"
                        """,
                        "META-INF/MANIFEST.MF",
                        """
                        Manifest-Version: 1.0
                        """
                )
        );

        ServerExtension installed = gestor.instalarExtensionManual(server, sourceJar);

        assertThat(Files.exists(forgeDir.resolve("mods").resolve("example-mod.jar"))).isTrue();
        assertThat(installed.getDisplayName()).isEqualTo("Example Mod");
        assertThat(installed.getExtensionType()).isEqualTo(ServerExtensionType.MOD);
        assertThat(installed.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(installed.getInstallState()).isEqualTo(ExtensionInstallState.INSTALLED);
        assertThat(installed.getSource().getType()).isEqualTo(ExtensionSourceType.MANUAL);
        assertThat(installed.getLocalMetadata().getRelativePath()).isEqualTo("mods/example-mod.jar");
        assertThat(installed.getLocalMetadata().getInstalledVersion()).isEqualTo("1.0.0");
        assertThat(installed.getLocalMetadata().getUpdateState()).isEqualTo(ExtensionUpdateState.UNTRACKED);
        assertThat(server.getExtensions()).hasSize(1);
        assertThat(server.getExtensions().getFirst().getLocalMetadata().getSha256()).isNotBlank();
    }

    @Test
    void importarModpackModrinthManteniendoMods_debeOmitirConflictosExistentes() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("existing.jar");
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-keep-existing");
        Path existingJar = Path.of(server.getServerDir()).resolve("mods").resolve("existing.jar");
        writeFabricModJar(existingJar, "existing");

        List<String> warnings = new ArrayList<>();
        int installed = gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.KEEP_EXISTING,
                warnings
        );

        assertThat(installed).isZero();
        assertThat(existingJar).isRegularFile();
        assertThat(modpackService.downloads).isZero();
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("ya existe"));
    }

    @Test
    void importarModpackModrinthReemplazandoMods_debeEliminarJarsExistentesEInstalarFaltantes() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("new.jar");
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-replace-existing");
        Path serverDir = Path.of(server.getServerDir());
        Path oldJar = serverDir.resolve("mods").resolve("old.jar");
        Path notes = serverDir.resolve("mods").resolve("notes.txt");
        Path nestedJar = serverDir.resolve("mods").resolve("nested").resolve("nested.jar");
        writeFabricModJar(oldJar, "old");
        Files.writeString(notes, "keep", StandardCharsets.UTF_8);
        writeFabricModJar(nestedJar, "nested");

        List<String> warnings = new ArrayList<>();
        int installed = gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING,
                warnings
        );

        assertThat(installed).isEqualTo(1);
        assertThat(oldJar).doesNotExist();
        assertThat(notes).isRegularFile();
        assertThat(nestedJar).isRegularFile();
        assertThat(serverDir.resolve("mods").resolve("new.jar")).isRegularFile();
        assertThat(modpackService.downloads).isEqualTo(1);
        assertThat(warnings).noneSatisfy(warning -> assertThat(warning).contains("dora-modpack-backups"));
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("Se han eliminado 1 mod actual"));
        assertThat(serverDir.resolve("dora-modpack-backups")).doesNotExist();
    }

    @Test
    void importarModpackModrinthReemplazandoMods_debeEliminarEInstalarModsAunqueYaExistieran() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("existing.jar");
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-replace-skip-existing");
        Path serverDir = Path.of(server.getServerDir());
        Path existingJar = serverDir.resolve("mods").resolve("existing.jar");
        writeFabricModJar(existingJar, "existing");

        List<String> warnings = new ArrayList<>();
        int installed = gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING,
                warnings
        );

        assertThat(installed).isEqualTo(1);
        assertThat(existingJar).isRegularFile();
        assertThat(modpackService.downloads).isEqualTo(1);
        assertThat(warnings).noneSatisfy(warning -> assertThat(warning).contains("ya existe"));
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("Se han eliminado 1 mod actual"));
        assertThat(serverDir.resolve("dora-modpack-backups")).doesNotExist();
    }

    @Test
    void importarModpackModrinthReemplazandoMods_debeRestaurarModsSiFallaLaDescarga() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("new.jar");
        modpackService.failDownloads = true;
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-replace-download-failure");
        Path serverDir = Path.of(server.getServerDir());
        Path oldJar = serverDir.resolve("mods").resolve("old.jar");
        Path otherJar = serverDir.resolve("mods").resolve("other.jar");
        writeFabricModJar(oldJar, "old");
        writeFabricModJar(otherJar, "other");
        byte[] oldBytes = Files.readAllBytes(oldJar);

        assertThatThrownBy(() -> gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING,
                new ArrayList<>()
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Fallo de red simulado")
                .hasMessageContaining("se han restaurado los mods");

        assertThat(oldJar).isRegularFile();
        assertThat(Files.readAllBytes(oldJar)).isEqualTo(oldBytes);
        assertThat(otherJar).isRegularFile();
        assertThat(serverDir.resolve("mods").resolve("new.jar")).doesNotExist();
        assertThat(serverDir.resolve(".dora-backup")).doesNotExist();
        assertThat(server.getExtensions()).extracting(extension -> extension.getFileName())
                .containsExactlyInAnyOrder("old.jar", "other.jar");
    }

    @Test
    void importarModpackModrinthReemplazandoMods_noDebeEliminarSiServidorEstaEncendido() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("new.jar");
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-replace-running-server");
        Path existingJar = Path.of(server.getServerDir()).resolve("mods").resolve("existing.jar");
        writeFabricModJar(existingJar, "existing");
        server.setServerProcess(new FakeProcess(true));

        assertThatThrownBy(() -> gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.REPLACE_EXISTING,
                new ArrayList<>()
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Detén el servidor");

        assertThat(existingJar).isRegularFile();
        assertThat(modpackService.downloads).isZero();
    }

    @Test
    void importarModpackModrinthConIndicePreleido_noDebeLeerElPackOtraVez() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("new.jar");
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-preloaded-index");
        ModrinthModpackService.ImportIndex index = modpackService.readIndex(tempDir.resolve("pack.mrpack"));
        modpackService.reads = 0;

        int installed = gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                index,
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.KEEP_EXISTING,
                new ArrayList<>()
        );

        assertThat(installed).isEqualTo(1);
        assertThat(modpackService.reads).isZero();
    }

    @Test
    void importarModpackModrinthConPreflightCompleto_noDebeResincronizarExtensiones() throws Exception {
        FakeModrinthModpackService modpackService = new FakeModrinthModpackService("existing.jar");
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile(), modpackService);
        Server server = modpackServer("modpack-synced-preflight");
        Path existingJar = Path.of(server.getServerDir()).resolve("mods").resolve("existing.jar");
        writeFabricModJar(existingJar, "existing");
        gestor.sincronizarExtensionesInstaladas(server);
        Files.delete(existingJar);
        ModrinthModpackService.ImportIndex index = modpackService.readIndex(tempDir.resolve("pack.mrpack"));

        List<String> warnings = new ArrayList<>();
        int installed = gestor.importarModpackModrinth(
                server,
                tempDir.resolve("pack.mrpack"),
                index,
                ModrinthModpackService.ImportOptions.server(),
                GestorServidores.ModpackImportConflictPolicy.KEEP_EXISTING,
                warnings,
                true
        );

        assertThat(installed).isZero();
        assertThat(modpackService.downloads).isZero();
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("ya existe"));
    }

    @Test
    void sincronizarExtensionesInstaladas_debeDetectarPluginsLocalesYPersistirMetadataCuandoSeSolicita() throws Exception {
        Path jsonPath = tempDir.resolve("ServerList.json");
        Path paperDir = tempDir.resolve("paper-with-plugins");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "server.jar",
                "{\"id\":\"1.21.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.createDirectories(paperDir.resolve("plugins"));
        TestWorldFixtures.createJar(
                paperDir.resolve("plugins").resolve("welcome-plugin.jar"),
                Map.of(
                        "plugin.yml",
                        """
                        name: WelcomePlugin
                        version: 2.5.1
                        author: Dora
                        description: Sends welcome messages
                        """
                )
        );

        Server server = new Server();
        server.setDisplayName("Paper");
        server.setServerDir(paperDir.toString());
        server.setPlatform(ServerPlatform.PAPER);

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(server));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Server loaded = gestor.getListaServidores().getFirst();

        assertThat(loaded.getExtensions()).isNotNull().isEmpty();

        List<ServerExtension> synced = gestor.sincronizarExtensionesInstaladas(loaded);

        assertThat(synced).hasSize(1);
        assertThat(loaded.getExtensions()).hasSize(1);
        ServerExtension detected = loaded.getExtensions().getFirst();
        assertThat(detected.getDisplayName()).isEqualTo("WelcomePlugin");
        assertThat(detected.getVersion()).isEqualTo("2.5.1");
        assertThat(detected.getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(detected.getPlatform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(detected.getInstallState()).isEqualTo(ExtensionInstallState.DISCOVERED);
        assertThat(detected.getSource().getType()).isEqualTo(ExtensionSourceType.LOCAL_FILE);
        assertThat(detected.getLocalMetadata().getRelativePath()).isEqualTo("plugins/welcome-plugin.jar");
        assertThat(detected.getLocalMetadata().getInstalledVersion()).isEqualTo("2.5.1");
        assertThat(detected.getLocalMetadata().getUpdateState()).isEqualTo(ExtensionUpdateState.UNTRACKED);

        List<Server> reloaded = new ObjectMapper().readValue(jsonPath.toFile(),
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(reloaded.getFirst().getExtensions()).hasSize(1);
        assertThat(reloaded.getFirst().getExtensions().getFirst().getPlatform()).isEqualTo(ServerPlatform.PAPER);
    }

    @Test
    void eliminarExtensionLocal_debeBorrarElJarYActualizarMetadata() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("forge-remove-extension");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setDisplayName("Forge Remove");
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);
        gestor.guardarServidor(server);

        Path sourceJar = tempDir.resolve("remove-mod.jar");
        TestWorldFixtures.createJar(
                sourceJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml"
                        loaderVersion="[47,)"
                        license="MIT"
                        [[mods]]
                        modId="removeexample"
                        version="1.0.0"
                        displayName="Remove Example"
                        authors="Dora"
                        description="Forge test mod"
                        """
                )
        );

        ServerExtension installed = gestor.instalarExtensionManual(server, sourceJar);

        boolean removed = gestor.eliminarExtensionLocal(server, installed);

        assertThat(removed).isTrue();
        assertThat(Files.exists(forgeDir.resolve("mods").resolve("remove-mod.jar"))).isFalse();
        assertThat(server.getExtensions()).isEmpty();
    }

    @Test
    void obtenerRepositoríosExtensiones_debeActivarModrinthYDejarCurseForgeOpcional() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());

        List<ExtensionCatalogProviderDescriptor> providers = gestor.obtenerRepositoríosExtensiones();

        assertThat(providers).extracting(ExtensionCatalogProviderDescriptor::providerId)
                .contains("modrinth", "curseforge", "hangar");
        assertThat(providers.stream()
                .filter(provider -> "modrinth".equals(provider.providerId()))
                .findFirst()
                .orElseThrow()
                .capabilities()).contains(controlador.extensions.ExtensionCatalogCapability.SEARCH);
        assertThat(providers.stream()
                .filter(provider -> "curseforge".equals(provider.providerId()))
                .findFirst()
                .orElseThrow()
                .capabilities()).isEmpty();
    }

    @Test
    void validarCompatibilidadExtension_debeMarcarPluginComoIncompatibleEnServidorForge() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("forge-compat");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);
        server.setVersion("1.20.1");

        Path pluginJar = tempDir.resolve("incompatible-plugin.jar");
        TestWorldFixtures.createJar(
                pluginJar,
                Map.of(
                        "plugin.yml",
                        """
                        name: PluginOnly
                        version: 1.0.0
                        author: Dora
                        api-version: 1.20
                        description: Plugin test
                        """
                )
        );

        ExtensionCompatibilityReport report = gestor.validarCompatibilidadExtension(server, pluginJar);

        assertThat(report.status()).isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThat(report.summary()).contains("Plugin");
    }

    @Test
    void instalarExtensionManual_debeRechazarVersionDeMinecraftIncompatible() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path paperDir = tempDir.resolve("paper-version-compat");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "server.jar",
                "{\"id\":\"1.20.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.createDirectories(paperDir.resolve("plugins"));

        Server server = new Server();
        server.setServerDir(paperDir.toString());
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.20.1");
        gestor.guardarServidor(server);

        Path pluginJar = tempDir.resolve("future-plugin.jar");
        TestWorldFixtures.createJar(
                pluginJar,
                Map.of(
                        "paper-plugin.yml",
                        """
                        name: FuturePlugin
                        version: 1.0.0
                        author: Dora
                        api-version: 1.21
                        description: Needs a newer server
                        """
                )
        );

        ExtensionCompatibilityReport report = gestor.validarCompatibilidadExtension(server, pluginJar);

        assertThat(report.status()).isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gestor.instalarExtensionManual(server, pluginJar))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("1.21");
    }

    @Test
    void instalarExtensionManual_debeRechazarModsConVersionInferidaIncompatible() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("forge-version-hint");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.21.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);
        server.setVersion("1.21.1");
        gestor.guardarServidor(server);

        Path modJar = tempDir.resolve("examplemod-1.17.1-2.0.0.jar");
        TestWorldFixtures.createJar(
                modJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader="javafml"
                        loaderVersion="[37,)"
                        license="MIT"
                        [[mods]]
                        modId="examplemod"
                        version="1.17.1-2.0.0"
                        displayName="Example Mod"
                        authors="Dora"
                        description="Legacy 1.17.1 build"
                        """
                )
        );

        ExtensionCompatibilityReport report = gestor.validarCompatibilidadExtension(server, modJar);

        assertThat(report.status()).isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThat(report.summary()).contains("1.17.1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gestor.instalarExtensionManual(server, modJar))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("1.17.1");
    }

    @Test
    void instalarExtensionManual_debeRechazarCasoRealFallingTree117EnServidor121() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path forgeDir = tempDir.resolve("forge-falling-tree");
        TestWorldFixtures.createValidServerJar(
                forgeDir,
                "server.jar",
                "{\"id\":\"1.21.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(forgeDir.resolve("mods"));
        Files.createDirectories(forgeDir.resolve("libraries"));

        Server server = new Server();
        server.setServerDir(forgeDir.toString());
        server.setPlatform(ServerPlatform.FORGE);
        server.setVersion("1.21.1");
        gestor.guardarServidor(server);

        Path modJar = tempDir.resolve("FallingTree-1.17.1-2.14.6.jar");
        TestWorldFixtures.createJar(
                modJar,
                Map.of(
                        "META-INF/mods.toml",
                        """
                        modLoader = "javafml"
                        loaderVersion = "[37,)"
                        authors = "RakSrinaNa"
                        license = "GPL-3.0"

                        [[mods]]
                        modId = "fallingtree"
                        version = "2.14.6"
                        displayName = "FallingTree"
                        description = '''Change the way you cut trees.'''

                        [[dependencies.fallingtree]]
                        modId = "forge"
                        mandatory = true
                        versionRange = "[37.0.0,)"
                        ordering = "NONE"
                        side = "BOTH"
                        [[dependencies.fallingtree]]
                        modId = "minecraft"
                        mandatory = true
                        versionRange = "[1.17.1]"
                        ordering = "NONE"
                        side = "BOTH"
                        """,
                        "fabric.mod.json",
                        """
                        {
                          "schemaVersion": 1,
                          "id": "fallingtree",
                          "version": "2.14.6",
                          "name": "FallingTree",
                          "description": "Change the way you cut trees."
                        }
                        """
                )
        );

        ExtensionCompatibilityReport report = gestor.validarCompatibilidadExtension(server, modJar);

        assertThat(report.status()).isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
        assertThat(report.summary()).contains("1.17.1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gestor.instalarExtensionManual(server, modJar))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("1.17.1");
    }

    @Test
    void puedeConvertirseAPlataformaCompatible_debeAceptarServidoresConPlataformaConvertible() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());

        Path vanillaDir = tempDir.resolve("convertible-vanilla");
        TestWorldFixtures.createValidServerJar(vanillaDir, "launch-anything.jar", "{\"id\":\"1.20.1\"}");
        Server vanilla = new Server();
        vanilla.setServerDir(vanillaDir.toString());
        vanilla.setPlatform(ServerPlatform.UNKNOWN);

        Path paperDir = tempDir.resolve("non-convertible-paper");
        TestWorldFixtures.createValidServerJar(
                paperDir,
                "runtime.jar",
                "{\"id\":\"1.20.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Server paper = new Server();
        paper.setServerDir(paperDir.toString());

        assertThat(gestor.puedeConvertirseAPlataformaCompatible(vanilla)).isTrue();
        assertThat(vanilla.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(gestor.puedeConvertirseAPlataformaCompatible(paper)).isTrue();
        assertThat(paper.getPlatform()).isEqualTo(ServerPlatform.PAPER);
    }

    @Test
    void construirAvisoConversion_debeMarcarCambioDeEcosistemaComoRiesgoso() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());

        ConversorPlataformaServidor.ConversionWarning warning = new ConversorPlataformaServidor(gestor).construirAvisoConversion(ServerPlatform.FORGE, ServerPlatform.PAPER);
        ConversorPlataformaServidor.ConversionWarning sameEcosystemWarning = new ConversorPlataformaServidor(gestor).construirAvisoConversion(ServerPlatform.SPIGOT, ServerPlatform.PAPER);

        assertThat(warning.crossEcosystem()).isTrue();
        assertThat(warning.message()).contains("ecosistemas distintos").contains("no se migrarán");
        assertThat(sameEcosystemWarning.crossEcosystem()).isFalse();
        assertThat(sameEcosystemWarning.message()).contains("plataforma de plugins");
    }

    @Test
    void convertirServidorExistente_debeCrearBackupYPreservarConfiguracionYMundo() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path serverDir = tempDir.resolve("vanilla-to-forge");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"1.20.1\"}");
        Files.createDirectories(serverDir.resolve("world"));
        Files.writeString(serverDir.resolve("world").resolve("level.dat"), "world-data");
        Properties props = new Properties();
        props.setProperty("motd", "Vanilla MOTD");
        props.setProperty("level-name", "world");
        TestWorldFixtures.writeServerProperties(serverDir, props);

        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setDisplayName("Convertible");
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.VANILLA);
        gestor.guardarServidor(server);

        ServerPlatformAdapter forgeInstaller = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.FORGE;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server transientServer, ServerInstallationRequest request) throws java.io.IOException {
                Files.deleteIfExists(request.targetDirectory().resolve("server.jar"));
                TestWorldFixtures.createValidServerJar(
                        request.targetDirectory(),
                        "runtime.jar",
                        "{\"id\":\"1.20.1\"}",
                        "net/minecraftforge/server/ServerMain.class"
                );
                Files.createDirectories(request.targetDirectory().resolve("libraries"));
                Files.createDirectories(request.targetDirectory().resolve("mods"));
                Files.writeString(request.targetDirectory().resolve("run.bat"), "@echo off");
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("runtime.jar");
            }
        };

        Server converted = new ConversorPlataformaServidor(gestor).convertirServidorExistente(
                server,
                forgeInstaller,
                new ServerCreationOption(ServerPlatform.FORGE, "1.20.1", "1.20.1-47.4.18", "Forge 1.20.1", "ignored"),
                (url, destination) -> {}
        );

        assertThat(converted).isNotNull();
        assertThat(converted.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(converted.getLoader()).isEqualTo(ServerLoader.FORGE);
        assertThat(converted.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(converted.getCapabilities()).contains(ServerCapability.MOD_EXTENSIONS);
        assertThat(converted.getLoaderVersion()).isEqualTo("1.20.1-47.4.18");
        assertThat(
                Files.exists(serverDir.resolve("world").resolve("level.dat"))
                        || Files.exists(serverDir.resolve(GestorMundos.DIRECTORIO_MUNDOS).resolve("world").resolve("level.dat"))
        ).isTrue();
        assertThat(Files.exists(serverDir.resolve("mods"))).isTrue();
        assertThat(Utilidades.leerMotdDesdeProperties(serverDir)).isEqualTo("Vanilla MOTD");

        try (var backups = Files.list(tempDir)) {
            assertThat(backups.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("backup_before_forge"))
                    .toList()).isNotEmpty();
        }
    }

    @Test
    void convertirServidorExistente_puedeOmitirBackupCompletoYPreservarDatos() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path serverDir = tempDir.resolve("vanilla-to-forge-no-backup");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"1.20.1\"}");
        Files.createDirectories(serverDir.resolve("world"));
        Files.writeString(serverDir.resolve("world").resolve("level.dat"), "world-data");
        Properties props = new Properties();
        props.setProperty("motd", "No Backup MOTD");
        props.setProperty("level-name", "world");
        TestWorldFixtures.writeServerProperties(serverDir, props);

        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setDisplayName("Convertible");
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.VANILLA);
        gestor.guardarServidor(server);

        ServerPlatformAdapter forgeInstaller = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.FORGE;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server transientServer, ServerInstallationRequest request) throws java.io.IOException {
                deleteDirectoryIfExists(request.targetDirectory().resolve("world"));
                Files.deleteIfExists(request.targetDirectory().resolve("server.properties"));
                TestWorldFixtures.createValidServerJar(
                        request.targetDirectory(),
                        "runtime.jar",
                        "{\"id\":\"1.20.1\"}",
                        "net/minecraftforge/server/ServerMain.class"
                );
                Files.createDirectories(request.targetDirectory().resolve("libraries"));
                Files.createDirectories(request.targetDirectory().resolve("mods"));
                Files.writeString(request.targetDirectory().resolve("run.bat"), "@echo off");
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("runtime.jar");
            }
        };

        Server converted = new ConversorPlataformaServidor(gestor).convertirServidorExistente(
                server,
                forgeInstaller,
                new ServerCreationOption(ServerPlatform.FORGE, "1.20.1", "1.20.1-47.4.18", "Forge 1.20.1", "ignored"),
                (url, destination) -> {},
                false
        );

        assertThat(converted).isNotNull();
        assertThat(converted.getPlatform()).isEqualTo(ServerPlatform.FORGE);
        assertThat(
                Files.exists(serverDir.resolve("world").resolve("level.dat"))
                        || Files.exists(serverDir.resolve(GestorMundos.DIRECTORIO_MUNDOS).resolve("world").resolve("level.dat"))
        ).isTrue();
        assertThat(Utilidades.leerMotdDesdeProperties(serverDir)).isEqualTo("No Backup MOTD");
        try (var backups = Files.list(tempDir)) {
            assertThat(backups.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("backup_before_forge"))
                    .toList()).isEmpty();
        }
    }

    @Test
    void convertirServidorExistente_deModsAPluginsDebePreservarModsYReiniciarCache() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path serverDir = tempDir.resolve("forge-to-paper");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "forge-runtime.jar",
                "{\"id\":\"1.20.1\"}",
                "net/minecraftforge/server/ServerMain.class"
        );
        Files.createDirectories(serverDir.resolve("libraries"));
        Files.createDirectories(serverDir.resolve("mods"));
        TestWorldFixtures.createJar(
                serverDir.resolve("mods").resolve("example-mod.jar"),
                Map.of("mods.toml", "modLoader=\"javafml\"\nmodId=\"example\"")
        );

        ServerExtension installedMod = new ServerExtension();
        installedMod.setDisplayName("Example Mod");
        installedMod.setFileName("example-mod.jar");
        installedMod.setExtensionType(ServerExtensionType.MOD);
        installedMod.setPlatform(ServerPlatform.FORGE);
        installedMod.setInstallState(ExtensionInstallState.INSTALLED);
        modelo.extensions.ExtensionLocalMetadata installedModMetadata = new modelo.extensions.ExtensionLocalMetadata();
        installedModMetadata.setRelativePath("mods/example-mod.jar");
        installedModMetadata.setFileName("example-mod.jar");
        installedMod.setLocalMetadata(installedModMetadata);

        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setDisplayName("Forge Server");
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.FORGE);
        server.setExtensions(List.of(installedMod));
        gestor.guardarServidor(server);

        ServerPlatformAdapter paperInstaller = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.PAPER;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server transientServer, ServerInstallationRequest request) throws java.io.IOException {
                deleteDirectoryIfExists(request.targetDirectory().resolve("mods"));
                TestWorldFixtures.createValidServerJar(
                        request.targetDirectory(),
                        "paper-runtime.jar",
                        "{\"id\":\"1.20.1\"}",
                        "io/papermc/paper/PaperBootstrap.class",
                        "org/bukkit/craftbukkit/Main.class"
                );
                Files.createDirectories(request.targetDirectory().resolve("plugins"));
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("paper-runtime.jar");
            }

            @Override
            public List<Path> getExtensionDirectories(Path serverDir) {
                return List.of(serverDir.resolve("plugins"));
            }
        };

        Server converted = new ConversorPlataformaServidor(gestor).convertirServidorExistente(
                server,
                paperInstaller,
                new ServerCreationOption(ServerPlatform.PAPER, "1.20.1", "42", "Paper 1.20.1", "ignored"),
                (url, destination) -> {}
        );

        assertThat(converted).isNotNull();
        assertThat(converted.getPlatform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(converted.getEcosystemType()).isEqualTo(ServerEcosystemType.PLUGINS);
        assertThat(converted.getCapabilities()).contains(ServerCapability.PLUGIN_EXTENSIONS);
        assertThat(Files.exists(serverDir.resolve("plugins"))).isTrue();
        assertThat(Files.exists(serverDir.resolve("mods").resolve("example-mod.jar"))).isTrue();
        assertThat(converted.getExtensions())
                .anySatisfy(extension -> {
                    assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.MOD);
                    assertThat(extension.getLocalMetadata().getRelativePath()).isEqualTo("mods/example-mod.jar");
                    assertThat(gestor.evaluarEstadoExtensionInstalada(converted, extension).severity())
                            .isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
                });
        assertThat(Files.readString(serverDir.resolve("dora-extensions.json"))).contains("example-mod.jar");

        try (var backups = Files.list(tempDir)) {
            assertThat(backups.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("backup_before_paper"))
                    .toList()).isNotEmpty();
        }
    }

    @Test
    void convertirServidorExistente_dePluginsAModsDebePreservarPluginsYReiniciarCache() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path serverDir = tempDir.resolve("paper-to-fabric");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "paper-runtime.jar",
                "{\"id\":\"1.20.1\"}",
                "io/papermc/paper/PaperBootstrap.class",
                "org/bukkit/craftbukkit/Main.class"
        );
        Files.createDirectories(serverDir.resolve("plugins"));
        TestWorldFixtures.createJar(
                serverDir.resolve("plugins").resolve("welcome-plugin.jar"),
                Map.of("plugin.yml", """
                        name: WelcomePlugin
                        main: test.WelcomePlugin
                        version: 1.0.0
                        api-version: '1.20'
                        """)
        );

        ServerExtension installedPlugin = new ServerExtension();
        installedPlugin.setDisplayName("Welcome Plugin");
        installedPlugin.setFileName("welcome-plugin.jar");
        installedPlugin.setExtensionType(ServerExtensionType.PLUGIN);
        installedPlugin.setPlatform(ServerPlatform.PAPER);
        installedPlugin.setInstallState(ExtensionInstallState.INSTALLED);
        modelo.extensions.ExtensionLocalMetadata installedPluginMetadata = new modelo.extensions.ExtensionLocalMetadata();
        installedPluginMetadata.setRelativePath("plugins/welcome-plugin.jar");
        installedPluginMetadata.setFileName("welcome-plugin.jar");
        installedPlugin.setLocalMetadata(installedPluginMetadata);

        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setDisplayName("Paper Server");
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.PAPER);
        server.setExtensions(List.of(installedPlugin));
        gestor.guardarServidor(server);

        ServerPlatformAdapter fabricInstaller = new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return ServerPlatform.FABRIC;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server transientServer, ServerInstallationRequest request) throws java.io.IOException {
                deleteDirectoryIfExists(request.targetDirectory().resolve("plugins"));
                TestWorldFixtures.createValidServerJar(
                        request.targetDirectory(),
                        "fabric-runtime.jar",
                        "{\"id\":\"1.20.1\"}",
                        "net/fabricmc/loader/impl/launch/server/FabricServerLauncher.class"
                );
                Files.createDirectories(request.targetDirectory().resolve("mods"));
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("fabric-runtime.jar");
            }

            @Override
            public List<Path> getExtensionDirectories(Path serverDir) {
                return List.of(serverDir.resolve("mods"));
            }
        };

        Server converted = new ConversorPlataformaServidor(gestor).convertirServidorExistente(
                server,
                fabricInstaller,
                new ServerCreationOption(ServerPlatform.FABRIC, "1.20.1", "0.15.11", "Fabric 1.20.1", "ignored"),
                (url, destination) -> {}
        );

        assertThat(converted).isNotNull();
        assertThat(converted.getPlatform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(converted.getEcosystemType()).isEqualTo(ServerEcosystemType.MODS);
        assertThat(converted.getCapabilities()).contains(ServerCapability.MOD_EXTENSIONS);
        assertThat(Files.exists(serverDir.resolve("mods"))).isTrue();
        assertThat(Files.exists(serverDir.resolve("plugins").resolve("welcome-plugin.jar"))).isTrue();
        assertThat(converted.getExtensions())
                .anySatisfy(extension -> {
                    assertThat(extension.getExtensionType()).isEqualTo(ServerExtensionType.PLUGIN);
                    assertThat(extension.getLocalMetadata().getRelativePath()).isEqualTo("plugins/welcome-plugin.jar");
                    assertThat(gestor.evaluarEstadoExtensionInstalada(converted, extension).severity())
                            .isEqualTo(ExtensionCompatibilityStatus.INCOMPATIBLE);
                });
        assertThat(Files.readString(serverDir.resolve("dora-extensions.json"))).contains("welcome-plugin.jar");

        try (var backups = Files.list(tempDir)) {
            assertThat(backups.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("backup_before_fabric"))
                    .toList()).isNotEmpty();
        }
    }

    @Test
    void convertirServidorExistente_debeRechazarAdaptadorNoAutomatizableSinBackup() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("ServerList.json").toFile());
        Path serverDir = tempDir.resolve("vanilla-to-spigot");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"1.20.1\"}");
        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.VANILLA);

        Server converted = new ConversorPlataformaServidor(gestor).convertirServidorExistente(
                server,
                ServerPlatformAdapters.forPlatform(ServerPlatform.SPIGOT),
                new ServerCreationOption(ServerPlatform.SPIGOT, "1.20.1", "latest", "Spigot 1.20.1", "ignored"),
                (url, destination) -> {}
        );

        assertThat(converted).isNull();
        try (var backups = Files.list(tempDir)) {
            assertThat(backups.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("backup_before_spigot"))
                    .toList()).isEmpty();
        }
    }

    @Test
    void establecerFavoritoYReordenarServidores_debenMantenerOrdenVisualEstable() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Server alpha = new Server();
        alpha.setDisplayName("Alpha");
        Server beta = new Server();
        beta.setDisplayName("Beta");
        Server gamma = new Server();
        gamma.setDisplayName("Gamma");
        gestor.guardarServidor(alpha);
        gestor.guardarServidor(beta);
        gestor.guardarServidor(gamma);

        gestor.establecerFavorito(beta, true);
        gestor.reordenarServidores(List.of(gamma.getId(), alpha.getId(), beta.getId()));

        List<Server> ordered = gestor.getListaServidores();
        assertThat(ordered.getFirst().getId()).isEqualTo(beta.getId());
        assertThat(ordered).extracting(Server::getId).containsExactly(beta.getId(), gamma.getId(), alpha.getId());
    }

    @Test
    void reordenarServidores_debePersistirOrdenManualDeFavoritos() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Server alpha = crearServidorPersistible("Alpha", tempDir.resolve("alpha"));
        Server beta = crearServidorPersistible("Beta", tempDir.resolve("beta"));
        Server gamma = crearServidorPersistible("Gamma", tempDir.resolve("gamma"));
        gestor.guardarServidor(alpha);
        gestor.guardarServidor(beta);
        gestor.guardarServidor(gamma);

        gestor.establecerFavorito(alpha, true);
        gestor.establecerFavorito(beta, true);
        gestor.reordenarServidores(List.of(beta.getId(), alpha.getId(), gamma.getId()));

        GestorServidores recargado = new GestorServidores(jsonPath.toFile());

        assertThat(recargado.getListaServidores())
                .extracting(Server::getId)
                .containsExactly(beta.getId(), alpha.getId(), gamma.getId());
        assertThat(recargado.getServerById(beta.getId()).getOrdenFavorito())
                .isLessThan(recargado.getServerById(alpha.getId()).getOrdenFavorito());
    }

    @Test
    void reordenarServidores_debeMantenerOrdenBaseDeNoFavoritosConFavoritosOrdenados() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Server alpha = crearServidorPersistible("Alpha", tempDir.resolve("alpha"));
        Server beta = crearServidorPersistible("Beta", tempDir.resolve("beta"));
        Server gamma = crearServidorPersistible("Gamma", tempDir.resolve("gamma"));
        Server delta = crearServidorPersistible("Delta", tempDir.resolve("delta"));
        gestor.guardarServidor(alpha);
        gestor.guardarServidor(beta);
        gestor.guardarServidor(gamma);
        gestor.guardarServidor(delta);

        gestor.establecerFavorito(alpha, true);
        gestor.establecerFavorito(beta, true);
        gestor.reordenarServidores(List.of(beta.getId(), alpha.getId(), delta.getId(), gamma.getId()));

        GestorServidores recargado = new GestorServidores(jsonPath.toFile());

        assertThat(recargado.getListaServidores())
                .extracting(Server::getId)
                .containsExactly(beta.getId(), alpha.getId(), delta.getId(), gamma.getId());
    }

    @Test
    void refrescarServidoresGuardados_debeOcultarCarpetasBorradasSinPerderlasYLimpiarSeleccion() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Path staleDir = tempDir.resolve("stale");
        Server stale = crearServidorPersistible("Stale", staleDir);
        Server valid = crearServidorPersistible("Valid", tempDir.resolve("valid"));
        gestor.guardarServidor(stale);
        gestor.guardarServidor(valid);
        gestor.setServidorSeleccionado(stale);

        deleteDirectoryIfExists(staleDir);
        gestor.refrescarServidoresGuardados();

        assertThat(gestor.getListaServidores()).extracting(Server::getId).containsExactly(valid.getId());
        assertThat(gestor.getServidoresNoDisponibles()).extracting(Server::getId).containsExactly(stale.getId());
        assertThat(gestor.getServidorSeleccionado()).isNull();
        assertThat(gestor.getAvisoServidoresNoCargados())
                .contains("No se han podido cargar 1 servidores")
                .contains("se conservan");

        // Sigue oculto al recargar, pero no se ha borrado del JSON.
        GestorServidores recargado = new GestorServidores(jsonPath.toFile());
        assertThat(recargado.getListaServidores()).extracting(Server::getId).containsExactly(valid.getId());
        assertThat(recargado.getServidoresNoDisponibles()).extracting(Server::getId).containsExactly(stale.getId());
        assertThat(Files.readString(jsonPath)).contains(stale.getId());
    }

    @Test
    void servidorNoDisponible_debeVolverConSuConfiguracionCuandoLaCarpetaReaparece() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Path externalDir = tempDir.resolve("external-drive").resolve("server");
        Path parkedDir = tempDir.resolve("unplugged");
        Server external = crearServidorPersistible("External", externalDir);
        external.getServerConfig().setRamMax(6144);
        external.setFavorito(true);
        gestor.guardarServidor(external);

        // Simulamos un disco externo desconectado y un guardado posterior de otro servidor.
        Files.move(externalDir, parkedDir);
        GestorServidores sinDisco = new GestorServidores(jsonPath.toFile());
        assertThat(sinDisco.getListaServidores()).isEmpty();
        sinDisco.guardarServidor(crearServidorPersistible("Other", tempDir.resolve("other")));

        Files.move(parkedDir, externalDir);
        GestorServidores conDisco = new GestorServidores(jsonPath.toFile());

        assertThat(conDisco.getServidoresNoDisponibles()).isEmpty();
        Server recuperado = conDisco.getServerById(external.getId());
        assertThat(recuperado).isNotNull();
        assertThat(recuperado.getServerConfig().getRamMax()).isEqualTo(6144);
        assertThat(recuperado.getFavorito()).isTrue();
    }

    @Test
    void olvidarServidorNoDisponible_debeQuitarloDelJson() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        Path staleDir = tempDir.resolve("stale");
        Server stale = crearServidorPersistible("Stale", staleDir);
        gestor.guardarServidor(stale);
        deleteDirectoryIfExists(staleDir);
        gestor.refrescarServidoresGuardados();

        assertThat(gestor.olvidarServidorNoDisponible(stale.getId())).isTrue();

        assertThat(gestor.getServidoresNoDisponibles()).isEmpty();
        assertThat(Files.readString(jsonPath)).doesNotContain(stale.getId());
    }

    @Test
    void constructor_debeOcultarServidoresPersistidosNoCargablesSinBorrarlos() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        Path validDir = tempDir.resolve("valid-server");
        Path invalidDir = tempDir.resolve("invalid-server");
        Files.createDirectories(invalidDir);
        TestWorldFixtures.createValidServerJar(validDir, "server.jar");

        Server valid = new Server();
        valid.setDisplayName("Valid");
        valid.setServerDir(validDir.toString());

        Server invalid = new Server();
        invalid.setDisplayName("Invalid");
        invalid.setServerDir(invalidDir.toString());

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), List.of(valid, invalid));

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());

        assertThat(gestor.getListaServidores()).extracting(Server::getDisplayName).containsExactly("Valid");
        assertThat(gestor.getServidoresNoDisponibles()).extracting(Server::getDisplayName).containsExactly("Invalid");
        assertThat(gestor.getAvisoServidoresNoCargados()).contains("No se han podido cargar 1 servidores");
        assertThat(Files.readString(jsonPath)).contains(invalid.getId());
    }

    @Test
    void constructor_debeRespaldarListaIlegibleAntesDeSobrescribirla() throws Exception {
        Path jsonPath = tempDir.resolve("dora-server-list.json");
        String contenidoRoto = "[{\"id\":\"abc\",\"displayName\":\"Mi servidor\"";
        Files.writeString(jsonPath, contenidoRoto, StandardCharsets.UTF_8);

        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        gestor.guardarServidor(crearServidorPersistible("Nuevo", tempDir.resolve("nuevo")));

        assertThat(gestor.getAvisoListaServidoresIlegible()).contains("copia");
        List<Path> copias;
        try (var files = Files.list(tempDir)) {
            copias = files.filter(path -> path.getFileName().toString().startsWith("dora-server-list.json.corrupt-")).toList();
        }
        assertThat(copias).hasSize(1);
        assertThat(Files.readString(copias.getFirst(), StandardCharsets.UTF_8)).isEqualTo(contenidoRoto);
    }

    @Test
    void guardarServidores_noDebeDejarArchivosTemporales() throws Exception {
        Path configDir = tempDir.resolve("config");
        Path jsonPath = configDir.resolve("dora-server-list.json");
        GestorServidores gestor = new GestorServidores(jsonPath.toFile());
        gestor.guardarServidor(crearServidorPersistible("Uno", tempDir.resolve("uno")));
        gestor.guardarServidor(crearServidorPersistible("Dos", tempDir.resolve("dos")));

        try (var files = Files.list(configDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("dora-server-list.json");
        }
        assertThat(new GestorServidores(jsonPath.toFile()).getListaServidores()).hasSize(2);
    }

    @Test
    void importarServidorNoVanillaSinVersionDetectada_debePersistirloYUsarNombreDeCarpeta() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("paper-import");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "paper-server.jar",
                "{\"id\":\"git-Paper-123\"}",
                "io/papermc/paperclip/Paperclip.class"
        );
        Files.writeString(serverDir.resolve("paper.yml"), "settings: {}\n");

        Server imported = gestor.importarServidorDesdeDirectorio(serverDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(modelo.extensions.ServerPlatform.PAPER);
        assertThat(imported.getVersion()).isNull();
        assertThat(imported.getDisplayName()).isEqualTo("paper-import");
        assertThat(gestor.getListaServidores()).extracting(Server::getId).contains(imported.getId());
    }

    @Test
    void importarServidorDebeEvitarDuplicadosConRutasNormalizadas() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("paper-duplicate");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "paper-1.20.4.jar",
                "{\"id\":\"git-Paper-123\"}",
                "io/papermc/paperclip/Paperclip.class"
        );
        Files.writeString(serverDir.resolve("paper.yml"), "settings: {}\n");

        Server first = gestor.importarServidorDesdeDirectorio(serverDir);
        Server second = gestor.importarServidorDesdeDirectorio(serverDir.resolve("."));

        assertThat(second).isSameAs(first);
        assertThat(gestor.getListaServidores()).hasSize(1);
    }

    @Test
    void importarServidorDebeDetectarVersionAntiguaDesdeMetadatos() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("forge-legacy");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "forge-server.jar",
                "{\"id\":\"forge\"}",
                "net/minecraftforge/common/MinecraftForge.class"
        );
        Files.createDirectories(serverDir.resolve("mods"));
        Files.createDirectories(serverDir.resolve("libraries"));
        Files.writeString(serverDir.resolve("version_history.json"), "{\"minecraftVersion\":\"1.7.10\"}\n");

        Server imported = gestor.importarServidorDesdeDirectorio(serverDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(modelo.extensions.ServerPlatform.FORGE);
        assertThat(imported.getVersion()).isEqualTo("1.7.10");
    }

    @Test
    void importarServidorDebeAceptarVersionSemanticaFuturaDesdeVersionJson() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("future-vanilla");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"26.1.2\",\"name\":\"26.1.2\"}");

        Server imported = gestor.importarServidorDesdeDirectorio(serverDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(modelo.extensions.ServerPlatform.VANILLA);
        assertThat(imported.getVersion()).isEqualTo("26.1.2");
    }

    @Test
    void importarServidorDebeAceptarVersionSemanticaFuturaDesdeJarLauncherFabric() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("future-fabric");
        Files.createDirectories(serverDir.resolve("mods"));
        TestWorldFixtures.createJar(
                serverDir.resolve("fabric-server-mc.26.1.2-loader.0.19.2-launcher.1.1.1.jar"),
                Map.of(
                        "META-INF/MANIFEST.MF",
                        """
                        Manifest-Version: 1.0
                        Implementation-Title: FabricInstaller
                        Main-Class: net.fabricmc.installer.ServerLauncher
                        """
                ),
                "net/fabricmc/installer/ServerLauncher.class"
        );

        Server imported = gestor.importarServidorDesdeDirectorio(serverDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(modelo.extensions.ServerPlatform.FABRIC);
        assertThat(imported.getVersion()).isEqualTo("26.1.2");
    }

    @Test
    void importarServidorDebeAceptarSnapshotSemanticoFuturoDesdeVersionJson() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("future-snapshot-vanilla");
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"26.2-snapshot-7\",\"name\":\"26.2-snapshot-7\"}");

        Server imported = gestor.importarServidorDesdeDirectorio(serverDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(modelo.extensions.ServerPlatform.VANILLA);
        assertThat(imported.getVersion()).isEqualTo("26.2-snapshot-7");
    }

    @Test
    void importarServidorDebePreferirIdSnapshotSobreNombreReleaseDesdeVersionJson() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("future-snapshot-vanilla-release-name");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "server.jar",
                "{\"id\":\"26.2-snapshot-7\",\"name\":\"26.2\"}"
        );

        Server imported = gestor.importarServidorDesdeDirectorio(serverDir);

        assertThat(imported).isNotNull();
        assertThat(imported.getPlatform()).isEqualTo(modelo.extensions.ServerPlatform.VANILLA);
        assertThat(imported.getVersion()).isEqualTo("26.2-snapshot-7");
        assertThat(imported.getVersion()).isNotEqualTo("26.2");
    }

    @Test
    void resolverOrigenConversionDebeActualizarSnapshotTruncadoDesdeJar() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Path serverDir = tempDir.resolve("existing-snapshot-vanilla-release-name");
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "server.jar",
                "{\"id\":\"26.2-snapshot-7\",\"name\":\"26.2\"}"
        );
        Server server = new Server();
        server.setServerDir(serverDir.toString());
        server.setPlatform(modelo.extensions.ServerPlatform.VANILLA);
        server.setVersion("26.2");
        Method resolverOrigenConversion = ConversorPlataformaServidor.class.getDeclaredMethod(
                "resolverOrigenConversion",
                Server.class
        );
        resolverOrigenConversion.setAccessible(true);

        Object source = resolverOrigenConversion.invoke(new ConversorPlataformaServidor(gestor), server);

        assertThat(source).isNotNull();
        assertThat(server.getVersion()).isEqualTo("26.2-snapshot-7");
    }

    @Test
    void eliminarServidor_debeFallarSiProcesoSigueVivo() {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        Server server = new Server();
        server.setDisplayName("Busy");
        gestor.guardarServidor(server);
        server.setServerProcess(new FakeProcess(true));

        boolean deleted = gestor.eliminarServidor(server);

        assertThat(deleted).isFalse();
        assertThat(gestor.getListaServidores()).extracting(Server::getId).contains(server.getId());
    }

    @Test
    void iniciarServidor_noReutilizaElPuertoDeOtroServidorQueSigueArrancando() throws Exception {
        GestorServidores gestor = new GestorServidores(tempDir.resolve("dora-server-list.json").toFile());
        int puerto;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            puerto = socket.getLocalPort();
        }
        // El otro servidor ya está lanzado pero todavía no ha abierto el puerto: el puerto parece libre.
        Server arrancando = modpackServer("start-port-other");
        arrancando.setServerConfig(new modelo.ServerConfig(1024, 2048, puerto));
        gestor.guardarServidor(arrancando);
        arrancando.setServerProcess(new FakeProcess(true));

        Server nuevo = modpackServer("start-port-reserved");
        nuevo.setServerConfig(new modelo.ServerConfig(1024, 2048, puerto));
        Files.writeString(Path.of(nuevo.getServerDir()).resolve("server.properties"), "server-port=" + puerto + "\n");
        gestor.guardarServidor(nuevo);

        gestor.iniciarServidor(nuevo);

        // Sin interfaz no se puede elegir otro puerto, así que el arranque se cancela en vez de chocar.
        assertThat(nuevo.getServerProcess()).isNull();
        assertThat(nuevo.getConsoleText()).contains("Inicio cancelado por selección de puerto.");
    }

    private Server modpackServer(String name) throws Exception {
        Path serverDir = tempDir.resolve(name);
        TestWorldFixtures.createValidServerJar(
                serverDir,
                "server.jar",
                "{\"id\":\"1.21.1\"}",
                "net/fabricmc/loader/impl/launch/server/FabricServerLauncher.class"
        );
        Files.createDirectories(serverDir.resolve("mods"));
        Server server = new Server();
        server.setDisplayName(name);
        server.setServerDir(serverDir.toString());
        server.setPlatform(ServerPlatform.FABRIC);
        server.setLoader(ServerLoader.FABRIC);
        server.setVersion("1.21.1");
        server.setEcosystemType(ServerEcosystemType.MODS);
        return server;
    }

    private static void writeFabricModJar(Path target, String id) throws Exception {
        TestWorldFixtures.createJar(
                target,
                Map.of("fabric.mod.json", """
                        {
                          "schemaVersion": 1,
                          "id": "%s",
                          "name": "%s",
                          "version": "1.0.0",
                          "depends": { "minecraft": "1.21.1" }
                        }
                        """.formatted(id, id))
        );
    }

    private static ServerPlatformAdapter adapterConversionConOpciones(ServerPlatform platform,
                                                                      List<ServerCreationOption> options) {
        return new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return platform;
            }

            @Override
            public boolean supportsAutomatedCreation() {
                return true;
            }

            @Override
            public List<ServerCreationOption> listCreationOptions() {
                return options;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server server, ServerInstallationRequest request) {
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("server.jar");
            }
        };
    }

    private static final class FakeModrinthModpackService extends ModrinthModpackService {
        private final String fileName;
        private int reads;
        private int downloads;
        private boolean failDownloads;

        private FakeModrinthModpackService(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public ImportIndex readIndex(Path sourcePack) {
            reads++;
            return new ImportIndex(
                    "Pack de prueba",
                    "test-pack",
                    "Pack local",
                    Map.of("minecraft", "1.21.1"),
                    List.of(new IndexedFile(
                            "mods/" + fileName,
                            Map.of("sha1", "0123456789012345678901234567890123456789"),
                            new Env("optional", "required"),
                            List.of("https://cdn.modrinth.com/data/project/versions/version/" + fileName),
                            1L
                    ))
            );
        }

        @Override
        public ExtensionDownloadPlan resolveImportDownloadPlan(IndexedFile file, Server server) {
            return new ExtensionDownloadPlan(
                    "modrinth",
                    "project",
                    "version",
                    "1.0.0",
                    null,
                    fileName,
                    "https://cdn.modrinth.com/data/project/versions/version/" + fileName,
                    ExtensionSourceType.MODRINTH,
                    ServerExtensionType.MOD,
                    ServerPlatform.FABRIC,
                    "1.21.1",
                    true,
                    "Ready"
            );
        }

        @Override
        public void downloadAndVerify(IndexedFile file,
                                      String requestedUrl,
                                      File destination,
                                      controlador.platform.FileDownloader downloader) throws java.io.IOException {
            downloads++;
            if (failDownloads) {
                throw new java.io.IOException("Fallo de red simulado");
            }
            try {
                writeFabricModJar(destination.toPath(), fileName.replace(".jar", ""));
            } catch (Exception ex) {
                throw new java.io.IOException(ex);
            }
        }

        @Override
        public int extractOverrides(Path sourcePack, Server server, ImportOptions options, List<String> warnings) {
            return 0;
        }
    }

    private static void deleteDirectoryIfExists(Path directory) throws java.io.IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Server crearServidorPersistible(String displayName, Path serverDir) throws IOException {
        TestWorldFixtures.createValidServerJar(serverDir, "server.jar", "{\"id\":\"1.20.1\"}");
        Server server = new Server();
        server.setDisplayName(displayName);
        server.setServerDir(serverDir.toString());
        server.setVersion("1.20.1");
        server.setPlatform(ServerPlatform.VANILLA);
        return server;
    }

    private static final class FakeProcess extends Process {
        private final boolean alive;

        private FakeProcess(boolean alive) {
            this.alive = alive;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
