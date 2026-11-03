# Fire Remote

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.local.firetvremote%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fjoelander-dev%2Ffiretv-remote%22%2C%22author%22%3A%22joelander-dev%22%2C%22name%22%3A%22Fire%20Remote%22%7D"><img src=".github/badge_obtainium.png" alt="Get it on Obtainium" width="161" height="48"></a>

An Android app that controls a Fire TV over your home Wi-Fi. It talks to the
Fire TV directly on your local network. It has no account and no cloud
service, and it never connects to anything outside your home network.

> Fire Remote is an independent project. It is not affiliated with, endorsed
> by, or sponsored by Amazon. "Fire TV" is a trademark of Amazon.com, Inc. or
> its affiliates.

## Features

- Finds Fire TVs on your Wi-Fi automatically (SSDP, with a subnet scan as a fallback)
- Pairs with the PIN the TV shows on screen
- D-pad, select, home, back, menu, power, volume, channel, play/pause, fast-forward and rewind
- Holding the d-pad, volume, channel or seek buttons repeats them
- Remembers paired TVs

## Install

Requires Android 8.0 or newer.

**With [Obtainium](https://github.com/ImranR98/Obtainium)** (gets updates
automatically): tap the **Get it on Obtainium** button above on your phone,
or add `https://github.com/joelander-dev/firetv-remote` in Obtainium.

**By hand:**

1. Download `fire-remote-<version>.apk` from the [Releases](../../releases) page.
2. Open it on your phone. If asked, allow installs from your browser or file
   manager.

## Use

1. Connect your phone to the same Wi-Fi network as the Fire TV.
2. Open Fire Remote and pick your TV from the list. If it doesn't appear,
   tap **Search the whole network** or type in the TV's IP address
   (on the Fire TV: Settings → My Fire TV → About → Network).
3. Enter the PIN that appears on the TV.

Pairing details stay on the phone. They are left out of backups and
phone-to-phone transfers, so you pair again on a new phone.

## Security notes

- **Local network only.** Every request is checked against private IPv4
  ranges (10.x, 172.16–31.x, 192.168.x, 169.254.x) before it is sent, and
  redirects are off. See `PrivateAddress.kt`.
- **Self-signed certificates.** The Fire TV's remote service uses HTTPS with a
  self-signed certificate, so the app can't check it against a certificate
  authority. It accepts the certificate only for private addresses.
- **Plain HTTP.** Wake and discovery use plain HTTP on the LAN, because that is
  what the Fire TV serves.
- **API key.** The `X-Api-Key` value in `FireTvClient.kt` is a fixed value the
  Fire TV protocol expects. It is not a secret.

## Build from source

Requires JDK 17 or newer and the Android SDK.

    ./gradlew :app:testDebugUnitTest :app:assembleDebug

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
The Android workflow builds and tests on every push to `main` and every pull
request, and uploads that APK as an artifact.

## Releasing

Pushing a tag builds a signed APK and publishes it as a GitHub release for
that tag. Before you can do this, create a signing key and add it to the
repository's secrets.

### 1. Create the signing key

keytool comes with the JDK. Choose your own alias, passwords and file name,
and use the same values in the secrets below.

    keytool -genkeypair -v \
      -keystore firetv-release.jks \
      -alias firetv \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000

Keep the `.jks` file and both passwords safe. If you lose them, Android
won't accept future releases as updates to the same app. Never commit the
`.jks` file or the passwords.

### 2. Turn the keystore into a secret

macOS:

    base64 -i firetv-release.jks | tr -d '\n' | pbcopy

Linux:

    base64 -w 0 firetv-release.jks

### 3. Add the repository secrets

In the GitHub repo, go to Settings → Secrets and variables → Actions →
New repository secret, and create these four:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the one-line base64 text |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | the alias, such as `firetv` |
| `ANDROID_KEY_PASSWORD` | the key password (the same as the keystore password if you pressed Enter at that prompt) |

### 4. Tag a release

    git tag v1.0.0
    git push origin v1.0.0

The Release workflow builds the tagged commit, signs it with `apksigner`,
verifies the signature, and attaches `fire-remote-v1.0.0.apk` to the
`v1.0.0` release. The app's version name comes from the tag (a leading `v`
is dropped). The version code is the workflow run number, which goes up with
every run, so each release installs as an update over the one before.

The keystore is decoded only on the runner for that job, and deleted
afterwards. Starting the Release workflow by hand builds a signed artifact
(version `0.0.0-dev.<run>`) and does not publish a release.

## License

[MIT](LICENSE)
