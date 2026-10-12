use std::path::Path;

use avl_base::Reporter;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::{ClassRow, report as classes};
use crate::bench::arm::Arm;
use crate::bench::command::replay;
use crate::bench::files;
use crate::bench::options::{ClassesArgs, DEFAULT_TOP};
use crate::bench::record::{self, LaunchFacts, RunId};
use crate::bench::session::{self, SUMMARY_FILE};
use crate::bench::summary::Summary;
use crate::bench::testing::{assert_golden, fixture_session};

fn args() -> ClassesArgs {
    ClassesArgs {
        session: String::new(),
        arm: None,
        top: DEFAULT_TOP,
        plugins: Vec::new(),
        at: None,
    }
}

fn row(name: &str, classes: f64) -> ClassRow {
    ClassRow {
        name: name.to_owned(),
        classes,
    }
}

/// Writes `summary.json` of a session whose only arm is the project arm, over `project-run-01` of the fixture.
fn project_session(session: &Path) {
    let mut info = session::read_info(session).expect("the session");
    info.arms = vec![Arm::Project];
    let id = RunId::parse("project-run-01").expect("a run");
    let run = record::collect(&session.join("project-run-01"), &id, LaunchFacts::default());
    files::write_json(&session.join("project-run-01/result.json"), &run).expect("result.json");
    let summary = Summary::build(&info, &session.display().to_string(), &[run]);
    files::write_json(&session.join(SUMMARY_FILE), &summary).expect("summary.json");
}

/// The report of the replayed fixture takes the non-modal arm and its last anchor, the quit, and its text equals
/// `testdata/bench/classes.txt`.
#[test]
fn the_classes_of_the_fixture_match_the_golden() {
    let (_dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    let report = classes(&session, &args()).expect("a report");
    assert_eq!((report.arm, report.at.as_deref(), report.runs), (Arm::NonModal, Some("quit"), 1));
    let text = format!("{}\n", report.text().replace(&session.display().to_string(), "<session>"));
    assert_golden("classes.txt", &text);

    let json = serde_json::to_value(&report).expect("JSON");
    let keys: Vec<&str> = json.as_object().expect("an object").keys().map(String::as_str).collect();
    assert_eq!(keys, ["arm", "at", "byModule", "byPlugin", "missing", "runs", "session", "totals"]);
    assert_eq!(json["arm"], "nonModal");
    assert_eq!(json["totals"]["classes.named@quit"]["median"], 32.0);
    assert_eq!(json["byPlugin"][0], json!({"name": "com.intellij", "classes": 4.0}));
    assert_eq!(json["missing"], json!([]));
    assert!(
        summary_has_no_class_map(&session),
        "summary.json holds no class map; result.json holds them"
    );
}

fn summary_has_no_class_map(session: &Path) -> bool {
    let text = std::fs::read_to_string(session.join(SUMMARY_FILE)).expect("summary.json");
    !text.contains("classesByPlugin") && text.contains("classes.named")
}

/// `--at` picks the anchor of the plugin table, and `--plugin` keeps the named plugins only, with a zero row for a
/// plugin without a class before the anchor.
#[test]
fn the_plugin_table_takes_the_anchor_and_the_named_plugins() {
    let (_dir, session) = fixture_session();
    project_session(&session);
    let markdown = "org.intellij.plugins.markdown";
    let report = classes(
        &session,
        &ClassesArgs {
            at: Some(record::EDITOR_HIGHLIGHTED.to_owned()),
            plugins: vec!["intellij.webp".to_owned(), markdown.to_owned(), "com.intellij.python".to_owned()],
            ..args()
        },
    )
    .expect("a report");
    assert_eq!(report.arm, Arm::Project, "the only arm with a valid run");
    assert_eq!(
        report.by_plugin,
        [row(markdown, 3.0), row("com.intellij.python", 0.0), row("intellij.webp", 0.0)],
        "webp loads after the highlighting"
    );
    assert_eq!(report.missing, ["com.intellij.python", "intellij.webp"]);
    assert_eq!(
        report.totals.keys().map(String::as_str).collect::<Vec<_>>(),
        [
            "classes.hidden",
            "classes.jar",
            "classes.jdk",
            "classes.named",
            "classes.named@editor highlighting completed",
            "classes.platform",
            "classes.plugins",
        ]
    );
    let text = report.text();
    assert!(
        text.contains("\nby plugin at editor highlighting completed, the 3 plugins of --plugin:\n"),
        "{text}"
    );
    assert!(
        text.ends_with("\nno class at editor highlighting completed: com.intellij.python, intellij.webp"),
        "{text}"
    );

    let whole = classes(&session, &ClassesArgs { top: 2, ..args() }).expect("a report");
    assert_eq!(whole.at.as_deref(), Some("quit"));
    assert_eq!(whole.by_plugin, [row(markdown, 3.0), row("com.intellij", 2.0)]);
    assert!(whole.text().contains("\nby plugin at quit, 2 of 8 plugins:\n"), "{}", whole.text());
}

/// An anchor that the arm does not have, and an arm that the session does not have, are usage refusals.
#[test]
fn an_anchor_or_an_arm_that_the_session_lacks_is_a_usage_refusal() {
    let (_dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    let refusal = |args: ClassesArgs| classes(&session, &args).expect_err("a refusal");
    let modal = refusal(ClassesArgs {
        arm: Some(Arm::Modal),
        at: Some("quit".to_owned()),
        ..args()
    });
    assert_eq!(modal.code, "usage");
    assert!(
        modal
            .message
            .ends_with("has no class count at quit; it has no anchor: a run needs class-load.log and startup-stats.json for one"),
        "{}",
        modal.message
    );
    let welcome = refusal(ClassesArgs {
        at: Some(record::EDITOR_HIGHLIGHTED.to_owned()),
        ..args()
    });
    assert!(
        welcome.message.ends_with(
            "has no class count at editor highlighting completed; its anchors are frameBecameVisible, welcomeBecameVisible, \
             welcome screen painted, quit"
        ),
        "{}",
        welcome.message
    );
    let project = refusal(ClassesArgs {
        arm: Some(Arm::Project),
        ..args()
    });
    assert!(
        project.message.ends_with("has no project arm; its arms are modal, non-modal"),
        "{}",
        project.message
    );

    let modal = classes(
        &session,
        &ClassesArgs {
            arm: Some(Arm::Modal),
            ..args()
        },
    )
    .expect("a report");
    assert_eq!(modal.at, None, "an arm without an anchor reads the whole run");
    assert!(modal.totals.is_empty(), "{:?}", modal.totals);
    assert!(!modal.by_plugin.is_empty(), "plugin-classes.txt alone gives the plugin table");
    assert!(
        modal.text().contains("\nno class count: the runs have no class-load.log\n"),
        "{}",
        modal.text()
    );
}
