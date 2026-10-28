//! The ssh calls of `bt --host <alias> --prepare`: the repository, the PATH, the tools and the credentials of the
//! host. A run needs no ssh: `bd-host` carries it.

use std::ffi::OsString;
use std::time::Duration;

use bt_core::runtime::{Runtime, SpawnResult};

use crate::execute::posix_shell_quote;
use crate::remote::Host;

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
pub(crate) const COPY_IDLE: Duration = Duration::from_mins(30);

/// The git URL of the main repository on the host.
pub(crate) fn push_url(alias: &str, repo: &str) -> String {
    format!("{alias}:{}", repo.strip_prefix("~/").unwrap_or(repo))
}

/// The main repository of `--prepare` on the host.
pub(crate) fn host_repo<'a>(host: &Host<'a>) -> &'a str {
    host.repo.unwrap_or(crate::remote::DEFAULT_HOST_REPO)
}

/// The `-c` value that makes git run ssh in batch mode.
pub(crate) fn ssh_git_config() -> String {
    format!("core.sshCommand=ssh {}", SSH_OPTIONS.join(" "))
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
    let command: Vec<OsString> = std::iter::once("git").chain(args.iter().copied()).map(OsString::from).collect();
    runtime.spawn_watched(&command, PUSH_IDLE)
}

#[cfg(test)]
mod tests;
