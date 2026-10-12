//! What a worker VM has to be on the inside before a host-built Bazel output can run in it, and every command this
//! controller sends through the exec channel to get it there.
//!
//! What this module owns is everything that happens *inside* a running VM: the mount the controller takes over
//! from the guest's own boot automount and the remount sweep that invalidates it, the parity layout that gives the
//! guest the host's absolute paths, TCC admission, SSH host-key uniqueness, the guest agent's install, storage
//! readiness, and the run supervisor's invocation.
//!
//! What it does not own is either half of getting there. How a share is *declared* is [`crate::share`]'s -
//! [`SharedFolder`](crate::share::SharedFolder) and [`shares`](crate::share::shares) compose it and each backend
//! renders it its own way - and how a command *reaches* the guest is [`Channel`]'s. Both are consumed here
//! and neither is restated, which is why nothing here names a backend except where a macOS guest and a Linux one
//! genuinely differ: TCC, an Aqua session, an APFS container, a sealed golden's SSH identity. Those branch on
//! [`Config::guest_os`], never on the hypervisor.
//!
//! # A session, not a triple
//!
//! Almost every step takes the operation context, the settings, the channel into one worker and the reporter its
//! notes go to. [`Guest`] is those four borrowed together, so a step is `guest.remount_shares()` rather than a
//! function of four arguments repeated at forty call sites.
//!
//! # Host paths are a precondition, not a side effect
//!
//! The resolved host paths live behind [`Config::host_repo`], which refuses to answer before
//! [`ensure_host_paths`] has run and names that function in its refusal - so the readers here propagate that
//! refusal instead of resolving on demand. One resolver, one `git rev-parse`, and a caller that skipped it learns
//! what it skipped rather than declaring a share at the empty path.

use std::path::{Path, PathBuf};
use std::sync::LazyLock;
use std::time::Duration;

use avl_base::format::{clip, words};
use avl_base::phase::{ArgvBounds, redact_guest_argv};
use avl_base::{Config, Exit, OrRefuse, Refusal, Reporter, Scope};
use regex::Regex;
use serde_json::json;

use crate::ctx::Ctx;
use crate::fs::real_path;
use crate::paths::GuestPaths;
use crate::proc::{Captured, Channel, FAILURE_OUTPUT_TAIL_BYTES, ProbeExit, ProbeOutput, Runner, SpawnOptions, probe_unanswered};
use avl_base::RefusalExt;

mod agent;
mod external;
mod mount;
mod parity;
mod runfiles;
mod sshkeys;
mod storage;
mod supervisor;
mod tcc;

#[cfg(test)]
mod testing;
#[cfg(test)]
mod tests;

pub use agent::{
    AgentReceipt, BazelHost, agent_digest, agent_label, agent_receipt_path, host_file_signature, installed_agent_receipt,
    write_agent_receipt,
};
pub use external::{external_file, external_files};
pub use mount::{remount_script, share_mount_path};
pub use parity::{
    InitReceipt, PARITY_MARKER, ParityError, ShareMount, init_receipt_path, parity_marker_content, parity_script, read_init_receipt,
    validate_parity_entry_name, write_init_receipt,
};
pub use sshkeys::{PeerChannels, parse_ssh_host_key_fingerprint};
pub use supervisor::{AgentAccount, OLDER_AGENT_HINT, ParkedDaemonProbe, RunSlot, SupervisorOptions};
pub use tcc::{SENSITIVE_TCC_SERVICES, TCC_DECISION_QUERY, worker_tcc_admission_error};

/// One worker, reached through one channel, for one operation: the context, the settings, the channel and the
/// reporter every guest step needs. Borrowed and `Copy`, so a caller builds one where it has the four and hands it
/// down.
#[derive(Clone, Copy)]
pub struct Guest<'a> {
    pub ctx: &'a Ctx,
    pub settings: &'a Config,
    pub channel: &'a dyn Channel,
    pub reporter: &'a Reporter,
}

// --- host paths ----------------------------------------------------------------------------------------------

/// Resolves the two host directories the guest must see: the repository checkout and Bazel's output user root.
/// The guest sees each one at its [`GuestPaths`] root, which is the same absolute path on a Unix host.
///
/// Everything a host-built runtime descriptor names lives under one of them, which is what makes a copy-free guest
/// run possible at all. Both are resolved together and published together through [`Config::set_host_paths`],
/// because a config carrying one of them is a config no reader can trust.
///
/// Idempotent, and cheap on the second call: a config that already has its paths is answered without another
/// `git rev-parse` or another realpath.
pub async fn ensure_host_paths(ctx: &Ctx, runner: &Runner, settings: &Config) -> Result<(), Refusal> {
    if settings.host_repo().is_ok() {
        return Ok(());
    }
    let repo = resolve_host_repo(ctx, runner, settings).await?;
    let configured = settings.configured_bazel_user_root();
    // Created rather than required: a host that has never run a Bazel build still has to be able to declare the
    // share, and `tart run` refuses a `--dir` whose path does not exist.
    let mut builder = std::fs::DirBuilder::new();
    builder.recursive(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::DirBuilderExt;
        builder.mode(0o755);
    }
    builder.create(configured).or_refuse("host_paths_unresolved", Exit::FAILURE, || {
        format!("cannot create the Bazel output user root {}", configured.display())
    })?;
    // The realpath, not the configured path. The share is declared with whatever this answers and the guest's
    // parity symlink points at the mount of it; a path that still contains a symlink component would make the
    // guest's absolute paths differ from the host's by that component, which is the one thing parity buys.
    let resolved = real_path(configured).or_refuse("host_paths_unresolved", Exit::FAILURE, || {
        format!("cannot resolve the Bazel output user root {}", configured.display())
    })?;
    settings.set_host_paths(repo, resolved)?;
    // Checked here, once, so that a host root no guest root can hold is refused before any share is declared.
    GuestPaths::of(settings).map(drop)
}

/// The checkout the guest reads through the repository share.
///
/// The `--is-inside-work-tree` probe is not redundant with `--show-toplevel`: the override path skips the first
/// command entirely, and an override naming a directory that is not a checkout would otherwise be shared into the
/// guest and fail as a missing `.git` probe two steps later, blaming the mount.
async fn resolve_host_repo(ctx: &Ctx, runner: &Runner, settings: &Config) -> Result<PathBuf, Refusal> {
    let not_a_checkout = |message: String| Refusal::new("host_repo_required", Exit::USAGE, message);
    let candidate = if let Some(configured) = &settings.host_repo_override {
        std::path::absolute(configured).map_err(|error| {
            not_a_checkout(format!(
                "cannot resolve the configured host repository {}: {error}",
                configured.display()
            ))
        })?
    } else {
        // `checked` rather than `checked_quietly`: this is a host command, and what `git` says about a
        // directory that is not a checkout is the whole diagnosis.
        let argv = words([&settings.git, "rev-parse", "--show-toplevel"]);
        let captured = runner.checked(ctx, &argv, &SpawnOptions::within(HOST_GIT_TIMEOUT)).await?;
        match captured.stdout.trim() {
            "" => return Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted)),
            toplevel => PathBuf::from(toplevel),
        }
    };
    let argv = words([
        &settings.git,
        "-C",
        &candidate.to_string_lossy(),
        "rev-parse",
        "--is-inside-work-tree",
    ]);
    // `git` answers `true` or `false`, and exits 128 outside a repository. Any other answer is no answer.
    let inside = runner.capture(ctx, &argv, &SpawnOptions::within(HOST_GIT_TIMEOUT)).await?;
    match (inside.probe_exit(), inside.stdout.trim()) {
        (ProbeExit::Answered, "true") => {}
        (ProbeExit::Answered, "false") | (ProbeExit::Negative, _) => {
            return Err(not_a_checkout(format!("{} is not a Git working tree", candidate.display())));
        }
        _ => return Err(probe_unanswered(&argv, &inside, ProbeOutput::Quoted)),
    }
    real_path(&candidate).map_err(|error| not_a_checkout(format!("cannot resolve the host repository {}: {error}", candidate.display())))
}

/// The timeout of a `git rev-parse` of the host checkout. It reads `.git` and answers in milliseconds, so half a
/// minute covers a cold disk.
const HOST_GIT_TIMEOUT: Duration = Duration::from_secs(30);

// --- reaching the guest --------------------------------------------------------------------------------------

/// The timeout of one short command in the guest: a `mkdir`, a `chown` of one directory, a `mv`, a `cat`, a `who`, a
/// `test`. One exec round trip costs about 0.65 s on Tart, so a guest that has not answered in a minute has stopped
/// answering.
pub const GUEST_COMMAND_TIMEOUT: Duration = Duration::from_secs(60);

/// The timeout of [`Guest::write_file`]. The largest file it writes is the guest agent, a few MiB through the stdin
/// of the exec channel.
const GUEST_WRITE_TIMEOUT: Duration = Duration::from_mins(5);

/// How much of a failed command's argument list travels in the refusal. Bounded for the reason
/// [`FAILURE_OUTPUT_TAIL_BYTES`] is: the refusal ends up inside a JSON envelope an agent reads, and `gc /vm/runtime
/// <60 digests>` is not a message. It bounds the two quoted stderr lines as well, which are the only other thing
/// this module puts in a refusal that it did not compose itself.
pub(crate) const GUEST_ARGV_MESSAGE_BYTES: usize = 240;

/// How long one argument may be before it is redacted whole. Generous next to the phase skeleton's 48, because
/// here a path *is* the diagnosis - which file `tee` could not write, which root the supervisor was asked about -
/// and a 64-character generation digest has to survive too. What it stops is an element long enough to be a
/// payload rather than an argument.
const GUEST_ARGV_ELEMENT_BYTES: usize = 96;

/// [`Guest::raw`]'s share of [`redact_guest_argv`]'s policy: every element, each of them bounded, and the whole
/// rendering bounded again.
const GUEST_ARGV_MESSAGE_BOUNDS: ArgvBounds = ArgvBounds {
    elements: 0,
    element_bytes: GUEST_ARGV_ELEMENT_BYTES,
    bytes: GUEST_ARGV_MESSAGE_BYTES,
};

/// The withholding contract's exceptions, both of them, in one place.
///
/// These two lists are every prefix this module will quote a line for, and there is no other way to add one:
/// [`quoted_first_line`] takes one of these lists and not a literal, so a widening is an edit here, in front of
/// both arguments, rather than at a call site where only one of them is in view.
///
/// Two lists and not one, because the two rest on different guarantees and only one of them is about the prefix.
/// The guest agent's `usage:` line is written by a program this repository builds, whose usage text is a static
/// string with no argv in it. `sudo:` is read off the whole command's stream, where [`Guest::raw`] runs argv this
/// controller did not necessarily compose, and what bounds it there is the shape rather than who wrote the line.
/// Merging the two would apply each argument to the path it was not made for.
const SUDO_LINE_PREFIXES: &[&str] = &["sudo:"];
pub(crate) const AGENT_USAGE_LINE_PREFIXES: &[&str] = &["usage:", "Usage:"];

impl Guest<'_> {
    /// The worker this session reaches.
    pub fn worker(&self) -> &str {
        self.channel.worker()
    }

    /// The scope a note about this worker carries.
    pub(crate) fn scope(&self) -> Scope {
        Scope::worker(self.worker())
    }

    /// Runs one command inside the worker and fails unless it exited 0, withholding what it printed.
    ///
    /// The withholding is the contract, not caution: a guest process can echo the UI-test bridge token, and a
    /// refusal ends up inside a JSON envelope an agent reads and may log. See [`Runner::checked_quietly`], whose
    /// asymmetry with [`Runner::checked`] this is the guest side of.
    ///
    /// The message names the *effective* guest program and the worker, not `argv[0]`, which on this channel is
    /// almost never the program that ran: `tart exec` lands in the guest as root, so every real call is
    /// re-targeted through [`user_argv`] or [`root_argv`] and `argv[0]` is `/usr/bin/sudo` for all of them. `sudo
    /// in air-linux-1 exited with 64` is what that produced, and on 2026-08-24 a session read it, concluded
    /// passwordless sudo was gone from the guest, and abandoned two workers - while 64 was `EX_USAGE` from the
    /// guest agent four argv elements further along.
    ///
    /// The argument list travels with the name; guest output still does not. Redacted and bounded by
    /// [`redact_guest_argv`], which is the one policy on what may be said about a guest argv: an element that
    /// holds a value is replaced whole rather than trusted, and the rendering stops rather than being cut.
    ///
    /// # The exception, and its shape
    ///
    /// A first line of stderr that begins with `sudo:` is quoted. Sudo prints that prefix from its own
    /// diagnostics, before it has exec'd anything - but the stream is the whole command's, and this runs argv it
    /// did not necessarily compose, so a program sudo *did* run can begin a line that way too. What bounds this is
    /// therefore the shape and not the provenance: the first non-blank line alone, clipped. Quoting it at all is
    /// worth that: naming the effective program makes a broken wrapper *less* obvious, so without this a guest
    /// that really had lost passwordless sudo would read as a guest agent refusing every verb.
    pub async fn raw(&self, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        if argv.is_empty() {
            return Err(Refusal::internal("empty guest command"));
        }
        let captured = self.channel.exec(self.ctx, argv, options).await?;
        if captured.exit_code == 0 {
            return Ok(captured);
        }
        let (program, arguments) = effective_guest_program(argv);
        let mut message = format!("{program} in {} exited with {}", self.worker(), captured.exit_code);
        let rendered = redact_guest_argv(arguments, GUEST_ARGV_MESSAGE_BOUNDS);
        if !rendered.is_empty() {
            message.push_str(&format!(" ({rendered})"));
        }
        message.push_str("; guest output was withheld");
        if let Some(complaint) = sudo_complaint(&captured.stderr) {
            message.push_str("; ");
            message.push_str(&complaint);
        }
        Err(
            Refusal::new("subprocess_failed", Exit::from_status(captured.exit_code, Exit::FAILURE), message)
                .with_details(json!({ "exitCode": captured.exit_code })),
        )
    }

    /// Runs a command as the account the daemon and the IDE run as. See [`user_argv`] for the prefix.
    pub async fn as_user(&self, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        self.raw(&user_argv(self.settings, argv), options).await
    }

    /// Runs a command as root in the guest. See [`root_argv`] for the prefix.
    pub async fn as_root(&self, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        self.raw(&root_argv(argv), options).await
    }

    /// Whether a guest command exited 0, and nothing about why it did not.
    ///
    /// For the probes: is the share mounted, is the directory there, is it writable. A timeout is reported as
    /// `guest_agent_timeout`, so that "the guest never answered" is distinguishable from "a host tool hung" - though
    /// this swallows both, by design, because a probe's answer is a boolean. A probe that ran out of `timeout`
    /// answers false.
    pub async fn succeeds(&self, argv: &[String], timeout: Duration) -> bool {
        self.raw(argv, &SpawnOptions::timeout(timeout, "guest_agent_timeout")).await.is_ok()
    }

    /// Puts bytes in the guest at a path the worker user owns.
    ///
    /// `tee` over the exec channel's stdin, because there is no file transfer on either backend's exec: what both
    /// can carry is one argv and one stdin. The mode is applied afterwards with an explicit `chmod` rather than
    /// left to `tee`, whose destination is created under the guest's umask.
    ///
    /// A backend whose exec needs a tty to carry stdin must turn that on whenever [`SpawnOptions::stdin`] is set,
    /// and the production channel does. Stated here because the failure is silent: a write whose stdin was never
    /// forwarded produces an empty file and exits 0.
    ///
    /// Not through [`Guest::raw`]: this is the one guest call whose own exit code has to reach the refusal under a
    /// code of its own, because several steps of a daemon restart write a guest file and a caller must tell "the
    /// write failed" from "the command failed".
    pub async fn write_file(&self, path: &str, content: &[u8], mode: &str) -> Result<(), Refusal> {
        let argv = user_argv(self.settings, &words(["/usr/bin/tee", path]));
        // Some even for empty content: no stdin would leave `tee` reading whatever the channel inherits.
        let options = SpawnOptions {
            stdin: Some(content.to_vec()),
            ..SpawnOptions::within(GUEST_WRITE_TIMEOUT)
        };
        let captured = self.channel.exec(self.ctx, &argv, &options).await?;
        if captured.exit_code != 0 {
            return Err(Refusal::new(
                "guest_write_failed",
                Exit::from_status(captured.exit_code, Exit::FAILURE),
                format!("could not write {path}; tee exited with {}", captured.exit_code),
            )
            .with_details(json!({ "stderr": clip(&captured.stderr, FAILURE_OUTPUT_TAIL_BYTES) })));
        }
        self.as_user(&words(["/bin/chmod", mode, path]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await
            .map(drop)
    }

    /// Puts a secret in the guest at a path the worker user owns, mode 0600 from its creation.
    ///
    /// Not [`Guest::write_file`], for three reasons. `tee` echoes its stdin to its stdout, which the channel captures
    /// into memory a refusal or a trace could reach. `tee` creates the file under the guest umask, so it is readable
    /// until the `chmod` lands. And that refusal quotes `tee`'s stderr.
    ///
    /// Here `cat` writes the file under `umask 077` and prints nothing, the content travels on stdin only, and the
    /// argv names the path and no value. A failure withholds all guest output: the exit code says the write failed,
    /// and the path says which.
    pub async fn write_secret_file(&self, path: &str, content: &[u8]) -> Result<(), Refusal> {
        let argv = user_argv(self.settings, &secret_write_argv(path));
        let options = SpawnOptions {
            stdin: Some(content.to_vec()),
            ..SpawnOptions::within(GUEST_COMMAND_TIMEOUT)
        };
        let captured = self.channel.exec(self.ctx, &argv, &options).await?;
        if captured.exit_code != 0 {
            return Err(Refusal::new(
                "guest_write_failed",
                Exit::from_status(captured.exit_code, Exit::FAILURE),
                format!(
                    "could not write the secret file {path}; sh exited with {}; guest output was withheld",
                    captured.exit_code
                ),
            ));
        }
        Ok(())
    }

    /// Whether the worker has a login session to run in: a macOS worker needs an Aqua session at its console, and a
    /// Linux worker has no login session to wait for.
    pub async fn has_console_login(&self) -> Result<bool, Refusal> {
        if !self.settings.is_macos_guest() {
            return Ok(true);
        }
        static CONSOLE_SESSION: LazyLock<Regex> =
            LazyLock::new(|| Regex::new(r"^\S+\s+console(\s|$)").expect("a constant pattern compiles"));
        let captured = self
            .raw(&words(["/usr/bin/who"]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await?;
        Ok(captured.stdout.lines().any(|line| CONSOLE_SESSION.is_match(line)))
    }

    /// Refuses a macOS worker with no Aqua session ([`Self::has_console_login`]).
    ///
    /// A Linux worker has no login session to wait for: its display is an Xvfb the controller starts, not a seat a
    /// user logs into. Checked before a run rather than diagnosed after one, because running without it is an IDE
    /// that starts, finds no window server, and reports something else entirely minutes later.
    pub async fn require_console_login(&self) -> Result<(), Refusal> {
        if self.has_console_login().await? {
            return Ok(());
        }
        Err(Refusal::new(
            "console_login_required",
            Exit::FAILURE,
            format!(
                "worker {} has no Aqua session; log in as {} at its console, or configure automatic login and \
                 disable sleep/lock so a boot needs no human",
                self.worker(),
                self.settings.vm_user
            ),
        ))
    }
}

/// Prefixes `argv` with the sudo invocation that runs it as the account the daemon and the IDE run as.
///
/// `sudo -H` and not a bare `sudo`: without it the command keeps the invoking user's `HOME`, and everything the
/// lane touches - the agent CLIs under `$HOME/.local/bin`, the IDE's config, the JVM's caches - is resolved from a
/// home directory that is not the one the run uses. Public because `observe` needs the same prefix on argv it does
/// not route through [`Guest::raw`], and a second spelling of a security prefix is the copy that drifts.
pub fn user_argv(settings: &Config, argv: &[String]) -> Vec<String> {
    let mut line = words(["/usr/bin/sudo", "-H", "-u", &settings.vm_user]);
    line.extend_from_slice(argv);
    line
}

/// The guest command of [`Guest::write_secret_file`]: a shell that sets the umask before `cat` creates the file. The
/// path is the shell's `$1` and never part of the script, so no quoting of it can break the command.
fn secret_write_argv(path: &str) -> Vec<String> {
    words(["/bin/sh", "-c", "umask 077 && exec cat > \"$1\"", "sh", path])
}

/// Prefixes `argv` with the sudo invocation that runs it as root. `-H` because the worker prefix has it: two
/// prefixes differing in one flag would be a difference nothing explains.
pub fn root_argv(argv: &[String]) -> Vec<String> {
    let mut line = words(["/usr/bin/sudo", "-H"]);
    line.extend_from_slice(argv);
    line
}

/// The basename of the program a guest argv actually runs, and the arguments it runs it with, found by walking
/// past the security and session wrappers this controller puts in front of everything.
///
/// The shapes it skips are exactly the shapes this controller builds, and it is deliberately not a general argv
/// parser: [`user_argv`]'s `sudo -H -u USER`, [`root_argv`]'s `sudo -H`, the supervisor's two aqua variants -
/// `launchctl asuser UID` in front of the sudo on macOS, `setsid --wait /usr/bin/env DISPLAY=…` behind it on Linux
/// - plus the `/usr/bin/env NAME=VALUE…` every agent invocation carries. Anything else is a program.
///
/// A wrapper that consumes the whole argv names itself, because then it really is the last thing that ran.
pub(crate) fn effective_guest_program(argv: &[String]) -> (&str, &[String]) {
    if argv.is_empty() {
        return ("", &[]);
    }
    let mut index = 0;
    loop {
        let width = guest_wrapper_width(argv, index);
        if width == 0 || index + width >= argv.len() {
            break;
        }
        index += width;
    }
    (base_name(&argv[index]), &argv[index + 1..])
}

/// How many argv elements the wrapper at `index` occupies, or 0 when what is there is a program to report rather
/// than a wrapper to walk past.
fn guest_wrapper_width(argv: &[String], index: usize) -> usize {
    static ENVIRONMENT_ASSIGNMENT: LazyLock<Regex> =
        LazyLock::new(|| Regex::new(r"^[A-Za-z_][A-Za-z0-9_]*=").expect("a constant pattern compiles"));
    let flag_at = |position: usize| argv.get(position).is_some_and(|word| word.starts_with('-'));
    let mut width = 1;
    match base_name(&argv[index]) {
        "sudo" => {
            // `-u` takes the account name with it; every other flag this controller passes sudo is a bare one.
            while flag_at(index + width) {
                if argv[index + width] == "-u" {
                    width += 1;
                }
                width += 1;
            }
            width
        }
        // Only `asuser UID`, the one launchctl subcommand this controller runs a program under. Every other one
        // *is* the command - `launchctl print` failing is not a wrapper failing.
        "launchctl" if index + 2 < argv.len() && argv[index + 1] == "asuser" => 3,
        "setsid" => {
            while flag_at(index + width) {
                width += 1;
            }
            width
        }
        "env" => {
            while argv.get(index + width).is_some_and(|word| ENVIRONMENT_ASSIGNMENT.is_match(word)) {
                width += 1;
            }
            width
        }
        _ => 0,
    }
}

/// Sudo's own first line of stderr, or `None` for anything else on that stream. See [`Guest::raw`] for why this
/// one line is quoted when the rest is withheld.
pub(crate) fn sudo_complaint(stderr: &str) -> Option<String> {
    quoted_first_line(stderr, SUDO_LINE_PREFIXES)
}

/// A stream's first non-blank line when it begins with one of a named set of prefixes, clipped to
/// [`GUEST_ARGV_MESSAGE_BYTES`], and `None` for everything else.
///
/// One function for the *shape* of both exceptions to the withholding contract: the first line and no more of the
/// stream, a prefix test rather than a substring one, and the clip. A prefix test because what is being claimed is
/// about the *beginning* of the line, the one part a program cannot have inherited from something it ran. The
/// prefixes are not this function's: each caller names one of the two lists declared above.
pub(crate) fn quoted_first_line(text: &str, prefixes: &[&str]) -> Option<String> {
    let line = text.lines().map(str::trim).find(|line| !line.is_empty())?;
    prefixes
        .iter()
        .any(|prefix| line.starts_with(prefix))
        .then(|| clip(line, GUEST_ARGV_MESSAGE_BYTES).to_owned())
}

/// A guest path under a guest directory. Guest paths are unix paths the controller composes, so this is a join of
/// two strings rather than a host path operation.
pub fn guest_join(directory: &str, name: &str) -> String {
    format!("{}/{name}", directory.trim_end_matches('/'))
}

/// A host path as the text a guest argv or a receipt carries.
pub(crate) fn path_text(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

fn base_name(program: &str) -> &str {
    program.rsplit('/').next().unwrap_or(program)
}
