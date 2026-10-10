Fire Remote
===========

A local-network Android app for controlling a Fire TV. The debug workflow
builds an installable test APK. The release workflow signs a production APK
with a keystore you create and store as GitHub secrets.


Create the signing certificate
------------------------------

Run this on your computer. keytool comes with the JDK. Pick your own alias,
passwords, and file name, then use those same values in the secrets below.

    keytool -genkeypair -v \
      -keystore firetv-release.jks \
      -alias firetv \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000

keytool asks for a keystore password, a key password, and a name. The name
can be your own. The file firetv-release.jks is the certificate store.

Keep that file and both passwords. If you lose them, you cannot publish an
update that Android will accept as the same app. Do not commit the .jks
file or the passwords.


Turn the keystore into a GitHub secret
---------------------------------------

On a Mac:

    base64 -i firetv-release.jks | tr -d '\n' | pbcopy

On Linux:

    base64 -w 0 firetv-release.jks

The copied text is one long line. That line is the keystore secret.


Add the repository secrets
--------------------------

Open the GitHub repo, then Settings, Secrets and variables, Actions,
New repository secret. Create these four. The names have to match the
release workflow.

    ANDROID_KEYSTORE_BASE64      the one-line base64 text
    ANDROID_KEYSTORE_PASSWORD    the keystore password from keytool
    ANDROID_KEY_ALIAS            the alias, such as firetv
    ANDROID_KEY_PASSWORD         the key password from keytool

If you pressed Enter at the key password prompt, the key password is the
same as the keystore password. Store that same value in both secrets.


How the release workflow signs the APK
--------------------------------------

.github/workflows/release.yml does this:

1. Builds an unsigned release APK with ./gradlew :app:assembleRelease.
   That file is app/build/outputs/apk/release/app-release-unsigned.apk.
2. Decodes ANDROID_KEYSTORE_BASE64 into a temporary keystore on the runner.
3. Signs the APK with apksigner, using ANDROID_KEY_ALIAS,
   ANDROID_KEYSTORE_PASSWORD, and ANDROID_KEY_PASSWORD.
4. Runs apksigner verify --verbose --print-certs on the signed file.
   The job fails if the signature is not valid.
5. Deletes the temporary keystore, then uploads app-release.apk.

The keystore never goes into the git repo. The runner only has it for
that job.


Run the release workflow
------------------------

After the four secrets exist, either:

- On GitHub, open Actions, choose Release, and click Run workflow.
- Or push a version tag, which starts the same workflow:

    git tag v1.0.0
    git push origin v1.0.0

Download app-release from the finished run's artifacts. That is the
signed APK. The debug workflow on main is separate and does not use
the keystore.
