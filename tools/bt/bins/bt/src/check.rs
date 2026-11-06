//! The check record: the tree of the checkout that a green build or test saw.
//!
//! `bd lease pick` of Branch Deck reads it. In a repository whose `.branchdeck.toml` sets `pick_requires_check`, the
//! pick lands a lease only when a record names the tree of its HEAD. bt is the only writer. A record is one JSON line
//! in `bd-checks.jsonl` of the git directory of a linked work tree, its own directory under `.git/worktrees/`. The
//! main checkout records nothing, because no pick lands it. The file keeps the last [`KEPT_RECORDS`] lines, and each
//! write replaces it in one step.
//!
//! A local run takes the tree before its first bazel spawn and again after a green result. It writes the record only
//! when both are the same tree, so an edit during the run leaves no record. A red run leaves none. A record of a tree
//! with local changes names those paths on the stderr: the pick finds the record only when the commit holds the same
//! files. A `--host` run records the tree of the commit that the host ran: `bd-host run --head` refuses a checkout
//! with local changes, so that tree is the tree of the checkout.

use bt_core::Runtime;
use bt_core::exit;
use bt_core::runtime::CheckoutTree;
use serde::Serialize;

/// The name of the record file in the git directory.
pub(crate) const CHECKS_FILE: &str = "bd-checks.jsonl";

/// How many records the file keeps: the newest ones.
pub(crate) const KEPT_RECORDS: usize = 100;

/// What ran: a test run or `bt build`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum CheckCommand {
    Test,
    Build,
}

/// What a record says about the run, beside the tree.
pub(crate) struct Check<'a> {
    pub(crate) command: CheckCommand,
    /// The target patterns of the bazel command. For a `--host` run, the arguments of bt on the host.
    pub(crate) patterns: &'a [String],
    /// The alias of the machine of a `--host` run.
    pub(crate) host: Option<&'a str>,
}

/// One line of the record file. The field order is the key order on the line.
#[derive(Serialize)]
struct Record<'a> {
    schema: u32,
    tree: &'a str,
    head: &'a str,
    tool: &'static str,
    command: CheckCommand,
    patterns: &'a [String],
    exit: u8,
    at: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    host: Option<&'a str>,
}

/// The tree of the checkout before a run, or `None` when the run cannot leave a record: outside git, or when git
/// cannot tell the tree, which a stderr line names.
pub(crate) fn tree_before_run(runtime: &dyn Runtime) -> Option<CheckoutTree> {
    read_tree(runtime)
}

/// Records a green local run, when the tree after the run is the tree before it.
pub(crate) fn record_green_run(runtime: &dyn Runtime, before: Option<&CheckoutTree>, check: &Check<'_>) {
    let Some(before) = before else {
        return;
    };
    let Some(after) = read_tree(runtime) else {
        return;
    };
    if after.tree != before.tree {
        runtime.write_error(&format!(
            "bt: the files changed during the run (tree {} before it, {} after it), so the run leaves no check record for bd lease pick",
            before.tree, after.tree
        ));
        return;
    }
    if append(runtime, &after, check) && !after.changed.is_empty() {
        let mut paths: Vec<&str> = after.changed.iter().take(SHOWN_PATHS).map(String::as_str).collect();
        let rest = after.changed.len().saturating_sub(SHOWN_PATHS);
        let more = format!("{rest} more");
        if rest > 0 {
            paths.push(&more);
        }
        runtime.write_error(&format!(
            "bt: the check record names the tree with the local changes of {}. Commit them, or delete the ones that \
             are not part of the work, before bd lease pick: the pick finds the record only for a commit of these files",
            paths.join(", ")
        ));
    }
}

/// How many paths of a tree with local changes the stderr line names.
const SHOWN_PATHS: usize = 10;

/// Records a green run on a host: the tree of the commit that the host ran.
pub(crate) fn record_host_run(runtime: &dyn Runtime, commit: &str, check: &Check<'_>) {
    match runtime.commit_tree(commit) {
        Ok(tree) => {
            let tree = CheckoutTree {
                head: commit.to_owned(),
                tree,
                changed: Vec::new(),
            };
            append(runtime, &tree, check);
        }
        Err(error) => warn(runtime, &format!("git cannot tell the tree of the commit {commit}: {error}")),
    }
}

fn read_tree(runtime: &dyn Runtime) -> Option<CheckoutTree> {
    runtime.checkout_tree().unwrap_or_else(|error| {
        warn(runtime, &format!("git cannot tell the tree of the checkout: {error}"));
        None
    })
}

/// Appends one record, and drops the oldest ones past [`KEPT_RECORDS`]. Answers whether the record was written.
///
/// [`Runtime::write_text_file`] writes a temporary file beside the record file and renames it over the file, so a
/// concurrent reader such as `bd lease pick` sees the old records or the new ones, never a part.
fn append(runtime: &dyn Runtime, tree: &CheckoutTree, check: &Check<'_>) -> bool {
    match write_record(runtime, tree, check) {
        Ok(()) => true,
        Err(reason) => {
            warn(runtime, &reason);
            false
        }
    }
}

fn write_record(runtime: &dyn Runtime, tree: &CheckoutTree, check: &Check<'_>) -> Result<(), String> {
    let record = Record {
        schema: 1,
        tree: &tree.tree,
        head: &tree.head,
        tool: "bt",
        command: check.command,
        patterns: check.patterns,
        exit: exit::GREEN,
        at: runtime.now_ms(),
        host: check.host,
    };
    let line = serde_json::to_string(&record).map_err(|error| format!("the record does not encode: {error}"))?;
    let path = runtime
        .git_path(CHECKS_FILE)
        .map_err(|error| format!("git names no git directory: {error}"))?;
    let text = match runtime.read_text_file(&path) {
        Ok(text) => text,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
        Err(error) => return Err(format!("cannot read {}: {error}", path.display())),
    };
    let mut lines: Vec<&str> = text.lines().filter(|line| !line.is_empty()).collect();
    lines.push(&line);
    let kept = &lines[lines.len().saturating_sub(KEPT_RECORDS)..];
    let mut text = kept.join("\n");
    text.push('\n');
    runtime
        .write_text_file(&path, &text)
        .map_err(|error| format!("cannot write {}: {error}", path.display()))
}

/// A record that cannot be written is a warning, not a refusal: the verdict of the run is already decided.
fn warn(runtime: &dyn Runtime, reason: &str) {
    runtime.write_error(&format!("bt: no check record for bd lease pick, because {reason}"));
}

#[cfg(test)]
mod tests;
