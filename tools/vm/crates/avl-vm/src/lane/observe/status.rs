//! `status`: every worker of the pool, reported without leasing, booting or repairing anything.
//!
//! The JSON rows are read by agents and by the docs (`.agents/skills/vm-ui-tests/SKILL.md`), so every key is always
//! present and a tri-state fact is `null` rather than absent. `null` is "the question does not apply or was not
//! asked" - TCC on a worker whose guest never answered, provenance on a Linux guest - and collapsing it into `false`
//! would report a worker as failing a check nobody ran.

#[cfg(unix)]
use std::sync::LazyLock;

use crate::worker::docker::{ContainerState, Docker, ImageSource};
use crate::worker::hypervisor::Machine;
use crate::worker::lima::EngineState;
use crate::worker::worker::{Manager, read_lease};
use avl_base::format::words;
use avl_base::{Backend, Config, Outcome, Refusal};
use avl_host_sys::guest::{GUEST_COMMAND_TIMEOUT, Guest, ensure_host_paths};
use avl_host_sys::{Ctx, Runner};
#[cfg(unix)]
use avl_wire::supervisor::RunState;
use futures::future::join_all;
#[cfg(unix)]
use regex::Regex;
use serde::Serialize;

#[cfg(unix)]
use super::split_lines;

#[cfg(unix)]
mod parallels;
#[cfg(unix)]
mod tart;

/// A `who` line whose terminal is the console, which is what an Aqua session is. The Tart and the Parallels rows ask
/// it of a macOS guest.
#[cfg(unix)]
static CONSOLE_SESSION_LINE: LazyLock<Regex> = LazyLock::new(|| {
    // An invariant: a literal pattern, exercised by the tests.
    Regex::new(r"^\S+\s+console(?:\s|$)").expect("a literal pattern compiles")
});

#[cfg(unix)]
fn console_login_seen(stdout: &str) -> bool {
    split_lines(stdout).any(|line| CONSOLE_SESSION_LINE.is_match(line))
}

/// What `status` says about a worker's lease: that it is held and since when, never by whom - who holds a worker is
/// not something an unauthenticated `status` is told.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
struct LeaseSummary {
    state: &'static str,
    acquired_at: String,
}

fn lease_summary(settings: &Config, worker: &str) -> Result<Option<LeaseSummary>, Refusal> {
    Ok(read_lease(&settings.lease_path(worker))?.map(|held| LeaseSummary {
        state: "leased",
        acquired_at: held.acquired_at,
    }))
}

/// The two host paths a report names, resolved once for the whole command, and the refusal instead of a failure.
///
/// `status` is a report: a checkout `git` will not call a work tree, or a Bazel output root that cannot be created,
/// must still not stop the command from saying what every worker's state is. So the refusal becomes a field, and
/// both status paths produce it here. Resolution happens before the fan-out because it is not a worker fact, and
/// because the parity verdict is a comparison against these paths. It is the only write `status` performs, and it is
/// on the host: [`ensure_host_paths`] creates the configured Bazel output root if it does not exist yet.
struct HostPaths {
    repo: String,
    bazel_user_root: String,
    error: Option<String>,
}

impl HostPaths {
    async fn resolve(ctx: &Ctx, runner: &Runner, settings: &Config) -> Self {
        let error = ensure_host_paths(ctx, runner, settings)
            .await
            .err()
            .map(|refusal| refusal.code.into_owned());
        Self {
            repo: settings
                .host_repo()
                .map(|repo| repo.to_string_lossy().into_owned())
                .unwrap_or_default(),
            bazel_user_root: settings
                .host_bazel_user_root()
                .unwrap_or_else(|_| settings.configured_bazel_user_root())
                .to_string_lossy()
                .into_owned(),
            error,
        }
    }

    /// The checkout a row names, or `null` where nothing resolved one. The Tart and the Parallels rows name it.
    #[cfg(unix)]
    fn repo(&self) -> Option<String> {
        Some(self.repo.clone()).filter(|repo| !repo.is_empty())
    }
}

/// Reports every worker of the pool, without leasing, booting or repairing anything.
pub(crate) async fn command_status(ctx: &Ctx, manager: &Manager) -> Result<Outcome, Refusal> {
    manager.prepare_runtime_dirs()?;
    match manager.machine() {
        #[cfg(unix)]
        Machine::Tart(tart) => tart::status(ctx, manager, tart).await,
        #[cfg(unix)]
        Machine::Parallels(parallels) => parallels::status(ctx, manager, parallels).await,
        Machine::Docker(docker) => docker_status(ctx, manager, docker).await,
    }
}

/// A tri-state verdict and the refusal that decided a negative one.
#[derive(Debug, Default)]
struct Verdict {
    ready: Option<bool>,
    error: Option<String>,
}

impl Verdict {
    fn of(result: Result<(), Refusal>) -> Self {
        Self::of_answer(result.map(|()| true))
    }

    /// A question whose answer may be `false` without a refusal, as a stale container declaration is.
    fn of_answer(result: Result<bool, Refusal>) -> Self {
        match result {
            Ok(ready) => Self {
                ready: Some(ready),
                error: None,
            },
            Err(refusal) => Self {
                ready: Some(false),
                error: Some(refusal.code.into_owned()),
            },
        }
    }
}

/// The two layout questions the Tart and the Docker rows ask of a running guest. The Parallels row asks parity
/// behind two gates of its own and has no storage fact.
#[derive(Debug, Default)]
struct Layout {
    worker_storage_ready: bool,
    parity: Verdict,
}

impl Layout {
    async fn probe(guest: &Guest<'_>, host_paths: &HostPaths) -> Self {
        let worker_storage_ready = guest
            .succeeds(&words(["/bin/test", "-d", &guest.settings.vm_data]), GUEST_COMMAND_TIMEOUT)
            .await;
        // Only where the host paths resolved: parity is a verdict on the *guest's* layout, decided by comparing it
        // against those paths, so without them the question was never asked and the row says `n/a` rather than
        // contradicting the one `hostPathsError` above it.
        let parity = if host_paths.error.is_none() {
            Verdict::of(guest.ensure_parity_ready().await)
        } else {
            Verdict::default()
        };
        Self {
            worker_storage_ready,
            parity,
        }
    }
}

/// The run slot as the guest agent answered it. The Tart and the Parallels rows ask it.
#[cfg(unix)]
#[derive(Debug, Default)]
struct RunSlotFacts {
    active: Option<RunState>,
    error: Option<String>,
}

#[cfg(unix)]
impl RunSlotFacts {
    /// Asked only where the agent is installed at all: a worker that never had one has no run slot to report.
    async fn read(guest: &Guest<'_>) -> Self {
        let agent = &guest.settings.vm_agent;
        if !guest.succeeds(&words(["/bin/test", "-f", agent]), GUEST_COMMAND_TIMEOUT).await {
            return Self::default();
        }
        match guest.active_run().await {
            Ok(active) => Self { active, error: None },
            Err(refusal) => Self {
                active: None,
                error: Some(refusal.code.into_owned()),
            },
        }
    }
}

/// One tri-state verdict with the refusal that decides what to do about it, because the reader of a one-line report
/// is the one who most needs `guest_init_stale` - which the next run repairs by itself - told apart from a layout
/// somebody has to look at.
fn verdict_word(ready: Option<bool>, refusal: Option<&str>) -> String {
    verdict_words(ready, refusal, "ready", "not-ready")
}

/// [`verdict_word`] for a verdict whose two answers have words of their own, as `declaration=current|stale` has.
fn verdict_words(ready: Option<bool>, refusal: Option<&str>, when_true: &'static str, when_false: &'static str) -> String {
    let word = tri_state_word(ready, when_true, when_false);
    match refusal {
        Some(refusal) => format!("{word}({refusal})"),
        None => word.to_owned(),
    }
}

/// `n/a` is deliberately neither `not-ready` nor `no`: null means the question does not apply to this guest OS, or
/// that nothing asked it because the guest never answered, and a worker must not read as failing a check nobody
/// ran.
const fn tri_state_word(value: Option<bool>, when_true: &'static str, when_false: &'static str) -> &'static str {
    match value {
        None => "n/a",
        Some(true) => when_true,
        Some(false) => when_false,
    }
}

/// A host-path refusal for a text report, and nothing when there is none. The Parallels line appends it after its
/// own verdicts, the Tart report prepends it as the pool's own row ([`pool_report`]), and the Docker report appends
/// it to its pool row, which names the engine.
fn host_paths_fragment(refusal: Option<&str>) -> String {
    refusal
        .map(|refusal| format!(" host_paths={}", verdict_word(Some(false), Some(refusal))))
        .unwrap_or_default()
}

/// The text report of a pool of workers: one line per worker, below the host-path refusal when there is one.
///
/// Above the workers, and only when there is one: it is what left every `parity=n/a` below it unasked. A row of its
/// own because the fact is the pool's, and `pool` is not a worker name this controller accepts.
#[cfg(unix)]
fn pool_report(host_paths_error: Option<&str>, lines: impl Iterator<Item = String>) -> String {
    host_paths_error
        .map(|error| format!("pool:{}", host_paths_fragment(Some(error))))
        .into_iter()
        .chain(lines)
        .collect::<Vec<_>>()
        .join("\n")
}

const fn leased_word(lease: Option<&LeaseSummary>) -> &'static str {
    if lease.is_some() { "leased" } else { "free" }
}

// --- the docker status ---------------------------------------------------------------------------------------

/// One Docker worker's row, whose field order is the JSON order.
///
/// A shape of its own, as the Parallels row is, and not the Tart row with holes: a container has no provenance, no
/// console session, no TCC, no SSH host key and no root disk this controller sized. It has two facts of its own
/// instead, the image it runs and the create argv it was made with, and a start compares both.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DockerWorkerStatus {
    worker: String,
    exists: bool,
    /// The engine's word: `absent`, `created`, `running`, `exited`, or another word kept as the engine said it.
    /// `unknown` when the Lima engine does not run, because `status` never starts it.
    state: String,
    /// The exit code of the entrypoint on an exited container, and null in every other state.
    exit_code: Option<i32>,
    /// The tag a start would run now. It is the same on every row, because it is a digest of the image inputs of
    /// this checkout.
    image: String,
    /// Where the engine's copy of that tag came from. `missing` means that the next start pulls the image, or builds
    /// it when the pull fails, which takes minutes.
    image_source: ImageSourceWord,
    /// Whether the container of that name is the one this controller created, with the argv a create would use now:
    /// the question a start asks before it keeps a container. Null where no container exists, and where the host
    /// paths that the argv binds did not resolve. `false` means that the next lease creates the container again.
    declaration_current: Option<bool>,
    declaration_error: Option<String>,
    /// Asked only of a running container.
    guest_agent: bool,
    worker_storage_ready: bool,
    parity_ready: Option<bool>,
    parity_error: Option<String>,
    lease: Option<LeaseSummary>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DockerStatusData {
    backend: Backend,
    /// `lima` for the controller's Lima VM, `host` for the engine of the host environment.
    engine: &'static str,
    /// Lima's word for its VM (`Absent`, `Stopped`, `Running`, or another word kept as Lima said it). Null for the
    /// engine of the host environment, which this controller does not own.
    engine_status: Option<String>,
    workers: Vec<DockerWorkerStatus>,
    host_repo: String,
    host_bazel_user_root: String,
    /// One host fact for the pool, as on the Tart report.
    host_paths_error: Option<String>,
}

/// Where the engine's copy of the image came from, as `status` says it.
///
/// `pulled` when the pool's image record says this tag was pulled; `local` when the engine has the tag and the
/// record says built, or says nothing about this tag; `missing` when the engine does not have the tag; `unknown` when
/// the Lima engine does not run and nothing asked it.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
enum ImageSourceWord {
    Local,
    Pulled,
    Missing,
    Unknown,
}

impl ImageSourceWord {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Local => "local",
            Self::Pulled => "pulled",
            Self::Missing => "missing",
            Self::Unknown => "unknown",
        }
    }
}

/// The image a start would run, asked once for the whole pool.
struct DockerImage {
    tag: String,
    source: ImageSourceWord,
}

/// `status` for a Docker pool.
///
/// Read-only toward the engine: `docker version`, `docker image inspect`, one `docker inspect` per slot, and
/// `docker exec` of read-only probes in a running container. It never creates, starts, stops, removes or builds.
///
/// Read-only toward the Lima engine too: `limactl list` only, and none at all for an engine never created. An engine
/// that does not run is reported with its word, and no `docker` command asks it, so every row says `unknown`. The
/// gate of a running Lima engine is not run, because it would make an engine of another template again. The pinned
/// CLI is resolved, and the engine answers the questions below.
async fn docker_status(ctx: &Ctx, manager: &Manager, docker: &Docker) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    let (engine_status, reachable) = match docker.engine() {
        None => {
            docker.require_available(ctx, "").await?;
            (None, true)
        }
        Some(lima) => {
            let state = lima.state(ctx).await?;
            let running = state == EngineState::Running;
            if running {
                docker.resolved_program(ctx).await?;
            }
            (Some(state.as_str().to_owned()), running)
        }
    };
    // Before the fan-out, for the reason the Tart report gives: the parity and the declaration questions both need
    // these paths.
    let host_paths = HostPaths::resolve(ctx, manager.runner(), settings).await;
    let tag = docker.image_tag();
    let source = if !reachable {
        ImageSourceWord::Unknown
    } else if docker.image_present(ctx, &tag).await? {
        match docker.read_image_record() {
            Some(record) if record.tag == tag && record.source == ImageSource::Pulled => ImageSourceWord::Pulled,
            _ => ImageSourceWord::Local,
        }
    } else {
        ImageSourceWord::Missing
    };
    let image = DockerImage { tag, source };
    let workers = if reachable {
        let probes = settings
            .workers
            .iter()
            .map(|worker| docker_worker_status(ctx, manager, docker, worker, &image, &host_paths));
        join_all(probes).await.into_iter().collect::<Result<Vec<_>, Refusal>>()?
    } else {
        settings
            .workers
            .iter()
            .map(|worker| unreachable_docker_worker_status(settings, worker, &image))
            .collect::<Result<Vec<_>, Refusal>>()?
    };
    let engine = settings.docker_engine.as_str();
    let engine_line = format!(
        "pool: engine={engine}{}{}",
        engine_status
            .as_deref()
            .map(|word| format!(" engine_status={word}"))
            .unwrap_or_default(),
        host_paths_fragment(host_paths.error.as_deref())
    );
    let text = std::iter::once(engine_line)
        .chain(workers.iter().map(render_docker_status_line))
        .collect::<Vec<_>>()
        .join("\n");
    Outcome::new(
        DockerStatusData {
            backend: settings.backend,
            engine,
            engine_status,
            workers,
            host_repo: host_paths.repo,
            host_bazel_user_root: host_paths.bazel_user_root,
            host_paths_error: host_paths.error,
        },
        text,
    )
}

/// Probes one Docker worker. The guest is asked only when the engine says the container runs, because `docker exec`
/// into a stopped container fails and a probe that fails reads as a broken guest.
async fn docker_worker_status(
    ctx: &Ctx,
    manager: &Manager,
    docker: &Docker,
    worker: &str,
    image: &DockerImage,
    host_paths: &HostPaths,
) -> Result<DockerWorkerStatus, Refusal> {
    let settings = manager.settings();
    let lease = lease_summary(settings, worker)?;
    let state = docker.state(ctx, worker).await?;
    let exists = state != ContainerState::Absent;
    // Not asked without the host paths: the current argv binds them, so the verdict would only repeat the one
    // `hostPathsError` of the pool on every row.
    let declaration = if exists && host_paths.error.is_none() {
        Verdict::of_answer(docker.container_is_current(ctx, worker).await)
    } else {
        Verdict::default()
    };
    let (guest_agent, layout) = if state == ContainerState::Running {
        let channel = manager.channel(worker);
        let guest = Guest {
            ctx,
            settings,
            channel: channel.as_ref(),
            reporter: manager.reporter(),
        };
        if guest.succeeds(&words(["/usr/bin/true"]), GUEST_COMMAND_TIMEOUT).await {
            (true, Layout::probe(&guest, host_paths).await)
        } else {
            (false, Layout::default())
        }
    } else {
        (false, Layout::default())
    };
    let exit_code = match state {
        ContainerState::Exited(code) => Some(code),
        _ => None,
    };
    Ok(DockerWorkerStatus {
        worker: worker.to_owned(),
        exists,
        state: state.as_str().to_owned(),
        exit_code,
        image: image.tag.clone(),
        image_source: image.source,
        declaration_current: declaration.ready,
        declaration_error: declaration.error,
        guest_agent,
        worker_storage_ready: layout.worker_storage_ready,
        parity_ready: layout.parity.ready,
        parity_error: layout.parity.error,
        lease,
    })
}

/// The row of a worker whose Lima engine does not run: only the lease and the image tag are known without the engine.
fn unreachable_docker_worker_status(settings: &Config, worker: &str, image: &DockerImage) -> Result<DockerWorkerStatus, Refusal> {
    Ok(DockerWorkerStatus {
        worker: worker.to_owned(),
        exists: false,
        state: "unknown".to_owned(),
        exit_code: None,
        image: image.tag.clone(),
        image_source: image.source,
        declaration_current: None,
        declaration_error: None,
        guest_agent: false,
        worker_storage_ready: false,
        parity_ready: None,
        parity_error: None,
        lease: lease_summary(settings, worker)?,
    })
}

fn render_docker_status_line(row: &DockerWorkerStatus) -> String {
    let exit_code = row.exit_code.map(|code| format!("({code})")).unwrap_or_default();
    format!(
        "{}: {}{exit_code} lease={} image={} declaration={} parity={}",
        row.worker,
        row.state,
        leased_word(row.lease.as_ref()),
        row.image_source.as_str(),
        verdict_words(row.declaration_current, row.declaration_error.as_deref(), "current", "stale"),
        verdict_word(row.parity_ready, row.parity_error.as_deref()),
    )
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
