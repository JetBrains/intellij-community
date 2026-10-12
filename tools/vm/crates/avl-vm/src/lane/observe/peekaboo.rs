//! `peekaboo`: the guest's Peekaboo CLI run as the logged-in Aqua user, and the artifacts it was asked to publish.

use std::path::{Path, PathBuf};
use std::time::Duration;

use crate::terminal::Output;
use crate::worker::hypervisor::unsupported;
#[cfg(unix)]
use crate::worker::parallels::parallels_current_user_argv;
use crate::worker::worker::Manager;
#[cfg(windows)]
use avl_base::Config;
use avl_base::RefusalExt;
use avl_base::{Backend, Exit, Outcome, Refusal};
use avl_host_sys::fs::resolve_pull_destination;
use avl_host_sys::{Ctx, SpawnOptions};
use serde::Serialize;
use serde_json::json;

use super::pull::pull_guest_file;
use super::with_leased_worker;

/// The timeout of a captured Peekaboo command. It captures the screen or drives the UI of the guest for seconds.
const PEEKABOO_TIMEOUT: Duration = Duration::from_mins(10);

/// The stand-in of the Parallels argv on a Windows host, which has no Parallels backend. It answers the refusal the
/// Parallels argv answers for another backend.
#[cfg(windows)]
fn parallels_current_user_argv(
    _settings: &Config,
    _worker: &str,
    _argv: &[String],
    _interactive: bool,
    _dir: Option<&str>,
) -> Result<Vec<String>, Refusal> {
    Err(unsupported("Peekaboo is currently available only on Parallels"))
}

/// What a Peekaboo command that succeeded but whose artifact publication failed exits with: `sysexits.h`'s
/// EX_IOERR, because publishing the artifact is an I/O failure. Not on [`Exit`]'s vocabulary because this is its
/// only use.
pub(crate) const EXIT_ARTIFACT_PUBLICATION_FAILED: i32 = 74;

/// `peekaboo [--interactive] [--cwd GUEST_PATH] [--publish GUEST_FILE RELATIVE_ARTIFACT]... -- <peekaboo
/// arguments...>`, for `vm` to flatten into its subcommand.
///
/// Everything before `--` is the controller's, everything after is handed to Peekaboo untouched, and the separator
/// is required so a Peekaboo flag can never be mistaken for a controller one.
#[derive(Clone, Debug, Default, PartialEq, Eq, clap::Args)]
pub(crate) struct PeekabooArgs {
    /// Runs Peekaboo on a terminal; needs the controller's --text mode.
    #[arg(long)]
    pub interactive: bool,
    /// The absolute guest directory Peekaboo runs in.
    #[arg(long, value_name = "GUEST_PATH", value_parser = absolute_guest_path)]
    pub cwd: Option<String>,
    /// Publishes one guest file into the worker's artifact directory after the command, even when it failed.
    #[arg(
        long,
        num_args = 2,
        value_names = ["GUEST_FILE", "RELATIVE_ARTIFACT"],
        value_parser = non_empty
    )]
    pub publish: Vec<String>,
    /// The Peekaboo arguments, after `--`.
    #[arg(last = true, required = true, value_name = "PEEKABOO_ARGUMENTS")]
    pub command: Vec<String>,
}

impl PeekabooArgs {
    /// The `--publish` pairs, as the caller spelled them: a guest file, and a relative artifact path that has not
    /// been resolved yet.
    fn publish_requests(&self) -> impl Iterator<Item = (&str, &str)> {
        // `num_args = 2` makes every occurrence a whole pair.
        self.publish
            .as_chunks::<2>()
            .0
            .iter()
            .map(|[source, destination]| (source.as_str(), destination.as_str()))
    }

    /// Refuses the one combination the option grammar cannot see: a terminal session inside a JSON envelope.
    fn validate(&self, output: Output) -> Result<(), Refusal> {
        if self.interactive && output == Output::Json {
            return Err(Refusal::usage("Peekaboo --interactive requires controller --text mode"));
        }
        Ok(())
    }
}

fn absolute_guest_path(value: &str) -> Result<String, String> {
    if value.starts_with('/') && !value.contains('\0') {
        Ok(value.to_owned())
    } else {
        Err("Peekaboo --cwd must be an absolute guest path".to_owned())
    }
}

fn non_empty(value: &str) -> Result<String, String> {
    if value.is_empty() {
        Err("Peekaboo --publish needs a guest file and relative artifact path".to_owned())
    } else {
        Ok(value.to_owned())
    }
}

/// Why one artifact could not be published, in the code-plus-message shape the envelope's own errors carry.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub(crate) struct ArtifactError {
    pub code: String,
    pub message: String,
}

/// What happened to one requested artifact: exactly one of `bytes` and `error` is present, and the absent one is
/// omitted rather than written as a zero.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub(crate) struct ArtifactOutcome {
    pub source: String,
    pub destination: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bytes: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<ArtifactError>,
}

/// Pulls every requested artifact, answering an outcome per request rather than failing on the first.
///
/// Per-request outcomes are the contract: the artifacts are a screenshot session's evidence, and one file that
/// cannot be published must not discard the ones that can.
async fn publish_artifacts(ctx: &Ctx, manager: &Manager, worker: &str, requests: &[(String, PathBuf)]) -> Vec<ArtifactOutcome> {
    let mut outcomes = Vec::with_capacity(requests.len());
    for (source, destination) in requests {
        let pulled = pull_guest_file(ctx, manager, worker, source, destination).await;
        outcomes.push(ArtifactOutcome {
            source: source.clone(),
            destination: destination.to_string_lossy().into_owned(),
            bytes: pulled.as_ref().ok().copied(),
            error: pulled.err().map(|refusal| ArtifactError {
                code: refusal.code.into_owned(),
                message: refusal.message,
            }),
        });
    }
    outcomes
}

/// The JSON-mode reply's captured output.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct Progress {
    stdout: String,
    stderr: String,
    stdout_truncated: bool,
    stderr_truncated: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct PeekabooData {
    worker: String,
    exit_code: i32,
    #[serde(skip_serializing_if = "Option::is_none")]
    progress: Option<Progress>,
    artifacts: Vec<ArtifactOutcome>,
}

/// Decides what a finished Peekaboo invocation answers, given its exit code and what happened to its artifacts.
///
/// The ordering is the contract:
///
/// - the command's own failure is reported first, with the artifact outcomes as evidence in its details - the exit
///   code is the primary fact and the artifacts are what was salvaged around it;
/// - an artifact failure alone - the command succeeded - is its own refusal at
///   [`EXIT_ARTIFACT_PUBLICATION_FAILED`], because "your screenshot ran and you do not have it" must not exit 0.
///
/// `progress` is `None` in text mode, where the output already went to the caller's descriptors.
fn verdict(worker: String, exit_code: i32, progress: Option<Progress>, artifacts: Vec<ArtifactOutcome>) -> Result<PeekabooData, Refusal> {
    let mut details = json!({ "exitCode": exit_code, "artifacts": artifacts });
    if let Some(progress) = &progress {
        details["progress"] = json!(progress);
    }
    if exit_code != 0 {
        return Err(Refusal::new(
            "peekaboo_command_failed",
            Exit::from_status(exit_code, Exit::FAILURE),
            format!("Peekaboo exited with {exit_code}"),
        )
        .with_details(details));
    }
    if let Some(failed) = artifacts.iter().find_map(|artifact| artifact.error.as_ref()) {
        return Err(Refusal::new(
            "peekaboo_artifact_failed",
            Exit::from_status(EXIT_ARTIFACT_PUBLICATION_FAILED, Exit::FAILURE),
            format!("Peekaboo completed, but artifact publication failed: {}", failed.message),
        )
        .with_details(details));
    }
    Ok(PeekabooData {
        worker,
        exit_code,
        progress,
        artifacts,
    })
}

/// Runs the guest's Peekaboo CLI as the logged-in Aqua user, then publishes the artifacts it was asked for.
///
/// Parallels only: running as the console user is `prlctl exec --current-user`, which Tart's exec has no
/// equivalent of, and Peekaboo's physical input needs the real TCC grants only the Parallels image carries.
///
/// The artifacts are published **even when the Peekaboo command failed** - a screenshot taken before the failure is
/// exactly the evidence the caller needs - and the verdict then reports the command's own failure first.
pub(crate) async fn command_peekaboo(
    ctx: &Ctx,
    manager: &Manager,
    args: PeekabooArgs,
    lease_file: Option<&Path>,
    output: Output,
) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    if settings.backend != Backend::Parallels {
        return Err(unsupported("Peekaboo control is currently available only on the Parallels backend"));
    }
    args.validate(output)?;
    let data = with_leased_worker(ctx, manager, lease_file, "peekaboo", async |current| {
        manager.require_ready(ctx, &current).await?;
        let worker = current.worker;
        let mut requests = Vec::new();
        for (source, destination) in args.publish_requests() {
            let resolved = resolve_pull_destination(&settings.runtime_root, &settings.worker_key(&worker), destination)?;
            requests.push((source.to_owned(), resolved));
        }
        let mut peekaboo = vec![settings.parallels_peekaboo.clone()];
        peekaboo.extend(args.command.iter().cloned());
        let line = parallels_current_user_argv(settings, &worker, &peekaboo, args.interactive, args.cwd.as_deref())?;
        if output == Output::Text {
            let exit_code = manager.runner().inherited(ctx, &line, None).await?;
            let artifacts = publish_artifacts(ctx, manager, &worker, &requests).await;
            return verdict(worker, exit_code, None, artifacts);
        }
        let options = SpawnOptions {
            allow_truncated: true,
            ..SpawnOptions::within(PEEKABOO_TIMEOUT)
        };
        let captured = manager.runner().capture(ctx, &line, &options).await?;
        let artifacts = publish_artifacts(ctx, manager, &worker, &requests).await;
        let progress = Progress {
            stdout: captured.stdout,
            stderr: captured.stderr,
            stdout_truncated: captured.stdout_truncated,
            stderr_truncated: captured.stderr_truncated,
        };
        verdict(worker, captured.exit_code, Some(progress), artifacts)
    })
    .await?;
    Outcome::data(data)
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
