use std::sync::{Mutex, PoisonError};

use pretty_assertions::assert_eq;
use yonder_client::{Phase, Ran, Stop};

use super::*;

/// Child processes that answer every call with exit 0.
struct Checkout;

impl Processes for Checkout {
    fn run(&self, _: &Call, _: Stop<'_>) -> Ran {
        Ran {
            code: Some(0),
            ..Ran::default()
        }
    }
}

/// One invocation in a configuration directory without host files. Answers the exit code, the stdout and the stderr.
fn invoke(args: &[&str]) -> (u8, String, Vec<String>) {
    let home = tempfile::tempdir().expect("a temporary directory");
    invoke_in(home.path(), args)
}

/// One invocation with the configuration directory and the skill directory of the home directory `home`, which is a
/// checkout too.
fn invoke_in(home: &Path, args: &[&str]) -> (u8, String, Vec<String>) {
    fs::create_dir_all(home.join(".git")).expect("the git directory");
    let said = Mutex::new(Vec::new());
    let say = |line: &str| said.lock().unwrap_or_else(PoisonError::into_inner).push(line.to_owned());
    let mut stdout = Vec::new();
    let argv = std::iter::once("yonder").chain(args.iter().copied());
    let skills = home.join("skills/yonder");
    let code = run(argv, Ok(Config::in_home(home)), Some(&skills), home, &Checkout, &mut stdout, &say);
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
    let Command::Run { alias, fetch, command, .. } = cli.command else {
        panic!("{cli:?}")
    };
    assert_eq!(alias.as_str(), "studio");
    assert_eq!(fetch, ["out/bt-runs"]);
    assert_eq!(command, ["./bt.cmd", "--lane", "fast"]);
}

#[test]
fn a_run_without_a_command_is_a_usage_error() {
    let (code, stdout, said) = invoke(&["run", "studio"]);
    assert_eq!(code, 2);
    assert_eq!(stdout, "");
    assert!(said.join("\n").contains("<COMMAND>"), "{said:?}");
}

#[test]
fn a_run_ships_the_working_tree_at_niceness_10_unless_told_otherwise() {
    let parse = |args: &[&str]| {
        let cli = Cli::try_parse_from(std::iter::once("yonder").chain(args.iter().copied())).expect("parsed");
        let Command::Run { head, nice, apply, .. } = cli.command else {
            panic!("{cli:?}")
        };
        (head, nice, apply)
    };
    assert_eq!(parse(&["run", "studio", "--", "true"]), (false, 10, false));
    assert_eq!(
        parse(&["run", "studio", "--head", "--nice", "0", "--apply", "--", "true"]),
        (true, 0, true)
    );
    let (code, _, said) = invoke(&["run", "studio", "--nice", "20", "--", "true"]);
    assert_eq!(code, 2);
    assert!(said.join("\n").contains("20"), "{said:?}");
}

#[test]
fn a_run_that_the_host_did_not_take_leaves_by_75() {
    let (code, stdout, said) = invoke(&["run", "studio", "--", "true"]);
    assert_eq!(code, 75);
    assert_eq!(stdout, "");
    assert!(said[0].starts_with("yonder: no_host: "), "{said:?}");
}

#[test]
fn an_unprepared_host_is_refused_with_its_code_and_the_prepare_commands() {
    let (code, _, said) = invoke(&["runs", "studio"]);
    assert_eq!(code, 255);
    assert_eq!(said.len(), 1, "{said:?}");
    assert!(said[0].starts_with("yonder: no_host: no host file "), "{}", said[0]);
    assert!(said[0].contains("`yonder prepare studio` in a checkout"), "{}", said[0]);
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
    assert_eq!(holder_of(None, Path::new("/c")), holder(HOLDER_PREFIX, Path::new("/c")));
    assert_eq!(holder_of(Some("h".to_owned()), Path::new("/c")), "h");
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
            step("slot", 542_000),
            step("checkout", 41_000),
            step("command", 3_909_000),
        ],
        fetch: Vec::new(),
    };
    assert_eq!(
        run_line(&run),
        "1000-1  exit 0  yonder-0123456789ab  0123456789ab  ./bt.cmd FooTest  (slot 9m02s, checkout 41.0s, command 1h05m09s)"
    );
    run.exit = None;
    run.phases = vec![step("lock", 20)];
    assert_eq!(
        run_line(&run),
        "1000-1  running  yonder-0123456789ab  0123456789ab  ./bt.cmd FooTest"
    );
}

/// The output that `cquery --output=files` names, relative to the execution root.
const AGENT_OUTPUT: &str = "bazel-out/local_macos-fastbuild/bin/external/community+/tools/yonder/yonderd";

/// Child processes of `prepare` in the checkout `root` (none outside a checkout): a green Bazel whose execution root is
/// `<root>/execroot`, and an ssh that cannot connect. Each call is recorded.
struct Preparing {
    root: Option<PathBuf>,
    calls: Mutex<Vec<String>>,
}

impl Processes for Preparing {
    fn run(&self, call: &Call, _: Stop<'_>) -> Ran {
        let text = call.text();
        self.calls.lock().unwrap_or_else(PoisonError::into_inner).push(text.clone());
        let answer = |code: i32, stdout: String, stderr: &str| Ran {
            code: Some(code),
            stdout: stdout.into_bytes(),
            stderr: stderr.to_owned(),
            ..Ran::default()
        };
        if text.contains(" cquery ") {
            return answer(0, format!("{AGENT_OUTPUT}\n"), "");
        }
        if let (true, Some(root)) = (text.contains(" info execution_root"), &self.root) {
            return answer(0, format!("{}\n", root.join("execroot").display()), "");
        }
        if text.starts_with("ssh ") && !text.starts_with("ssh -G") {
            return answer(255, String::new(), "ssh: connect to host studio port 22: Connection refused");
        }
        answer(0, String::new(), "")
    }
}

fn prepare_in(root: Option<PathBuf>, args: &[&str]) -> (u8, Vec<String>, Vec<String>) {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let processes = Preparing {
        root,
        calls: Mutex::default(),
    };
    let said = Mutex::new(Vec::new());
    let say = |line: &str| said.lock().unwrap_or_else(PoisonError::into_inner).push(line.to_owned());
    let argv = ["yonder", "prepare"].into_iter().chain(args.iter().copied());
    let working = processes.root.clone().unwrap_or_else(|| dir.path().join("outside"));
    if let Some(root) = &processes.root {
        fs::create_dir_all(root.join(".git")).expect("the git directory");
    }
    let code = run(
        argv,
        Ok(Config::new(dir.path().to_path_buf())),
        None,
        &working,
        &processes,
        &mut Vec::new(),
        &say,
    );
    let calls = processes.calls.into_inner().unwrap_or_else(PoisonError::into_inner);
    (code, calls, said.into_inner().unwrap_or_else(PoisonError::into_inner))
}

/// `yonder prepare studio` in the monorepo builds the agent from the checkout root, takes its path from cquery, and
/// copies that binary over ssh.
#[test]
fn prepare_without_an_agent_builds_it_in_the_monorepo() {
    let checkout = tempfile::tempdir().expect("a checkout");
    let root = checkout.path().to_path_buf();
    fs::create_dir_all(root.join("community/tools/yonder")).expect("the agent sources");
    let binary = root.join("execroot").join(AGENT_OUTPUT);
    fs::create_dir_all(binary.parent().expect("a parent")).expect("the output directory");
    fs::write(&binary, b"yonderd").expect("the binary");

    let (code, calls, said) = prepare_in(Some(root.clone()), &["studio"]);
    assert_eq!(code, 255, "{said:?}");
    let label = "@community//tools/yonder:yonderd";
    let wrapper = root.join("bazel.cmd");
    assert!(calls[0].ends_with(&format!("{} build {label}", wrapper.display())), "{calls:#?}");
    assert!(
        calls[1].ends_with(&format!("{} cquery --output=files {label}", wrapper.display())),
        "{calls:#?}"
    );
    assert!(
        calls[2].ends_with(&format!("{} info execution_root", wrapper.display())),
        "{calls:#?}"
    );
    assert!(calls[3..].iter().any(|call| call.starts_with("ssh -o BatchMode=yes")), "{calls:#?}");
    assert!(
        said.last().is_some_and(|line| line.starts_with("yonder: ssh_failed: ")),
        "the build went through, and the copy failed at ssh: {said:#?}"
    );
}

/// Outside a checkout of the monorepo, `prepare` without `--agent` refuses with `usage` and builds nothing.
#[test]
fn prepare_without_an_agent_outside_the_monorepo_names_the_flag() {
    let plain = tempfile::tempdir().expect("a plain checkout");
    for root in [None, Some(plain.path().to_path_buf())] {
        let (code, calls, said) = prepare_in(root, &["studio"]);
        assert_eq!(code, 255);
        assert_eq!(said.len(), 1, "{said:#?}");
        assert!(said[0].starts_with("yonder: usage: "), "{}", said[0]);
        assert!(said[0].contains("`--agent <yonderd>`"), "{}", said[0]);
        assert!(!calls.iter().any(|call| call.contains("bazel.cmd")), "{calls:#?}");
    }
}

#[test]
fn prepare_takes_only_the_alias() {
    let cli = Cli::try_parse_from(["yonder", "prepare", "studio"]).expect("parsed");
    let Command::Prepare {
        alias, agent, port, repo, ..
    } = cli.command
    else {
        panic!("{cli:?}")
    };
    assert_eq!(
        (alias.as_str(), agent, port, repo.as_str()),
        ("studio", None, DEFAULT_PORT, "~/projects/idea-1")
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

#[test]
fn upgrade_takes_the_alias_and_an_agent_and_refuses_an_unprepared_host() {
    let cli = Cli::try_parse_from(["yonder", "upgrade", "studio", "--agent", "/tmp/yonderd", "--force"]).expect("parsed");
    let Command::Upgrade { alias, agent, force } = cli.command else {
        panic!("{cli:?}")
    };
    assert_eq!(
        (alias.as_str(), agent, force),
        ("studio", Some(PathBuf::from("/tmp/yonderd")), true)
    );
    let (code, _, said) = invoke(&["upgrade", "studio"]);
    assert_eq!(code, 255);
    assert!(said[0].starts_with("yonder: no_host: "), "{said:?}");
}

#[test]
fn an_upgrade_says_how_the_binary_came_and_which_agent_answers() {
    let upgraded = |way| yonder_client::Upgraded {
        way,
        previous_sha256: Some("a".repeat(64)),
        sha256: "b".repeat(64),
        version: "0.1.0".to_owned(),
        agent: yonder_client::AgentInfo {
            version: "0.1.0".to_owned(),
            os: "macos".to_owned(),
            arch: "aarch64".to_owned(),
            address: "192.168.1.24:7741".parse().expect("an address"),
        },
    };
    let answers = "yonderd 0.1.0 answers on 192.168.1.24:7741 (macos aarch64)";
    assert_eq!(
        upgraded_text("studio", &upgraded(UpgradeWay::Request)),
        format!("upgraded yonderd on studio from aaaaaaaaaaaa to bbbbbbbbbbbb over the link\n{answers}")
    );
    assert_eq!(
        upgraded_text("studio", &upgraded(UpgradeWay::Current)),
        format!("yonderd bbbbbbbbbbbb on studio is current\n{answers}")
    );
}

/// `yonder skill install` writes into the skill directory of the user, says so, and a second call finds it current.
#[test]
fn skill_install_writes_the_skill_of_the_user_and_then_finds_it_current() {
    let home = tempfile::tempdir().expect("a home directory");
    let dir = home.path().join("skills/yonder");
    let (code, stdout, said) = invoke_in(home.path(), &["skill", "install"]);
    assert_eq!(
        (code, stdout),
        (
            0,
            format!("installed the yonder skill of build {} in {}\n", skill::build(), dir.display())
        ),
        "{said:?}"
    );
    assert_eq!(fs::read_to_string(dir.join("SKILL.md")).expect("the skill"), skill::SKILL);

    let (code, stdout, _) = invoke_in(home.path(), &["skill", "install"]);
    assert_eq!(
        (code, stdout),
        (
            0,
            format!("the yonder skill of build {} in {} is current\n", skill::build(), dir.display())
        )
    );
}

/// An edited copy leaves by 255 with `skill_edited`, and `--dir` names another directory.
#[test]
fn skill_install_refuses_an_edited_copy_without_force() {
    let home = tempfile::tempdir().expect("a home directory");
    let other = home.path().join("codex/yonder");
    let dir = other.to_str().expect("a UTF-8 path");
    assert_eq!(invoke_in(home.path(), &["skill", "install", "--dir", dir]).0, 0);
    fs::write(other.join("SKILL.md"), "mine").expect("written");

    let (code, stdout, said) = invoke_in(home.path(), &["skill", "install", "--dir", dir]);
    assert_eq!((code, stdout.as_str()), (255, ""));
    assert!(said[0].starts_with("yonder: skill_edited: the skill in "), "{said:?}");

    let (code, stdout, _) = invoke_in(home.path(), &["skill", "install", "--dir", dir, "--force"]);
    assert_eq!(
        (code, stdout),
        (
            0,
            format!("replaced the edited yonder skill with build {} in {dir}\n", skill::build())
        )
    );
}

#[test]
fn skill_print_writes_the_skill_to_the_stdout() {
    let (code, stdout, _) = invoke(&["skill", "print"]);
    assert_eq!(code, 0);
    assert_eq!(stdout.trim_end(), skill::SKILL.trim_end());
}

/// `yonder upgrade` says that it updated a copy of another build before it talks to the host.
#[test]
fn upgrade_refreshes_an_installed_skill_of_another_build() {
    let home = tempfile::tempdir().expect("a home directory");
    let dir = home.path().join("skills/yonder");
    assert_eq!(invoke_in(home.path(), &["skill", "install"]).0, 0);
    let marker = dir.join(".yonder-skill.sha256");
    let old = "old words";
    fs::write(dir.join("SKILL.md"), old).expect("written");
    let lines = fs::read_to_string(&marker).expect("the marker");
    let sha256 = lines.split_once("  ").expect("a marker line").0;
    let mut digest = yonder_link::digest::Sha256::default();
    digest.update(old.as_bytes());
    let old_marker = lines.replacen(sha256, &digest.hex(), 1);
    fs::write(&marker, &old_marker).expect("written");
    let mut digest = yonder_link::digest::Sha256::default();
    digest.update(old_marker.as_bytes());
    let old_build = digest.hex()[..12].to_owned();

    let (code, _, said) = invoke_in(home.path(), &["upgrade", "studio"]);
    assert_eq!(code, 255);
    assert_eq!(
        said[0],
        format!(
            "yonder: replaced the skill of build {old_build} with build {} in {}",
            skill::build(),
            dir.display()
        )
    );
    assert!(said[1].starts_with("yonder: no_host: "), "{said:?}");
    assert_eq!(fs::read_to_string(dir.join("SKILL.md")).expect("the skill"), skill::SKILL);
}
