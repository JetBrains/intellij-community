//! The text digest: one line for a green run, about ten for a failure. Advisory, so it may be reformatted; the
//! `--json` payload is the stable half.

use bt_junit::TestCase;

use bt_core::result::{RunResult, RunStatus, TargetResult, filter_matched_no_class, totals_of};
use bt_core::selector::Selector;

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

/// How the digest names one failure: `SimpleClass#name`, exactly as test.xml carries it.
///
/// An identifier and not a selector; see [`rerun_selector`] for the selector, and for why the two differ.
pub(crate) fn test_case_id(case: &TestCase) -> String {
    let simple_class = case
        .class_name
        .rsplit_once('.')
        .map_or(case.class_name.as_str(), |(_, simple)| simple);
    if simple_class.is_empty() {
        return case.name.clone();
    }
    format!("{simple_class}#{}", case.name)
}

/// These cannot appear in a JVM method name, so a name holding one is a display name.
const JVM_FORBIDDEN_METHOD_CHARACTERS: &[char] = &['.', ';', '[', ']', '/', '<', '>'];

/// The selector that reruns one failure. It names the method only when the method is selectable, and the class
/// alone otherwise.
///
/// The class alone is a real answer rather than a degradation, because a class rerun works.
/// `community/platform/testFramework/bootstrap/src/com/intellij/tests/bazel/TestCaseXmlRenderer.java` writes the
/// display name for a dynamic test, truncates a legacy reporting name at the first `(`, and prefixes a
/// test-template child with its parent name and a dot. So the name in test.xml is not always a method name, and a
/// `#` selector built from one reaches the runner as a method it cannot find. The run then reports NO TESTS for a
/// class that plainly failed.
///
/// The method survives two checks. It must round-trip through [`Selector::classify`], the parser this tool already
/// owns, so the hint grows no second grammar. It must also hold no character a JVM method name forbids.
pub(crate) fn rerun_selector(case: &TestCase) -> String {
    let simple_class = rerun_class_name(&case.class_name);
    if simple_class.is_empty() {
        // No class to fall back to. The name is all there is, and a caller at least reads what failed.
        return case.name.clone();
    }
    let candidate = format!("{simple_class}#{}", case.name);
    if !case.name.contains(JVM_FORBIDDEN_METHOD_CHARACTERS)
        && Selector::classify(&candidate).is_ok_and(|selector| selector.method.is_some())
    {
        return candidate;
    }
    simple_class.to_owned()
}

/// The class part of a rerun selector: the last dotted segment, and the outer half of a nested pair.
///
/// A `@Nested` class reaches test.xml as `Outer$Nested`, which the selector parser refuses. `Outer` is a selector
/// it accepts, and it runs the nested class too, because the runner's class filter matches a prefix.
fn rerun_class_name(class_name: &str) -> &str {
    let simple = class_name.rsplit_once('.').map_or(class_name, |(_, simple)| simple);
    simple.split_once('$').map_or(simple, |(outer, _)| outer)
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
}

impl Default for RenderOptions {
    fn default() -> Self {
        Self {
            max_failures: 3,
            rerun_hint: None,
            method_filtered: false,
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
            out.push(format!(
                "FAIL  {} of {}{scope}  {duration}{single}",
                sum.failed,
                plural(sum.tests, "test")
            ));
            out.push(String::new());
            let shown = result.failures.len().min(options.max_failures);
            for (position, failure) in result.failures[..shown].iter().enumerate() {
                out.extend(render_failure(position + 1, failure));
                out.push(String::new());
            }
            let rest = &result.failures[shown..];
            for failure in rest.iter().take(MAX_FAILURE_ONE_LINERS) {
                out.push(format!(
                    "+ {} — {}",
                    test_case_id(failure),
                    one_line(failure_message(failure).unwrap_or(""), 100)
                ));
            }
            if rest.len() > MAX_FAILURE_ONE_LINERS {
                out.push(format!("+ {} more (see log)", rest.len() - MAX_FAILURE_ONE_LINERS));
            }
        }
        RunStatus::BuildFailed => {
            out.push(format!("BUILD FAILED  0 tests ran{single}"));
            out.push(String::new());
            out.extend(result.build_errors.iter().take(6).cloned());
            if result.build_errors.len() > 6 {
                out.push(format!("+ {} more errors", result.build_errors.len() - 6));
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
                    // The `#method` half of the filter is the suspect the reader can act on. A display name and a
                    // method that takes a parameter both land here, and the class-level rerun runs either.
                    "cause     the #method filter matched no method — rerun the class alone, without the #"
                } else {
                    "cause     no test target matched, or every shard was filtered out"
                }
                .to_owned(),
            );
        }
        RunStatus::Infra => {
            out.push(format!("INFRA  the bazel run did not produce usable results{single}"));
        }
    }

    while out.last().is_some_and(String::is_empty) {
        out.pop();
    }

    // A 61-target lane run has 61 logs; only the ones belonging to a target that actually failed are worth pointing
    // an agent at.
    let blamed: Vec<&TargetResult> = result
        .targets
        .iter()
        .filter(|target| target.failed > 0 || target.wrapper_exit_code.is_some())
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
    if let Some(hint) = &options.rerun_hint
        && result.status == RunStatus::Fail
    {
        out.push(format!("rerun  {hint}"));
    }
    if let Some(cost) = render_cost(&result.targets) {
        out.push(cost);
    }
    if result.degraded {
        out.push("note   result details were incomplete; the digest may be partial".to_owned());
    }
    out.join("\n")
}

/// The last lines of captured output, which is all that is ever shown of it, and only when the run was classified
/// as infrastructure, where bazel's own words are the only evidence there is.
pub(crate) fn tail_lines(text: &str, max_lines: usize) -> String {
    let lines: Vec<&str> = text.split('\n').collect();
    if lines.len() <= max_lines {
        return text.to_owned();
    }
    lines[lines.len() - max_lines..].join("\n")
}

#[cfg(test)]
mod tests;
