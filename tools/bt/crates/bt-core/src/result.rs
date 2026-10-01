//! Result collection: the BEP says which test.xml files to open, the files say what actually ran, and one decision
//! table turns the whole into a status and an exit code.

use std::collections::{HashMap, HashSet};
use std::path::Path;

use bt_junit::{self as junit, TestCase};
use serde::{Deserialize, Serialize};

use crate::bep::{BepAttempt, BepSummary};
use crate::exit;
use crate::runtime::Runtime;

/// The verdict a run gets. It pairs one-for-one with an exit code, and [`classify_run`] is the only place that
/// decides either.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RunStatus {
    Pass,
    Fail,
    NoTests,
    BuildFailed,
    Infra,
}

/// One bazel test target's outcome, aggregated over its shards and attempts.
///
/// The serde names are the `--json` contract: this struct is published verbatim as the payload's `targets`, so a
/// field rename here is a breaking change for a caller nobody can recompile.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TargetResult {
    pub label: String,
    pub tests: u32,
    pub failed: u32,
    pub skipped: u32,
    pub flaky: u32,
    pub cached: bool,
    pub shards: u32,
    /// The target's own wall time: its slowest shard, since shards run concurrently. Their sum would report a 30 s
    /// target as costing three minutes.
    pub duration_ms: u64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub log_path: Option<String>,
    /// The runner's own exit code, when bazel had to synthesize the report because the runner wrote none. 42 is
    /// "the filter matched no test class", which is why absent and 0 must stay distinguishable.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub wrapper_exit_code: Option<i32>,
}

/// One target or shard that passed only on a retry.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct FlakyRun {
    pub id: String,
    pub attempts: u32,
}

/// Everything one bazel run produced.
#[derive(Clone, Debug, PartialEq)]
pub struct RunResult {
    pub status: RunStatus,
    pub exit_code: u8,
    pub duration_ms: u64,
    pub targets: Vec<TargetResult>,
    pub failures: Vec<TestCase>,
    pub flaky: Vec<FlakyRun>,
    pub build_errors: Vec<String>,
    /// The details are incomplete: a failed attempt whose test.xml was not there, or a BEP file with no events. The
    /// digest says so, and the classifier can turn it into [`RunStatus::Infra`], because a partial reading must
    /// never be reported as a confident green.
    pub degraded: bool,
}

/// The per-target counters, summed.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Totals {
    pub tests: u32,
    pub failed: u32,
    pub skipped: u32,
    pub flaky: u32,
}

pub fn totals_of(targets: &[TargetResult]) -> Totals {
    targets.iter().fold(Totals::default(), |sum, target| Totals {
        tests: sum.tests + target.tests,
        failed: sum.failed + target.failed,
        skipped: sum.skipped + target.skipped,
        flaky: sum.flaky + target.flaky,
    })
}

/// What the decision table reads. A struct rather than five parameters, because every one of them is consulted and
/// the order they are passed in must not be something a caller can get wrong.
#[derive(Clone, Copy, Debug)]
pub struct ClassifyInput<'a> {
    pub bazel_exit: i32,
    pub bep: &'a BepSummary,
    pub targets: &'a [TargetResult],
    pub build_errors: &'a [String],
    pub degraded: bool,
}

/// The status and exit code of a finished run.
///
/// Zero executed tests must never read as green. Bazel's own exit code cannot express it: a filter that matches
/// nothing exits the runner with 42, which bazel reports as an ordinary test failure, while a lane whose tag filter
/// matches no target exits 4 with nothing run at all.
///
/// The order of the branches is the table, and it is not arbitrary. Infrastructure comes first because a bazel that
/// never started says nothing about the tests. Build failures come before the test count because a target that did
/// not compile has no test count to be zero. `NO_TESTS` comes before failure counting because a runner that matched
/// nothing exits 3, and reading that as a test failure is a red run nobody can reproduce.
pub fn classify_run(input: ClassifyInput<'_>) -> (RunStatus, u8) {
    let sum = totals_of(input.targets);
    // 8 is bazel's own "command failed", 33 is "out of memory", 127 is "wrapper not found".
    if matches!(input.bazel_exit, 8 | 33 | 127) || (input.degraded && input.targets.is_empty()) {
        return (RunStatus::Infra, exit::INFRA);
    }
    if input.bep.aborted.iter().any(|reason| reason == "ANALYSIS_FAILURE")
        || !input.bep.failed_to_build.is_empty()
        || !input.build_errors.is_empty()
        || input.bazel_exit == 1
    {
        return (RunStatus::BuildFailed, exit::BUILD_FAILED);
    }
    // Zero tests covers both an all-Bucketing sharding and an entirely assumeTrue-skipped lane.
    if input.bazel_exit == 4 || filter_matched_no_class(input.targets) || sum.tests == 0 {
        return (RunStatus::NoTests, exit::NO_TESTS);
    }
    if sum.failed > 0 || input.bazel_exit == 3 {
        return (RunStatus::Fail, exit::TEST_FAILED);
    }
    if input.bazel_exit != 0 {
        return (RunStatus::Infra, exit::INFRA);
    }
    (RunStatus::Pass, exit::GREEN)
}

/// Whether any target's runner exited 42, "the test filter matched no test class". [`classify_run`] turns it into
/// NO_TESTS and the digest names it as the cause; both must agree.
pub fn filter_matched_no_class(targets: &[TargetResult]) -> bool {
    targets.iter().any(|target| target.wrapper_exit_code == Some(42))
}

/// The repository's out/bazel-testlogs spelling of the absolute execroot path bazel reports, so the path in a
/// digest is one an agent can open. A path with no `testlogs` segment is answered as it was.
///
/// Either separator, because a Windows BEP reports a backslash path: a marker spelled one way would never match it,
/// and the digest would then name a path under bazel's output base instead of one in the checkout. The answer is
/// forward-slash on both platforms, which Windows opens and which keeps one spelling in a digest an agent reads.
pub fn relativize_testlogs(path: &str) -> String {
    let normalized = path.replace('\\', "/");
    match normalized.split_once("/testlogs/") {
        Some((_, relative)) => format!("out/bazel-testlogs/{relative}"),
        None => path.to_owned(),
    }
}

/// Strips the sandbox prefix off a compiler-reported path so it is clickable in the repo.
pub fn relativize_source_path(path: &str) -> &str {
    path.split_once("/execroot/_main/").map_or(path, |(_, relative)| relative)
}

/// One target under construction. The shard count is a set while it is built: the same shard can report several
/// attempts, and counting attempts as shards would make a flaky target look wider than it is.
struct Aggregate {
    label: String,
    tests: u32,
    failed: u32,
    skipped: u32,
    flaky: u32,
    cached: bool,
    shards: HashSet<(i64, i64)>,
    duration_ms: u64,
    log_path: Option<String>,
    wrapper_exit_code: Option<i32>,
}

/// Reads a finished run.
pub fn collect_results(runtime: &dyn Runtime, bep: &BepSummary, duration_ms: u64, bazel_exit: i32, bazel_output: &str) -> RunResult {
    // The last attempt per (label, run, shard) decides the outcome; earlier ones only signal flakiness. Groups keep
    // their first-seen order, because the `targets` array is part of the published payload.
    let mut groups: Vec<Vec<&BepAttempt>> = Vec::new();
    let mut group_of: HashMap<(&str, i64, i64), usize> = HashMap::new();
    for attempt in &bep.attempts {
        let key = (attempt.label.as_str(), attempt.run, attempt.shard);
        let index = *group_of.entry(key).or_insert_with(|| {
            groups.push(Vec::new());
            groups.len() - 1
        });
        groups[index].push(attempt);
    }

    let mut targets: Vec<Aggregate> = Vec::new();
    let mut target_of: HashMap<String, usize> = HashMap::new();
    let mut failures = Vec::new();
    let mut flaky = Vec::new();
    let mut degraded = false;

    for mut group in groups {
        // Attempts arrive in order already; a stable sort by attempt number is what makes "the last one decides"
        // true rather than merely usual.
        group.sort_by_key(|attempt| attempt.attempt);
        let Some(&last) = group.last() else {
            continue;
        };
        let index = *target_of.entry(last.label.clone()).or_insert_with(|| {
            targets.push(Aggregate {
                label: last.label.clone(),
                tests: 0,
                failed: 0,
                skipped: 0,
                flaky: 0,
                cached: true,
                shards: HashSet::new(),
                duration_ms: 0,
                log_path: None,
                wrapper_exit_code: None,
            });
            targets.len() - 1
        });
        let target = &mut targets[index];
        target.shards.insert((last.run, last.shard));
        // One shard that really ran makes the whole target not a cache hit: the cost line must not claim a target
        // was free when part of it was paid for.
        target.cached &= last.cached;
        target.duration_ms = target.duration_ms.max(last.duration_ms);
        let passed = last.status == "PASSED";
        // A failing shard's log is the one worth reading, so it wins over an already-recorded green one.
        if let Some(log) = &last.log_path
            && (!passed || target.log_path.is_none())
        {
            target.log_path = Some(relativize_testlogs(log));
        }

        let mut suites = Vec::new();
        match &last.xml_path {
            Some(xml) if runtime.exists(Path::new(xml)) => {
                match runtime.read_text_file(Path::new(xml)) {
                    // No error path: the reader is a scanner that answers partial suites rather than failing, so a
                    // truncated document degrades the *counts* and not the run.
                    Ok(text) => suites = junit::parse(&text),
                    Err(_) => degraded = true,
                }
            }
            _ if !passed => degraded = true,
            _ => {}
        }

        for suite in suites {
            if suite.bucketing_stub {
                continue;
            }
            if suite.wrapper_exit_code.is_some() {
                target.wrapper_exit_code = suite.wrapper_exit_code;
                continue;
            }
            target.tests += suite.tests;
            target.skipped += suite.skipped;
            let before = failures.len();
            failures.extend(suite.cases.into_iter().filter(|case| case.outcome == junit::Outcome::Failed));
            let counted = failures.len() - before;
            // Trust the suite header when a runner reported counts without per-case failure elements.
            target.failed += if counted == 0 {
                suite.failures + suite.errors
            } else {
                u32::try_from(counted).unwrap_or(u32::MAX)
            };
        }

        if group.len() > 1 && passed {
            target.flaky += 1;
            let retried_a_failure = group[..group.len() - 1].iter().any(|earlier| earlier.status != "PASSED");
            let id = if retried_a_failure {
                format!("{} shard {}", last.label, last.shard)
            } else {
                last.label.clone()
            };
            flaky.push(FlakyRun {
                id,
                attempts: u32::try_from(group.len()).unwrap_or(u32::MAX),
            });
        }
    }

    let mut build_errors = parse_compile_errors(bazel_output);
    if build_errors.is_empty() {
        // Three at most: the same broken file is reported by every action that consumed it, and the fourth stderr
        // has never added a diagnostic the first three did not have.
        for stderr in bep.action_stderr.iter().take(3) {
            let path = Path::new(stderr);
            if !runtime.exists(path) {
                continue;
            }
            if let Ok(text) = runtime.read_text_file(path) {
                build_errors.extend(parse_compile_errors(&text));
            }
        }
    }

    let mut targets: Vec<TargetResult> = targets
        .into_iter()
        .map(|target| TargetResult {
            label: target.label,
            tests: target.tests,
            failed: target.failed,
            skipped: target.skipped,
            flaky: target.flaky,
            cached: target.cached,
            shards: u32::try_from(target.shards.len()).unwrap_or(u32::MAX),
            duration_ms: target.duration_ms,
            log_path: target.log_path,
            wrapper_exit_code: target.wrapper_exit_code,
        })
        .collect();
    // A target that failed to build emitted no testResult at all, so it would otherwise vanish from the digest that
    // is supposed to explain why nothing ran. Zero shards is what keeps it out of the cost line.
    for label in &bep.failed_to_build {
        if !targets.iter().any(|target| &target.label == label) {
            targets.push(TargetResult {
                label: label.clone(),
                ..TargetResult::default()
            });
        }
    }

    if !bep.saw_any_event {
        degraded = true;
    }

    let (status, exit_code) = classify_run(ClassifyInput {
        bazel_exit,
        bep,
        targets: &targets,
        build_errors: &build_errors,
        degraded,
    });
    RunResult {
        status,
        exit_code,
        duration_ms,
        targets,
        failures,
        flaky,
        build_errors,
        degraded,
    }
}

/// Compiler diagnostics normalised to `file:line:col: error: message`.
///
/// The input is bazel's captured output, not the BEP: the BEP's `action.stderr` URI points at
/// bazel-out/_tmp/actions/stderr-*, which bazel deletes before this code can read it.
pub fn parse_compile_errors(text: &str) -> Vec<String> {
    // The repo's JvmCompile worker reports the message and the location on separate lines, with the location
    // indented and absolute:
    //
    //   Kotlinc Runner: Error: Unresolved reference 'foo'.
    //   \t/…/execroot/_main/plugins/air/x/testSrc/FooTest.kt:17:16
    let worker_diagnostic = crate::regex!(r"^(?:[A-Za-z0-9_][A-Za-z0-9_ ]*: )?(?:Error|error):\s*(.+)$");
    let worker_location = crate::regex!(r"^(\S+\.(?:kt|java|kts)):(\d+)(?::(\d+))?$");
    // javac and plain kotlinc use the one-line form instead, so both are recognised.
    let inline_diagnostic = crate::regex!(r"(?i)^(?:ERROR: )?(\S+\.(?:kt|java|kts)):(\d+)(?::(\d+))?:\s*(?:error|e):\s*(.+)$");
    let render = |file: &str, line: &str, column: Option<regex::Match<'_>>, message: &str| {
        let column = column.map_or(String::new(), |column| format!(":{}", column.as_str()));
        format!("{}:{line}{column}: error: {}", relativize_source_path(file), message.trim())
    };

    let lines: Vec<&str> = text.split('\n').collect();
    let mut errors = Vec::new();
    for (position, line) in lines.iter().enumerate() {
        let line = line.trim();
        if let Some(worker) = worker_diagnostic.captures(line) {
            let next = lines.get(position + 1).map_or("", |next| next.trim());
            if let Some(location) = worker_location.captures(next) {
                errors.push(render(&location[1], &location[2], location.get(3), &worker[1]));
                continue;
            }
        }
        if let Some(inline) = inline_diagnostic.captures(line) {
            errors.push(render(&inline[1], &inline[2], inline.get(3), &inline[4]));
        }
    }
    errors
}

#[cfg(test)]
mod tests;
