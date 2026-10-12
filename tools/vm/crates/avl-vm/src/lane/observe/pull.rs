//! `pull`: one guest file copied into the worker's private artifact directory, and the primitive under it that the
//! daemon publishes run artifacts through.

use std::fs::File;
use std::io::{self, BufReader, Read, Write};
use std::path::Path;
use std::time::Duration;

use crate::worker::worker::Manager;
use avl_base::format::words;
use avl_base::fs::{PublishError, private_temporary, publish};
use avl_base::{Backend, Config, Exit, OrRefuse, Outcome, Refusal};
use avl_host_sys::fs::resolve_pull_destination;
use avl_host_sys::guest::{OLDER_AGENT_HINT, user_argv};
use avl_host_sys::private::restrict_file;
use avl_host_sys::{Ctx, ProcError, SpawnOptions};
use avl_wire::pull::{FileReceipt, ReceiptHasher};
use avl_wire::supervisor::{AgentExit, Envelope};
use avl_wire::verb::AgentVerb;
use base64::engine::general_purpose::STANDARD;
use base64::read::DecoderReader;
use serde::Serialize;
use serde_json::json;
use tempfile::{NamedTempFile, TempPath};

use super::with_leased_worker;
use avl_base::RefusalExt;

/// The timeout of the guest read of one pulled file. A trace zip can hold hundreds of MiB, and it crosses the exec
/// channel.
const PULL_TIMEOUT: Duration = Duration::from_mins(30);

/// The most bytes one read of a pulled file takes before they go on.
const CHUNK: usize = 64 * 1024;

/// How one guest file crosses the exec channel of a backend.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Transfer {
    /// The bytes unchanged, through the agent's `read-file`, which names them in a [`FileReceipt`] on stderr. The Tart
    /// and Docker exec channels carry bytes unchanged, which ADR 0182 and ADR 0183 measured.
    Raw,
    /// The guest's `/usr/bin/base64 -i`, decoded here. No measurement proved the `prlctl exec` channel byte-clean, so
    /// Parallels keeps the text encoding.
    Base64,
}

impl Transfer {
    pub(crate) const fn of(backend: Backend) -> Self {
        match backend {
            Backend::Tart | Backend::Docker => Self::Raw,
            Backend::Parallels => Self::Base64,
        }
    }

    /// The guest argv that reads `source`, which the worker-user prefix then wraps.
    fn read_argv(self, settings: &Config, source: &str) -> Vec<String> {
        match self {
            Self::Raw => words([settings.vm_agent.as_str(), AgentVerb::ReadFile.as_str(), source]),
            Self::Base64 => words(["/usr/bin/base64", "-i", source]),
        }
    }
}

/// Copies one guest file to a host destination, as a fail-closed atomic publish, and answers its size in bytes.
///
/// The destination must come from [`resolve_pull_destination`]; what this function adds is that the bytes land
/// there whole or not at all, with nothing following a symlink on the way:
///
/// - the guest reads the file as the worker user, the account whose runs produced it, in the [`Transfer`] of the
///   backend. Its stdout streams into a `.pull-<pid>-<uuid>.part` sibling that the runner creates exclusively at mode
///   0600, so two pulls cannot share a temporary and a planted file cannot be written through. Streamed to a file
///   rather than captured, because an artifact can be far larger than the capture limit and a truncated pull is worse
///   than a refused one;
/// - the bytes land in a private `.pull-*.tmp` sibling and [`publish`] moves it into place, a no-clobber move that
///   checks the entry is still the file it wrote. A raw pull counts and hashes them on the way and refuses
///   `pull_digest_mismatch` at [`Exit::DATA_ERR`] when they are not what the guest's receipt names: that check is
///   the proof of a clean transfer. A base64 pull decodes them, and a stream that does not decode is `pull_failed`;
/// - an existing destination is `pull_destination_exists` at [`Exit::CANT_CREATE`], and every other way the publish
///   can fail is `pull_destination_unsafe` at [`Exit::NO_PERM`], carrying its cause: without it a planted symlink and
///   a full disk read identically.
///
/// Both temporaries are removed on every path, so a refusal on any step leaves nothing behind.
///
/// The base64 decode is in-process rather than a `/usr/bin/base64 -D`, whose `-D` is BSD-only. A wrapping encoder's
/// `\r` and `\n` are stripped before the decoder sees them, which is what BSD `base64 -D` does with them too.
pub(crate) async fn pull_guest_file(ctx: &Ctx, manager: &Manager, worker: &str, source: &str, destination: &Path) -> Result<u64, Refusal> {
    let directory = match destination.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent,
        _ => Path::new("."),
    };
    // Cosmetic uniqueness: what enforces it is the exclusive create. Removed when dropped, whether or not anything
    // was ever created there.
    let streamed = TempPath::try_from_path(directory.join(format!(".pull-{}-{}.part", std::process::id(), avl_base::new_id()))).or_refuse(
        "state_write_failed",
        Exit::FAILURE,
        || format!("cannot name a temporary beside {}", destination.display()),
    )?;

    let settings = manager.settings();
    let transfer = Transfer::of(settings.backend);
    let line = manager
        .machine()
        .guest_argv(ctx, worker, &user_argv(settings, &transfer.read_argv(settings, source)), false)
        .await?;
    let said = match manager
        .runner()
        .checked_to_file(ctx, &line, &streamed, &SpawnOptions::within(PULL_TIMEOUT))
        .await
    {
        Ok(said) => said,
        // Guest output stays out of refusals (see the module comment).
        Err(ProcError::Exited { exit_code, refusal }) => {
            let older = if transfer == Transfer::Raw && exit_code == AgentExit::Usage.code() {
                format!("; {OLDER_AGENT_HINT}")
            } else {
                String::new()
            };
            return Err(pull_failed(format!(
                "guest exec exited {} reading {source} from {worker}{older}; guest output was withheld",
                refusal.exit
            )));
        }
        Err(other) => return Err(other.into()),
    };
    let received = match transfer {
        Transfer::Raw => Received::Raw(read_receipt(&said).ok_or_else(|| {
            pull_failed(format!(
                "the guest named no receipt for {source} from {worker}; guest output was withheld"
            ))
        })?),
        Transfer::Base64 => Received::Base64,
    };

    // The copy and the publish are blocking file work over what can be a large artifact or trace zip, so they run
    // off the async workers. `streamed` stays owned here and is removed once the blocking step is done.
    let job = {
        let streamed = streamed.to_path_buf();
        let directory = directory.to_path_buf();
        let source = source.to_owned();
        let destination = destination.to_path_buf();
        tokio::task::spawn_blocking(move || copy_and_publish(&streamed, &directory, &source, &destination, &received))
    };
    match job.await {
        Ok(result) => result,
        Err(error) if error.is_panic() => std::panic::resume_unwind(error.into_panic()),
        Err(error) => Err(pull_failed(format!("the copy of {source} did not finish: {error}"))),
    }
}

/// What the guest's stdout is, which decides how its bytes become the published file.
enum Received {
    /// The file itself, which the receipt names.
    Raw(FileReceipt),
    /// The file in base64.
    Base64,
}

/// The receipt of a `read-file`: the success envelope on the last line of its stderr. Lines before it are not the
/// agent's, a warning of `sudo` for one.
fn read_receipt(said: &str) -> Option<FileReceipt> {
    let line = said.lines().rev().find(|line| !line.trim().is_empty())?;
    let envelope: Envelope<FileReceipt> = serde_json::from_str(line).ok()?;
    if envelope.command != AgentVerb::ReadFile.as_str() {
        return None;
    }
    envelope.into_result().ok()
}

/// Turns the guest's stdout into a private temporary beside the destination, publishes it at `destination`, and
/// answers its size.
fn copy_and_publish(streamed: &Path, directory: &Path, source: &str, destination: &Path, received: &Received) -> Result<u64, Refusal> {
    let input = File::open(streamed)
        .map_err(|error| pull_failed(format!("cannot read the pulled {} at {}: {error}", source, streamed.display())))?;
    let mut output = private_temporary(directory, "pull")
        .map_err(|error| pull_failed(format!("cannot create the copy destination in {}: {error}", directory.display())))?;
    match received {
        Received::Raw(receipt) => copy_verified(input, &mut output, source, receipt)?,
        Received::Base64 => {
            let mut decoder = DecoderReader::new(WithoutLineBreaks(BufReader::new(input)), &STANDARD);
            io::copy(&mut decoder, &mut output)
                .map_err(|error| pull_failed(format!("the guest's base64 for {source} did not decode: {error}")))?;
        }
    }
    // Private again after the write: a creation mode is masked by `umask`, and on Windows the temporary has the
    // access list its directory passed down.
    restrict_file(output.path()).map_err(|error| pull_failed(format!("cannot set the mode of {}: {error}", output.path().display())))?;
    match publish(output, destination) {
        Ok(()) => {}
        Err(PublishError::DestinationExists(_)) => {
            return Err(Refusal::new(
                "pull_destination_exists",
                Exit::CANT_CREATE,
                format!("refusing to overwrite an existing artifact: {}", destination.display()),
            ));
        }
        Err(error) => {
            return Err(Refusal::new(
                "pull_destination_unsafe",
                Exit::NO_PERM,
                format!("could not atomically publish artifact: {}", destination.display()),
            )
            .with_details(json!({ "cause": error.to_string() })));
        }
    }
    let published = std::fs::metadata(destination)
        .map_err(|error| pull_failed(format!("cannot stat the published artifact {}: {error}", destination.display())))?;
    Ok(published.len())
}

/// Copies the raw bytes into `output`, and refuses them when they are not the bytes that `receipt` names.
fn copy_verified(mut input: File, output: &mut NamedTempFile, source: &str, receipt: &FileReceipt) -> Result<(), Refusal> {
    let mut hasher = ReceiptHasher::default();
    let mut buffer = vec![0; CHUNK];
    loop {
        let read = input
            .read(&mut buffer)
            .map_err(|error| pull_failed(format!("cannot read the pulled bytes of {source}: {error}")))?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
        output
            .write_all(&buffer[..read])
            .map_err(|error| pull_failed(format!("cannot copy the pulled bytes of {source}: {error}")))?;
    }
    let arrived = hasher.finish();
    if arrived != *receipt {
        return Err(Refusal::new(
            "pull_digest_mismatch",
            Exit::DATA_ERR,
            format!(
                "the bytes of {source} that arrived ({} bytes, sha256 {}) are not the bytes the guest sent ({} bytes, sha256 {})",
                arrived.bytes, arrived.sha256, receipt.bytes, receipt.sha256
            ),
        ));
    }
    Ok(())
}

/// A reader that drops `\r` and `\n`: the `base64` crate's engines refuse whitespace, and both BSD and GNU encoders
/// wrap their output, so a wrapped encoding decodes to the same bytes an unwrapped one does only once the line
/// breaks are gone.
struct WithoutLineBreaks<R>(R);

impl<R: Read> Read for WithoutLineBreaks<R> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        loop {
            let read = self.0.read(buffer)?;
            if read == 0 {
                return Ok(0);
            }
            let mut kept = 0;
            for index in 0..read {
                let byte = buffer[index];
                if byte != b'\r' && byte != b'\n' {
                    buffer[kept] = byte;
                    kept += 1;
                }
            }
            // A read that was nothing but line breaks is not the end of the stream.
            if kept > 0 {
                return Ok(kept);
            }
        }
    }
}

fn pull_failed(message: String) -> Refusal {
    Refusal::new("pull_failed", Exit::FAILURE, message)
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct PullData {
    worker: String,
    source: String,
    destination: String,
    bytes: u64,
}

/// Copies one guest file into the worker's private artifact directory, at the relative path the caller chose.
///
/// Deliberately allowed while a run is active, like `ls` and unlike `exec`: one fixed read-only argv over the
/// non-GUI channel cannot take the pointer or the focus a UI run depends on, and pulling a failing lane's artifacts
/// is needed exactly while the daemon holds its run slot.
pub(crate) async fn command_pull(
    ctx: &Ctx,
    manager: &Manager,
    source: String,
    destination: String,
    lease_file: Option<&Path>,
) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    let data = with_leased_worker(ctx, manager, lease_file, "pull", async |current| {
        // Resolved before the readiness gate: a destination that would be refused must not cost a worker boot first.
        let resolved = resolve_pull_destination(&settings.runtime_root, &settings.worker_key(&current.worker), &destination)?;
        manager.require_ready(ctx, &current).await?;
        let bytes = pull_guest_file(ctx, manager, &current.worker, &source, &resolved).await?;
        Ok(PullData {
            worker: current.worker,
            source: source.clone(),
            destination: resolved.to_string_lossy().into_owned(),
            bytes,
        })
    })
    .await?;
    let text = data.destination.clone();
    Outcome::new(data, text)
}

// The suite runs on the observe fixture, a Tart pool over the fake `tart`, which is Unix only.
#[cfg(test)]
#[cfg(unix)]
mod tests;
