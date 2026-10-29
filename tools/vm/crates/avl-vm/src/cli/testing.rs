//! The hermetic invocation of the controller that the suites of the entry point and of the bench verbs share.

use std::ffi::OsString;
use std::path::Path;

use avl_base::{Environment, Exit, Reporter};
use avl_host_sys::Interrupts;

use crate::controller::run;
use crate::terminal::TerminalFacts;

/// One invocation's two streams and its exit status, which is the whole observable surface.
#[derive(Debug)]
pub(crate) struct Answer {
    pub(crate) exit: Exit,
    pub(crate) stdout: String,
    pub(crate) stderr: String,
}

/// Runs the controller over in-memory writers, with every path of its environment under `root` and the variables
/// `extra` added.
///
/// `BUILD_WORKSPACE_DIRECTORY` is what the skill directory resolves from, so the run never searches upwards and never
/// depends on where the test binary started. Under Bazel that is a sandbox with no checkout above it.
pub(crate) async fn invoke_under(root: &Path, argv: &[&str], extra: &[(&str, &str)]) -> Answer {
    avl_affected::bridge::install_fixture();
    let path = |relative: &str| root.join(relative).to_string_lossy().into_owned();
    let pairs: Vec<(String, String)> = [
        ("HOME", path("")),
        ("BUILD_WORKSPACE_DIRECTORY", path("")),
        ("AIR_VM_RUNTIME_ROOT", path("runtime")),
        ("AIR_VM_IMAGE_ROOT", path("image")),
        ("AIR_VM_HOST_REPO", path("")),
        ("AIR_VM_BAZEL_USER_ROOT", path("bazel-root")),
    ]
    .into_iter()
    .chain(extra.iter().map(|(name, value)| (*name, (*value).to_owned())))
    .map(|(name, value)| (name.to_owned(), value))
    .collect();
    let (reporter, stdout, stderr) = Reporter::in_memory("vm");
    let exit = run(
        argv.iter().map(OsString::from).collect(),
        Environment::from_pairs(pairs),
        reporter,
        TerminalFacts::default(),
        Interrupts::detached(),
    )
    .await;
    Answer {
        exit,
        stdout: stdout.text(),
        stderr: stderr.text(),
    }
}
