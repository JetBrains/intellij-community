//! `bt build <pattern>...`: one `bazel build` with the UI of bazel silenced, read from its BEP, answered as a digest.
//!
//! It exists for the check record ([`crate::check`]): a green build records the tree that it saw, and a raw
//! `./bazel.cmd build` cannot, because `bazel.cmd` hands the process over to bazelisk.

use std::path::PathBuf;

use bt_core::bep::BepSummary;
use bt_core::lanes::{BEP_FLAG, bep_override};
use bt_core::os_runtime::command_line;
use bt_core::result::{RunResult, RunStatus};
use bt_core::runtime::Runtime;
use bt_core::selector::{check_package_dir, checkout_label};
use bt_core::{Refusal, bazel_command, exit};

use crate::check::{Check, CheckCommand, record_green_run, tree_before_run};
use crate::digest::{format_duration, tail_lines};
use crate::execute::{BazelRun, BepFile, DIGEST_TAIL_LINES, Outcome, run_bazel, to_json};
use crate::options::BuildArgs;

/// How many compile errors and failed targets the digest shows.
const SHOWN: usize = 6;

/// Runs one parsed `bt build`. A pattern of the root repository whose directory is not in the checkout is refused
/// before bazel starts.
pub(crate) fn execute_build(args: &BuildArgs, runtime: &dyn Runtime) -> Result<Outcome, Refusal> {
    let patterns: Vec<String> = args.patterns.iter().map(|pattern| checkout_label(runtime, pattern)).collect();
    for pattern in &patterns {
        check_package_dir(runtime, pattern)?;
    }
    let overridden_bep = bep_override(&args.passthrough);
    let owns_bep_file = overridden_bep.is_none();
    let bep_path = overridden_bep.unwrap_or_else(|| runtime.temp_file("air-bazel-build-bep").to_string_lossy().into_owned());
    let _bep_file = owns_bep_file.then(|| BepFile {
        runtime,
        path: PathBuf::from(&bep_path),
    });
    let bazel_args = build_bazel_args(&bep_path, &patterns, &args.passthrough);
    let command = bazel_command(runtime.repo_root(), runtime.platform(), &bazel_args);
    if args.dry_run {
        return Ok(Outcome {
            exit_code: exit::GREEN,
            text: format!("argv    {}", command_line(&command)),
            json: None,
        });
    }

    let tree_before = tree_before_run(runtime);
    let BazelRun {
        spawned,
        bep,
        result: collected,
    } = run_bazel(runtime, &command, &bazel_args, &bep_path);
    let ignored = ignored_patterns(&spawned.output);
    let (status, exit_code) = classify_build(spawned.exit_code, &bep, !ignored.is_empty());
    let result = RunResult {
        status,
        exit_code,
        ..collected
    };

    if args.verbose {
        runtime.write_error(&spawned.output);
    } else if exit_code != exit::GREEN {
        // A failed build has the words of bazel as its evidence, beside the compile errors the digest names.
        runtime.write_error(&tail_lines(&spawned.output, DIGEST_TAIL_LINES));
    }
    if exit_code == exit::GREEN {
        let check = Check {
            command: CheckCommand::Build,
            patterns: &patterns,
            host: None,
        };
        record_green_run(runtime, tree_before.as_ref(), &check);
    }
    Ok(Outcome {
        exit_code,
        text: render_build(&result, &patterns, &ignored),
        json: args.json.then(|| to_json(&result)),
    })
}

/// The argv of `bazel build`. `-k`, so one broken target does not hide the others. The passthrough lands last and
/// wins.
pub(crate) fn build_bazel_args(bep_path: &str, patterns: &[String], passthrough: &[String]) -> Vec<String> {
    let mut args: Vec<String> = ["build", "--noshow_progress", "--curses=no", "--color=no", "--show_result=0", "-k"]
        .map(str::to_owned)
        .into();
    args.push(format!("{BEP_FLAG}={bep_path}"));
    args.extend(patterns.iter().cloned());
    args.extend(passthrough.iter().cloned());
    args
}

/// A pattern that bazel dropped, and the ignored directory that it lies under.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct IgnoredPattern<'a> {
    pub(crate) pattern: &'a str,
    pub(crate) dir: &'a str,
}

/// The patterns that bazel dropped because they lie under an ignored directory, such as one of `.bazelignore`. Bazel
/// only warns about such a pattern, builds the other patterns and exits 0. Its BEP names no pattern that it dropped.
pub(crate) fn ignored_patterns(output: &str) -> Vec<IgnoredPattern<'_>> {
    output
        .lines()
        .filter_map(|line| {
            let rest = line.strip_prefix("WARNING: Pattern '")?;
            let (pattern, rest) = rest.split_once("' was filtered out by ignored directory '")?;
            let dir = rest.strip_suffix('\'')?;
            Some(IgnoredPattern { pattern, dir })
        })
        .collect()
}

/// The status of a build. Bazel exits 0 when it built every target, 1 when a target did not build, and with every
/// other code when bazel itself failed. A 0 is green only when the patterns named targets: a build of no target, or
/// a pattern that bazel dropped, proves nothing. A 0 without a BEP event says nothing about the build.
pub(crate) const fn classify_build(bazel_exit: i32, bep: &BepSummary, dropped_pattern: bool) -> (RunStatus, u8) {
    match bazel_exit {
        0 if !bep.saw_any_event => (RunStatus::Infra, exit::INFRA),
        0 if dropped_pattern || bep.completed_targets == 0 => (RunStatus::NoTests, exit::NO_TESTS),
        0 => (RunStatus::Pass, exit::GREEN),
        1 => (RunStatus::BuildFailed, exit::BUILD_FAILED),
        _ => (RunStatus::Infra, exit::INFRA),
    }
}

/// The digest of a build: one line when green. Otherwise the compile errors and the failed targets, or the patterns
/// that named no target.
pub(crate) fn render_build(result: &RunResult, patterns: &[String], ignored: &[IgnoredPattern<'_>]) -> String {
    let duration = format_duration(result.duration_ms);
    let patterns = patterns.join(" ");
    let mut out = Vec::new();
    match result.status {
        RunStatus::Pass => out.push(format!("BUILD OK  {patterns}  {duration}")),
        RunStatus::BuildFailed => {
            out.push(format!("BUILD FAILED  {patterns}  {duration}"));
            out.extend(result.build_errors.iter().take(SHOWN).cloned());
            if result.build_errors.len() > SHOWN {
                out.push(format!("+ {} more errors", result.build_errors.len() - SHOWN));
            }
            let failed: Vec<&str> = result
                .targets
                .iter()
                .filter(|target| target.shards == 0)
                .map(|target| target.label.as_str())
                .collect();
            for label in failed.iter().take(SHOWN) {
                out.push(format!("failed  {label}"));
            }
            if failed.len() > SHOWN {
                out.push(format!("+ {} more targets", failed.len() - SHOWN));
            }
            if result.build_errors.is_empty() {
                out.push("cause   no compile error was recognised; the tail of the bazel output is above".to_owned());
            }
        }
        RunStatus::NoTests => {
            out.push(format!("BUILD EMPTY  {patterns}  {duration}"));
            for IgnoredPattern { pattern, dir } in ignored {
                out.push(format!(
                    "ignored {pattern}  bazel ignores the directory {dir}, so the pattern names no target"
                ));
            }
            if ignored.is_empty() {
                out.push("cause   the patterns name no target, so bazel built nothing".to_owned());
            }
        }
        RunStatus::Fail | RunStatus::Infra => {
            out.push(format!(
                "INFRA  the bazel build did not finish, and the tail of its output is above  {patterns}  {duration}"
            ));
        }
    }
    out.join("\n")
}

#[cfg(test)]
mod tests;
