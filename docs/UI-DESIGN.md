# BEACON COMPUTING INITIATIVE
**Developer Seal:** `CJB-DEV-2026`  
**SATELLITE1 v0.1.5 — Android Motion + Interface Design System**

## Design intent

SATELLITE1 is a precision mobile telemetry instrument, not a fantasy command terminal. Its visual language is an **orbital mission-control cockpit** with high-contrast cyan against BCI graphite and maroon. Data values must remain grounded in local evidence; no badge should claim that a tower was conclusively authenticated, or that the ORBIT11 server accepted data, unless it actually did so.

## Visual tokens

| Token | Hex | Application |
| --- | --- | --- |
| Obsidian | `#05080D` | Full-screen background and cold-start splash |
| Graphite | `#111820` | Instrument panels |
| BCI maroon | `#650D28` | Accent depth and panel gradients |
| Telemetry cyan | `#00E5FF` | Active measurement indication and tracing |
| Polar white | `#F4F8FB` | Headline text |
| Muted steel | `#A8BAC6` | Explanatory metadata and limits |

**Typography:** Oxanium is the intended title family, Rajdhani is the intended UI family, and JetBrains Mono is the intended log family. This repository intentionally **does not redistribute font binaries**; Compose uses accessible system sans/monospace fallbacks. If licensed font assets are added later, typography roles can be mapped through `FontFamily` resources.

## Native Compose implementation

- `BootSequence.kt`: pure vector Canvas with deterministic starfield, depth-sorted satellite, radial-lit planet, tracking ellipses, rotating radar arc and pulsing telemetry marker. A `rememberInfiniteTransition` drives motion with stable labels for tooling. No network, sensor, camera or device permission reads occur inside the composable.
- `MainActivity.kt`: `rememberSaveable` retains the user's boot-completion state across configuration changes; a coroutine transitions to the dashboard after roughly 2.6s; Android's `ANIMATOR_DURATION_SCALE=0` skips the transition as a reduced-motion default. The entry button always allows early skip.
- `styles.xml`: native AndroidX SplashScreen paints the initial frame obsidian, avoiding a default light flash while the Compose renderer initializes.
- `MissionControlHeader`: live foreground-service state and local evidence/cell-transition counters; the small satellite animates separately while Compose `animateIntAsState` and `animateColorAsState` update visible values smoothly.
- `CardPanel`: bordered graphitic panels and gradients. No navigation controls are decoration-only; existing telemetry controls retain their behavior and approval gates.
- Two `@Preview` functions provide Android Studio UI previews for faster iteration without a running collector.

## Security and consent boundaries

- **Cold start does not grant permissions or start collection.** Visual animation is never evidence of active sampling or upload.
- **Foreground monitoring requires explicit user action and a visible Android notification.** The mission header reflects `MonitorRuntimeState` rather than claiming saved policy equals live activity.
- **Rogue-cell alerts are assessments, not determinations.** The UI states they remain unconfirmed.
- **Data remains private:** no demo telemetry, location coordinates, uploaded secrets, keystores or capture files are included in this repository.
- **ORBIT11 is opt-in:** a configured TLS endpoint and verified signed receipt must pass before queued samples are acknowledged; the remote candidate stores accepted batches in quarantine.

## Screen and accessibility acceptance checks

1. Cold launch: dark Android splash then moving satellite boot scene, with readable BCI header and developer seal.
2. Skip control immediately opens dashboard; no sensors are started simply by displaying the intro.
3. Android animator duration scale disabled: skip animated intro without blocking app startup.
4. Landscape / small screen: important controls remain reachable and no privacy/consent copy is hidden; physical-device verification still pending.
5. Service active/inactive: status text changes with live runtime state; animation alone must not imply active monitoring.
6. Evidence and transition counters update from database-derived values, without fake placeholders.
7. Screen readers should expose concise meaningful text for controls; decorative Canvas art does not need spoken descriptions.
8. Return from background: collectors retain their existing foreground-service lifecycle; no new permission or upload action is triggered.

## Qualification status

Source and static smoke tests can PASS in development; **a full Android Gradle compile, visual inspection on Samsung, battery profiling, TalkBack review and signed-app update remain NOT RUN** in this authoring environment. The design is a development candidate, not a certified monitoring product.
