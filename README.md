# OnePlus 13R AOD — No Root Full-Screen All-Day

Full-screen wallpaper Always-On Display on the OnePlus 13R (OxygenOS 16), no root,
no bootloader unlock. OnePlus withholds the "all-day" bit on the 13R panel while
shipping it on the 13/13s; this app runs the one shell command that skips the
timeout enforcement inside SystemUI, then puts Developer Options back to hidden
so banking / integrity-checking apps stay happy.

## What it actually does

Every reboot, the app fires this against your phone over local wireless ADB:

```
dumpsys activity service com.android.systemui/.keyguard.KeyguardService debug keep_aod_on 1
```

Then to make banking apps happy again:

```
settings put global development_settings_enabled 0
settings put global adb_wifi_enabled 0
settings put global adb_enabled 0
```

That's the whole trick. Everything else is UI, pairing, and TLS plumbing.

**This is real hardware AOD**, not fake screen-on. It uses the same doze mode as
OnePlus's official all-day AOD on the 13/13s: proximity/pocket detection works,
ambient brightness works, burn-in mitigation runs. Battery cost is normal AOD.

## Why the trick works

Inside `SmoothTransitionController.initSmoothTransitionState()`, SystemUI reads
panel feature 217. On the 13R the panel HAL returns `[6]`: smooth-transition +
panoramic yes, all-day no. That in turn drives `isSupportPanoramicAllDay = false`
and fires `hideByTimeoutReceiver` about 6–10 s into every AOD session.

`BaseAodClockLayoutController.onEnergySavingNotifyHide()` checks a debug field
`AODDisplayUtil.mKeepAodOn` before running the hide. If it's `1`, the hide is
skipped with the log line `"keep aod on for test!"`.

`OplusKeyguardServiceExImpl.dumpPhaseThree(["debug","keep_aod_on","1"])` sets
that field. No `Build.isDebuggable()` guard, no permission check. Reachable from
any adb-privileged shell via the `dumpsys` route above.

The field resets on every SystemUI restart, so it needs to be re-fired at each
boot.

## Building

Nothing local — just push and GitHub Actions builds a debug APK. Download it
from the Actions tab or a tagged release, sideload, done.

Local build if you want it:

```powershell
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. Install the APK.
2. Boot the phone (fresh boot needed to expose Wireless Debugging port).
3. Settings → About phone → tap Build number 7 times → back → Developer Options
   → Wireless debugging (turn on).
4. Tap **Wireless debugging** to open the panel (do not just toggle it — you
   need to see the port numbers).
5. **One-time pairing** (only needed the first time you install the app):
   - Tap **Pair device with pairing code**
   - In the app's Pair section, type the port shown on the pairing screen and
     the 6-digit code
   - Tap Pair
6. **Every subsequent boot:**
   - Go back to the Wireless Debugging main screen — note the "IP address & Port"
     number (this is different from the pairing port)
   - In the app, type that port and tap **Fire**
   - App fires the AOD command, then turns Developer Options and ADB fully off
   - Unplug / put away

## Notes / caveats

- The APK is signed with the CI's debug key, so Android will call it "unknown
  developer" on install. That's normal for a debug build; it doesn't change
  anything about how the app runs.
- If pairing breaks, tap **Forget pairing** in the app and re-pair. Toggling
  Wireless Debugging off and on on the phone also usually clears stale state.
- Because everything happens over `127.0.0.1` inside the phone, no data leaves
  the device. The app has only `INTERNET` permission (needed to open any local
  socket on Android) and no exported components.
- No account, no analytics, no background service.

## License

MIT. See LICENSE.
