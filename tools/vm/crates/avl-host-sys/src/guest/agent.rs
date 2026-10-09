//! Putting the guest agent into a worker, and knowing when it is already there.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{LazyLock, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use avl_base::format::words;
use avl_base::fs::write_atomically_if_changed;
use avl_base::sync::lock;
use avl_base::{Config, Exit, GuestArch, GuestOs, OrRefuse, Refusal, Reporter, Scope};
use avl_wire::supervisor::SCHEMA_VERSION;
use avl_wire::verb::AgentVerb;
use regex::Regex;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::supervisor::AgentAccount;
use super::{GUEST_COMMAND_TIMEOUT, Guest, path_text};
use crate::ctx::Ctx;
use crate::proc::SpawnOptions;

#[cfg(test)]
mod tests;

/// The timeout of the `contract` verb, which prints the digest of the agent itself: a read of its own binary.
const CONTRACT_TIMEOUT: Duration = Duration::from_mins(1);

/// What this crate needs of the host's Bazel: two questions, neither of which builds anything.
///
/// A trait and not a dependency on the lane's Bazel helpers, for two reasons that point the same way. The
/// controller-wide Bazel argv - the `--config` flags a `cquery` must repeat or it discards the analysis of every
/// configured target - belongs to whoever owns the lane's build, not to the guest; and a test of the install path
/// must never spawn Bazel, which on a cold server is 24 s and on a contended host is worse.
#[async_trait]
pub trait BazelHost: Send + Sync {
    /// Runs one non-building Bazel command in the host checkout and answers its stdout.
    async fn query(&self, ctx: &Ctx, command: &str, args: &[String]) -> Result<String, Refusal>;

    /// The directory every path `cquery` prints is relative to. Cached by the implementation: it cannot move under
    /// a running controller, because a different output base means a different server than the one this process
    /// has been talking to.
    async fn execution_root(&self, ctx: &Ctx) -> Result<PathBuf, Refusal>;
}

/// The target that builds the guest agent for one guest.
///
/// Keyed by the *guest* OS and architecture and not by the host's: the binary runs inside the VM, so a controller
/// on an Apple-silicon host stages a Linux worker with the linux-arm64 build and a macOS worker with the darwin-arm64
/// one, and a controller on an x86_64 host stages its Docker worker with the linux-x86_64 build. All are
/// statically linked, which is what lets the guest run them with no interpreter and no shared library the image has
/// to provide.
///
/// Public for the lane, which adds it to the host build's target set so that the bytes installed here were built
/// from this checkout's sources. The direction is deliberate: the guest side knows *what* gets installed, the lane
/// knows *how* the host builds, so the label is declared here - beside the install that reads it - and the build
/// asks for it. The reverse dependency is the one [`BazelHost`] exists to prevent.
pub const fn agent_label(guest_os: GuestOs, guest_arch: GuestArch) -> &'static str {
    match (guest_os, guest_arch) {
        // A macOS guest is an Apple-silicon VM whatever the host; Tart runs no other.
        (GuestOs::Macos, _) => "@community//tools/vm:vm-guest-agent-darwin-arm64",
        (GuestOs::Linux, GuestArch::Arm64) => "@community//tools/vm:vm-guest-agent-linux-arm64",
        (GuestOs::Linux, GuestArch::X86_64) => "@community//tools/vm:vm-guest-agent-linux-x86_64",
    }
}

/// The `cquery` argv that answers the one file a target produces, read out of its own `DefaultInfo`:
/// `--output=files` answers nothing for a target that reaches its output through a transition.
pub(super) fn cquery_output_file(label: &str) -> Vec<String> {
    words([
        "--output=starlark",
        "--starlark:expr=[f.path for f in target.files.to_list()][0]",
        label,
    ])
}

/// The last non-empty line of a query's stdout. The last and not the first, so that anything Bazel decides to
/// print ahead of the answer cannot be mistaken for it.
pub(super) fn last_non_empty_line(text: &str) -> Option<&str> {
    text.lines().map(str::trim).rfind(|line| !line.is_empty())
}

/// A process-wide memo of host paths already resolved, keyed by checkout and label.
///
/// The two Bazel queries behind one answer cost about 5 s against a warm server and 24 s against a busy one, and
/// an install happens once per lease operation, once per observation and again on every daemon start - while the
/// answer cannot change under a running controller, because nothing here builds it. Process-wide, unlike the
/// phase timeline, because this is an idempotent answer keyed by everything that can change it: two tasks racing
/// to compute it agree.
pub(super) struct ResolvedPaths(LazyLock<Mutex<HashMap<(PathBuf, String), PathBuf>>>);

impl ResolvedPaths {
    pub(super) const fn new() -> Self {
        Self(LazyLock::new(Mutex::default))
    }

    pub(super) fn get(&self, repo: &Path, label: &str) -> Option<PathBuf> {
        let map = lock(&self.0);
        map.get(&(repo.to_owned(), label.to_owned())).cloned()
    }

    pub(super) fn remember(&self, repo: &Path, label: &str, path: &Path) {
        let mut map = lock(&self.0);
        map.insert((repo.to_owned(), label.to_owned()), path.to_owned());
    }
}

static RESOLVED_AGENT_BINARIES: ResolvedPaths = ResolvedPaths::new();

/// What this controller last installed into one worker, and where those bytes came from.
///
/// The point of recording it is the *source signature*: it lets the guest's answer be compared against the host
/// binary without resolving the host binary, which is the expensive half of an install. A digest alone would not
/// do - it says what was installed, not whether the host still holds those bytes.
///
/// Every field is required: a receipt missing one does not read, which counts as no receipt and means an install.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AgentReceipt {
    pub schema_version: u32,
    pub worker: String,
    pub guest_os: String,
    /// The checkout whose controller installed those bytes.
    ///
    /// The pool is per machine and not per checkout, so two working copies write this same file for the same
    /// worker - and a source path plus its stat identity does not distinguish them. Measured on air-linux-2 on
    /// 2026-08-24: a receipt written from one checkout named that output base's binary, which was still
    /// byte-identical and stat-identical, so it validated under a controller built from another; the guest agreed
    /// with the receipt, the install was skipped, and the newer controller drove the older agent until it asked
    /// for a verb that agent did not have (`launch-prep`, usage, exit 64).
    pub host_repo: String,
    /// The host path the installed bytes were read from.
    pub source: String,
    pub source_signature: String,
    /// The digest of the bytes that were installed, which is what the guest reports about itself.
    pub sha256: String,
}

/// Where one worker's agent receipt lives on the host.
pub fn agent_receipt_path(settings: &Config, worker: &str) -> PathBuf {
    settings.worker_dir(worker).join("guest-agent.json")
}

/// The stat identity of one host file, `ino:size:mtimeMs:ctimeMs`, or the empty string when there is no file to
/// identify.
///
/// Empty is deliberately never equal to a recorded signature, so a host binary that has been deleted - a `bazel
/// clean`, an output base that moved - invalidates the receipt rather than matching it.
///
/// Follows a symlink, and has to: what `cquery` answers for a transitioned binary is a *link* into the transitioned
/// configuration's output directory, so an `lstat` describes the link and never the bytes. An `lstat`-based check
/// silently degraded to "always reinstall", and that bug reached hardware after passing its whole suite. A link
/// repointed at another file still invalidates the receipt, because the identity is the target's inode.
///
/// Bazel writes an output by creating a new file, so a rebuilt binary cannot keep that tuple; the ctime is in it
/// because an mtime can be preserved across a rebuild while a ctime cannot.
///
/// On Windows the identity is the volume serial and the 128-bit file id in hex, which have the part of the inode.
/// The two times are `LastWriteTime` and `ChangeTime`. `ChangeTime` is the metadata change time, which a copied
/// mtime cannot keep either.
pub fn host_file_signature(path: &Path) -> String {
    match std::fs::metadata(path) {
        Ok(metadata) if metadata.is_file() => file_identity(path, &metadata).map_or_else(String::new, |identity| {
            format!(
                "{}:{}:{}:{}",
                identity.id,
                metadata.len(),
                identity.modified_ms,
                identity.changed_ms,
            )
        }),
        _ => String::new(),
    }
}

/// What [`host_file_signature`] records beyond the size.
struct FileIdentity {
    id: String,
    modified_ms: i64,
    changed_ms: i64,
}

#[cfg(unix)]
#[expect(
    clippy::unnecessary_wraps,
    reason = "the signature of the Windows file_identity, which can fail after the metadata did not"
)]
fn file_identity(_path: &Path, metadata: &std::fs::Metadata) -> Option<FileIdentity> {
    use std::os::unix::fs::MetadataExt;
    Some(FileIdentity {
        id: metadata.ino().to_string(),
        modified_ms: metadata.mtime() * 1000 + metadata.mtime_nsec() / 1_000_000,
        changed_ms: metadata.ctime() * 1000 + metadata.ctime_nsec() / 1_000_000,
    })
}

/// The file id from `FileIdInfo`, and the two times from `FileBasicInfo`, both of one handle.
#[cfg(windows)]
fn file_identity(path: &Path, _metadata: &std::fs::Metadata) -> Option<FileIdentity> {
    use std::os::windows::io::AsRawHandle;
    use windows_sys::Win32::Storage::FileSystem::{FILE_BASIC_INFO, FILE_ID_INFO, FileBasicInfo, FileIdInfo, GetFileInformationByHandleEx};

    /// 100-nanosecond intervals from 1601-01-01 to 1970-01-01.
    const UNIX_EPOCH_TICKS: i64 = 116_444_736_000_000_000;
    let unix_ms = |ticks: i64| (ticks - UNIX_EPOCH_TICKS) / 10_000;

    let file = std::fs::File::open(path).ok()?;
    let handle = file.as_raw_handle();
    let mut id = FILE_ID_INFO::default();
    // SAFETY: the handle is open, and `id` is a buffer of the size the call is told.
    let read = unsafe {
        GetFileInformationByHandleEx(
            handle,
            FileIdInfo,
            (&raw mut id).cast(),
            u32::try_from(size_of::<FILE_ID_INFO>()).ok()?,
        )
    };
    if read == 0 {
        return None;
    }
    let mut basic = FILE_BASIC_INFO::default();
    // SAFETY: as above, for `basic`.
    let read = unsafe {
        GetFileInformationByHandleEx(
            handle,
            FileBasicInfo,
            (&raw mut basic).cast(),
            u32::try_from(size_of::<FILE_BASIC_INFO>()).ok()?,
        )
    };
    if read == 0 {
        return None;
    }
    Some(FileIdentity {
        id: format!("{:x}-{}", id.VolumeSerialNumber, hex::encode(id.FileId.Identifier)),
        modified_ms: unix_ms(basic.LastWriteTime),
        changed_ms: unix_ms(basic.ChangeTime),
    })
}

/// The sha256 of the bytes about to be installed, as text. Never a number: a digest routed through a JSON number
/// rounds, and a rounded digest compares equal to bytes it does not describe.
pub fn agent_digest(content: &[u8]) -> String {
    hex::encode(Sha256::digest(content))
}

fn is_hex_digest(value: &str) -> bool {
    static HEX_DIGEST: LazyLock<Regex> = LazyLock::new(|| Regex::new("^[0-9a-f]{64}$").expect("a constant pattern compiles"));
    HEX_DIGEST.is_match(value)
}

/// The receipt whose host bytes are still on disk unchanged, or `None`.
///
/// `None` is always safe: it means the install happens. The failure mode it accepts is a host binary rewritten *in
/// place* while keeping its inode, size and both timestamps - which Bazel cannot do to an output, and which `touch
/// -r` can do to a hand-built one. `AIR_VM_GUEST_AGENT_SOURCE` names exactly such a hand-built binary, and it is
/// recorded like any other source: an inner loop that rebuilds it gets a new mtime and reinstalls.
///
/// A receipt from another checkout is rejected outright, and an unresolved host repository rejects every receipt:
/// both mean "install", which is the safe direction. See [`AgentReceipt::host_repo`].
///
/// The other checkout's receipt is the one rejection said out loud. Every other rejection is ordinary - a worker
/// nobody has installed to, a rebuilt source, a receipt that does not read - and reporting those would put a line
/// in front of the operator on lease operations that work as designed. A foreign checkout is the shared pool taking
/// an install away from another working copy, it happens again every time the two alternate, and naming both
/// checkouts turns what used to be a bare exit 64 into a fact the reader can act on.
pub fn installed_agent_receipt(settings: &Config, worker: &str, reporter: &Reporter) -> Option<AgentReceipt> {
    let repo = path_text(settings.host_repo().ok()?);
    let raw = std::fs::read(agent_receipt_path(settings, worker)).ok()?;
    let receipt: AgentReceipt = serde_json::from_slice(&raw).ok()?;
    if receipt.schema_version != SCHEMA_VERSION || receipt.worker != worker || receipt.guest_os != settings.guest_os.as_str() {
        return None;
    }
    if receipt.host_repo != repo {
        // The worker is the scope's and not the message's: both renderings carry the scope already.
        reporter.note(
            format!(
                "reinstalling the guest agent: its receipt was written by {}, and this controller is {repo}",
                receipt.host_repo
            ),
            Some(&Scope::worker(worker)),
        );
        return None;
    }
    if receipt.source.is_empty() || !is_hex_digest(&receipt.sha256) {
        return None;
    }
    if receipt.source_signature.is_empty() || receipt.source_signature != host_file_signature(Path::new(&receipt.source)) {
        return None;
    }
    Some(receipt)
}

/// Records what was just installed: one JSON line at 0600, written atomically.
pub fn write_agent_receipt(settings: &Config, worker: &str, receipt: &AgentReceipt) -> Result<(), Refusal> {
    let mut encoded = serde_json::to_vec(receipt).or_refuse("internal_error", Exit::FAILURE, || {
        format!("cannot describe {worker}'s agent receipt")
    })?;
    encoded.push(b'\n');
    write_atomically_if_changed(&agent_receipt_path(settings, worker), &encoded, 0o600).map(drop)
}

/// The part of the `contract` reply the install reads. Its own shape rather than the wire's whole `Contract`,
/// because an agent older than a field of that document still reports its digest.
#[derive(Deserialize)]
struct SelfDigest {
    #[serde(rename = "selfDigest", default)]
    self_digest: String,
}

impl Guest<'_> {
    /// What the installed agent says its own bytes hash to, or `None` when it cannot answer at all.
    ///
    /// Asked through the `contract` verb, which exists to publish the wire this controller and that binary have to
    /// agree on. `None` covers every way of not knowing - an agent too old to report a digest, a reply that is not
    /// JSON, an exec that failed - and every one of them means "install it".
    ///
    /// The refusal is noted even though it is not returned. This is asked only when a receipt already says this
    /// controller installed an agent here, so a failing `contract` is not the ordinary "nothing here yet": it is
    /// the agent this controller put there refusing to say what it is, and answering silently made the reason
    /// invisible.
    pub async fn agent_self_digest(&self) -> Option<String> {
        let options = SpawnOptions::timeout(CONTRACT_TIMEOUT, "guest_agent_timeout");
        let reply = match self.invoke_agent(AgentAccount::Worker, AgentVerb::Contract, &[], &options).await {
            Ok(reply) => reply,
            Err(refusal) => {
                self.reporter.note(
                    format!("reinstalling the guest agent: it could not say what it is: {}", refusal.message),
                    Some(&self.scope()),
                );
                return None;
            }
        };
        let declared: SelfDigest = serde_json::from_str(&reply).ok()?;
        is_hex_digest(&declared.self_digest).then_some(declared.self_digest)
    }

    /// Puts the guest agent in the guest, unless the guest already runs exactly these bytes.
    ///
    /// Called once per lease operation, once per observation and again on every daemon start. Measured on a warm
    /// `daemon restart`, an unconditional push was the single largest phase: 21.0 s, of which 15.4 s pushed a
    /// binary the guest already had and 5.6 s asked Bazel where the host's copy was. So the guest is asked what it
    /// has, against a host-side [`AgentReceipt`] rather than against the binary itself: a receipt whose source
    /// still has its recorded stat identity says what the host holds without asking Bazel anything. One guest
    /// round-trip (~93 ms) replaces both halves.
    ///
    /// Written beside the destination and renamed onto it, rather than written in place. `tee` opens its
    /// destination with O_TRUNC, and the installed agent is being *executed* whenever a daemon is up, because its
    /// `supervise` process holds the binary as its executable image. Linux refuses the truncation with ETXTBSY,
    /// while a macOS guest lets it through, exits 0, and leaves an unrunnable binary for the *next* invocation to
    /// fail on. A rename replaces only the directory entry and leaves that image alone.
    ///
    /// The staging path is fixed rather than unique: a failed push leaves it behind, and the next install
    /// overwrites it. Two installs never race - every caller holds the worker's lease-operation lock, which is
    /// the same file a lifecycle operation locks.
    pub async fn install_agent(&self, bazel: &dyn BazelHost) -> Result<(), Refusal> {
        let settings = self.settings;
        let worker = self.worker();
        if let Some(installed) = installed_agent_receipt(settings, worker, self.reporter)
            && self.agent_self_digest().await.as_deref() == Some(installed.sha256.as_str())
        {
            return Ok(());
        }
        let source = resolve_agent_binary(self.ctx, settings, bazel).await?;
        // Read between two identical signatures. A source rewritten while it was being read would otherwise be
        // recorded with the new file's identity and the old file's digest, and every later start would skip an
        // install it needed. An unstable source simply gets no receipt, so the next start installs again.
        let before = host_file_signature(&source);
        let content = std::fs::read(&source).or_refuse("guest_agent_missing", Exit::SOFTWARE, || {
            format!("cannot read the guest agent {}", source.display())
        })?;
        let incoming = format!("{}.incoming", settings.vm_agent);
        self.write_file(&incoming, &content, "700").await?;
        self.as_user(
            &words(["/bin/mv", "-f", &incoming, &settings.vm_agent]),
            &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
        )
        .await?;
        // An unresolved host repository is the same answer as an unstable source: a receipt that cannot say which
        // checkout installed these bytes is the one another checkout's controller would trust.
        if let Ok(repo) = settings.host_repo()
            && !before.is_empty()
            && before == host_file_signature(&source)
        {
            return write_agent_receipt(
                settings,
                worker,
                &AgentReceipt {
                    schema_version: SCHEMA_VERSION,
                    worker: worker.to_owned(),
                    guest_os: settings.guest_os.as_str().to_owned(),
                    host_repo: path_text(repo),
                    source: path_text(&source),
                    source_signature: before,
                    sha256: agent_digest(&content),
                },
            );
        }
        // No receipt rather than a wrong one: the next start reinstalls, which is only ever a cost.
        match std::fs::remove_file(agent_receipt_path(settings, worker)) {
            Err(error) if error.kind() != std::io::ErrorKind::NotFound => Err(Refusal::new(
                "state_write_failed",
                Exit::FAILURE,
                format!("cannot discard {worker}'s agent receipt: {error}"),
            )),
            _ => Ok(()),
        }
    }
}

/// Where on the host the guest agent for this guest's platform is, or a refusal naming the build to run.
///
/// Asked of Bazel rather than assembled by hand: the binary reaches its target through a platform transition, so
/// its output lands in a configuration directory of its own that the `out/bazel-bin` convenience symlink never
/// contains. What `cquery` prints is relative to the execution root, which is why the second question is needed.
///
/// Resolution never builds, and freshness is not its job. The host build that `run`, `shard`, `flake` and `daemon
/// start` all perform builds [`agent_label`] beside the lane's own target, outside the lease lock, before any worker
/// is touched. This runs under that lock, where a `bazel build` would spend minutes while every other step spends
/// seconds; what is left over - a lease-only operation in a checkout where the agent was never built - is the
/// operator's to fix in one command, and the refusal names it.
async fn resolve_agent_binary(ctx: &Ctx, settings: &Config, bazel: &dyn BazelHost) -> Result<PathBuf, Refusal> {
    if let Some(source) = &settings.vm_agent_source {
        return match std::fs::metadata(source) {
            Ok(metadata) if !metadata.is_dir() => Ok(source.clone()),
            _ => Err(Refusal::new(
                "guest_agent_missing",
                Exit::FAILURE,
                format!("guest agent binary is missing: {}", source.display()),
            )),
        };
    }
    let label = agent_label(settings.guest_os, settings.guest_arch);
    let repo = settings.host_repo()?;
    if let Some(known) = RESOLVED_AGENT_BINARIES.get(repo, label) {
        return Ok(known);
    }
    let stdout = bazel.query(ctx, "cquery", &cquery_output_file(label)).await?;
    // A single label resolves to one configured target, so stdout is one path and a newline.
    let Some(relative) = last_non_empty_line(&stdout) else {
        return Err(Refusal::new(
            "guest_agent_unresolved",
            Exit::SOFTWARE,
            format!("bazel cquery did not report an output file for {label}"),
        ));
    };
    let root = bazel.execution_root(ctx).await?;
    let root = path_text(&root);
    let root = root.trim();
    if root.is_empty() {
        return Err(Refusal::new(
            "bazel_execution_root_unknown",
            Exit::SOFTWARE,
            "bazel info execution_root answered nothing",
        ));
    }
    let binary = Path::new(root).join(relative);
    if std::fs::metadata(&binary).is_err() {
        return Err(Refusal::new(
            "guest_agent_missing",
            Exit::SOFTWARE,
            format!(
                "the guest agent for a {} guest is not built: {} does not exist. Build it with `./bazel.cmd build \
                 {label}` in {}.",
                settings.guest_os.as_str(),
                binary.display(),
                repo.display()
            ),
        ));
    }
    RESOLVED_AGENT_BINARIES.remember(repo, label, &binary);
    Ok(binary)
}
