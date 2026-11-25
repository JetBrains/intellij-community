# A TeamCity lane run records video and keeps the evidence of a run that is not green

## Context

A TeamCity Air UI build runs each lane through `lane-run` under a Bazel test. Without this decision, the recorder takes
paint stills only. The full `idea.log`, the dumps and the authored artifacts stay below `TEST_TMPDIR`, and Bazel
deletes that directory after the test.

The rule against the screen protects a person. The screen of a developer shows everything else that is open. A
TeamCity agent runs one build at a time, and no person works on its display.

The UI agent of TeamCity runs a GNOME desktop on `:0`. A recording of that display shows the wallpaper, the dock and
the icons around the IDE frame. The agent image belongs to infra, and every UI build of the monorepo shares it.

The host probe of `lane-run` refuses a display without a window manager. A keyboard scenario needs the X input focus
on the frame of the project.

The requirements are in [the lane-daemon verb](../../../../spec/lane-controller.spec.md#the-lane-daemon-verb) and in
[Capture and video](../../../../spec/scenario-trace.spec.md#capture-and-video).

## Decisions

1. **`lane-run` lets the recorder read the screen of a TeamCity build on Linux.** The conditions are a set
   `TEAMCITY_VERSION`, Linux, and a `DISPLAY` that is not empty. A developer host has no `TEAMCITY_VERSION`. The
   refusal of the screen property as a JVM flag stays.
2. **A failed scenario keeps its video, and a passed one keeps it only in a build of `master`.** A passed scenario is
   the baseline for a failed one, and the baseline comes from `master`. Other builds pass `AIR_LANE_VIDEO=failed` with
   `--test_env`. The recorder then deletes the video at the end of a passed scenario, and the manifest says why. An
   unset variable keeps every video. Each attempt is a bundle of its own, so a flaky scenario keeps the video of its
   failed attempt.
3. **A run that is not green keeps its evidence in `lane/` of the outputs directory.** The end of the run and the hard
   stop copy the logs and the process output of each affected IDE launch. They also copy the slow steps and the
   authored artifacts.
   The caps are 32 MiB per file and 256 MiB in all. `evidence.txt` names what the caps cut. A green run copies
   nothing.
4. **Only `lane-run` makes these choices.** The lane daemon of a worker also sees `TEST_UNDECLARED_OUTPUTS_DIR`. So
   the choices live in [run.rs](../../../../crates/avl-guest/src/lane/run.rs) and travel through the trace inputs.
5. **`lane-run` records an X display of its own, with the window manager `air-lane-wm`.** When decision 1 applies,
   `lane-run` starts Xvfb with `-displayfd` at 1920x1080x24 before the host probe. Then it runs
   [the window manager](../../../../crates/avl-guest/src/lane/window_manager.rs) on a thread of its own. The window
   manager maps windows, applies configure requests, and moves the input focus. It sets the EWMH properties that the
   probe and the driver read, and paints the root a neutral gray. It does not reparent and draws no decoration, so the
   IDE gets `_JAVA_AWT_WM_NONREPARENTING=1`. A host without Xvfb keeps the display of the build and gets a warning.
6. **A TeamCity lane run shows while it goes on.** `lane-run` appends a progress line for each scenario to the file of
   `AIR_LANE_LIVE_LOG`. It publishes the video and the last picture of a failed scenario through
   `air-trace teamcity-publish`. The results still come only from the JUnit XML.

## Alternatives rejected

- **Keep the video of a failed scenario only, in every build.** A build of `master` keeps the passed baseline.
- **Let `lane-run` read the branch from the property files of the TeamCity agent.** One variable keeps the branch rule
  in the build configuration, and a person can set it for a local `bazel test`.
- **Let a person pass the screen flag to a direct lane.** It brings back the risk to the screen of a person.
- **Record the display of the build, and crop the stills in the viewer.** The video and the stills still carry the
  desktop, and each viewer needs the crop.
- **Change the Xvfb step of the build configuration.** The step cannot take `:0` from GNOME, and a display of the run
  works the same on every agent.
- **fluxbox from the agent image.** The image belongs to infra, and a package request makes the lane wait for a change
  outside the repository.
- **mutter.** Ubuntu ships the program in a separate debug package, and its compositor needs OpenGL on the Xvfb.
- **Copy every launch of every run.** A green run needs no `idea.log`.

## Consequences

- A CI trace of a failed scenario has a video, and so has each trace of a build of `master`.
- A red CI run has its full `idea.log`, the output of its IDE processes and its dumps in `bazel.logs.zip`.
- A CI still is 1920x1080 and shows the IDE frame on a plain gray root.
- The build must still set `DISPLAY`, because decision 1 reads it.
- `air-trace teamcity-publish` packs each bundle into an artifact zip of its own. A build that runs that step stores
  each video a second time.
- A trace attachment outside a recorded scenario goes to `lane/attachments/` under `lane-run`. The lane daemon of a
  worker drops it.
