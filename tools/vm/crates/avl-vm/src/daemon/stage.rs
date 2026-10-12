//! The daemon's stable runtime generation on the guest: staged once, proved cheaply afterwards.

use std::path::PathBuf;
use std::time::Duration;

use avl_base::format::words;
use avl_base::fs::write_atomically;
use avl_base::{Config, Exit, OrRefuse, Refusal, SCHEMA_VERSION, Scope};
use avl_host_sys::SpawnOptions;
use avl_host_sys::guest::{AgentAccount, GUEST_COMMAND_TIMEOUT, Guest, guest_join};
use avl_report::digest;
use avl_wire::stage::{self as wire, RuntimeManifest, RuntimeResult, RuntimeReuse};
use avl_wire::verb::AgentVerb;
use serde::{Deserialize, Serialize};

use crate::daemon::build::PreparedBuild;
use crate::daemon::host::Host;
use crate::daemon::state::guest_state_dir;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// Where one generation ended up on the guest, and whether staging had to do anything.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct GuestRuntime {
    pub(crate) root: String,
    pub(crate) java_binary: String,
    pub(crate) classpath: Vec<String>,
    pub(crate) classpath_file: String,
    pub(crate) reused: bool,
}

/// What one worker's last staged runtime generation was, as this controller was told it.
///
/// Exists so that reusing a generation costs no bytes on the exec channel. The stager's reply is a thousand absolute
/// guest paths and its manifest a thousand absolute host ones, and pushing and pulling those to be told `reused:
/// true` was 16.1 s of a 95.7 s warm restart. Remembering the answer turns the question into a digest
/// ([`wire::classpath_file_text`]) the guest holds against its own `classpath.txt`.
///
/// Per worker rather than per pool: a generation is staged on a worker's own disk, and another worker's having it
/// says nothing.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct StagedRuntimeReceipt {
    pub schema_version: u32,
    pub worker: String,
    pub runtime_digest: String,
    pub root: String,
    pub java_binary: String,
    pub classpath_file: String,
    pub classpath: Vec<String>,
}

/// Where one worker's staged-runtime receipt lives on the host.
pub(crate) fn staged_runtime_receipt_path(settings: &Config, worker: &str) -> PathBuf {
    settings.worker_dir(worker).join("runtime-stage.json")
}

/// The receipt for this worker, or `None` on *any* mismatch.
///
/// `None`, never an error: every way of not trusting the receipt - another schema, another worker, a digest that is
/// not 64-hex, a classpath that is not a string list, a missing path - falls through to a full stage, which is what
/// happened unconditionally before the receipt existed. A refusal here could only turn a slow path into a broken
/// one.
pub(crate) fn read_staged_runtime_receipt(settings: &Config, worker: &str) -> Option<StagedRuntimeReceipt> {
    let content = std::fs::read(staged_runtime_receipt_path(settings, worker)).ok()?;
    let receipt: StagedRuntimeReceipt = serde_json::from_slice(&content).ok()?;
    (receipt.schema_version == SCHEMA_VERSION && receipt.worker == worker && wire::is_sha256_hex(&receipt.runtime_digest))
        .then_some(receipt)
}

/// Refuses a staged reply whose paths lie outside the generation that was asked for. The reply comes back from the
/// guest, and a stage that answered another tree's paths would launch a JVM over bytes this build never named.
fn require_stage_within_generation(staged: &GuestRuntime, expected_root: &str, expected_count: usize) -> Result<(), Refusal> {
    let inside = |path: &str| path.strip_prefix(expected_root).is_some_and(|rest| rest.starts_with('/'));
    let within = staged.root == expected_root
        && staged.classpath.len() == expected_count
        && inside(&staged.java_binary)
        && inside(&staged.classpath_file)
        && staged.classpath.iter().all(|path| inside(path));
    if within {
        return Ok(());
    }
    Err(Refusal::new(
        "guest_runtime_stage_failed",
        Exit::SOFTWARE,
        "runtime staging returned paths outside its generation",
    ))
}

/// The generation directory one runtime digest is staged into.
fn generation_root(settings: &Config, runtime_digest: &str) -> String {
    wire::generation_dir(&settings.guest_runtime_root(), runtime_digest)
}

/// The timeout of `stage-check`. It reads the marker of one generation and stats each staged jar on the guest's own
/// disk, so two minutes covers a guest under load.
const STAGE_CHECK_TIMEOUT: Duration = Duration::from_secs(120);
/// The timeout of `stage`. A cold stage copies every jar of the classpath and extracts the JBR through the Bazel
/// share.
const STAGE_TIMEOUT: Duration = Duration::from_mins(30);
/// The timeout of `gc`. It removes whole staged generations, and each holds a classpath and a JBR.
const GC_TIMEOUT: Duration = Duration::from_mins(10);
const STAGE_TIMEOUT_CODE: &str = "guest_runtime_stage_timeout";

impl Host {
    /// The generation this worker already holds, or `None` - asked and answered in one small guest call.
    ///
    /// Only asked when a receipt names this exact generation, because the receipt's classpath is what the question
    /// is made of. The guest still makes every check it made when it was handed a manifest: the marker's schema,
    /// digest and count, one stat per staged jar, the JBR binary. Those are local stats on the guest's own disk and
    /// were never the cost; the cost was the list, in both directions.
    ///
    /// A probe that answers "not complete" - a collected generation, a partial tree, a `classpath.txt` that does not
    /// match what this controller remembers - simply falls through to a full stage.
    async fn reuse_staged_runtime(&self, guest: &Guest<'_>, prep: &PreparedBuild) -> Result<Option<GuestRuntime>, Refusal> {
        let worker = guest.worker();
        let Some(remembered) = read_staged_runtime_receipt(&self.settings, worker).filter(|receipt| {
            receipt.runtime_digest == prep.runtime_digest && receipt.classpath.len() == prep.descriptor.classpath.stable.len()
        }) else {
            return Ok(None);
        };
        let arguments = [
            self.settings.guest_runtime_root(),
            prep.runtime_digest.clone(),
            remembered.classpath.len().to_string(),
            prep.descriptor.jbr.java_home_suffix.clone(),
            digest::sha256_text(&wire::classpath_file_text(&remembered.classpath)),
        ];
        let answered = guest
            .invoke_agent(
                AgentAccount::Worker,
                AgentVerb::StageCheck,
                &arguments,
                &SpawnOptions::timeout(STAGE_CHECK_TIMEOUT, STAGE_TIMEOUT_CODE),
            )
            .await;
        let stdout = match answered {
            Ok(stdout) => stdout,
            Err(refusal) => {
                // An agent too old to know the verb, or a guest that could not answer. Staging is the fallback for
                // every one of those, and it is what used to happen unconditionally; which one it was is in the
                // note, because the agent refusal names the agent and its exit code.
                self.reporter.note(
                    format!(
                        "could not ask {worker} whether it still holds the runtime generation: {}",
                        refusal.message
                    ),
                    Some(&Scope::worker(worker)),
                );
                return Ok(None);
            }
        };
        let answer: RuntimeReuse = serde_json::from_str(&stdout).map_err(|error| {
            Refusal::new(
                "guest_runtime_stage_failed",
                Exit::SOFTWARE,
                format!("runtime reuse check returned invalid JSON: {stdout} ({error})"),
            )
        })?;
        if !answer.complete {
            return Ok(None);
        }
        let (Some(root), Some(java_binary), Some(classpath_file)) = (answer.root, answer.java_binary, answer.classpath_file) else {
            return Err(Refusal::new(
                "guest_runtime_stage_failed",
                Exit::SOFTWARE,
                "runtime reuse check answered without its paths",
            ));
        };
        let staged = GuestRuntime {
            root,
            java_binary,
            classpath: remembered.classpath,
            classpath_file,
            reused: true,
        };
        require_stage_within_generation(
            &staged,
            &generation_root(&self.settings, &prep.runtime_digest),
            staged.classpath.len(),
        )?;
        Ok(Some(staged))
    }

    /// Materializes (or proves) the build's stable generation on the guest, and remembers the answer.
    pub(crate) async fn ensure_guest_runtime(&self, guest: &Guest<'_>, prep: &PreparedBuild) -> Result<GuestRuntime, Refusal> {
        let worker = guest.worker();
        self.require_guest_free_space(guest, Some(&Scope::worker(worker))).await?;
        if let Some(reused) = self.reuse_staged_runtime(guest, prep).await? {
            return Ok(reused);
        }

        let state_dir = guest_state_dir(&self.settings);
        // The stager is a verb of the guest agent the start already installed, and its manifest goes on the verb's
        // stdin, so nothing is pushed here. A manifest file cost one more guest exec, 0.65 s on Tart. The mkdir keeps
        // the daemon's directory and `<vmData>/state`, where that agent lives: a worker whose state directory is
        // missing is one nothing else here can reach either.
        let agent_state = guest_join(&self.settings.vm_data, "state");
        guest
            .as_user(
                &words(["/bin/mkdir", "-p", &state_dir, &agent_state]),
                &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
            )
            .await?;
        let manifest = RuntimeManifest {
            schema_version: wire::SCHEMA_VERSION,
            runtime_digest: prep.runtime_digest.clone(),
            // Guest paths: the stager reads each source through the guest runfiles root.
            stable_sources: prep
                .descriptor
                .classpath
                .stable
                .iter()
                .map(|file| guest_join(&prep.guest_runfiles_root, &file.logical_path))
                .collect(),
            jbr_archive: guest_join(&prep.guest_runfiles_root, &prep.descriptor.jbr.archive.logical_path),
            java_home_suffix: prep.descriptor.jbr.java_home_suffix.clone(),
        };
        self.reporter.note(
            format!("staging daemon runtime {} in {worker}", short(&prep.runtime_digest)),
            Some(&Scope::worker(worker)),
        );
        let options = SpawnOptions {
            stdin: Some(json_line(&manifest)?),
            ..SpawnOptions::timeout(STAGE_TIMEOUT, STAGE_TIMEOUT_CODE)
        };
        let stdout = guest
            .invoke_agent(
                AgentAccount::Worker,
                AgentVerb::Stage,
                &[self.settings.guest_runtime_root()],
                &options,
            )
            .await?;
        let result: RuntimeResult = serde_json::from_str(&stdout).map_err(|error| {
            Refusal::new(
                "guest_runtime_stage_failed",
                Exit::SOFTWARE,
                format!("runtime staging returned invalid JSON: {stdout} ({error})"),
            )
        })?;
        let staged = GuestRuntime {
            root: result.root,
            java_binary: result.java_binary,
            classpath: result.classpath,
            classpath_file: result.classpath_file,
            reused: result.reused,
        };
        require_stage_within_generation(
            &staged,
            &generation_root(&self.settings, &prep.runtime_digest),
            prep.descriptor.classpath.stable.len(),
        )?;
        let receipt = StagedRuntimeReceipt {
            schema_version: SCHEMA_VERSION,
            worker: worker.to_owned(),
            runtime_digest: prep.runtime_digest.clone(),
            root: staged.root.clone(),
            java_binary: staged.java_binary.clone(),
            classpath_file: staged.classpath_file.clone(),
            classpath: staged.classpath.clone(),
        };
        write_atomically(&staged_runtime_receipt_path(&self.settings, worker), &json_line(&receipt)?, 0o600)?;
        Ok(staged)
    }

    /// Retires every staged generation but the ones still worth holding, and swallows its own failure: a collection
    /// that could not run is a disk that stays fuller, not a start that failed.
    pub(crate) async fn gc_guest_runtimes(&self, guest: &Guest<'_>, keep: &[String]) {
        let mut arguments = vec![self.settings.guest_runtime_root()];
        arguments.extend_from_slice(keep);
        if let Err(refusal) = guest
            .invoke_agent(AgentAccount::Worker, AgentVerb::Gc, &arguments, &SpawnOptions::within(GC_TIMEOUT))
            .await
        {
            self.reporter.note(
                format!("could not garbage-collect old daemon runtimes: {}", refusal.message),
                Some(&Scope::worker(guest.worker())),
            );
        }
    }
}

/// A document as one compact JSON line, the shape every guest-bound file and every receipt here is written in.
pub(crate) fn json_line(value: &impl Serialize) -> Result<Vec<u8>, Refusal> {
    let mut encoded = serde_json::to_vec(value).or_refuse("internal_error", Exit::FAILURE, || "cannot encode a document".to_owned())?;
    encoded.push(b'\n');
    Ok(encoded)
}

/// The first twelve characters of a digest, the way notes name a generation.
pub(crate) fn short(digest: &str) -> &str {
    digest.get(..12).unwrap_or(digest)
}
