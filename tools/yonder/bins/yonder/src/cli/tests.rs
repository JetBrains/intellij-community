use std::sync::{Mutex, PoisonError};

use pretty_assertions::assert_eq;
use yonder_client::{Phase, Ran};

use super::*;

/// Child processes that answer `git rev-parse --show-toplevel` with `/c` and every other call with exit 0.
struct Checkout;

impl Processes for Checkout {
    fn run(&self, call: &Call) -> Ran {
        let stdout = if call.text().contains("--show-toplevel") {
            b"/c\n".to_vec()
        } else {
            Vec::new()
        };
        Ran {
            code: Some(0),
            stdout,
            ..Ran::default()
        }
    }

    fn run_to_file(&self, call: &Call, stdout: &Path, _: &(dyn Fn() -> bool + Sync)) -> Ran {
        let ran = self.run(call);
        std::fs::write(stdout, &ran.stdout).expect("the stdout file");
        ran
    }
}

/// One invocation in a configuration directory without host files. Answers the exit code, the stdout and the stderr.
fn invoke(args: &[&str]) -> (u8, String, Vec<String>) {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let said = Mutex::new(Vec::new());
    let say = |line: &str| said.lock().unwrap_or_else(PoisonError::into_inner).push(line.to_owned());
    let mut stdout = Vec::new();
    let argv = std::iter::once("yonder").chain(args.iter().copied());
    let code = run(argv, Ok(Config::new(dir.path().to_path_buf())), &Checkout, &mut stdout, &say);
    let said = said.into_inner().unwrap_or_else(PoisonError::into_inner);
    (code, String::from_utf8_lossy(&stdout).into_owned(), said)
}

#[test]
fn a_run_takes_its_command_after_the_separator() {
    let cli = Cli::try_parse_from([
        "yonder",
        "run",
        "studio",
        "--fetch",
        "out/bt-runs",
        "--",
        "./bt.cmd",
        "--lane",
        "fast",
    ])
    .expect("parsed");
    let Command::Run {
        alias,
        fetch,
        command,
        idle_hours,
        ..
    } = cli.command
    else {
        panic!("{cli:?}")
    };
    assert_eq!((alias.as_str(), idle_hours), ("studio", 8));
    assert_eq!(fetch, ["out/bt-runs"]);
    assert_eq!(command, ["./bt.cmd", "--lane", "fast"]);
}

#[test]
fn a_run_without_a_command_is_refused_with_255() {
    let (code, stdout, said) = invoke(&["run", "studio"]);
    assert_eq!(code, 255);
    assert_eq!(stdout, "");
    assert!(said.join("\n").contains("<COMMAND>"), "{said:?}");
}

#[test]
fn an_unprepared_host_is_refused_with_its_code_and_the_prepare_commands() {
    let (code, _, said) = invoke(&["runs", "studio"]);
    assert_eq!(code, 255);
    assert_eq!(said.len(), 1, "{said:?}");
    assert!(said[0].starts_with("yonder: no_host: no host file "), "{}", said[0]);
    assert!(said[0].contains("`yonder prepare studio --agent <yonderd>`"), "{}", said[0]);
}

#[test]
fn the_help_goes_to_the_stdout_and_leaves_by_0() {
    let (code, stdout, _) = invoke(&["--help"]);
    assert_eq!(code, 0);
    assert!(stdout.contains("prepare"), "{stdout}");
}

#[test]
fn an_exit_code_outside_a_byte_leaves_by_255() {
    assert_eq!(exit_code(3), 3);
    assert_eq!(exit_code(130), 130);
    assert_eq!(exit_code(-1), 255);
    assert_eq!(exit_code(256), 255);
}

#[test]
fn the_default_holder_is_the_one_of_the_checkout() {
    assert_eq!(
        holder_of(None, &Checkout).expect("a holder"),
        holder(HOLDER_PREFIX, Path::new("/c"))
    );
    assert_eq!(holder_of(Some("h".to_owned()), &Checkout).expect("a holder"), "h");
}

/// A run of `yonder runs` shows its steps in the short form, and a run without long steps shows none.
#[test]
fn a_listed_run_shows_its_long_steps() {
    let step = |name: &str, ms: u64| Phase { name: name.to_owned(), ms };
    let mut run = RunInfo {
        run: "1000-1".to_owned(),
        holder: "yonder-0123456789ab".to_owned(),
        commit: "0123456789abcdef0123456789abcdef01234567".to_owned(),
        command: vec!["./bt.cmd".to_owned(), "FooTest".to_owned()],
        started_ms: 1000,
        exit: Some(0),
        phases: vec![
            step("lock", 0),
            step("lease", 542_000),
            step("checkout", 41_000),
            step("command", 3_909_000),
        ],
    };
    assert_eq!(
        run_line(&run),
        "1000-1  exit 0  yonder-0123456789ab  0123456789ab  ./bt.cmd FooTest  (lease 9m02s, checkout 41.0s, command 1h05m09s)"
    );
    run.exit = None;
    run.phases = vec![step("lock", 20)];
    assert_eq!(
        run_line(&run),
        "1000-1  running  yonder-0123456789ab  0123456789ab  ./bt.cmd FooTest"
    );
}

#[test]
fn fetch_and_forget_name_a_run() {
    let cli = Cli::try_parse_from(["yonder", "fetch", "studio", "1-2", "--fetch", "out/bt-runs"]).expect("parsed");
    let Command::Fetch { alias, run, fetch } = cli.command else {
        panic!("{cli:?}")
    };
    assert_eq!(
        (alias.as_str(), run.as_str(), fetch),
        ("studio", "1-2", vec!["out/bt-runs".to_owned()])
    );
    let cli = Cli::try_parse_from(["yonder", "forget", "studio", "1-2"]).expect("parsed");
    assert!(matches!(cli.command, Command::Forget { .. }), "{cli:?}");
}
