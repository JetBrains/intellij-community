use pretty_assertions::assert_eq;
use serde_json::json;

use super::{NO_OTHER_OWNER, NO_STACK, report, strip_frame_prefix, trigger_frame};
use crate::bench::arm::Arm;
use crate::bench::options::{DEFAULT_TOP, RunSelection, TriggersArgs};
use crate::bench::pluginlog::{self, Owner};
use crate::bench::record::EDITOR_HIGHLIGHTED;
use crate::bench::testing::{assert_golden, fixture_session, project_session, replayed_session};

fn args() -> TriggersArgs {
    TriggersArgs {
        selection: RunSelection {
            session: String::new(),
            run: None,
            arm: None,
        },
        plugins: Vec::new(),
        at: None,
        top: DEFAULT_TOP,
    }
}

fn frames(frames: &[&str]) -> Vec<String> {
    frames.iter().map(|frame| (*frame).to_owned()).collect()
}

#[test]
fn a_frame_loses_the_prefix_of_its_loader_and_its_module() {
    assert_eq!(
        strip_frame_prefix("app//com.intellij.Foo.bar(Foo.kt:12)"),
        "com.intellij.Foo.bar(Foo.kt:12)"
    );
    assert_eq!(
        strip_frame_prefix("java.base/java.lang.Thread.run(Thread.java:1583)"),
        "java.lang.Thread.run(Thread.java:1583)"
    );
    assert_eq!(
        strip_frame_prefix("app/java.base@25/java.lang.Thread.run(Thread.java:1583)"),
        "java.lang.Thread.run(Thread.java:1583)"
    );
    assert_eq!(
        strip_frame_prefix("app//com.intellij.Foo$$Lambda/0x0000000801001000.invoke(Unknown Source)"),
        "com.intellij.Foo$$Lambda/0x0000000801001000.invoke(Unknown Source)",
        "the `/` of a hidden class stays"
    );
    assert_eq!(
        strip_frame_prefix("com.intellij.Foo.bar(Foo.kt:12)"),
        "com.intellij.Foo.bar(Foo.kt:12)"
    );
}

/// The trigger is the nearest frame to the top whose class has another owner. A frame of the runtime or of the Kotlin
/// libraries does not count, and a class without an owner is a platform class. A lambda counts as its host class.
#[test]
fn the_trigger_is_the_nearest_frame_of_another_owner() {
    let log = pluginlog::parse(
        "a.First [sub = a.one.xml] a edt 1\n\
         a.Main [m] a bg 1\n\
         b.Other [m] b bg 1\n",
    )
    .expect("a plugin log");
    let one = Owner {
        plugin: "a".to_owned(),
        module: Some("a.one".to_owned()),
    };
    let trigger = |stack: &[&str]| trigger_frame(&one, &frames(stack), &log);
    assert_eq!(
        trigger(&[
            "java.base/java.util.ServiceLoader$ProviderImpl.newInstance(ServiceLoader.java:789)",
            "kotlin.sequences.SequencesKt.toList(_Sequences.kt:1)",
            "kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:1)",
            "sun.reflect.Foo.bar(Foo.java:1)",
            "jdk.internal.Foo.bar(Foo.java:1)",
            "app//com.intellij.Platform.load(Platform.kt:3)",
        ]),
        Some("com.intellij.Platform.load(Platform.kt:3)".to_owned())
    );
    assert_eq!(
        trigger(&["a.First.own(First.kt:1)", "a.Main.call(Main.kt:2)"]),
        Some("a.Main.call(Main.kt:2)".to_owned()),
        "the main module is another loader than a content module of the same plugin"
    );
    assert_eq!(
        trigger(&["b.Other$$Lambda/0x01.invoke(Unknown Source)"]),
        Some("b.Other$$Lambda/0x01.invoke(Unknown Source)".to_owned())
    );
    assert_eq!(
        trigger(&["a.First$$Lambda/0x02.invoke(Unknown Source)", "java.lang.Thread.run(Thread.java:1)"]),
        None
    );
    assert_eq!(trigger(&[]), None);
}

/// The triggers of the project run before the quit: the record grouped its loaders by the trigger frame of their
/// activation stack. The text equals `testdata/bench/triggers.txt`.
#[test]
fn the_triggers_of_the_project_run_match_the_golden() {
    let (_dir, session) = fixture_session();
    project_session(&session);
    let report = report(
        &session,
        &TriggersArgs {
            at: Some("quit".to_owned()),
            ..args()
        },
    )
    .expect("a report");
    assert_eq!(
        (report.run.as_str(), report.arm.clone(), report.median_by),
        ("project-run-01", Arm::PROJECT, Some(EDITOR_HIGHLIGHTED))
    );
    assert_eq!((report.loaders, report.total_triggers), (9, 8));
    let rows: Vec<&str> = report.triggers.iter().map(|row| row.trigger.as_str()).collect();
    assert!(rows.contains(&NO_STACK) && rows.contains(&NO_OTHER_OWNER), "{rows:?}");
    assert_golden("triggers.txt", &format!("{}\n", report.text()));
}

/// The default anchor is the main anchor of the arm. `--plugin` keeps the loaders of the named plugins, and `--top`
/// cuts the rows. A trigger row sums the classes and the EDT ms of its loaders and keeps the first load of the earliest
/// one.
#[test]
fn the_triggers_take_the_anchor_the_plugins_and_the_top() {
    let (_dir, session) = fixture_session();
    project_session(&session);
    let report = report(
        &session,
        &TriggersArgs {
            plugins: vec!["org.intellij.plugins.markdown".to_owned()],
            ..args()
        },
    )
    .expect("a report");
    assert_eq!(report.at.as_deref(), Some(EDITOR_HIGHLIGHTED));
    assert_eq!(report.loaders, 2);
    let json = serde_json::to_value(&report).expect("JSON");
    assert_eq!(
        json["triggers"][0],
        json!({
            "trigger": "com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl.instantiateFileTypeBean(FileTypeManagerImpl.kt:510)",
            "classes": 2,
            "edtMs": 0.2,
            "firstLoadMs": 2022.0,
            "totalLoaders": 1,
            "loaders": [{
                "plugin": "org.intellij.plugins.markdown",
                "module": null,
                "classes": 2,
                "edtMs": 0.2,
                "firstLoadMs": 2022.0,
                "firstClass": "org.intellij.plugins.markdown.lang.MarkdownFileType",
            }],
        })
    );
    assert_eq!(
        json["triggers"][1]["trigger"], "org.intellij.plugins.markdown.lang.MarkdownLanguage.getParser(MarkdownLanguage.kt:18)",
        "the main module triggers the content module of its own plugin"
    );

    let top = report_of(&session, &TriggersArgs { top: 1, ..args() });
    assert_eq!((top.triggers.len(), top.total_triggers, top.loaders), (1, 4, 4));
    let refusal = super::report(
        &session,
        &TriggersArgs {
            at: Some("welcomeBecameVisible".to_owned()),
            ..args()
        },
    )
    .expect_err("a refusal");
    assert!(
        refusal
            .message
            .ends_with("has no class count at welcomeBecameVisible; its anchors are editor highlighting completed, quit"),
        "{}",
        refusal.message
    );
}

fn report_of(session: &std::path::Path, args: &TriggersArgs) -> super::TriggerReport {
    report(session, args).expect("a report")
}

/// A plugin log of the old shape has no activation stack, so every loader is in the row of [`NO_STACK`], and a note
/// says why.
#[test]
fn a_plugin_log_of_the_old_shape_puts_every_loader_in_one_row() {
    let (_dir, session) = replayed_session();
    let report = report_of(&session, &args());
    assert_eq!(report.run, "non-modal-run-01");
    assert_eq!(report.triggers.len(), 1);
    assert_eq!(report.triggers[0].trigger, NO_STACK);
    assert_eq!(report.triggers[0].edt_ms, None);
    let text = report.text();
    assert!(
        text.ends_with(
            "note: no loader has an activation stack: plugin-classes.txt of the run has the old shape, without the thread, \
             the time and the stack of a class"
        ),
        "{text}"
    );
}
