use avl_base::Environment;
use avl_base::config::{Presentation, VIEWER_OFF_VARIABLE};
use avl_host_sys::viewer::viewer_dir;
use avl_host_testkit::prose;

use super::*;

/// A runner with an empty environment: no `PATH`, so nothing the checkout does not name can start.
fn runner() -> Runner {
    avl_host_testkit::runner(&[])
}

// `AIR_VM_VIEWER=off` starts nothing and names no link, so a run on a machine without the site stays quiet.
#[tokio::test]
async fn the_viewer_start_can_be_turned_off() {
    let (reporter, stderr) = prose();
    let runtime_root = tempfile::tempdir().expect("a runtime root");
    let environment = Environment::from_pairs([(VIEWER_OFF_VARIABLE, "off")]);
    let presentation = Presentation::load(&environment);
    ensure_viewer(runtime_root.path(), &presentation, &environment, &runner(), &reporter).await;
    assert!(stderr.is_empty(), "an off viewer said {:?}", stderr.text());
    assert!(!viewer_dir(runtime_root.path()).exists(), "an off viewer made its directory");
}

// Without the checkout the wrapper names, there is no `trace.cmd` to start. The run says so and goes on.
#[tokio::test]
async fn a_viewer_without_a_checkout_is_noted_and_the_run_goes_on() {
    let (reporter, stderr) = prose();
    let runtime_root = tempfile::tempdir().expect("a runtime root");
    ensure_viewer(
        runtime_root.path(),
        &Presentation::default(),
        &Environment::default(),
        &runner(),
        &reporter,
    )
    .await;
    // A viewer that already answers on this machine wins the probe, and then nothing is noted; either way the run
    // is not refused and nothing was started.
    let said = stderr.text();
    assert!(
        said.is_empty()
            || said.contains(
                "cannot start the trace viewer: BUILD_WORKSPACE_DIRECTORY is not set; start it with ./community/tools/trace.cmd serve"
            ),
        "the missing checkout was not named: {said:?}"
    );
    assert!(!viewer_dir(runtime_root.path()).exists());
}

// A probe that panicked is noted with its panic, not read as "no viewer" in silence. The run still starts a viewer.
#[tokio::test]
async fn a_probe_that_panicked_is_noted() {
    let (reporter, stderr) = prose();
    let probed = tokio::task::spawn_blocking(|| -> bool { panic!("the probe broke") }).await;
    assert!(!probe_answer(probed, &reporter));
    let said = stderr.text();
    assert!(
        said.contains("the trace viewer probe failed: "),
        "the failed probe was not noted: {said:?}"
    );
    assert!(said.contains("the probe broke"), "the note does not carry the panic: {said:?}");

    let (reporter, stderr) = prose();
    assert!(probe_answer(Ok(true), &reporter));
    assert!(!probe_answer(Ok(false), &reporter));
    assert!(stderr.is_empty(), "a finished probe said {:?}", stderr.text());
}
