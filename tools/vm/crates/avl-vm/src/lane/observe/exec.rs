//! `exec`: one arbitrary command in the leased worker, as the worker user.

use std::path::Path;
use std::time::Duration;

use crate::terminal::Output;
use crate::worker::worker::Manager;
use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::guest::{Guest, user_argv};
use avl_host_sys::{Ctx, SpawnOptions};
use serde::Serialize;
use serde_json::json;

use super::with_leased_worker;
use avl_base::RefusalExt;

/// The timeout of a captured `exec`. An agent runs a diagnosis command there, and an hour is far above one. The bound
/// stops a command that never ends, which would hold the lease for ever.
const EXEC_TIMEOUT: Duration = Duration::from_hours(1);

/// What `exec` answers: the exit code alone in text mode, where the output already went to the caller's own
/// descriptors, and the captured output beside it in JSON mode.
#[derive(Debug, Serialize)]
#[serde(untagged)]
enum ExecData {
    Inherited(InheritedData),
    Captured(CapturedData),
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct InheritedData {
    worker: String,
    exit_code: i32,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct CapturedData {
    worker: String,
    exit_code: i32,
    stdout: String,
    stderr: String,
    stdout_truncated: bool,
    stderr_truncated: bool,
}

/// Runs one arbitrary command in the leased worker, as the worker user.
///
/// Refused while a run holds the supervisor's slot, because an arbitrary command in the guest can take the pointer
/// or the focus a UI run depends on. `json` selects between capturing the output for the envelope and inheriting
/// the controller's own descriptors - the latter is what an interactive guest shell needs. A leading `--` in
/// `argv` is the separator a caller may spell, not part of the command.
pub(crate) async fn command_exec(
    ctx: &Ctx,
    manager: &Manager,
    argv: Vec<String>,
    lease_file: Option<&Path>,
    output: Output,
) -> Result<Outcome, Refusal> {
    let command = match argv.split_first() {
        Some((separator, rest)) if separator == "--" => rest,
        _ => argv.as_slice(),
    };
    if command.is_empty() {
        return Err(Refusal::usage(format!(
            "usage: {} exec -- <command...>",
            manager.reporter().program()
        )));
    }
    let settings = manager.settings();
    let data = with_leased_worker(ctx, manager, lease_file, "exec", async |current| {
        manager.require_ready(ctx, &current).await?;
        let worker = current.worker;
        let channel = manager.channel(&worker);
        let guest = Guest {
            ctx,
            settings,
            channel: channel.as_ref(),
            reporter: manager.reporter(),
        };
        guest.install_agent(manager.bazel()).await?;
        guest.reject_active_run("execute an arbitrary guest command").await?;
        let as_user = user_argv(settings, command);
        if output == Output::Text {
            let line = manager.machine().guest_argv(ctx, &worker, &as_user, false).await?;
            let exit_code = manager.runner().inherited(ctx, &line, None).await?;
            if exit_code != 0 {
                return Err(guest_command_failed(exit_code, format!("guest command exited with {exit_code}")));
            }
            return Ok(ExecData::Inherited(InheritedData { worker, exit_code }));
        }
        let options = SpawnOptions {
            allow_truncated: true,
            ..SpawnOptions::within(EXEC_TIMEOUT)
        };
        let captured = channel.exec(ctx, &as_user, &options).await?;
        if captured.exit_code != 0 {
            // What the guest printed stays out of the refusal; see the module comment.
            return Err(guest_command_failed(
                captured.exit_code,
                format!("guest command exited with {}; subprocess output was withheld", captured.exit_code),
            ));
        }
        Ok(ExecData::Captured(CapturedData {
            worker,
            exit_code: captured.exit_code,
            stdout: captured.stdout,
            stderr: captured.stderr,
            stdout_truncated: captured.stdout_truncated,
            stderr_truncated: captured.stderr_truncated,
        }))
    })
    .await?;
    Outcome::data(data)
}

/// The guest command's own failure, at the guest command's own exit status.
fn guest_command_failed(exit_code: i32, message: String) -> Refusal {
    Refusal::new("guest_command_failed", Exit::from_status(exit_code, Exit::FAILURE), message)
        .with_details(json!({ "exitCode": exit_code }))
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
