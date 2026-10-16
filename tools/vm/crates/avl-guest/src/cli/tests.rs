use clap::CommandFactory;
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::run_agent;

fn parsed(args: &[&str]) -> Verb {
    let argv: Vec<OsString> = std::iter::once("vm-guest-agent")
        .chain(args.iter().copied())
        .map(OsString::from)
        .collect();
    parse(&argv).unwrap_or_else(|error| panic!("{args:?} was refused: {error}")).verb
}

/// A flag a verb does not read is refused rather than ignored. `--tail` belongs to `log` alone and `--grace-ms` to
/// `cancel` alone, so either one elsewhere used to parse and do nothing. The refusal is exit 64, which the host
/// reads as "agent older than controller" - so it must be the usage envelope, echoing the verb.
#[test]
fn verb_refuses_another_verbs_flag() {
    let cases: [&[&str]; 6] = [
        &["status", "--root", "/x", "--cwd", "/y"],
        &["active", "--root", "/x", "--run", "run-a"],
        &["status", "--root", "/x", "--run", "run-a", "--tail", "10"],
        &["log", "--root", "/x", "--run", "run-a", "--grace-ms", "5"],
        &["cancel", "--root", "/x", "--run", "run-a", "--tail", "10"],
        &[
            "start",
            "--root",
            "/x",
            "--run",
            "run-a",
            "--cwd",
            "/w",
            "--grace-ms",
            "5",
            "--",
            "/bin/true",
        ],
    ];
    for argv in cases {
        let answered = run_agent(argv, b"");
        assert_eq!(answered.exit, 64, "{argv:?}: {}", answered.stderr);
        assert_eq!(answered.code(), "usage", "{argv:?}");
        assert_eq!(answered.failure()["command"], argv[0], "{argv:?}");
        assert!(answered.stdout.is_empty(), "{argv:?} answered on stdout: {}", answered.stdout);
    }
}

#[test]
fn verb_accepts_its_own_flags() {
    let Verb::Start(start) = parsed(&["start", "--root", "/x", "--run", "run-a", "--cwd", "/w", "--", "/bin/true", "-x"]) else {
        panic!("start parsed as another verb");
    };
    assert_eq!(start.run.root.root, PathBuf::from("/x"));
    assert_eq!(start.run.run_id, "run-a");
    assert_eq!(start.cwd, PathBuf::from("/w"));
    assert_eq!(start.snapshot_id, None);
    // Everything after `--` is the child's, flags included.
    assert_eq!(start.argv, ["/bin/true", "-x"]);

    let Verb::Cancel(cancel) = parsed(&["cancel", "--root", "/x", "--run", "run-r", "--grace-ms", "5"]) else {
        panic!("cancel parsed as another verb");
    };
    assert_eq!(cancel.grace_ms, 5);
    let Verb::Cancel(cancel) = parsed(&["cancel", "--root", "/x", "--run", "run-r"]) else {
        panic!("cancel parsed as another verb");
    };
    assert_eq!(cancel.grace_ms, 10_000);
    let Verb::Log(log) = parsed(&["log", "--root", "/x", "--run", "run-r", "--tail", "9"]) else {
        panic!("log parsed as another verb");
    };
    assert_eq!(log.tail, Some(9));
    let Verb::Start(start) = parsed(&[
        "start",
        "--root",
        "/x",
        "--run",
        "run-a",
        "--cwd",
        "/w",
        "--snapshot",
        "snapshot-1.2_b",
        "--",
        "x",
    ]) else {
        panic!("start parsed as another verb");
    };
    assert_eq!(start.snapshot_id.as_deref(), Some("snapshot-1.2_b"));
}

#[test]
fn unknown_verb_is_refused() {
    let answered = run_agent(&["ide-acquire", "--root", "/x"], b"");
    assert_eq!(answered.exit, 64);
    assert_eq!(answered.code(), "usage");
    assert_eq!(answered.failure()["command"], "ide-acquire");

    let bare = run_agent::<&str>(&[], b"");
    assert_eq!(bare.exit, 64);
    assert_eq!(bare.code(), "usage");
}

/// The shapes the supervisor verbs check before anything touches the disk: each one is the usage refusal.
#[test]
fn a_malformed_supervisor_value_is_a_usage_refusal() {
    let cases: [&[&str]; 8] = [
        &["status", "--root", "/x", "--run", "not-a-run"],
        &["status", "--root", "", "--run", "run-a"],
        &["status", "--root", "/x"],
        &["log", "--root", "/x", "--run", "run-a", "--tail", "0"],
        &["log", "--root", "/x", "--run", "run-a", "--tail", "10001"],
        &["cancel", "--root", "/x", "--run", "run-a", "--grace-ms", "60001"],
        &["start", "--root", "/x", "--run", "run-a", "--cwd", "/w"],
        &[
            "start",
            "--root",
            "/x",
            "--run",
            "run-a",
            "--cwd",
            "/w",
            "--snapshot",
            "snap",
            "--",
            "x",
        ],
    ];
    for argv in cases {
        let answered = run_agent(argv, b"");
        assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{argv:?}");
    }
}

/// Help is the one argv clap stops on that is not a refusal.
#[test]
fn help_answers_on_stdout_and_succeeds() {
    let answered = run_agent(&["--help"], b"");
    assert_eq!(answered.exit, 0);
    assert!(answered.stdout.contains("validate-guest"), "{}", answered.stdout);
    assert!(
        !answered.stdout.contains("\n  supervise "),
        "the internal verb is listed: {}",
        answered.stdout
    );
    assert!(answered.stderr.is_empty());
}

/// The verb names are the wire's, not a second spelling here: every [`AgentVerb`] is a subcommand under its wire
/// name, and parses back into itself.
#[test]
fn every_declared_verb_is_a_subcommand_under_its_wire_name() {
    let command = Agent::command();
    for verb in AgentVerb::ALL {
        assert!(command.find_subcommand(verb.as_str()).is_some(), "{verb} is not a verb");
    }
    assert_eq!(parsed(&["contract"]).name(), AgentVerb::Contract);
    assert_eq!(parsed(&["gc", "/r"]).name(), AgentVerb::Gc);
    assert_eq!(parsed(&["trace-pack-ready", "/a", "/b", "/c"]).name(), AgentVerb::TracePackReady);
}

/// The image pins Packer passes: all three required, by name, in any order, each once.
#[test]
fn the_image_pins_are_three_named_required_flags() {
    let Verb::ProvisionImage(pins) = parsed(&[
        "provision-image",
        "--junie-version",
        "1.2",
        "--node-major",
        "24",
        "--macos-version",
        "26.0",
    ]) else {
        panic!("provision-image parsed as another verb");
    };
    assert_eq!(
        pins,
        ImagePins {
            macos_version: "26.0".to_owned(),
            node_major: 24,
            junie_version: "1.2".to_owned(),
        }
    );
    let refused: [&[&str]; 6] = [
        &["validate-image", "--macos-version", "26.0", "--node-major", "24"],
        &[
            "validate-image",
            "--macos-version",
            "26.0",
            "--macos-version",
            "26.1",
            "--node-major",
            "24",
            "--junie-version",
            "1",
        ],
        &[
            "validate-image",
            "--macos-version",
            "",
            "--node-major",
            "24",
            "--junie-version",
            "1",
        ],
        &[
            "validate-image",
            "--macos-version",
            "26.0",
            "--node-major",
            "0",
            "--junie-version",
            "1",
        ],
        &[
            "validate-image",
            "--macos-version",
            "26.0",
            "--node-major",
            "24",
            "--junie-version",
            "1",
            "--extra",
            "x",
        ],
        &["validate-image", "26.0", "--node-major", "24", "--junie-version", "1"],
    ];
    for argv in refused {
        let answered = run_agent(argv, b"");
        assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{argv:?}");
    }
}

/// The Linux verb's positional shapes, checked before any step runs. The retired Tart Linux verbs are unknown.
#[test]
fn the_linux_verb_checks_its_positional_shapes() {
    let Verb::ValidateGuest(validate) = parsed(&["validate-guest", ":88", "/data/daemon-runtime"]) else {
        panic!("validate-guest parsed as another verb");
    };
    assert_eq!(validate.display, ":88");
    let refused: [&[&str]; 6] = [
        &["validate-guest", ":88"],
        &["validate-guest", ":88x", "/data/daemon-runtime"],
        &["validate-guest", ":88", "daemon-runtime"],
        &["provision-guest", "/w", ":88", "admin", "/mnt", "xvfb"],
        &["stage-node", "/node", "/node.tar.gz", "24.19.0"],
        &["check-node", "/node/bin/node", "24"],
    ];
    for argv in refused {
        let answered = run_agent(argv, b"");
        assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{argv:?}");
    }
}

/// The IDE verbs: `ide-prepare` and `ide-launch` take no argument, `ide-gc` takes one of its two stop rules at most, and the jcmd of
/// `cancel --thread-dump` is an absolute path.
#[test]
fn the_ide_verbs_check_their_flags() {
    assert_eq!(parsed(&["ide-prepare"]).name(), AgentVerb::IdePrepare);
    assert_eq!(parsed(&["ide-launch"]).name(), AgentVerb::IdeLaunch);
    let Verb::IdeGc(gc) = parsed(&["ide-gc", "--root", "/data/ide", "--keep-product", "p1"]) else {
        panic!("ide-gc parsed as another verb");
    };
    assert_eq!(
        (gc.root, gc.stop_all, gc.keep_product.as_deref(), gc.keep_logs, gc.grace_ms),
        (PathBuf::from("/data/ide"), false, Some("p1"), 5, 10_000)
    );
    let Verb::Cancel(cancel) = parsed(&["cancel", "--root", "/x", "--run", "run-ide-a", "--thread-dump", "/jbr/bin/jcmd"]) else {
        panic!("cancel parsed as another verb");
    };
    assert_eq!(cancel.thread_dump, Some(PathBuf::from("/jbr/bin/jcmd")));
    let refused: [&[&str]; 6] = [
        &["ide-prepare", "/data/ide"],
        &["ide-launch", "/data/ide"],
        &["ide-gc", "--root", "/data/ide", "--stop-all", "--keep-product", "p1"],
        &["ide-gc", "--root", "ide"],
        &["ide-gc", "--root", "/data/ide", "--keep-logs", "0"],
        &["cancel", "--root", "/x", "--run", "run-a", "--thread-dump", "jcmd"],
    ];
    for argv in refused {
        let answered = run_agent(argv, b"");
        assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{argv:?}");
    }
}
