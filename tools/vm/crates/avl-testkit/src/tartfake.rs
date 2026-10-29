//! The one fake hypervisor, and the answer files it reads.
//!
//! Eight host suites need a hypervisor they can point `TART_BIN` or `AIR_VM_PARALLELS_BIN` at, and each of them once
//! grew its own shell script over the subset of verbs its paths reached. The copies then drifted: `list` answered a
//! JSON fixture in one suite and a hard-coded `[]` in another, `--version` honoured an exit-code file in one copy
//! and not in the others. This is their union - one script, every verb, and every answer in a file beside it - so
//! what a suite gets is decided by what that suite seeds rather than by which copy it inherited.
//!
//! It stays a real process on purpose. The argv shape is half of what these suites check - the `--dir=` share
//! grammar, the guest argv, whether a tty was asked for - and an in-process mock would agree with whatever it was
//! passed. The version floor arrives as a parameter rather than being read from the Tart backend, because that
//! crate's own tests use this one and a dependency back would close a cycle.
//!
//! # The binary name is the backend
//!
//! The script serves every backend, and it reads its own name to decide which one it is. That is not a
//! convenience: `tart` and `prlctl` are two programs, and the one thing the two backends genuinely disagree about
//! is how a command reaches the guest. `tart exec` takes an argv, so the fake `tart` matches on the whole argv.
//! `prlctl exec` takes a single shell string it must re-parse, so the fake `prlctl` matches inside its third
//! argument - which is only possible because the controller really did collapse the command into one string. A
//! fake that matched the whole argv for both would let a concatenating call site pass unnoticed. `docker exec`
//! takes an argv as `tart exec` does, so the fake `docker` shares the `tart` exec arms.
//!
//! # Verbs
//!
//! The script records every argv, then answers the verbs the host tree spawns. Each verb's exit code comes from a
//! file, so the default is what an unseeded suite sees. [`Answer`] names every file and what reads it. Before any
//! verb, [`Answer::KilledVerb`] makes a call die by SIGKILL and [`Answer::SilentVerb`] makes it exit 0 with nothing
//! printed, for every fake of the directory, so a suite can show what a probe without an answer does.
//!
//! The `tart` verbs, which only the fake `tart` answers:
//!
//! - `ip` answers the loopback unconditionally.
//! - `run` fails by default, and that is the honest answer: a fake that was told nothing about the VM it was asked
//!   to boot did not boot one. A test that wants a live one seeds [`Answer::RunSleeps`], and the sleep is in a
//!   child rather than an `exec` - the recorded identity is `ps -o command=`, so a process that replaced its own
//!   argv would stop matching the receipt written a moment earlier.
//! - `exec -i <vm> <agent> relay <port>`, that whole argv and nothing more, becomes a relay to `127.0.0.1:<port>`
//!   on the host. That is how a suite over the production channel reaches a loopback listener of its own: the
//!   relay bytes go through the fake like they go through `tart exec`. The arm comes before the verb files, so a
//!   seeded `relay` file cannot hide it. See the relay of the script below.
//! - `exec` then answers by verb: the first argument without a `/` that has a file seeded by
//!   [`Fake::answer_exec_verb`] prints that file, exit 0. That is how a suite over the production guest channel
//!   answers the agent's `active` and `cancel` apart.
//! - `exec` serves the agent's `read-file <path>` from [`Answer::ReadFile`] on stdout and [`Answer::ReadFileReceipt`]
//!   on stderr, which is how a pull runs without a guest. A suite that seeds neither `read-file` file falls through
//!   to the shared `exec` answer, so a pull still reads its bytes from [`Answer::ExecStdout`]. A Parallels pull is a
//!   `base64` in a `prlctl exec` shell string, and it reads the shared answer too.
//!
//! The `prlctl` verbs, which only the fake `prlctl` answers:
//!
//! - `stop`, `start` and `suspend` are a state machine, because the operations under test are sequences rather
//!   than single calls: a `stop` has to make the *next* `list` say stopped, or the wait loop after it never
//!   returns. Each one copies a template the suite wrote over [`Answer::ListJson`]. A suite that seeded no
//!   template gets the state it already had.
//! - `exec` matches inside its third argument, for the reason above. A shell string that ends in `relay <port>`,
//!   with each word quoted or not, becomes the same relay on the host: the `prlctl` spelling of the `tart` relay
//!   arm.
//!
//! The `docker` verbs, which only the fake `docker` answers. One fake holds one container, so a Docker pool of
//! `avl_host_testkit` has one slot:
//!
//! - `version` prints [`Answer::DockerVersion`], `linux/arm64` when unseeded, and exits from
//!   [`Answer::VersionExit`].
//! - `inspect --type container --format <format> <name>` answers from [`Answer::ContainerState`], a
//!   `<status>/<exit code>` such as `running/0`. An `inspect` without `--type container` exits 2, so a suite proves
//!   the controller never asks for an object of another type. No such file is no container: exit 1, as
//!   `docker inspect` of an unknown name exits. A format that asks for `.State.Running` gets `true` or `false`,
//!   derived from the same file. A format that asks for `.Id` gets [`Answer::ContainerId`]. A failed read of the
//!   state is named on stderr, and the exit stays 0, so the controller's refusal of an answer without a state
//!   carries the reason.
//! - `create`, `start`, `stop` and `rm` are a state machine over that file, like the `prlctl` verbs: `create`
//!   writes `created/0`, `start` writes `running/0`, `stop` writes `exited/0` and `rm` removes it. Each exits from
//!   [`Answer::Exit`], and a nonzero exit changes nothing. A seeded [`Answer::StartedState`] is what `start` writes
//!   instead, for a container whose entrypoint exits at once.
//! - `create` also prints a new id, `fake-container-<n>`, and writes it over [`Answer::ContainerId`], so a
//!   container made again has another id, as on the engine.
//! - `rm` without `--force` exits 1 for a running container and changes nothing, as the engine refuses it.
//! - The images are a list of references. `build --tag <ref>`, a `pull <ref>` that succeeded and `tag <src> <ref>`
//!   add `<ref>`, and `image rm <ref>` removes it. `image inspect <ref>` exits 0 when the list holds `<ref>`, or
//!   when [`Answer::ImagePresent`] exists, which makes every reference present; else 1. With a `--format` that
//!   names `Labels` it prints [`Answer::ImageRevision`]. `build`, `tag` and `image rm` exit from [`Answer::Exit`],
//!   `pull` from [`Answer::PullExit`], 1 when unseeded, because a fake told nothing about a registry has no image
//!   there, and `buildx` (the multi-platform publish) from [`Answer::PushExit`].
//! - `exec -i <name> <agent> relay <port>` is the relay of the `tart` arm, and the other `exec` arms are the
//!   `tart` ones too.
//! - `volume`, `logs` and anything else exit from [`Answer::Exit`].
//!
//! The `container` verbs, which only the fake Apple `container` answers. It holds one container in the state file of
//! the fake `docker`, [`Answer::ContainerState`], and prints it as the Apple CLI does:
//!
//! - `system status --format json` prints [`Answer::ContainerSystem`]. No such file is a server that is down: it
//!   prints `{"status":"unregistered"}` and exits 1. `system start` exits from [`Answer::ContainerStartExit`], and a
//!   start that succeeds copies [`Answer::ContainerSystemStarted`] over the status, or writes a running server of
//!   version 1.5.0 whose install root is the parent of the fake's directory, the grandparent of the executable.
//!   `system stop` exits 3: the controller must never stop the server of the login session.
//! - `inspect <name>` prints `[{"configuration":{"id":…},"id":…,"status":{"state":…}}]`: `running` for `running/*`,
//!   `stopped` for `created/*` and `exited/*`, and the word itself for any other state. No state file is
//!   `container not found`, exit 1.
//! - `create`, `start`, `stop` and `delete` are the state machine of the fake `docker`. `create` prints the name and
//!   writes it over [`Answer::ContainerId`], because the Apple id is the name. `delete` without `-f` or `--force`
//!   exits 1 for a running container.
//! - `build … -t <ref> …` and `image tag <src> <ref>` add `<ref>` to the images, and `image delete <ref>` removes it.
//!   `image load -i <file>` adds each line of the file, so an archive of the fake is the list of its references.
//!   `image save … -o <file> <ref>` writes `<ref>` into the file. `image inspect <ref>` exits as the `docker` one and
//!   prints a JSON array whose variant labels hold [`Answer::ImageRevision`]. `image pull` and `image push` exit 3:
//!   the controller pulls and publishes the image archive over the file mirror.
//! - `volume inspect` says `volume not found`, exit 1. `logs` prints [`Answer::ContainerLog`], and `logs --boot`
//!   prints [`Answer::ContainerBootLog`]. `builder`, `volume delete` and anything else exit from [`Answer::Exit`].
//! - The `exec` arms are the `tart` ones.
//!
//! The fake host `curl`, which `AIR_VM_HOST_CURL` points at. It answers the two transfers of the image archive:
//!
//! - `… --output <file> <url>` copies [`Answer::MirrorArchive`] to the file, exit 0. No such file is an archive the
//!   mirror does not hold: `curl: (22) The requested URL returned error: 404` on stderr, exit 22.
//! - `… --upload-file <file> --header @<header> <url>` copies the file to [`MIRROR_UPLOAD`], the header file to
//!   [`MIRROR_HEADER`], and the permission column of `ls -ln` of the header file to [`MIRROR_HEADER_MODE`]. It exits
//!   from [`Answer::PushExit`].
//!
//! The `container.cmd` verbs, which only the fake script of the `testing-ui` skill answers. One fake holds one
//! container, as the skill's script does. The state file is [`Answer::ContainerLinuxState`], `running` or `exited`:
//!
//! - `start` writes `running`, records its environment in [`CONTAINER_LINUX_START_ENVIRONMENT`], and writes under
//!   `<checkout>/out/testing-ui`, four levels above its own directory as for the real script, the three files the
//!   real script and its guest write: `container.ctl_port` with the
//!   port of [`Answer::ContainerLinuxPort`], `container.ctl_bearer` with [`CONTAINER_LINUX_BEARER`], and `container.json` with
//!   [`CONTAINER_LINUX_NOVNC_URL`] and [`CONTAINER_LINUX_VNC_PASSWORD`]. A start without a seeded port exits 2.
//! - `stop` writes `exited` and removes the three files.
//! - `list` prints the container line `<name>  running  <checkout root>` with [`Answer::ContainerLinuxRoot`] as the root,
//!   then the indented detail lines, while the state is `running`; else the one line the real script prints for
//!   no container.
//! - `exec` is the `tart` exec arms, as on `docker`: the per-verb answers and the shared exec answer.
//! - [`Answer::ContainerLinuxFailedVerb`] names the one verb that exits 1 with a line on stderr, for a runtime that
//!   does not answer or an image build that failed.
//!
//! The shared verbs, which both answer:
//!
//! - `--version` and `list` exit 0 unless a suite seeds the code. Both backends' gates check what a hypervisor too
//!   old or too broken to answer does to the version gate.
//! - `list` answers [`Answer::ListJson`], which is the JSON of `tart list --format json` and of
//!   `prlctl list -a -i -j` alike. `tart list --quiet` is a line-for-line name list rather than JSON, so it is a
//!   second question with a second answer file, [`Answer::ListQuiet`]. `prlctl` has no `--quiet` listing.
//! - `status` answers [`Answer::Status`], which only `prlctl` has a verb for. It stays shared because the observer
//!   drives both backends over one set of answers.
//! - `exec` prints [`Answer::ExecStdout`] and [`Answer::ExecStderr`] and exits from [`Answer::ExecExit`]. This is
//!   the answer for a guest command no backend-specific arm claimed.
//! - Anything else - `clone`, `set`, `delete`, and `tart stop` - is recorded and exits from [`Answer::Exit`],
//!   because for those the controller cares that it was asked, not what came back.
//!
//! # Windows
//!
//! A Windows host runs no shell script, and it has the Docker backend only. There the fake `docker` is the program of
//! [`crate::fakebin`], a port of the `docker` verbs of the script, linked into the directory as `docker.exe`. The
//! answer files and the call log are the same. A fake `tart` or `prlctl` does not exist on Windows.
//!
//! # One file per content
//!
//! The executable of every fake is a hard link to one file that all test processes share, the script on Unix and the
//! program on Windows. The file is named by its content (`crate::shared`), so only the first process after a change of
//! the fake writes a new executable file, which macOS scans at its first exec.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::Arc;

#[cfg(test)]
mod tests;

/// The hypervisor CLI one fake stands in for. It is the file name of the installed executable, and the script
/// reads it to select the verbs and the `exec` grammar of that backend.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Binary {
    /// The fake `tart`, which `TART_BIN` points at.
    Tart,
    /// The fake `prlctl`, which `AIR_VM_PARALLELS_BIN` points at.
    Parallels,
    /// The fake `docker`, which `DOCKER_BIN` points at.
    Docker,
    /// The fake Apple `container`, which `CONTAINER_BIN` points at.
    Container,
    /// The fake host `curl`, which `AIR_VM_HOST_CURL` points at.
    Curl,
    /// The fake `container.cmd` of the `testing-ui` skill, installed where the checkout's would be. Unix only: a
    /// POSIX shell script. A Unix host runs the `.sh` beside the configured `.cmd`, so the fake is installed under
    /// both names.
    ContainerLinux,
}

impl Binary {
    /// The executable's file name, which is what the script dispatches on.
    pub const fn file_name(self) -> &'static str {
        match self {
            Self::Tart => "tart",
            Self::Parallels => "prlctl",
            Self::Docker => "docker",
            Self::Container => "container",
            Self::Curl => "curl",
            Self::ContainerLinux => "container.cmd",
        }
    }
}

/// The bearer of the control port that the fake `container.cmd` writes, which a fake control port checks.
pub const CONTAINER_LINUX_BEARER: &str = "0123456789abcdef0123456789abcdef";

/// The noVNC page the fake `container.cmd` writes into `container.json`.
pub const CONTAINER_LINUX_NOVNC_URL: &str = "http://127.0.0.1:1/vnc.html";

/// The VNC password the fake `container.cmd` writes into `container.json`.
pub const CONTAINER_LINUX_VNC_PASSWORD: &str = "pw";

/// The name of the one container of the fake `container.cmd`, the first field of its `list` line.
pub const CONTAINER_LINUX_CONTAINER: &str = "ui-fake";

/// The environment the last `container.cmd start` ran with, beside the executable: one `NAME=value` line per
/// variable the real script reads.
pub const CONTAINER_LINUX_START_ENVIRONMENT: &str = "container-linux-start-env.txt";

/// One answer file beside the executable. An exit-code file holds a decimal code; a flag file only has to exist.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Answer {
    /// Standard output of `--version`.
    Version,
    /// Exit code of `--version`, default 0.
    VersionExit,
    /// Standard output of `list` without `--quiet` (`tart list --format json`, `prlctl list -a -i -j`). Unseeded,
    /// `list` prints nothing, which is not the same answer as the empty pool `[]`.
    ListJson,
    /// Standard output of `tart list --quiet`: one name per line.
    ListQuiet,
    /// Exit code of `list`, default 0.
    ListExit,
    /// Standard output of `status`.
    Status,
    /// Exit code of `status`, default 0.
    StatusExit,
    /// Standard output of an `exec` no backend-specific arm claimed.
    ExecStdout,
    /// Standard error of that `exec`.
    ExecStderr,
    /// Exit code of that `exec`, default 0.
    ExecExit,
    /// `tart exec ... read-file <path>`: the file it prints on stdout, exit 0.
    ReadFile,
    /// What that `read-file` prints on stderr after the file: the agent's envelope with the receipt.
    ReadFileReceipt,
    /// Flag: `tart exec ... read-file ...` exits 1. Wins over [`Answer::ReadFile`].
    ReadFileFails,
    /// Output of `tart run`, printed before it sleeps or exits.
    RunOutput,
    /// Exit code of a `tart run` that does not sleep, default 1.
    RunExit,
    /// Flag: `tart run` stays alive for 30 s in a child `/bin/sleep`, then exits 0.
    RunSleeps,
    /// Exit code of every verb nothing above answers (`clone`, `set`, `delete`, `tart stop`, ...), default 0.
    Exit,
    /// `prlctl stop` copies this over [`Answer::ListJson`] when it exists.
    StateStopped,
    /// `prlctl start` copies this over [`Answer::ListJson`] when it exists.
    StateRunning,
    /// `prlctl suspend` copies this over [`Answer::ListJson`] when it exists.
    StateSuspended,
    /// Exit code of `prlctl suspend`, default 0.
    SuspendExit,
    /// `prlctl exec` whose shell string runs `/usr/bin/who`: its output, exit 0.
    Who,
    /// `prlctl exec` whose shell string reads `autoLoginUser`: its output.
    Autologin,
    /// Exit code of that `autoLoginUser` read, default 0.
    AutologinExit,
    /// Exit code of a `prlctl exec` whose shell string touches `kcpassword`, default 0.
    KcpasswordExit,
    /// Standard output of `docker version`. Unseeded, it is `linux/arm64`; a `HostPool` seeds the platform of the
    /// architecture it was built with, so the gate passes for that guest.
    DockerVersion,
    /// The one container of the fake `docker`, as `<status>/<exit code>`. Absent is no container.
    ContainerState,
    /// What `docker start` writes over [`Answer::ContainerState`] when seeded, instead of `running/0`.
    StartedState,
    /// Flag: `docker image inspect` finds the image. `docker build` creates it.
    ImagePresent,
    /// The id of the one container, which `docker inspect --type container --format {{.Id}}` prints.
    /// `docker create` writes it.
    ContainerId,
    /// Exit code of `docker pull`, default 1. A `0` makes the pulled reference present.
    PullExit,
    /// Exit code of `docker buildx build --push`, the publish, default 0, and of the upload of the fake `curl`.
    PushExit,
    /// What `docker image inspect --format {{index .Config.Labels …}}` prints: the revision label of the image.
    /// Unseeded, it prints nothing, as an image without the label does.
    ImageRevision,
    /// What `container system status --format json` prints. Absent is a server that is down.
    ContainerSystem,
    /// What `container system start` copies over [`Answer::ContainerSystem`] when seeded.
    ContainerSystemStarted,
    /// Exit code of `container system start`, default 0. A nonzero exit starts nothing.
    ContainerStartExit,
    /// What `container logs` prints.
    ContainerLog,
    /// What `container logs --boot` prints.
    ContainerBootLog,
    /// The image archive that the file mirror holds, which a download of the fake `curl` copies: one image reference
    /// per line, which `container image load` adds. Absent is an archive the mirror does not hold.
    MirrorArchive,
    /// The state of the one container of the fake `container.cmd`, `running` or `exited`. Absent is no container.
    /// `start` and `stop` write it.
    ContainerLinuxState,
    /// The TCP port that `container.cmd start` writes into `container.ctl_port`: the fake control port of the suite.
    ContainerLinuxPort,
    /// The checkout root that `container.cmd list` names in its container line.
    ContainerLinuxRoot,
    /// The first argument of the calls of the fake `container.cmd` that exit 1 with a line on stderr.
    ContainerLinuxFailedVerb,
    /// The first argument of the calls of any fake of this directory that exit 0 and print nothing, such as
    /// `inspect` or `--version`: a probe that gave no answer.
    SilentVerb,
    /// The first argument of the calls of any fake of this directory that a SIGKILL ends before they print, so the
    /// runner reads exit 137: a probe that a signal ended.
    KilledVerb,
}

impl Answer {
    /// The file the script reads, in the fake's directory.
    pub const fn file_name(self) -> &'static str {
        match self {
            Self::Version => "version.txt",
            Self::VersionExit => "version-exit.txt",
            Self::ListJson => "list-json.txt",
            Self::ListQuiet => "list-quiet.txt",
            Self::ListExit => "list-exit.txt",
            Self::Status => "status.txt",
            Self::StatusExit => "status-exit.txt",
            Self::ExecStdout => "exec-stdout.txt",
            Self::ExecStderr => "exec-stderr.txt",
            Self::ExecExit => "exec-exit.txt",
            Self::ReadFile => "read-file.bin",
            Self::ReadFileReceipt => "read-file-receipt.txt",
            Self::ReadFileFails => "read-file-fails.txt",
            Self::RunOutput => "run-output.txt",
            Self::RunExit => "run-exit.txt",
            Self::RunSleeps => "run-sleeps.txt",
            Self::Exit => "exit.txt",
            Self::StateStopped => "state-stopped.txt",
            Self::StateRunning => "state-running.txt",
            Self::StateSuspended => "state-suspended.txt",
            Self::SuspendExit => "suspend-exit.txt",
            Self::Who => "who.txt",
            Self::Autologin => "autologin.txt",
            Self::AutologinExit => "autologin-exit.txt",
            Self::KcpasswordExit => "kcpassword-exit.txt",
            Self::DockerVersion => "docker-version.txt",
            Self::ContainerState => "container-state.txt",
            Self::StartedState => "started-state.txt",
            Self::ImagePresent => "image-present.txt",
            Self::ContainerId => "container-id.txt",
            Self::PullExit => "pull-exit.txt",
            Self::PushExit => "push-exit.txt",
            Self::ImageRevision => "image-revision.txt",
            Self::ContainerSystem => "container-system.json",
            Self::ContainerSystemStarted => "container-system-started.json",
            Self::ContainerStartExit => "container-start-exit.txt",
            Self::ContainerLog => "container-log.txt",
            Self::ContainerBootLog => "container-boot-log.txt",
            Self::MirrorArchive => "mirror-archive.txt",
            Self::ContainerLinuxState => "container-linux-state.txt",
            Self::ContainerLinuxPort => "container-linux-port.txt",
            Self::ContainerLinuxRoot => "container-linux-root.txt",
            Self::ContainerLinuxFailedVerb => "container-linux-failed-verb.txt",
            Self::SilentVerb => "silent-verb.txt",
            Self::KilledVerb => "killed-verb.txt",
        }
    }
}

/// The call log beside the executable.
pub(crate) const CALLS: &str = "calls.txt";

/// The copy of the file the last upload of the fake `curl` sent, beside the executable.
pub const MIRROR_UPLOAD: &str = "mirror-upload.txt";

/// The copy of the header file of the last upload of the fake `curl`, beside the executable.
pub const MIRROR_HEADER: &str = "mirror-header.txt";

/// The permission column of `ls -ln` of the header file of the last upload, such as `-rw-------`.
pub const MIRROR_HEADER_MODE: &str = "mirror-header-mode.txt";

/// Separates the arguments of one recorded call (ASCII unit separator), so an argument may hold spaces and
/// newlines - a `prlctl exec` shell string often does - and still come back whole.
pub(crate) const ARG_END: char = '\u{1f}';
/// Ends one recorded call (ASCII record separator), followed by a newline so the log stays readable.
pub(crate) const CALL_END: &str = "\u{1e}\n";

/// The record is staged in a file of this pid and appended by one `cat`, which writes it in one `write(2)`: the
/// shell's `printf` flushes a long record in pieces, and two fakes spawned at once would interleave them. `printf`
/// runs its format once even without arguments, so an empty argv is spelled out. A call that cannot record itself
/// names the call log on stderr and exits 125 before any verb. The script finds its directory without a child process.
///
/// The relay runs under `/bin/bash`, because its `/dev/tcp` redirection opens the socket and no `nc` of the test
/// host is needed. It ends like the guest relay: at the end of the socket, and not at the end of stdin. So the
/// copy from the socket to stdout runs in the foreground, and the copy from stdin to the socket is a background
/// job that is killed when the socket ends. A background job reads `/dev/null` as its stdin, so stdin goes to it
/// on fd 4. The bash relay does not half-close the socket at the end of stdin, and an HTTP client does not need
/// that.
#[cfg(unix)]
const SCRIPT: &str = r#"#!/bin/sh
case $0 in */*) dir=${0%/*} ;; *) dir=. ;; esac
self=${0##*/}
relay='exec 3<>"/dev/tcp/127.0.0.1/$1" || exit 70
exec 4<&0
cat <&4 >&3 &
feeder=$!
cat <&3
kill "$feeder" 2>/dev/null
exit 0'
{ if [ $# -eq 0 ]; then printf '\036\n'; else printf '%s\037' "$@"; printf '\036\n'; fi > "$dir/.call-$$" &&
  cat "$dir/.call-$$" >> "$dir/calls.txt"; } || { echo "fake $self: cannot record the call in $dir/calls.txt" >&2; exit 125; }
[ -f "$dir/killed-verb.txt" ] && [ "$1" = "$(cat "$dir/killed-verb.txt")" ] && kill -KILL $$
[ -f "$dir/silent-verb.txt" ] && [ "$1" = "$(cat "$dir/silent-verb.txt")" ] && exit 0
# The container and docker verbs share these steps. Each reads $dir and $state when it runs.
started() {
  if [ -f "$dir/started-state.txt" ]; then cp "$dir/started-state.txt" "$state"; else echo running/0 > "$state"; fi
}
has_image() {
  [ -f "$dir/image-present.txt" ] ||
    { [ -f "$dir/images.txt" ] && awk -v r="$1" '$0 == r { found = 1 } END { exit !found }' "$dir/images.txt"; }
}
drop_image() {
  [ -f "$dir/images.txt" ] || return 0
  awk -v r="$1" '$0 != r' "$dir/images.txt" > "$dir/images.txt.new" && mv "$dir/images.txt.new" "$dir/images.txt"
}
if [ "$self" = curl ]; then
  out=; upload=; header=; prev=
  for word in "$@"; do
    case "$prev" in
      --output) out=$word ;;
      --upload-file) upload=$word ;;
      --header) header=${word#@} ;;
    esac
    prev=$word
  done
  if [ -n "$upload" ]; then
    cp "$upload" "$dir/mirror-upload.txt"
    if [ -n "$header" ]; then
      cat "$header" > "$dir/mirror-header.txt"
      ls -ln "$header" | cut -c1-10 > "$dir/mirror-header-mode.txt"
    fi
    ucode=$(cat "$dir/push-exit.txt" 2>/dev/null || echo 0)
    [ "$ucode" = 0 ] || { echo "curl: (22) The requested URL returned error: 403" >&2; exit "$ucode"; }
    exit 0
  fi
  if [ -f "$dir/mirror-archive.txt" ]; then cp "$dir/mirror-archive.txt" "$out"; exit 0; fi
  echo "curl: (22) The requested URL returned error: 404" >&2
  exit 22
fi
if [ "$self" = container.cmd ] || [ "$self" = container.sh ]; then
  [ -f "$dir/container-linux-failed-verb.txt" ] && [ "$1" = "$(cat "$dir/container-linux-failed-verb.txt")" ] && {
    echo "fake container.cmd: $1 failed" >&2; exit 1; }
  ustate="$dir/container-linux-state.txt"
  # The real script writes under <checkout>/out/testing-ui, four levels above its own directory.
  root="$(cd "$dir/../../../.." && pwd)/out/testing-ui"
  case "$1" in
    start)
      [ -f "$dir/container-linux-port.txt" ] || { echo "fake container.cmd: no control port is seeded in container-linux-port.txt" >&2; exit 2; }
      mkdir -p "$root"
      printf 'APP_EXEC=%s\n' "${APP_EXEC-}" \
        > "$dir/container-linux-start-env.txt"
      printf '%s' "$(cat "$dir/container-linux-port.txt")" > "$root/container.ctl_port"
      echo 0123456789abcdef0123456789abcdef > "$root/container.ctl_bearer"
      printf '{"novnc":"http://127.0.0.1:1/vnc.html","vncPassword":"pw"}\n' > "$root/container.json"
      echo running > "$ustate"
      echo "container 'ui-fake' started"
      exit 0 ;;
    stop)
      echo exited > "$ustate"
      rm -f "$root/container.ctl_port" "$root/container.ctl_bearer" \
        "$root/container.json"
      exit 0 ;;
    list)
      if [ "$(cat "$ustate" 2>/dev/null)" = running ]; then
        echo "ui-fake  running  $(cat "$dir/container-linux-root.txt" 2>/dev/null)"
        echo "  run none"
        echo "  noVNC http://127.0.0.1:1/vnc.html  password pw"
      else
        echo "no containers of this tool are running"
      fi
      exit 0 ;;
  esac
fi
if [ "$self" = prlctl ]; then
  case "$1" in
    stop)
      [ -f "$dir/state-stopped.txt" ] && cp "$dir/state-stopped.txt" "$dir/list-json.txt"
      exit 0 ;;
    start)
      [ -f "$dir/state-running.txt" ] && cp "$dir/state-running.txt" "$dir/list-json.txt"
      exit 0 ;;
    suspend)
      [ -f "$dir/state-suspended.txt" ] && cp "$dir/state-suspended.txt" "$dir/list-json.txt"
      exit "$(cat "$dir/suspend-exit.txt" 2>/dev/null || echo 0)" ;;
    exec)
      case "$3" in
        *relay*)
          words=$(printf '%s' "$3" | tr -d "'")
          case "$words" in
            *" relay "*)
              port=${words##*" relay "}
              case "$port" in ''|*[!0-9]*) ;; *) exec /bin/bash -c "$relay" relay "$port" ;; esac ;;
          esac ;;
        *"/usr/bin/who"*) cat "$dir/who.txt" 2>/dev/null; exit 0 ;;
        *autoLoginUser*)
          cat "$dir/autologin.txt" 2>/dev/null
          exit "$(cat "$dir/autologin-exit.txt" 2>/dev/null || echo 0)" ;;
        *kcpassword*) exit "$(cat "$dir/kcpassword-exit.txt" 2>/dev/null || echo 0)" ;;
      esac ;;
  esac
else
  if [ "$self" = container ]; then
    code=$(cat "$dir/exit.txt" 2>/dev/null || echo 0)
    state="$dir/container-state.txt"
    sys="$dir/container-system.json"
    for ref in "$@"; do :; done
    case "$1" in
      system)
        case "$2" in
          status)
            if [ -f "$sys" ]; then cat "$sys"; exit 0; fi
            echo '{"status":"unregistered"}'
            exit 1 ;;
          start)
            scode=$(cat "$dir/container-start-exit.txt" 2>/dev/null || echo 0)
            echo "fake container: starting the server"
            [ "$scode" = 0 ] || { echo "fake container: the start failed" >&2; exit "$scode"; }
            if [ -f "$dir/container-system-started.json" ]; then
              cp "$dir/container-system-started.json" "$sys"
            else
              printf '{"status":"running","paths":{"appRoot":"/fake/app-root/","installRoot":"%s/"},"client":{"version":"1.5.0"},"server":{"version":"1.5.0"},"host":{"architecture":"arm64"}}\n' "${dir%/*}" > "$sys"
            fi
            exit 0 ;;
          stop) echo "fake container: the controller never stops the server" >&2; exit 3 ;;
        esac
        exit "$code" ;;
      inspect)
        [ -f "$state" ] || { echo "Error: container not found: $2" >&2; exit 1; }
        id=$(cat "$dir/container-id.txt" 2>/dev/null)
        current=$(cat "$state")
        case "$current" in running/*) word=running ;; created/*|exited/*) word=stopped ;; *) word=${current%%/*} ;; esac
        printf '[{"configuration":{"id":"%s"},"id":"%s","status":{"state":"%s"}}]\n' "$id" "$id" "$word"
        exit 0 ;;
      create)
        name=; prev=
        for word in "$@"; do [ "$prev" = --name ] && name=$word; prev=$word; done
        if [ "$code" = 0 ]; then
          echo "$name" > "$dir/container-id.txt"
          echo created/0 > "$state"
          echo "$name"
        fi
        exit "$code" ;;
      start)
        if [ "$code" = 0 ]; then
          started
          echo "$2"
        fi
        exit "$code" ;;
      stop) [ "$code" = 0 ] && [ -f "$state" ] && echo exited/0 > "$state"; exit "$code" ;;
      delete)
        case "$2" in
          -f|--force) ;;
          *) if [ -f "$state" ]; then
               case "$(cat "$state")" in
                 running/*) echo "Error: container $2 is running and can not be deleted" >&2; exit 1 ;;
               esac
             fi ;;
        esac
        [ "$code" = 0 ] && rm -f "$state" "$dir/container-id.txt"
        exit "$code" ;;
      build)
        tagged=; prev=
        for word in "$@"; do [ "$prev" = -t ] && tagged=$word; prev=$word; done
        [ "$code" = 0 ] && printf '%s\n' "$tagged" >> "$dir/images.txt"
        exit "$code" ;;
      image)
        case "$2" in
          inspect)
            has_image "$ref" || { echo "Error: image not found: $ref" >&2; exit 1; }
            revision=$(cat "$dir/image-revision.txt" 2>/dev/null)
            printf '[{"variants":[{"config":{"config":{"Labels":{"org.opencontainers.image.revision":"%s"}}}}]}]\n' "$revision"
            exit 0 ;;
          load)
            loaded=; prev=
            for word in "$@"; do [ "$prev" = -i ] && loaded=$word; prev=$word; done
            [ "$code" = 0 ] || { echo "Error: cannot load $loaded" >&2; exit "$code"; }
            cat "$loaded" >> "$dir/images.txt"
            exit 0 ;;
          save)
            saved=; prev=
            for word in "$@"; do [ "$prev" = -o ] && saved=$word; prev=$word; done
            [ "$code" = 0 ] && printf '%s\n' "$ref" > "$saved"
            exit "$code" ;;
          pull|push) echo "fake container: the controller uses the file mirror, not image $2" >&2; exit 3 ;;
          tag) [ "$code" = 0 ] && printf '%s\n' "$4" >> "$dir/images.txt"; exit "$code" ;;
          delete)
            [ "$code" = 0 ] && drop_image "$ref"
            exit "$code" ;;
        esac
        exit "$code" ;;
      volume)
        [ "$2" = inspect ] && { echo "Error: volume not found: $3" >&2; exit 1; }
        exit "$code" ;;
      logs)
        case " $* " in
          *" --boot "*) cat "$dir/container-boot-log.txt" 2>/dev/null ;;
          *) cat "$dir/container-log.txt" 2>/dev/null ;;
        esac
        exit 0 ;;
    esac
  fi
  if [ "$self" = docker ]; then
    code=$(cat "$dir/exit.txt" 2>/dev/null || echo 0)
    state="$dir/container-state.txt"
    case "$1" in
      version)
        if [ -f "$dir/docker-version.txt" ]; then cat "$dir/docker-version.txt"; else echo linux/arm64; fi
        exit "$(cat "$dir/version-exit.txt" 2>/dev/null || echo 0)" ;;
      inspect)
        [ "$2 $3" = "--type container" ] || { echo "fake docker: inspect without --type container" >&2; exit 2; }
        [ -f "$state" ] || { echo "Error: No such container: $6" >&2; exit 1; }
        case "$5" in
          *.Id*) cat "$dir/container-id.txt" 2>/dev/null ;;
          *.State.Running*) case "$(cat "$state")" in running/*) echo true ;; *) echo false ;; esac ;;
          *) cat "$state" || echo "fake docker: cannot print the state, cat exited with $?" >&2 ;;
        esac
        exit 0 ;;
      create)
        if [ "$code" = 0 ]; then
          created=$(( $(cat "$dir/create-count.txt" 2>/dev/null || echo 0) + 1 ))
          echo "$created" > "$dir/create-count.txt"
          echo "fake-container-$created" > "$dir/container-id.txt"
          echo created/0 > "$state"
          echo "fake-container-$created"
        fi
        exit "$code" ;;
      start)
        [ "$code" = 0 ] && started
        exit "$code" ;;
      stop) [ "$code" = 0 ] && [ -f "$state" ] && echo exited/0 > "$state"; exit "$code" ;;
      rm)
        if [ "$2" != --force ] && [ -f "$state" ]; then
          case "$(cat "$state")" in
            running/*) echo "Error: cannot remove container: container is running" >&2; exit 1 ;;
          esac
        fi
        [ "$code" = 0 ] && rm -f "$state" "$dir/container-id.txt"
        exit "$code" ;;
      build) [ "$code" = 0 ] && printf '%s\n' "$3" >> "$dir/images.txt"; exit "$code" ;;
      pull)
        pcode=$(cat "$dir/pull-exit.txt" 2>/dev/null || echo 1)
        [ "$pcode" = 0 ] && printf '%s\n' "$2" >> "$dir/images.txt"
        exit "$pcode" ;;
      buildx) exit "$(cat "$dir/push-exit.txt" 2>/dev/null || echo 0)" ;;
      tag) [ "$code" = 0 ] && printf '%s\n' "$3" >> "$dir/images.txt"; exit "$code" ;;
      image)
        for ref in "$@"; do :; done
        case "$2" in
          inspect)
            has_image "$ref" || exit 1
            case "$*" in *Labels*) cat "$dir/image-revision.txt" 2>/dev/null ;; esac
            exit 0 ;;
          rm)
            [ "$code" = 0 ] && drop_image "$ref"
            exit "$code" ;;
        esac ;;
    esac
  fi
  case "$1" in
    ip) [ "$self" = tart ] && { echo 127.0.0.1; exit 0; } ;;
    run)
      [ "$self" = tart ] || exit "$(cat "$dir/exit.txt" 2>/dev/null || echo 0)"
      cat "$dir/run-output.txt" 2>/dev/null
      if [ -f "$dir/run-sleeps.txt" ]; then /bin/sleep 30; exit 0; fi
      exit "$(cat "$dir/run-exit.txt" 2>/dev/null || echo 1)" ;;
    exec)
      if [ $# -eq 6 ] && [ "$2" = -i ] && [ "$5" = relay ]; then
        case "$6" in ''|*[!0-9]*) ;; *) exec /bin/bash -c "$relay" relay "$6" ;; esac
      fi
      for word in "$@"; do
        case "$word" in */*|'') continue ;; esac
        if [ -f "$dir/exec-verb-$word.txt" ]; then cat "$dir/exec-verb-$word.txt"; exit 0; fi
      done
      case " $* " in
        *" read-file "*)
          if [ -f "$dir/read-file-fails.txt" ]; then exit 1; fi
          if [ -f "$dir/read-file.bin" ]; then
            cat "$dir/read-file.bin"
            cat "$dir/read-file-receipt.txt" >&2 2>/dev/null
            exit 0
          fi ;;
      esac ;;
  esac
fi
case "$1" in
  --version)
    cat "$dir/version.txt" 2>/dev/null
    exit "$(cat "$dir/version-exit.txt" 2>/dev/null || echo 0)" ;;
  list)
    case "$*" in
      *--quiet*) cat "$dir/list-quiet.txt" 2>/dev/null ;;
      *) cat "$dir/list-json.txt" 2>/dev/null ;;
    esac
    exit "$(cat "$dir/list-exit.txt" 2>/dev/null || echo 0)" ;;
  status)
    cat "$dir/status.txt" 2>/dev/null
    exit "$(cat "$dir/status-exit.txt" 2>/dev/null || echo 0)" ;;
  exec)
    cat "$dir/exec-stdout.txt" 2>/dev/null
    cat "$dir/exec-stderr.txt" >&2 2>/dev/null
    exit "$(cat "$dir/exec-exit.txt" 2>/dev/null || echo 0)" ;;
esac
exit "$(cat "$dir/exit.txt" 2>/dev/null || echo 0)"
"#;

/// One installed fake hypervisor: the executable a config points a binary variable at, and the directory its
/// answers and its call log live in. Cloning shares the directory; the last clone (or fake installed beside it)
/// to go removes it.
#[derive(Debug, Clone)]
pub struct Fake {
    directory: Arc<OwnedDir>,
    executable: PathBuf,
}

impl Fake {
    /// Writes the fake `tart` into a fresh temporary directory and answers `--version` with `version`.
    ///
    /// Nothing else is seeded. An unseeded `list` answers no output at all, which is not the same refusal as the
    /// empty pool `[]`: a suite that wants a pool says so.
    pub fn install(version: &str) -> Self {
        Self::install_binary(Binary::Tart, version)
    }

    /// [`Fake::install`] for a named backend.
    pub fn install_binary(binary: Binary, version: &str) -> Self {
        let directory = Arc::new(OwnedDir::create());
        let fake = Self::in_directory(directory, binary);
        fake.answer(Answer::Version, version);
        fake
    }

    /// A fake `tart` in `directory`, which this fake creates and owns. A container-linux fixture passes the `scripts`
    /// directory of a testing-ui skill under its checkout, because the fake `container.cmd` beside it finds the
    /// checkout four levels up, as the real script does.
    pub fn install_in(directory: PathBuf, version: &str) -> Self {
        let fake = Self::in_directory(Arc::new(OwnedDir::at(directory)), Binary::Tart);
        fake.answer(Answer::Version, version);
        fake
    }

    /// Writes a second backend's fake into this fake's directory, and answers the fake for it.
    ///
    /// One directory, so the two share every answer file and one call log. That is what a suite which drives both
    /// backends over one set of answers needs, and the two names keep the two `exec` grammars apart while it does.
    #[must_use]
    pub fn install_beside(&self, binary: Binary) -> Self {
        Self::in_directory(Arc::clone(&self.directory), binary)
    }

    fn in_directory(directory: Arc<OwnedDir>, binary: Binary) -> Self {
        #[cfg(unix)]
        let executable = {
            let executable = directory.path.join(binary.file_name());
            let mut names = vec![executable.clone()];
            if binary == Binary::ContainerLinux {
                names.push(executable.with_extension("sh"));
            }
            for name in &names {
                let placed = crate::shared::script(SCRIPT).and_then(|script| crate::shared::place(&script, name));
                if let Err(error) = placed {
                    panic!("install the fake {}: {error}", name.display());
                }
            }
            executable
        };
        #[cfg(windows)]
        let executable = {
            assert_eq!(
                binary,
                Binary::Docker,
                "a Windows host has the Docker backend only, and no fake {}",
                binary.file_name()
            );
            crate::fakebin::place(&directory.path, binary.file_name())
        };
        Self { directory, executable }
    }

    /// Holds the executable, every answer file and the call log. A suite that seeds an answer from outside its
    /// fixture writes it here.
    pub fn directory(&self) -> &Path {
        &self.directory.path
    }

    /// What the backend's binary variable (`TART_BIN`, `AIR_VM_PARALLELS_BIN`) has to be set to.
    pub fn executable(&self) -> &Path {
        &self.executable
    }

    /// Seeds one answer file beside the executable, replacing what it held.
    pub fn answer(&self, answer: Answer, content: impl AsRef<[u8]>) {
        let path = self.directory().join(answer.file_name());
        if let Err(error) = fs::write(&path, content) {
            panic!("seed {}: {error}", path.display());
        }
    }

    /// Seeds what a `tart exec` answers, exit 0, when `verb` is its first argument without a `/` that has an answer:
    /// the guest agent's subcommand, since the agent's own path has one.
    pub fn answer_exec_verb(&self, verb: &str, stdout: impl AsRef<[u8]>) {
        assert!(!verb.is_empty() && !verb.contains('/'), "an exec verb is one word: {verb:?}");
        let path = self.directory().join(exec_verb_file(verb));
        if let Err(error) = fs::write(&path, stdout) {
            panic!("seed {}: {error}", path.display());
        }
    }

    /// Removes one answer file, so the verb answers its default again.
    pub fn forget(&self, answer: Answer) {
        remove_if_present(&self.directory().join(answer.file_name()));
    }

    /// Every argv the fakes of this directory were spawned with, in order, without the program name.
    pub fn argvs(&self) -> Vec<Vec<String>> {
        let Some(log) = read_if_present(&self.directory().join(CALLS)) else {
            return Vec::new();
        };
        log.split_terminator(CALL_END)
            .map(|record| record.split_terminator(ARG_END).map(str::to_owned).collect())
            .collect()
    }

    /// The environment the last `container.cmd start` ran with, as `NAME=value` pairs in the order the real script
    /// reads them. Empty before the first start.
    pub fn container_linux_start_environment(&self) -> Vec<(String, String)> {
        let Some(text) = read_if_present(&self.directory().join(CONTAINER_LINUX_START_ENVIRONMENT)) else {
            return Vec::new();
        };
        text.lines()
            .filter_map(|line| line.split_once('='))
            .map(|(name, value)| (name.to_owned(), value.to_owned()))
            .collect()
    }

    /// Every argv the fakes were spawned with, in order, each joined by spaces.
    pub fn calls(&self) -> Vec<String> {
        self.argvs().into_iter().map(|argv| argv.join(" ")).collect()
    }

    /// Whether any recorded call, joined by spaces, contains `fragment`.
    pub fn saw_call_containing(&self, fragment: &str) -> bool {
        self.calls().iter().any(|call| call.contains(fragment))
    }

    /// Forgets every recorded call, so the next case of a test that shares this fake reads only its own calls.
    pub fn forget_calls(&self) {
        remove_if_present(&self.directory().join(CALLS));
    }

    /// Runs the fake once with `--version`, waits for it, and forgets the call. A suite calls it before it runs
    /// anything else through the fake.
    ///
    /// macOS scans a new executable file at its first exec, and the first execs of new files wait for each other's
    /// scans. The fakes of a process share one file, so on a loaded host the first exec of a fake in the process can
    /// take many seconds, and the next exec of the same file does not wait. A suite whose first command through the
    /// fake has a short budget calls this first, so that the scan is not inside the budget.
    #[cfg(unix)]
    pub fn exec_once(&self) {
        let status = std::process::Command::new(&self.executable)
            .arg("--version")
            .stdout(std::process::Stdio::null())
            .status();
        if let Err(error) = status {
            panic!("spawn {}: {error}", self.executable.display());
        }
        self.forget_calls();
    }
}

/// The file that answers a `tart exec` or `docker exec` whose first word without a `/` is `verb`.
pub(crate) fn exec_verb_file(verb: &str) -> String {
    format!("exec-verb-{verb}.txt")
}

/// The text of a file the fake writes, or `None` before the fake wrote it.
fn read_if_present(path: &Path) -> Option<String> {
    match fs::read_to_string(path) {
        Ok(text) => Some(text),
        Err(error) if error.kind() == io::ErrorKind::NotFound => None,
        Err(error) => panic!("read {}: {error}", path.display()),
    }
}

/// Removes a file of the fake directory. A file that is not there is not an error.
fn remove_if_present(path: &Path) {
    match fs::remove_file(path) {
        Ok(()) => {}
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => panic!("remove {}: {error}", path.display()),
    }
}

/// A temporary directory removed when the last fake sharing it goes.
#[derive(Debug)]
struct OwnedDir {
    path: PathBuf,
}

impl OwnedDir {
    fn create() -> Self {
        Self {
            path: crate::shared::new_directory(),
        }
    }

    fn at(path: PathBuf) -> Self {
        fs::create_dir_all(&path).unwrap_or_else(|error| panic!("create {}: {error}", path.display()));
        Self { path }
    }
}

impl Drop for OwnedDir {
    fn drop(&mut self) {
        // Best effort: a `tart run` child may still be sleeping, and it holds nothing open in here.
        let _ = fs::remove_dir_all(&self.path);
    }
}
