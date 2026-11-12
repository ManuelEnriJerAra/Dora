# Server Jar Downloads Are Not Verified

## Status

Fixed (Vanilla, Paper, Purpur)

## Original Issue

Server jars were saved and used without checking them against the hash the provider publishes. A truncated or tampered download was run as the server. Because install skips the download when the jar already exists, a broken jar also stayed in the folder for every retry.

## Root Cause

The Vanilla, Paper and Purpur adapters called `FileDownloader.download(...)` and moved on. Mojang (`sha1`) and Paper Fill (`checksums.sha256`) already return the hash in the metadata Dora reads, and Purpur publishes an `md5` per build, but none of it was used.

## Solution

- New `controlador.platform.DownloadChecksum.verifyOrDelete(...)`: hashes the downloaded file, and on a mismatch deletes it and throws an `IOException` with a Spanish message.
- Vanilla: `MojangAPI` now caches the server `sha1` next to the URL (`obtenerSha1ServerJar`), checked as SHA-1.
- Paper: `PaperBuild` carries `checksums.sha256` from the Fill API, checked as SHA-256.
- Purpur: `PurpurDownloadsClient.md5(...)` reads the build's `md5`, checked as MD5.
- If a provider does not return a hash, the download is accepted as before.

Not covered: Fabric's server launcher (Fabric Meta publishes no hash) and the Forge/NeoForge/Quilt installers (only Maven `.sha1` side files, which would need an extra request per install; the installers verify the libraries they download themselves).

## Files Changed

- `src/main/java/controlador/platform/DownloadChecksum.java`
- `src/main/java/controlador/MojangAPI.java`
- `src/main/java/controlador/platform/PaperDownloadsClient.java`
- `src/main/java/controlador/platform/PaperServerPlatformAdapter.java`
- `src/main/java/controlador/platform/PurpurDownloadsClient.java`
- `src/main/java/controlador/platform/PurpurServerPlatformAdapter.java`
- `src/main/java/controlador/platform/VanillaServerPlatformAdapter.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`

## Verification

- `mvn -q -Dtest=ServerPlatformAdaptersTest test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/server-jar-download-hash-verification.md`

## Regression Notes

Verify right after downloading and delete on mismatch: install skips existing jars, so a bad file left behind is reused forever.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
