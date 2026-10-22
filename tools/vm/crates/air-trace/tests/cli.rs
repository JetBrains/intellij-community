//! The process-level tests of `air-trace`, the binary behind `trace.cmd`.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_air-trace`. Cargo sets it when it compiles the test,
//! and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn air_trace() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_air-trace")
        .or_else(|| option_env!("CARGO_BIN_EXE_air-trace").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_air-trace");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// Runs `air-trace` with `args` and no standard input, and answers the exit code, the standard output and the
/// standard error.
fn answer(args: &[&str]) -> (Option<i32>, String, String) {
    let output = Command::new(air_trace()).args(args).stdin(Stdio::null()).output().unwrap();
    let text = |bytes: Vec<u8>| String::from_utf8(bytes).unwrap();
    (output.status.code(), text(output.stdout), text(output.stderr))
}

/// No subcommand is a usage error on stderr with exit 2, and `--help` is the usage on stdout with exit 0. No
/// command line exits with 78, which `trace.cmd` keeps for its own failures.
#[test]
fn the_usage_is_exit_2_on_stderr_and_help_is_exit_0_on_stdout() {
    let (exit, stdout, stderr) = answer(&[]);
    assert_eq!((exit, stdout.as_str()), (Some(2), ""), "{stderr}");
    assert!(stderr.contains("Usage: air-trace"), "{stderr}");

    let (exit, stdout, stderr) = answer(&["--help"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    for subcommand in ["pack", "serve", "plan", "teamcity-publish"] {
        assert!(stdout.contains(subcommand), "{subcommand}: {stdout}");
    }
}

/// A refusal is one `air-trace <command>: <message>` line on stderr, and the exit of the refusal.
#[test]
fn a_refusal_is_one_line_and_its_exit() {
    let scratch = std::env::temp_dir().join(format!("air-trace-cli-{}", std::process::id()));
    let missing = scratch.join("missing");
    let missing = missing.to_str().unwrap();

    let (exit, stdout, stderr) = answer(&["pack", missing, &format!("{missing}.zip")]);
    assert_eq!((exit, stdout.as_str()), (Some(1), ""), "{stderr}");
    assert!(stderr.starts_with("air-trace pack: "), "{stderr}");
    assert_eq!(stderr.lines().count(), 1, "{stderr}");

    let (exit, stdout, stderr) = answer(&["plan", "--repo", missing, "flow-x"]);
    assert_eq!((exit, stdout.as_str()), (Some(1), ""), "{stderr}");
    assert!(stderr.starts_with("air-trace plan: the checkout "), "{stderr}");

    let (exit, stdout, stderr) = answer(&["serve", "--no-default-roots"]);
    assert_eq!(
        (exit, stdout.as_str(), stderr.as_str()),
        (Some(2), "", "air-trace serve: no roots to look in; pass --root DIR\n")
    );

    let (exit, _, stderr) = answer(&["serve", "--teamcity", "1", "--detach"]);
    assert_eq!(exit, Some(2), "{stderr}");
    assert!(
        stderr.starts_with("air-trace serve: --teamcity needs a server of its own"),
        "{stderr}"
    );

    let (exit, _, stderr) = answer(&["serve", "--no-default-roots", "--port", "0", "--teamcity", "build-1"]);
    assert_eq!(exit, Some(2), "{stderr}");
    assert!(stderr.contains("air-trace serve: --teamcity build-1: not a build id"), "{stderr}");
}

/// `teamcity-publish` never fails a build: testlogs that are not there are a warning of the build log and exit 0.
/// Only a usage error of the command line exits 2.
#[test]
fn teamcity_publish_answers_0_without_testlogs_and_2_for_a_usage_error() {
    let scratch = std::env::temp_dir().join(format!("air-trace-cli-publish-{}", std::process::id()));
    let missing = scratch.join("missing");
    let (out, missing) = (scratch.join("out"), missing.to_str().unwrap());
    let (exit, stdout, stderr) = answer(&["teamcity-publish", "--testlogs", missing, "--out", out.to_str().unwrap()]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        stdout.contains(&format!(
            "##teamcity[message text='air-trace teamcity-publish: no testlogs: {missing}: not a directory' status='WARNING']"
        )),
        "{stdout}"
    );
    assert!(stderr.contains("0 bundle zip(s) from 0 test.xml file(s)"), "{stderr}");

    for usage in [
        &["teamcity-publish", "--no-such-flag"][..],
        &["teamcity-publish", "--since", "yesterday"],
    ] {
        let (exit, _, stderr) = answer(usage);
        assert_eq!(exit, Some(2), "{usage:?}: {stderr}");
    }
}

/// A testlogs tree with one bundle, a checkout whose `bazel.cmd` writes its arguments into `bazel-calls`, and an
/// output directory, under one temporary directory.
#[cfg(unix)]
fn publish_fixture() -> tempfile::TempDir {
    use std::os::unix::fs::PermissionsExt;
    let dir = tempfile::tempdir().unwrap();
    let lane = dir.path().join("testlogs/ui/ui_lane_test");
    // A bundle of its manifest and an empty span file: the test has no test data of the trace crate under Bazel.
    let placed = avl_trace::bundle::bundle_path("iter-1", "AirComposerUiTest", "sends a prompt");
    let bundle = avl_trace::bundle::bundle_file(&lane.join("test.outputs/air-traces"), &placed);
    std::fs::create_dir_all(&bundle).unwrap();
    let manifest = r#"{"schema": "air-trace/1", "runId": "iter-1", "testClass": "AirComposerUiTest",
        "scenario": "sends a prompt", "lane": "UI", "launcher": "daemon", "status": "passed",
        "startedAt": "2026-10-21T10:00:00.000Z", "durationMs": 1000, "capture": {"source": "x11"},
        "video": {"codec": "none", "reason": "fixture"}}"#;
    std::fs::write(bundle.join(avl_trace::bundle::MANIFEST_FILE), manifest).unwrap();
    std::fs::write(bundle.join(avl_trace::bundle::SPANS_FILE), "").unwrap();
    std::fs::write(lane.join("test.xml"), "<testsuites/>").unwrap();
    let docs = dir.path().join("checkout/plugins/air/docs");
    std::fs::create_dir_all(&docs).unwrap();
    std::fs::write(docs.join("package.json"), "{}").unwrap();
    let bazel = dir.path().join("checkout/bazel.cmd");
    let calls = dir.path().join("bazel-calls");
    std::fs::write(&bazel, format!("#!/bin/sh\necho \"$*\" >> '{}'\n", calls.display())).unwrap();
    std::fs::set_permissions(&bazel, std::fs::Permissions::from_mode(0o755)).unwrap();
    dir
}

/// Runs `teamcity-publish` over the fixture with the variables given, and no other variable of TeamCity, Bazel or the
/// viewer tools. Answers the exit and the standard error.
#[cfg(unix)]
fn publish_with(fixture: &std::path::Path, variables: &[(&str, &str)]) -> (Option<i32>, String) {
    let mut command = Command::new(air_trace());
    command
        .args(["teamcity-publish", "--testlogs"])
        .arg(fixture.join("testlogs"))
        .arg("--out")
        .arg(fixture.join("out"))
        .env("BUILD_WORKSPACE_DIRECTORY", fixture.join("checkout"))
        .stdin(Stdio::null());
    for name in [
        "TEAMCITY_VERSION",
        "TEAMCITY_BUILD_PROPERTIES_FILE",
        "BUILD_URL",
        "BUILD_WORKING_DIRECTORY",
        "AIR_TRACE_NODE",
        "AIR_TRACE_PNPM",
        "XML_OUTPUT_FILE",
    ] {
        command.env_remove(name);
    }
    command.envs(variables.iter().copied());
    let output = command.output().unwrap();
    (output.status.code(), String::from_utf8(output.stderr).unwrap())
}

/// The variables of the step before the publish name the tools of the viewer build, and `none` builds no viewer with
/// one warning. On TeamCity without them, the step starts no Bazel and builds no viewer. A local step without them
/// asks the Bazel of the checkout, as `serve` does.
#[cfg(unix)]
#[test]
fn teamcity_publish_takes_its_tools_from_the_variables_and_starts_no_bazel_on_team_city() {
    let fixture = publish_fixture();
    let calls = fixture.path().join("bazel-calls");

    let (exit, stderr) = publish_with(fixture.path(), &[("AIR_TRACE_NODE", "none"), ("AIR_TRACE_PNPM", "none")]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        stderr.contains("no viewer: AIR_TRACE_NODE or AIR_TRACE_PNPM is none, so no node and no pnpm build the viewer"),
        "{stderr}"
    );
    assert!(!calls.exists(), "no Bazel runs: {stderr}");

    let (exit, stderr) = publish_with(fixture.path(), &[]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        stderr.contains("1 bundle zip(s) from 1 test.xml"),
        "the fixture has a bundle: {stderr}"
    );
    let asked = std::fs::read_to_string(&calls).unwrap_or_default();
    assert!(
        asked.starts_with("cquery "),
        "a local step asks the Bazel of the checkout: {asked:?} {stderr}"
    );
    std::fs::remove_file(&calls).unwrap();

    let (exit, stderr) = publish_with(fixture.path(), &[("TEAMCITY_VERSION", "2026.3 EAP (build 265562)")]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        stderr.contains("no viewer: on TeamCity the step starts no Bazel to find node and pnpm"),
        "{stderr}"
    );
    assert!(!calls.exists(), "no Bazel runs on TeamCity: {stderr}");
}

/// Off TeamCity the testlogs keep their bundles, unless `--remove-packed` asks. On TeamCity the step removes the
/// bundles that it published from the testlogs.
#[cfg(unix)]
#[test]
fn teamcity_publish_removes_the_packed_bundles_on_team_city_or_when_asked() {
    let bundle = |fixture: &tempfile::TempDir| {
        avl_trace::bundle::bundle_file(
            &fixture.path().join("testlogs/ui/ui_lane_test/test.outputs/air-traces"),
            &avl_trace::bundle::bundle_path("iter-1", "AirComposerUiTest", "sends a prompt"),
        )
    };
    let none = [("AIR_TRACE_NODE", "none"), ("AIR_TRACE_PNPM", "none")];

    let local = publish_fixture();
    let (exit, stderr) = publish_with(local.path(), &none);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        bundle(&local).join(avl_trace::bundle::MANIFEST_FILE).is_file(),
        "a local step keeps the testlogs: {stderr}"
    );
    assert!(!stderr.contains("removed "), "{stderr}");

    let asked = publish_fixture();
    let output = Command::new(air_trace())
        .args(["teamcity-publish", "--remove-packed", "--testlogs"])
        .arg(asked.path().join("testlogs"))
        .arg("--out")
        .arg(asked.path().join("out"))
        .envs(none)
        .env_remove("TEAMCITY_VERSION")
        .env_remove("BUILD_WORKSPACE_DIRECTORY")
        .stdin(Stdio::null())
        .output()
        .unwrap();
    let stderr = String::from_utf8(output.stderr).unwrap();
    assert_eq!(output.status.code(), Some(0), "{stderr}");
    assert!(stderr.contains("removed 1 packed bundle(s) with "), "{stderr}");
    assert!(!bundle(&asked).exists(), "{stderr}");

    let on_team_city = publish_fixture();
    let (exit, stderr) = publish_with(on_team_city.path(), &[none[0], none[1], ("TEAMCITY_VERSION", "2026.3")]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(stderr.contains("removed 1 packed bundle(s) with "), "{stderr}");
    assert!(!bundle(&on_team_city).exists(), "{stderr}");
}
