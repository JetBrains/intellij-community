//! `ide-gc`: the live IDE runs under an IDE root, canceled or kept, and the old log directories of each context.
//!
//! The controller calls it where it has no daemon to ask: `--stop-all` when it releases or recycles a worker or
//! stops the daemon, and `--keep-product <digest>` when a restart of the daemon keeps the IDE of the product. Then
//! an IDE whose launch record names the same product digest survives the restart. Without either flag it cancels
//! nothing. Each call also keeps only the newest `--keep-logs` log directories of each context, and never the log
//! directory of a live IDE.
//!
//! A cancel that its supervisor does not end in time ends with a kill, and the run is `killed`. A run whose processes
//! outlive the kill is `abandoned`: its slot is free, and the record [`ABANDONED_FILE`] in its context names it on
//! each later call, until its processes are gone.

use std::fs;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use avl_wire::ide::{self, ABANDONED_FILE, AbandonedRun, IdeGcResult, IdeRun, LAUNCH_RECORD_FILE, LOG_DIR, LaunchRecord};
use avl_wire::verb::AgentVerb;

use super::live_run;
use crate::cli::{CancelArgs, IdeGcArgs, RootArgs, RunArgs};
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::supervisor::{self, CancelEnd, System};

#[cfg(test)]
mod tests;

/// Cancels or keeps the live IDE runs under `args.root`, removes the old log directories, and answers what it did.
pub(crate) fn collect(system: &dyn System, args: &IdeGcArgs) -> Result<IdeGcResult, AgentRefusal> {
    let mut result = IdeGcResult::default();
    for launch_key in context_names(&args.root)? {
        let context = args.root.join(&launch_key);
        let mut live_launch = None;
        if let Some(run) = live_run(system, &context)? {
            let entry = IdeRun {
                launch_key: launch_key.clone(),
                run_id: run.run_id.clone(),
            };
            if should_stop(&context, args) {
                let end = supervisor::cancel_to_end(
                    system,
                    &CancelArgs {
                        run: RunArgs {
                            root: RootArgs { root: context.clone() },
                            run_id: run.run_id.clone(),
                        },
                        grace_ms: args.grace_ms,
                        thread_dump: None,
                    },
                )?;
                match end {
                    CancelEnd::Finished(_) => result.stopped.push(entry),
                    CancelEnd::Killed(_) => result.killed.push(entry),
                    // The record of the abandoned run is read below, with the records of earlier calls.
                    CancelEnd::Abandoned(_) => {}
                }
            } else {
                live_launch = ide::launch_name_of(&run.run_id).map(str::to_owned);
                result.kept.push(entry);
            }
        }
        if let Some(abandoned) = abandoned_run(system, &context) {
            result.abandoned.push(IdeRun {
                launch_key: launch_key.clone(),
                run_id: abandoned.run_id,
            });
        }
        result
            .removed
            .extend(trim_logs(&context, args.keep_logs as usize, live_launch.as_deref()));
    }
    Ok(result)
}

/// The abandoned run that the context `context` records, while a process of its group is still there. A record whose
/// group is gone is removed, and a record that cannot be read names no run.
fn abandoned_run(system: &dyn System, context: &Path) -> Option<AbandonedRun> {
    let file = context.join(ABANDONED_FILE);
    let record: AbandonedRun = serde_json::from_slice(&fs::read(&file).ok()?).ok()?;
    if system.group_alive(record.pgid) {
        return Some(record);
    }
    let _ = fs::remove_file(&file);
    None
}

/// The context directories under `root`, by name, in order. A root that does not exist holds none.
fn context_names(root: &Path) -> Result<Vec<String>, AgentRefusal> {
    let entries = match fs::read_dir(root) {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(error) => {
            return Err(AgentRefusal::for_verb(
                AgentVerb::IdeGc,
                format!("cannot read {}: {error}", root.display()),
            ));
        }
    };
    let mut names: Vec<String> = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
        .filter_map(|entry| entry.file_name().into_string().ok())
        .filter(|name| ide::is_launch_key(name))
        .collect();
    names.sort();
    Ok(names)
}

/// Whether the live IDE of `context` must go. `--keep-product` keeps it only when its launch record names the
/// requested digest. A record that is absent, unreadable or names no digest describes an IDE of no known product.
fn should_stop(context: &Path, args: &IdeGcArgs) -> bool {
    if args.stop_all {
        return true;
    }
    let Some(keep) = &args.keep_product else {
        return false;
    };
    read_launch_record(context).is_none_or(|record| record.product_digest.is_empty() || &record.product_digest != keep)
}

fn read_launch_record(context: &Path) -> Option<LaunchRecord> {
    let raw = fs::read(context.join(LAUNCH_RECORD_FILE)).ok()?;
    ide::decode_launch_record(&raw).ok()
}

/// Removes all but the newest `keep` log directories of `context`, and the finished run directory of each removed
/// launch. The log directory of `live_launch` stays whatever its age. Answers the removed paths.
fn trim_logs(context: &Path, keep: usize, live_launch: Option<&str>) -> Vec<String> {
    let Ok(entries) = fs::read_dir(context.join(LOG_DIR)) else {
        return Vec::new();
    };
    let mut launches: Vec<(SystemTime, String, PathBuf)> = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
        .filter_map(|entry| {
            let name = entry.file_name().into_string().ok().filter(|name| ide::is_launch_name(name))?;
            let modified = entry
                .metadata()
                .and_then(|metadata| metadata.modified())
                .unwrap_or(SystemTime::UNIX_EPOCH);
            Some((modified, name, entry.path()))
        })
        .collect();
    // Newest first, and by name between two directories of the same time, so the choice is the same on each call.
    launches.sort_by(|left, right| right.0.cmp(&left.0).then_with(|| right.1.cmp(&left.1)));
    let mut removed = Vec::new();
    for (_, name, path) in launches.into_iter().skip(keep) {
        if live_launch == Some(name.as_str()) {
            continue;
        }
        if fs::remove_dir_all(&path).is_ok() {
            removed.push(path.to_string_lossy().into_owned());
        }
        let Some(run_id) = ide::ide_run_id(&name) else {
            continue;
        };
        let run_directory = context.join(run_id);
        if run_directory.is_dir() && fs::remove_dir_all(&run_directory).is_ok() {
            removed.push(run_directory.to_string_lossy().into_owned());
        }
    }
    removed
}
