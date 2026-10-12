//! The `bt` runtime this controller resolves selectors through.

use std::path::Path;

use avl_base::Reporter;
use bt_core::OsRuntime;

/// [`OsRuntime::resolver`] over the real checkout: it cannot spawn, because a selector is resolved from the checkout
/// alone and a resolution that reached for bazel would start it from inside a held lease, minutes into a step where
/// every other one spends seconds.
///
/// Its diagnostics go to the reporter rather than the process's descriptors, because `shard` runs several workers
/// at once and a progress line has to say which one it came from.
pub(crate) fn bt_runtime(repo_root: &Path, reporter: &Reporter) -> OsRuntime {
    let reporter = reporter.clone();
    OsRuntime::resolver(repo_root, move |text| {
        reporter.note(text.trim_end_matches('\n'), None);
    })
}

#[cfg(test)]
mod tests;
