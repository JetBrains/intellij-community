use pretty_assertions::assert_eq;

use super::{Args, Invocation, parse_args, usage};
use bt_core::exit;
use bt_core::fake::{areas, refusal};

fn run(argv: &[&str]) -> Args {
    match parse_args(argv, areas()) {
        Ok(Invocation::Run(args)) => args,
        other => panic!("{argv:?} parsed as {other:?}"),
    }
}

#[test]
fn parse_options_separates_positionals_flags_values_and_passthrough() {
    let args = run(&["FooTest", "--shards", "3", "--json", "--", "--test_arg=--x"]);
    assert_eq!(args.selector.as_deref(), Some("FooTest"));
    assert!(args.json);
    assert_eq!(args.shards, Some(3));
    assert_eq!(args.max_failures, 3, "the default");
    // Everything past `--` is bazel's, including a token that looks like one of ours.
    assert_eq!(args.passthrough, ["--test_arg=--x"]);

    let passthrough = run(&["--lane", "fast", "--", "--json", "-k"]);
    assert!(!passthrough.json);
    assert_eq!(passthrough.passthrough, ["--json", "-k"]);
}

#[test]
fn a_repeated_test_env_is_collected() {
    let args = run(&[
        "--lane",
        "acp",
        "--test-env",
        "CLAUDE_BIN",
        "--test-env",
        "NODE_BIN",
    ]);
    assert_eq!(args.test_env, ["CLAUDE_BIN", "NODE_BIN"]);
}

/// clap words the message; the contract is the code, the exit and the flag named in it.
#[test]
fn an_unknown_flag_is_refused() {
    let failure = refusal(parse_args(["--nope"], areas()));
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("usage", exit::USAGE)
    );
    assert!(failure.message.contains("--nope"), "{}", failure.message);
    assert!(!failure.message.contains("Usage:"), "{}", failure.message);
}

/// `--lane --json` is a missing value, not a lane called "--json": adopting it would report an unknown lane rather
/// than the real mistake.
#[test]
fn a_value_flag_with_no_value_is_refused() {
    let failure = refusal(parse_args(["--lane", "--json"], areas()));
    assert_eq!(failure.exit, exit::USAGE);
    assert!(failure.message.contains("--lane"), "{}", failure.message);
    assert!(
        failure.message.contains("value is required"),
        "{}",
        failure.message
    );
}

/// A flag that may appear once is refused when repeated, rather than one occurrence silently winning.
#[test]
fn a_single_value_flag_is_refused_when_repeated() {
    for flag in ["--lane", "--filter", "--shards", "--max-failures"] {
        let failure = refusal(parse_args([flag, "1", flag, "2"], areas()));
        assert_eq!(failure.exit, exit::USAGE, "{flag}");
        assert!(
            failure.message.contains(flag),
            "{flag}: {}",
            failure.message
        );
    }
    let two_selectors = refusal(parse_args(["FooTest", "BarTest"], areas()));
    assert_eq!(two_selectors.exit, exit::USAGE);
}

/// A repeated boolean flag asks for the same thing twice; unlike a repeated `--lane`, nothing is lost by accepting it.
#[test]
fn a_repeated_boolean_flag_is_accepted() {
    let args = run(&[
        "--lane",
        "fast",
        "--json",
        "--json",
        "--dry-run",
        "--dry-run",
        "--list",
        "--list",
        "--verbose",
        "--verbose",
        "--no-cache",
        "--no-cache",
    ]);
    assert!(args.json && args.dry_run && args.list && args.verbose && args.no_cache);
}

/// A bazel-style `-k` can legitimately be a value, so only a long flag is taken for a missing one.
#[test]
fn a_single_dash_value_is_kept() {
    let args = run(&["//a:b_test", "--filter", "-k", "--test-env", "-X"]);
    assert_eq!(args.filter.as_deref(), Some("-k"));
    assert_eq!(args.test_env, ["-X"]);
    for argv in [["--filter", "--json"], ["--filter", "--"]] {
        let failure = refusal(parse_args(argv, areas()));
        assert_eq!(failure.exit, exit::USAGE, "{argv:?}");
        assert!(
            failure.message.contains("value is required"),
            "{argv:?}: {}",
            failure.message
        );
    }
}

/// clap's `tip:` lines suggest `-- -k`, which for bt would hand the value to bazel instead.
#[test]
fn a_refusal_carries_no_clap_tip() {
    for argv in [
        vec!["--bogus"],
        vec!["FooTest", "-k"],
        vec!["--shards", "07"],
        vec!["--lane"],
    ] {
        let failure = refusal(parse_args(argv.clone(), areas()));
        assert_eq!(failure.exit, exit::USAGE, "{argv:?}");
        assert!(
            !failure.message.contains("tip:"),
            "{argv:?}: {}",
            failure.message
        );
        assert!(
            !failure.message.contains("--help"),
            "{argv:?}: {}",
            failure.message
        );
    }
}

/// A typo in a count must not surface after a five-minute build.
#[test]
fn a_count_is_checked_before_any_work_starts() {
    for argv in [
        ["--shards", "six"],
        ["--shards", "0"],
        ["--shards", "51"],
        ["--max-failures", "101"],
        ["--max-failures", "-1"],
        ["--shards", "07"],
        ["--shards", "+7"],
        ["--shards", "99999999999"],
    ] {
        assert_eq!(
            refusal(parse_args(argv, areas())).exit,
            exit::USAGE,
            "{argv:?}"
        );
    }
    assert_eq!(run(&["--lane", "fast"]).shards, None);
    assert_eq!(
        run(&["--lane", "fast", "--shards", "50", "--max-failures", "100"]).shards,
        Some(50)
    );
}

#[test]
fn usage_text_names_the_selectors_and_the_exit_codes() {
    for expected in [
        "Agent-friendly wrapper",
        "ClassName#method",
        "--lane fast",
        "Exit codes: 0 green, 2 usage, 3 tests failed, 4 zero tests executed",
    ] {
        assert!(
            usage(areas()).contains(expected),
            "the usage text does not mention {expected:?}"
        );
    }
}

/// `--help` is a green answer carrying the curated text; no arguments at all is a usage failure that still prints
/// it, because the help is the answer to it.
#[test]
fn help_is_the_curated_text_and_no_arguments_is_a_usage_failure() {
    for flag in ["--help", "-h"] {
        assert_eq!(
            parse_args([flag], areas()),
            Ok(Invocation::Help {
                text: usage(areas()),
                exit: exit::GREEN
            })
        );
    }
    assert_eq!(
        parse_args(Vec::<String>::new(), areas()),
        Ok(Invocation::Help {
            text: usage(areas()),
            exit: exit::USAGE
        })
    );
}

/// The help lists the lanes of every area with their descriptions, and says so when there is no area.
#[test]
fn the_help_lists_the_lanes_of_the_areas() {
    let text = usage(areas());
    assert!(
        text.contains("Lanes of plugins/air:\n  fast       "),
        "{text}"
    );
    assert!(text.contains("\n  acp        "), "{text}");
    assert!(!text.contains("{lanes}"), "{text}");
    let none = usage(&bt_core::Areas::default());
    assert!(
        none.contains("Lanes: none, because bt.json names no area."),
        "{none}"
    );
}
