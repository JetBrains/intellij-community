---
topic: testing
---

# A TeamCity lane run records video and keeps its evidence

Date: 2026-10-20

## Status

Accepted. Issue [IJAI-4382](https://youtrack.jetbrains.com/issue/IJAI-4382). It amends the status note of
[ADR 0185](0185-the-recorder-encodes-its-own-video.md), which says that only the descriptor of a Linux guest lets the
recorder read the screen. A TeamCity build on Linux now records the screen too. A developer's machine still never does,
as [The lane daemon is the only runner of the Air UI lanes](2026-10-19-the-lane-daemon-is-the-only-runner-of-the-air-ui-lanes.md)
states.

Since 2026-10-21 a TeamCity lane run also shows while it goes on. The personal build 1101267129 proved that a file
which a step streams into the build log reaches it during the Bazel step. `lane-run` appends a progress message for
each scenario to the file of `AIR_LANE_LIVE_LOG`. It also publishes the video and the last picture of a failed scenario
at the paths of `air-trace teamcity-publish`. The results still come from the JUnit XML only.

Since 2026-10-23 a passed scenario keeps its video only in a build of `master`, and decision 2 states why. The shard
build 1101705922 published 148 MiB, and 64.4 MB of it was the `video.mp4` of each scenario, the passed ones too. The composite
`ijplatform_master_AirPluginUiTests` ran more than 500 builds in 7 days, with 2 shards each, and only 52 of them were
builds of `master`. The other builds were Safe Push checks and personal builds.

The behavior is stated in [the scenario trace spec](../../spec/scenario-trace.spec.md) under **Capture and video**, and
in [the controller spec](../../spec/lane-controller.spec.md) under `lane-run`. How to find the files is
[the VM guide](../vm-ui-tests.md#artifacts-and-diagnostics).

## Context

The shard build 1099482304 of `AirPluginUiTestsAggregator` ran 56 scenarios through `lane-run` under a Bazel test.
It lost what a person needs to see what each test did:

- The recorder took paint stills only: `capture ide-paint, video none (no X11: the lane did not let the recorder read
  the screen ...)`. Only the descriptor of a Linux guest lets the recorder read the screen, and `lane-run` refuses the
  flag as a JVM flag.
- The full `idea.log`, the heartbeat screenshots, the kill dumps, the slow-step dumps and the authored artifacts stayed
  below `TEST_TMPDIR`. Bazel deletes that directory after the test. Commit 7b750fbf48cb1 saved only the bundles.

The rule against the screen protects a person: a developer's screen shows everything else they have open, and macOS
asks for the Screen Recording permission in the middle of a scenario. A TeamCity agent runs one build at a time, and
no person works on its display, so nothing of a person is on that screen.

The Air UI builds start `Xvfb :0 -screen 0 1024x768x24` before the Bazel step. On the UI agents of the shard builds
1099482304 and 1100310296, that `Xvfb` exited, because another X server already listened on `:0`. The IDE drew on
the other server, and its paint stills are 1920x1200.

## Decision

1. **`lane-run` lets the recorder read the screen of a TeamCity build on Linux.** The conditions are a set
   `TEAMCITY_VERSION`, Linux, and a `DISPLAY` that is not empty. A developer host has no `TEAMCITY_VERSION`, so
   `lane-run` still never reads the screen of a person. The refusal of the JVM flag stays.
2. **A failed scenario keeps its video, and a passed one keeps it only in a build of `master`.** This decision was
   "every scenario keeps its video" until 2026-10-23. A passed scenario is the baseline that a failed one is compared
   with, and the baseline comes from `master`. A Safe Push check, a personal build and a build of another branch set
   `AIR_LANE_VIDEO=failed` and pass it with `--test_env`. The recorder then records each scenario as before, and at the
   end of a passed scenario it deletes the video. The manifest says why. An unset variable keeps every video, so a
   build keeps every video until its configuration sets the variable. The choice is a variable of the build, because
   the build configuration knows the branch, and `lane-run` stays free of the branch names of TeamCity. A flaky scenario
   keeps the video of its failed attempt, because each attempt is a bundle of its own. A later attempt in the same
   session that passes keeps its video too.

   The numbers of the first version of this decision follow. The shard build 1100310296 ran 56 scenarios in 15.1
   scenario minutes, with stills only. Its `bazel.logs.zip` is 181 MiB: 89 MiB of bundles, 71 MiB of
   `allure-results` and 18 MiB of other files. On the guest display of 1920x1080, 1952 bundles hold 3.07 MB of video
   per scenario minute. At the 1920x1200 of the CI frame, a shard then gets about 49 MiB of video. Since 2026-10-21 the recorder writes no Allure results
   by default, as the amended [ADR 0231](../../../../plugins/air/docs/decisions/0231-the-recorder-writes-the-allure-results.md)
   states. So a shard holds each video once, and it goes from 181 MiB to about 159 MiB. With the Allure results
   asked for, it grows to about 280 MiB.
3. **A run that is not green keeps its evidence in `lane/` of the outputs directory.** The end of the run and the hard
   stop copy the log directory and the process output of each affected IDE launch, the slow steps and the authored
   artifacts. The caps are 32 MiB per file and 256 MiB in all, and `evidence.txt` names what the caps cut. A green run
   copies nothing.
4. **Only `lane-run` makes these choices.** The lane daemon of a worker also has `TEST_UNDECLARED_OUTPUTS_DIR` in its
   environment, so the choices live in `lane/run.rs` and travel through the trace inputs and the monitor paths.

## Consequences

- A CI trace of a failed scenario has a video, and so has each trace of a build of `master`. A red CI run has its full
  `idea.log`, the output of its IDE processes and its dumps in `bazel.logs.zip`.
- The configuration of the Air UI builds sets `AIR_LANE_VIDEO` and passes it with `--test_env`. Until it does, every
  build keeps every video.
- The recorder reads whatever X server answers on the `DISPLAY` of the build, which is not always the `Xvfb` of the
  build. When its pixels are not BGRX, the recorder says why in the manifest and keeps the paint stills.
- The trace tools of IJAI-4382 add the step `air-trace teamcity-publish`, which packs each bundle into an artifact zip
  of its own. A build that runs that step stores each video a second time.
- A trace attachment outside a recorded scenario goes to `lane/attachments/` under `lane-run`. The lane daemon of a
  worker still drops it.

## Alternatives rejected

- **Keep the video of a failed scenario only, in every build.** It saves about 49 MiB per shard. A passed scenario is
  the baseline that a failed one is compared with. The copy of each attachment in `allure-results` cost more, 71 MiB of
  the 181 MiB, and it is off by default now. Since 2026-10-23 decision 2 takes this choice for each build that is not
  a build of `master`. A build of `master` still keeps the baseline.
- **Let `lane-run` read the branch from the property files of the TeamCity agent.** The Bazel step passes
  `TEAMCITY_BUILD_PROPERTIES_FILE` already, so no configuration change would be necessary. But `lane-run` would then
  know which branch is the baseline and how TeamCity names a personal build. One variable keeps that rule in the build
  configuration, and a person can set it for a local `bazel test` too.
- **Let a person pass the screen flag to a direct lane.** It brings back the risk that the rule exists for.
- **Copy every launch of every run.** A green run needs no `idea.log`, and the copy would add the full logs of every
  launch to every shard.
