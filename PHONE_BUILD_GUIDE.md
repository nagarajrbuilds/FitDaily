# FitDaily v2.1 — Build APK using only your phone

This repository is ready for GitHub Actions.

## Easiest build: Debug APK

1. Open this repository in GitHub.
2. Tap **Actions**.
3. Select **Build Debug APK**.
4. Tap **Run workflow** → **Run workflow**.
5. Wait for the workflow to finish.
6. Open the completed workflow run.
7. Under **Artifacts**, download **FitDaily-v2.1-debug**.
8. Extract the downloaded ZIP on your Samsung phone.
9. Tap `app-debug.apk` to install it.

The debug APK does not need your own signing key.

## Signed release APK / Play Store AAB

Add repository secrets:
- `FITDAILY_KEYSTORE_B64`
- `FITDAILY_STORE_PASSWORD`
- `FITDAILY_KEY_ALIAS`
- `FITDAILY_KEY_PASSWORD`

Never commit a .jks/.keystore file or passwords.
