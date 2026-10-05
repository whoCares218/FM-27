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
  training, development, the Champions League and club finances. Deterministic and
  seedable.
- `viewmodel/GameViewModel` — the single ViewModel that wires engines to the UI.
- `ui/` — Compose screens and reusable components; `ui/navigation/Routes.kt` holds
  the six bottom-navigation tabs.
- `ui/sound/SoundManager.kt` — synthesised UI cues (no audio assets). Gated by the
  `soundEnabled` setting; a future AdMob integration replaces
  `ui/screens/RewardScreen.kt`'s `RewardAdProvider` without touching the UI.

## League sizes

Every league holds 16 clubs so all leagues share the same 30-matchday calendar and
`Career.totalMatchdays()` stays consistent. Adding clubs to one league alone makes
its season longer than the others, so the extra rounds are never played. Keep the
leagues the same size, or give each league its own matchday count.

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

## Match lifecycle & resume (added 2026-10-01)

- A live match is a persisted object, not transient UI state. `InProgressMatchState`
  (in `domain/model`) is a `@Serializable` snapshot of everything the engine reads:
  clock, period, score, stats, cards, subs, fatigue, sent-off clubs, per-player
  rating accumulators and the RNG state. It lives inside `Career`, so it is saved by
  the same single atomic write as the rest of the career — there is no parallel
  persistence path to keep in sync.
- `SerializableRandom` (SplitMix64) makes a match byte-identically resumable: its
  whole state is one `Long`. `java.util.Random` is not usable for this — its state
  is a multi-word object. `ProgressiveMatchEngine` wraps any caller-supplied
  `Random` into a `SerializableRandom` on construction.
- `GameViewModel` persists the snapshot when leaving a live match and rebuilds it on
  load. `FootballManagerApp` MATCH_DAY back and `MatchDayScreen`'s `BackHandler` both
  route through `leaveMatch()`; do not pop MATCH_DAY without it or the snapshot is
  lost.
- The engine loop / week advance must release `isBusy` in a `finally`. A thrown
  exception previously left the UI permanently "busy".
- `RatingTracker` is self-contained: it stores every player it rates, so a resumed
  match (or one with substitutions) still rates players who came off.
- Domination red-card signal uses `(hHandicap - aHandicap)`, not the reverse.
  Handicaps are lower for a ten-man side; inverting this made the short side look
  dominant. `ProgressiveMatchEngineTest` guards the direction of change.

## Red-card / domination test note

- Assert the *direction* of the domination change around a dismissal, not an
  absolute `< 50` / `> 50` threshold. Late dismissals cannot flip a bar that is
  anchored to possession. The test samples seeds to find a sending-off.

## Curated squads & live tactics (added 2026-10-01)

- `domain/data/CuratedSquads.kt` holds hand-authored, legally-safe squads (fictionalised
  names) for a curated subset of clubs. `CuratedSquadBuilder` turns each design into a
  `Player` using the shared attribute logic in `PlayerGenerator` (`buildAttributes` +
  position offsets + `overallFor`), so an authored player's `overall` always matches the
  attributes it was given. `CareerFactory` wires the designs in by club name and calls
  `topUp` with generated cover so every club still fields a full squad.
- Do not hand-write an `overall` for a curated player: derive it with
  `PlayerGenerator.overallFor(position, attributes)` or `CuratedSquadAndLiveTacticsTest`
  will fail.
- Live tactics: `GameViewModel.applyLiveTactics` writes the career copy (so it persists)
  *and* pushes the change into the running `ProgressiveMatchEngine` via
  `activeHome`/`activeAway`. `liveTactics()` reads back what the engine is actually
  using — use it in tests rather than `career.tactics` when asserting mid-match state.
- `TacticsScreen` puts formation in an `FmDropdown` at the top and the XI in a compact
  `LineupGrid` (role-tagged rows), not the old pitch-token `TacticalBoard`. `ScreenRenderTest`
  opens the dropdown to change formation; keep the selected formation's name tappable.

## Versioning

- `versionCode` / `versionName` live in `app/build.gradle.kts`. Tag test builds as
  `vX.Y.Z-test`. Release signing uses `keystore.properties` (never committed); when
  absent, release falls back to the debug keystore so `assembleRelease` still
  produces an installable artifact.
- `GITHUB_TOKEN` is valid in this environment, but plain `git push` has no credential
  helper and will block on an interactive `Username for 'https://github.com':` prompt.
  Push with the token in the URL instead, which does not prompt:
  `git -c credential.helper= push "https://x-access-token:${GITHUB_TOKEN}@github.com/whoCares218/FM-27.git" HEAD:<branch>`.
  Do not leave a credential-bearing remote configured; the token-in-URL form is per-command.
- There are no GitHub Actions workflows in this repo, so PRs carry no CI checks to wait on.
- `origin/fix/match-lifecycle-persistence` and `origin/main` are both fast-forward bases;
  push new work as a normal (non-force) fast-forward.

## Economy, transfers, stadium & market values (added 2026-10-04)

- The whole economy is one reconciled ledger. `FinanceEngine.summarise` reads totals
  straight from `career.ledger` split by sign, so the finances screen can never disagree
  with the transactions. Any new income/expense must both write a ledger entry **and**
  move the club balance, or the summary and the balance will drift apart.
- Wages, commercial/broadcast income and non-wage operating costs are charged **once per
  week** in `SeasonEngine.advanceWeek`, never inside a fixture result. A previous bug paid
  the whole wage bill inside `applyResultToCareer`, so a season charged it hundreds of
  times and every club went billions into the red. `FinanceAndMarketTest` has a
  "once per week" regression test — keep it passing.
- Home matchday revenue is settled in `applyResultToCareer`: the ledger entry and the club
  balance are updated together, guarded on the user being the home side.
- `FinanceModel.annualRevenue` is the anchor for the whole economy: revenue tiers, transfer
  budgets, wage budgets and operating costs all derive from it. Wages (`PlayerValuer.weeklyWage`)
  are calibrated so a squad's bill sits just under the board's wage budget; a star signing
  pushes it over. Do not change one curve without re-checking the other, or budgets will
  either become trivial or impossible.
- Market value (`PlayerValuer`) is an original hedonic model (ability, age, potential,
  contract, form, output, position, injury, morale), not a copied database. Transfer fees
  (`TransferEngine.requiredPackage`) are market value plus club/contract/buyer factors, so
  fee and value are deliberately different numbers.
- Stadiums start at level 1 for every club, but level 1 is the club's own base capacity and
  the ceiling is reputation-based (`Stadium.maxCapacityForReputation`), so a small club
  cannot click its way to 150,000 seats. Upgrade cost is non-linear.
- Contracts renew at season turnover for wanted/emerging players; without that the whole
  world's squads drain to free agents within a few seasons and wage bills collapse to zero.
- The multi-season economy is bounded (10-season test): money does not explode, does not
  become permanently impossible, and richer clubs stay stronger.

## Transfer desk, audio & negotiation history (added 2026-10-05)

- The Transfers hub has three tabs: BUY | SELL | NEGOTIATIONS. NEGOTIATIONS is the
  persistent transfer desk (`NegotiationsScreen.kt`); tapping a card opens
  `NegotiationDetailScreen.kt`, and `TransferHistoryScreen.kt` lists completed deals.
  Routes live in `Routes.kt` (NEGOTIATION, NEGOTIATION_DETAIL, TRANSFER_HISTORY).
- `domain/model/Negotiation.kt` defines `NegotiationRecord` with a full event timeline,
  plus `NegotiationSide/Status/Outcome`. Records live in `Career.negotiations` and are
  saved by the same atomic write as everything else, so they survive restarts and season
  turnover (`SeasonEngine` uses `copy`, preserving both new fields).
- `domain/engine/NegotiationEngine` derives records from `Career.pendingOffers` and
  `Career.pendingSale` in `GameViewModel.updateCareer`. Dedupe is by `sourceKey`:
  `buy:<playerId>:<fromClubId>` on the buy side (so re-bidding appends, never duplicates)
  and `sell:<saleId>:<clubId>` on the sell side. Terminal sell/complete moments sync the
  source object explicitly *before* it is cleared (`acceptSaleBid`, `cancelSale`).
- ID allocation bug to remember: `NegotiationEngine.upsert` must derive a new record's id
  from the ids already in the running list, not just `career.idCounter`, or two records
  created in one sync collide. Covered by `NegotiationTest`.
- Audio: `MusicEngine` synthesises an original, copyright-safe pad (no assets) on a
  daemon thread; `SoundManager` wraps `ToneGenerator`. `MusicEngine.enabled` is a
  setter that starts/stops immediately, and `FootballManagerApp` drives start/stop from
  the lifecycle (`ON_START`/`ON_STOP`), re-asserting the settings on start so "music off"
  stays off across navigation.
- Settings: `GameSettings` carries `musicEnabled`, `soundVolume`, `musicVolume`; the
  Audio card in `SettingsScreen` exposes toggles + sliders. All persist via DataStore.
- Tests: `NegotiationTest` covers create/counter/no-duplicate/complete/reject/withdraw +
  save-load; `ScreenRenderTest` renders the desk, detail and history screens.

