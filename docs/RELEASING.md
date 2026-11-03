# Releasing

Pushing a tag builds a signed APK and publishes it as a GitHub release for
that tag. Before you can do this, create a signing key and add it to the
repository's secrets.

## 1. Create the signing key

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

## 2. Turn the keystore into a secret

macOS:

    base64 -i firetv-release.jks | tr -d '\n' | pbcopy

Linux:

    base64 -w 0 firetv-release.jks

## 3. Add the repository secrets

In the GitHub repo, go to Settings → Secrets and variables → Actions →
New repository secret, and create these four:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the one-line base64 text |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | the alias, such as `firetv` |
| `ANDROID_KEY_PASSWORD` | the key password (the same as the keystore password if you pressed Enter at that prompt) |

## 4. Tag a release

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
