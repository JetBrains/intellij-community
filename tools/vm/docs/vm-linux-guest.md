# A Linux guest for the Air UI lanes

**Status.** The Tart Linux worker was retired on 2026-10-08
([ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). The Docker
guest keeps the Linux guest model of this note, and its image carries Node. The rest of this note is history. The
current model is in the [VM guide](vm-ui-tests.md).

Whether the UI-test workers have to be macOS, and what it cost for them not to be. **They do not have
to be** — and since 2026-08-21 they are not: a Linux worker is what the controller leases when nobody
names a backend, and `--lane ui` is run and judged there. An Air UI flow test passes on it and the frame
it captures is a real one. Two of the three pieces below are done; the third — several lanes on several
displays in one VM — is not started. Every claim here was measured rather than argued. The controller is
in the [VM guide](vm-ui-tests.md), which is also where the sealed-macOS backend's evidence now
lives: that backend's separate open list was retired on 2026-08-23 once its golden was rebuilt and the
lane was finally measured on it.

For the host-side Bazel assembly and launch boundary, see
[Dev-build architecture](../../../../build/dev-build-architecture.md).

## Why ask

Almost everything hard about the current worker is hard *because the guest is macOS*, not because it is
a VM: TCC admission and the fail-closed grant audit, autologin plus `/etc/kcpassword`, `launchctl asuser
501`, the console-login wait, the Packer seal/audit pipeline, and the forced VirtioFS remount that exists
only because a macOS guest keeps dead nodes for host-rewritten files. Robot screenshots come back black
for the same reason — `java.awt.Robot.createScreenCapture` needs a Screen Recording grant a worker
deliberately does not have — and on X11 that call needs no permission at all.

The throughput argument is separate and larger: a macOS guest has exactly one Aqua session, so one VM
runs one lane. A Linux guest can host several independent X displays, so one VM could hold several lanes.
That is the reason to care, and it is also the part this note does *not* propose doing first.

## What is already true

None of this needed to be built; it was found.

**The tests are not macOS-coupled.** Every flow lane pins
`driver.robot.use.input.events=true` (`plugins/air/tests/integration/flow/src/flow/AirFlowLaneHost.kt`),
and the platform picks that same robot automatically on Wayland —
`IdeRobot.useInputEvents()` is `System.getProperty("driver.robot.use.input.events").toBoolean() ||
StartupUiUtil.isWaylandToolkit()`. Air's own input primitives run inside the IDE on that same robot
(`plugins/air/tests/integration/bridge/tests/AirUiTestInputBridge.kt`); no `CGEvent`, `NSEvent` or
`Robot.keyPress` appears in any Air test source.
`AcpDebugScreenshot.kt` paints a `BufferedImage` from the window rather than capturing the screen.

**IDE Starter already runs on Linux.** `LinuxIdeDistribution.linuxCommandLine` wraps a launch in
`xvfb-run --server-num=88` unless `DISPLAY` is already set; `XorgWindowManagerHandler` starts fluxbox and
verifies it through EWMH `_NET_SUPPORTING_WM_CHECK`; `getRunningDisplays()` enumerates Xvfb displays;
and `IdeFromCodeInstaller` — the dev-build path these lanes use — already selects the Linux command line
when `SystemInfoRt.isLinux`.

**The lanes do need a real display.** `AirIdeLaunch.kt` removes `java.awt.headless` on purpose, and JCEF
needs a real X server. Xvfb satisfies both.

**A Linux distribution cross-builds here, and does so today.** `//build:idea_air_lane_dist_linux`
assembles on this macOS arm64 host and is a genuine Linux build: `bin/product-info.json` reports
`Linux`/`aarch64` with launcher `bin/idea`, and `lib/native/linux-aarch64` where the macOS distribution
has `mac-aarch64`. `//plugins/air/tests/integration/ui:ui_daemon` builds under
`--define=air_lane_guest_os=linux` with its stub naming `idea_air_lane_dist_linux` and
`dev_launch_linux_aarch64_jbr`. This is piece 1 below, and it is done.

**A Linux worker is a cheap worker.** Measured on 2026-08-12 with `air-linux-1`, an
`ghcr.io/cirruslabs/ubuntu:24.04` clone sized 8 vCPU / 16 GiB / 80 GB:

| | macOS worker | Linux worker |
| --- | --- | --- |
| clone size | 400 GB sparse (50 GB golden) | 80 GB (20 GB image) |
| boot to `tart exec` | 32 s | **11 s** |
| image pipeline | Packer, seal, offline audit | `tart clone` and `apt-get` |
| guest agent | installed by our provisioning | preinstalled in the Cirrus image |

`tart exec` and `tart ip --resolver=agent` — the controller's only two channels into a guest — work
unchanged, and the guest user is `admin` with passwordless sudo, the same name the macOS image uses.

**The guest model transfers.** Both read-only shares mount with
`mount -t virtiofs com.apple.virtio-fs.automount /mnt/AirVmShares`, on the same tag and named by the same
`--dir` prefix. The parity layout builds without a fight: `/Users/develar/projects/idea-1` and
`/Users/develar/Library/Caches/JetBrains/MonorepoBazel` are ordinary creatable paths, with per-entry
symlinks onto the mount and `out` diverted to guest-local storage, and the host's Bazel outputs — the
cross-built distribution included — resolve through them. `Xvfb :88` plus `fluxbox` come up from
`apt-get`, and fluxbox registers itself through EWMH `_NET_SUPPORTING_WM_CHECK`, which is exactly what
`XorgWindowManagerHandler.isWmRunning` looks for. The linux-aarch64 JBR out of
`@dev_launch_linux_aarch64_jbr` runs in the guest and reports itself as JBR 25.0.4 with JCEF.

## What it would cost

Three pieces, in the order they have to happen.

### 1. A linux-aarch64 lane distribution — done

The dev-distribution fragment macros take one `target_platform` that selects both OS and architecture,
`preloaded_downloads_for_platform` names that platform's archives without a host `select`, and
`//build:idea_air_lane_dist_linux` puts the two together. `ui_lane_ide.bzl` selects the pair on
`--define=air_lane_guest_os=linux` on a macOS host. On a Linux x86_64 host the same define selects the
x86_64 Docker guest, whose distribution is the host build.

Two things are worth remembering about it. Every distribution fragment now requires a non-empty download
manifest, runs with `--preloaded-only`, and is network-blocked. The Linux manifest is therefore an enforced
inventory: a newly required archive fails the build until the platform-specific Bazel repository set declares
it; it can no longer fall through to a guest or host network download. And the macOS side changed too — it used
to take whatever the host `select` produced, so a lane built anywhere but macOS ARM64 would silently have
carried no preloaded JBR at all.

### 2. A guest-OS axis in the controller — done

`--backend linux` exists, and a worker built by it is verified up to the point where a lane would start:
`pool start air-linux-1` clones the public image, boots, provisions and reports parity ready in 75 s the
first time and 55 s warm, with both display services active, the window manager registered through the
EWMH property `XorgWindowManagerHandler` reads, both shares mounted, and the cross-assembled Linux
distribution visible through the share. `pool stop` and a restart work — and
`AirNewSessionShellTerminalGeneratedFlowUiTest` **passes** on it, one testcase in 38.56 s, against a
daemon holding a warm IDE.

`backend` names the hypervisor and `guestOs` what it runs; `--backend linux` is the one spelling for
`{tart, linux}`. Most of the guest model turned out to be shared, so what actually differs is a
five-entry `GuestOsProfile` — mount point, `chown`, the `ln` no-dereference flag, how `mount` names the
filesystem, how it is mounted — and these obligations, which are branches on the guest OS rather than
profile entries, because the two are not one machine with different paths:

| macOS worker | Linux worker |
| --- | --- |
| `RequireConsoleLogin`, autologin, `/etc/kcpassword` | nothing — no login session to wait for |
| `RequireCleanWorkerTCC`, the seal/audit pipeline | nothing — Linux has no TCC |
| `launchctl asuser 501 sudo -H -u admin` | `setsid env DISPLAY=:88 …` |
| `/sbin/mount_virtiofs com.apple.virtio-fs.automount` | `mount -t virtiofs com.apple.virtio-fs.automount` |
| forced remount before every product-input run | needed for the same reason, measured 2026-08-13 |
| Packer golden + `.air-seal.json` provenance | clone `ghcr.io/cirruslabs/ubuntu:24.04` and provision |
| `air-init-worker-storage` grows the APFS container | a cloud image grows its own root at boot |
| 120 GB root disk over a 50 GB image, 32 GiB RAM | 80 GB over a 20 GB image, 6 GiB RAM (16 GiB until 2026-09-29) |

Two things turned out easier than expected. The parity layout needed no fight —
`/Users/develar/projects/idea-1` and `/Users/develar/Library/Caches/...` are ordinary creatable paths on
Linux, with no SIP restriction on `/`. And the display seam fitted as designed: provisioning runs
`Xvfb :88` and `fluxbox` as systemd services, so `LinuxIdeDistribution.linuxCommandLine` takes its
"DISPLAY is already set" branch instead of starting a private `xvfb-run` per launch, and
`XorgWindowManagerHandler.startFluxBox` finds a window manager already registered and no-ops.

Three things the hypervisor and the guest decided for us, all found by trying rather than reading:

- **`tart suspend` is macOS-only, and `tart run` is where that bites.** `--suspendable` fails a Linux VM
  outright with "You can only suspend macOS VMs", so the flag is skipped and a Linux worker can never
  hold a warm daemon across `pool stop`. Its boot is about a third of a macOS worker's, which is the
  trade.
- **Root creates the worker's state directory; the worker user writes into it.** The first thing the
  controller does to a fresh Linux worker fails on its own root-owned directory without a `chown`, which
  macOS never needed because the parity step happened to do it first.
- **Provisioning must wait for the display, not for `systemctl enable`.** A display that is still
  starting looks exactly like one that failed, and the difference otherwise surfaces as an IDE that
  cannot open a window, minutes later and somewhere else.

### 3. Only then, isolated desktops

The throughput idea — one VM hosting N independent X displays, one daemon and one IDE per display — is
the reason to want any of this, and it is deliberately last. It changes worker identity from a VM name to
`(vm, display)`, gives each display its own daemon port and its own guest-local `WorkerData`, and turns
the lease's one-worker-one-lane rule into one-display-one-lane. None of that is worth designing before a
single class has run green on one Linux display, and the number that decides whether it is worth doing at
all — how many IDEs one VM can actually drive before they contend — does not exist yet.

## The four things the spike had to answer

1. **A class passes.** `AirNewSessionShellTerminalGeneratedFlowUiTest`, one testcase, 38.56 s of a 46.0 s
   iteration, zero failures — against the same class's 38.96 s on the macOS Tart worker the day before.
   It is the class the open list named as the sharpest available comparison, and it is green on both.
2. **The screenshot is a real frame.** 1400×1000, 223 distinct byte values, showing the project tree, the
   Air chat panel and the agent picker. On a macOS Tart worker the same capture is an all-black PNG,
   because `java.awt.Robot.createScreenCapture` needs a Screen Recording grant that a fail-closed worker
   deliberately does not have. On X11 it needs no permission at all, so **a Linux worker has the visual
   artifact a Tart macOS worker structurally cannot produce.**
3. **Warm timing is comparable, not merely acceptable.** 46.0 s per iteration against the macOS worker's
   43.2 s, both reusing a warm IDE (`build 15.7s stamp 0.2s push 141 jar(s) 0.3s ide reuse tests 46.0s`).
   Where Linux is markedly cheaper is everything around a run: an 11 s boot, 55 s to a provisioned worker,
   80 GB of disk and 16 GiB of RAM (the size on 2026-08-12; 6 GiB since 2026-09-29).
4. **The VirtioFS remount is needed, and the guide was wrong about why.** The stale-node problem is not a
   macOS quirk. Measured on `air-linux-1`: rename a file over itself on the host — which is what a Bazel
   build does to its outputs — and the guest's `stat` still answers the old size from cache while `open`
   fails with `ENOENT`. The remount is the only cure there too, and it carries the same constraint, since
   `umount` answers "target is busy" while anything in the guest still holds the mount. The controller
   brackets it either way: `startDaemon` remounts between two daemons, and a product-input change
   quiesces the live daemon and stops its IDE first.

Two things a reader of the screenshot should not misread. "Native file watcher executable is not found"
was not a Linux gap: no dev distribution shipped `fsnotifier` at the time, the macOS one included, so
every lane ran against an IDE with no file-change events at all. A dev distribution now carries the same
`bin` natives a production one does — `OsSpecificDistributionBuilder.copyNativeBinFiles`, declared to
Bazel as `@community//bin:bin_linux` for this guest — so a current screenshot should show neither that
warning nor the "cannot receive filesystem event notifications for the project" one that followed from it.
And the `Install Codex` notice is what the lane shows when the IDE finds no `codex`. That was this
guest's state when the screenshot was taken. The IDE gets a pinned `codex` from its test runfiles now,
so a current screenshot should not show that notice either.
[Codex as a test runtime](#codex-as-a-test-runtime) has the mechanism.

## What was tried and abandoned along the way

A hand-derived launch was attempted first and is not evidence either way. It read the class path out of a
`java_binary` stub that started `PreBuiltDevMain`. That class path holds the launcher module, not the IDE, so
`com.intellij.idea.Main` cannot start from it. The IDE starts from the `core-classpath.txt` of the distribution,
as IDE Starter and every dev row do. The hand-assembled command line also failed to resolve `PathClassLoader` at VM
init. That is a defect of the command line, not a finding about Linux. The supported path is IDE Starter's
`PrebuiltDevDistRunner` inside the lane's own test JVM, reached through the daemon the controller already starts. The
stub is gone.

## What the run cost to get to

Five failures, each one something the design had not predicted, all in guest-side plumbing rather than
anywhere near a test:

- **The daemon's JVM came from the stub, and the stub names the host's.** A macOS-host build hands a Linux
  worker a darwin `java`. Fixed by unpacking the linux-aarch64 JBR the lane already declares — the same
  archive `JBRResolver` would use to launch the IDE — into guest-local storage, keyed by archive name so a
  bump lands in a new directory, with the completion marker written last so an interrupted extraction is
  redone rather than trusted.
- **Every entry in a runfiles tree is a symlink**, so a search that asks `isDirectory()` before testing the
  name descends into the file it is looking for.
- **The guest had no Node**, and the run supervisor is a Node script — the first thing the controller
  executes in a guest, so the failure was `exit 127` with nothing to say why.
- **Ubuntu 24.04 ships Node 18, where `crypto` is not yet a global.** The supervisor now imports
  `randomUUID` from `node:crypto` instead of reaching for `globalThis`, which is the right shape for the
  one file that runs on whatever runtime its guest happens to have.
- **`mount` prints a mount differently on the two guests.** Splitting on `" on "` and stripping the
  parenthesis leaves ` type virtiofs` attached on Linux, and `umount` answers "no mount point specified"
  for the whole string. One expression now strips both, for both guests.

## The lane, green on 2026-08-21

`--lane ui` on `air-linux-1`, invoked with **no `--backend`** — the first run of the lane on its new
default runner: **23 of 23 passed, 0 failures**, `status: passed`, 4 m 45 s of tests behind a 78 s host
build (`build 78.5s stamp 0.2s push 20 jar(s) 0.2s ide remount tests 284.8s`). One container was skipped
and named its reason; see below.

`ide remount` in that line matters more than the count. A remount means the IDE was **relaunched**
mid-lane, and a relaunched IDE that never finished opening its project was the entire failing set of the
previous run. `AirFlowRecycleSmokeUiTest` ("failed reset recycles one warm IDE and retries on the
replacement") and `AirResumeSessionAfterRestartGeneratedFlowUiTest` both pass now, which is the
`AgentSettings` fix observed rather than argued.

### The run it replaces

`--lane ui` on 2026-08-19 was **18 of 22 passed**, 15 minutes, one warm IDE. Run twice, a fresh worker
apart, with an identical failing set both times — so that number was stable rather than a sample. For
comparison, the same lane four days earlier was 16/23 on the macOS Tart worker and 23/23 on Parallels.
Both of those macOS numbers are now superseded by the same-tree comparison of 2026-08-23 above; keep them
only as the sequence that led to it, and do not read a trend across them — the product, the harness and the
macOS golden all changed in between.

Two guest gaps were found and closed, and both were quiet rather than loud:

- **The image carries none of the libraries the IDE's own native code links against.** `libEGL.so.1` for
  `libskiko-linux-arm64.so`, and six more for `libcef.so`/`libjcef.so`. The IDE started anyway and the lane
  kept running; the only trace was `SEVERE - Failed to preload Skiko` in the guest `idea.log`. Now in
  `GuestPackages`, found with `ldd <so> | awk '/not found/'` rather than by guessing package names.
- **The JBR's GTK lookup** printed `Looking for GTK3 library... Not found.` on every launch; `libgtk-3-0t64`
  is now installed too. `-Djdk.gtk.version=2` in the lane's VM options does not change what it looks for.

Neither changed the failing set. All four failures are one shape — **a relaunched IDE never finishes
opening its project**. `ProjectFrameAllocator` reports `Cannot load project in 10 seconds`, and its
coroutine dump has `tool window pane creation` suspended in `ToolWindowManagerImpl.doInit` with
`tool window manager init` still `CREATED`. The screenshot says the same thing in one picture: a titled IDE
frame with an entirely empty interior.

**That was Air's own settings service, and it is fixed.** `AgentSettings.loadState` published a change event
while the service was still initializing; a listener reached back for the same service, parked forever
holding a read permit, and no background write action could start — which stops the platform dispatching
every `Dispatchers.EDT` task, project open included. It reproduced only on relaunches because only a
relaunch loads persisted settings that differ from the defaults. The deleted note
`linux-lane-relaunch-hang-plan.md` has the full account, the two harness bounds, and the thread dumps. Git
history keeps it. None of it was Linux-specific; this guest simply produces a real frame and is slow enough to cross the harness bounds first.

**The splash was tested and is not the cause.** The dump also has `frame allocator background` suspended in
`hideSplashWhenEditorOrToolWindowShown`, and a `splash` window sits in the fluxbox taskbar beside the empty
frame, which reads like the answer: a product build bakes `-Dsplash=true` into the distribution, and the
platform hides that splash only once an editor or a tool window is shown. It is not. With
`-Dnosplash=true` reaching all six launches of a run, the same class fails at the same operation with the
same 3-minute timeout. That coroutine is where the frame allocator sits during *any* project open, not the
thing holding it.

`AirFlowLaneHost.constantLaunchProperties` carries `nosplash` regardless, because a test IDE showing a
splash it never asked for is worth removing on its own — just do not read that change as this fix. It sits
in that map rather than in the shared starter, so it describes Air's own lane launches and no other team's,
and so the lane's `configKey` covers it.

## What a wrong diagnosis cost, and what names it now

On 2026-08-24 a session working against this pool concluded that passwordless sudo had gone from both Linux
workers, released the leases on `air-linux-1` and `air-linux-2`, and stopped with nothing delivered. Neither
worker had anything wrong with its sudo. The tooling produced the wrong answer twice over, and both halves
belong in this record, because each one read as evidence.

**A stale guest agent presented as a sudo failure.** `tart exec` lands as root, so every privileged guest
command is re-targeted with `sudo -H -u admin` — and the controller's refusal named
`filepath.Base(argv[0])`, which is therefore `sudo` for every command it has ever run. The exit code that
refusal carried, 64, came from the guest agent's own dispatch: `EX_USAGE`, an agent asked for a verb it does
not have, which is the ordinary shape of a worker last provisioned by the other checkout on this machine.
Established by reading the two argv builders rather than by re-running anything: `guest.AsUser` and
`guest.AsRoot` are the only shapes there are, and both put `sudo` first, so no exit code from inside a guest
could ever have been attributed to the program that produced it. The skill had even described the old
symptom as "a bare exit 64" — what nobody could see was whose 64 it was.

**A healthy Linux worker reported three macOS failures.** `vm.cmd status` ran TCC admission, the `who`
console read and the SSH host-key fingerprint read against a Linux guest, so `worker_tcc_admission_failed`,
`console=no` and `ssh_host_key=not-ready` sat on every healthy Linux row. And `parityReady` was a plain
`bool`, so `guest_init_stale` — the normal, self-repairing state of a pool shared between this machine's
`idea-1` and `idea-3` checkouts — arrived as an unexplained `false`. Established from the guest side rather
than from the field values, which is the part worth copying: the three probes now sit behind the same
`is_macos_guest()` guard the readiness gate uses, and the test that pins them asserts the fake guest channel
was never asked for `TCC.db`, `/usr/bin/who` or a host-key fingerprint at all. Reverting the guard makes it
fail by naming the question that leaked, not by comparing a nil.

What names it now. A refusal walks the wrapper shapes this controller builds and reports the first token
that is not one of them, so `false in air-linux-1 exited with 3 (--flag)` replaces `sudo … exited with 3`.
The controller's own agent verbs go further and name the agent, the verb, the worker and the agent's own
reply, and an exit 64 from one of them carries a sentence saying `EX_USAGE` and "an agent older than this
controller" — the 2026-08-24 diagnosis, moved out of a person's head and into the message. A Linux `status`
row reports the macOS questions as `null`, rendered `n/a` in text, and `parityError` carries the
refusal code beside `parityReady` — a code of the guest in every case, because a host path the controller
cannot resolve is reported once for the pool as `hostPathsError` and leaves each row's parity verdict
`null`. That last part was itself measured, on 2026-08-25: the Tart path had never resolved those paths, so
both Linux workers read `parity=not-ready(host_paths_unresolved)` until it did, and then read
`parity=ready` — they had been provisioned for this checkout the whole time, and nothing had asked them.
The shapes are in the guide, under
[Reading a refusal](vm-ui-tests.md#reading-a-refusal) and
[The Linux guest](vm-ui-tests.md#the-linux-guest).
A genuinely broken passwordless sudo stays legible without a probe for it, through one of the
exceptions to the withholding contract: a failed command's first stderr line travels when it begins with
`sudo:`, because that is the one line sudo itself writes before executing anything. The other is the guest
agent's own `usage:` or `Usage:` line, which its argument parsing prints before any guest work ran. Both
go through one helper — argued at `Guest::raw` and at `agent_refusal` — so neither can widen alone, and both
are deliberately no wider.

## Why the guest is validated and not just installed

Both real gaps in this guest were found by reading `idea.log` days later: an IDE that started without the
shared objects its own native libraries link against (`Failed to preload Skiko`, one SEVERE line, the lane
running on), and a display that nothing was serving. A provisioned worker that is merely *installed* still
accepts a run and still produces a report, which is why provisioning ends with the guest proving itself
rather than with `apt-get` exiting 0. Since 2026-08-25 the prover is a verb rather than a script:
`validate-guest` on `vm-guest-agent`, run as root immediately after `provision-guest` on every boot. Both
halves of the boot's guest work are verbs of that binary now, which is why the controller installs the agent
into a Linux worker before it runs either of them. Each answers a document rather than lines, and the
controller notes each document whole as the verb returns — the probe counts, and the packages this boot
installed against the ones it found already there — because a boot verb's streams are captured and echoed
nowhere, which keeps a first boot's `dpkg-query` and `ldd` chatter out of the log the report is in.

It checks four things in dependency order — and not cheapest first, because the second of them waits — and
each refusal names what it looked at and what it found:

- the display answers `xdpyinfo` — `linux_display_not_answering`, which also names the systemd unit that
  should be serving it. Not "the unit is enabled" and not "the unit is active": an Xvfb that is still
  starting and one that died on a bad `-screen` are both `active` for a moment, and only a client connecting
  tells them apart;
- a window manager is registered on it, read exactly as `XorgWindowManagerHandler.isWmRunning` reads it — the
  root's `_NET_SUPPORTING_WM_CHECK`, then the support window's self-reference. Nothing registered is
  `linux_window_manager_missing`; a property left behind by a fluxbox that exited is
  `linux_window_manager_stale`. Two codes because they are two repairs: a unit to look at, and a worker to
  recycle. **Only the first of the two is waited for**, on the same one-probe-a-second minute
  `provision-guest` spends on the display, and the report says which probe answered. Absent means fluxbox has
  not taken the display yet — `air-fluxbox.service` is `Type=simple` and `Requires=air-xvfb.service`, so
  systemd starts it when Xvfb is *forked*, which on a first boot leaves it about a second — and a later probe
  can improve on that. Stale means fluxbox ran and exited, which no wait improves, so it refuses on the first
  pass; waiting it out would spend a minute turning the one real fault here into a timeout. That wait was
  bought by a measurement: on 2026-08-25 a `pool recycle` of `air-linux-2` refused
  `linux_window_manager_missing` on a guest that was healthy thirty seconds later, same fluxbox pid, journal
  unbroken. The retired script asked once too, so the race was inherited rather than introduced — what the
  port added was the name to hang the fix on;
- the Node the run supervisor will be started with runs at the path the controller configured, not whatever
  is first on PATH — `linux_node_not_runnable`, which says why it matters: the supervisor's children are Node
  programs, so a guest without that binary fails as a bare `exit 127` from the only command that mattered;
- and `ldd` finds nothing missing under the staged IDE root, which is where the JBR, JCEF and Skiko `.so`
  files live, since `skiko.library.path` points inside the distribution. `linux_shared_object_unresolved`
  names each object with the sonames it wanted, caps the list with a count of what it did not show, and says
  where to add the package that carries them. A root that exists and cannot be *read* is
  `linux_ide_root_unreadable` — the one distinction the script's `2>/dev/null || true` threw away.

**Where the exit codes went.** The script answered its five failure classes with a numeric vocabulary of its
own, because a guest *script* has no envelope to speak in and the host withholds every non-envelope shape of
a script's stderr by contract (`Guest::raw` in `crates/avl-host-sys/src/guest.rs`). Every refusal above travels
as a code and a sentence at one status instead, `AgentExit::Refused` in `avl_wire::supervisor` — and the window
manager's two say which of its two repairs an operator has, where one integer had covered both.
[ADR 0108](decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md) owns that argument, names the five
numbers, and says why the history of them stays there.

The validator holds no package list and no soname-to-package mapping — it is passed a display, a Node path
and an IDE root, and none of the three is the list. `GUEST_PACKAGES` in
`crates/avl-host-sys/src/guest/linux.rs` stays the single list; the validator reads what the guest *has*, so a package added there is covered here without
being named here twice, and the mapping from `libEGL.so.1` to `libegl1` — the third copy that using the list
would need — never exists. A fresh worker is provisioned before anything is staged into the IDE root, so a
first boot's sweep finds nothing to read and says so in its report rather than passing quietly; it runs again
on every boot, and a warm worker is checked against the IDE it actually holds.

Established on the host, not in a VM, and the port widened that rather than replacing it. Where the script
was exercised against stubbed `xdpyinfo`, `xprop`, `node`, `ldd` and `find`, the two verbs are exercised
against stubs for those plus `apt-get`, `dpkg-query`, `systemctl`, `netplan` and `install`, on a host with no
X server, no systemd and no root — reaching every refusal above and every one the provisioner has, including
both of the window manager's. Both wait loops are exercised to exhaustion in microseconds, because each verb
takes its pause as a seam rather than sleeping itself. The shared-object sweep walks a real
temporary directory rather than a table, because "which files under this root look like shared objects" is a
question a directory answers more honestly than a fixture would. That the self-check is
*reached* was established differently, and by accident: the field is required rather than optional, so
between it landing and its caller passing it every Linux boot refused `linux_validation_unset` — loudly, in
one wave, with a message another session could act on without asking anybody. An optional field would have
produced a green suite over a controller that had quietly stopped validating its guests.

The base image under all of it is now pinned by digest — `ghcr.io/cirruslabs/ubuntu@sha256:e018055c…`,
resolved on 2026-08-25 from the registry with the same two calls `verify-base-digest.sh` already made for
the macOS base, and held in the skill's `provision/versions.env`, which the controller embeds at compile time
(`pins` in `crates/avl-base/src/config.rs`) and passes to `tart clone`. That pin is the entirety of
a Linux worker's provenance: there is no seal and no audit here, so the package set a worker starts from and
the passwordless sudo it inherits — which nothing in this repository installs — are properties of those
bytes and of nothing else.

## What to do next

1. ~~**Run a full lane on both guests at one commit.**~~ **Done, 2026-08-23**, and it answered the
   question this note was written around. At one tree the lane is 23 of 23 here and 8 of 24 on the Tart
   macOS worker, and the join is one-sided: 15 classes fail only on macOS, 8 pass on both, **none fail on
   both**. So the two guests were worth having for exactly the reason claimed — the diff attributes a
   failure — and the attribution came out entirely on the macOS side, at a focus/window-activation
   mechanism that X11 does not have. The run, the per-class table and the mechanism are in the
   [ADR 0113](../../../../plugins/air/docs/decisions/0113-the-macos-lane-is-red-on-window-activation.md); the product side is
   [IJAI-1228](https://youtrack.jetbrains.com/issue/IJAI-1228). Note the denominators differ on purpose:
   the macOS golden installs Codex, and this guest held none at that tree. Codex is a declared test
   runtime now, so both guests read it from the test runfiles.
2. **Then decide about isolated desktops**, with a number rather than a hope — how many IDEs one VM drives
   before they contend. The 11 s boot and 6 GiB footprint make several VMs a real alternative to several
   displays in one, and that comparison did not exist when this note was written.

The Linux preload set needed no work: a whole lane's guest run log carries not one `* Downloading` line, so
`_LINUX_AARCH64_GUEST.exhaustive` is now `True` and the next undeclared URL fails the build instead of
being fetched over the guest's NAT.

## Codex as a test runtime

**Codex is a declared Bazel test runtime now.** The runtime lives under `tests/tools/codex`. It pins the
version, it carries the locked npm dependencies, and it selects the native package of the machine that
executes the tests. The IDE reaches it through one launcher directory the launch prepends to PATH. Pi has
the same shape, under `tests/tools/pi`.
[ADR 0137](../../../../plugins/air/docs/decisions/0137-agent-clis-are-declared-bazel-test-runtimes.md) states the decision, and
[ADR 0047](decisions/0047-agent-clis-are-provisioned-per-boot.md) holds the per-boot install it replaced.

VM preparation therefore installs and probes no agent CLI. `install-agent-clis`, its pins and its four
refusal codes are gone, and so is `CODEX_BIN`. A cold boot reaches the network once, for `apt` in
`provision-guest`. A missing input or a wrong version fails before the IDE starts, on a host and in a
guest alike. No class skips for a missing CLI. An existing image can keep an unused installation, and
no image rebuild is required.

**Measured on 2026-08-28 with `air-linux-2`, on the cold boot after a recycle.** This measurement is the
history of the per-boot install, and not of the runtime that replaced it. `install-agent-clis`
reported `reused: false` there, and a second, warm boot reported `reused: true` and ran no npm step.
`--lane ui` is **26 of 26 passed**, 411.3 s of tests. On `--lane ui-real` both real-CLI classes executed
and passed, where that lane ended in `all_tests_skipped` before.

Scope discipline, revised 2026-08-21: Linux is **the gate for `--lane ui`**, and the controller selects it
by default. That is not a claim that Linux covers more; it is that the lane asserts Swing/AWT component
behavior and nothing else, so the guest that runs it cheapest and shows a real frame should be the one that
runs it. `gui-chat`, `ui-real` native-input coverage, native menus, macOS keymaps and WindowServer stay on
a macOS guest, named explicitly with `--backend tart` or `--backend parallels`, and Parallels remains the
only guest with Peekaboo and real TCC grants.

The consequence worth stating plainly: a red Linux run is now a verdict about the product, not a guest gap
awaiting a macOS confirmation. That inverts the reading the earlier revisions of this note asked for, and
it is safe to invert precisely because the one failing set this guest ever produced turned out to be an Air
bug that reproduced everywhere.
