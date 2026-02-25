Pulse – private emotional signals (Kotlin / Firebase)
====================================================

What’s here
- Android app scaffold (MVVM + Hilt) with separate Login → Signup → Pair → Signal screens (dark, purple-gradient UI, gated buttons).
- AES encryption helper + per-couple key storage (EncryptedSharedPreferences).
- Firestore + FCM wiring; background service vibrates on incoming data message and deletes the signal.
- 5 preset emotions mapped to vibration patterns; high-importance, vibration-only channel created at launch.
- Reliability: WorkManager fallback sync (`PendingSignalsWorker`) pulls undelivered signals every 15 minutes and on app launch; backend ack stub via Retrofit with certificate pinning stubbed in `network_security_config.xml`.

Getting started
1) Add `google-services.json` to `app/`.
2) In Firebase console, enable Email/Password auth, Firestore, Cloud Functions, and FCM.
3) Deploy a callable function `dispatchSignal` that sends a high-priority data message with fields `signalId`, `coupleId`, `encryptedEmotionId` to the recipient’s token(s). (The client writes the Firestore doc and then calls this function.)
4) Update Firestore rules with `firestore.rules`.
5) Open the project in Android Studio Giraffe+ and let Gradle sync.

Runtime notes
- The app auto-closes the main screen after 30 seconds to avoid shoulder-surfing.
- Data messages vibrate only; no visible notification text. DND or OEM battery policies may still suppress vibration—test on target devices.
- Signals are marked delivered and deleted after handling; no local chat log is kept.
- Unique per-couple AES keys are stored in encrypted prefs; clearing app data resets keys.

Quick commands (from project root)
- `./gradlew assembleDebug` (after Android SDK + wrapper are available)
- `./gradlew connectedDebugAndroidTest` to run device tests.

Next steps
- Add UI polish and biometrics/PIN gate.
- Implement a disguised launcher icon if desired.
- Add integration test to verify end-to-end send/receive with the deployed Cloud Function.
