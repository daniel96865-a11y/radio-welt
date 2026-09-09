# Publishing Radio Welt updates

We control the in-app update feed via GitHub (and paste.rs mirror). No rentry edit codes needed.

## Update mirrors (in app)

1. `https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/update-feed.txt`
2. `https://raw.githubusercontent.com/daniel96865-a11y/radio-welt/main/docs/update-feed.json`
3. `https://paste.rs/Cko3Z`

## When you have a new APK or changelog

1. Bump `versionCode` (and `versionName` if needed) in `app/build.gradle`.
2. Update `docs/update-feed.txt` and `docs/update-feed.json` with the new versionCode, versionName, title, message, and APK url.
3. Rebuild release (`./gradlew assembleRelease`) **or** replace `releases/RadioWelt-latest.apk` with the new signed APK.
4. Also copy/rename a versioned APK if desired (e.g. `RadioWelt-3.x-feed.apk`).
5. Commit and push to `main` so the raw GitHub feed URLs update.
6. Upload the APK to the GitHub release (clobber latest):

```bash
gh release upload latest releases/RadioWelt-latest.apk --clobber
```

Or create/update a tagged release as needed.

## Feed format

Plain text (`update-feed.txt`) and JSON (`update-feed.json`) both work. The app parses `versionCode`, `versionName`, `title`, `message`, and an `https://…apk` URL. The in-app banner shows when remote `versionCode` is greater than the installed one.
