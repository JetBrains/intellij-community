use std::ffi::OsString;
use std::path::PathBuf;
use std::time::Duration;

use avl_base::Exit;
use pretty_assertions::assert_eq;

use super::{ArmChoice, BenchVerb, DEFAULT_TARGET, ProjectArmChoice, ReplayArgs, SpanThread, parse_hold, parse_load};
use crate::bench::arm::Arm;
use crate::bench::profile::ProfileEvent;
use crate::cli::{Cmd, Parsed, parse};
use crate::terminal::{Output, TerminalFacts};

fn parsed(argv: &[&str]) -> Parsed {
    avl_affected::bridge::install_fixture();
    let argv: Vec<OsString> = std::iter::once("bench").chain(argv.iter().copied()).map(OsString::from).collect();
    parse(&argv, &TerminalFacts::default())
}

fn verb(argv: &[&str]) -> BenchVerb {
    match parsed(argv) {
        Parsed::Invocation(invocation) => match invocation.command {
            Some(Cmd::Bench { verb }) => verb,
            other => panic!("expected bench, got {other:?}"),
        },
        other => panic!("{argv:?} did not parse: {other:?}"),
    }
}

#[test]
fn welcome_takes_the_defaults_of_the_plan() {
    let BenchVerb::Welcome(args) = verb(&["welcome"]) else {
        panic!("expected welcome");
    };
    assert_eq!(args.runs, 5);
    assert_eq!(args.arm, ArmChoice::Both);
    assert_eq!(args.launch.target, DEFAULT_TARGET);
    assert_eq!(args.launch.hold, Duration::from_secs(3));
    assert_eq!(args.launch.session, None);
    assert_eq!(args.launch.max_load, None);
    assert_eq!(args.launch.generation, None);
    assert!(!args.cold && !args.profile);
    assert_eq!(args.profile_event, ProfileEvent::Cpu);
    assert_eq!(args.arm.arms(), vec![Arm::MODAL, Arm::NON_MODAL]);
}

/// `--generation` takes the start of a digest on every session verb. It names the distribution, so it conflicts with
/// `--target`.
#[test]
fn a_session_runs_over_a_generation_by_a_prefix_of_its_digest() {
    let BenchVerb::Welcome(args) = verb(&["welcome", "--generation", "5f1c0d9e2b7a"]) else {
        panic!("expected welcome");
    };
    assert_eq!(args.launch.generation.as_deref(), Some("5f1c0d9e2b7a"));
    let BenchVerb::Project(args) = verb(&["project", "markdown", "--generation", "5f1c"]) else {
        panic!("expected project");
    };
    assert_eq!(args.launch.generation.as_deref(), Some("5f1c"));
    for argv in [
        &["welcome", "--generation", "5F1C"][..],
        &["welcome", "--generation", ""],
        &["welcome", "--generation", "5f1c", "--target", "//build:idea_dist"],
    ] {
        match parsed(argv) {
            Parsed::Refused { refusal, .. } => assert_eq!(refusal.code, "usage", "{argv:?}"),
            other => panic!("{argv:?} parsed: {other:?}"),
        }
    }
}

/// `--profile-event` picks the event of the profile, and it needs `--profile`.
#[test]
fn the_profile_samples_the_cpu_or_the_wall_clock() {
    let BenchVerb::Welcome(args) = verb(&["welcome", "--profile", "--profile-event", "wall"]) else {
        panic!("expected welcome");
    };
    assert_eq!((args.profile, args.profile_event), (true, ProfileEvent::Wall));
    match parsed(&["welcome", "--profile-event", "wall"]) {
        Parsed::Refused { refusal, .. } => assert_eq!(refusal.code, "usage"),
        other => panic!("--profile-event without --profile parsed: {other:?}"),
    }
}

/// `classes --span` takes the thread, the run and the timeline. `--at`, `--entries` and `--plugin` refuse it, and the
/// options of the window need it.
#[test]
fn the_span_window_of_classes_takes_its_options() {
    let BenchVerb::Classes(args) = verb(&[
        "classes",
        "latest",
        "--span",
        "air.emptyState.createContent",
        "--thread",
        "all",
        "--run",
        "empty-editor-run-05",
        "--timeline",
        "20",
    ]) else {
        panic!("expected classes");
    };
    assert_eq!(
        (args.span.as_deref(), args.thread, args.run.as_deref(), args.timeline),
        (
            Some("air.emptyState.createContent"),
            SpanThread::All,
            Some("empty-editor-run-05"),
            Some(20)
        )
    );
    let BenchVerb::Classes(args) = verb(&["classes", "latest", "--span", "x"]) else {
        panic!("expected classes");
    };
    assert_eq!(args.thread, SpanThread::Edt);
    for argv in [
        &["classes", "latest", "--span", "x", "--at", "quit"][..],
        &["classes", "latest", "--span", "x", "--entries", "3"],
        &["classes", "latest", "--span", "x", "--plugin", "com.intellij.air"],
        &["classes", "latest", "--thread", "all"],
        &["classes", "latest", "--timeline", "20"],
        &["classes", "latest", "--run", "empty-editor-run-05"],
        &["classes", "latest", "--span", "x", "--timeline", "0"],
    ] {
        match parsed(argv) {
            Parsed::Refused { refusal, .. } => assert_eq!(refusal.code, "usage", "{argv:?}"),
            other => panic!("{argv:?} parsed: {other:?}"),
        }
    }
}

#[test]
fn welcome_takes_every_option_and_the_global_form() {
    let argv = [
        "welcome",
        "--runs",
        "2",
        "--arm",
        "non-modal",
        "--cold",
        "--profile",
        "--hold",
        "500ms",
        "--target",
        "//build:idea_air_dist",
        "--session",
        "s",
        "--max-load",
        "10",
        "--text",
    ];
    let BenchVerb::Welcome(args) = verb(&argv) else {
        panic!("expected welcome");
    };
    assert_eq!(args.runs, 2);
    assert_eq!(args.arm.arms(), vec![Arm::NON_MODAL]);
    assert!(args.cold && args.profile);
    assert_eq!(args.launch.hold, Duration::from_millis(500));
    assert_eq!(args.launch.target, "//build:idea_air_dist");
    assert_eq!(args.launch.session, Some(PathBuf::from("s")));
    assert_eq!(args.launch.max_load, Some(10.0));
    let Parsed::Invocation(invocation) = parsed(&argv) else {
        panic!("expected an invocation");
    };
    assert!(invocation.form.output == Output::Text, "--text after the verb is the global option");
}

#[test]
fn replay_and_open_project_take_a_directory() {
    assert_eq!(
        verb(&["replay", "dir"]),
        BenchVerb::Replay(ReplayArgs { session: "dir".to_owned() })
    );
    let BenchVerb::OpenProject(args) = verb(&["open-project", "p"]) else {
        panic!("expected open-project");
    };
    assert_eq!(args.project, PathBuf::from("p"));
    assert_eq!(args.runs, 3);
    let BenchVerb::OpenProject(args) = verb(&["open-project", "p", "--max-load", "2.5"]) else {
        panic!("expected open-project");
    };
    assert_eq!(args.launch.max_load, Some(2.5));
}

#[test]
fn project_takes_markdown_or_a_directory() {
    let BenchVerb::Project(args) = verb(&["project", "markdown"]) else {
        panic!("expected project");
    };
    assert_eq!(args.project, "markdown");
    assert_eq!(args.runs, 3);
    assert_eq!(args.launch.target, DEFAULT_TARGET);
    let BenchVerb::Project(args) = verb(&[
        "project",
        "docs",
        "--runs",
        "7",
        "--target",
        "//build:jetbrains_light_all_plugins_dist",
    ]) else {
        panic!("expected project");
    };
    assert_eq!((args.project.as_str(), args.runs), ("docs", 7));
    assert_eq!(args.launch.target, "//build:jetbrains_light_all_plugins_dist");
}

/// `bench project` runs the project arm by default. `--arm` adds the empty-editor arm or runs it alone, and only the
/// empty-editor arm takes `--cold`, because the project arm restores the editor of its prime run.
#[test]
fn project_takes_the_empty_editor_arm_and_cold_for_it_alone() {
    let arms = |argv: &[&str]| {
        let BenchVerb::Project(args) = verb(argv) else {
            panic!("expected project");
        };
        args.arms()
    };
    let BenchVerb::Project(args) = verb(&["project", "markdown"]) else {
        panic!("expected project");
    };
    assert_eq!((args.arm, args.cold), (ProjectArmChoice::Project, false));
    assert_eq!(arms(&["project", "markdown"]), Ok(vec![Arm::PROJECT]));
    assert_eq!(arms(&["project", "markdown", "--arm", "empty-editor"]), Ok(vec![Arm::EMPTY_EDITOR]));
    assert_eq!(
        arms(&["project", "markdown", "--arm", "both"]),
        Ok(vec![Arm::PROJECT, Arm::EMPTY_EDITOR])
    );
    assert_eq!(
        arms(&["project", "markdown", "--arm", "empty-editor", "--cold"]),
        Ok(vec![Arm::EMPTY_EDITOR])
    );
    let refused = "--cold needs --arm empty-editor: the project arm restores the editor that its prime run saved, and a cold session has no prime run";
    assert_eq!(arms(&["project", "markdown", "--cold"]), Err(refused.to_owned()));
    assert_eq!(arms(&["project", "markdown", "--arm", "both", "--cold"]), Err(refused.to_owned()));

    let refused = "--suppress-plugin com.intellij.air needs --arm project: the gate of the empty-editor arm is the span of the Air composer, so a run without Air never passes it";
    for arm in ["both", "empty-editor"] {
        assert_eq!(
            arms(&["project", "markdown", "--arm", arm, "--suppress-plugin", "com.intellij.air"]),
            Err(refused.to_owned())
        );
    }
    assert_eq!(
        arms(&["project", "markdown", "--suppress-plugin", "com.intellij.air"]),
        Ok(vec![Arm::PROJECT, Arm::PROJECT.without("com.intellij.air")])
    );
    assert_eq!(
        arms(&[
            "project",
            "markdown",
            "--arm",
            "empty-editor",
            "--suppress-plugin",
            "org.jetbrains.kotlin"
        ]),
        Ok(vec![Arm::EMPTY_EDITOR, Arm::EMPTY_EDITOR.without("org.jetbrains.kotlin")])
    );
}

/// `--suppress-plugin` adds a twin right after each arm of a welcome or a project session. `--arm` of a verb that reads
/// a session takes the label of a twin.
#[test]
fn suppress_plugin_adds_an_interleaved_twin_to_each_arm() {
    let air = "com.intellij.air";
    let BenchVerb::Welcome(args) = verb(&["welcome", "--suppress-plugin", air]) else {
        panic!("expected welcome");
    };
    assert_eq!(
        args.arms(),
        vec![Arm::MODAL, Arm::MODAL.without(air), Arm::NON_MODAL, Arm::NON_MODAL.without(air)]
    );
    let BenchVerb::Welcome(args) = verb(&["welcome"]) else {
        panic!("expected welcome");
    };
    assert_eq!(
        (args.suppress.plugin.clone(), args.arms()),
        (None, vec![Arm::MODAL, Arm::NON_MODAL])
    );
    let kotlin = "org.jetbrains.kotlin";
    let BenchVerb::Project(args) = verb(&["project", "markdown", "--arm", "both", "--suppress-plugin", kotlin]) else {
        panic!("expected project");
    };
    assert_eq!(
        args.arms(),
        Ok(vec![
            Arm::PROJECT,
            Arm::PROJECT.without(kotlin),
            Arm::EMPTY_EDITOR,
            Arm::EMPTY_EDITOR.without(kotlin)
        ])
    );
    let BenchVerb::Classes(args) = verb(&["classes", "s", "--arm", "project-without-com.intellij.air"]) else {
        panic!("expected classes");
    };
    assert_eq!(args.arm, Some(Arm::PROJECT.without(air)));
    let BenchVerb::Triggers(args) = verb(&["triggers", "s", "--arm", "empty-editor", "--plugin", air, "--top", "3"]) else {
        panic!("expected triggers");
    };
    assert_eq!(
        (args.selection.arm, args.plugins, args.at, args.top),
        (Some(Arm::EMPTY_EDITOR), vec![air.to_owned()], None, 3)
    );
}

#[test]
fn a_bad_option_is_a_usage_refusal() {
    for argv in [
        &["welcome", "--runs", "0"][..],
        &["welcome", "--arm", "sideways"],
        &["welcome", "--target", "--json"],
        &["welcome", "--hold", "3"],
        &["welcome", "--max-load", "0"],
        &["welcome", "--profile", "--profile-event", "alloc"],
        &["open-project", "p", "--max-load", "-1"],
        &["project"],
        &["project", "markdown", "--runs", "0"],
        &["project", "markdown", "--arm", "modal"],
        &["welcome", "--suppress-plugin", "a,b"],
        &["welcome", "--suppress-plugin", ""],
        &["project", "markdown", "--suppress-plugin", "a:b"],
        &["open-project", "p", "--suppress-plugin", "a"],
        &["classes", "s", "--arm", "project-with-a"],
        &["trace", "s", "--arm", "nonModal"],
        &["triggers"],
        &["triggers", "s", "--top", "0"],
        &["ls", "--limit", "0"],
        &["ls", "latest"],
        &["welcome", "--bogus"],
        &["replay"],
        &[],
    ] {
        match parsed(argv) {
            Parsed::Refused { refusal, .. } => {
                assert_eq!(refusal.exit, Exit::USAGE, "{argv:?}");
                assert_eq!(refusal.code, "usage", "{argv:?}");
            }
            other => panic!("{argv:?} parsed: {other:?}"),
        }
    }
}

#[test]
fn hold_needs_a_unit() {
    assert_eq!(parse_hold("0"), Ok(Duration::ZERO));
    assert_eq!(parse_hold("3s"), Ok(Duration::from_secs(3)));
    assert_eq!(parse_hold("2m"), Ok(Duration::from_secs(120)));
    assert_eq!(parse_hold("3"), Err("needs a unit (ms, s or m), got: 3".to_owned()));
    assert_eq!(parse_hold("3h"), Err("unknown unit h: use ms, s or m".to_owned()));
    assert_eq!(
        parse_hold("s"),
        Err("must be a whole number with a unit (ms, s or m), got: s".to_owned())
    );
    assert_eq!(parse_hold("11m"), Err("must be at most 10m, got: 11m".to_owned()));
}

#[test]
fn max_load_is_a_finite_load_above_zero() {
    assert_eq!(parse_load("10"), Ok(10.0));
    assert_eq!(parse_load("0.001"), Ok(0.001));
    for value in ["0", "-1", "inf", "NaN", "ten", ""] {
        assert_eq!(
            parse_load(value),
            Err(format!("must be a load average above 0, got: {value}")),
            "{value}"
        );
    }
}

#[test]
fn ls_lists_ten_sessions_by_default() {
    assert_eq!(verb(&["ls"]), BenchVerb::Ls(super::ListArgs { limit: 10 }));
    assert_eq!(verb(&["ls", "--limit", "3"]), BenchVerb::Ls(super::ListArgs { limit: 3 }));
}

#[test]
fn gc_keeps_two_generations_by_default() {
    assert_eq!(verb(&["gc"]), BenchVerb::Gc(super::GcArgs { keep: 2 }));
    assert_eq!(verb(&["gc", "--keep", "0"]), BenchVerb::Gc(super::GcArgs { keep: 0 }));
}

#[test]
fn trace_takes_a_session_a_run_a_window_a_minimum_and_all() {
    let BenchVerb::Trace(args) = verb(&["trace", "6291e91e"]) else {
        panic!("expected trace");
    };
    assert_eq!(args.selection.session, "6291e91e");
    assert_eq!(args.selection.run, None);
    assert_eq!((args.window.from, args.window.to), (None, None));
    assert_eq!(args.min.to_string(), "10");
    assert!(!args.all);
    let argv = [
        "trace",
        "latest",
        "--run",
        "non-modal-run-02",
        "--from",
        "897",
        "--to",
        "1608.5",
        "--min",
        "0",
        "--all",
    ];
    let BenchVerb::Trace(args) = verb(&argv) else {
        panic!("expected trace");
    };
    assert_eq!(args.selection.run.as_deref(), Some("non-modal-run-02"));
    assert_eq!(args.window.from.map(|ms| ms.to_string()).as_deref(), Some("897"));
    assert_eq!(args.window.to.map(|ms| ms.to_string()).as_deref(), Some("1608.5"));
    assert_eq!(args.min.to_string(), "0");
    assert!(args.all);
}

#[test]
fn activities_take_a_session_a_run_a_window_and_a_top() {
    let BenchVerb::Activities(args) = verb(&["activities", "6291e91e"]) else {
        panic!("expected activities");
    };
    assert_eq!(args.selection.session, "6291e91e");
    assert_eq!((args.selection.run, args.window.from, args.window.to), (None, None, None));
    assert_eq!(args.top, super::DEFAULT_TOP);
    let BenchVerb::Activities(args) = verb(&["activities", "latest", "--run", "modal-run-02", "--to", "1608", "--top", "10"]) else {
        panic!("expected activities");
    };
    assert_eq!(args.selection.run.as_deref(), Some("modal-run-02"));
    assert_eq!(args.window.to.map(|ms| ms.to_string()).as_deref(), Some("1608"));
    assert_eq!(args.top, 10);
    for argv in [
        &["activities"][..],
        &["activities", "s", "--top", "0"],
        &["activities", "s", "--from", "x"],
    ] {
        match parsed(argv) {
            Parsed::Refused { refusal, .. } => assert_eq!(refusal.code, "usage", "{argv:?}"),
            other => panic!("{argv:?} parsed: {other:?}"),
        }
    }
}

#[test]
fn compare_takes_two_to_twenty_six_sessions_and_the_dist_expectation() {
    let expected = super::CompareArgs {
        sessions: vec!["2104c3b2".to_owned(), "latest".to_owned()],
        expect_different_dist: false,
    };
    assert_eq!(verb(&["compare", "2104c3b2", "latest"]), BenchVerb::Compare(expected));
    let BenchVerb::Compare(args) = verb(&["compare", "a", "b", "c", "--expect-different-dist"]) else {
        panic!("expected compare");
    };
    assert_eq!(args.sessions, vec!["a", "b", "c"]);
    assert!(args.expect_different_dist);
    let many: Vec<String> = (0..=super::MAX_COMPARED).map(|index| format!("s{index}")).collect();
    let too_many: Vec<&str> = std::iter::once("compare").chain(many.iter().map(String::as_str)).collect();
    for argv in [&["compare"][..], &["compare", "a"], &too_many] {
        match parsed(argv) {
            Parsed::Refused { refusal, .. } => assert_eq!(refusal.code, "usage", "{argv:?}"),
            other => panic!("{argv:?} parsed: {other:?}"),
        }
    }
}

#[test]
fn a_window_bound_is_a_finite_number_of_milliseconds() {
    use super::parse_ms;
    assert_eq!(parse_ms("12.5").map(|ms| ms.to_string()), Ok("12.5".to_owned()));
    for value in ["-1", "nan", "inf", "1s", ""] {
        assert_eq!(
            parse_ms(value),
            Err(format!("must be a number of milliseconds, 0 or more, got: {value}")),
            "{value}"
        );
    }
    for argv in [&["trace"][..], &["trace", "s", "--from", "-5"], &["trace", "s", "--min", "x"]] {
        match parsed(argv) {
            Parsed::Refused { refusal, .. } => assert_eq!(refusal.code, "usage", "{argv:?}"),
            other => panic!("{argv:?} parsed: {other:?}"),
        }
    }
}
