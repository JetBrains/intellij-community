use std::fs;
use std::path::{Path, PathBuf};

use avl_trace::bundle::{
    ALLURE_RESULT_SUFFIX, AllureResult, AllureStatus, allure_history_id, allure_results_dir, bundle_dir, decode_allure_result,
    snap_image_path,
};
use avl_trace::protocol::{AttachCommand, Command, encode_command};
use pretty_assertions::assert_eq;

use super::*;
use crate::tests::harness::*;

const TEST_CLASS: &str = "com.intellij.air.ui.AirRenameUiTest";
const SCENARIO: &str = r#"{"op":"scenario","name":"rename","testClass":"com.intellij.air.ui.AirRenameUiTest","lane":"UI","flow":"rename-session","labels":[{"name":"testMethod","value":"renamesTheSession"},{"name":"feature","value":"Sessions"},{"name":"traceBundle","value":"the lane's own"}]}"#;
const OPERATION: &str = r#"{"op":"span","id":1,"kind":"operation","key":"operation:rename","title":"Rename the session"}"#;
const ACTION: &str = r#"{"op":"span","id":2,"parent":1,"kind":"action","key":"action:open-dialog","title":"Open the dialog"}"#;
const ASSERTION: &str = r#"{"op":"span","id":3,"parent":1,"kind":"assertion","key":"assertion:renamed","title":"The row is renamed","expectation":"The row shows the new name."}"#;
const SNAP_ACTION: &str = r#"{"op":"snap","span":2,"phase":"boundary"}"#;
const SNAP_ASSERTION: &str = r#"{"op":"snap","span":3,"phase":"boundary"}"#;
const END_ACTION: &str = r#"{"op":"end","id":2,"status":"passed"}"#;
const END_ASSERTION: &str =
    r#"{"op":"end","id":3,"status":"failed","error":{"type":"java.lang.AssertionError","message":"the row keeps the old name"}}"#;
const END_OPERATION: &str = r#"{"op":"end","id":1,"status":"failed","error":{"type":"java.lang.AssertionError"}}"#;
const DONE: &str = r#"{"op":"done","status":"failed","message":"the row keeps the old name","trace":"java.lang.AssertionError\nat AirRenameUiTest.renamesTheSession(AirRenameUiTest.kt:42)"}"#;

fn attach_line(file: &Path, span: u32) -> String {
    let command = Command::Attach(AttachCommand {
        name: "The table".to_owned(),
        mime: "text/plain".to_owned(),
        file: file.to_string_lossy().into_owned(),
        span,
    });
    String::from_utf8(encode_command(&command).unwrap()).unwrap()
}

/// Every result of the run `run-1`, with the bytes of each file the result names, by the file's name.
fn results_of(h: &Harness) -> Vec<(AllureResult, PathBuf)> {
    let dir = allure_results_dir(&h.root, "run-1");
    let mut results: Vec<(AllureResult, PathBuf)> = fs::read_dir(&dir)
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .filter(|path| path.to_string_lossy().ends_with(ALLURE_RESULT_SUFFIX))
        .map(|path| (decode_allure_result(&fs::read(&path).unwrap()).unwrap(), dir.clone()))
        .collect();
    results.sort_by_key(|(result, _)| result.start);
    results
}

/// Feeds one failed scenario: an operation with an action and a failed assertion, a snapshot in each of the two, the
/// dialog on the second snapshot, and one lane file on the assertion.
fn feed_failed_scenario(h: &Harness, session: &mut crate::session::Session<crate::testing::SharedBuffer>) {
    let lane_dir = h.root.join("run-1").join("attach");
    fs::create_dir_all(&lane_dir).unwrap();
    let table = lane_dir.join(format!("{}.txt", h.clock.ms()));
    fs::write(&table, "row name\nold\n").unwrap();
    h.feed(session, &[SCENARIO, OPERATION, ACTION, SNAP_ACTION, END_ACTION, ASSERTION]);
    h.ide.show(SCREEN_DIALOG, Vec::new());
    h.feed(
        session,
        &[SNAP_ASSERTION, &attach_line(&table, 3), END_ASSERTION, END_OPERATION, DONE],
    );
}

fn label(result: &AllureResult, name: &str) -> Vec<String> {
    result
        .labels
        .iter()
        .filter(|label| label.name == name)
        .map(|label| label.value.clone())
        .collect()
}

/// A failed scenario becomes one failed result. The spans are the steps, each with its Before and After picture,
/// the lane file sits on its step, and `done` gives the details. A picture that three steps name is one file.
#[test]
fn a_failed_scenario_becomes_a_result_with_its_steps_pictures_and_attachment() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(&mut session, &[&h.hello(false)]);
    feed_failed_scenario(&h, &mut session);

    let results = results_of(&h);
    assert_eq!(results.len(), 1, "one scenario writes one result");
    let (result, dir) = &results[0];
    let bundle = bundle_dir(&h.root, "run-1", TEST_CLASS, "rename");
    assert_eq!(
        (result.name.as_str(), result.full_name.as_str(), result.status),
        ("rename", "com.intellij.air.ui.AirRenameUiTest.rename", AllureStatus::Failed)
    );
    assert_eq!(result.history_id, allure_history_id(&result.full_name));
    assert_eq!(result.status_details.message.as_deref(), Some("the row keeps the old name"));
    assert_eq!(
        result.status_details.trace.as_deref(),
        Some("java.lang.AssertionError\nat AirRenameUiTest.renamesTheSession(AirRenameUiTest.kt:42)")
    );
    for (name, want) in [
        ("testMethod", "renamesTheSession"),
        ("feature", "Sessions"),
        ("suite", "AirRenameUiTest"),
        ("testClass", TEST_CLASS),
        ("package", "com.intellij.air.ui"),
        ("host", GOLDEN_HOST),
        ("lane", "UI"),
        ("flow", "rename-session"),
        ("traceBundle", "com.intellij.air.ui.AirRenameUiTest/rename"),
    ] {
        assert_eq!(label(result, name), [want], "the label {name}");
    }

    let titles = |attachments: &[AllureAttachment]| attachments.iter().map(|a| a.name.clone()).collect::<Vec<_>>();
    assert_eq!(titles(&result.attachments), [BUNDLE]);
    assert_eq!(
        fs::read_to_string(dir.join(&result.attachments[0].source)).unwrap(),
        "com.intellij.air.ui.AirRenameUiTest/rename\n"
    );
    let [operation] = result.steps.as_slice() else {
        panic!("the root has one step: {:?}", result.steps);
    };
    let [action, assertion] = operation.steps.as_slice() else {
        panic!("the operation has two steps: {:?}", operation.steps);
    };
    let shape = |step: &AllureStep| (step.name.clone(), step.status, titles(&step.attachments));
    assert_eq!(
        [shape(operation), shape(action), shape(assertion)],
        [
            (
                "Rename the session".to_owned(),
                AllureStatus::Failed,
                vec![BEFORE.to_owned(), AFTER.to_owned()]
            ),
            (
                "Open the dialog".to_owned(),
                AllureStatus::Passed,
                vec![BEFORE.to_owned(), AFTER.to_owned()]
            ),
            (
                "The row is renamed".to_owned(),
                AllureStatus::Failed,
                vec![BEFORE.to_owned(), "The table".to_owned()]
            ),
        ]
    );
    assert_eq!(
        assertion.status_details.message.as_deref(),
        Some("java.lang.AssertionError: the row keeps the old name")
    );
    // The action's After is the assertion's Before, and the operation's After is its last picture.
    let source = |step: &AllureStep, index: usize| step.attachments[index].source.clone();
    assert_eq!(source(operation, 0), source(action, 0));
    assert_eq!(source(action, 1), source(assertion, 0));
    assert_eq!(source(operation, 1), source(assertion, 0));
    assert_ne!(source(action, 0), source(action, 1));
    assert_eq!(assertion.attachments[0].mime, "image/webp");
    let bytes = |name: &str| fs::read(dir.join(name)).unwrap();
    assert_eq!(
        bytes(&source(action, 0)),
        fs::read(bundle_file(&bundle, &snap_image_path(1))).unwrap()
    );
    assert_eq!(
        bytes(&source(action, 1)),
        fs::read(bundle_file(&bundle, &snap_image_path(2))).unwrap()
    );
    assert_eq!(bytes(&source(assertion, 1)), b"row name\nold\n");
    let files = fs::read_dir(dir).unwrap().count();
    assert_eq!(files, 5, "the result, the bundle's name, two pictures and the lane file");
}

/// A retry of one scenario in one run has a uuid of its own and the history of the first attempt. A replay of the
/// same lines writes the same result.
#[test]
fn a_retry_shares_the_history_and_a_replay_writes_the_same_result() {
    let write = || {
        let h = Harness::new();
        let mut session = h.session(h.options());
        h.feed(&mut session, &[&h.hello(false)]);
        feed_failed_scenario(&h, &mut session);
        feed_failed_scenario(&h, &mut session);
        let results: Vec<AllureResult> = results_of(&h).into_iter().map(|(result, _)| result).collect();
        (h, results)
    };
    let (_first_harness, first) = write();
    let (_second_harness, second) = write();
    assert_eq!(first, second, "two replays of one transcript wrote different results");
    let [attempt, retry] = first.as_slice() else {
        panic!("two attempts write two results: {first:?}");
    };
    assert_ne!(attempt.uuid, retry.uuid);
    assert_eq!(attempt.history_id, retry.history_id);
    assert_eq!(label(retry, "traceBundle"), ["com.intellij.air.ui.AirRenameUiTest/rename.2"]);
}

/// A scenario the lane left before `done` is broken, and the message names the span that was running.
#[test]
fn a_truncated_scenario_is_broken_and_names_its_running_span() {
    let h = Harness::new();
    let mut session = h.session(h.options());
    h.feed(&mut session, &[&h.hello(false), SCENARIO, OPERATION, ACTION]);
    session.close("the test ended");
    let results = results_of(&h);
    let [(result, _)] = results.as_slice() else {
        panic!("a truncated scenario writes one result");
    };
    assert_eq!(result.status, AllureStatus::Broken);
    assert_eq!(
        result.status_details.message.as_deref(),
        Some(r#"the lane stopped before done, in the span "Open the dialog""#)
    );
    assert_eq!(result.steps[0].status, AllureStatus::Broken);
}

/// When the hard link fails, as it does across two file systems, the linker copies the file under a partial name and
/// renames it into place. The copy keeps the bytes it had, and a file that two steps name is still one attachment.
#[test]
fn an_attachment_is_copied_when_the_link_fails() {
    let root = tempfile::tempdir().unwrap();
    let dir = root.path().join("bundle");
    let results = root.path().join(avl_trace::bundle::ALLURE_RESULTS_DIR);
    let picture = snap_image_path(1);
    fs::create_dir_all(bundle_file(&dir, &picture).parent().unwrap()).unwrap();
    fs::create_dir_all(&results).unwrap();
    let links: [(&str, LinkFile, &[u8]); 2] = [
        ("the hard link", |from, to| fs::hard_link(from, to), b"changed"),
        (
            "the copy",
            |_, _| Err(io::Error::other("a link across two file systems")),
            b"picture",
        ),
    ];
    for (how, link, kept) in links {
        fs::write(bundle_file(&dir, &picture), "picture").unwrap();
        let mut linker = Linker {
            dir: &dir,
            results: &results,
            uuid: "11111111-1111-8111-8111-111111111111",
            linked: HashMap::new(),
            count: 0,
            problems: Vec::new(),
            link,
        };
        let first = linker.required(BEFORE, MIME_WEBP, &picture).unwrap();
        let second = linker.required(AFTER, MIME_WEBP, &picture).unwrap();
        assert_eq!(first.source, second.source, "{how}: one bundle file is two attachments");
        assert_eq!(linker.problems, Vec::<String>::new(), "{how}");
        fs::write(bundle_file(&dir, &picture), "changed").unwrap();
        assert_eq!(fs::read(results.join(&first.source)).unwrap(), kept, "{how}");
        let left: Vec<String> = fs::read_dir(&results)
            .unwrap()
            .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
            .filter(|name| name.ends_with(avl_trace::bundle::PARTIAL_SUFFIX))
            .collect();
        assert_eq!(left, Vec::<String>::new(), "{how}: a partial file is left");
        fs::remove_dir_all(&results).unwrap();
        fs::create_dir_all(&results).unwrap();
    }
}
