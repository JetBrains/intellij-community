//! `gc`: the retained generations, down to the two newest plus the ones the host asked to keep, and the staging
//! trees of stages that no longer run.

use std::collections::HashSet;

use avl_wire::stage::GcResult;

use super::donor::generation_candidates;
use super::{generation_root, generations_dir, is_directory, remove_abandoned_staging_trees, remove_tree, require_runtime_root};
use crate::reply::AgentRefusal;
use avl_wire::verb::AgentVerb;

/// How many generations stay at rest whatever was asked for: the newest is what a rerun of the same build reuses,
/// and the one before it is the donor the next build links from.
const KEEP_AT_REST: usize = 2;

pub(crate) fn collect(root: &str, requested_keeps: &[String]) -> Result<GcResult, AgentRefusal> {
    let runtime_root = require_runtime_root(AgentVerb::Gc, root)?;
    if !is_directory(&generations_dir(&runtime_root)) {
        return Ok(GcResult { removed: Vec::new() });
    }
    // Newest first, so the first two are the ones at rest; a requested generation stays in addition to them.
    let candidates = generation_candidates(&runtime_root, "");
    let mut keep: HashSet<&str> = candidates
        .iter()
        .take(KEEP_AT_REST)
        .map(|candidate| candidate.name.as_str())
        .collect();
    keep.extend(requested_keeps.iter().map(String::as_str));
    let mut removed = Vec::new();
    for candidate in &candidates {
        if keep.contains(candidate.name.as_str()) {
            continue;
        }
        remove_tree(&generation_root(AgentVerb::Gc, &runtime_root, &candidate.name)?);
        removed.push(candidate.name.clone());
    }
    removed.extend(remove_abandoned_staging_trees(&generations_dir(&runtime_root)));
    Ok(GcResult { removed })
}
