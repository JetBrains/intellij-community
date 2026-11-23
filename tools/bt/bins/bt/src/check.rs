//! The check record: the tree of the checkout that a green build or test saw.
//!
//! `brd lease pick` of Branch Deck reads it. In a repository whose `.branchdeck.toml` sets `pick_requires_check`, the
//! pick lands a lease only when a record names the tree of its HEAD. bt is the only writer. A record is one JSON line
//! in `bd-checks.jsonl` of the git directory of a linked work tree, its own directory under `.git/worktrees/`. The
//! main checkout records nothing, because no pick lands it. The file keeps the last [`KEPT_RECORDS`] lines, and each
//! write replaces it in one step.
//!
//! A local run takes the tree before its first bazel spawn and again after a green result. It writes the record only
//! when both are the same tree, so an edit during the run leaves no record. A red run leaves none. A record of a tree
//! with local changes names those paths on the stderr: the pick finds the record only when the commit holds the same
//! files. A `--host` run records the tree of the commit that the host ran, and HEAD of the checkout. `brd-host run`
//! writes that commit from the working tree, so its tree holds the local changes, as the tree of a local run does.

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
            "bt: the files changed during the run (tree {} before it, {} after it), so the run leaves no check record for brd lease pick",
            before.tree, after.tree
        ));
        return;
    }
    if append(runtime, &after, check) {
        name_local_changes(runtime, &after.changed);
    }
}

/// Names the local changes of a recorded tree on the stderr, so the agent commits them before the pick.
fn name_local_changes(runtime: &dyn Runtime, changed: &[String]) {
    if changed.is_empty() {
        return;
    }
    let mut paths: Vec<&str> = changed.iter().take(SHOWN_PATHS).map(String::as_str).collect();
    let rest = changed.len().saturating_sub(SHOWN_PATHS);
    let more = format!("{rest} more");
    if rest > 0 {
        paths.push(&more);
    }
    runtime.write_error(&format!(
        "bt: the check record names the tree with the local changes of {}. Commit them, or delete the ones that \
         are not part of the work, before brd lease pick: the pick finds the record only for a commit of these files",
        paths.join(", ")
    ));
}

/// How many paths of a tree with local changes the stderr line names.
const SHOWN_PATHS: usize = 10;

/// What the report of a `--host` run says about the commit that the host ran.
pub(crate) struct HostSnapshot<'a> {
    /// The commit that the host ran.
    pub(crate) commit: &'a str,
    /// HEAD of the checkout, or empty when the report names none.
    pub(crate) head: &'a str,
    /// Whether the commit holds the working tree. The local changes of the checkout are named only then.
    pub(crate) working_tree: bool,
}

/// Records a green run on a host: the tree of the commit that the host ran, with HEAD of the checkout. Without a HEAD
/// in the report, the record names the commit as its HEAD. For a run of the working tree, the stderr names the local
/// changes of that tree, as after a local run. A checkout whose files changed after the start of the run gets a line
/// that the record names the tree of the start.
pub(crate) fn record_host_run(runtime: &dyn Runtime, snapshot: &HostSnapshot<'_>, check: &Check<'_>) {
    let commit = snapshot.commit;
    let tree = match runtime.commit_tree(commit) {
        Ok(tree) => tree,
        Err(error) => return warn(runtime, &format!("git cannot tell the tree of the commit {commit}: {error}")),
    };
    let head = if snapshot.head.is_empty() { commit } else { snapshot.head };
    let ran = CheckoutTree {
        head: head.to_owned(),
        tree,
        changed: Vec::new(),
    };
    if !append(runtime, &ran, check) || !snapshot.working_tree {
        return;
    }
    match runtime.checkout_tree() {
        Ok(Some(now)) if now.tree == ran.tree => name_local_changes(runtime, &now.changed),
        Ok(Some(now)) => runtime.write_error(&format!(
            "bt: the files changed after the start of the run (tree {} ran, the checkout has {}). The check record \
             names the tree that ran: the pick finds it only for a commit of that tree",
            ran.tree, now.tree
        )),
        Ok(None) => {}
        Err(error) => runtime.write_error(&format!(
            "bt: git cannot tell the tree of the checkout ({error}), so bt cannot name the local changes of the check record"
        )),
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
/// concurrent reader such as `brd lease pick` sees the old records or the new ones, never a part.
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
    runtime.write_error(&format!("bt: no check record for brd lease pick, because {reason}"));
}

#[cfg(test)]
mod tests;
