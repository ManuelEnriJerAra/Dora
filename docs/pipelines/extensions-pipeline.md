# Extensions Pipeline

Use this guide before editing mods/plugins listing, manual install, catalog search, dependency resolution, updates, modpack import/export, or extension metadata parsing.

## Core Packages

Controller services:

- `controlador.extensions.ServerExtensionsService`
- `controlador.extensions.ExtensionCatalogService`
- `controlador.extensions.ExtensionCatalogRegistry`
- `controlador.extensions.ModrinthExtensionCatalogProvider`
- `controlador.extensions.HangarExtensionCatalogProvider`
- `controlador.extensions.CurseForgeModpackService`
- `controlador.extensions.ModrinthModpackService`
- `controlador.extensions.InstalledExtensionsCacheService`
- `controlador.extensions.ExtensionArtifactDownloader`

UI:

- `vista.PanelExtensiones`
- `vista.ExtensionMarketplaceDialog`
- `vista.ExtensionDescriptionRenderer`
- `vista.ExtensionDetailsLayout`
- `vista.ExtensionIconLoader`
- `vista.ExtensionStatusPresentation`
- `vista.MarketplaceEntryViewModel`
- `vista.ProcessWizardDialog`

Models:

- `modelo.extensions.*`

## Managed Directories

Extension directories come from the current server platform adapter.

Typical locations:

- plugins for Bukkit/Paper/Purpur/Pufferfish/Spigot style servers
- mods for Forge/NeoForge/Fabric/Quilt style servers

Do not assume a hard-coded directory without checking platform capabilities.

## Installed Extension Detection

`ServerExtensionsService.detectInstalledExtensions(...)` scans managed directories and reads jar metadata.

Metadata sources include:

- `plugin.yml`
- `paper-plugin.yml`
- `fabric.mod.json`
- `quilt.mod.json`
- `META-INF/mods.toml`
- manifests and inferred metadata

The service merges detected metadata with persisted cache data so catalog origin/update data survives rescans.

Do not synchronize every server's extensions during application startup. Keep extension scans lazy for the selected server or the specific workflow that needs them. During UI reloads, batch status/dependency evaluation instead of recalculating server profile and dependency presence per row. Cache persistence should compare stable extension content separately from write timestamps so unchanged syncs do not rewrite cache files.

Do not let extension display metadata keep live handles to installed jars. Embedded icon references may use `jar:file:` URLs, but icon loading must disable URL caches and eagerly decode the image into memory before UI rendering so users can delete or rename stopped-server jars while the app is open.

## Manual Install Pipeline

1. User selects a jar in `PanelExtensiones`.
2. `ServerExtensionsService.validateCompatibility(...)` checks ecosystem/platform/version.
3. `installManualJar(...)` copies the jar into the managed extension directory.
4. Metadata/cache is updated.
5. UI refreshes installed extensions.

Avoid installing plugins into mod directories or mods into plugin directories unless compatibility rules explicitly allow it.

## Catalog Pipeline

`ExtensionMarketplaceDialog` performs catalog search and install.

Flow:

1. Build `ExtensionCatalogQuery`.
2. Search the selected provider through `ExtensionCatalogService`.
3. Load details/versions.
4. Evaluate compatibility and dependencies.
5. Build `ExtensionDownloadPlan`.
6. Queue or install downloads.
7. Persist installed metadata/cache.

Providers:

- Modrinth
- Hangar
- CurseForge currently has stub/provider support plus modpack service behavior.

Marketplace search is provider-scoped from the UI. The provider selector does not offer an "all providers" mode because cross-provider aggregation can show the same plugin twice and can mix dependency identities from different catalogs. Hangar is the default provider when available because its plugin dependency metadata is usually better; users can switch to Modrinth for its broader catalog.

Search-row compatibility can be deliberately incomplete. Modrinth project search rows may omit full platform/version compatibility sets, but filtered searches should preserve the platform and Minecraft version that matched the active query so rows do not degrade into blanket warning or blank states. Default marketplace lists should hide only clearly incompatible rows; do not hide warning rows solely because details have not been fetched yet, or valid mod lists can appear empty after provider-side filters already matched the server. Reserve warning styling and "Revisar" copy for real compatibility caveats, while details/install resolution still verify the exact downloadable build.

## Dependency Resolution

Dependency prompts and queue logic live mostly in `ExtensionMarketplaceDialog`, while installation compatibility is enforced by `ServerExtensionsService`.

Keep UI dependency decisions and service-level validation consistent. Service validation is the safety net.

Dependency identity matching is shared through `controlador.extensions.ExtensionDependencyMatcher`. Use it for marketplace queue checks and installed-tab diagnostics so provider/project IDs, normalized names, file names, local IDs, local dependency descriptions, and embedded dependency metadata stay aligned. After catalog installs, preserve catalog dependency identities when rescanning jars that also declare local descriptor dependency IDs.

Adding an extension to the marketplace queue should acknowledge the click immediately. Long dependency and optional-complement resolution runs in the background while the queue shows a removable `RESOLVING` item; once resolution finishes, that same item becomes pending or failed so users do not need to click the add action again.

Marketplace row actions should tolerate normal hand movement between mouse press and release. Prefer press/release handling with a small movement threshold for result-row and queue-row action zones instead of relying on exact `mouseClicked` events.

Dependency warnings must not block marketplace downloads. If a required dependency cannot be resolved automatically, has failed earlier in the queue, or is installed after the dependent plugin, keep the selected plugin downloadable/installable and show a warning so the user can fix dependency order manually if the server rejects it later.

Hangar dependency metadata may provide a display-style project identifier instead of the canonical numeric id or namespace slug, for example `NBT+API` while the project is `tr7zw/NBTAPI`. When resolving Hangar dependency downloads, non-numeric simple identifiers should try a small provider-scoped project search before direct lookup; other direct lookup failures should fall back to the same search. Only accept exact normalized matches by id, name, slug, or owner/slug before fetching versions. If search completes without an exact match, treat the dependency as unresolved instead of logging a provider download failure.

Persisted installed-extension metadata may come from older cache entries or local jar detection, so nullable boxed fields such as download counts must be compared with explicit null handling before writing catalog details back into the installed cache.

Marketplace dialog option records, queue/dependency helper models, and related view-state types may live beside the dialog in package-scoped helper files when extracted for size control. Keep the dialog focused on orchestration and rendering, and keep extracted types behavior-neutral unless a broader redesign is intentional.

## Modpack Import/Export

`ModrinthModpackService` is the practical modpack path for mod servers. It writes and reads Modrinth `.mrpack` archives with `modrinth.index.json`, validates ZIP/index paths defensively, verifies downloads against indexed hashes, installs indexed jars through `ServerExtensionsService`, and extracts path-validated config-style overrides only from `config/` and `defaultconfigs/`. Override imports must not write `mods/*.jar`; mod jars must arrive through indexed downloads. Override exports must not follow symlinks when collecting files. Modrinth-origin installed mods are exported as indexed files when their persisted `projectId` and `versionId` resolve and local hashes match. Manual/local installed jars may be exported as indexed Modrinth files only when Modrinth `/version_file/{hash}` resolves an exact version file and the returned hashes match the local jar; the recovered Modrinth source metadata should be persisted afterward. Non-Modrinth/local jars that cannot be resolved this way must be skipped instead of exported as overrides. Hashes should be calculated once per local jar during an export and reused for all SHA-1/SHA-512 checks.

Modrinth import must ask the user which content side to install: server, client, or complete, defaulting the selection to complete. Do not silently infer this choice. `PanelExtensiones` shows a modal loading dialog while the import worker downloads, verifies, installs, and extracts selected content. Loading dialogs should use the shared `AppTheme.createLoadingProgressBar(...)` style so progress bars remain visually consistent across import/download flows.

When importing a modpack into a server that already has managed mods, the UI must ask how to handle the current mods. Keep mode skips already installed/conflicting entries with warnings. Replace mode is destructive only once the import succeeds: after the pack metadata and planned downloads are resolved, move only non-symlink regular `.jar` files inside adapter-managed extension directories into a temporary `.dora-backup/mods-*` folder under the server folder, refresh extension state, then install the modpack entries. If any install fails, delete the partially installed jars, move the previous jars back, restore the previous extension metadata, and rethrow with a message saying nothing changed. Delete the temporary backup only after every entry installed. Do not move or delete unmanaged files, nested folders, symlinks, or files outside the server directory.

Modpack import uses `ProcessWizardDialog` so content selection, existing-mod handling, and final review happen in one guided process. When existing mods are detected, keep existing-mod handling below modpack content in the same step. Reuse pre-read pack metadata and already-synchronized extension state when starting the import worker; avoid parsing the same pack or rescanning extension directories again after the wizard.

`CurseForgeModpackService` remains as legacy support for CurseForge-style `manifest.json` ZIPs. CurseForge import still depends on a configured CurseForge API key to resolve manifest file IDs.

Be careful about side classification, optional files, and skipped entries. Do not silently include client-only files in server exports or server imports.

## Tests

Relevant tests:

- `ServerExtensionsServiceTest`
- `ModrinthModpackServiceTest`
- `GestorServidoresTest`
- `ExtensionCatalogServiceTest`
- `ModrinthExtensionCatalogProviderTest`
- `HangarExtensionCatalogProviderTest`
- `ExtensionMarketplaceDependencyTest`
- `PanelExtensionesTest`
- `ExtensionDescriptionRendererTest`

Run targeted tests when changing parsing, compatibility, dependency resolution, catalog behavior, or install/remove flows.
