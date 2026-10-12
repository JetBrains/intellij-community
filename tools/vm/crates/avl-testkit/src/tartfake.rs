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
//! The `limactl` verbs, which only the fake `limactl` answers. One fake holds one instance, the Docker engine, under
//! the `LIMA_HOME` its caller sets; without one it exits 2:
//!
//! - `list --format {{.Status}} <name>` prints [`Answer::LimaState`]. No such file is no instance: exit 1 with the
//!   "No instance matching" warning, as `limactl` answers.
//! - `start` copies a template argument (`*.yaml`) to [`LIMA_TEMPLATE_COPY`], then exits from
//!   [`Answer::LimaStartExit`]. A start that succeeds writes `Running` over [`Answer::LimaState`] and creates the
//!   forwarded socket `$LIMA_HOME/<name>/sock/docker.sock` as a plain file.
//! - `stop` writes `Stopped` and removes the socket. `delete` removes the state and `$LIMA_HOME/<name>`.
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
    /// The fake `limactl`, which a suite's Bazel resolves as the pinned one.
    Limactl,
}

impl Binary {
    /// The executable's file name, which is what the script dispatches on.
    pub const fn file_name(self) -> &'static str {
        match self {
            Self::Tart => "tart",
            Self::Parallels => "prlctl",
            Self::Docker => "docker",
            Self::Limactl => "limactl",
        }
    }
}

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
    /// Exit code of `docker buildx build --push`, the publish, default 0.
    PushExit,
    /// What `docker image inspect --format {{index .Config.Labels …}}` prints: the revision label of the image.
    /// Unseeded, it prints nothing, as an image without the label does.
    ImageRevision,
    /// The instance of the fake `limactl`, as `limactl list --format {{.Status}}` prints it. Absent is no instance.
    LimaState,
    /// Exit code of `limactl start`, default 0. A nonzero exit starts nothing.
    LimaStartExit,
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
            Self::LimaState => "lima-state.txt",
            Self::LimaStartExit => "lima-start-exit.txt",
            Self::SilentVerb => "silent-verb.txt",
            Self::KilledVerb => "killed-verb.txt",
        }
    }
}

/// The call log beside the executable.
pub(crate) const CALLS: &str = "calls.txt";

/// The copy of the template the last `limactl start` of a template was given, beside the executable.
pub const LIMA_TEMPLATE_COPY: &str = "lima-template.yaml";

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
if [ "$self" = limactl ]; then
  [ -n "$LIMA_HOME" ] || { echo "fake limactl: LIMA_HOME is not set" >&2; exit 2; }
  lstate="$dir/lima-state.txt"
  name=; prev=
  for word in "$@"; do
    [ "$prev" = --name ] && name=$word
    prev=$word
  done
  [ -n "$name" ] || name=$prev
  case "$1" in
    list)
      if [ -f "$lstate" ]; then cat "$lstate"; exit 0; fi
      echo "level=warning msg=\"No instance matching $name found.\"" >&2
      exit 1 ;;
    start)
      case "$prev" in *.yaml) cp "$prev" "$dir/lima-template.yaml" ;; esac
      echo "fake limactl: starting $name"
      lcode=$(cat "$dir/lima-start-exit.txt" 2>/dev/null || echo 0)
      [ "$lcode" = 0 ] || { echo "fake limactl: the start failed" >&2; exit "$lcode"; }
      mkdir -p "$LIMA_HOME/$name/sock" && : > "$LIMA_HOME/$name/sock/docker.sock"
      echo Running > "$lstate"
      exit 0 ;;
    stop) rm -f "$LIMA_HOME/$name/sock/docker.sock"; echo Stopped > "$lstate"; exit 0 ;;
    delete) rm -rf "${LIMA_HOME:?}/$name"; rm -f "$lstate"; exit 0 ;;
  esac
  exit 0
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
        if [ "$code" = 0 ]; then
          if [ -f "$dir/started-state.txt" ]; then
            cp "$dir/started-state.txt" "$state"
          else
            echo running/0 > "$state"
          fi
        fi
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
            if [ -f "$dir/image-present.txt" ]; then :
            elif [ -f "$dir/images.txt" ] && awk -v r="$ref" '$0 == r { found = 1 } END { exit !found }' "$dir/images.txt"; then :
            else exit 1; fi
            case "$*" in *Labels*) cat "$dir/image-revision.txt" 2>/dev/null ;; esac
            exit 0 ;;
          rm)
            if [ "$code" = 0 ] && [ -f "$dir/images.txt" ]; then
              awk -v r="$ref" '$0 != r' "$dir/images.txt" > "$dir/images.txt.new" && mv "$dir/images.txt.new" "$dir/images.txt"
            fi
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
            let placed = crate::shared::script(SCRIPT).and_then(|script| crate::shared::place(&script, &executable));
            if let Err(error) = placed {
                panic!("install the fake {}: {error}", executable.display());
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
        let path = self.directory().join(answer.file_name());
        match fs::remove_file(&path) {
            Ok(()) => {}
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => panic!("remove {}: {error}", path.display()),
        }
    }

    /// Every argv the fakes of this directory were spawned with, in order, without the program name.
    pub fn argvs(&self) -> Vec<Vec<String>> {
        let path = self.directory().join(CALLS);
        let log = match fs::read_to_string(&path) {
            Ok(log) => log,
            Err(error) if error.kind() == io::ErrorKind::NotFound => return Vec::new(),
            Err(error) => panic!("read {}: {error}", path.display()),
        };
        log.split_terminator(CALL_END)
            .map(|record| record.split_terminator(ARG_END).map(str::to_owned).collect())
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
        let path = self.directory().join(CALLS);
        match fs::remove_file(&path) {
            Ok(()) => {}
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => panic!("remove {}: {error}", path.display()),
        }
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
}

impl Drop for OwnedDir {
    fn drop(&mut self) {
        // Best effort: a `tart run` child may still be sleeping, and it holds nothing open in here.
        let _ = fs::remove_dir_all(&self.path);
    }
}
