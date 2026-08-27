# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | Yes       |

## Reporting a vulnerability

Email security reports to the maintainers via GitHub Security Advisories on this repository.
Do not open a public issue for exploitable flaws in the detection heuristics or privilege paths.

## Design notes

- The app never exfiltrates CellInfo / IMSI / IMEI. There is no network client.
- `WRITE_SECURE_SETTINGS` is optional and only used to toggle airplane mode when
  explicitly granted (`adb shell pm grant …`).
- Detection is heuristic. It is not a substitute for modem-level Mobile Network
  Security (Android 16+) or baseband tooling (SnoopSnitch, GrapheneOS Canary tier-2).
