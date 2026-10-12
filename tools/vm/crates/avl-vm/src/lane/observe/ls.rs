//! `ls`: a listing of guest paths, so `pull` can be aimed at an artifact whose exact path nobody knew.

use std::path::Path;
use std::time::Duration;

use crate::worker::worker::Manager;
use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::guest::user_argv;
use avl_host_sys::{Ctx, SpawnOptions};
use serde::Serialize;

use super::{split_lines, with_leased_worker};

/// The timeout of the guest `find` of `ls`. A deep listing of the IDE output walks many thousands of entries.
const LS_TIMEOUT: Duration = Duration::from_mins(10);

/// The most entries one listing answers; more is named `truncated` rather than dropped silently.
pub(crate) const LS_ENTRY_LIMIT: usize = 400;

/// The deepest `--depth` accepts.
pub(crate) const LS_MAX_DEPTH: u8 = 4;

/// `ls <guest-directory> [--depth N]`, for `vm` to flatten into its subcommand.
#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct LsArgs {
    /// The absolute guest directory to list.
    #[arg(value_name = "GUEST_DIRECTORY", value_parser = absolute_guest_directory)]
    pub directory: String,
    /// How many levels below the directory to list, 1 to 4.
    #[arg(long, value_name = "N", default_value_t = 1, value_parser = ls_depth)]
    pub depth: u8,
}

/// One path, passed as one argv element. No shell runs it, so a metacharacter is a filename and not a command, but
/// a relative path would resolve against whatever directory the guest channel happens to start in.
fn absolute_guest_directory(value: &str) -> Result<String, String> {
    if value.starts_with('/') {
        Ok(value.to_owned())
    } else {
        Err("the guest directory must be absolute".to_owned())
    }
}

/// What may be a `--depth` value: base-ten digits and nothing else, once surrounding blanks are trimmed.
///
/// Deliberately stricter than a number parser: `2.0`, `0x2`, `1e1` and `+2` are all refused rather than
/// reinterpreted (a JavaScript `Number(raw)` accepts the first three, and `u8::from_str` accepts the last).
/// The trim is kept, because a padded value from a shell here-doc is a real spelling.
fn ls_depth(raw: &str) -> Result<u8, String> {
    let refused = || format!("--depth must be an integer in 1..{LS_MAX_DEPTH}");
    let trimmed = raw.trim();
    if trimmed.is_empty() || !trimmed.bytes().all(|byte| byte.is_ascii_digit()) {
        return Err(refused());
    }
    match trimmed.parse::<u8>() {
        Ok(depth) if (1..=LS_MAX_DEPTH).contains(&depth) => Ok(depth),
        _ => Err(refused()),
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct LsData {
    worker: String,
    directory: String,
    depth: u8,
    entries: Vec<String>,
    truncated: bool,
    /// A listing `find` could not complete: it exited 1 after it listed what it could read. An IDE output tree
    /// holds directories the worker user cannot read, so a deep listing of it always ends this way.
    partial: bool,
}

/// Lists guest paths as the worker user.
///
/// Deliberately not `exec`, and deliberately allowed while a run is active. `exec` refuses mid-run because an
/// arbitrary command in the guest can take the pointer or the focus a UI run depends on; this cannot, for the same
/// reason `pull` cannot - one fixed read-only argv over the non-GUI channel, no shell, no glob. Without it the
/// permitted operation was unusable exactly when it is needed: the daemon keeps its run active for as long as it
/// holds the warm IDE, so a failing lane could not be diagnosed without stopping the thing that makes the next
/// iteration fast.
pub(crate) async fn command_ls(ctx: &Ctx, manager: &Manager, args: LsArgs, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
    let LsArgs { directory, depth } = args;
    let settings = manager.settings();
    let data = with_leased_worker(ctx, manager, lease_file, "ls", async |current| {
        manager.require_ready(ctx, &current).await?;
        let find = [
            "/usr/bin/find".to_owned(),
            directory.clone(),
            "-maxdepth".to_owned(),
            depth.to_string(),
        ];
        let options = SpawnOptions {
            allow_truncated: true,
            ..SpawnOptions::within(LS_TIMEOUT)
        };
        let captured = manager
            .channel(&current.worker)
            .exec(ctx, &user_argv(settings, &find), &options)
            .await?;
        // `find` exits 1 when it could not read some entry, and it still lists every entry it could read. A refusal
        // there answered nothing for the whole tree, so a deep listing of the IDE output was unusable. The listing
        // is kept and named partial. An exit of 1 with no output is still a failure: then the directory itself
        // could not be read.
        let partial = captured.exit_code == 1 && !captured.stdout.trim().is_empty();
        if captured.exit_code != 0 && !partial {
            return Err(Refusal::new(
                "ls_failed",
                Exit::from_status(captured.exit_code, Exit::FAILURE),
                format!("guest find exited {}; guest output was withheld", captured.exit_code),
            ));
        }
        let all: Vec<&str> = split_lines(&captured.stdout).filter(|line| !line.is_empty()).collect();
        let entries: Vec<String> = all.iter().take(LS_ENTRY_LIMIT).map(|line| (*line).to_owned()).collect();
        Ok(LsData {
            worker: current.worker,
            directory: directory.clone(),
            depth,
            // Named rather than silent: a listing cut off at the cap is not the same fact as an empty directory.
            truncated: all.len() > entries.len() || captured.stdout_truncated,
            entries,
            partial,
        })
    })
    .await?;
    let text = ls_text(&data.entries, data.partial);
    Outcome::new(data, text)
}

/// The listing for `--text`, with the partial fact as a last line so a reader does not take a gap for an empty
/// directory.
fn ls_text(entries: &[String], partial: bool) -> String {
    let mut text = entries.join("\n");
    if partial {
        text.push_str("\n(partial: find could not read some entries)");
    }
    text
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
