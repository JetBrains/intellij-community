//! One invocation end to end: resolve, spawn bazel, read the BEP and test.xml, answer the digest and the payload.

use std::ffi::OsString;
use std::path::{Path, PathBuf};

use bt_core::areas::{Area, Areas};
use bt_core::{Refusal, affected_text, exit, fail_usage, resolve_module};
use serde::{Deserialize, Serialize};

use crate::build::execute_build;
use crate::check::{Check, CheckCommand, record_green_run, tree_before_run};
use crate::digest::{
    DEFAULT_MAX_FRAMES, RenderOptions, failure_detail, failure_message, failure_type, format_duration, render_digest, rerun_selector,
    tail_lines, trim_frames,
};
use crate::options::{Args, Invocation, parse_args};
use crate::prepare::{Prepare, prepare_host};
use crate::remote::bd_host::BdHost;
use crate::remote::{Host, HostLink, run_on_host};
use bt_core::bep::{BepSummary, parse_bep};
use bt_core::evidence::keep_failed_evidence;
use bt_core::lanes::{
    BEP_FLAG, LaneSpec, RunPlan, bazel_command, bep_override, build_bazel_args, default_shards, uses_shard_plan, wildcard_guards,
};
use bt_core::os_runtime::command_line;
use bt_core::result::{FlakyRun, RunResult, RunStatus, TargetResult, TargetTiming, collect_results};
use bt_core::runtime::{Runtime, SpawnResult, repo_file};
use bt_core::scan::{ResolutionInputs, derive_package, read_text};
use bt_core::selector::{
    Resolution, Selector, SelectorKind, as_filter_or_package, checkout_label, directory_pattern, resolve_selector, suggest_names,
    wanted_simple_name,
};
use bt_core::shard_plan::{PROFILE_FILE, SHARD_PLAN_FILE, ShardPlan};
use bt_core::suites::{named_suites, resolve_suite_run};

/// What one invocation answers.
///
/// `text` and `json` are both filled for a `--json` run, and the caller writes the payload to stdout and the digest
/// to stderr. That split is the contract: a caller reading stdout gets exactly one JSON object whether or not the
/// run failed, and never has to know whether a digest was printed.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Outcome {
    pub(crate) exit_code: u8,
    pub(crate) text: String,
    /// `None` unless `--json` was passed, because "no payload was asked for" and "an empty payload" are different
    /// answers and only the first may leave stdout untouched.
    pub(crate) json: Option<JsonPayload>,
}

/// One failure in the published payload.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct JsonFailure {
    pub(crate) class_name: String,
    pub(crate) name: String,
    #[serde(rename = "type", default, skip_serializing_if = "Option::is_none")]
    pub(crate) failure_type: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) message: Option<String>,
    /// Always present, empty when the failure carried no stack: a caller iterating it must not have to distinguish
    /// absent from empty.
    pub(crate) frames: Vec<String>,
    pub(crate) frames_omitted: usize,
}

/// The `--json` object, and the STABLE half of this tool's contract. The text digest may be reformatted freely;
/// this may not. Field order here is the key order on the wire, and every list is `[]` rather than absent.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct JsonPayload {
    pub(crate) status: RunStatus,
    pub(crate) exit_code: u8,
    pub(crate) duration_ms: u64,
    /// The per-target breakdown is in `targets`; these two are the run-level counters worth trending.
    pub(crate) cached_targets: usize,
    pub(crate) ran_targets: usize,
    pub(crate) targets: Vec<TargetResult>,
    pub(crate) failures: Vec<JsonFailure>,
    pub(crate) flaky: Vec<FlakyRun>,
    pub(crate) errors: Vec<String>,
    pub(crate) degraded: bool,
}

/// The published payload of a run.
pub(crate) fn to_json(result: &RunResult) -> JsonPayload {
    let cached = result.targets.iter().filter(|target| target.cached).count();
    JsonPayload {
        status: result.status,
        exit_code: result.exit_code,
        duration_ms: result.duration_ms,
        cached_targets: cached,
        ran_targets: result.targets.len() - cached,
        targets: result.targets.clone(),
        failures: result
            .failures
            .iter()
            .map(|failure| {
                let stack = trim_frames(failure_detail(failure), DEFAULT_MAX_FRAMES);
                JsonFailure {
                    class_name: failure.class_name.clone(),
                    name: failure.name.clone(),
                    failure_type: failure_type(failure).map(str::to_owned),
                    message: failure_message(failure).map(str::to_owned),
                    frames: stack.frames,
                    frames_omitted: stack.omitted,
                }
            })
            .collect(),
        flaky: result.flaky.clone(),
        errors: result.build_errors.clone(),
        degraded: result.degraded,
    }
}

/// How much of bazel's captured output an INFRA outcome shows. Enough to carry a stack trace or a startup refusal,
/// and far short of the progress log the digest exists to suppress.
pub(crate) const DIGEST_TAIL_LINES: usize = 40;

/// The command a rerun hint names.
const WRAPPER: &str = "./community/tools/bt.cmd";

/// Runs one command line (without the program name) end to end.
///
/// It never exits the process and never writes stdout itself: the digest and the payload come back for the caller
/// to write, which is what lets the whole suite, a full run included, be exercised in process.
///
/// The areas are read first, because the help lists their lanes. A `bt.json` that is there and broken is refused
/// before anything else, since every answer below would depend on it.
pub(crate) fn execute<I, T>(argv: I, runtime: &dyn Runtime) -> Result<Outcome, Refusal>
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    execute_with(argv, runtime, &BdHost::new(runtime))
}

/// [`execute`] with the link to the agent of a `--host` machine, which the tests script.
pub(crate) fn execute_with<I, T>(argv: I, runtime: &dyn Runtime, link: &dyn HostLink) -> Result<Outcome, Refusal>
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    let argv: Vec<OsString> = argv.into_iter().map(Into::into).collect();
    let areas = Areas::load(runtime)?;
    match parse_args(argv.iter().cloned(), &areas)? {
        Invocation::Help { text, exit } => Ok(Outcome {
            exit_code: exit,
            text,
            json: None,
        }),
        Invocation::Build(args) => match &args.host {
            Some(alias) => {
                let host = Host {
                    alias,
                    repo: args.host_repo.as_deref(),
                };
                run_on_host(&host, CheckCommand::Build, args.json, &argv, runtime, link)
            }
            None => execute_build(&args, runtime),
        },
        Invocation::Run(args) => match &args.host {
            Some(alias) => {
                let host = Host {
                    alias,
                    repo: args.host_repo.as_deref(),
                };
                if args.prepare {
                    let prepare = Prepare {
                        change: !args.dry_run,
                        yes: args.yes,
                    };
                    return Ok(prepare_host(&host, &prepare, runtime, link));
                }
                if args.selector.is_none() && args.module.is_none() && args.lane.is_none() {
                    return Err(fail_usage("Pass a selector, --module or --lane; see --help"));
                }
                run_on_host(&host, CheckCommand::Test, args.json, &argv, runtime, link)
            }
            None if args.prepare => Err(fail_usage("--prepare needs --host")),
            None => execute_args(&args, runtime, &areas),
        },
    }
}

/// Runs one parsed invocation.
pub(crate) fn execute_args(args: &Args, runtime: &dyn Runtime, areas: &Areas) -> Result<Outcome, Refusal> {
    let selector = match &args.module {
        Some(module) => Some(Selector::new(SelectorKind::Module, module)),
        None => args.selector.as_deref().map(Selector::classify).transpose()?,
    };
    let lane_name = args.lane.as_deref();
    match (&selector, lane_name) {
        // A flow or a suite selector takes `--lane` as the answer to the two-lane refusal, which narrows the
        // selector rather than naming a second run.
        (Some(selector), Some(_)) if !selector.kind.names_suites() => {
            return Err(fail_usage("A selector and --lane are mutually exclusive"));
        }
        (None, None) => return Err(fail_usage("Pass a selector, --module or --lane; see --help")),
        _ => {}
    }
    let named_lane = match lane_name {
        Some(name) => Some(
            areas
                .lane(name)?
                .ok_or_else(|| fail_usage(format!("Unknown lane: {name}. Known lanes: {}", areas.lane_names().join(", "))))?,
        ),
        None => None,
    };
    let explicit_filter = args.filter.as_deref();

    let mut lane: Option<(&Area, &LaneSpec)> = None;
    let mut extra: Vec<String> = Vec::new();
    let resolution = match &selector {
        None => {
            // Checked above: a run without a selector names a lane an area declares.
            let Some((area, spec)) = named_lane else {
                return Err(fail_usage("Pass a selector or --lane; see --help"));
            };
            lane = Some((area, spec));
            // A lane's flags are its definition, including `all`, which deliberately adds none.
            extra = spec.extra.clone();
            let multi_target = spec.is_multi_target();
            if explicit_filter.is_some() && multi_target {
                return Err(fail_usage(format!(
                    "--filter cannot be combined with the wildcard pattern of lane {}",
                    spec.name
                )));
            }
            let (filter, include_package) = as_filter_or_package(explicit_filter);
            Resolution {
                labels: spec.targets.clone(),
                filter,
                include_package,
                multi_target,
                ..Resolution::default()
            }
        }
        Some(selector) => {
            if explicit_filter.is_some() && !matches!(selector.kind, SelectorKind::Label | SelectorKind::Module) {
                return Err(fail_usage(
                    "--filter is only valid with an explicit label (//pkg:target or @repo//pkg:target) or --module; a name selector already implies a filter",
                ));
            }
            let inputs = ResolutionInputs::new(runtime, areas);
            if args.list {
                return Ok(Outcome {
                    exit_code: exit::GREEN,
                    text: list_candidates(runtime, areas, selector, &inputs)?,
                    json: None,
                });
            }
            let mut resolution = if selector.kind.names_suites() {
                resolve_suite_run(runtime, areas.with_catalog()?, selector, lane_name)?
            } else {
                resolve_selector(runtime, selector, &inputs)?
            };
            if explicit_filter.is_some() {
                if resolution.multi_target {
                    return Err(fail_usage("--filter cannot be combined with a wildcard target pattern"));
                }
                // Only the field the value can be expressed in is replaced; the other keeps whatever resolution
                // put there.
                match as_filter_or_package(explicit_filter) {
                    (Some(filter), _) => resolution.filter = Some(filter),
                    (None, Some(package)) => resolution.include_package = Some(package),
                    (None, None) => {}
                }
            }
            let suite_lane = match resolution.lane.as_deref() {
                Some(name) => {
                    let area = areas.with_catalog()?;
                    area.lanes().get(name).map(|spec| (area, spec))
                }
                None => None,
            };
            if let Some((area, spec)) = suite_lane {
                // The lane the suites belong to owns the flags and the shard count, as `--lane` would: a UI lane
                // launches one IDE, so it pins one shard.
                lane = Some((area, spec));
                extra = spec.extra.clone();
            } else if resolution.multi_target {
                extra = wildcard_guards(areas, &resolution.labels);
            }
            resolution
        }
    };

    let overridden_bep = bep_override(&args.passthrough);
    let lane_spec = lane.map(|(_, spec)| spec);
    let shards = args.shards.unwrap_or_else(|| default_shards(&resolution, lane_spec));
    let planned = args.shards.is_none() && uses_shard_plan(&resolution, lane_spec);
    let shard_plan_path = repo_file(runtime, SHARD_PLAN_FILE);
    let path_text = |path: &PathBuf| path.to_string_lossy().into_owned();
    let profile = resolution.multi_target.then(|| path_text(&repo_file(runtime, PROFILE_FILE)));
    let owns_bep_file = overridden_bep.is_none();
    let bep_path = overridden_bep.unwrap_or_else(|| runtime.temp_file("air-bazel-test-bep").to_string_lossy().into_owned());
    // The file is ours from the moment it is named, because the OS runtime creates it to reserve the name: it goes
    // away on every way out, a dry run, a failed excluded build and a panic included. A leaked BEP file is tens of
    // MB for a lane run.
    let _bep_file = owns_bep_file.then(|| BepFile {
        runtime,
        path: PathBuf::from(&bep_path),
    });
    let method_filtered = resolution.filter.as_deref().is_some_and(|filter| filter.contains('#'));
    let plan = RunPlan {
        resolution,
        extra,
        shards,
        bep_path,
        owns_bep_file,
        no_cache: args.no_cache,
        test_env: args.test_env.clone(),
        passthrough: args.passthrough.clone(),
        // Relative to the checkout: the value is a part of the key of each test action, and the checkouts share a cache.
        shard_plan: planned.then(|| SHARD_PLAN_FILE.to_owned()),
        profile,
    };
    let bazel_args = build_bazel_args(&plan);
    let command = bazel_command(runtime.repo_root(), runtime.platform(), &bazel_args);
    let build_only = lane.and_then(|(area, spec)| spec.build_only_args(area.lanes()));

    if args.dry_run {
        return Ok(Outcome {
            exit_code: exit::GREEN,
            text: dry_run_text(runtime, &plan.resolution, &command, build_only.as_deref()),
            json: None,
        });
    }

    // Before the first spawn: a green run records this tree only when the files are the same after it.
    let tree_before = tree_before_run(runtime);

    // Compile what the lane excludes from its run, before the run, so a compile break is the answer rather than a
    // long green lane that proved nothing about those targets.
    if let Some(build_only) = &build_only
        && let Some(failed) = build_excluded_targets(runtime, build_only)
    {
        return Ok(failed);
    }

    // The directory of the profile, and the plan the tests read. A run before the first record splits each target
    // into the default count, by class.
    if plan.profile.is_some() && !runtime.exists(&shard_plan_path) {
        update_shard_plan(runtime, &shard_plan_path, &[]);
    }

    let BazelRun { spawned, bep, result } = run_bazel(runtime, &command, &bazel_args, &plan.bep_path);

    // Before anything else reads the run: the next run of a failed target overwrites its logs.
    let kept_run = keep_failed_evidence(runtime, &bep);

    if planned {
        // A filtered run times only some classes of a target, and they must not replace its record.
        let timings = if runs_all_classes(&plan) { &result.timings[..] } else { &[] };
        update_shard_plan(runtime, &shard_plan_path, timings);
    }

    if args.verbose {
        runtime.write_error(&spawned.output);
    }
    if result.status == RunStatus::Infra {
        // The only status where bazel's own words are the sole evidence: nothing structural was produced.
        runtime.write_error(&tail_lines(&spawned.output, DIGEST_TAIL_LINES));
    }
    if result.exit_code == exit::GREEN {
        let patterns = checked_patterns(&plan.resolution);
        let check = Check {
            command: CheckCommand::Test,
            patterns: &patterns,
            host: None,
        };
        record_green_run(runtime, tree_before.as_ref(), &check);
    }

    // The rerun selector rather than the test case id, and quoted rather than wrapped in bare single quotes: a
    // Kotlin backticked name holds apostrophes, and a display name is no selector at all.
    let rerun_hint = result
        .failures
        .first()
        .map(|failure| format!("{WRAPPER} {}", posix_shell_quote(&rerun_selector(failure))));
    let text = render_digest(
        &result,
        &RenderOptions {
            max_failures: usize::try_from(args.max_failures).unwrap_or(usize::MAX),
            rerun_hint,
            method_filtered,
            kept_run,
        },
    );
    Ok(Outcome {
        exit_code: result.exit_code,
        json: args.json.then(|| to_json(&result)),
        text,
    })
}

/// What the check record of a test run names: the labels, then `--filter <value>` when the run took only some
/// classes, so a reader sees that `bt FooTest#one` ran one method and not the target.
fn checked_patterns(resolution: &Resolution) -> Vec<String> {
    let mut patterns = resolution.labels.clone();
    if let Some(filter) = filter_text(resolution) {
        patterns.extend(["--filter".to_owned(), filter]);
    }
    patterns
}

/// The test filter of a run as one value: a class or a method, a package, or the JUnit 5 filters of the suites.
fn filter_text(resolution: &Resolution) -> Option<String> {
    match (&resolution.filter, &resolution.include_package) {
        (Some(filter), _) => Some(filter.clone()),
        (None, Some(package)) => Some(package.clone()),
        (None, None) if !resolution.junit5_filters.is_empty() => Some(resolution.junit5_filters.join(";")),
        (None, None) => None,
    }
}

/// One bazel spawn, and what it produced.
pub(crate) struct BazelRun {
    pub(crate) spawned: SpawnResult,
    pub(crate) bep: BepSummary,
    pub(crate) result: RunResult,
}

/// Echoes the command line on the stderr, runs bazel with a heartbeat, and reads its BEP and its results.
///
/// The BEP path is stripped from the echo: it is a temporary file nobody can act on, and it would make every echoed
/// command line differ from the last.
pub(crate) fn run_bazel(runtime: &dyn Runtime, command: &[OsString], bazel_args: &[String], bep_path: &str) -> BazelRun {
    let echoed: Vec<&str> = bazel_args
        .iter()
        .map(String::as_str)
        .filter(|argument| !argument.strip_prefix(BEP_FLAG).is_some_and(|rest| rest.starts_with('=')))
        .collect();
    runtime.write_error(&format!("> bazel {}", echoed.join(" ")));
    let started = runtime.now_ms();
    let heartbeat = |elapsed: u64| {
        runtime.write_error(&format!("  … still running, {} elapsed", format_duration(elapsed)));
    };
    let spawned = runtime.spawn(command, Some(&heartbeat));
    let bep = parse_bep(runtime.read_lines(Path::new(bep_path)), runtime.platform());
    let result = collect_results(
        runtime,
        &bep,
        runtime.now_ms().saturating_sub(started),
        spawned.exit_code,
        &spawned.output,
    );
    BazelRun { spawned, bep, result }
}

/// Whether the run gives each target all its classes: no test filter of bt, of a suite or of the passthrough.
fn runs_all_classes(plan: &RunPlan) -> bool {
    let resolution = &plan.resolution;
    resolution.filter.is_none()
        && resolution.include_package.is_none()
        && resolution.junit5_filters.is_empty()
        && !plan.test_env.iter().any(|name| name.starts_with("JB_TEST_JUNIT5_FILTERS"))
        && !plan
            .passthrough
            .iter()
            .any(|argument| argument.starts_with("--test_filter") || argument.contains("JB_TEST_JUNIT5_FILTERS"))
}

/// Records the class times of a run in the shard plan file.
///
/// A plan that cannot be written is a warning, not a refusal: the plan steers the speed of the next run, and the
/// verdict of this one is already decided.
fn update_shard_plan(runtime: &dyn Runtime, path: &Path, timings: &[TargetTiming]) {
    let mut plan = runtime.read_text_file(path).map(|text| ShardPlan::parse(&text)).unwrap_or_default();
    for timing in timings {
        plan.record(timing);
    }
    if let Err(error) = runtime.write_text_file(path, &plan.render()) {
        runtime.write_error(&format!("cannot write the shard plan {}: {error}", path.display()));
    }
}

/// A value quoted for a POSIX shell, so a rerun hint can be pasted as it stands.
pub(crate) fn posix_shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', r"'\''"))
}

/// Removes a BEP file this invocation asked bazel for, on every way out of the run.
pub(crate) struct BepFile<'r> {
    pub(crate) runtime: &'r dyn Runtime,
    pub(crate) path: PathBuf,
}

impl Drop for BepFile<'_> {
    fn drop(&mut self) {
        // Nothing to do about a file that cannot be removed: the answer is already decided, and it is a temp file.
        let _ = self.runtime.remove(&self.path);
    }
}

fn dry_run_text(runtime: &dyn Runtime, resolution: &Resolution, command: &[OsString], build_only: Option<&[String]>) -> String {
    let first = resolution.labels.first().map_or("", String::as_str);
    let suffix = match resolution.labels.len() {
        0 | 1 => String::new(),
        count => format!(" (+{} more)", count - 1),
    };
    let filter = filter_text(resolution).unwrap_or_else(|| "(none)".to_owned());
    let mut lines = vec![format!("label   {first}{suffix}")];
    lines.extend(
        resolution
            .suites
            .iter()
            .map(|suite| format!("suite   {} ({}, lane {})", suite.class, suite.suite, suite.lane)),
    );
    lines.push(format!("filter  {filter}"));
    lines.push(format!("argv    {}", command_line(command)));
    if let Some(build_only) = build_only {
        lines.push(format!(
            "build   {}",
            command_line(&bazel_command(runtime.repo_root(), runtime.platform(), build_only))
        ));
    }
    lines.join("\n")
}

/// Runs a lane's extra `bazel build`, and answers the outcome when it failed.
///
/// A build failure here is [`exit::BUILD_FAILED`] like any other: nothing ran, and the reason is a compile or an
/// analysis error. Bazel's own words are the only evidence a build spawn produces, so the tail is printed.
fn build_excluded_targets(runtime: &dyn Runtime, args: &[String]) -> Option<Outcome> {
    runtime.write_error(&format!("> bazel {}", args.join(" ")));
    let heartbeat = |elapsed: u64| {
        runtime.write_error(&format!("  … still building, {} elapsed", format_duration(elapsed)));
    };
    let spawned = runtime.spawn(&bazel_command(runtime.repo_root(), runtime.platform(), args), Some(&heartbeat));
    if spawned.exit_code == 0 {
        return None;
    }
    runtime.write_error(&tail_lines(&spawned.output, DIGEST_TAIL_LINES));
    Some(Outcome {
        exit_code: exit::BUILD_FAILED,
        text: format!(
            "BUILD FAILED  the targets this lane excludes from its run do not compile. They were built by `bazel {}`",
            args.join(" ")
        ),
        json: None,
    })
}

/// What `--list` prints: everything resolution would have considered, without running anything. It is the escape
/// hatch for an ambiguity the refusal text cannot fully explain.
fn list_candidates(runtime: &dyn Runtime, areas: &Areas, selector: &Selector, inputs: &ResolutionInputs<'_>) -> Result<String, Refusal> {
    match selector.kind {
        SelectorKind::Label | SelectorKind::Pattern => {
            return Ok(format!("label   {}", checkout_label(runtime, &selector.name)));
        }
        SelectorKind::Dir => {
            return Ok(format!("label   {}", directory_pattern(runtime, &selector.name)?));
        }
        SelectorKind::Module => {
            let resolution = resolve_module(runtime, areas, &selector.name)?;
            return Ok(format!("label   {}", resolution.labels.join(" ")));
        }
        // Every lane the selector names, before `--lane` narrows it, so a two-lane refusal can be read in full.
        SelectorKind::Flow | SelectorKind::Suite => {
            return Ok(affected_text(&named_suites(runtime, areas.with_catalog()?, selector)?));
        }
        SelectorKind::Package => {
            let lines: Vec<String> = inputs
                .roots()?
                .iter()
                .filter(|root| {
                    root.package_prefix.as_deref().is_some_and(|prefix| {
                        selector.name == prefix || selector.name.strip_prefix(prefix).is_some_and(|rest| rest.starts_with('.'))
                    })
                })
                .map(|root| format!("{}\n    {}", root.label, root.src_dir))
                .collect();
            if lines.is_empty() {
                return Ok(format!("(no test root declares a prefix covering {})", selector.name));
            }
            return Ok(lines.join("\n"));
        }
        SelectorKind::SimpleName | SelectorKind::Fqn => {}
    }

    let index = inputs.index()?;
    let simple_name = wanted_simple_name(selector);
    let Some(candidates) = index.get(simple_name).filter(|found| !found.is_empty()) else {
        let suggestions = suggest_names(simple_name, index.keys().map(String::as_str), 3);
        if suggestions.is_empty() {
            return Ok("(no candidates)".to_owned());
        }
        return Ok(format!("(no candidates)\nsimilar: {}", suggestions.join(", ")));
    };
    let mut lines = Vec::new();
    for candidate in candidates {
        let text = read_text(runtime, &candidate.file)?;
        lines.push(format!(
            "{}.{}\n    {}\n    {}",
            derive_package(&text),
            candidate.simple_name,
            candidate.root.label,
            candidate.file
        ));
    }
    Ok(lines.join("\n"))
}

#[cfg(test)]
mod tests;
