# GNSS Reference Logger (Android)

Records what a standard Android phone sees from the GNSS satellites, once per second, in the same four
steps and with the same definitions as the MSIL 4G TCU report:

| Step | Measure | Source on the phone |
|---|---|---|
| 1 Tracked | satellites whose signal is heard | `GnssStatus` callback |
| 2 Usable | C/N0 >= 32 dB-Hz **and** elevation > 5 deg (Quectel's conditions) | `GnssStatus` (C/N0, elevation) |
| 3 Used | satellites in the position calculation (4 needed) | `GnssStatus.usedInFix` |
| 4 Position | valid position, HDOP, accuracy | GPS provider at 1 Hz, NMEA `GGA` |
| Start-up | first fix / steady fix after session start, Android TTFF | `onFirstFix`, per-second records |

The engine (`app/src/main/java/com/harman/gnssref/core`) is pure Java and is unit-tested by replaying the
Bangalore TCU log (`app/src/test/resources/bangalore_tcu.nmea`): it reproduces the deck's numbers
(12.0 used, 15.1 usable, 29.3 tracked, 41.6 / 33.2 dB-Hz, HDOP median 1.4). Phone and TCU reports therefore
use identical maths.

## Getting the APK (no PC needed)

Every push builds the app in GitHub Actions:

1. **Releases → "Latest build (main)"** always holds `GnssReferenceLogger-latest.apk`, rebuilt on every push to `main`.
   Direct link: `https://github.com/dharabhishekemail-ops/GnssReferenceLogger/releases/tag/latest-main`
2. Every run also keeps the APK as an **Actions artifact** (`GnssReferenceLogger-apk`), and a `v*` tag creates a numbered release.
3. On the phone: open the link in the browser (GitHub login needed while the repo is private), download the `.apk`,
   allow "install from unknown sources" for the browser, open the file, install.

The APK is debug-signed, which is fine for internal test phones. Minimum Android 8.0 (API 26).

## Using the app

1. Enter a label (e.g. `Gurgaon spot A dashboard`), choose the duration (25 min = 1,500 s is the MSIL format), start.
2. Place the phone where the TCU antenna is, screen up, and start the TCU log at the same time.
3. For a start-up time comparable with the TCU, switch mobile data and Wi-Fi **off** (otherwise the phone uses A-GPS).
   The "cold start" checkbox asks the phone to clear its satellite data (`delete_aiding_data`); it works on some
   phones only and the result is written to the summary.
4. The live screen shows position valid / not, tracked / usable / used, signal, HDOP, TTFF, a sky view and a rolling chart.
   Recording continues with the screen off (foreground service with notification).
5. After the planned duration (or **Stop**), the summary screen shows the four-step figures, the time charts,
   the funnel, the weakest-second sky view, the photo of the spot and the summary text. **Share (zip)** sends the whole
   session folder (`per_second.csv`, `satellites.csv`, `raw.nmea`, `summary.txt`, `photo.jpg`).

Session folders live in `Android/data/com.harman.gnssref/files/sessions/` and can be reopened from
**Recorded sessions** on the start screen.

## Reading the result against the TCU

- Compare step 2 (usable) and step 4 (position valid) at the same minutes, not the absolute tracked count:
  the phone has a different receiver and antenna, and dual-frequency phones hear more signals.
- Phone TTFF is assisted unless data/Wi-Fi were off; hot starts are comparable either way.
- `per_second.csv` has the same columns as the TCU `per_second_metrics.csv`, and `raw.nmea` can be run through the
  same Python scripts (`parse_nmea.py`, `make_charts.py`) used for the TCU log.

## Project layout

```
app/src/main/java/com/harman/gnssref/
  core/      Metrics, SessionStats, NmeaParser, CsvFormat, ReportBuilder   (pure Java, tested)
  service/   GnssLoggerService (foreground service, 1 Hz ticker, file writers), Session
  ui/        SetupActivity, LiveActivity, SummaryActivity
  ui/views/  SkyView, TimeSeriesView, StatusStripView, FunnelView, Palette (same colours as the deck)
  util/      SessionLoader (reopen old sessions), ZipShare
app/src/test/  CoreTest, TcuLogReplayTest
.github/workflows/android.yml   CI: unit tests + debug APK artifact + release on v* tags
```

Local build (optional): Android Studio Koala or newer, or `gradle assembleDebug` with JDK 17 and the Android SDK.
