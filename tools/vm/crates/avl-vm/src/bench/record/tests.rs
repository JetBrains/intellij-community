use std::collections::BTreeMap;
use std::path::Path;

use pretty_assertions::assert_eq;

use super::{
    CLASSES_HIDDEN, CLASSES_JAR, CLASSES_JDK, CLASSES_NAMED, CLASSES_PLATFORM, CLASSES_PLUGINS, EDITOR_HIGHLIGHTED, EMPTY_STATE_BUILT,
    EMPTY_STATE_PREFIX, EMPTY_STATE_SPAN, JVM_LOADING_TIME, JVM_PROCESS_START, LaunchFacts, RunId, RunKind, RunRecord, TOTAL_DURATION,
    WELCOME_BECAME_VISIBLE, collect, named_at,
};
use crate::bench::arm::Arm;
use crate::bench::testing::{EMPTY_STATE_FIXTURE_SPANS, append_spans, empty_editor_run, fixture_session};

/// The plugin of the Markdown classes of the project fixture.
const MARKDOWN: &str = "org.intellij.plugins.markdown";

fn counts(pairs: &[(&str, u64)]) -> BTreeMap<String, u64> {
    pairs.iter().map(|(name, count)| ((*name).to_owned(), *count)).collect()
}

fn id(arm: Arm) -> RunId {
    RunId {
        arm,
        kind: RunKind::Measured,
        index: 1,
    }
}

/// The note of the non-modal fixture, whose profile is the one of the whole run.
const WHOLE_RUN_NOTE: &str = "cpu.collapsed is the whole-run profile, with the hold and the quit: the run has no cpu-gate.collapsed";

/// The span ends of a run, see [`super::span_end`], by metric.
fn ends(record: &RunRecord) -> BTreeMap<&str, f64> {
    record
        .metrics
        .iter()
        .filter(|(metric, _)| metric.ends_with(" end"))
        .map(|(metric, value)| (metric.as_str(), *value))
        .collect()
}

/// A welcome span, a feature span of the right tab and a modal span are each the sum of the spans of their name, as
/// the perf test of the welcome screen sums them. A name that repeats is a note. A helper twin never counts.
#[test]
fn a_welcome_span_metric_is_the_sum_of_its_spans_and_a_repeated_name_is_a_note() {
    let (_dir, session) = fixture_session();
    let run = session.join("non-modal-run-01");
    append_spans(
        &run,
        &[
            ("welcome right tab creating", 2400, 10),
            ("welcome right tab creating: scheduled", 2399, 1),
            ("welcome right tab body: createContent trial.started.banner", 2500, 2),
            ("welcome right tab body: createContent whats.new", 2510, 5),
            ("welcome right tab body: createContent whats.new: scheduled", 2509, 1),
            ("welcome frame creating", 2600, 30),
            ("welcome frame creating", 2700, 20),
        ],
    );
    let record = collect(&run, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(record.reason, None);
    let metric = |name: &str| record.metrics.get(name).copied();
    assert_eq!(
        metric("welcome right tab creating"),
        Some(33.7),
        "23.7 ms of the fixture span and 10 ms"
    );
    assert_eq!(metric("welcome right tab body: createContent trial.started.banner"), Some(2.1));
    assert_eq!(metric("welcome right tab body: createContent whats.new"), Some(5.0));
    assert_eq!(metric("welcome frame creating"), Some(50.0));
    assert_eq!(metric("welcome left panel creating"), Some(73.2), "a span of one occurrence");
    assert!(
        !record.metrics.keys().any(|metric| metric.ends_with(": scheduled")),
        "{:?}",
        record.metrics.keys()
    );
    assert_eq!(
        record.notes,
        [
            r#"opentelemetry.json has 2 "welcome right tab creating" spans; the metric is their sum"#,
            r#"opentelemetry.json has 2 "welcome frame creating" spans; the metric is their sum"#,
            r#"opentelemetry.json has 2 "welcome right tab body: createContent trial.started.banner" spans; the metric is their sum"#,
            WHOLE_RUN_NOTE,
        ]
    );
}

/// The end of the first span of each name of the end list is a metric in ms from the process start, the clock of the
/// anchors. A span that the run lacks gives none, and a platform span keeps the duration of its first span. The
/// report gives `jvm.loadingTime`.
#[test]
fn the_span_ends_are_metrics_on_the_clock_of_the_anchors() {
    let (_dir, session) = fixture_session();
    let run = session.join("non-modal-run-01");
    append_spans(&run, &[("restoreEditors", 6000, 50), ("welcome frame creating", 2600, 30)]);
    let record = collect(&run, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert_eq!(record.notes, [WHOLE_RUN_NOTE], "a platform span that repeats is no sum");
    assert_eq!(
        ends(&record),
        BTreeMap::from([
            ("app initialization end", 1225.7),
            ("welcome screen project opening end", 2533.0),
            ("toolwindow creating end", 2294.7),
            ("welcome left panel creating end", 2713.8),
            ("restoreEditors end", 2226.6),
            ("welcome frame creating end", 2630.0),
        ])
    );
    assert_eq!(record.metrics.get("restoreEditors"), Some(&210.7), "the first span");
    assert_eq!(record.metrics.get(JVM_LOADING_TIME), Some(&358.0));
}

/// Without a trace, the report items give the sums and the ends. An item starts in ms from the process start, the
/// clock of the anchors.
#[test]
fn a_run_without_a_trace_takes_the_sums_and_the_ends_from_the_report() {
    let (_dir, session) = fixture_session();
    let run = session.join("project-run-01");
    std::fs::remove_file(run.join("opentelemetry.json")).expect("a removal");
    let path = run.join("startup-stats.json");
    let mut report: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&path).expect("a report")).expect("a report");
    let items = report["items"].as_array_mut().expect("the items");
    for (start, duration) in [(1300, 40), (1400, 2)] {
        items.push(serde_json::json!({"n": "welcome frame creating", "s": start, "d": duration, "t": "edt"}));
    }
    std::fs::write(&path, report.to_string()).expect("a report");
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert_eq!(
        ends(&record),
        BTreeMap::from([
            ("app initialization end", 1225.0),
            ("toolwindow creating end", 2294.0),
            ("restoreEditors end", 2225.0),
            ("welcome frame creating end", 1340.0),
        ])
    );
    assert_eq!(record.metrics.get("welcome frame creating"), Some(&42.0));
    assert_eq!(
        record.notes,
        [
            "no fus.jsonl",
            "no opentelemetry.json",
            r#"startup-stats.json has 2 "welcome frame creating" spans; the metric is their sum"#,
        ]
    );
}

#[test]
fn a_non_modal_run_passes_the_gate_and_has_the_welcome_spans() {
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("non-modal-run-01"), &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert!(record.valid);
    for metric in [
        WELCOME_BECAME_VISIBLE,
        "frameBecameInteractive",
        "totalDuration",
        "classLoading.requests",
        "pluginClasses",
        "ProjectManager.openAsync",
        "welcome screen painted",
        "welcome left toolbar first fill",
        "welcome right tab body: createContent trial.started.banner",
    ] {
        assert!(record.metrics.contains_key(metric), "{metric}: {:?}", record.metrics.keys());
    }
    assert!(!record.metrics.keys().any(|metric| metric.ends_with(": scheduled")));
    assert!(record.edt_samples.is_some_and(|samples| samples > 0));
    assert_eq!(record.metrics.get("classLoading.count"), None, "the report key is a request count");
    assert!(!record.triggers.is_empty(), "the profile gives the class-loading triggers");
    assert!(
        record.define_share.is_some_and(|share| share > 0.0 && share < 1.0),
        "{:?}",
        record.define_share
    );
}

/// A modal run reads the start-up report that the IDE writes at the exit. A start without a project ends no start-up,
/// so a negative or a missing totalDuration is a note and no metric, and the other report metrics stay. The trace starts
/// with `bootstrap`, so the spans of the welcome frame and their ends are on the clock of the anchors.
#[test]
fn a_modal_run_reads_the_report_of_its_exit() {
    let (_dir, session) = fixture_session();
    let run = session.join("modal-run-01");
    let record = collect(&run, &id(Arm::MODAL), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert_eq!(
        record.notes,
        ["startup-stats.json has a negative totalDuration, as a start without a project writes it, so the run has none"]
    );
    let metric = |name: &str| record.metrics.get(name).copied();
    assert_eq!(metric(TOTAL_DURATION), None);
    for (name, value) in [
        (JVM_LOADING_TIME, 352.0),
        ("classLoading.time", 5240.0),
        ("classLoading.edtTime", 26.0),
        ("bootstrap", 44.1),
        ("app initialization", 940.2),
        ("welcome frame EDT waiting", 21.3),
        ("welcome frame creating", 612.4),
        ("welcome frame showing", 466.7),
        ("app initialized callback end", 1254.4),
        ("welcome frame creating end", 1863.0),
        ("classes.named@welcomeBecameVisible", 25.0),
        ("classes.named@quit", 27.0),
    ] {
        assert_eq!(metric(name), Some(value), "{name}");
    }
    assert!(
        !record.metrics.contains_key("ProjectManager.openAsync"),
        "a start without a project has no project span"
    );

    let path = run.join("startup-stats.json");
    let mut report: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&path).expect("a report")).expect("a report");
    report.as_object_mut().expect("an object").remove("totalDuration");
    std::fs::write(&path, report.to_string()).expect("a report");
    let record = collect(&run, &id(Arm::MODAL), LaunchFacts::default());
    assert_eq!(record.notes, ["startup-stats.json has no totalDuration"]);
    assert_eq!(record.metrics.get(JVM_LOADING_TIME), Some(&352.0));

    std::fs::remove_file(&path).expect("a removal");
    std::fs::remove_file(run.join("class-load.log")).expect("a removal");
    let record = collect(&run, &id(Arm::MODAL), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert_eq!(record.notes, ["no startup-stats.json", "no class-load.log"]);
    assert_eq!(record.metrics.get("pluginClasses"), Some(&157.0));
    assert_eq!(
        record.class_maps.classes_by_plugin.values().sum::<u64>(),
        157,
        "the plugin log alone gives the map"
    );
}

#[test]
fn the_gate_names_what_is_missing() {
    let (_dir, session) = fixture_session();
    let non_modal = session.join("non-modal-run-01");
    let record = collect(&non_modal, &id(Arm::MODAL), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("welcome.screen.became.visible has is_modal=false, and the modal arm expects is_modal=true")
    );
    assert!(record.metrics.is_empty(), "a failed run reports no number");

    std::fs::write(non_modal.join("log/idea.log"), "nothing\n").expect("a log");
    let record = collect(&non_modal, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("log/idea.log has no line \"Opened the welcome screen project\"")
    );

    let failure = LaunchFacts {
        failure: Some("no welcome.screen.became.visible within 180 s".to_owned()),
        ..LaunchFacts::default()
    };
    let record = collect(&session.join("modal-run-01"), &id(Arm::MODAL), failure);
    assert_eq!(record.reason.as_deref(), Some("no welcome.screen.became.visible within 180 s"));

    std::fs::remove_file(session.join("modal-run-01/fus.jsonl")).expect("a removal");
    let record = collect(&session.join("modal-run-01"), &id(Arm::MODAL), LaunchFacts::default());
    assert_eq!(record.reason.as_deref(), Some("no fus.jsonl"));
}

#[test]
fn a_truncated_trace_is_a_note_and_an_unknown_report_is_a_failure() {
    let (_dir, session) = fixture_session();
    let run = session.join("non-modal-run-01");
    let trace = std::fs::read_to_string(run.join("opentelemetry.json")).expect("a trace");
    std::fs::write(run.join("opentelemetry.json"), &trace[..trace.len() / 2]).expect("a trace");
    let record = collect(&run, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert!(
        record
            .notes
            .contains(&"opentelemetry.json is truncated; the reader closed it".to_owned())
    );

    let report = std::fs::read_to_string(run.join("startup-stats.json")).expect("a report");
    std::fs::write(run.join("startup-stats.json"), report.replace("\"38\"", "\"40\"")).expect("a report");
    let record = collect(&run, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("startup-stats.json: version 40 is not supported, only 38")
    );
}

#[test]
fn a_run_directory_name_round_trips() {
    for name in [
        "modal-prime",
        "non-modal-run-07",
        "open-project-run-12",
        "project-prime",
        "open-project-prime",
        "empty-editor-prime",
        "empty-editor-run-02",
        "project-without-com.intellij.air-prime",
        "empty-editor-without-com.intellij.air-run-03",
        "non-modal-without-Lombook Plugin-run-01",
    ] {
        assert_eq!(RunId::parse(name).map(|id| id.dir_name()).as_deref(), Some(name));
    }
    assert_eq!(
        RunId::parse("project-without-com.intellij.air-run-03").map(|id| id.arm),
        Some(Arm::PROJECT.without("com.intellij.air"))
    );
    for name in [
        "template",
        "modal-run-00",
        "sideways-run-01",
        "modal-run-x",
        "project-without--run-01",
        "project-sandbox",
    ] {
        assert_eq!(RunId::parse(name), None, "{name}");
    }
}

#[test]
fn a_project_run_passes_the_highlighted_gate_without_a_fus_log() {
    let (_dir, session) = fixture_session();
    assert_eq!(
        RunId::parse("project-run-01"),
        Some(RunId {
            arm: Arm::PROJECT,
            kind: RunKind::Measured,
            index: 1
        })
    );
    let record = collect(&session.join("project-run-01"), &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert_eq!(record.notes, vec!["no fus.jsonl".to_owned()]);
    let metric = |name: &str| record.metrics.get(name).copied();
    assert_eq!(
        metric(EDITOR_HIGHLIGHTED),
        Some(2950.0),
        "the span of the trace wins over the report"
    );
    assert_eq!(metric("editor restoring till paint"), Some(620.0));
    assert_eq!(metric("editor restoring"), Some(180.5));
    assert_eq!(metric("project frame creating"), Some(218.1));
    assert_eq!(metric(WELCOME_BECAME_VISIBLE), None);
}

/// The class-load log gives the counts by source. With the plugin log it gives the owners, and with the report the
/// counts before each anchor: the anchor plus `jvm.loadingTime`, 358 ms, on the JVM clock.
#[test]
fn a_project_run_counts_the_classes_of_the_jvm_log_before_each_anchor() {
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("project-run-01"), &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(record.reason, None);
    let classes: BTreeMap<&str, f64> = record
        .metrics
        .iter()
        .filter(|(metric, _)| metric.starts_with("classes."))
        .map(|(metric, value)| (metric.as_str(), *value))
        .collect();
    assert_eq!(
        classes,
        BTreeMap::from([
            (CLASSES_NAMED, 26.0),
            (CLASSES_HIDDEN, 3.0),
            (CLASSES_JDK, 5.0),
            (CLASSES_JAR, 2.0),
            (CLASSES_PLATFORM, 8.0),
            (CLASSES_PLUGINS, 11.0),
            ("classes.named@editor highlighting completed", 20.0),
            ("classes.named@quit", 25.0),
            ("classes.edt@editor highlighting completed", 6.0),
            ("classes.edt@quit", 7.0),
        ])
    );
    assert_eq!(record.metrics.get("pluginClasses"), Some(&43.0));
    assert_eq!(record.class_maps.classes_by_plugin.get(MARKDOWN), Some(&3));
    assert_eq!(record.class_maps.classes_by_plugin.values().sum::<u64>(), 11);
    assert_eq!(
        record
            .class_maps
            .classes_by_loader
            .iter()
            .find(|loader| loader.module.as_deref() == Some("intellij.markdown.core"))
            .map(|loader| loader.classes),
        Some(1)
    );
    assert_eq!(
        record.class_maps.classes_by_plugin_at.keys().collect::<Vec<_>>(),
        ["editor highlighting completed", "quit"]
    );
    assert_eq!(
        record.class_maps.classes_by_plugin_at.get(EDITOR_HIGHLIGHTED),
        Some(&counts(&[
            ("com.intellij", 2),
            ("com.jetbrains.performancePlugin", 1),
            (MARKDOWN, 3)
        ]))
    );
}

/// The log of `run` without the line of the main class.
fn remove_main_class(run: &Path) {
    let log = std::fs::read_to_string(run.join("class-load.log")).expect("a log");
    let kept: String = log
        .lines()
        .filter(|line| !line.contains("com.intellij.idea.Main "))
        .map(|line| format!("{line}\n"))
        .collect();
    std::fs::write(run.join("class-load.log"), kept).expect("a log");
}

/// A bound moves with the load of the main class, the start of the process clock on the JVM clock. `jvm.loadingTime`
/// does not move it while the log has the main class.
#[test]
fn the_anchor_counts_move_with_the_main_class() {
    let (_dir, session) = fixture_session();
    let run = session.join("project-run-01");
    let log = std::fs::read_to_string(run.join("class-load.log")).expect("a log");
    let report = std::fs::read_to_string(run.join("startup-stats.json")).expect("a report");
    let at_highlighting = |main_uptime: &str, loading_time: &str| {
        let main = "[class,load] com.intellij.idea.Main ";
        let shifted = log.replace(&format!("[0.358s][4355 ]{main}"), &format!("[{main_uptime}][4355 ]{main}"));
        std::fs::write(run.join("class-load.log"), shifted).expect("a log");
        let report = report.replace(r#""loadingTime":358"#, &format!(r#""loadingTime":{loading_time}"#));
        std::fs::write(run.join("startup-stats.json"), report).expect("a report");
        let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
        let markdown = record
            .class_maps
            .classes_by_plugin_at
            .get(EDITOR_HIGHLIGHTED)
            .and_then(|counts| counts.get(MARKDOWN).copied());
        (
            record.metrics.get("classes.named@editor highlighting completed").copied(),
            markdown,
            record.metrics.get(JVM_PROCESS_START).copied(),
        )
    };
    assert_eq!(at_highlighting("0.358s", "358"), (Some(20.0), Some(3), Some(358.0)));
    assert_eq!(
        at_highlighting("0.358s", "0"),
        (Some(20.0), Some(3), Some(358.0)),
        "jvm.loadingTime is no anchor while the log has the main class"
    );
    assert_eq!(
        at_highlighting("0.000s", "358"),
        (Some(18.0), Some(2), Some(0.0)),
        "the bound is 2950 ms on the JVM clock"
    );
    assert_eq!(
        at_highlighting("1.000s", "358"),
        (Some(22.0), Some(3), Some(1000.0)),
        "the bound is 3950 ms on the JVM clock"
    );
}

/// A log without the main class takes `jvm.loadingTime` of the report as the start of the process clock, with a note.
#[test]
fn a_log_without_the_main_class_takes_the_loading_time_with_a_note() {
    let (_dir, session) = fixture_session();
    let run = session.join("project-run-01");
    remove_main_class(&run);
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(
        record.notes,
        [
            "no fus.jsonl",
            "class-load.log has no com.intellij.idea.Main, so the class anchors start the process clock at jvm.loadingTime, about 100 ms too early"
        ]
    );
    assert_eq!(record.metrics.get("classes.named@editor highlighting completed"), Some(&19.0));
    assert_eq!(record.metrics.get(JVM_PROCESS_START), None);
}

/// A missing class-load log is a note, and so is a log of another shape. Neither fails the run. A log without the main
/// class and a report without `jvm.loadingTime` leave the counts without anchors.
#[test]
fn a_missing_or_bad_class_log_is_a_note_and_a_run_without_a_clock_start_has_no_anchors() {
    let (_dir, session) = fixture_session();
    let run = session.join("project-run-01");
    let report = std::fs::read_to_string(run.join("startup-stats.json")).expect("a report");
    std::fs::write(run.join("startup-stats.json"), report.replace(r#""jvm":{"loadingTime":358},"#, "")).expect("a report");
    remove_main_class(&run);
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert_eq!(
        record.notes,
        [
            "no fus.jsonl",
            "class-load.log has no com.intellij.idea.Main and startup-stats.json has no jvm.loadingTime, so the class counts have no anchors"
        ]
    );
    assert_eq!(record.metrics.get(CLASSES_NAMED), Some(&25.0));
    assert!(!record.metrics.keys().any(|metric| metric.contains('@')), "{:?}", record.metrics);
    assert!(record.class_maps.classes_by_plugin_at.is_empty());
    assert_eq!(record.metrics.get(JVM_LOADING_TIME), None, "the report has no JVM part");

    std::fs::write(run.join("class-load.log"), "[0.1s][class,load] a.A\n").expect("a log");
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert_eq!(
        record.notes,
        [
            "no fus.jsonl",
            "class-load.log: line 1: [0.1s][class,load] a.A: no ` source: ` after the class name",
        ]
    );
    assert_eq!(record.metrics.get(CLASSES_NAMED), None);
    assert_eq!(
        record.class_maps.classes_by_plugin.get(MARKDOWN),
        Some(&3),
        "the plugin log alone gives the map"
    );

    std::fs::remove_file(run.join("class-load.log")).expect("a removal");
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(record.notes, ["no fus.jsonl", "no class-load.log"]);
}

#[test]
fn the_highlighted_gate_reads_the_report_without_a_trace_and_names_what_is_missing() {
    let (_dir, session) = fixture_session();
    let run = session.join("project-run-01");
    std::fs::remove_file(run.join("opentelemetry.json")).expect("a removal");
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert_eq!(
        record.metrics.get(EDITOR_HIGHLIGHTED).copied(),
        Some(2950.4),
        "the instant event of the report"
    );

    let report = std::fs::read_to_string(run.join("startup-stats.json")).expect("a report");
    std::fs::write(run.join("startup-stats.json"), report.replace(EDITOR_HIGHLIGHTED, "editor opened")).expect("a report");
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("no \"editor highlighting completed\" in the trace or the report")
    );
    assert!(record.metrics.is_empty(), "a failed run reports no number");

    std::fs::remove_file(run.join("startup-stats.json")).expect("a removal");
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(record.reason.as_deref(), Some("no opentelemetry.json and no startup-stats.json"));
}

/// An empty-editor run ends at the composer: the end of its first `air.emptyState.createComponent` span, in ms from the
/// process start. Each composer span is a metric with its duration, without the helper twins, and the end is an anchor
/// of the class counts.
#[test]
fn an_empty_editor_run_ends_at_the_built_composer_and_counts_the_classes_before_it() {
    let (_dir, session) = fixture_session();
    let run = empty_editor_run(&session, &EMPTY_STATE_FIXTURE_SPANS);
    let record = collect(&run, &id(Arm::EMPTY_EDITOR), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert_eq!(record.notes, vec!["no fus.jsonl".to_owned()], "the span gate reads no FUS event");
    let composer: BTreeMap<&str, f64> = record
        .metrics
        .iter()
        .filter(|(metric, _)| metric.starts_with(EMPTY_STATE_PREFIX) || metric.as_str() == EMPTY_STATE_BUILT)
        .map(|(metric, value)| (metric.as_str(), *value))
        .collect();
    assert_eq!(
        composer,
        BTreeMap::from([
            (EMPTY_STATE_BUILT, 2900.0),
            (EMPTY_STATE_SPAN, 900.0),
            ("air.emptyState.prologue", 150.0),
            ("air.emptyState.prologue.promptDocument", 136.0),
            ("air.emptyState.prologue.aiSource", 5.0),
            ("air.emptyState.buildOnEdt", 730.0),
            ("air.emptyState.createContent", 651.0),
            ("air.emptyState.initializeContent", 64.0),
        ])
    );
    assert_eq!(
        record.empty_state_order,
        [
            EMPTY_STATE_SPAN,
            "air.emptyState.prologue",
            "air.emptyState.prologue.aiSource",
            "air.emptyState.prologue.promptDocument",
            "air.emptyState.buildOnEdt",
            "air.emptyState.createContent",
            "air.emptyState.initializeContent",
        ],
        "by the start, the longer span first at one start, without the helper twin"
    );
    assert_eq!(
        record.metrics.get(&named_at(EMPTY_STATE_BUILT)),
        Some(&19.0),
        "the bound is 2900 ms, before the highlighted editor of the fixture at 2950 ms"
    );
    assert!(
        record.class_maps.classes_by_plugin_at.contains_key(EMPTY_STATE_BUILT),
        "{:?}",
        record.class_maps.classes_by_plugin_at.keys()
    );
}

/// The first span of a name counts: a composer that the IDE builds again later moves no number.
#[test]
fn a_second_composer_moves_no_number() {
    let (_dir, session) = fixture_session();
    let mut spans = EMPTY_STATE_FIXTURE_SPANS.to_vec();
    spans.push((EMPTY_STATE_SPAN, 9000, 50));
    let run = empty_editor_run(&session, &spans);
    let record = collect(&run, &id(Arm::EMPTY_EDITOR), LaunchFacts::default());
    assert_eq!(record.metrics.get(EMPTY_STATE_BUILT), Some(&2900.0));
    assert_eq!(record.metrics.get(EMPTY_STATE_SPAN), Some(&900.0));
}

/// A measured run of the empty-editor arm without the composer span is not valid, and the reason says why a run can
/// lack it. A prime run is held to the same gate.
#[test]
fn an_empty_editor_run_without_the_composer_span_is_invalid() {
    let (_dir, session) = fixture_session();
    let spans: Vec<_> = EMPTY_STATE_FIXTURE_SPANS
        .into_iter()
        .filter(|(name, _, _)| *name != EMPTY_STATE_SPAN)
        .collect();
    let run = empty_editor_run(&session, &spans);
    for kind in [RunKind::Measured, RunKind::Prime] {
        let id = RunId {
            arm: Arm::EMPTY_EDITOR,
            kind,
            index: u32::from(kind == RunKind::Measured),
        };
        let record = collect(&run, &id, LaunchFacts::default());
        assert!(!record.valid, "{kind:?}");
        assert_eq!(
            record.reason.as_deref(),
            Some(
                "no air.emptyState.createComponent span in opentelemetry.json: the project restored an editor, or the registry key air.inline.empty.state.prompt is off"
            )
        );
        assert!(record.metrics.is_empty(), "a failed run reports no number");
    }

    std::fs::remove_file(run.join("opentelemetry.json")).expect("a removal");
    let record = collect(&run, &id(Arm::EMPTY_EDITOR), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("no opentelemetry.json, so no air.emptyState.createComponent span")
    );
}

/// A project run whose trace holds no composer span has no composer metric and no anchor at the composer.
#[test]
fn a_project_run_without_a_composer_has_no_composer_metric() {
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("project-run-01"), &id(Arm::PROJECT), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert!(
        !record
            .metrics
            .keys()
            .any(|metric| metric.starts_with(EMPTY_STATE_PREFIX) || metric.contains(EMPTY_STATE_BUILT)),
        "{:?}",
        record.metrics.keys()
    );
}

/// A composer build that threw ends its span too, with the error tags of the IDE, so the run is not valid and reports
/// no number.
#[test]
fn an_empty_editor_run_whose_composer_threw_is_invalid() {
    let (_dir, session) = fixture_session();
    let run = empty_editor_run(&session, &EMPTY_STATE_FIXTURE_SPANS);
    let path = run.join("opentelemetry.json");
    let mut trace: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&path).expect("a trace")).expect("a Jaeger trace");
    let span = trace["data"][0]["spans"]
        .as_array_mut()
        .expect("a span list")
        .iter_mut()
        .find(|span| span["operationName"] == EMPTY_STATE_SPAN)
        .expect("the composer span");
    span["tags"] = serde_json::json!([
        {"key": "otel.status_code", "type": "string", "value": "ERROR"},
        {"key": "error", "type": "boolean", "value": "true"},
    ]);
    std::fs::write(&path, trace.to_string()).expect("a trace");
    let record = collect(&run, &id(Arm::EMPTY_EDITOR), LaunchFacts::default());
    assert!(!record.valid);
    assert_eq!(
        record.reason.as_deref(),
        Some("the air.emptyState.createComponent span ended with an error")
    );
    assert!(record.metrics.is_empty(), "a failed run reports no number");
}

/// The quit cancels a composer build that has not finished, and the IDE still ends its span, without an error. A span
/// that ends after `application.exit` started, at 7553.7 ms in the fixture, is thus not valid.
#[test]
fn an_empty_editor_run_whose_composer_the_quit_cut_short_is_invalid() {
    let (_dir, session) = fixture_session();
    let mut spans = EMPTY_STATE_FIXTURE_SPANS.to_vec();
    spans[0] = (EMPTY_STATE_SPAN, 2000, 6000);
    let run = empty_editor_run(&session, &spans);
    let record = collect(&run, &id(Arm::EMPTY_EDITOR), LaunchFacts::default());
    assert!(!record.valid);
    assert_eq!(
        record.reason.as_deref(),
        Some("the air.emptyState.createComponent span did not end before the application.exit span started, so the quit cut it short")
    );
    assert!(record.metrics.is_empty(), "a failed run reports no number");
}

/// The record reads the profile to the gate when the run has one. Else it reads the whole-run profile with a note that
/// names why the run did not stop the profiler at its gate.
#[test]
fn the_record_prefers_the_profile_to_the_gate() {
    use crate::bench::profile::ProfileScope;
    let (_dir, session) = fixture_session();
    let run = session.join("non-modal-run-01");
    let whole = collect(&run, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(whole.profile_scope, Some(ProfileScope::WholeRun));
    assert_eq!(whole.notes, [WHOLE_RUN_NOTE]);
    let failed = LaunchFacts {
        profile_stop_failure: Some("the JBR of the generation has no bin/jcmd".to_owned()),
        ..LaunchFacts::default()
    };
    let record = collect(&run, &id(Arm::NON_MODAL), failed);
    assert_eq!(
        record.notes,
        ["cpu.collapsed is the whole-run profile, with the hold and the quit: the JBR of the generation has no bin/jcmd"]
    );

    std::fs::write(
        run.join("cpu-gate.collapsed"),
        "[AWT-EventQueue-0 tid=1035];java/lang/Thread.run;com/example/Gate.paint 7\n[DefaultDispatcher-worker-1 tid=77];com/example/Work.run 3\n",
    )
    .expect("a profile to the gate");
    let record = collect(&run, &id(Arm::NON_MODAL), LaunchFacts::default());
    assert_eq!(record.profile_scope, Some(ProfileScope::Gate));
    assert_eq!(record.notes, Vec::<String>::new(), "the profile to the gate needs no note");
    assert_eq!(
        (record.edt_samples, record.edt_frames[0].frame.as_str()),
        (Some(7), "com/example/Gate.paint"),
        "the profile to the gate, not the {:?} EDT samples of the whole run",
        whole.edt_samples
    );
}

/// The plugin log of the project fixture has the new shape. The record keeps the classes of each loader with their
/// EDT and BG nanos before each anchor, and one activation per loader with its first load on the process clock and its
/// trigger frame.
#[test]
fn a_project_run_keeps_the_loaders_and_their_activations() {
    use super::{DefineTimes, LoaderClasses};
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("project-run-01"), &id(Arm::PROJECT), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    let maps = &record.class_maps;
    assert_eq!(maps.classes_by_loader.len(), 9, "the loaders of the class-load log");
    assert_eq!(maps.activations.len(), 19, "every loader of the plugin log");
    let at_highlighting = maps
        .classes_by_loader_at
        .get(EDITOR_HIGHLIGHTED)
        .expect("the loaders at the highlighting");
    assert_eq!(
        at_highlighting
            .iter()
            .find(|loader| loader.plugin == MARKDOWN && loader.module.is_none()),
        Some(&LoaderClasses {
            plugin: MARKDOWN.to_owned(),
            module: None,
            classes: 2,
            times: Some(DefineTimes {
                edt_classes: 2,
                edt_nanos: 201_439,
                bg_nanos: 0
            }),
        })
    );
    let docker = maps
        .activations
        .iter()
        .find(|activation| activation.module.as_deref() == Some("intellij.clouds.docker.gateway.json"))
        .expect("an activation");
    assert_eq!(
        (docker.first_load_ms, docker.stack.len(), docker.trigger.as_deref()),
        (
            None,
            2,
            Some("com.intellij.platform.settings.local.SettingsControllerMediator$$Lambda/0x0000000801234000.invoke(Unknown Source)")
        ),
        "a loader without a class in the class-load log has no first load; a lambda of another loader triggers it"
    );
    let settings = maps
        .activations
        .iter()
        .find(|activation| activation.module.as_deref() == Some("intellij.platform.settings.local"))
        .expect("an activation");
    assert_eq!(
        settings.first_load_ms,
        Some(1122.0),
        "1480 ms on the JVM clock minus jvm.loadingTime"
    );
    let json = serde_json::to_value(&record).expect("JSON");
    assert!(json["classesByLoaderAt"]["quit"].is_array(), "{}", json["classesByLoaderAt"]);
    assert_eq!(json["activations"][0]["plugin"], "com.intellij");
}

/// The v2 log of the project fixture ends with the `#cost` trailer, so the run has the cost of the log as two metrics,
/// and the digest shows them in a group of their own. The v1 log of the non-modal fixture has no trailer, so its run
/// has neither metric, and its digest has no such group and no `not measured` row for it.
#[test]
fn the_trailer_of_a_v2_log_gives_the_cost_of_the_log() {
    use super::{PLUGIN_LOG_STACK_MS, PLUGIN_LOG_WRITE_MS};
    use crate::bench::digest;
    use crate::bench::testing::summarize;
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("project-run-01"), &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(
        (record.metrics.get(PLUGIN_LOG_WRITE_MS), record.metrics.get(PLUGIN_LOG_STACK_MS)),
        (Some(&1.8), Some(&0.9))
    );
    let summary = summarize(&session, vec![Arm::PROJECT], &["project-run-01"]);
    let text = digest::render(&summary);
    assert!(
        text.contains("\nplugin class log cost (measured by the IDE):\n  pluginLog.writeMs "),
        "{text}"
    );
    let old = collect(&session.join("non-modal-run-01"), &id(Arm::NON_MODAL), LaunchFacts::default());
    assert!(
        !old.metrics.keys().any(|metric| metric.starts_with("pluginLog.")),
        "{:?}",
        old.metrics
    );
    let summary = summarize(&session, vec![Arm::NON_MODAL], &["non-modal-run-01"]);
    assert!(!digest::render(&summary).contains("pluginLog."), "a v1 log has no cost");
}

/// The thread of a class of a v2 plugin log comes from the JVM log: its tid, and the name of that tid in the thread
/// dump. A run without the dump gives no thread, so its loaders have no EDT and BG times, and a failed dump is a note.
#[test]
fn the_thread_of_a_plugin_class_comes_from_the_jvm_log_and_the_dump() {
    let (_dir, session) = fixture_session();
    let run = session.join("project-run-01");
    let markdown_at = |record: &RunRecord| {
        record.class_maps.classes_by_loader_at[EDITOR_HIGHLIGHTED]
            .iter()
            .find(|loader| loader.plugin == MARKDOWN && loader.module.is_none())
            .map(|loader| (loader.classes, loader.times.map(|times| (times.edt_classes, times.edt_nanos))))
    };
    let record = collect(&run, &id(Arm::PROJECT), LaunchFacts::default());
    assert_eq!(markdown_at(&record), Some((2, Some((2, 201_439)))));
    std::fs::remove_file(run.join("threads.txt")).expect("a removal");
    let facts = LaunchFacts {
        thread_dump_failure: Some("log/idea.log names no PID".to_owned()),
        ..LaunchFacts::default()
    };
    let record = collect(&run, &id(Arm::PROJECT), facts);
    assert_eq!(markdown_at(&record), Some((2, None)), "no thread, so no time");
    assert!(
        !record.metrics.keys().any(|metric| metric.starts_with("classes.edt@")),
        "{:?}",
        record.metrics
    );
    assert!(
        record
            .notes
            .contains(&"no threads.txt, so a Java thread has no name: log/idea.log names no PID".to_owned()),
        "{:?}",
        record.notes
    );
}

/// A run of the cargo-sync arm in `session`: a copy of the fixture `project-run-01` whose `idea.log` gets the line
/// `CargoSyncTask started` at `after_ms` from the process start, in the local time of the trace (`+0200`), with the
/// column `[log_ms]`. Answers the run directory.
fn cargo_sync_run(session: &Path, after_ms: i64, log_ms: i64) -> std::path::PathBuf {
    let run = session.join("cargo-sync-run-01");
    avl_testkit::traces::copy_tree(&session.join("project-run-01"), &run);
    let trace = crate::bench::trace::parse(&std::fs::read_to_string(run.join("opentelemetry.json")).expect("a trace")).expect("a trace");
    assert_eq!(trace.utc_offset_s, Some(7200), "the fixture names +0200");
    let origin_us = trace.origin_us().expect("a process start");
    let offset = jiff::tz::Offset::from_seconds(7200).expect("an offset");
    let local = jiff::Timestamp::from_microsecond(origin_us + after_ms * 1000)
        .expect("a time")
        .to_zoned(jiff::tz::TimeZone::fixed(offset))
        .datetime();
    let line = format!(
        "{:04}-{:02}-{:02} {:02}:{:02}:{:02},{:03} [{log_ms:>7}]   INFO - #o.r.c.p.m.i.CargoSyncTask - CargoSyncTask started\n",
        local.year(),
        local.month(),
        local.day(),
        local.hour(),
        local.minute(),
        local.second(),
        local.subsec_nanosecond() / 1_000_000
    );
    let log = run.join("log").join("idea.log");
    let text = std::fs::read_to_string(&log).expect("a log");
    std::fs::write(&log, format!("{text}{line}")).expect("a log");
    run
}

/// A cargo-sync run ends at the first `CargoSyncTask started` line of `idea.log`. Its local time and the UTC offset of
/// the trace give the ms from the process start, the clock of the anchors, and the column gives the ms of the log. The
/// end is an anchor of the class counts.
#[test]
fn a_cargo_sync_run_ends_at_the_first_cargo_sync_line() {
    use super::{CARGO_SYNC_LOG_MS, CARGO_SYNC_STARTED};
    let (_dir, session) = fixture_session();
    let run = cargo_sync_run(&session, 2500, 2400);
    let record = collect(&run, &id(Arm::CARGO_SYNC), LaunchFacts::default());
    assert_eq!(record.reason, None);
    let started = record.metrics.get(CARGO_SYNC_STARTED).copied().expect("the gate metric");
    assert!((started - 2500.0).abs() < 1.0, "the line has a resolution of 1 ms: {started}");
    assert_eq!(record.metrics.get(CARGO_SYNC_LOG_MS), Some(&2400.0));
    assert_eq!(
        record.metrics.get(&named_at(CARGO_SYNC_STARTED)),
        Some(&18.0),
        "the bound is 2500 ms, before the highlighted editor of the fixture at 2950 ms"
    );
}

/// A cargo-sync run without the line is not valid, and the reason says why a run can lack it.
#[test]
fn a_cargo_sync_run_without_the_line_is_invalid() {
    let (_dir, session) = fixture_session();
    let run = session.join("cargo-sync-run-01");
    avl_testkit::traces::copy_tree(&session.join("project-run-01"), &run);
    let record = collect(&run, &id(Arm::CARGO_SYNC), LaunchFacts::default());
    assert!(!record.valid);
    assert_eq!(
        record.reason.as_deref(),
        Some("no line \"CargoSyncTask started\" in log/idea.log: the IDE has no Rust plugin, or the project has no Cargo.toml")
    );
}
