# IMSnitch Catcher

Pure-Kotlin Android app that watches the **serving / neighbor cellular environment** and alerts you when behaviour looks like a **rogue base station / IMSI-catcher** (downgrade to 2G/3G, isolated cell, abnormal RSRP, operator spoof, TAC jumps, …).

Package: `ltechnologies.onionphone.imsnitch` · minSdk 26 · targetSdk 37 · Jetpack Compose.

## What Android actually exposes

Stock apps **cannot** read modem cipher suites or baseband “IMSI requested” events — those need privileged / vendor APIs (or Android 16+ Safety Center *Mobile network security* on supported devices).

What we *can* use:

| API | Role |
|-----|------|
| `TelephonyManager.requestCellInfoUpdate` / `getAllCellInfo` | GSM / WCDMA / TD-SCDMA / LTE / NR identities + signal |
| `CellIdentity*` + `CellSignalStrength*` | MCC/MNC, LAC/TAC, CI/NCI, PCI, RSRP/RSSI |
| `TelephonyManager.getDataNetworkType` / `voiceNetworkType` | RAT generation for downgrade detection |
| `ServiceState` | Emergency / limited service |
| `SubscriptionManager` | Active SIMs |
| `Settings.Global.AIRPLANE_MODE_ON` | Read airplane state; **write** only with `WRITE_SECURE_SETTINGS` |
| `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` | One-tap airplane / radio panel fallback |

## Detection heuristics

1. **Downgrade** — LTE/NR recently, now on 2G/3G  
2. **Isolated cell** — registered cell with zero neighbors  
3. **Strong new cell** — brand-new CI with very high RSRP  
4. **Operator spoof** — cell PLMN vs SIM / trusted operator  
5. **TAC/LAC jump** — large tracking-area delta in seconds  
6. **Rapid hop** — many distinct serving cells in a short window  
7. **Limited service** — emergency-only attach on a strong cell  
8. **Known rogue** — optional local deny-list of cell identities  

## Airplane mode mitigation

Ordinary apps **cannot** flip airplane mode since API 17 (secure setting).

- **Preferred:** grant once via ADB (no root required on user builds that allow it):

```bash
adb shell pm grant ltechnologies.onionphone.imsnitch android.permission.WRITE_SECURE_SETTINGS
# debug build:
adb shell pm grant ltechnologies.onionphone.imsnitch.debug android.permission.WRITE_SECURE_SETTINGS
```

- **Fallback:** the app opens the system Internet / Airplane panel.  
- Optional **Auto airplane on CRITICAL** uses the grant when present.

Also open **Mobile network settings** to disable **2G** on Android 12+ devices that expose the toggle.

## Build

```bash
./gradlew test :app:assembleDebug
```

### Signed release APKs

```bash
./scripts/generate-release-keystore.sh   # once
./gradlew :app:assembleRelease           # per-ABI signed APKs under app/build/outputs/apk/release/
./scripts/upload-release-secrets.sh      # once — push RELEASE_* secrets to GitHub
git tag v0.1.0 && git push origin v0.1.0 # triggers Release workflow
```

Or run **Actions → Release → Run workflow** with tag `v0.1.0`.

## Permissions

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — required for CellInfo  
- `READ_PHONE_STATE` — network / SIM metadata  
- `POST_NOTIFICATIONS` — threat alerts (API 33+)  
- `FOREGROUND_SERVICE` + `SPECIAL_USE` — continuous monitor  
- `WRITE_SECURE_SETTINGS` — optional airplane toggle (ADB grant)

## Privacy

No analytics, no network stack, backups disabled for app data. Cell samples stay on-device.

## License

MIT — see [LICENSE](LICENSE).
