use std::path::Path;

use avl_base::Reporter;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::{ClassRow, ModuleRow, report as classes};
use crate::bench::arm::Arm;
use crate::bench::command::replay;
use crate::bench::options::{ClassesArgs, DEFAULT_TOP};
use crate::bench::record;
use crate::bench::session::SUMMARY_FILE;
use crate::bench::testing::{assert_golden, fixture_session, project_session, summarize};

fn args() -> ClassesArgs {
    ClassesArgs {
        session: String::new(),
        arm: None,
        top: DEFAULT_TOP,
        plugins: Vec::new(),
        at: None,
        entries: None,
    }
}

/// A row of the plugin table with the EDT and BG milliseconds of a timed plugin log.
fn row(name: &str, classes: f64, loaders: f64, edt_ms: f64) -> ClassRow {
    ClassRow {
        name: name.to_owned(),
        classes,
        loaders: Some(loaders),
        edt_ms: Some(edt_ms),
        bg_ms: Some(0.0),
    }
}

/// The report of the replayed fixture takes the non-modal arm and its main anchor, the welcome event, and its text
/// equals `testdata/bench/classes.txt`.
#[test]
fn the_classes_of_the_fixture_match_the_golden() {
    let (_dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    let report = classes(&session, &args()).expect("a report");
    assert_eq!(
        (report.arm.clone(), report.at.as_deref(), report.runs),
        (Arm::NON_MODAL, Some("welcomeBecameVisible"), 1)
    );
    let text = format!("{}\n", report.text().replace(&session.display().to_string(), "<session>"));
    assert_golden("classes.txt", &text);

    let json = serde_json::to_value(&report).expect("JSON");
    let keys: Vec<&str> = json.as_object().expect("an object").keys().map(String::as_str).collect();
    assert_eq!(keys, ["arm", "at", "byModule", "byPlugin", "missing", "runs", "session", "totals"]);
    assert_eq!(json["arm"], "nonModal");
    assert_eq!(json["totals"]["classes.named@welcomeBecameVisible"]["median"], 30.0);
    assert_eq!(json["byPlugin"][0], json!({"name": "com.intellij", "classes": 4.0, "loaders": 2.0}));
    assert_eq!(
        json["byModule"][2],
        json!({"plugin": "com.jetbrains.performancePlugin", "module": null, "classes": 2.0}),
        "a main module is a row too"
    );
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
    assert_eq!(report.arm, Arm::PROJECT, "the only arm with a valid run");
    assert_eq!(
        report.by_plugin,
        [
            row(markdown, 3.0, 2.0, 0.3),
            row("com.intellij.python", 0.0, 0.0, 0.0),
            row("intellij.webp", 0.0, 0.0, 0.0)
        ],
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

    let default = classes(&session, &ClassesArgs { top: 2, ..args() }).expect("a report");
    assert_eq!(
        default.at.as_deref(),
        Some(record::EDITOR_HIGHLIGHTED),
        "the main anchor of the project arm"
    );
    assert_eq!(
        default.by_plugin,
        [row(markdown, 3.0, 2.0, 0.3), row("com.intellij", 2.0, 1.0, 0.1)]
    );
    assert!(
        default
            .text()
            .contains("\nby plugin at editor highlighting completed, 2 of 3 plugins:\n"),
        "{}",
        default.text()
    );
    let quit = ClassesArgs {
        top: 2,
        at: Some("quit".to_owned()),
        ..args()
    };
    let whole = classes(&session, &quit).expect("a report");
    assert_eq!(whole.by_plugin, [row(markdown, 3.0, 2.0, 0.3), row("com.intellij", 2.0, 1.0, 0.1)]);
    assert!(whole.text().contains("\nby plugin at quit, 2 of 8 plugins:\n"), "{}", whole.text());
}

/// `--entries` lists the first classes of each plugin of the plugin table that the median run loaded before the
/// anchor, in ms from the process start: the uptime of the JVM log minus `jvm.loadingTime`, 358 ms in the fixture. The
/// text equals `testdata/bench/classes-entries.txt`.
#[test]
fn the_entries_are_the_first_classes_of_each_plugin_before_the_anchor() {
    let (_dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    let report = classes(
        &session,
        &ClassesArgs {
            entries: Some(2),
            ..args()
        },
    )
    .expect("a report");
    let text = format!("{}\n", report.text().replace(&session.display().to_string(), "<session>"));
    assert_golden("classes-entries.txt", &text);
    let json = serde_json::to_value(&report).expect("JSON");
    assert_eq!(json["entries"]["run"], "non-modal-run-01");
    assert_eq!(
        json["entries"]["plugins"][0],
        json!({"plugin": "com.intellij", "classes": [
            {"name": "com.intellij.platform.kernel.backend.BackendKernelService", "ms": 942.0},
            {"name": "com.intellij.platform.kernel.backend.BackendKernelServiceKt", "ms": 952.0},
        ]})
    );

    let named = classes(
        &session,
        &ClassesArgs {
            entries: Some(1),
            at: Some("quit".to_owned()),
            plugins: vec!["com.intellij.dfa.analysis".to_owned(), "com.intellij.python".to_owned()],
            ..args()
        },
    )
    .expect("a report");
    let text = named.text();
    assert!(
        text.contains(
            "\nthe first class of each plugin at quit, in non-modal-run-01 (ms from the process start):\n  \
             com.intellij.dfa.analysis:\n     2842  com.intellij.dfa.analysis.rml.utils.RmlServiceResolver\n  \
             com.intellij.python: none\n"
        ),
        "the quit bound keeps the late classes: {text}"
    );
}

/// `--entries` reads the class logs of the median run, so an arm whose run has no class-load log is a usage refusal.
#[test]
fn the_entries_of_a_run_without_its_class_log_are_refused() {
    let (_dir, session) = fixture_session();
    std::fs::remove_file(session.join("modal-run-01/class-load.log")).expect("a removal");
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    let refusal = classes(
        &session,
        &ClassesArgs {
            arm: Some(Arm::MODAL),
            entries: Some(3),
            ..args()
        },
    )
    .expect_err("no class-load.log");
    assert_eq!(refusal.code, "usage");
    assert_eq!(
        refusal.message,
        "--entries reads class-load.log of the median run modal-run-01, and it has none"
    );
}

/// An anchor that the arm does not have, and an arm that the session does not have, are usage refusals. A modal run
/// without its class-load log has no anchor, and its plugin table covers the whole run.
#[test]
fn an_anchor_or_an_arm_that_the_session_lacks_is_a_usage_refusal() {
    let (_dir, session) = fixture_session();
    std::fs::remove_file(session.join("modal-run-01/class-load.log")).expect("a removal");
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    let refusal = |args: ClassesArgs| classes(&session, &args).expect_err("a refusal");
    let modal = refusal(ClassesArgs {
        arm: Some(Arm::MODAL),
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
        arm: Some(Arm::PROJECT),
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
            arm: Some(Arm::MODAL),
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

/// The module table follows the anchor, holds the main module of a plugin as `<plugin id> (main)`, and names the plugin
/// of each module. With `--plugin`, it holds the modules of the named plugins, and a rank line follows the plugin
/// table. The plugin log of the project run has the new shape, so the tables have EDT and BG times.
#[test]
fn the_module_table_follows_the_anchor_and_names_the_plugin_of_each_module() {
    let (_dir, session) = fixture_session();
    project_session(&session);
    let markdown = "org.intellij.plugins.markdown";
    let report = classes(
        &session,
        &ClassesArgs {
            plugins: vec![markdown.to_owned()],
            ..args()
        },
    )
    .expect("a report");
    assert_eq!(report.at.as_deref(), Some(record::EDITOR_HIGHLIGHTED));
    let module = |module: Option<&str>, classes: f64, edt_ms: f64| ModuleRow {
        plugin: markdown.to_owned(),
        module: module.map(str::to_owned),
        classes,
        edt_ms: Some(edt_ms),
        bg_ms: Some(0.0),
    };
    assert_eq!(
        report.by_module,
        [module(None, 2.0, 0.2), module(Some("intellij.markdown.core"), 1.0, 0.1)]
    );
    let text = report.text();
    assert!(
        text.contains(
            "\norg.intellij.plugins.markdown: rank 1 of 3 plugins, 50.0 % of the plugin classes at editor highlighting completed\n"
        ),
        "{text}"
    );
    assert!(
        text.contains(
            "\n  org.intellij.plugins.markdown (main)                             2     0.2       0  org.intellij.plugins.markdown\n"
        ),
        "{text}"
    );
    assert!(!text.contains("note:"), "a timed log needs no note: {text}");

    let whole = classes(
        &session,
        &ClassesArgs {
            at: Some("quit".to_owned()),
            ..args()
        },
    )
    .expect("a report");
    assert_eq!(whole.total_modules, 9, "the modules with a class before the quit");
    assert!(whole.ranks.is_empty(), "a rank needs --plugin");
}

/// A twin of `--suppress-plugin` is an arm of `--arm`, by its label.
#[test]
fn a_twin_arm_answers_its_own_tables() {
    let (_dir, session) = fixture_session();
    let twin = Arm::PROJECT.without("com.intellij.air");
    let twin_run = "project-without-com.intellij.air-run-01";
    avl_testkit::traces::copy_tree(&session.join("project-run-01"), &session.join(twin_run));
    summarize(&session, vec![Arm::PROJECT, twin.clone()], &["project-run-01", twin_run]);
    let report = classes(
        &session,
        &ClassesArgs {
            arm: Some(twin.clone()),
            ..args()
        },
    )
    .expect("a report");
    assert_eq!((report.arm.clone(), report.runs), (twin, 1));
    assert!(
        report
            .text()
            .starts_with("vm bench classes session project-without-com.intellij.air arm, 1 valid run\n"),
        "{}",
        report.text()
    );
    let json = serde_json::to_value(&report).expect("JSON");
    assert_eq!(json["arm"], "project-without-com.intellij.air");
}
