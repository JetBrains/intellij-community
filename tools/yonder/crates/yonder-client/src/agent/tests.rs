use std::fs;

use pretty_assertions::assert_eq;

use super::*;
use crate::testkit::{FakeProcesses, Said, calm, failed, ok};

const LABEL: &str = "@community//tools/yonder:yonderd";

/// The output path that `cquery --output=files` names, relative to the execution root.
const OUTPUT: &str = "bazel-out/local_macos-fastbuild/bin/external/community+/tools/yonder/yonderd";

/// An ultimate checkout root with the agent sources, and the binary of a build in `<root>/execroot` when `built`.
fn monorepo(built: bool) -> tempfile::TempDir {
    let dir = tempfile::tempdir().expect("a temporary directory");
    fs::create_dir_all(dir.path().join("community/tools/yonder")).expect("the agent sources");
    if built {
        let binary = execroot(dir.path()).join(OUTPUT);
        fs::create_dir_all(binary.parent().expect("a parent")).expect("the output directory");
        fs::write(&binary, b"yonderd").expect("the binary");
    }
    dir
}

/// The execution root of the fake Bazel, outside the checkout as in the monorepo.
fn execroot(root: &Path) -> PathBuf {
    root.join("execroot")
}

/// A green Bazel whose cquery names `files`.
fn green(root: &Path, files: &str) -> FakeProcesses {
    FakeProcesses::new(&[
        (" build @", ok("")),
        (" cquery ", ok(files)),
        (" info execution_root", ok(&format!("{}\n", execroot(root).display()))),
    ])
}

fn build_in(root: &Path, processes: &FakeProcesses, said: &Said) -> Result<PathBuf, Refusal> {
    let config = crate::Config::new(root.join("config"));
    let say = |line: &str| said.push(line);
    Client::new(config, processes, &say, &calm).build_agent(root)
}

/// The build goes from the checkout root under the quiet limit of 30 min. cquery names the binary relative to the
/// execution root, which `info` names.
#[test]
fn the_agent_is_built_in_the_monorepo_and_cquery_names_its_binary() {
    let root = monorepo(true);
    let processes = green(root.path(), &format!("{}\n{OUTPUT}\n", "bazel-out/other/yonderd.runfiles_manifest"));
    let said = Said::default();
    let binary = build_in(root.path(), &processes, &said).expect("built");
    assert_eq!(binary, execroot(root.path()).join(OUTPUT));

    let calls = processes.calls();
    let wrapper = root.path().join("bazel.cmd").to_string_lossy().into_owned();
    let tails: Vec<Vec<String>> = calls
        .iter()
        .map(|call| {
            let at = call
                .args
                .iter()
                .position(|arg| *arg == wrapper)
                .expect("the launcher of the checkout");
            call.args[at + 1..].to_vec()
        })
        .collect();
    assert_eq!(
        tails,
        [
            vec!["build", LABEL],
            vec!["cquery", "--output=files", LABEL],
            vec!["info", "execution_root"]
        ]
    );
    for call in &calls {
        assert_eq!(call.limit, Limit::Quiet(AGENT_BUILD_QUIET));
        assert_eq!(call.dir.as_deref(), Some(root.path()));
    }
    assert_eq!(AGENT_BUILD_QUIET, Duration::from_mins(30));
    assert!(
        said.lines()[0].starts_with(&format!("> ./bazel.cmd build {LABEL} in ")),
        "{:?}",
        said.lines()
    );
}

/// Outside the monorepo the client builds nothing and names `--agent`.
#[test]
fn outside_the_monorepo_the_build_is_refused_with_usage() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let processes = FakeProcesses::default();
    let refusal = build_in(dir.path(), &processes, &Said::default()).expect_err("no monorepo");
    assert_eq!(refusal.code, code::USAGE);
    assert!(refusal.message.contains("`--agent <yonderd>`"), "{}", refusal.message);
    assert_eq!(processes.calls(), []);
}

/// A failed build shows the end of its stderr, a quiet one says the limit, and a cquery without the binary fails.
#[test]
fn a_failed_build_shows_its_last_lines() {
    let root = monorepo(false);
    let stderr: String = (1..=30).map(|line| format!("line {line}\n")).collect();
    let processes = FakeProcesses::new(&[(" build @", failed(1, &stderr))]);
    let refusal = build_in(root.path(), &processes, &Said::default()).expect_err("failed");
    assert_eq!(refusal.code, code::AGENT_FAILED);
    assert!(refusal.message.contains("(exit 1):\nline 11\n"), "{}", refusal.message);
    assert!(refusal.message.ends_with("line 30"), "{}", refusal.message);
    assert!(!refusal.message.contains("line 10\n"), "{}", refusal.message);

    let quiet = Ran {
        stopped: Some(Stopped::Limit),
        ..Ran::default()
    };
    let processes = FakeProcesses::new(&[(" build @", quiet)]);
    let refusal = build_in(root.path(), &processes, &Said::default()).expect_err("stopped");
    assert!(
        refusal.message.ends_with("wrote nothing for 30 min and was stopped"),
        "{}",
        refusal.message
    );

    let processes = green(root.path(), &format!("{OUTPUT}\n"));
    let refusal = build_in(root.path(), &processes, &Said::default()).expect_err("no binary");
    assert_eq!(refusal.code, code::AGENT_FAILED);
    assert!(refusal.message.contains("named no yonderd file"), "{}", refusal.message);

    // A relative path from the checkout root is no execution root.
    let processes = FakeProcesses::new(&[
        (" build @", ok("")),
        (" cquery ", ok(&format!("{OUTPUT}\n"))),
        (" info execution_root", ok("execroot\n")),
    ]);
    let refusal = build_in(root.path(), &processes, &Said::default()).expect_err("no execution root");
    assert!(
        refusal.message.starts_with("`./bazel.cmd info execution_root` failed"),
        "{}",
        refusal.message
    );
}

#[test]
fn a_community_root_names_the_plain_target() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    assert_eq!(agent_label(dir.path()), None);
    fs::create_dir_all(dir.path().join("tools/yonder")).expect("the agent sources");
    assert_eq!(agent_label(dir.path()), None, "a community root has its own launcher");
    fs::write(dir.path().join("bazel.cmd"), "").expect("the launcher");
    assert_eq!(agent_label(dir.path()).as_deref(), Some(AGENT_TARGET));
}
