//! `air-trace plan INPUT...`.

use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;

use avl_trace_tools::discover::{Root, RootKind, default_roots};

use super::{Input, join_on_disk, render, resolve_all};
use crate::{Exit, Refusal, code, report, usage};

/// The long help of `air-trace plan`, for the program that composes the verb.
pub(crate) const PLAN_ABOUT: &str = "\
Answers the scenarios INPUT covers, the bundles INPUT names, the bundles of those scenarios already on this \
machine, and the commands that record new traces of them: the VM sequence, and the host run that takes your \
screen.

INPUT is a trace bundle (a directory, a zip, spans.jsonl or bundle.json), a flow-*.txt, a flow profile, a JUnit \
test.xml, a vm.cmd report or envelope, a source path or file name, a class name, a flow id, a suite, a scenario, \
or a step, operation or check id. \"-\" reads one document from standard input, such as a vm.cmd envelope piped \
in.

Exit 0 when something was reached, 3 when nothing was, 2 for usage, 1 when the checkout could not be read.

The bundles on this machine are looked for where air-trace serve looks: out/air-traces, the AIR lanes' \
bazel-testlogs, and the VM reports and runs. --root adds a directory or zip, and --no-default-roots drops those.";

/// `air-trace plan`'s command line.
#[derive(clap::Args, Clone, Debug, Default)]
pub(crate) struct PlanArgs {
    /// Answer the plan as JSON rather than as text.
    #[arg(long)]
    pub json: bool,
    /// The checkout to plan against; BUILD_WORKSPACE_DIRECTORY by default, which trace.cmd sets, and then the
    /// checkout around the working directory.
    #[arg(long, value_name = "DIR")]
    pub repo: Option<PathBuf>,
    /// Another directory or zip to find the scenarios' bundles in; repeatable.
    #[arg(long = "root", value_name = "DIR")]
    pub roots: Vec<PathBuf>,
    /// Look for bundles only in the --root values.
    #[arg(long)]
    pub no_default_roots: bool,
    /// What to plan for; "-" reads standard input.
    #[arg(value_name = "INPUT", required = true)]
    pub inputs: Vec<String>,
}

/// Bounds a document read from standard input: the largest JUnit XML the controller retrieves is 8 MiB, and a
/// report envelope carries at most that much again.
const MAX_STDIN_BYTES: u64 = 64 << 20;

/// Runs `air-trace plan` and answers its exit code: [`Exit::Ok`] or [`Exit::Nothing`] with the answer on stdout, or
/// the exit of the refusal, whose message goes to stderr.
pub(crate) fn run_plan(args: &PlanArgs, stdin: &mut dyn Read, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    match plan_to(args, stdin, stdout) {
        Ok(exit) => exit.code(),
        Err(refused) => report(stderr, "plan", &refused),
    }
}

fn plan_to(args: &PlanArgs, stdin: &mut dyn Read, stdout: &mut dyn Write) -> Result<Exit, Refusal> {
    let mut inputs = Vec::new();
    for argument in &args.inputs {
        if argument != "-" {
            inputs.push(Input {
                text: argument.clone(),
                ..Input::default()
            });
            continue;
        }
        let mut content = Vec::new();
        stdin
            .take(MAX_STDIN_BYTES + 1)
            .read_to_end(&mut content)
            .map_err(|error| Refusal::new("stdin_unreadable", Exit::Broken, format!("cannot read standard input: {error}")))?;
        if content.len() as u64 > MAX_STDIN_BYTES {
            return Err(usage(format!("standard input is over {MAX_STDIN_BYTES} bytes")));
        }
        inputs.push(Input {
            content,
            ..Input::default()
        });
    }
    let root = checkout_root(args.repo.as_deref())?;
    avl_affected::bridge::install(&root).map_err(crate::from_controller)?;
    let mut roots = if args.no_default_roots {
        Vec::new()
    } else {
        let runtime_root = crate::vm_runtime_root();
        default_roots(Some(&root), runtime_root.as_deref().map_err(|refused| refused.message.as_str()))
    };
    for extra in &args.roots {
        let absolute = std::path::absolute(extra).map_err(|error| usage(format!("--root {}: {error}", extra.display())))?;
        roots.push(Root::new(RootKind::Flag, absolute));
    }
    let mut result = resolve_all(&root, inputs).map_err(|error| Refusal::new(code::PLAN_FAILED, Exit::Broken, format!("{error:#}")))?;
    join_on_disk(&mut result, &roots);
    let written = if args.json {
        serde_json::to_writer_pretty(&mut *stdout, &result)
            .map_err(io::Error::from)
            .and_then(|()| writeln!(stdout))
    } else {
        stdout.write_all(render(&result).as_bytes())
    };
    written.map_err(|error| Refusal::new("output_failed", Exit::Broken, error.to_string()))?;
    if result.scenarios.is_empty() && result.classes.is_empty() && result.bundles.is_empty() {
        return Ok(Exit::Nothing);
    }
    Ok(Exit::Ok)
}

/// The checkout to plan against: the flag, then `BUILD_WORKSPACE_DIRECTORY`, which `trace.cmd` sets because a
/// Bazel-built binary's own path says nothing about the checkout, then the work tree around the working directory.
fn checkout_root(flag: Option<&Path>) -> Result<PathBuf, Refusal> {
    let candidate = match flag {
        Some(flag) => flag.to_path_buf(),
        None => {
            if let Some(workspace) = std::env::var_os("BUILD_WORKSPACE_DIRECTORY").filter(|value| !value.is_empty()) {
                PathBuf::from(workspace)
            } else {
                let output = Command::new("git")
                    .args(["rev-parse", "--show-toplevel"])
                    .output()
                    .ok()
                    .filter(|output| output.status.success())
                    .ok_or_else(|| no_checkout("no checkout: pass --repo, or run it through community/tools/trace.cmd"))?;
                PathBuf::from(String::from_utf8_lossy(&output.stdout).trim())
            }
        }
    };
    let absolute = std::path::absolute(&candidate)
        .map_err(|error| no_checkout(format!("cannot resolve the checkout {}: {error}", candidate.display())))?;
    if !absolute.is_dir() {
        return Err(no_checkout(format!("the checkout {} is not a directory", absolute.display())));
    }
    Ok(absolute)
}

/// The checkout cannot be read, so the plan has nothing to resolve against.
fn no_checkout(message: impl Into<String>) -> Refusal {
    Refusal::new(code::NO_CHECKOUT, Exit::Broken, message)
}
