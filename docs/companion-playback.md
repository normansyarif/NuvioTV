# Play on TV (protocol v1)

Install companion-enabled builds of Nuvio Mobile and Nuvio TV. Open Nuvio TV,
finish setup and select a profile. On iOS, hold a stream and choose **Play on TV**.
Allow Local Network access, select the TV, and enter its six-digit code once.
Subsequent sends from that paired iPhone start playback immediately.

The TV fetches the media itself. The phone does not relay video and can lock after
playback starts. This version supports direct HTTP/HTTPS media links, including
links resolved from debrid on the phone. Browser-only links, phone-local playback
servers and unresolved torrents are unsupported. Playback starts from the beginning
and uses the TV's active profile and built-in player, including its normal progress
tracking. A new request replaces an existing player; Back returns to Nuvio.

## Discovery and lifecycle

The TV uses Android NSD to publish `_nuvio-tv._tcp.` on an OS-assigned TCP port.
TXT records contain a persistent device `id` and `version=1`. The iOS host browses
with Bonjour and resolves the current host and port each time, so DHCP changes do
not invalidate a pairing. The last-used TV is first in the picker.

The receiver starts only while the main TV UI is ready, after profile selection,
and stops when the app leaves the foreground or that UI is disposed. It does not
wake the TV, launch from the home screen, or run a background service.

## HTTP API

All responses are JSON with `Cache-Control: no-store`. POST requests must use
`application/json` and a Content-Length between 2 and 65536 bytes. Browser Origin
requests and chunked bodies are rejected. Transport is HTTP on the local network;
use a trusted LAN. Tokens and stream URLs must not be logged or advertised in TXT.
The iPhone refuses HTTP redirects and stores pairing tokens in Keychain.

- `GET /v1/info`: device ID, name and protocol version.
- `POST /v1/pair/start` with `{}`: returns `challengeId`. Displays a random code
  only on the TV. One challenge at a time; expires after two minutes.
- `POST /v1/pair` with `challengeId` and `code`: returns a random 256-bit bearer
  token. At most five guesses per challenge. TV Cancel invalidates the challenge.
- `POST /v1/play` with bearer authorization: validates and queues a playback
  request, returning HTTP 202. This acknowledges receipt, not playback.
- `GET /v1/play/{requestId}` with the same token: `starting`, `playing`, `error`
  or `stopped`. The latter three are terminal startup outcomes. Player UI state
  confirms `playing`; an existing unrelated player cannot confirm a new request.

Playback JSON requires a UUID `requestId`, media `url`, and nonempty `title`.
Optional fields: `headers`, `contentId`, `contentType`, `videoId`, `season`,
`episode`, `episodeTitle`, `poster`, `backdrop`, `logo`, `streamName`, `addonName`,
`filename`, `bingeGroup`, and `subtitles` (`url`, `language`, `name`, `headers`).

The receiver passes media headers and metadata through `Screen.Player.createRoute`
and supplies stream subtitles through `StreamSidecarSubtitles`. It appends a
request ID to correlate the resulting player's startup state. Reusing a request
ID from the same phone does not start a second player. Another phone cannot read
its status or reuse that ID. Status retention is bounded to 32 requests and five
minutes at admission. The phone polls startup for up to 45 iterations, then reports
unconfirmed playback instead of claiming success or automatically resending.

## Verification

`CompanionProtocolTest` covers expiry, attempt limits, cancellation, URL/header
validation, and status ownership. `CompanionHttpServerTest` uses real HTTP sockets
to check pairing, token auth, framing, duplicate requests and player confirmation.

```sh
./gradlew :app:testFullDebugUnitTest --tests '*Companion*'
./gradlew :app:assembleFullDebug
```

Device QA: discover on the same Wi-Fi, enter an incorrect code then the correct
code, play a direct video, test required headers and sidecar subtitles, send a
second video (also the identical URL), leave/reopen the TV app, and verify that a
locked phone does not stop TV playback. Test denied Local Network permission and
an unavailable TV. Verify saved progress under the TV's selected profile.

## Verified on 2026-10-27

- All nine companion protocol/socket tests passed, run in an isolated JVM harness
  against the production protocol and HTTP server sources.
- The complete TV Kotlin compilation and Full Debug APK assembly passed. A local
  Gradle init override selected the standard debug certificate and disabled the
  release-only lint gate for this device-testing APK. The repository normally
  selects the maintainer’s release keystore even for Debug; the override did not
  modify that project signing configuration. Full repository lint/unit-test
  compilation was not completed.
- `:composeApp:compileKotlinIosSimulatorArm64` passed in the App Store configuration,
  including the shared stream-menu action. The native companion Swift source also
  compiled against the installed simulator SDK.
- The production Swift companion UI, hosted by a small simulator harness, discovered
  a physical Android 11 TV, displayed its pairing prompt, saved the pairing in
  Keychain, and started its built-in Nuvio player. The picker showed Playing only
  after the TV confirmed playback.
- Replaying the identical URL replaced the existing player without a second code.
  Pairing remained available after restarting the simulator companion.
- A test media server required a custom HTTP header; the TV supplied it and played
  the video. The TV fetched and visibly rendered a forwarded WebVTT subtitle.
- Leaving the TV app removed its Bonjour advertisement; the receiver was unavailable in the background.
- After terminating the simulator companion, the TV video continued advancing
  (verified visibly at 5.7 and 9.7 seconds).

## Verified on 2026-10-28

The full iOS app (build 150) was built on GitHub Actions, signed, installed and
verified on the physical iPhone through iPhone Mirroring. Play on TV from the
stream context menu started a real episode on the TV. Trakt device approval and
IntroDB Skip Intro / Skip Recap were also verified on both devices.

### iPhone remote

The iPhone's **Remote** tab opens the same drawer as **Play on TV**, with a
navigation pad, OK, back/home, volume, power, keyboard and **Launch Nuvio TV**.
A green indicator means a live connection; a saved pairing alone is not an online
connection. The iPhone disconnects in the background and reconnects on return.

Whole-TV control uses the installed Android TV Remote Service v2, independently
of this foreground HTTP receiver. Its six-character system pairing code is
separate from Nuvio's stream-sharing pairing. Nuvio's iOS host keeps its generated
client identity and authenticated TV certificate in Keychain. Runtime control
does not use ADB. Android TV Remote Service must be available on the TV.

This build handles `nuvio-tv://home` in MainActivity so the iPhone's launch button
can open it directly, even when a second Nuvio build also handles `nuvio://`.
Navigation, OK, home, volume, launch and text insertion were tested against the
physical TV. Text and Backspace were verified directly from iPhone build 150.
Some apps use custom fields that cannot accept remote keyboard input.

Power-off worked. The tested TV disconnected from Wi-Fi in standby, so remote
wake was not established and the physical remote was needed to turn it back on.
Network access in standby is required for the remote service to wake a TV.
