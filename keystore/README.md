# Signing key

The release key is **private** and is not stored in this repository.
The previous public key (`ashdroid.jks`, password `ashdroid`) is retired: anyone
could sign an APK with it, so it must not be used again.

## Create the key (once, keep a backup offline)

```sh
keytool -genkeypair -v -keystore ashdroid-release.jks -alias ashdroid \
  -keyalg RSA -keysize 4096 -validity 10000
```

## CI secrets (Settings → Secrets and variables → Actions)

| Secret | Value |
|---|---|
| `ASHDROID_KEYSTORE_BASE64` | `base64 -w0 ashdroid-release.jks` |
| `ASHDROID_STORE_PASSWORD` | keystore password |
| `ASHDROID_KEY_ALIAS` | `ashdroid` |
| `ASHDROID_KEY_PASSWORD` | key password |

## Local release build

Set `ASHDROID_KEYSTORE` (path to the .jks) plus the three variables above.
Release builds fail if any are missing. Debug builds use the Android debug key.

Verify the finished APK: `apksigner verify --print-certs app-release.apk`
