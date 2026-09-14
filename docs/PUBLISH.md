# Publishing Radio Welt updates

The app reads `docs/update-feed.txt` and `docs/update-feed.json` from this repository's public raw URLs on `main`.

1. Increase the versionCode and versionName in `app/build.gradle`, build and verify the signed release APK. Keep the signing certificate compatible with the previous version.
2. Copy the verified APK to `releases/RadioWelt-latest.apk`, update `releases/CHANGELOG.md`, and run `sha256sum releases/RadioWelt-latest.apk > releases/RadioWelt-latest.apk.sha256`.
3. Push the source and release files to `main`. The `Publish Radio Welt APK` workflow checks the checksum and creates the matching GitHub release, for example `v3.5`, with asset `RadioWelt-latest.apk`.
4. Wait for the workflow to succeed. Download the public release asset and compare its checksum before offering it to installed apps.
5. In a separate commit, update `docs/update-feed.txt`, `docs/update-feed.json`, and `docs/updates.json` with the version and verified version-specific release URL. Push to `main`, then check both public feeds.

Existing tagged releases are not overwritten. Each new APK requires a new version and tag.

The app offers an update when the feed versionCode is greater than the installed versionCode. Keep both feed files consistent. The old paste.rs mirror is no longer used.
