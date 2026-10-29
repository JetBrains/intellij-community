use std::path::Path;

use pretty_assertions::assert_eq;

use super::{ModuleRow, PackageRow, PluginRow, WindowCounts, host, package, report};
use crate::bench::arm::Arm;
use crate::bench::classload::ClockAnchor;
use crate::bench::options::{ClassesArgs, DEFAULT_TOP, SpanThread};
use crate::bench::testing::{assert_golden, empty_editor_run, fixture_session, summarize};

/// The span of the tests. Its window is 2000 to 3000 ms on the process clock, and 2358 to 3358 ms on the JVM clock of
/// the fixture, whose main class loads at 358 ms.
const SPAN: &str = "air.emptyState.createContent";

/// The lines that the tests add to the class-load log of the fixture: a lambda of a Markdown class on the EDT, and a
/// lambda form of the JDK on the EDT, both in the window.
const HIDDEN_LINES: &str = "\
[2.500s][12291][class,load] org.intellij.plugins.markdown.lang.MarkdownFileType$$Lambda/0x0000000801500000 source: org.intellij.plugins.markdown.lang.MarkdownFileType
[3.000s][12291][class,load] java.lang.invoke.LambdaForm$MH/0x0000000801500400 source: __JVM_LookupDefineClass__
";

fn args(thread: SpanThread) -> ClassesArgs {
    ClassesArgs {
        session: String::new(),
        arm: None,
        top: DEFAULT_TOP,
        plugins: Vec::new(),
        at: None,
        entries: None,
        span: Some(SPAN.to_owned()),
        thread,
        run: None,
        timeline: None,
    }
}

/// A session whose only arm is the empty-editor arm, with one run: the project fixture with the composer spans, the
/// span of the tests, and [`HIDDEN_LINES`].
fn span_session(session: &Path) {
    let run = empty_editor_run(
        session,
        &[
            ("air.emptyState.createComponent", 1900, 1200),
            (SPAN, 2000, 1000),
            (SPAN, 6000, 100),
        ],
    );
    let log = std::fs::read_to_string(run.join("class-load.log")).expect("a log");
    std::fs::write(run.join("class-load.log"), format!("{log}{HIDDEN_LINES}")).expect("a log");
    summarize(session, vec![Arm::EMPTY_EDITOR], &["empty-editor-run-01"]);
}

/// The EDT loads of the first span: the Markdown classes by their modules, a platform class by its package, and the
/// hidden classes with their hosts. The loads of the main thread count apart. The text equals
/// `testdata/bench/classes-span.txt`.
#[test]
fn the_edt_loads_of_the_window_match_the_golden() {
    let (_dir, session) = fixture_session();
    span_session(&session);
    let mut args = args(SpanThread::Edt);
    args.timeline = Some(500);
    let report = report(&session, &args).expect("a report");
    assert_eq!((report.arm.clone(), report.runs.len()), (Arm::EMPTY_EDITOR, 1));
    let run = &report.runs[0];
    assert_eq!((run.clock, run.start_ms), (ClockAnchor::MainClass, 2000.0));
    assert_eq!(
        run.counts,
        WindowCounts {
            duration_ms: 1000.0,
            loads: 6.0,
            hidden: 2.0,
            find_ms: 0.3,
            other_loads: Some(1.0),
            other_find_ms: Some(0.0),
        },
        "the second span at 6000 ms moves no number"
    );
    assert_eq!(
        report.by_plugin,
        [PluginRow {
            plugin: "org.intellij.plugins.markdown".to_owned(),
            loads: 4.0,
            hidden: 1.0,
            modules: 2.0,
            find_ms: 0.3,
        }]
    );
    assert_eq!(
        report.by_module,
        [
            ModuleRow {
                plugin: "org.intellij.plugins.markdown".to_owned(),
                module: None,
                loads: 3.0,
                hidden: 1.0,
                find_ms: 0.2,
            },
            ModuleRow {
                plugin: "org.intellij.plugins.markdown".to_owned(),
                module: Some("intellij.markdown.core".to_owned()),
                loads: 1.0,
                hidden: 0.0,
                find_ms: 0.1,
            },
        ]
    );
    assert_eq!(
        report.by_package,
        [
            PackageRow {
                package: "com.intellij.openapi.fileEditor".to_owned(),
                jdk: false,
                loads: 1.0,
                hidden: 0.0,
            },
            PackageRow {
                package: "java.lang.invoke".to_owned(),
                jdk: true,
                loads: 1.0,
                hidden: 1.0,
            },
        ]
    );
    let timeline = report.timeline.as_ref().expect("a timeline");
    let buckets: Vec<(u32, u64, usize)> = timeline
        .buckets
        .iter()
        .map(|bucket| (bucket.from_ms, bucket.loads, bucket.first.len()))
        .collect();
    assert_eq!(buckets, [(0, 4, 3), (500, 2, 1)]);
    let text = format!("{}\n", report.text().replace(&session.display().to_string(), "<session>"));
    assert_golden("classes-span.txt", &text);
}

/// `--thread all` counts the loads of every thread in the tables and has no line of the other threads.
#[test]
fn every_thread_counts_the_main_thread_too() {
    let (_dir, session) = fixture_session();
    span_session(&session);
    let report = report(&session, &args(SpanThread::All)).expect("a report");
    let counts = report.runs[0].counts;
    assert_eq!((counts.loads, counts.other_loads, counts.other_find_ms), (7.0, None, None));
    assert!(
        report.by_package.iter().any(|row| row.package == "com.intellij.codeInsight.daemon"),
        "{:?}",
        report.by_package
    );
}

/// A session without the span is a usage refusal that names the span, and `--thread edt` without the name of the EDT
/// is a usage refusal that names the thread dump.
#[test]
fn a_missing_span_or_a_missing_edt_is_refused() {
    let (_dir, session) = fixture_session();
    span_session(&session);
    let mut other = args(SpanThread::Edt);
    other.span = Some("air.emptyState.none".to_owned());
    let refusal = report(&session, &other).expect_err("no span");
    assert_eq!(refusal.code, "usage");
    assert!(refusal.message.contains("has a air.emptyState.none span"), "{}", refusal.message);

    std::fs::remove_file(session.join("empty-editor-run-01").join("threads.txt")).expect("a removal");
    let refusal = report(&session, &args(SpanThread::Edt)).expect_err("no EDT");
    assert!(refusal.message.contains("has no threads.txt"), "{}", refusal.message);
    assert!(report(&session, &args(SpanThread::All)).is_ok(), "every thread needs no EDT");
}

/// A hidden class counts with the class before `/0x` and `$$`, and a package keeps four names.
#[test]
fn a_hidden_class_has_a_host_and_a_package_keeps_four_names() {
    assert_eq!(host("a.B$$Lambda/0x0000000801500000"), "a.B");
    assert_eq!(host("a.B$$TypeSwitch/0x0000000801500000"), "a.B");
    assert_eq!(
        host("java.lang.invoke.LambdaForm$MH/0x0000000801500400"),
        "java.lang.invoke.LambdaForm$MH"
    );
    assert_eq!(package("com.intellij.openapi.wm.impl.ToolWindowImpl"), "com.intellij.openapi.wm");
    assert_eq!(package("java.lang.invoke.LambdaForm$MH"), "java.lang.invoke");
    assert_eq!(package("Top"), "(default)");
}
