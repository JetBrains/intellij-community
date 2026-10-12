use std::fs;
use std::path::{Path, PathBuf};
use std::time::Duration;

use clap::CommandFactory;
use clap::error::ErrorKind;
use pretty_assertions::assert_eq;

use super::*;

/// Parses a command line after the program name, as the process would see it.
fn parse(args: &[&str]) -> Result<Cli, clap::Error> {
    Cli::try_parse_from(std::iter::once("air-trace").chain(args.iter().copied()))
}

/// Parses a command line clap must refuse, and answers the error with its rendered text.
fn refused(args: &[&str]) -> (clap::Error, String) {
    match parse(args) {
        Ok(cli) => panic!("air-trace {args:?} parsed into {cli:?}"),
        Err(error) => {
            let text = error.render().to_string();
            (error, text)
        }
    }
}

#[test]
fn the_dispatcher_routes_every_declared_subcommand_and_refuses_the_rest() {
    Cli::command().debug_assert();
    let names: Vec<String> = Cli::command()
        .get_subcommands()
        .map(|subcommand| subcommand.get_name().to_owned())
        .collect();
    assert_eq!(names, ["pack", "serve", "plan"]);

    // No subcommand: the usage, on stderr, as a usage error.
    let (error, text) = refused(&[]);
    assert_eq!(parse_exit(&error), Exit::Usage.code(), "{text}");
    assert!(error.use_stderr());
    assert!(text.contains("Usage: air-trace"), "{text}");

    // --help: the usage on stdout, naming every subcommand, and exit 0.
    let (error, text) = refused(&["--help"]);
    assert_eq!(error.kind(), ErrorKind::DisplayHelp);
    assert_eq!(parse_exit(&error), 0);
    for name in &names {
        assert!(text.contains(name.as_str()), "{name} is not in {text}");
    }
    assert!(text.contains(&runs_url(DEFAULT_SERVE_PORT)), "{text}");

    let (error, text) = refused(&["replay"]);
    assert_eq!(error.kind(), ErrorKind::InvalidSubcommand);
    assert_eq!(parse_exit(&error), Exit::Usage.code());
    assert!(text.contains("'replay'"), "{text}");

    // A subcommand parses its own arguments: `plan` without an INPUT is its usage error, not the dispatcher's.
    let (error, text) = refused(&["plan"]);
    assert_eq!(error.kind(), ErrorKind::MissingRequiredArgument);
    assert_eq!(parse_exit(&error), Exit::Usage.code());
    assert!(text.contains("Usage: air-trace plan"), "{text}");
}

/// No exit code the dispatcher can answer for a command line is 78, which `trace.cmd` keeps for its own failures.
#[test]
fn no_refused_command_line_exits_with_the_wrappers_code() {
    for args in [
        &[][..],
        &["--help"],
        &["replay"],
        &["plan"],
        &["pack", "only-one"],
        &["serve", "--port", "many"],
        &["serve", "--idle-exit", "soon"],
    ] {
        let (error, text) = refused(args);
        assert_ne!(parse_exit(&error), 78, "air-trace {args:?}: {text}");
    }
}

/// `serve --detach`, and the controller through `trace.cmd serve`, start this binary as `serve --port N --idle-exit
/// D --pid-file F …`, with the duration the way `avl_host_sys::viewer` writes it, so the dispatcher must route every one of
/// those flags, the hidden one included.
#[test]
fn the_argv_a_detached_server_is_started_with_parses() {
    let cli = parse(&[
        "serve",
        "--port",
        "7357",
        "--idle-exit",
        "30m",
        "--pid-file",
        "runtime/viewer/serve.pid",
        "--site",
        "out/air-site",
        "--root",
        "one",
        "--root",
        "two.zip",
        "--no-default-roots",
    ])
    .unwrap();
    let Command::Serve(args) = cli.command else {
        panic!("{:?} is not serve", cli.command);
    };
    assert_eq!(args.port, 7357);
    assert_eq!(args.idle_exit, Some(Duration::from_mins(30)));
    assert_eq!(args.pid_file.as_deref(), Some(Path::new("runtime/viewer/serve.pid")));
    assert_eq!(args.site.as_deref(), Some(Path::new("out/air-site")));
    assert_eq!(args.roots, [PathBuf::from("one"), PathBuf::from("two.zip")]);
    assert!(args.no_default_roots);
    assert!(!args.detach);

    // `trace.cmd serve --detach` as the docs spell it, with the port's default.
    let Command::Serve(args) = parse(&["serve", "--detach"]).unwrap().command else {
        panic!("not serve");
    };
    assert!(args.detach);
    assert_eq!(args.port, DEFAULT_SERVE_PORT);
}

/// `trace.cmd plan` as the docs and the skill spell it: a report path, and the JSON answer with extra roots.
#[test]
fn the_plan_argv_of_the_docs_parses() {
    let Command::Plan(args) = parse(&["plan", "out/vm-report.json"]).unwrap().command else {
        panic!("not plan");
    };
    assert_eq!(args.inputs, ["out/vm-report.json"]);
    assert!(!args.json);

    let Command::Plan(args) = parse(&[
        "plan",
        "--json",
        "--repo",
        "checkout",
        "--root",
        "traces",
        "--no-default-roots",
        "AirChatScenarioTest",
        "-",
    ])
    .unwrap()
    .command
    else {
        panic!("not plan");
    };
    assert!(args.json && args.no_default_roots);
    assert_eq!(args.repo.as_deref(), Some(Path::new("checkout")));
    assert_eq!(args.roots, [PathBuf::from("traces")]);
    assert_eq!(args.inputs, ["AirChatScenarioTest", "-"]);
}

/// `pack SOURCE_DIR DESTINATION_ZIP`: exactly two paths, and no flag.
#[test]
fn the_pack_argv_takes_exactly_two_paths() {
    let Command::Pack(pack) = parse(&["pack", "traces", "out/traces.zip"]).unwrap().command else {
        panic!("not pack");
    };
    assert_eq!(
        pack,
        PackArgs {
            source: "traces".into(),
            destination: "out/traces.zip".into(),
        }
    );
    for args in [&["pack", "traces"][..], &["pack", "a", "b", "c"], &["pack", "--av1", "a", "b"]] {
        let (error, text) = refused(args);
        assert_eq!(parse_exit(&error), Exit::Usage.code(), "air-trace {args:?}: {text}");
    }
}

/// A directory of its own under the test's temporary directory, removed when dropped.
struct Scratch(PathBuf);

impl Scratch {
    fn new(name: &str) -> Self {
        let base = std::env::var_os("TEST_TMPDIR").map_or_else(std::env::temp_dir, PathBuf::from);
        let path = base.join(format!("air-trace-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&path);
        fs::create_dir_all(&path).unwrap();
        Self(path)
    }
}

impl Drop for Scratch {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

#[test]
fn pack_answers_its_summary_on_stdout_and_exits_0() {
    let scratch = Scratch::new("pack");
    let bundle = scratch.0.join("traces/run/Test/scenario");
    fs::create_dir_all(&bundle).unwrap();
    fs::write(bundle.join(avl_trace::bundle::SPANS_FILE), "{}\n").unwrap();
    let destination = scratch.0.join("traces.zip");
    let args = PackArgs {
        source: scratch.0.join("traces"),
        destination: destination.clone(),
    };

    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    let code = run_pack(&args, &mut stdout, &mut stderr);
    let (stdout, stderr) = (String::from_utf8(stdout).unwrap(), String::from_utf8(stderr).unwrap());
    assert_eq!(code, 0, "{stderr}");
    assert_eq!(stderr, "");
    assert!(stdout.starts_with("packed 1 files from 1 bundles into "), "{stdout}");
    assert!(destination.is_file());
}

#[test]
fn pack_of_a_missing_tree_exits_1_with_the_reason_on_stderr() {
    let scratch = Scratch::new("pack-missing");
    let args = PackArgs {
        source: scratch.0.join("absent"),
        destination: scratch.0.join("traces.zip"),
    };

    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    let code = run_pack(&args, &mut stdout, &mut stderr);
    let stderr = String::from_utf8(stderr).unwrap();
    assert_eq!(code, 1);
    assert!(stdout.is_empty());
    assert!(stderr.starts_with("air-trace pack: "), "{stderr}");
    assert!(stderr.contains("absent"), "{stderr}");
}
