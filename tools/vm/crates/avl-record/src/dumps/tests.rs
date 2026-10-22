use std::fs;
use std::path::Path;
use std::time::{Duration, UNIX_EPOCH};

use avl_trace::bundle::{attach_path, bundle_file};
use avl_trace::otlp::{attr, event, lookup_str, span_id};
use pretty_assertions::assert_eq;

use crate::tests::harness::*;

const SCENARIO: &str = r#"{"op":"scenario","name":"example","testClass":"AirExampleUiTest","lane":"UI"}"#;
const FAILED: &str = r#"{"op":"done","status":"failed"}"#;
const PASSED: &str = r#"{"op":"done","status":"passed"}"#;
const DUMP_NAME: &str = "the_composer_popup_air_compose-thread-dump-at_202509.log";
const DUMP: &str = "\"AWT-EventQueue-0\" prio=6 tid=0x0 nid=0x0 waiting on condition\n";

/// The span and the name of each `air.attachment` record of the bundle.
fn attachments(bundle: &Path) -> Vec<(String, String)> {
    records(bundle)
        .iter()
        .filter(|record| record.event_name == event::ATTACHMENT)
        .map(|record| {
            (
                record.span_id.clone(),
                lookup_str(&record.attributes, attr::ATTACHMENT_NAME).unwrap_or_default().to_owned(),
            )
        })
        .collect()
}

/// Runs one scenario that ends with `done` while the IDE writes a dump and another file into its log directory. A
/// dump from before the scenario is there from the start.
fn run_with_dumps(done: &str) -> (Harness, std::path::PathBuf) {
    let h = Harness::new();
    let log_dir = h.ide.log_path.parent().unwrap().to_owned();
    let earlier = log_dir.join("an_earlier_wait-thread-dump-at_101500.log");
    fs::write(&earlier, "an earlier dump\n").unwrap();
    fs::File::options()
        .write(true)
        .open(&earlier)
        .unwrap()
        .set_modified(UNIX_EPOCH + Duration::from_secs(1))
        .unwrap();
    let mut session = h.session(h.options());
    h.feed(&mut session, &[&h.hello(true), SCENARIO]);
    fs::write(log_dir.join(DUMP_NAME), DUMP).unwrap();
    fs::write(log_dir.join("threadDumps.txt"), "not a dump of a wait\n").unwrap();
    h.feed(&mut session, &[done]);
    (h, log_dir)
}

/// The dump that a wait wrote while the scenario ran is an attachment of the failed bundle, on the root span. An
/// earlier dump and another file of the log directory stay out, and the IDE keeps its file.
#[test]
fn a_failed_scenario_keeps_the_thread_dumps_that_the_ide_wrote_while_it_ran() {
    let (h, log_dir) = run_with_dumps(FAILED);
    let bundle = h.example_bundle();
    assert_eq!(fs::read_to_string(bundle_file(&bundle, &attach_path(1, "log"))).unwrap(), DUMP);
    assert!(
        !bundle_file(&bundle, &attach_path(2, "log")).exists(),
        "the bundle keeps a dump from before the scenario"
    );
    assert_eq!(attachments(&bundle), [(span_id(0), DUMP_NAME.to_owned())]);
    assert!(log_dir.join(DUMP_NAME).exists(), "the IDE lost its dump");
}

/// A passed scenario keeps no dump, because its waits all held.
#[test]
fn a_passed_scenario_keeps_no_thread_dump() {
    let (h, _) = run_with_dumps(PASSED);
    let bundle = h.example_bundle();
    assert!(
        !bundle_file(&bundle, &attach_path(1, "log")).exists(),
        "a passed bundle keeps a dump"
    );
    assert_eq!(attachments(&bundle), Vec::<(String, String)>::new());
}
