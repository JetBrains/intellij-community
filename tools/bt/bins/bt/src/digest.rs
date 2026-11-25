//! The text digest: one line for a green run, about ten for a failure. Advisory, so it may be reformatted; the
//! `--json` payload is the stable half.

use bt_junit::TestCase;

use bt_core::result::{RunResult, RunStatus, ShardTimeout, TargetResult, filter_matched_no_class, totals_of};
use bt_core::selector::{is_method_name, is_target_pattern};

/// Everything a stack trace carries that is not the test.
///
/// Trimming is not cosmetic: the frame that names the assertion and the frame that names the test method are the
/// two an agent acts on, and a 60-frame IntelliJ test-framework trace between them is what makes a failure
/// unreadable at a glance.
const NOISE_FRAME_PREFIXES: &[&str] = &[
    "org.junit.",
    "org.opentest4j.",
    "org.assertj.",
    "java.",
    "javax.",
    "jdk.internal.",
    "sun.reflect",
    "kotlin.coroutines.jvm.internal.",
    "kotlinx.coroutines.",
    "com.intellij.testFramework.",
    "com.intellij.tests.",
    "com.intellij.rt.",
];

/// A trimmed trace plus how much was dropped, so the digest can say so rather than quietly showing two frames of a
/// sixty-frame failure.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct TrimmedStack {
    pub(crate) frames: Vec<String>,
    pub(crate) omitted: usize,
}

/// How many frames survive trimming.
pub(crate) const DEFAULT_MAX_FRAMES: usize = 6;

/// Keeps the first frame plus every frame that is not runner or platform noise.
///
/// The first frame is kept even when it is noise: it is the assertion helper that threw, and which helper it was is
/// the difference between a failed assertion and a NullPointerException.
pub(crate) fn trim_frames(detail: &str, max_frames: usize) -> TrimmedStack {
    let mut kept = Vec::new();
    let mut total = 0;
    for line in detail.split('\n') {
        let frame = line.trim();
        let Some(target) = frame.strip_prefix("at ") else {
            continue;
        };
        total += 1;
        let interesting = total == 1 || !NOISE_FRAME_PREFIXES.iter().any(|prefix| target.starts_with(prefix));
        if interesting && kept.len() < max_frames {
            kept.push(frame.to_owned());
        }
    }
    TrimmedStack {
        omitted: total - kept.len(),
        frames: kept,
    }
}

/// Collapses whitespace and truncates to a limit in characters, for the one-liner tail of a long failure list.
pub(crate) fn one_line(value: &str, limit: usize) -> String {
    let collapsed = value.split_whitespace().collect::<Vec<_>>().join(" ");
    if collapsed.chars().count() <= limit {
        return collapsed;
    }
    let mut truncated: String = collapsed.chars().take(limit.saturating_sub(1)).collect();
    truncated.push('…');
    truncated
}

/// Bounds a failure headline in both dimensions: an assertion diff can be one 40 KB line or 900 short ones, and
/// either would bury the rest of the digest.
pub(crate) fn clamp_lines(value: &str, max_lines: usize, max_chars: usize) -> Vec<String> {
    let truncated = if value.chars().count() > max_chars {
        let mut clipped: String = value.chars().take(max_chars).collect();
        clipped.push('…');
        clipped
    } else {
        value.to_owned()
    };
    let mut lines: Vec<&str> = truncated.splitn(max_lines.saturating_add(1), '\n').collect();
    if lines.len() <= max_lines {
        return lines.into_iter().map(str::to_owned).collect();
    }
    let rest = lines.pop().unwrap_or_default();
    let mut clamped: Vec<String> = lines.into_iter().map(str::to_owned).collect();
    clamped.push(format!("…({} more lines)", rest.matches('\n').count() + 1));
    clamped
}

pub(crate) fn failure_type(case: &TestCase) -> Option<&str> {
    case.failure.as_ref().and_then(|failure| failure.r#type.as_deref())
}

pub(crate) fn failure_message(case: &TestCase) -> Option<&str> {
    case.failure.as_ref().and_then(|failure| failure.message.as_deref())
}

pub(crate) fn failure_detail(case: &TestCase) -> &str {
    case.failure.as_ref().map_or("", |failure| failure.detail.as_str())
}

/// How the digest names one failure: `SimpleClass#name` of a JVM test, exactly as test.xml carries it, and the path of
/// a libtest test, such as `agent_home::tests::reads_the_home`.
///
/// An identifier and not a filter; see [`rerun_filter`] for the filter, and for why the two differ.
pub(crate) fn test_case_id(case: &TestCase) -> String {
    if let Some(path) = libtest_path(case) {
        return path;
    }
    let class = jvm_class(case);
    let simple_class = class.rsplit_once('.').map_or(class, |(_, simple)| simple);
    if simple_class.is_empty() {
        return case.name.clone();
    }
    format!("{simple_class}#{}", case.name)
}

/// These cannot appear in a JVM method name, so a name holding one is a display name.
const JVM_FORBIDDEN_METHOD_CHARACTERS: &[char] = &['.', ';', '[', ']', '/', '<', '>'];

/// The class name that `community/build/rust-tools/libtest-junit-wrapper` writes for a test at the root of its crate.
const LIBTEST_ROOT_CLASS: &str = "crate";

/// The filter that reruns one failure in the target that ran it, or `None` for a case that names no class.
///
/// A libtest test gives its path, which the libtest wrapper passes to libtest. The wrapper writes the module path as
/// the class name, and `crate` for a test at the root of its crate.
///
/// A JVM test gives the FQN of its class, and the method only when the method is selectable. The class alone is a real
/// answer rather than a degradation, because a class rerun works.
/// `community/platform/testFramework/bootstrap/src/com/intellij/tests/bazel/TeamCityTestNames.java` writes the name
/// of a test as TeamCity shows it: `test()`, `test(TestInfo)`, `test()[MONOLITH]`, `parameterized[FIRST]`. The method
/// ends at the first `[`, and the runner selects `test()`, `test(TestInfo)` and every method of a name. A name with
/// no method, such as a `@DisplayName` that starts with the method name, reaches the runner as a method it cannot
/// find. The run then reports NO TESTS for a class that plainly failed.
///
/// The method survives three checks. Its class must be a top-level class, because the runner finds an `@Nested` class
/// `Outer$Nested` only through `Outer`, and `Outer#method` looks for the method in `Outer` only. It must match
/// [`is_method_name`], the grammar of `--filter`. It must also hold no character a JVM method name forbids. A case with
/// no class name, such as `Class Configuration`, reruns the class at the start of its suite name.
pub(crate) fn rerun_filter(case: &TestCase) -> Option<String> {
    if let Some(path) = libtest_path(case) {
        return Some(path);
    }
    let class = jvm_class(case);
    if class.is_empty() {
        return None;
    }
    if let Some((outer, _)) = class.split_once('$') {
        return Some(outer.to_owned());
    }
    let method = method_name(&case.name);
    let selectable =
        !case.class_name.is_empty() && !method.is_empty() && !method.contains(JVM_FORBIDDEN_METHOD_CHARACTERS) && is_method_name(method);
    Some(if selectable {
        format!("{class}#{method}")
    } else {
        class.to_owned()
    })
}

/// The path of a libtest test, `<class name>::<name>`, or `None` for a JVM test.
///
/// The libtest wrapper names the suite by the label of the target, and the class by the module path, such as `tests`
/// or `agent_home::tests`. A JVM runner names the suite by a class. A module path holds no dot, so a JVM class that a
/// Rust test names with `junit_class` stays a JVM class.
fn libtest_path(case: &TestCase) -> Option<String> {
    let class = case.class_name.as_str();
    let libtest = !class.is_empty()
        && !class.contains('.')
        && (class == LIBTEST_ROOT_CLASS || class.contains("::") || is_target_pattern(&case.suite_name));
    libtest.then(|| format!("{class}::{}", case.name))
}

/// The class of a JVM test as test.xml names it. A case with no class name, such as `Class Configuration`, takes the
/// class at the start of its suite name.
fn jvm_class(case: &TestCase) -> &str {
    if case.class_name.is_empty() {
        suite_class(&case.suite_name)
    } else {
        &case.class_name
    }
}

/// The test name up to the first `[` outside the parameter types: `test(String[])[1]` gives `test(String[])`.
fn method_name(name: &str) -> &str {
    let mut depth = 0usize;
    for (index, char) in name.char_indices() {
        match char {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            '[' if depth == 0 => return &name[..index],
            _ => {}
        }
    }
    name
}

/// The FQN of the class at the start of a suite name: the dotted segments up to the first one with an upper-case first
/// letter, which ends at a `$`, `[` or `(`. The innermost suite comes last, after the last `: `.
///
/// `com.x.FooTest$Inner[MONOLITH].SPLIT`, `com.x.FooTest.factory()` and `S: com.x.FooTest` all give `com.x.FooTest`.
fn suite_class(suite_name: &str) -> &str {
    let innermost = suite_name.rsplit_once(": ").map_or(suite_name, |(_, innermost)| innermost);
    let mut start = 0;
    for segment in innermost.split('.') {
        if segment.starts_with(|first: char| first.is_ascii_uppercase()) {
            let class = segment.split(['$', '[', '(']).next().unwrap_or_default();
            return &innermost[..start + class.len()];
        }
        start += segment.len() + 1;
    }
    ""
}

/// One failure: its id, its headline, and its trimmed stack.
pub(crate) fn render_failure(position: usize, case: &TestCase) -> Vec<String> {
    let mut out = vec![format!("{position}) {}", test_case_id(case))];
    let message = failure_message(case).unwrap_or("");
    let headline = match failure_type(case) {
        Some(kind) if !kind.is_empty() => format!("{kind}: {message}"),
        _ => message.to_owned(),
    };
    out.extend(clamp_lines(&headline, 12, 500).into_iter().map(|line| format!("   {line}")));
    let stack = trim_frames(failure_detail(case), DEFAULT_MAX_FRAMES);
    out.extend(stack.frames.iter().map(|frame| format!("   {frame}")));
    if stack.omitted > 0 {
        out.push(format!("   ... {} frames omitted", stack.omitted));
    }
    out
}

/// One shard that Bazel stopped at its timeout. The test that had not ended is where the shard stopped, and the
/// digest names it as that, not as a failure.
pub(crate) fn render_timeout(timeout: &ShardTimeout) -> String {
    // `com.a.FooTest#name` as `FooTest#name`, as [`test_case_id`] names a failure.
    let simple = |id: &str| match id.split_once('#') {
        Some((class, name)) => format!("{}#{name}", class.rsplit_once('.').map_or(class, |(_, simple)| simple)),
        None => id.to_owned(),
    };
    let stopped = match timeout.interrupted.as_slice() {
        [] => String::new(),
        [only] => format!(", stopped in {}", simple(only)),
        [first, rest @ ..] => format!(", stopped in {} (+{} more)", simple(first), rest.len()),
    };
    format!(
        "shard timeout  {} shard {} of {} after {}{stopped}",
        target_name(&timeout.label),
        timeout.shard,
        timeout.shards,
        format_duration(timeout.duration_ms)
    )
}

/// A duration the way the digest shows it: `420ms`, `12.3s`, `4m12s`.
///
/// Tenths are rounded half up in integer arithmetic, so 3750 ms is `3.8s`: the exact decimal value, with no binary
/// fraction to push a near-tie either way.
pub(crate) fn format_duration(milliseconds: u64) -> String {
    if milliseconds < 1000 {
        return format!("{milliseconds}ms");
    }
    if milliseconds < 60_000 {
        let tenths = (milliseconds + 50) / 100;
        return format!("{}.{}s", tenths / 10, tenths % 10);
    }
    // Round before splitting: rounding the remainder on its own renders 119.6s as "1m60s".
    let whole = (milliseconds + 500) / 1000;
    format!("{}m{:02}s", whole / 60, whole % 60)
}

/// How much of a failure list to render.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RenderOptions {
    pub(crate) max_failures: usize,
    /// The command that reproduces the first failure; `None` for a run with nothing to rerun.
    pub(crate) rerun_hint: Option<String>,
    /// The run narrowed to one method with `#`. A NO_TESTS run then has a known cause, so the digest names it
    /// instead of listing the three it cannot choose between.
    pub(crate) method_filtered: bool,
    /// The hint of a NO_TESTS run about the form of its filter, see [`bt_core::selector::no_tests_hint`].
    pub(crate) no_tests_hint: Option<String>,
    /// The repo-relative run directory that holds the logs of the failed attempts, which the next run of the
    /// target cannot overwrite.
    pub(crate) kept_run: Option<String>,
    /// Full-lane classes over their time limit and outside the baseline.
    pub(crate) class_time_budget_violations: Vec<String>,
    /// Where the time of the run went: the `time` line, and a `note` for a part that is slow for a known reason.
    pub(crate) time: Option<RunTime>,
    /// The repo-relative file that keeps the output of bazel of a run whose build failed.
    pub(crate) bazel_log: Option<String>,
}

/// How many targets that did not build the digest names one by one.
const MAX_UNBUILT_TARGETS: usize = 6;

/// Where the time of a run went, beside the tests: the build of bt, the check record and the bazel commands. A narrow
/// run of one cached class is all of that and almost no test, so the line shows an agent what to fix without a
/// profile.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct RunTime {
    /// The build of bt by its wrapper, when the wrapper named its time.
    pub(crate) self_build_ms: Option<u64>,
    /// The two scans of the checkout for the check record: before the run and after a green result.
    pub(crate) check_record_ms: u64,
    /// The bazel commands of the run, from the first spawn to the end of the last one.
    pub(crate) bazel_ms: u64,
    /// The analysis phase that bazel reported, a part of [`RunTime::bazel_ms`].
    pub(crate) analysis_ms: u64,
}

/// A part of a run that takes at least this long gets a `note` with its cause and its fix.
const SLOW_PART_MS: u64 = 10_000;

/// The `time` line of a run, and a `note` for each part that is slow for a known reason.
fn render_time(time: &RunTime) -> Vec<String> {
    let mut parts = Vec::new();
    if let Some(build) = time.self_build_ms {
        parts.push(format!("bt build {}", format_duration(build)));
    }
    if time.check_record_ms > 0 {
        parts.push(format!("check record {}", format_duration(time.check_record_ms)));
    }
    if time.analysis_ms > 0 {
        parts.push(format!(
            "bazel {} (analysis {})",
            format_duration(time.bazel_ms),
            format_duration(time.analysis_ms)
        ));
    } else {
        parts.push(format!("bazel {}", format_duration(time.bazel_ms)));
    }
    let mut lines = vec![format!("time   {}", parts.join(" · "))];
    if let Some(build) = time.self_build_ms.filter(|build| *build >= SLOW_PART_MS) {
        lines.push(format!(
            "note   bt built itself for {}: its sources changed, or the Bazel server of this checkout started cold. \
             The next run reuses the build",
            format_duration(build)
        ));
    }
    if time.check_record_ms >= SLOW_PART_MS {
        lines.push(format!(
            "note   the check record read the status of the checkout for {}. A heavy load of the machine, or an \
             index without an untracked cache, makes the read slow. With `core.untrackedCache=true`, one `git status` in \
             the checkout writes that cache into the index",
            format_duration(time.check_record_ms)
        ));
    }
    if time.analysis_ms >= SLOW_PART_MS && time.analysis_ms * 2 >= time.bazel_ms {
        lines.push(format!(
            "note   bazel spent {} of {} in analysis: a cold server or other flags analyze the graph again. \
             The next run of this checkout with the same flags reuses the analysis",
            format_duration(time.analysis_ms),
            format_duration(time.bazel_ms)
        ));
    }
    lines
}

impl Default for RenderOptions {
    fn default() -> Self {
        Self {
            max_failures: 3,
            rerun_hint: None,
            method_filtered: false,
            no_tests_hint: None,
            kept_run: None,
            class_time_budget_violations: Vec::new(),
            time: None,
            bazel_log: None,
        }
    }
}

/// Bounds the collapsed tail. Past this the list itself is the noise.
const MAX_FAILURE_ONE_LINERS: usize = 20;

fn plural(count: impl Into<u64>, noun: &str) -> String {
    let count = count.into();
    if count == 1 {
        format!("{count} {noun}")
    } else {
        format!("{count} {noun}s")
    }
}

/// What a multi-target run actually cost, which its target count alone does not say: a lane that re-ran 41 of 63
/// targets and one that re-ran 4 both report "63 targets". Rendered on every status, because "why was that slow" is
/// most pressing on a run that also failed.
///
/// The slowest target is picked among the ones that really ran: a cache hit still reports the duration of the run
/// it was cached from, which was not paid here. Targets that failed to build never reached a test JVM at all, so
/// they are left out rather than counted as run.
pub(crate) fn render_cost(targets: &[TargetResult]) -> Option<String> {
    let executed: Vec<&TargetResult> = targets.iter().filter(|target| target.shards > 0).collect();
    if executed.len() <= 1 {
        return None;
    }
    let ran: Vec<&TargetResult> = executed.iter().copied().filter(|target| !target.cached).collect();
    // The first of equally slow targets, which `max_by_key` would not answer.
    let slowest = ran.iter().copied().fold(None::<&TargetResult>, |slowest, target| match slowest {
        Some(slowest) if slowest.duration_ms >= target.duration_ms => Some(slowest),
        _ => Some(target),
    });
    let mut parts = vec![format!("{} ran", ran.len()), format!("{} cached", executed.len() - ran.len())];
    if let Some(slowest) = slowest.filter(|slowest| slowest.duration_ms > 0) {
        parts.push(format!(
            "slowest {} {}",
            target_name(&slowest.label),
            format_duration(slowest.duration_ms)
        ));
    }
    Some(format!("cost   {}", parts.join(" · ")))
}

/// `//plugins/air/shared/core:air-shared-core-tests_test` as `air-shared-core-tests_test`.
fn target_name(label: &str) -> &str {
    label.rsplit_once(':').map_or(label, |(_, name)| name)
}

/// The whole digest.
pub(crate) fn render_digest(result: &RunResult, options: &RenderOptions) -> String {
    let mut out: Vec<String> = Vec::new();
    let sum = totals_of(&result.targets);
    let duration = format_duration(result.duration_ms);
    // A single target is named outright; several are counted, because 63 labels is the noise the digest exists to
    // remove.
    let single = match result.targets.as_slice() {
        [only] => format!("  {}", only.label),
        _ => String::new(),
    };
    let scope = if result.targets.len() > 1 {
        format!("  {}", plural(result.targets.len() as u64, "target"))
    } else {
        String::new()
    };

    match result.status {
        RunStatus::Pass => {
            let mut notes = Vec::new();
            if sum.flaky > 0 {
                notes.push(format!("{} flaky", sum.flaky));
            }
            if sum.skipped > 0 {
                notes.push(format!("{} skipped", sum.skipped));
            }
            // For several targets the cost line below carries the cache split instead.
            if let [only] = result.targets.as_slice()
                && only.cached
            {
                notes.push("cached".to_owned());
            }
            let suffix = if notes.is_empty() {
                String::new()
            } else {
                format!("  ({})", notes.join(", "))
            };
            out.push(format!("PASS  {}{scope}  {duration}{suffix}{single}", plural(sum.tests, "test")));
            for flaky in &result.flaky {
                out.push(format!(
                    "flaky  {}  passed on attempt {} of {}",
                    flaky.id, flaky.attempts, flaky.attempts
                ));
            }
        }
        RunStatus::Fail => {
            let timeouts = if result.timeouts.is_empty() {
                String::new()
            } else {
                format!(", {}", plural(result.timeouts.len() as u64, "shard timeout"))
            };
            out.push(format!(
                "FAIL  {} of {}{timeouts}{scope}  {duration}{single}",
                sum.failed,
                plural(sum.tests, "test")
            ));
            out.push(String::new());
            out.extend(result.timeouts.iter().map(render_timeout));
            if !result.timeouts.is_empty() {
                out.push(String::new());
            }
            let shown = result.failures.len().min(options.max_failures);
            for (position, failure) in result.failures[..shown].iter().enumerate() {
                out.extend(render_failure(position + 1, &failure.case));
                out.push(String::new());
            }
            let rest = &result.failures[shown..];
            for failure in rest.iter().take(MAX_FAILURE_ONE_LINERS) {
                out.push(format!(
                    "+ {} — {}",
                    test_case_id(&failure.case),
                    one_line(failure_message(&failure.case).unwrap_or(""), 100)
                ));
            }
            if rest.len() > MAX_FAILURE_ONE_LINERS {
                out.push(format!("+ {} more (see log)", rest.len() - MAX_FAILURE_ONE_LINERS));
            }
        }
        RunStatus::BuildFailed => {
            // A target that failed to build reported no shard, see `collect_results`. The other invocations of a lane
            // still ran their tests, and the count says so.
            let unbuilt: Vec<&str> = result
                .targets
                .iter()
                .filter(|target| target.shards == 0)
                .map(|target| target.label.as_str())
                .collect();
            let unbuilt_count = if unbuilt.is_empty() {
                String::new()
            } else {
                format!(", {} did not build", plural(unbuilt.len() as u64, "target"))
            };
            out.push(format!("BUILD FAILED  {} ran{unbuilt_count}{single}", plural(sum.tests, "test")));
            out.push(String::new());
            out.extend(result.build_errors.iter().take(6).cloned());
            if result.build_errors.len() > 6 {
                out.push(format!("+ {} more errors", result.build_errors.len() - 6));
            }
            if result.targets.len() > 1 {
                for label in unbuilt.iter().take(MAX_UNBUILT_TARGETS) {
                    out.push(format!("did not build  {label}"));
                }
                if unbuilt.len() > MAX_UNBUILT_TARGETS {
                    out.push(format!("+ {} more targets that did not build", unbuilt.len() - MAX_UNBUILT_TARGETS));
                }
            }
            if result.build_errors.is_empty() {
                out.push(match &options.bazel_log {
                    Some(log) => format!("cause  bt recognised no build error; the output of bazel is in {log}"),
                    None => "cause  bt recognised no build error; the tail of the bazel output is above".to_owned(),
                });
            }
        }
        RunStatus::NoTests => {
            let skipped = if sum.skipped > 0 {
                format!(", {} skipped", sum.skipped)
            } else {
                String::new()
            };
            out.push(format!("NO TESTS  0 executed{skipped}{single}"));
            // The cause matters more than the count here: these look identical from the outside and need different
            // fixes.
            out.push(
                if filter_matched_no_class(&result.targets) {
                    "cause     the test filter matched no test class (runner exit 42)"
                } else if sum.skipped > 0 {
                    "cause     every matched test was skipped (assumeTrue / @Disabled) — a required *_BIN may be absent"
                } else if options.method_filtered {
                    // The `#method` half of the filter is the suspect the reader can act on. A display name lands
                    // here, and the class-level rerun runs it.
                    "cause     the #method filter matched no method — rerun the class alone, without the #"
                } else {
                    "cause     no test target matched, or every shard was filtered out"
                }
                .to_owned(),
            );
            // A skipped test or a test of a different target was matched, so the filter did its job.
            if sum.skipped == 0
                && sum.tests == 0
                && let Some(hint) = &options.no_tests_hint
            {
                out.push(format!("hint      {hint}"));
            }
        }
        RunStatus::Infra => {
            out.push(format!("INFRA  the bazel run did not produce usable results{single}"));
            for label in &result.mixed_shard_plans {
                out.push(format!(
                    "cause  the shards of {label} read different shard plans, so a class can have run in no shard; \
                     rerun with --no-cache"
                ));
            }
        }
    }

    if !options.class_time_budget_violations.is_empty() {
        out.push(format!("class-time budget  {}", options.class_time_budget_violations.join("; ")));
    }

    while out.last().is_some_and(String::is_empty) {
        out.pop();
    }

    // A 61-target lane run has 61 logs; only the ones belonging to a target that actually failed are worth pointing
    // an agent at.
    let blamed: Vec<&TargetResult> = result
        .targets
        .iter()
        .filter(|target| {
            target.failed > 0 || target.wrapper_exit_code.is_some() || result.timeouts.iter().any(|timeout| timeout.label == target.label)
        })
        .collect();
    let source: Vec<&TargetResult> = if blamed.is_empty() {
        result.targets.iter().collect()
    } else {
        blamed
    };
    let logs: Vec<&str> = source.iter().filter_map(|target| target.log_path.as_deref()).collect();
    if let Some(first) = logs.first()
        && result.status != RunStatus::Pass
    {
        out.push(String::new());
        out.push(format!("log    {first}"));
        if logs.len() > 1 {
            out.push(format!("       (+{} more target logs)", logs.len() - 1));
        }
    }
    if let Some(log) = &options.bazel_log
        && result.status == RunStatus::BuildFailed
    {
        out.push(format!("bazel  {log}  (the output of bazel, where the build failed)"));
    }
    if let Some(kept) = &options.kept_run
        && result.status != RunStatus::Pass
    {
        out.push(format!(
            "kept   {kept}  (the logs of each failed shard; a rerun overwrites out/bazel-testlogs)"
        ));
    }
    if let Some(hint) = &options.rerun_hint
        && result.status == RunStatus::Fail
    {
        out.push(format!("rerun  {hint}"));
    }
    if let Some(cost) = render_cost(&result.targets) {
        out.push(cost);
    }
    if let Some(time) = &options.time {
        out.extend(render_time(time));
    }
    if result.degraded {
        out.push("note   result details were incomplete; the digest may be partial".to_owned());
    }
    out.join("\n")
}

/// The last `max_lines` lines of the captured output of bazel. Without `--verbose`, bt shows no more of the output
/// than such a tail.
pub(crate) fn tail_lines(text: &str, max_lines: usize) -> String {
    let lines: Vec<&str> = text.split('\n').collect();
    if lines.len() <= max_lines {
        return text.to_owned();
    }
    lines[lines.len() - max_lines..].join("\n")
}

#[cfg(test)]
mod tests;
