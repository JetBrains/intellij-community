use std::path::PathBuf;
use std::time::Duration;

use bt_core::exit;
use pretty_assertions::assert_eq;

use super::{ArmChoice, Command, Invocation, parse_args, parse_hold};
use crate::arm::Arm;

fn command(argv: &[&str]) -> Command {
    match parse_args(argv) {
        Ok(Invocation::Run(command)) => command,
        other => panic!("expected a command, got {other:?}"),
    }
}

#[test]
fn welcome_takes_the_defaults_of_the_plan() {
    let Command::Welcome(args) = command(&["welcome"]) else {
        panic!("expected welcome");
    };
    assert_eq!(args.runs, 5);
    assert_eq!(args.arm, ArmChoice::Both);
    assert_eq!(args.launch.target, "//build:idea");
    assert_eq!(args.launch.hold, Duration::from_secs(3));
    assert!(!args.cold && !args.profile && !args.launch.json);
    assert_eq!(args.arm.arms(), vec![Arm::Modal, Arm::NonModal]);
}

#[test]
fn welcome_takes_every_option() {
    let Command::Welcome(args) = command(&[
        "welcome",
        "--runs",
        "2",
        "--arm",
        "non-modal",
        "--cold",
        "--profile",
        "--hold",
        "500ms",
        "--json",
        "--session",
        "s",
    ]) else {
        panic!("expected welcome");
    };
    assert_eq!(args.runs, 2);
    assert_eq!(args.arm.arms(), vec![Arm::NonModal]);
    assert!(args.cold && args.profile && args.launch.json);
    assert_eq!(args.launch.hold, Duration::from_millis(500));
    assert_eq!(args.launch.session, Some(PathBuf::from("s")));
}

#[test]
fn replay_and_open_project_take_a_directory() {
    assert_eq!(
        command(&["replay", "dir", "--json"]),
        Command::Replay(super::ReplayArgs {
            session: PathBuf::from("dir"),
            json: true
        })
    );
    let Command::OpenProject(args) = command(&["open-project", "p"]) else {
        panic!("expected open-project");
    };
    assert_eq!(args.project, PathBuf::from("p"));
    assert_eq!(args.runs, 3);
}

#[test]
fn a_bad_option_is_a_usage_refusal() {
    for argv in [
        &["welcome", "--runs", "0"][..],
        &["welcome", "--arm", "sideways"],
        &["welcome", "--target", "--json"],
        &["welcome", "--bogus"],
        &["replay"],
    ] {
        let refusal = parse_args(argv).expect_err("a refusal");
        assert_eq!(refusal.exit, exit::USAGE, "{argv:?}");
        assert_eq!(refusal.code, "usage");
        assert!(!refusal.message.contains("Usage:"), "{}", refusal.message);
    }
}

#[test]
fn help_is_green_and_no_argument_is_usage() {
    match parse_args(["--help"]) {
        Ok(Invocation::Help { text, exit: code }) => {
            assert_eq!(code, exit::GREEN);
            assert!(text.contains("open-project"), "{text}");
        }
        other => panic!("expected help, got {other:?}"),
    }
    match parse_args(Vec::<String>::new()) {
        Ok(Invocation::Help { exit: code, .. }) => assert_eq!(code, exit::USAGE),
        other => panic!("expected help, got {other:?}"),
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
