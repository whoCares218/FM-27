# Football Manager 26/27 Simulator

Offline Android football-management game. Kotlin + Jetpack Compose, MVVM, DataStore
persistence. No backend, no login, no network for core play.

## Build and test

```bash
./gradlew :app:testDebugUnitTest          # unit + Robolectric suite (no emulator needed)
./gradlew :app:assembleRelease            # release APK  -> app/build/outputs/apk/release
./gradlew :app:bundleRelease              # release AAB  -> app/build/outputs/bundle/release
./gradlew :app:lintDebug                  # Android lint
```

No emulator or KVM is available in this environment, so UI and ViewModel behaviour
is verified with Robolectric. Keep the suite passing before building a release.

## Architecture

- `domain/model` — pure data classes for clubs, players, careers, fixtures.
- `domain/data` — databases, codec and persistence. `CareerStore`/`SettingsStore`
  are the persistence interfaces; `SaveRepository`/`SettingsRepository` are the
  DataStore-backed production implementations.
- `domain/engine` — match simulation, league tables, season progression, transfers,
  training, development. Deterministic and seedable.
- `viewmodel/GameViewModel` — the single ViewModel that wires engines to the UI.
- `ui/` — Compose screens and reusable components; `ui/navigation/Routes.kt` holds
  the six bottom-navigation tabs.

## Testing conventions

- `GameViewModelTest` runs against `FakeCareerStore`/`FakeSettingsStore` (in-memory).
  These tests target game logic and write ordering, so they avoid DataStore.
- `SaveRepositoryTest` covers the real DataStore write/read/delete round trip.
- `SaveLoadTest` covers the `SaveCodec` serialization round trip.
- `viewModelScope` runs on `Dispatchers.Main`, which Robolectric pauses. Tests swap
  it with `Dispatchers.setMain(Dispatchers.Default)` and poll with a small timeout.

## Gotchas

- Kotlin evaluates a safe-call's argument lazily. `x?.invoke(sideEffect())` does NOT
  run `sideEffect()` when `x` is null. Persistence code must assign the result first
  (`val saved = store.save(...); callback?.invoke(saved)`), otherwise the save is
  silently skipped. This bug once made every write a no-op while reporting success.
- `Channel(CONFLATED)` collapses queued writes to the newest one, which is what the
  save queue wants: rapid edits persist only the latest state, in order.
