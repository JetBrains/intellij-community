//! The run option grammar ([`RunArgs`]), the selection it resolves to, the hot-jar push and the NDJSON run stream
//! with its transport watchdog.

use std::collections::HashSet;
use std::fmt;
use std::time::Duration;

use crate::lane::{AffectedSelection, is_named_suite_selector};
use avl_base::clock::stamp;
use avl_base::{Exit, OrRefuse, Refusal, Scope};
use avl_host_sys::Ctx;
use avl_host_sys::guest::ensure_host_paths;
use avl_report::digest::PathDigest;
use avl_wire::daemon::{self as wire, RunEvent, RunEventKind, RunFailed, Summary, WatchdogExpired, conflict};
use avl_wire::progress::Event;
use bt_junit::simple_class_name_pattern;
use futures::StreamExt as _;
use http_body_util::BodyExt;
use jiff::{SignedDuration, Timestamp};
use serde::Serialize;
use serde_json::json;
use tokio::time::{Instant, sleep_until};

use crate::daemon::build::PreparedBuild;
use crate::daemon::host::{Host, WatchdogPolicy};
use crate::daemon::http::{DaemonClient, error_chain, protocol_refusal, transport_refusal};
use crate::daemon::state::HostState;
use avl_base::RefusalExt;

#[cfg(test)]
#[cfg(unix)]
pub(crate) mod tests;

// --- the option grammar ------------------------------------------------------------------------------------------

/// The kinds of JUnit5 filter the daemon implements, and therefore the only ones `--filter` accepts.
///
/// Kept host-side because the alternative is a round trip: an unknown option reaches
/// `AirUiDaemonIteration.buildFilters` as an `IllegalArgumentException` after the build, the daemon start and the
/// jar push have all been paid for, and it surfaces as a container failure rather than as a typo. This enum has to
/// stay in step with that `when` - it is the same six options.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Junit5FilterKind {
    IncludeTag,
    ExcludeTag,
    IncludeClassname,
    ExcludeClassname,
    IncludePackage,
    ExcludePackage,
}

impl Junit5FilterKind {
    /// Every kind, in the order of the daemon's `when`.
    pub(crate) const ALL: [Self; 6] = [
        Self::IncludeTag,
        Self::ExcludeTag,
        Self::IncludeClassname,
        Self::ExcludeClassname,
        Self::IncludePackage,
        Self::ExcludePackage,
    ];

    /// The option as the daemon and `--filter` spell it.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::IncludeTag => "include-tag",
            Self::ExcludeTag => "exclude-tag",
            Self::IncludeClassname => "include-classname",
            Self::ExcludeClassname => "exclude-classname",
            Self::IncludePackage => "include-package",
            Self::ExcludePackage => "exclude-package",
        }
    }

    /// Every option, as the help text and a refusal list them.
    pub(crate) fn names() -> String {
        Self::ALL.map(Self::as_str).join(", ")
    }
}

/// One JUnit5 filter: its kind and its value. The `/run` request spells it `<option>=<value>`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Junit5Filter {
    pub kind: Junit5FilterKind,
    pub value: String,
}

impl fmt::Display for Junit5Filter {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}={}", self.kind.as_str(), self.value)
    }
}

/// What `run` selects and how long it may take. `run` parses it, and `shard` and `flake` flatten it into their own
/// options, so the three commands share one grammar.
///
/// `--changed` takes every value that follows it, and with `--changed` every bare argument is a changed path too:
/// `--changed A B` and `--changed A --changed B` are the same run. Without it, the one bare argument is the
/// selector.
#[derive(clap::Args, Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct RunArgs {
    /// A test class, an FQN[#method], a flow id or a suite id; with --changed, more changed paths.
    #[arg(value_name = "SELECTOR")]
    pub selectors: Vec<String>,
    /// An IDE UI lane, run whole, or the lane that settles a selection reaching two.
    #[arg(long, value_name = "LANE")]
    pub lane: Option<String>,
    /// Runs the generated suites the changed paths reach.
    #[arg(long, value_name = "PATH", num_args = 1..)]
    pub changed: Vec<String>,
    /// One more JUnit5 filter, `<option>=<value>`, for an option the daemon applies.
    #[arg(long = "filter", value_name = "OPTION=VALUE", value_parser = parse_filter)]
    pub filters: Vec<Junit5Filter>,
    /// The per-execution watchdog budget, in seconds.
    #[arg(long, value_name = "SECONDS", value_parser = parse_execution_seconds)]
    pub timeout: Option<u64>,
    /// The between-execution watchdog budget, in seconds.
    #[arg(long = "progress-timeout", value_name = "SECONDS", value_parser = parse_gap_seconds)]
    pub progress_timeout: Option<u64>,
    /// Relaunches the IDE before the first iteration.
    #[arg(long = "fresh-ide")]
    pub fresh_ide: bool,
}

impl RunArgs {
    /// The arguments as one command line, the way a journal and the history of earlier runs name a run: the same
    /// options always spell the same line.
    pub(crate) fn argv(&self) -> Vec<String> {
        let mut argv = self.selectors.clone();
        if let Some(lane) = &self.lane {
            argv.extend(["--lane".to_owned(), lane.clone()]);
        }
        if !self.changed.is_empty() {
            argv.push("--changed".to_owned());
            argv.extend(self.changed.iter().cloned());
        }
        for filter in &self.filters {
            argv.extend(["--filter".to_owned(), filter.to_string()]);
        }
        if let Some(seconds) = self.timeout {
            argv.extend(["--timeout".to_owned(), seconds.to_string()]);
        }
        if let Some(seconds) = self.progress_timeout {
            argv.extend(["--progress-timeout".to_owned(), seconds.to_string()]);
        }
        if self.fresh_ide {
            argv.push("--fresh-ide".to_owned());
        }
        argv
    }
}

/// `run`'s own options: every option of [`RunArgs`], and the run secrets, which only `run` takes.
///
/// Not in [`RunArgs`], which `shard` and `flake` flatten: a secret of a run is read once and lives for one
/// iteration, and a command that repeats a selection over several workers or trials would multiply where it goes.
#[derive(clap::Args, Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct RunCommandArgs {
    #[command(flatten)]
    pub run: RunArgs,
    /// A file the guest test JVM reads as AIR_VM_RUN_SECRETS/NAME; repeatable. NAME=@- reads a pipe, NAME=@FILE a host
    /// file: a value is refused.
    #[arg(long = "test-env", value_name = "NAME=@-|NAME=@FILE", value_parser = crate::lane::secrets::parse_test_env)]
    pub test_env: Vec<crate::lane::secrets::RunSecretFile>,
}

/// One `--filter option=value`, validated against what the daemon can actually apply.
pub(crate) fn parse_filter(value: &str) -> Result<Junit5Filter, String> {
    let Some((option, filter_value)) = value
        .split_once('=')
        .filter(|(option, rest)| !option.is_empty() && !rest.is_empty())
    else {
        return Err(format!("--filter expects <option>=<value>, got {value:?}"));
    };
    match Junit5FilterKind::ALL.into_iter().find(|kind| kind.as_str() == option) {
        Some(kind) => Ok(Junit5Filter {
            kind,
            value: filter_value.to_owned(),
        }),
        None => Err(format!(
            "--filter option {option:?} is not one the daemon applies; accepted: {}",
            Junit5FilterKind::names()
        )),
    }
}

fn parse_execution_seconds(value: &str) -> Result<u64, String> {
    positive_seconds(value).ok_or_else(|| "--timeout expects positive per-execution seconds".to_owned())
}

fn parse_gap_seconds(value: &str) -> Result<u64, String> {
    positive_seconds(value).ok_or_else(|| "--progress-timeout expects positive seconds".to_owned())
}

/// Strict base ten, like every other integer this controller reads from a caller: `8.0`, `1e3` and `+5` are
/// refused rather than read differently from how a person meant them.
fn positive_seconds(value: &str) -> Option<u64> {
    if value.is_empty() || !value.bytes().all(|byte| byte.is_ascii_digit()) {
        return None;
    }
    value.parse::<u64>().ok().filter(|seconds| (1..=(1 << 53) - 1).contains(seconds))
}

// --- what a run selects ------------------------------------------------------------------------------------------

/// What one iteration executes: the JUnit selectors, the JUnit5 filters, and how the selection names itself in a
/// verdict.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct RunSelection {
    pub selectors: Vec<String>,
    pub junit5_filters: Vec<String>,
    pub description: String,
}

/// One controller-side selector in the daemon's `/run` shape: the selectors, then the JUnit5 filters.
///
/// A method reference or an FQN becomes a precise JUnit selector; a bare class name has no package to select by,
/// so it travels as a class-name regex filter over a full hot-tier scan, mirroring `JUnit5BazelRunner`'s
/// simple-name handling. [`simple_class_name_pattern`] owns that pattern.
fn selector_to_run_request(filter: &str) -> (Vec<String>, Vec<String>) {
    if filter.contains(['#', '.']) {
        return (vec![filter.to_owned()], Vec::new());
    }
    let filter = Junit5Filter {
        kind: Junit5FilterKind::IncludeClassname,
        value: simple_class_name_pattern(filter),
    };
    (Vec::new(), vec![filter.to_string()])
}

/// One selection, as `shard` and `flake` read `run`'s arguments.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ParsedRun {
    pub selection: RunSelection,
    pub policy: WatchdogPolicy,
    pub fresh_ide: bool,
}

/// Everything `run` learned from its arguments: one iteration per lane it runs.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RunPlan {
    /// One entry, except for generated suites that reach several lanes without `--lane`.
    pub iterations: Vec<LaneIteration>,
    pub policy: WatchdogPolicy,
    pub fresh_ide: bool,
}

/// One iteration of a [`RunPlan`].
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LaneIteration {
    /// The lane of the generated suites this iteration selects; `None` for a class, an FQN or `--lane`.
    pub lane: Option<String>,
    /// The lane's generated suite classes, in the answer's order; empty when `lane` is `None`.
    pub classes: Vec<String>,
    pub selection: RunSelection,
}

/// The lane's own filter list, `a;b`, with empty entries dropped.
fn split_filters(joined: Option<&String>) -> Vec<String> {
    joined
        .map(|joined| joined.split(';').filter(|entry| !entry.is_empty()).map(str::to_owned).collect())
        .unwrap_or_default()
}

/// How a `--filter`-narrowed selection names itself, so a false-green verdict says what excluded the tests.
fn describe_selection(base: String, extra_filters: &[Junit5Filter]) -> String {
    if extra_filters.is_empty() {
        return base;
    }
    let filters: Vec<String> = extra_filters.iter().map(ToString::to_string).collect();
    format!("{base} (filters: {})", filters.join(", "))
}

impl Host {
    /// The selection half of `run`'s grammar, as the refusal for a missing selection spells it.
    fn run_selection_usage(&self) -> String {
        let program = self.reporter.program();
        let lanes = avl_affected::ui_lane_names().join("|");
        format!(
            "usage: {program} run <TestClass|FQN[#method]> | {program} run <flow-id|suite-id> [--lane {lanes}] | \
             {program} run --lane {lanes} | {program} run --changed PATH..."
        )
    }

    /// `run`'s arguments as one selection, a watchdog policy and the fresh-IDE flag.
    ///
    /// The parse of `shard` and `flake`, which run one lane: generated suites of two lanes are refused as
    /// `affected_lanes_ambiguous` and need `--lane`.
    pub(crate) async fn parse_run(&self, ctx: &Ctx, args: &RunArgs) -> Result<ParsedRun, Refusal> {
        let mut plan = self.plan(ctx, args, false).await?;
        let iteration = plan.iterations.remove(0);
        Ok(ParsedRun {
            selection: iteration.selection,
            policy: plan.policy,
            fresh_ide: plan.fresh_ide,
        })
    }

    /// `run`'s arguments as its iterations, a watchdog policy and the fresh-IDE flag.
    ///
    /// A flow id, a suite id and `--changed` select generated suites: per lane, the lane's tag and one class filter
    /// per suite. Without `--lane`, the plan holds one iteration per reached lane; see
    /// [`crate::lane::Reached::every_lane`].
    pub(crate) async fn plan_run(&self, ctx: &Ctx, args: &RunArgs) -> Result<RunPlan, Refusal> {
        self.plan(ctx, args, true).await
    }

    /// The one reading of `run`'s arguments. `every_lane` is false for one selection, which refuses a two-lane
    /// answer and an explicit-only lane: only `shard` and `flake` read one selection, and both repeat it. Everything
    /// here is decided from the arguments and the checkout, so a refusal costs no build and no worker.
    async fn plan(&self, ctx: &Ctx, args: &RunArgs, every_lane: bool) -> Result<RunPlan, Refusal> {
        let changed: Vec<String> = if args.changed.is_empty() {
            Vec::new()
        } else {
            args.changed.iter().chain(&args.selectors).cloned().collect()
        };
        if changed.iter().any(String::is_empty) {
            return Err(Refusal::usage("--changed requires a path"));
        }
        let selector = match args.selectors.as_slice() {
            _ if !changed.is_empty() => None,
            [] => None,
            [one] => Some(one.as_str()),
            _ => return Err(Refusal::usage("pass exactly one test selector")),
        };
        // A flow or a suite id takes `--lane` as the answer to its two-lane refusal, exactly as `--changed` does, so
        // the pair is one selection rather than two.
        let names_suites = selector.is_some_and(is_named_suite_selector);
        if changed.is_empty() && !names_suites && args.lane.is_none() == selector.is_none() {
            return Err(Refusal::usage(self.run_selection_usage()));
        }
        let policy = self.watchdog_policy(
            args.timeout.map(Duration::from_secs),
            args.progress_timeout.map(Duration::from_secs),
        );
        let extra = &args.filters;
        let lane = args.lane.as_deref();
        let single = |selection: RunSelection| RunPlan {
            iterations: vec![LaneIteration {
                lane: None,
                classes: Vec::new(),
                selection,
            }],
            policy,
            fresh_ide: args.fresh_ide,
        };

        if !changed.is_empty() || names_suites {
            // The join reads the checkout, so the host paths have to resolve first.
            ensure_host_paths(ctx, &self.runner, &self.settings).await?;
            let (reached, asked) = match selector {
                Some(selector) if names_suites => (
                    crate::lane::resolve_named_suites(&self.settings, selector, &self.reporter)?,
                    selector.to_owned(),
                ),
                _ => (
                    crate::lane::resolve_changed_suites(&self.settings, &changed, &self.reporter)?,
                    format!("{} changed path(s)", changed.len()),
                ),
            };
            let lanes: Vec<AffectedSelection> = if every_lane {
                reached.every_lane(lane)?
            } else {
                vec![reached.one_lane(lane)?]
            };
            let iterations = lanes
                .into_iter()
                .map(|affected| {
                    let mut junit5_filters = split_filters(affected.selection.test_env.get("JB_TEST_JUNIT5_FILTERS"));
                    junit5_filters.extend(extra.iter().map(ToString::to_string));
                    LaneIteration {
                        selection: RunSelection {
                            selectors: Vec::new(),
                            junit5_filters,
                            description: describe_selection(
                                format!("{asked} -> lane {}: {}", affected.lane, affected.classes.join(", ")),
                                extra,
                            ),
                        },
                        lane: Some(affected.lane),
                        classes: affected.classes,
                    }
                })
                .collect();
            return Ok(RunPlan {
                iterations,
                policy,
                fresh_ide: args.fresh_ide,
            });
        }

        if let Some(lane) = lane {
            // Every UI lane runs through this daemon: its classpath is the union of both IDE-launching targets, and
            // the lane's agent-CLI passthroughs are already in its boot environment. What is left of a lane here is
            // its JUnit filter.
            let selected = crate::lane::lane_selection(lane)?;
            if !every_lane {
                refuse_explicit_only(&selected.label, &self.reporter.program())?;
            }
            let mut junit5_filters = split_filters(selected.test_env.get("JB_TEST_JUNIT5_FILTERS"));
            junit5_filters.extend(extra.iter().map(ToString::to_string));
            return Ok(single(RunSelection {
                selectors: Vec::new(),
                junit5_filters,
                description: describe_selection(format!("lane {lane}"), extra),
            }));
        }

        // The usage check above leaves a selector here. The resolution reads the checkout, so the host paths
        // resolve first; flag mistakes above still refuse before any subprocess runs.
        let selector = selector.unwrap_or_default();
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        let resolved = crate::lane::resolve_selector_selection(&self.settings, selector, &self.reporter)?;
        if !every_lane {
            refuse_explicit_only(&resolved.label, &self.reporter.program())?;
        }
        let description = describe_selection(selector.to_owned(), extra);
        let (selectors, mut junit5_filters) = match resolved.test_env.get("TESTBRIDGE_TEST_ONLY").filter(|only| !only.is_empty()) {
            Some(only) => selector_to_run_request(only),
            None => (Vec::new(), split_filters(resolved.test_env.get("JB_TEST_JUNIT5_FILTERS"))),
        };
        junit5_filters.extend(extra.iter().map(ToString::to_string));
        Ok(single(RunSelection {
            selectors,
            junit5_filters,
            description,
        }))
    }
}

/// Refuses a repeated selection of an explicit-only lane: `shard` and `flake` would multiply its billed turns.
///
/// A flow, a suite or a changed path never reaches such a lane, so only `--lane` and a selector that resolves to its
/// target can name one. Both are refused here, before a build or a lease (ADR 0200).
fn refuse_explicit_only(label: &str, program: &str) -> Result<(), Refusal> {
    match avl_affected::ui_lane_of_label(label) {
        Some(lane) if lane.explicit_only => Err(Refusal::new(
            "explicit_only_lane",
            Exit::USAGE,
            format!(
                "{name} is an explicit-only lane: each scenario spends a billed turn, so it runs once through \
                 `{program} run --lane {name}`, never through shard or flake",
                name = lane.name
            ),
        )),
        _ => Ok(()),
    }
}

// --- the jar push ------------------------------------------------------------------------------------------------

/// The jar bytes that one upload connection carries before the push opens another.
///
/// Each upload in flight holds a pooled HTTP connection of its own, and each new connection spawns one relay child in
/// the guest, which ADR 0182 measured at 0.65 s on Tart. A new connection pays only for bytes that one connection would
/// take longer than that to move. So a push of at most 8 MiB uses the one connection of the `/jars` query and opens no
/// relay more. The throughput of one `tart exec` stream is not measured, so the step is an estimate.
pub(crate) const BYTES_PER_UPLOAD_CONNECTION: u64 = 8 << 20;

/// The most jar uploads that run at once. It also bounds the jars held in memory at once.
pub(crate) const MAX_PARALLEL_UPLOADS: usize = 4;

/// How many uploads run at once for a push of `bytes`: one for each [`BYTES_PER_UPLOAD_CONNECTION`] begun, at least one
/// and at most [`MAX_PARALLEL_UPLOADS`].
pub(crate) fn upload_concurrency(bytes: u64) -> usize {
    usize::try_from(bytes.div_ceil(BYTES_PER_UPLOAD_CONNECTION))
        .unwrap_or(usize::MAX)
        .clamp(1, MAX_PARALLEL_UPLOADS)
}

/// Offers the hot tier and the context plugin files by digest, and uploads only what the guest does not already hold,
/// answering how many files travelled. A digest that two files share is offered once.
///
/// [`upload_concurrency`] of the bytes to push decides how many uploads run at once. The first upload that fails ends
/// the push with its refusal, and the uploads still in flight end with it, because their futures are dropped.
pub(crate) async fn push_jars(ctx: &Ctx, daemon: &DaemonClient, state: &HostState, prep: &PreparedBuild) -> Result<usize, Refusal> {
    let mut offered = HashSet::new();
    let jars: Vec<&PathDigest> = prep
        .hot_jars
        .iter()
        .chain(prep.plugin_files.iter().map(|file| &file.host))
        .filter(|jar| offered.insert(jar.sha256.as_str()))
        .collect();
    let digests: Vec<&str> = jars.iter().map(|jar| jar.sha256.as_str()).collect();
    let body = encode(&json!({ "jars": digests }))?;
    let (status, reply) = daemon.http(ctx, state, &wire::MISSING_JARS, Some(body.into())).await?;
    if !status.is_success() {
        return Err(push_failed(format!("/jars query returned {}", status.as_u16())));
    }
    let missing: HashSet<String> = wire::decode_missing_jars(&reply)
        .map_err(protocol_refusal)?
        .missing
        .into_iter()
        .collect();
    let travelling: Vec<&PathDigest> = jars.into_iter().filter(|jar| missing.contains(&jar.sha256)).collect();
    let mut bytes = 0;
    for jar in &travelling {
        // A jar that cannot be read counts nothing here, and its upload refuses with the reason.
        bytes += tokio::fs::metadata(&jar.path).await.map_or(0, |metadata| metadata.len());
    }
    let uploads = travelling.into_iter().map(|jar| upload_jar(ctx, daemon, state, jar));
    let mut uploads = futures::stream::iter(uploads).buffer_unordered(upload_concurrency(bytes));
    let mut pushed = 0;
    while let Some(uploaded) = uploads.next().await {
        uploaded?;
        pushed += 1;
    }
    Ok(pushed)
}

/// Uploads one jar of the push.
async fn upload_jar(ctx: &Ctx, daemon: &DaemonClient, state: &HostState, jar: &PathDigest) -> Result<(), Refusal> {
    let content = tokio::fs::read(&jar.path)
        .await
        .map_err(|error| push_failed(format!("cannot read {} to upload it: {error}", jar.path)))?;
    let (status, _) = daemon
        .http(ctx, state, &wire::upload_jar(&jar.sha256), Some(content.into()))
        .await?;
    if !status.is_success() {
        return Err(push_failed(format!("uploading {} returned {}", jar.path, status.as_u16())));
    }
    Ok(())
}

fn push_failed(message: String) -> Refusal {
    Refusal::new("daemon_push_failed", Exit::SOFTWARE, message)
}

fn encode(value: &impl Serialize) -> Result<Vec<u8>, Refusal> {
    serde_json::to_vec(value).or_refuse("internal_error", Exit::FAILURE, || "cannot encode a daemon request".to_owned())
}

// --- the transport watchdog --------------------------------------------------------------------------------------

/// The controller's margin behind the daemon's own deadline, and behind the first record's.
const TRANSPORT_MARGIN: Duration = Duration::from_secs(90);
/// How long a run whose watchdog completed may take to deliver its last records.
const COMPLETED_MARGIN: Duration = Duration::from_secs(30);

/// How long from now the controller waits before it gives up on the stream, derived only from the daemon's
/// watchdog lifecycle records, or `None` for a record that carries no deadline.
///
/// A negative deadline is still a record this controller must not arm a timer from, and an absent one names none.
pub(crate) fn transport_deadline_after(event: &RunEvent) -> Option<Duration> {
    match &event.kind {
        RunEventKind::WatchdogExpired(_) => Some(TRANSPORT_MARGIN),
        RunEventKind::WatchdogState(state) if state.phase == "completed" => Some(COMPLETED_MARGIN),
        RunEventKind::WatchdogState(state) => state
            .next_deadline_in_ms
            .and_then(|milliseconds| u64::try_from(milliseconds).ok())
            .map(|milliseconds| Duration::from_millis(milliseconds) + TRANSPORT_MARGIN),
        _ => None,
    }
}

/// The controller-side abort: a monotonic deadline to sleep until, and the wall-clock instant a refusal names.
struct TransportWatchdog {
    deadline: Instant,
    abort_at: Timestamp,
}

impl TransportWatchdog {
    fn armed(delay: Duration) -> Self {
        let millis = i64::try_from(delay.as_millis()).unwrap_or(i64::MAX);
        Self {
            deadline: Instant::now() + delay,
            abort_at: Timestamp::now()
                .checked_add(SignedDuration::from_millis(millis))
                .unwrap_or(Timestamp::MAX),
        }
    }

    fn diagnostic(&self) -> String {
        format!(
            "the controller transport guard expired at {} after the daemon stopped advancing its JUnit watchdog",
            stamp(self.abort_at)
        )
    }
}

// --- the run itself ----------------------------------------------------------------------------------------------

/// Everything one daemon NDJSON stream said, which is what a verdict is derived from.
///
/// [`Host::execute_run`] never fails for a protocol failure - a stream that broke, a record it could not decode.
/// It answers it in `protocol_failure` and publishes a synthetic `protocolFailed` record, because the events read
/// before the break are the only evidence the run left and an early return drops them.
#[derive(Clone, Debug, Default)]
pub(crate) struct RunExecution {
    pub summary: Option<Summary>,
    pub failed: Option<RunFailed>,
    pub watchdog_expired: Option<WatchdogExpired>,
    pub events: Vec<RunEvent>,
    pub iteration_id: Option<String>,
    pub iteration_started_at: Option<String>,
    pub protocol_failure: Option<Refusal>,
}

/// The `/run` request. Kotlin's fields are non-nullable, so an empty selection is `[]` and never `null`.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunRequestBody<'a> {
    pub(crate) selectors: &'a [String],
    pub(crate) junit5_filters: &'a [String],
    pub(crate) product_stamp: &'a str,
    /// How the daemon rejects work prepared for a different immutable launch generation.
    pub(crate) launch_digest: &'a str,
    pub(crate) hot_jars: Vec<&'a str>,
    pub(crate) active_execution_timeout_sec: u64,
    pub(crate) progress_gap_timeout_sec: u64,
    /// The context plugin files, which the daemon installs into the plugin directory of the IDE context.
    pub(crate) ide_plugins: Vec<IdePlugin<'a>>,
}

/// One context plugin file of the `/run` request: its destination below the plugin directory of the IDE context, and
/// the digest that the push stored it under.
#[derive(Serialize)]
pub(crate) struct IdePlugin<'a> {
    pub(crate) destination: &'a str,
    pub(crate) sha256: &'a str,
}

impl Host {
    /// Streams one iteration out of the daemon, handing every record to `observe` as it arrives.
    ///
    /// The transport watchdog is armed before the request goes out: the request itself can hang on a daemon whose
    /// acceptor died, and a watchdog armed after the response arrived would never see that. Every record re-arms it
    /// through [`transport_deadline_after`], so the controller's deadline always trails the daemon's own by a
    /// margin - the daemon is the authority on how long a run may be quiet, and this guard only catches the
    /// transport failing to deliver the daemon's answer.
    pub(crate) async fn execute_run(
        &self,
        ctx: &Ctx,
        worker: &str,
        state: &HostState,
        prep: &PreparedBuild,
        selection: &RunSelection,
        policy: WatchdogPolicy,
        observe: &mut (dyn FnMut(&RunEvent) + Send),
    ) -> Result<RunExecution, Refusal> {
        let body = encode(&RunRequestBody {
            selectors: &selection.selectors,
            junit5_filters: &selection.junit5_filters,
            product_stamp: &prep.product_digest,
            launch_digest: &prep.launch_digest,
            hot_jars: prep.hot_jars.iter().map(|jar| jar.sha256.as_str()).collect(),
            active_execution_timeout_sec: policy.active_execution.as_secs(),
            progress_gap_timeout_sec: policy.progress_gap.as_secs(),
            ide_plugins: prep
                .plugin_files
                .iter()
                .map(|file| IdePlugin {
                    destination: &file.destination,
                    sha256: &file.host.sha256,
                })
                .collect(),
        })?;
        let mut watchdog = TransportWatchdog::armed(policy.progress_gap + TRANSPORT_MARGIN);
        let run_failed = |message: String| Refusal::new("daemon_run_failed", Exit::SOFTWARE, message);
        let run = wire::RUN;
        let sent = self.daemon.send(state, &run, body.into());
        let response = tokio::select! {
            response = sent => response.map_err(|failure| transport_refusal(state, &run, &failure))?,
            () = sleep_until(watchdog.deadline) => {
                return Err(Refusal::new("daemon_transport_timeout", Exit::SOFTWARE, watchdog.diagnostic()));
            }
            () = ctx.cancelled() => return Err(interrupted()),
        };
        let status = response.status();
        if status == hyper::StatusCode::CONFLICT {
            let body = response
                .into_body()
                .collect()
                .await
                .or_refuse("daemon_run_failed", Exit::SOFTWARE, || {
                    "cannot read the daemon's 409 body".to_owned()
                })?
                .to_bytes();
            let refused = wire::decode_conflict(&body).map_err(protocol_refusal)?;
            return Err(conflict_refusal(&refused, &prep.launch_digest));
        }
        if !status.is_success() {
            return Err(run_failed(format!("/run returned {}", status.as_u16())));
        }

        // Every line this stream produces is attributed, because with several workers streaming at once an
        // unattributed `▶ FooTest#bar` names neither the machine that is running it nor the shard it belongs to.
        let scope = Scope::worker(worker);
        let mut decoder = wire::NdjsonDecoder::default();
        let mut execution = RunExecution::default();
        let mut body = response.into_body();
        // The daemon can vanish in the middle of its own stream: its watchdog exits when a run thread survives the
        // IDE kill after a lifecycle deadline expires. That arrives here as a read error, and reporting it verbatim
        // tells the caller nothing about what to do next - so a broken connection is turned into an answer. The
        // decoder splits on the newline byte alone, so a UTF-8 sequence split across two chunks stays whole.
        let failure = loop {
            let frame = tokio::select! {
                frame = body.frame() => frame,
                () = sleep_until(watchdog.deadline) => break Some(stream_timed_out(&watchdog.diagnostic())),
                () = ctx.cancelled() => break Some(interrupted()),
            };
            let bytes = match frame {
                None => break None,
                // A trailers frame carries no records.
                Some(Ok(frame)) => match frame.into_data() {
                    Ok(bytes) => bytes,
                    Err(_trailers) => continue,
                },
                Some(Err(error)) => break Some(daemon_died(&state.run_id, &error_chain(&error))),
            };
            let decoded = decoder.push(&bytes);
            for event in decoded.events {
                if let Some(delay) = transport_deadline_after(&event) {
                    watchdog = TransportWatchdog::armed(delay);
                }
                // One publish per record. The terminal, `--stream` and every sink derive their own form from it,
                // so the line a person reads and the record an agent parses are one fact.
                self.reporter.publish(Event::Daemon(event.clone()), Some(&scope));
                observe(&event);
                execution.absorb(event);
            }
            if let Some(failure) = decoded.failure {
                break Some(protocol_refusal(failure));
            }
        };
        if let Some(failure) = &failure {
            // No line for a person: the refusal the command answers states the same failure.
            let data = protocol_failed_record(failure, execution.iteration_id.as_deref());
            self.reporter.publish(Event::Structured { data, line: String::new() }, Some(&scope));
        }
        execution.protocol_failure = failure;
        execution.summary = wire::find_summary(&execution.events).cloned();
        Ok(execution)
    }
}

// --- the decisions of a run stream: values in, an answer out, no I/O --------------------------------------------

impl RunExecution {
    /// Takes one record of the stream: the identity of the iteration from `runStarted`, the watchdog expiry, the
    /// failure, and the record itself as evidence.
    pub(crate) fn absorb(&mut self, event: RunEvent) {
        match &event.kind {
            RunEventKind::RunStarted(started) => {
                self.iteration_id = Some(started.iteration_id.clone());
                self.iteration_started_at = Some(started.started_at.clone());
            }
            RunEventKind::WatchdogExpired(expired) => self.watchdog_expired = Some(expired.clone()),
            RunEventKind::RunFailed(failed) => self.failed = Some(failed.clone()),
            _ => {}
        }
        self.events.push(event);
    }
}

/// The refusal of a `/run` that the daemon answered 409, by the reason it named. `wanted_launch` is the launch
/// digest of the build this run prepared.
pub(crate) fn conflict_refusal(refused: &wire::Conflict, wanted_launch: &str) -> Refusal {
    match refused.reason.as_str() {
        conflict::STALE_DAEMON => Refusal::new(
            "daemon_stale",
            Exit::TEMP_FAIL,
            format!(
                "the running fast daemon was booted from a different build than the one just prepared (it serves {}, \
                 this run wants {wanted_launch}); the host state file is out of date — recover with: daemon restart",
                refused.launch_digest.as_deref().unwrap_or("an unnamed generation"),
            ),
        ),
        // Distinct from busy: the shares are unmounted, so the next move is to resume them rather than wait.
        conflict::MOUNT_QUIESCED => Refusal::new(
            "daemon_mount_quiesced",
            Exit::TEMP_FAIL,
            "the daemon has its shares quiesced and cannot start a run; recover with: daemon restart",
        ),
        reason => Refusal::new("daemon_busy", Exit::TEMP_FAIL, format!("the daemon rejected the run: {reason}")),
    }
}

/// The refusal of a stream whose transport watchdog expired, with what the watchdog last saw.
fn stream_timed_out(diagnostic: &str) -> Refusal {
    Refusal::new(
        "daemon_transport_timeout",
        Exit::SOFTWARE,
        format!("{diagnostic}; XML is not a liveness signal. Read the daemon tail with `daemon log`."),
    )
}

/// The refusal of a stream that broke in the middle of the run, which is what a daemon that exited looks like: its
/// watchdog exits when a run thread survives the IDE kill after a lifecycle deadline expires.
pub(crate) fn daemon_died(run_id: &str, error: &str) -> Refusal {
    Refusal::new(
        "daemon_died",
        Exit::SOFTWARE,
        format!(
            "the daemon ({run_id}) stopped answering in the middle of the run: {error}\nits watchdog exits when a run \
             outlives its timeout and the run thread survives the IDE kill; read its tail with `daemon log`. The next \
             `run` starts a new daemon by itself."
        ),
    )
}

/// The synthetic `protocolFailed` record of a stream that ended in `failure`, with the iteration when the stream
/// named it.
pub(crate) fn protocol_failed_record(failure: &Refusal, iteration_id: Option<&str>) -> serde_json::Value {
    let mut data = json!({
        "event": "protocolFailed",
        "code": failure.code,
        "message": failure.message,
    });
    if let Some(iteration_id) = iteration_id {
        data["iterationId"] = json!(iteration_id);
    }
    data
}

fn interrupted() -> Refusal {
    Refusal::new(
        "run_interrupted",
        Exit::SOFTWARE,
        "the run was interrupted while the daemon streamed it",
    )
}
