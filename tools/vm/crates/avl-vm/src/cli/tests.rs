//! The entry point's suite. Hermetic: no VM, no hypervisor binary, no Bazel, no network.
//!
//! What it covers is exactly what this crate decides for itself - the option grammar, which form a refusal is
//! written in, which pool an invocation resolves to, and the envelope's shape. What a command *does* is the owning
//! crate's suite, and asserting it again from here would only pin the dispatch twice.

use std::ffi::OsString;
use std::path::{Path, PathBuf};

use avl_base::config::{Presentation, THEME_VARIABLE};
use avl_base::report::{Renderer, Terminal};
use avl_base::{Backend, Environment, Exit, GuestOs, Reporter, SCHEMA_VERSION, Selection};
use avl_host_sys::Interrupts;
use avl_wire::progress::{Event, Verdict};
use pretty_assertions::assert_eq;
use serde_json::Value;
use tempfile::TempDir;

use super::*;
use crate::controller::{resolve_selection, run};
use crate::terminal::{DEFAULT_TERMINAL_WIDTH, TerminalFacts, colors_for, screen_for, wants_dashboard};

// --- fixtures ----------------------------------------------------------------------------------------------------

/// One invocation's two streams and its exit status, which is the whole observable surface.
#[derive(Debug)]
struct Answer {
    exit: Exit,
    stdout: String,
    stderr: String,
}

/// An environment whose every path is under a temporary root.
///
/// `BUILD_WORKSPACE_DIRECTORY` is what the skill directory resolves from, so the suite never searches upwards and
/// never depends on where the test binary started - which under Bazel is a sandbox with no checkout above it.
struct Hermetic {
    root: TempDir,
}

impl Hermetic {
    fn new() -> Self {
        avl_affected::bridge::install_fixture();
        Self {
            root: tempfile::tempdir().expect("a temporary root"),
        }
    }

    fn pairs(&self) -> Vec<(String, String)> {
        let root = self.root.path();
        let path = |relative: &str| root.join(relative).to_string_lossy().into_owned();
        [
            ("HOME", path("")),
            ("BUILD_WORKSPACE_DIRECTORY", path("")),
            ("AIR_VM_RUNTIME_ROOT", path("runtime")),
            ("AIR_VM_IMAGE_ROOT", path("image")),
            ("AIR_VM_HOST_REPO", path("")),
            ("AIR_VM_BAZEL_USER_ROOT", path("bazel-root")),
        ]
        .into_iter()
        .map(|(name, value)| (name.to_owned(), value))
        .collect()
    }

    /// Runs the controller over in-memory writers, which is why `main` holds nothing but the process's facts.
    async fn invoke(&self, argv: &[&str]) -> Answer {
        let (reporter, stdout, stderr) = Reporter::in_memory("vm");
        let exit = run(
            os(argv),
            Environment::from_pairs(self.pairs()),
            reporter,
            TerminalFacts::default(),
            Interrupts::detached(),
        )
        .await;
        Answer {
            exit,
            stdout: stdout.text(),
            stderr: stderr.text(),
        }
    }
}

fn os(argv: &[&str]) -> Vec<OsString> {
    argv.iter().map(OsString::from).collect()
}

/// A lease receipt that names one pool.
///
/// Only the marker and the two axes matter to the receipt's backend inference, which runs before a config exists
/// and validates nothing else; the rest makes the file a receipt rather than a fragment that happens to parse.
fn write_receipt(directory: &Path, backend: &str, guest_os: &str) -> PathBuf {
    let path = directory.join(format!("receipt-{backend}-{guest_os}.json"));
    let content = format!(
        r#"{{"schemaVersion":1,"worker":"air-worker-1","token":"opaque","holder":"suite","acquiredAt":"2026-08-23T00:00:00.000Z","backend":"{backend}","guestOs":"{guest_os}","kind":"macos-vm-ui-test-lease"}}"#
    );
    std::fs::write(&path, content + "\n").expect("the receipt is written");
    path
}

/// The envelope every refusal shares - stdout untouched, one object on stderr, a nonzero exit - decoded, so each
/// test asserts only what is its own.
fn refusal(answer: &Answer) -> Value {
    assert!(!answer.exit.is_ok(), "expected a refusal, got {answer:?}");
    assert_eq!(answer.stdout, "", "a refusal wrote to stdout");
    let line = answer
        .stderr
        .strip_suffix('\n')
        .filter(|line| !line.contains('\n'))
        .unwrap_or_else(|| panic!("expected exactly one line on stderr, got {:?}", answer.stderr));
    let envelope: Value = serde_json::from_str(line).unwrap_or_else(|error| panic!("stderr is not one JSON object: {error} ({line:?})"));
    assert_eq!(envelope["schemaVersion"], SCHEMA_VERSION);
    assert_eq!(envelope["ok"], false);
    envelope
}

fn keys(envelope: &Value) -> Vec<&str> {
    let mut keys: Vec<&str> = envelope.as_object().expect("an object").keys().map(String::as_str).collect();
    keys.sort_unstable();
    keys
}

fn code(envelope: &Value) -> &str {
    envelope["error"]["code"].as_str().unwrap_or_default()
}

fn message(envelope: &Value) -> &str {
    envelope["error"]["message"].as_str().unwrap_or_default()
}

fn parse_argv(argv: &[&str], facts: &TerminalFacts) -> Parsed {
    avl_affected::bridge::install_fixture();
    parse(&os(argv), facts)
}

fn invocation(argv: &[&str], facts: &TerminalFacts) -> Invocation {
    match parse_argv(argv, facts) {
        Parsed::Invocation(invocation) => invocation,
        other => panic!("{argv:?} did not parse: {other:?}"),
    }
}

fn scan(argv: &[&str]) -> PartialRead {
    read_partially(&os(argv))
}

/// The long help of the command at `path`, as `--help` after it prints it.
fn long_help_of(path: &[&str]) -> String {
    let mut command = Cli::command();
    command.build();
    let mut current = &mut command;
    for name in path {
        current = current.find_subcommand_mut(name).unwrap_or_else(|| panic!("no command {name}"));
    }
    current.render_long_help().to_string()
}

/// The long help of every command in the tree, the root first: the text an agent reads and copies.
fn every_long_help() -> Vec<String> {
    fn collect(command: &mut clap::Command, into: &mut Vec<String>) {
        into.push(command.render_long_help().to_string());
        for subcommand in command.get_subcommands_mut() {
            collect(subcommand, into);
        }
    }
    let mut root = Cli::command();
    root.build();
    let mut texts = Vec::new();
    collect(&mut root, &mut texts);
    texts
}

fn terminal() -> TerminalFacts {
    TerminalFacts {
        stdout: true,
        stderr: true,
        width: 120,
        new_dashboard: None,
    }
}

fn environment(pairs: &[(&str, &str)]) -> Environment {
    Environment::from_pairs(pairs.iter().copied())
}

// --- the option grammar ------------------------------------------------------------------------------------------

#[tokio::test]
async fn a_command_the_tree_does_not_know_is_refused_rather_than_guessed() {
    let envelope = refusal(&Hermetic::new().invoke(&["bogus"]).await);
    assert_eq!(code(&envelope), "unknown_command");
    // The command is named in the envelope: it was chosen, it was simply not one of ours. That is a different fact
    // from an invocation that named no command, which reports `command: null`.
    assert_eq!(envelope["command"], "bogus");
    assert!(message(&envelope).contains(r#""bogus""#), "{envelope}");
}

#[tokio::test]
async fn an_unknown_verb_of_a_known_command_is_that_commands_usage_error() {
    let envelope = refusal(&Hermetic::new().invoke(&["pool", "status"]).await);
    assert_eq!(code(&envelope), "usage");
    assert_eq!(envelope["command"], "pool");
    assert!(message(&envelope).contains("status"), "{envelope}");
}

// `recycle` deletes clones, so its target is never defaulted the way `start` and `stop` default theirs to the whole
// pool: a bare `pool recycle` is a usage error rather than a pool-wide delete. A missing verb, an unknown one and an
// extra argument after a valid one are the same refusal, and none of them reaches the pool.
#[tokio::test]
async fn a_pool_verb_that_is_missing_unknown_or_overlong_is_a_usage_error() {
    let hermetic = Hermetic::new();
    for args in [
        &[][..],
        &["restart"],
        &["start", "air-docker-1", "extra"],
        &["gc", "all"],
        &["recycle"],
        &["recycle", "all", "extra"],
    ] {
        let argv: Vec<&str> = std::iter::once("pool").chain(args.iter().copied()).collect();
        let answer = hermetic.invoke(&argv).await;
        let envelope = refusal(&answer);
        assert_eq!(code(&envelope), "usage", "{argv:?}: {envelope}");
        assert_eq!(envelope["command"], "pool", "{argv:?}");
        assert_eq!(answer.exit, Exit::USAGE, "{argv:?}");
    }
}

// `pool idle-stop` is the detached process a release starts, so it parses but is not in the help of `pool`.
#[test]
fn the_idle_stop_verb_parses_and_stays_out_of_the_help() {
    let parsed = invocation(
        &["--backend", "docker", "pool", "idle-stop", "air-docker-1", "--nonce", "n-1"],
        &terminal(),
    );
    let Some(Cmd::Pool { verb }) = parsed.command else {
        panic!("not a pool command: {:?}", parsed.command);
    };
    assert_eq!(
        PoolCommand::from(verb),
        PoolCommand::IdleStop {
            worker: "air-docker-1".to_owned(),
            nonce: "n-1".to_owned(),
        }
    );
    assert!(!long_help_of(&["pool"]).contains("idle-stop"));
    assert!(long_help_of(&["lease", "release"]).contains("AIR_VM_IDLE_STOP"));
}

#[tokio::test]
async fn naming_no_command_answers_the_usage_text_with_no_command_named() {
    let answer = Hermetic::new().invoke(&[]).await;
    let envelope = refusal(&answer);
    assert_eq!(keys(&envelope), ["command", "error", "ok", "schemaVersion"]);
    assert_eq!(envelope["command"], Value::Null);
    assert_eq!(code(&envelope), "usage");
    assert_eq!(message(&envelope), help());
    assert!(message(&envelope).contains(&format!("Usage: {PROGRAM}")), "{envelope}");
    assert_eq!(answer.exit, Exit::USAGE);
}

// A named option with nothing after it is the same mistake in every spelling, so it is the same refusal.
#[tokio::test]
async fn a_lease_file_option_with_no_value_is_a_usage_refusal_in_either_spelling() {
    for argv in [
        &["--lease-file", "", "lease", "show"][..],
        &["--lease-file=", "lease", "show"],
        &["--lease-file"],
    ] {
        let answer = Hermetic::new().invoke(argv).await;
        let envelope = refusal(&answer);
        assert_eq!(code(&envelope), "usage", "{argv:?}");
        assert_eq!(answer.exit, Exit::USAGE, "{argv:?}");
        assert_eq!(message(&envelope), "--lease-file needs a value", "{argv:?}");
    }
}

// A refusal from the parse itself comes from an option the parse reached before the `--text` that follows it.
// Without the form being read from the whole command line, the caller would be answered in JSON - the one form it
// said it could not read.
#[tokio::test]
async fn a_parse_failure_is_still_reported_in_the_format_the_caller_asked_for() {
    let hermetic = Hermetic::new();
    let prose = hermetic.invoke(&["--backend", "bogus", "--text", "status"]).await;
    assert_eq!(prose.exit, Exit::USAGE);
    assert_eq!(prose.stdout, "");
    assert_eq!(prose.stderr, "vm: --backend must be tart, parallels, docker or container-linux\n");

    // The same invocation without `--text` is the JSON control: the difference between the two is the form, not the
    // refusal.
    let structured = refusal(&hermetic.invoke(&["--backend", "bogus", "status"]).await);
    assert_eq!(code(&structured), "usage");
    assert_eq!(structured["command"], Value::Null);

    let empty = hermetic.invoke(&["--lease-file", "", "--text", "status"]).await;
    assert_eq!(empty.stderr, "vm: --lease-file needs a value\n");

    // And through a failure clap itself reports, where no parse reached the `--text` at all: the scan does.
    let unplaceable = hermetic.invoke(&["status", "--no-such-option", "--text"]).await;
    assert_eq!(unplaceable.exit, Exit::USAGE);
    assert_eq!(unplaceable.stdout, "");
    assert!(
        unplaceable.stderr.starts_with("vm: ") && unplaceable.stderr.contains("--no-such-option"),
        "{:?}",
        unplaceable.stderr
    );
}

// A global option after the command is the same option. `run --stream` once read as "no streaming for run", and an
// agent then polled a finished run for hours.
#[test]
fn a_global_option_after_the_command_is_the_same_option() {
    let pipe = TerminalFacts::default();
    let parsed = invocation(
        &[
            "run",
            "--stream",
            "flow-x",
            "--text",
            "--lane",
            "ui",
            "--backend",
            "parallels",
            "--lease-file=receipt.json",
        ],
        &pipe,
    );
    assert!(parsed.stream && parsed.form.output == Output::Text);
    assert_eq!(parsed.lease_file, Some(PathBuf::from("receipt.json")));
    assert_eq!(parsed.selection.map(|s| s.backend), Some(Backend::Parallels));
    let Some(Cmd::Run(args)) = parsed.command else {
        panic!("not a run: {:?}", parsed.command);
    };
    assert_eq!(args.run.argv(), ["flow-x", "--lane", "ui"]);

    let mixed = invocation(
        &["--text", "daemon", "status", "--json", "--lease-file", "r.json", "--backend=docker"],
        &pipe,
    );
    assert!(mixed.form.output == Output::Json && mixed.form.chosen);
    assert_eq!(mixed.lease_file, Some(PathBuf::from("r.json")));
    assert_eq!(mixed.selection.map(|s| s.guest_os), Some(GuestOs::Linux));
    assert!(matches!(mixed.command, Some(Cmd::Daemon { verb: DaemonVerb::Status })));

    // Given twice, the later spelling wins rather than refusing.
    let twice = invocation(
        &["--stream", "--backend", "tart", "status", "--stream", "--backend", "docker"],
        &pipe,
    );
    assert_eq!(
        twice.selection,
        Some(Selection {
            backend: Backend::Docker,
            guest_os: GuestOs::Linux
        })
    );
}

// An acquisition that names no holder takes the invoking user's, in the shape a self-leased run uses, so a caller
// needs no uuid of its own; a named holder is kept as it is.
#[test]
fn an_acquisition_without_a_holder_takes_the_actor_id() {
    let pipe = TerminalFacts::default();
    let environment = Environment::from_pairs([("USER", "dev")]);
    let acquire = |argv: &[&str]| {
        let Some(Cmd::Lease { verb: Some(verb) }) = invocation(argv, &pipe).command else {
            panic!("{argv:?} is not a lease command");
        };
        let LeaseCommand::Acquire(request) = verb.into_command(&environment).expect("a request") else {
            panic!("{argv:?} is not an acquisition");
        };
        request
    };
    let defaulted = acquire(&["lease", "acquire"]);
    let Some(uuid) = defaulted.holder().strip_prefix("dev-lease-") else {
        panic!("{:?} is not <user>-lease-<uuid>", defaulted.holder());
    };
    assert_eq!(uuid.len(), 36, "{uuid:?} is not a uuid");
    assert_eq!(defaulted.count(), 1);
    assert_eq!(acquire(&["lease", "acquire", "--holder", "mine"]).holder(), "mine");
    assert_ne!(
        acquire(&["lease", "acquire"]).holder(),
        defaulted.holder(),
        "two acquisitions take two holders"
    );
}

// The value of a command option stays that value, even when it spells a global option.
#[test]
fn the_value_of_a_subcommand_option_is_never_a_global_option() {
    let pipe = TerminalFacts::default();
    for (argv, holder) in [
        (&["lease", "acquire", "--holder", "--text"][..], "--text"),
        (&["lease", "acquire", "--holder", "--backend"], "--backend"),
    ] {
        let parsed = invocation(argv, &pipe);
        assert!(parsed.form.output == Output::Json && !parsed.form.chosen, "{argv:?}");
        let Some(Cmd::Lease {
            verb: Some(LeaseVerb::Acquire { holder: Some(got), .. }),
        }) = parsed.command
        else {
            panic!("{argv:?} is not an acquisition: {:?}", parsed.command);
        };
        assert_eq!(got, holder);
    }

    // Values clap refuses by shape are refused as the command's usage error - in the default form, so none of them
    // was read as the global option it spells.
    for argv in [
        &["run", "flow-x", "--filter", "--stream"][..],
        &["ls", "/tmp", "--depth", "--backend"],
    ] {
        match parse_argv(argv, &terminal()) {
            Parsed::Invocation(parsed) => {
                assert!(
                    !parsed.stream && !parsed.form.chosen && parsed.lease_file.is_none() && parsed.selection.is_none(),
                    "{argv:?}: {parsed:?}"
                );
            }
            Parsed::Refused { form, command, .. } => {
                assert!(!form.chosen, "{argv:?}: {form:?}");
                assert_eq!(command.as_deref(), Some(argv[0]), "{argv:?}");
            }
            Parsed::Help { .. } => panic!("{argv:?} answered help"),
        }
    }
    assert_eq!(scan(&["lease", "acquire", "--holder", "--text"]).output, None);

    // A token that clap reads as an option is that option, not the value of the option before it: `--changed` gets
    // no value, so the refusal is answered in the form `--json` chose. clap's grammar decides, in the parse and in
    // the second reading alike.
    for (argv, json) in [
        (&["run", "--changed", "--json"][..], true),
        (
            &["peekaboo", "--cwd", "--lease-file", "--publish", "--text", "--stream", "--", "see"],
            false,
        ),
    ] {
        let Parsed::Refused { form, command, .. } = parse_argv(argv, &terminal()) else {
            panic!("{argv:?} parsed");
        };
        assert_eq!((form.output == Output::Json, form.chosen), (json, true), "{argv:?}");
        assert_eq!(command.as_deref(), Some(argv[0]), "{argv:?}");
    }
}

// `run --test-env NAME=VALUE` is a usage error whose envelope never quotes the value, which is the secret itself.
// `shard` and `flake` take no `--test-env` at all.
#[test]
fn a_test_env_value_is_refused_without_being_quoted() {
    let Parsed::Refused { command, refusal, .. } = parse_argv(&["run", "--lane", "ui", "--test-env", "A=secret-0123"], &terminal()) else {
        panic!("a value was accepted");
    };
    assert_eq!(command.as_deref(), Some("run"));
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    assert!(refusal.message.contains("a run hands the guest a file"), "{}", refusal.message);
    assert!(!refusal.message.contains("secret-0123"), "{}", refusal.message);
    for command in ["shard", "flake"] {
        let Parsed::Refused { refusal, .. } = parse_argv(&[command, "--lane", "ui", "--test-env", "A=@/a"], &terminal()) else {
            panic!("{command} took --test-env");
        };
        assert_eq!(refusal.exit, Exit::USAGE, "{command}");
    }
}

// The tokens after `--` are the guest's, so nothing reads them.
#[test]
fn the_tokens_after_the_separator_are_never_scanned() {
    let parsed = invocation(
        &["exec", "--text", "--", "--json", "--backend", "bogus", "--token"],
        &TerminalFacts::default(),
    );
    assert!(parsed.form.output == Output::Text && parsed.selection.is_none());
    let Some(Cmd::Exec { command }) = parsed.command else {
        panic!("not an exec: {:?}", parsed.command);
    };
    assert_eq!(command, ["--json", "--backend", "bogus", "--token"]);
    assert_eq!(scan(&["exec", "--", "--text"]).output, None);
    // A refused command line is read the same way: an unknown option is left out, and the `--` still ends it.
    assert_eq!(
        scan(&["exec", "--nope", "--", "--text"]),
        PartialRead {
            output: None,
            command: Some("exec".to_owned())
        }
    );
    // A `--` ends the values of a command option too.
    assert_eq!(scan(&["peekaboo", "--publish", "a", "--", "--text"]).output, None);
}

// `--help` before the command is the help of `vm`; after it, the command's own help. Either is answered the way
// every answer is: data under JSON, prose under text.
#[tokio::test]
async fn help_after_the_command_is_that_commands_own_help() {
    let hermetic = Hermetic::new();
    let structured = hermetic.invoke(&["run", "--help"]).await;
    assert_eq!(structured.exit, Exit::OK, "{structured:?}");
    let envelope: Value = serde_json::from_str(structured.stdout.trim_end()).expect("one envelope");
    assert_eq!(envelope["command"], "help");
    let text = envelope["data"]["usage"].as_str().expect("the help text");
    assert!(text.contains(&format!("{PROGRAM} run")), "{text}");
    assert!(text.contains("--fresh-ide"), "{text}");

    let prose = hermetic.invoke(&["--text", "lease", "acquire", "-h"]).await;
    assert_eq!(prose.exit, Exit::OK);
    assert!(prose.stdout.contains("--holder"), "{:?}", prose.stdout);
}

#[tokio::test]
async fn a_parse_failure_after_the_command_is_reported_in_the_requested_format() {
    let hermetic = Hermetic::new();
    let prose = hermetic.invoke(&["status", "--backend", "bogus", "--text"]).await;
    assert_eq!(prose.stdout, "");
    assert_eq!(prose.stderr, "vm: --backend must be tart, parallels, docker or container-linux\n");
    let structured = refusal(
        &hermetic
            .invoke(&["lease", "acquire", "--holder", "--text", "--backend", "bogus"])
            .await,
    );
    assert_eq!(code(&structured), "usage");
    // A value parser's own refusal keeps its code through the parse.
    let count = refusal(&hermetic.invoke(&["lease", "acquire", "--holder", "h", "--count", "0"]).await);
    assert_eq!(code(&count), "invalid_count");
    assert_eq!(count["command"], "lease");
}

// Every option the help text names, in its prose too, is one the tree declares, so the text an agent copies parses.
#[test]
fn every_option_the_help_names_is_declared_by_the_tree() {
    avl_affected::bridge::install_fixture();
    let mut root = Cli::command();
    root.build();
    let mut declared = std::collections::BTreeSet::new();
    fn collect(command: &clap::Command, into: &mut std::collections::BTreeSet<String>) {
        for arg in command.get_arguments() {
            if let Some(long) = arg.get_long() {
                into.insert(format!("--{long}"));
            }
        }
        for subcommand in command.get_subcommands() {
            collect(subcommand, into);
        }
    }
    collect(&root, &mut declared);
    let text = every_long_help().join("\n");
    let named: std::collections::BTreeSet<&str> = text
        .split(|c: char| !(c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-'))
        .filter(|word| word.len() > 2 && word.starts_with("--") && word.as_bytes()[2] != b'-')
        .collect();
    assert!(named.len() >= 15, "the scan found only {named:?}");
    let undeclared: Vec<&&str> = named.iter().filter(|option| !declared.contains(**option)).collect();
    assert!(
        undeclared.is_empty(),
        "the help text names {undeclared:?}, which no command declares"
    );
}

// --- the backend selection ---------------------------------------------------------------------------------------

// One selection from one source, and a disagreement on either axis is a refusal rather than a merge.
#[test]
fn the_backend_selection_comes_from_exactly_one_source() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let tart = Selection {
        backend: Backend::Tart,
        guest_os: GuestOs::Macos,
    };
    let parallels = Selection {
        backend: Backend::Parallels,
        guest_os: GuestOs::Macos,
    };
    let linux = Selection {
        backend: Backend::Docker,
        guest_os: GuestOs::Linux,
    };
    let linux_receipt = write_receipt(directory.path(), "docker", "linux");
    let macos_receipt = write_receipt(directory.path(), "tart", "macos");

    assert_eq!(resolve_selection(Some(tart), None), Ok(tart), "the flag alone");
    assert_eq!(resolve_selection(None, Some(&linux_receipt)), Ok(linux), "a receipt alone");
    assert_eq!(
        resolve_selection(Some(linux), Some(&linux_receipt)),
        Ok(linux),
        "a flag and a receipt that agree"
    );
    assert_eq!(resolve_selection(None, None), Ok(Selection::DEFAULT), "neither");
    // The default is the Docker pool on every host, a Linux guest for a measured reason.
    assert_eq!(Selection::DEFAULT, "docker".parse::<Selection>().unwrap());

    // Both halves are load-bearing: the pair is what a pool is.
    for (flag, receipt, disagreement) in [
        (parallels, &macos_receipt, "the backend halves"),
        (tart, &linux_receipt, "the backend and the guest OS halves"),
    ] {
        let refused = resolve_selection(Some(flag), Some(receipt)).expect_err(disagreement);
        assert_eq!(refused.code, "lease_backend_mismatch", "{disagreement}");
        assert_eq!(refused.exit, Exit::NO_PERM, "{disagreement}");
    }
}

// A receipt of the retired Tart Linux pool and `--backend tart` share the backend, so the refusal names both axes.
// Without the guest OS it said "tart, but --backend selected tart".
#[test]
fn a_stale_tart_linux_receipt_names_both_axes() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let stale = write_receipt(directory.path(), "tart", "linux");
    let tart = "tart".parse::<Selection>().unwrap();
    let refused = resolve_selection(Some(tart), Some(&stale)).expect_err("a stale receipt");
    assert_eq!((refused.code.as_ref(), refused.exit), ("lease_backend_mismatch", Exit::NO_PERM));
    assert_eq!(
        refused.message,
        "lease receipt selects tart with a linux guest, but --backend selected tart with a macos guest"
    );
}

// The mismatch reaches the caller as an envelope with the command named: the invocation parsed, and it is the pair
// of handles that is wrong.
#[tokio::test]
async fn a_lease_receipt_that_contradicts_the_backend_flag_refuses_the_whole_invocation() {
    let hermetic = Hermetic::new();
    let receipt = write_receipt(hermetic.root.path(), "docker", "linux");
    let receipt = receipt.to_string_lossy();
    let answer = hermetic.invoke(&["--backend", "tart", "--lease-file", &receipt, "status"]).await;
    let envelope = refusal(&answer);
    assert_eq!(code(&envelope), "lease_backend_mismatch");
    assert_eq!(answer.exit, Exit::NO_PERM);
    assert_eq!(envelope["command"], "status");
    assert!(
        message(&envelope).contains("docker") && message(&envelope).contains("tart"),
        "{envelope}"
    );
}

// --- the image pipeline ------------------------------------------------------------------------------------------

// `image` tells the pools apart, because only one of them has a golden image. The Docker refusal matters most: the
// Docker pool is the default on every host, so a bare `image` must refuse rather than run the macOS pipeline for
// someone who never asked for it.
#[tokio::test]
async fn only_the_sealed_macos_pool_has_an_image_to_validate_or_build() {
    let hermetic = Hermetic::new();
    // The Docker pool has no golden image.
    let bare = refusal(&hermetic.invoke(&["image", "validate"]).await);
    assert_eq!(code(&bare), "unsupported_backend_operation");
    for word in ["docker", "Dockerfile", "--backend tart"] {
        assert!(message(&bare).contains(word), "{word}: {bare}");
    }

    // A Windows host drives the Docker backend only, so there the Parallels pool is refused before its image is asked.
    let parallels = refusal(&hermetic.invoke(&["--backend", "parallels", "image"]).await);
    if cfg!(windows) {
        assert_eq!(code(&parallels), "unsupported_host_backend");
    } else {
        assert_eq!(code(&parallels), "unsupported_backend_operation");
        assert!(message(&parallels).contains("Parallels"), "{parallels}");
    }

    let docker = refusal(&hermetic.invoke(&["--backend", "docker", "image", "build"]).await);
    assert_eq!(code(&docker), "unsupported_backend_operation");
    assert_eq!(message(&docker), message(&bare), "{docker}");

    // A verb the pipeline has no script for is a usage refusal naming the wrapper, not the binary.
    let action = refusal(&hermetic.invoke(&["--backend", "tart", "image", "publish"]).await);
    assert_eq!(code(&action), "usage");
    assert!(message(&action).contains(PROGRAM), "{action}");
    assert_eq!(action["command"], "image");
}

// --- the daemon's one receipt-free verb --------------------------------------------------------------------------

// `daemon warm` is the only daemon verb that carries no receipt, so the dispatch has to reach the daemon without
// one. Every other verb is refused for the receipt this one does not need.
#[tokio::test]
async fn the_warm_verb_is_dispatched_without_a_lease_file() {
    let hermetic = Hermetic::new();
    let warm = refusal(&hermetic.invoke(&["daemon", "warm", "extra"]).await);
    assert_eq!(code(&warm), "usage");
    assert!(message(&warm).contains("extra"), "{warm}");
    assert_eq!(warm["command"], "daemon");
    let held = refusal(&hermetic.invoke(&["daemon", "status"]).await);
    assert_eq!(code(&held), "lease_file_required");
    assert!(long_help_of(&["daemon"]).contains("daemon warm"));
    assert!(matches!(
        invocation(&["daemon", "warm"], &TerminalFacts::default()).command,
        Some(Cmd::Daemon { verb: DaemonVerb::Warm })
    ));
}

// --- help and the success envelope -------------------------------------------------------------------------------

#[tokio::test]
async fn help_answers_the_usage_text_as_data_in_json_and_as_prose_in_text() {
    let hermetic = Hermetic::new();
    let structured = hermetic.invoke(&["--help"]).await;
    assert_eq!(structured.exit, Exit::OK);
    assert_eq!(structured.stderr, "");
    let line = structured
        .stdout
        .strip_suffix('\n')
        .filter(|line| !line.contains('\n'))
        .expect("exactly one line on stdout");
    let envelope: Value = serde_json::from_str(line).expect("one JSON object");
    assert_eq!(keys(&envelope), ["command", "data", "ok", "schemaVersion"]);
    assert_eq!(envelope["schemaVersion"], SCHEMA_VERSION);
    assert_eq!(envelope["ok"], true);
    assert_eq!(envelope["command"], "help");
    assert_eq!(envelope["data"]["usage"], help());

    // Text mode adds exactly one newline, because the help text does not end with one. Two would be a blank line
    // in an operator's terminal; none would run the next prompt into the last line. `-h` prints the same text.
    let prose = hermetic.invoke(&["--text", "-h"]).await;
    assert_eq!(prose.exit, Exit::OK);
    assert_eq!(prose.stderr, "");
    assert_eq!(prose.stdout, help() + "\n");
}

// The help text is documentation an agent copies, and the permission rules match a literal argv prefix, so every
// usage line in it is the wrapper's own path.
#[test]
fn the_help_names_the_wrapper_and_interpolates_the_lists_it_does_not_own() {
    avl_affected::bridge::install_fixture();
    for text in every_long_help() {
        for line in text.lines().filter(|line| line.starts_with("Usage: ")) {
            assert!(
                line.starts_with(&format!("Usage: {PROGRAM}")),
                "a usage line does not start with the wrapper: {line:?}"
            );
        }
    }
    // Each of these has exactly one owner; the help text reads it rather than restating it.
    let text = every_long_help().join("\n");
    for expected in [
        "ui|ui-real|ui-live|ui-perf|gui-chat",
        "explicit-only lane (ui-live, ui-perf)",
        "none|fresh-ide|daemon",
        "capped at 5",
        "default --reset fresh-ide",
        "include-tag, exclude-tag",
    ] {
        assert!(text.contains(expected), "the help text does not carry {expected:?}");
    }
}

// --- the form and the renderer -----------------------------------------------------------------------------------

// The form follows the reader: a terminal on stdout reads text, a pipe reads JSON, and the parse and the scan of a
// failed parse agree, so a refusal from the parse answers in the form a successful parse would have.
#[test]
fn the_default_form_follows_the_terminal() {
    let screen = TerminalFacts {
        stdout: true,
        stderr: true,
        width: 100,
        new_dashboard: None,
    };
    let stderr_only = TerminalFacts {
        stderr: true,
        ..TerminalFacts::default()
    };
    let pipe = TerminalFacts::default();
    for (name, argv, facts, json) in [
        ("a pipe", &["status"][..], &pipe, true),
        ("a terminal", &["status"], &screen, false),
        ("--json on a terminal", &["--json", "status"], &screen, true),
        ("--text on a pipe", &["status", "--text"], &pipe, false),
        ("stderr alone is a terminal", &["status"], &stderr_only, true),
    ] {
        let parsed = invocation(argv, facts);
        let scanned = Form::of(scan(argv).output, facts);
        assert_eq!(parsed.form.output == Output::Json, json, "{name}: the parse");
        assert_eq!(scanned, parsed.form, "{name}: the scan disagrees with the parse");
    }
}

// The footer is redrawn only for a form that followed a terminal on both descriptors, and never for a terminal
// that cannot erase a line.
#[test]
fn the_footer_is_redrawn_only_for_a_terminal_that_asked_for_nothing() {
    let facts = terminal();
    let followed = invocation(&["run", "flow-x"], &facts).form;
    assert_eq!(
        screen_for(&facts, followed, &environment(&[])),
        Terminal {
            redraw: true,
            color: true,
            width: 120
        }
    );
    let no_color = screen_for(&facts, followed, &environment(&[("NO_COLOR", "1")]));
    assert!(no_color.redraw && !no_color.color, "{no_color:?}");
    assert!(!screen_for(&facts, followed, &environment(&[("TERM", "dumb")])).redraw);
    let explicit = invocation(&["--text", "run", "flow-x"], &facts).form;
    assert!(!screen_for(&facts, explicit, &environment(&[])).redraw);
    let logged = TerminalFacts {
        stdout: true,
        ..TerminalFacts::default()
    };
    assert!(!screen_for(&logged, followed, &environment(&[])).redraw);
}

// A pseudo-terminal can answer a width of 0. The footer then takes `COLUMNS`, and 80 without it, because a footer
// with no bound wraps and cannot be erased.
#[test]
fn an_unknown_terminal_width_falls_back() {
    let unknown = TerminalFacts { width: 0, ..terminal() };
    let followed = invocation(&["run", "flow-x"], &unknown).form;
    assert_eq!(screen_for(&unknown, followed, &environment(&[])).width, DEFAULT_TERMINAL_WIDTH);
    assert_eq!(screen_for(&unknown, followed, &environment(&[("COLUMNS", "132")])).width, 132);
    assert_eq!(
        screen_for(&unknown, followed, &environment(&[("COLUMNS", "wide")])).width,
        DEFAULT_TERMINAL_WIDTH
    );
    let known = TerminalFacts { width: 100, ..terminal() };
    assert_eq!(screen_for(&known, followed, &environment(&[("COLUMNS", "132")])).width, 100);
}

// The dashboard's palette comes from the terminal's answer, or from `AIR_VM_THEME` without a question; a terminal
// that answers nothing keeps its own colours. True colour is what `COLORTERM` or the JetBrains terminal say.
#[test]
fn the_palette_comes_from_the_terminal_or_the_theme_variable() {
    use crate::console::{ColorDepth, TerminalColors};

    let answered = || Some(([0xbb, 0xbb, 0xbb], [0x2b, 0x2b, 0x2b]));
    let unanswered = || None;
    let never = || panic!("the terminal was asked although the theme was named");

    assert_eq!(
        colors_for(&environment(&[]), None, answered),
        Some(TerminalColors {
            foreground: [0xbb, 0xbb, 0xbb],
            background: [0x2b, 0x2b, 0x2b],
            depth: ColorDepth::Ansi256,
        })
    );
    assert_eq!(colors_for(&environment(&[]), None, unanswered), None);
    for (name, value) in [
        ("COLORTERM", "truecolor"),
        ("COLORTERM", "24bit"),
        ("TERMINAL_EMULATOR", "JetBrains-JediTerm"),
    ] {
        assert_eq!(
            colors_for(&environment(&[(name, value)]), None, answered).map(|colors| colors.depth),
            Some(ColorDepth::TrueColor),
            "{name}={value}"
        );
    }
    assert_eq!(
        colors_for(&environment(&[("COLORTERM", "yes")]), None, answered).map(|colors| colors.depth),
        Some(ColorDepth::Ansi256)
    );

    type Query = fn() -> Option<([u8; 3], [u8; 3])>;
    let themed = |value: &str, query: Query| {
        let variables = environment(&[(THEME_VARIABLE, value)]);
        colors_for(&variables, Presentation::load(&variables).theme, query)
    };
    let light = themed("light", never).expect("a light theme");
    assert_eq!(light.background, [0xff, 0xff, 0xff]);
    let dark = themed("dark", never).expect("a dark theme");
    assert_eq!(dark.background, [0x1e, 0x1e, 0x1e]);
    // A value that is neither side is ignored, and the terminal is asked.
    assert_eq!(themed("solarized", unanswered), None);
}

struct Silent;

impl Renderer for Silent {
    fn render(&mut self, _event: &Event, _scope: Option<&avl_base::Scope>) {}

    fn finish(&mut self) -> Option<Box<Verdict>> {
        None
    }
}

// The live dashboard is for a run that a person reads on a terminal that redraws. A command that is not a run, a
// `--stream`, and `AIR_VM_DASHBOARD=off` keep the plain lines.
#[test]
fn the_dashboard_is_only_for_a_run_on_a_redrawn_terminal() {
    let mut facts = TerminalFacts {
        new_dashboard: Some(Box::new(|_, _, _| -> Box<dyn Renderer> { Box::new(Silent) })),
        ..terminal()
    };
    let want = |facts: &TerminalFacts, argv: &[&str], pairs: &[(&str, &str)]| {
        let parsed = invocation(argv, facts);
        let variables = environment(pairs);
        let is_run = parsed.command.as_ref().is_some_and(Cmd::is_run);
        wants_dashboard(
            facts,
            screen_for(facts, parsed.form, &variables),
            parsed.stream,
            is_run,
            &Presentation::load(&variables),
        )
    };
    assert!(want(&facts, &["run", "flow-x"], &[]), "a run");
    assert!(want(&facts, &["shard", "--lane", "ui", "--shards", "2"], &[]), "a shard");
    assert!(want(&facts, &["flake", "--lane", "ui", "--trials", "2"], &[]), "a flake");
    assert!(!want(&facts, &["status"], &[]), "status");
    assert!(!want(&facts, &["--stream", "run", "flow-x"], &[]), "--stream");
    assert!(
        !want(&facts, &["run", "flow-x"], &[("AIR_VM_DASHBOARD", "off")]),
        "AIR_VM_DASHBOARD=off"
    );
    assert!(!want(&facts, &["--text", "run", "flow-x"], &[]), "an explicit --text");
    facts.new_dashboard = None;
    assert!(!want(&facts, &["run", "flow-x"], &[]), "no dashboard factory");
}

// --- shard and flake options --------------------------------------------------------------------------------------

/// The refusal the parse answers for `argv`, which must not parse.
fn parse_refusal(argv: &[&str]) -> Refusal {
    match parse_argv(argv, &TerminalFacts::default()) {
        Parsed::Refused { refusal, .. } => refusal,
        other => panic!("{argv:?} was expected to refuse, and parsed to {other:?}"),
    }
}

// The command's own options refuse in the controller's words, before anything is leased: a count outside the useful
// range, a count that is not a strict base-ten integer, and an option with no value.
#[test]
fn a_shard_count_the_split_cannot_use_is_a_usage_refusal() {
    for (argv, fragment) in [
        (&["--lane", "ui"][..], "--shards"),
        (&["--shards", "x", "--lane", "ui"], "--shards"),
        (&["--shards", "2.0", "--lane", "ui"], "--shards"),
        (&["--shards", "0", "--lane", "ui"], "--shards"),
        (&["--shards", "1e1", "--lane", "ui"], "--shards"),
        (&["--shards", "100", "--lane", "ui"], "--shards"),
        (&["--shards", "6", "--lane", "ui"], "--shards"),
        (&["--shards"], "--shards needs a value"),
    ] {
        let argv: Vec<&str> = std::iter::once("shard").chain(argv.iter().copied()).collect();
        let refusal = parse_refusal(&argv);
        assert_eq!(refusal.exit, Exit::USAGE, "{argv:?}");
        assert!(refusal.message.contains(fragment), "{argv:?}: {}", refusal.message);
    }
}

#[test]
fn a_trial_count_or_reset_the_measurement_cannot_use_is_a_usage_refusal() {
    for (argv, fragment) in [
        (&["--lane", "ui"][..], "--trials"),
        (&["--lane", "ui", "--trials", "0"], "--trials"),
        (&["--lane", "ui", "--trials", "101"], "--trials"),
        (&["--lane", "ui", "--trials", "two"], "--trials"),
        (&["--lane", "ui", "--trials"], "--trials needs a value"),
        // Strict base ten: `1e2` is not a hundred.
        (&["--lane", "ui", "--trials", "1e2"], "--trials"),
        (&["--lane", "ui", "--trials", "2", "--reset", "reboot"], "none, fresh-ide, daemon"),
        (&["--lane", "ui", "--trials", "2", "--reset"], "--reset needs a value"),
    ] {
        let argv: Vec<&str> = std::iter::once("flake").chain(argv.iter().copied()).collect();
        let refusal = parse_refusal(&argv);
        assert_eq!(refusal.exit, Exit::USAGE, "{argv:?}");
        assert!(refusal.message.contains(fragment), "{argv:?}: {}", refusal.message);
    }
}

// `report time` reads the runtime root of the invocation and needs no worker, so a run that is not there is its own
// refusal in the envelope of the command.
#[tokio::test]
async fn report_time_answers_from_the_runtime_root_and_refuses_a_run_that_is_not_there() {
    let envelope = refusal(&Hermetic::new().invoke(&["report", "time", "run-none", "--json"]).await);
    assert_eq!(envelope["command"], "report");
    assert_eq!(code(&envelope), "report_run_unknown");
    assert!(message(&envelope).contains("runtime"), "{envelope}");
    let Parsed::Invocation(invocation) = parse_argv(&["report", "time", "run-a", "--baseline", "iter-b"], &TerminalFacts::default()) else {
        panic!("report time did not parse");
    };
    let Some(Cmd::Report {
        verb: ReportVerb::Time(args),
    }) = invocation.command
    else {
        panic!("not report time");
    };
    assert_eq!((args.run.as_str(), args.baseline.as_deref()), ("run-a", Some("iter-b")));
}
