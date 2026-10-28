<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioTV/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Get Nuvio TV

- [Android TV on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android TV APK](https://github.com/NuvioMedia/NuvioTV/releases/latest)

## Play on TV

The iOS companion can send a stream directly to Nuvio TV’s built-in player.
Keep this TV build open, then hold a stream on iOS and choose **Play on TV**.
Select the TV and enter its pairing code once. Both apps need companion-enabled
builds. See [setup, protocol and verification](docs/companion-playback.md).

The iPhone's **Remote** tab also provides navigation, volume, keyboard input and
**Launch Nuvio TV** through Android TV Remote Service. This uses a separate
one-time system pairing and works while Nuvio is not the foreground TV app.
Power-on requires the TV to keep its network connection in standby.

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioTV.git
cd NuvioTV
./gradlew :app:assembleFullDebug
```

Nuvio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK, and the Android SDK.

## License

[GNU General Public License v3.0](./LICENSE)


## Trakt and IntroDB in custom builds

IntroDB reads use `https://api.introdb.app/` by default, with no API key required.
An optional `INTRODB_API_URL` in local.properties overrides that endpoint; a blank
value keeps the public default. Skip Intro must be enabled in player settings,
and buttons appear only when IntroDB has timestamps for the selected episode.

Trakt requires a client ID from your own application at
https://developer.trakt.tv/apps. Add these to the ignored `local.properties`:

```properties
TRAKT_CLIENT_ID=your-client-id
TRAKT_REDIRECT_URI=http://localhost
```

New Trakt applications do not issue a client secret. Both apps support Trakt's
device-code flow with just the client ID: open Trakt settings, connect, and enter
the displayed code on the Trakt approval page. A Development application limits
sign-in to its owner and added members; its registered redirect URI can be
`http://localhost` (unused during device approval, sent during token refresh).
The iOS build retains its previous browser callback flow for legacy applications
that configure a client secret.

Keep access tokens out of commits and build logs. Rebuild and reinstall after changing configuration.
