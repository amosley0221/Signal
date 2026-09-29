# Signal — notes for contributors (human or AI)

## Layout
- `app/` — Android app (Kotlin, Jetpack Compose, Media3). Package `com.amosley.signal`.
  - `core/` pure Kotlin (models, artist suggester, LRC parser, search) — unit-tested in `app/src/test`.
  - `data/` repository, PC agent client, downloads, MediaStore scanner.
  - `playback/` ExoPlayer + MediaSession service, queue (`PlayerHub`).
  - `cast/` Google Cast and Sonos (UPnP) outputs.
  - `ui/` Compose screens. Folded (<600 dp) = single column + mini player; unfolded = two panes.
- `agent/` — Signal Agent, the Node.js server that runs on the PC. `npm test` inside `agent/`.
- `docs/API.md` — the HTTP contract between the app and the agent. Change both sides together.
- `design/` — the original design handoff (HTML prototype + screenshots).

## Releasing an update (required for every app change)
Every change to the app must ship as a new GitHub release so it can be installed over the old one:
1. Bump `VERSION_NAME` in `version.properties` (semver). `versionCode` is derived from it, so it always increases.
2. Add a `## [x.y.z] - YYYY-MM-DD` section at the top of `CHANGELOG.md` describing the changes in plain language.
3. Merge to the default branch. `.github/workflows/android.yml` builds the APK, signs it with the release key
   (repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`)
   and publishes `Signal-x.y.z.apk` as release `vx.y.z` with the changelog section as notes.

Never change `applicationId` (`com.amosley.signal`) or the signing key — either one would force users to uninstall.
