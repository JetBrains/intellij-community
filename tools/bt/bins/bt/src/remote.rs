//! `bt --host <ssh alias>`: one run of bt on another machine, from the committed HEAD of this checkout.
//!
//! The steps:
//!
//! 1. Refuse a checkout with local changes, because only the commit reaches the host.
//! 2. Push HEAD to the main repository of the host as `refs/bt-remote/<id>`. Only the missing git objects travel.
//! 3. Run `remote.sh` over ssh. It keeps one warm Branch Deck lease on the host for this checkout, checks the commit
//!    out there, and runs the same bt command without `--host`. The stderr of the host streams here as it comes. The
//!    stdout is the digest or the `--json` payload, and the exit code of the host is the exit code here.
//! 4. Copy the new run directories of `out/bt-runs` on the host to `out/bt-runs` here.
//!
//! The host script deletes the ref on every way out. The end of its stdin tells it that this process is gone, so
//! Ctrl+C or a kill here interrupts the run on the host too. `--release` returns the lease.

use std::ffi::OsString;
use std::path::PathBuf;
use std::time::Duration;

use bt_core::evidence::RUNS_DIR;
use bt_core::runtime::{Runtime, SpawnResult, repo_file};
use bt_core::{Refusal, exit};

use crate::execute::{JsonPayload, Outcome, posix_shell_quote};

/// The main repository on the host when `--host-repo` and `BT_HOST_REPO` name none.
pub(crate) const DEFAULT_HOST_REPO: &str = "~/projects/idea-1";

/// The namespace of the refs that a push makes on the host.
const REF_PREFIX: &str = "refs/bt-remote/";

/// The parent of the stage of each run on the host, relative to the home directory.
const STAGE_PARENT: &str = ".cache/bt-remote/runs";

/// How long a lease of another checkout may stay unused on the host before a run returns it, when
/// `--host-idle-hours` and `BT_HOST_IDLE_HOURS` name no count.
pub(crate) const DEFAULT_IDLE_HOURS: u32 = 8;

/// The host half, run by `sh -c`.
const HOST_SCRIPT: &str = include_str!("remote.sh");

/// The exit code of ssh for its own failure: no connection, no authentication.
const SSH_FAILED: i32 = 255;

/// The ssh options of every ssh, scp and git push to a host, so that a dead link fails in bounded time.
///
/// - `BatchMode`: a prompt would wait for a person who is not there.
/// - `ConnectTimeout`: on a Mac, a connect to a `.local` name without it waited more than 170 s in each try, while the
///   same connect with it took less than a second. The timeout also bounds the key exchange.
/// - `ServerAliveInterval` and `ServerAliveCountMax`: a link that stops answering ends the connection after 60 s.
/// - `ControlMaster=no` and `ControlPath=none`: a multiplexed client ignores its own keepalive options and waits for
///   its master, and a master without keepalives, or a stopped one, keeps the client waiting for ever. So each call
///   opens its own connection, and the options above apply to it.
/// - `LogLevel=ERROR`: no warning of ssh becomes a part of an answer that bt reads, such as a credential file.
pub(crate) const SSH_OPTIONS: [&str; 14] = [
    "-o",
    "BatchMode=yes",
    "-o",
    "ConnectTimeout=15",
    "-o",
    "ServerAliveInterval=15",
    "-o",
    "ServerAliveCountMax=4",
    "-o",
    "ControlMaster=no",
    "-o",
    "ControlPath=none",
    "-o",
    "LogLevel=ERROR",
];

/// How long a short call to the host may write nothing before it is stopped: the options do not bound a wait for
/// the key agent, which signs for each connection.
pub(crate) const CALL_IDLE: Duration = Duration::from_secs(120);

/// How long a git push may write nothing before it is stopped. It runs with `--progress`, so a push that moves
/// objects writes a line every second.
pub(crate) const PUSH_IDLE: Duration = Duration::from_secs(300);

/// How long an scp may run, since it writes nothing while it copies.
pub(crate) const COPY_IDLE: Duration = Duration::from_secs(1800);

/// The flags of this module with a value, which the host run does not get.
const HOST_FLAGS: [&str; 3] = ["--host", "--host-repo", "--host-idle-hours"];

/// The switches of this module, which the host run does not get.
const HOST_SWITCHES: [&str; 4] = ["--release", "--prepare", "--yes", "--take-lease"];

/// Where a run goes, from the local command line.
pub(crate) struct Host<'a> {
    /// The ssh alias.
    pub(crate) alias: &'a str,
    /// The main repository on the host.
    pub(crate) repo: &'a str,
    pub(crate) idle_hours: u32,
}

/// One run on the host, given as the whole local command line.
pub(crate) fn run_on_host(host: &Host<'_>, json: bool, argv: &[OsString], runtime: &dyn Runtime) -> Result<Outcome, Refusal> {
    let alias = host.alias;
    let id = format!("{}-{}", runtime.now_ms(), std::process::id());
    refuse_local_changes(runtime)?;
    let commit = head_commit(runtime)?;
    let reference = format!("{REF_PREFIX}{id}");
    push(runtime, host, &commit, &reference)?;

    let forwarded = host_args(argv);
    let short = commit.get(..12).unwrap_or(&commit);
    runtime.write_error(&format!("> {alias}: bt {}  (commit {short}, run {id})", forwarded.join(" ")));
    let script = HostScript {
        mode: "run",
        holder: &holder(runtime),
        id: &id,
        commit: &commit,
    };
    let ran = runtime.spawn_streaming(&ssh(alias, &host_command(host, &script, &forwarded)));
    if ran.exit_code == SSH_FAILED {
        // The host script deletes the ref, unless ssh never started it.
        let deleted = remote_call(
            runtime,
            &ssh(alias, &format!("git -C {} update-ref -d {reference}", host_path_word(host.repo))),
        );
        if deleted.exit_code != 0 {
            runtime.write_error(&format!("cannot delete {reference} on {alias}: {}", deleted.output.trim()));
        }
    }
    let copied = copy_evidence(runtime, alias, &id);
    answer(alias, json, &ran, &copied)
}

/// Returns the lease that the runs of this checkout keep on the host.
pub(crate) fn release_on_host(host: &Host<'_>, runtime: &dyn Runtime) -> Result<Outcome, Refusal> {
    let script = HostScript {
        mode: "release",
        holder: &holder(runtime),
        id: &format!("release-{}", runtime.now_ms()),
        commit: "",
    };
    let ran = runtime.spawn_streaming(&ssh(host.alias, &host_command(host, &script, &[])));
    answer(host.alias, false, &ran, &[])
}

/// The lease holder of this checkout on a host: one per checkout, so two checkouts never share a slot there.
pub(crate) fn holder(runtime: &dyn Runtime) -> String {
    // FNV-1a: stable across builds and platforms, unlike the hasher of the standard library.
    let hash = runtime
        .repo_root()
        .to_string_lossy()
        .bytes()
        .fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
            (hash ^ u64::from(byte)).wrapping_mul(0x0100_0000_01b3)
        });
    format!("bt-remote-{:012x}", hash & 0xffff_ffff_ffff)
}

/// The arguments of the host script, besides the host and the bt arguments.
pub(crate) struct HostScript<'a> {
    /// `run`, `lease` or `release`.
    pub(crate) mode: &'a str,
    pub(crate) holder: &'a str,
    pub(crate) id: &'a str,
    pub(crate) commit: &'a str,
}

/// The command line of the host: the local one less the flags of this module. Everything after `--` is bazel's.
pub(crate) fn host_args(argv: &[OsString]) -> Vec<String> {
    let mut forwarded = Vec::new();
    let mut arguments = argv.iter().map(|argument| argument.to_string_lossy().into_owned());
    while let Some(argument) = arguments.next() {
        if argument == "--" {
            forwarded.push(argument);
            forwarded.extend(arguments.by_ref());
            break;
        }
        if HOST_FLAGS.contains(&argument.as_str()) {
            arguments.next();
            continue;
        }
        if HOST_SWITCHES.contains(&argument.as_str()) {
            continue;
        }
        let is_host_flag = HOST_FLAGS
            .iter()
            .any(|flag| argument.strip_prefix(flag).is_some_and(|rest| rest.starts_with('=')));
        if !is_host_flag {
            forwarded.push(argument);
        }
    }
    forwarded
}

/// Refuses a checkout whose tracked or untracked files differ from HEAD.
fn refuse_local_changes(runtime: &dyn Runtime) -> Result<(), Refusal> {
    let status = git(runtime, &["status", "--porcelain"]);
    if status.exit_code != 0 {
        return Err(host_infra(&format!("git status failed: {}", status.output.trim())));
    }
    let changed: Vec<&str> = status.output.lines().filter(|line| !line.trim().is_empty()).collect();
    if changed.is_empty() {
        return Ok(());
    }
    let shown: Vec<&str> = changed.iter().take(5).copied().collect();
    let more = match changed.len() {
        0..=5 => String::new(),
        count => format!("\n  (+{} more)", count - 5),
    };
    Err(Refusal::new(
        "host_local_changes",
        exit::USAGE,
        format!(
            "--host runs the committed HEAD, and this checkout has local changes. Commit or stash them first:\n  {}{more}",
            shown.join("\n  ")
        ),
    ))
}

fn head_commit(runtime: &dyn Runtime) -> Result<String, Refusal> {
    let head = git(runtime, &["rev-parse", "--verify", "HEAD"]);
    let commit = head.output.trim();
    if head.exit_code != 0 || commit.is_empty() {
        return Err(host_infra(&format!("git rev-parse HEAD failed: {commit}")));
    }
    Ok(commit.to_owned())
}

/// The git URL of the main repository on the host.
pub(crate) fn push_url(host: &Host<'_>) -> String {
    format!("{}:{}", host.alias, host.repo.strip_prefix("~/").unwrap_or(host.repo))
}

/// The `-c` value that makes git run ssh in batch mode.
pub(crate) fn ssh_git_config() -> String {
    format!("core.sshCommand=ssh {}", SSH_OPTIONS.join(" "))
}

fn push(runtime: &dyn Runtime, host: &Host<'_>, commit: &str, reference: &str) -> Result<(), Refusal> {
    let url = push_url(host);
    let ssh_command = ssh_git_config();
    let pushed = watched_git(
        runtime,
        &["-c", &ssh_command, "push", "--progress", &url, &format!("{commit}:{reference}")],
    );
    if pushed.exit_code != 0 {
        return Err(host_infra(&format!("cannot push HEAD to {url}: {}", pushed.output.trim())));
    }
    Ok(())
}

/// Copies each run directory of the stage on the host to `out/bt-runs` here, then removes the stage. Answers the
/// local paths. A copy that fails is a warning: the run already has its verdict.
fn copy_evidence(runtime: &dyn Runtime, host: &str, id: &str) -> Vec<PathBuf> {
    let stage = format!("{STAGE_PARENT}/{id}");
    // The host script removes an empty stage, so a missing one is a run without evidence.
    let listed = remote_call(runtime, &ssh(host, &format!("ls -1 {stage} 2>/dev/null")));
    let runs: Vec<&str> = listed
        .output
        .lines()
        .map(str::trim)
        .filter(|name| name.starts_with(|first: char| first.is_ascii_digit()))
        .collect();
    if listed.exit_code != 0 || runs.is_empty() {
        return Vec::new();
    }
    let runs_dir = repo_file(runtime, RUNS_DIR);
    let mut copied = Vec::new();
    if let Err(error) = runtime.create_dir_all(&runs_dir) {
        runtime.write_error(&format!("cannot create {}: {error}", runs_dir.display()));
    } else {
        let mut command = scp(&["-rq"]);
        command.extend(runs.iter().map(|run| OsString::from(format!("{host}:{stage}/{run}"))));
        command.push(runs_dir.clone().into_os_string());
        let fetched = runtime.spawn_watched(&command, COPY_IDLE);
        if fetched.exit_code == 0 {
            copied.extend(runs.iter().map(|run| runs_dir.join(run)));
        } else {
            runtime.write_error(&format!("cannot copy the evidence from {host}:{stage}: {}", fetched.output.trim()));
        }
    }
    let removed = remote_call(runtime, &ssh(host, &format!("rm -rf {stage}")));
    if removed.exit_code != 0 {
        runtime.write_error(&format!("cannot remove {host}:{stage}: {}", removed.output.trim()));
    }
    copied
}

/// The outcome of the run on the host: its digest or payload, its exit code, and the local copies of its evidence.
fn answer(host: &str, json: bool, ran: &SpawnResult, copied: &[PathBuf]) -> Result<Outcome, Refusal> {
    let exit_code = local_exit(ran.exit_code);
    let notes: Vec<String> = copied
        .iter()
        .map(|path| format!("copied {}  (the kept logs of the run on {host})", path.display()))
        .collect();
    let stdout = ran.output.trim_end();
    let silent = |missing: &str| {
        Refusal::new(
            "host_run_failed",
            exit_code,
            format!(
                "the run on {host} exited with {} and wrote {missing}; its stderr is above",
                ran.exit_code
            ),
        )
    };
    if json {
        let payload: JsonPayload = serde_json::from_str(stdout).map_err(|error| silent(&format!("no JSON payload ({error})")))?;
        return Ok(Outcome {
            exit_code,
            text: notes.join("\n"),
            json: Some(payload),
        });
    }
    if stdout.is_empty() {
        return Err(silent("no digest"));
    }
    let mut text = stdout.to_owned();
    for note in &notes {
        text.push('\n');
        text.push_str(note);
    }
    Ok(Outcome {
        exit_code,
        text,
        json: None,
    })
}

/// The exit code of the host as an exit code of bt: each code of bt maps to itself, and any other code, such as the
/// 255 of ssh or the 130 of an interrupt, is [`exit::INFRA`].
pub(crate) fn local_exit(code: i32) -> u8 {
    match u8::try_from(code) {
        Ok(code @ (exit::GREEN | exit::USAGE | exit::TEST_FAILED | exit::NO_TESTS | exit::BUILD_FAILED | exit::INFRA)) => code,
        _ => exit::INFRA,
    }
}

/// The text that the login shell of the host runs: `sh -c` with the host script and its arguments.
pub(crate) fn host_command(host: &Host<'_>, script: &HostScript<'_>, args: &[String]) -> String {
    let mut words = vec![
        "sh".to_owned(),
        "-c".to_owned(),
        posix_shell_quote(&host_script()),
        "bt-remote".to_owned(),
    ];
    let idle_hours = host.idle_hours.to_string();
    words.extend([script.mode, host.repo, script.holder, script.id, script.commit, &idle_hours].map(posix_shell_quote));
    words.extend(args.iter().map(|arg| posix_shell_quote(arg)));
    words.join(" ")
}

/// The host script without its comment lines, which every `ps` line of the host would show.
fn host_script() -> String {
    HOST_SCRIPT
        .lines()
        .filter(|line| !line.trim_start().starts_with('#'))
        .map(|line| format!("{line}\n"))
        .collect()
}

/// A host path as one word for the login shell of the host: a leading `~/` stays outside the quotes, so the shell
/// expands it.
pub(crate) fn host_path_word(path: &str) -> String {
    match path.strip_prefix("~/") {
        Some(rest) => format!("~/{}", posix_shell_quote(rest)),
        None => posix_shell_quote(path),
    }
}

/// The argv of one command on the host.
pub(crate) fn ssh(host: &str, command: &str) -> Vec<OsString> {
    std::iter::once("ssh")
        .chain(SSH_OPTIONS)
        .chain([host, command])
        .map(OsString::from)
        .collect()
}

/// The argv of an scp with its flags, before its sources and its target.
pub(crate) fn scp(flags: &[&str]) -> Vec<OsString> {
    std::iter::once("scp")
        .chain(flags.iter().copied())
        .chain(SSH_OPTIONS)
        .map(OsString::from)
        .collect()
}

/// Runs a short command on the host, stopped after [`CALL_IDLE`] without output.
pub(crate) fn remote_call(runtime: &dyn Runtime, command: &[OsString]) -> SpawnResult {
    runtime.spawn_watched(command, CALL_IDLE)
}

/// Runs a local git that talks to the host, such as a push, stopped after [`PUSH_IDLE`] without output.
pub(crate) fn watched_git(runtime: &dyn Runtime, args: &[&str]) -> SpawnResult {
    runtime.spawn_watched(&git_argv(args), PUSH_IDLE)
}

fn git(runtime: &dyn Runtime, args: &[&str]) -> SpawnResult {
    runtime.spawn(&git_argv(args), None)
}

fn git_argv(args: &[&str]) -> Vec<OsString> {
    std::iter::once("git").chain(args.iter().copied()).map(OsString::from).collect()
}

fn host_infra(message: &str) -> Refusal {
    Refusal::new("host_infra", exit::INFRA, format!("bt --host: {message}"))
}

#[cfg(test)]
mod tests;
