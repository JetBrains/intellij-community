//! `bt build <pattern>...`: one `bazel build` with the UI of bazel silenced, read from its BEP, answered as a digest.
//!
//! It exists for the check record ([`crate::check`]): a green build records the tree that it saw, and a raw
//! `./bazel.cmd build` cannot, because `bazel.cmd` hands the process over to bazelisk.

use std::path::PathBuf;

use bt_core::lanes::{BEP_FLAG, bep_override};
use bt_core::os_runtime::command_line;
use bt_core::result::{RunResult, RunStatus};
use bt_core::runtime::Runtime;
use bt_core::{bazel_command, exit};

use crate::check::{Check, CheckCommand, record_green_run, tree_before_run};
use crate::digest::{format_duration, tail_lines};
use crate::execute::{BazelRun, BepFile, DIGEST_TAIL_LINES, Outcome, run_bazel, to_json};
use crate::options::BuildArgs;

/// How many compile errors and failed targets the digest shows.
const SHOWN: usize = 6;

/// Runs one parsed `bt build`.
pub(crate) fn execute_build(args: &BuildArgs, runtime: &dyn Runtime) -> Outcome {
    let overridden_bep = bep_override(&args.passthrough);
    let owns_bep_file = overridden_bep.is_none();
    let bep_path = overridden_bep.unwrap_or_else(|| runtime.temp_file("air-bazel-build-bep").to_string_lossy().into_owned());
    let _bep_file = owns_bep_file.then(|| BepFile {
        runtime,
        path: PathBuf::from(&bep_path),
    });
    let bazel_args = build_bazel_args(&bep_path, &args.patterns, &args.passthrough);
    let command = bazel_command(runtime.repo_root(), runtime.platform(), &bazel_args);
    if args.dry_run {
        return Outcome {
            exit_code: exit::GREEN,
            text: format!("argv    {}", command_line(&command)),
            json: None,
        };
    }

    let tree_before = tree_before_run(runtime);
    let BazelRun {
        spawned,
        result: collected,
        ..
    } = run_bazel(runtime, &command, &bazel_args, &bep_path);
    let (status, exit_code) = classify_build(spawned.exit_code);
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
            patterns: &args.patterns,
            host: None,
        };
        record_green_run(runtime, tree_before.as_ref(), &check);
    }
    Outcome {
        exit_code,
        text: render_build(&result, &args.patterns),
        json: args.json.then(|| to_json(&result)),
    }
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

/// The status of a build from the exit code of bazel, which is the whole verdict: 0 built, 1 did not build, and
/// every other code is bazel itself failing.
pub(crate) const fn classify_build(bazel_exit: i32) -> (RunStatus, u8) {
    match bazel_exit {
        0 => (RunStatus::Pass, exit::GREEN),
        1 => (RunStatus::BuildFailed, exit::BUILD_FAILED),
        _ => (RunStatus::Infra, exit::INFRA),
    }
}

/// The digest of a build: one line when green, the compile errors and the failed targets otherwise.
pub(crate) fn render_build(result: &RunResult, patterns: &[String]) -> String {
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
        RunStatus::Fail | RunStatus::NoTests | RunStatus::Infra => {
            out.push(format!(
                "INFRA  the bazel build did not finish, and the tail of its output is above  {patterns}  {duration}"
            ));
        }
    }
    out.join("\n")
}

#[cfg(test)]
mod tests;
