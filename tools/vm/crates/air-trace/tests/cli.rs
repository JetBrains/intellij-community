//! The process-level tests of `air-trace`, the binary behind `trace.cmd`.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_air-trace`. Cargo sets it when it compiles the test,
//! and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::path::PathBuf;
use std::process::Command;

fn air_trace() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_air-trace")
        .or_else(|| option_env!("CARGO_BIN_EXE_air-trace").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_air-trace");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// Runs `air-trace` with `args`, as [`avl_testkit::answer`] runs a command.
fn answer(args: &[&str]) -> (Option<i32>, String, String) {
    avl_testkit::answer(Command::new(air_trace()).args(args))
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
        &["teamcity-publish", "--viewer", "out/air-trace-viewer"],
    ] {
        let (exit, _, stderr) = answer(usage);
        assert_eq!(exit, Some(2), "{usage:?}: {stderr}");
    }
}

/// A testlogs tree with one bundle, a checkout with the docs project and a `bazel.cmd` that writes its arguments into
/// `bazel-calls`, and an output directory, under one temporary directory.
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
/// step. Answers the exit and the standard error.
#[cfg(unix)]
fn publish_with(fixture: &std::path::Path, variables: &[(&str, &str)]) -> (Option<i32>, String) {
    let mut command = Command::new(air_trace());
    command
        .args(["teamcity-publish", "--testlogs"])
        .arg(fixture.join("testlogs"))
        .arg("--out")
        .arg(fixture.join("out"))
        .env("BUILD_WORKSPACE_DIRECTORY", fixture.join("checkout"));
    for name in [
        "TEAMCITY_VERSION",
        "TEAMCITY_BUILD_PROPERTIES_FILE",
        "BUILD_URL",
        "BUILD_WORKING_DIRECTORY",
        "AIR_TRACE_SITE",
        "AIR_TRACE_SINCE",
        "XML_OUTPUT_FILE",
    ] {
        command.env_remove(name);
    }
    command.envs(variables.iter().copied());
    let (exit, _, stderr) = avl_testkit::answer(&mut command);
    (exit, stderr)
}

/// The step builds no viewer and starts no Bazel, also in a checkout with the docs project and off TeamCity. The
/// archive holds `runs.json` and the zip of the bundle, and no viewer page.
#[cfg(unix)]
#[test]
fn teamcity_publish_builds_no_viewer_and_starts_no_bazel() {
    for variables in [&[][..], &[("TEAMCITY_VERSION", "2026.3 EAP (build 265562)")]] {
        let fixture = publish_fixture();
        let (exit, stderr) = publish_with(fixture.path(), variables);
        assert_eq!(exit, Some(0), "{stderr}");
        assert!(stderr.contains("1 bundle zip(s) from 1 test.xml"), "{stderr}");
        assert!(!stderr.contains("viewer"), "{stderr}");
        assert!(!fixture.path().join("bazel-calls").exists(), "no Bazel runs: {stderr}");
        let archive = std::fs::File::open(fixture.path().join("out/air-traces.zip")).unwrap();
        let archive = zip::ZipArchive::new(archive).unwrap();
        let names: Vec<&str> = archive.file_names().collect();
        assert_eq!(names.len(), 2, "{names:?}");
        assert_eq!(names[0], "runs.json", "{names:?}");
        assert!(names[1].ends_with(".zip"), "{names:?}");
    }
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
    let local = publish_fixture();
    let (exit, stderr) = publish_with(local.path(), &[]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        bundle(&local).join(avl_trace::bundle::MANIFEST_FILE).is_file(),
        "a local step keeps the testlogs: {stderr}"
    );
    assert!(!stderr.contains("removed "), "{stderr}");

    let asked = publish_fixture();
    let (exit, _, stderr) = avl_testkit::answer(
        Command::new(air_trace())
            .args(["teamcity-publish", "--remove-packed", "--testlogs"])
            .arg(asked.path().join("testlogs"))
            .arg("--out")
            .arg(asked.path().join("out"))
            .env_remove("TEAMCITY_VERSION")
            .env_remove("AIR_TRACE_SINCE")
            .env_remove("BUILD_WORKSPACE_DIRECTORY"),
    );
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(stderr.contains("removed 1 packed bundle(s) with "), "{stderr}");
    assert!(!bundle(&asked).exists(), "{stderr}");

    let on_team_city = publish_fixture();
    let (exit, stderr) = publish_with(on_team_city.path(), &[("TEAMCITY_VERSION", "2026.3")]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(stderr.contains("removed 1 packed bundle(s) with "), "{stderr}");
    assert!(!bundle(&on_team_city).exists(), "{stderr}");
}

/// On TeamCity the start of the build comes from `AIR_TRACE_SINCE`, and the step asks no server for it. A test.xml
/// before that start is skipped. Without the variable, one warning says so and the test publishes.
#[cfg(unix)]
#[test]
fn teamcity_publish_takes_the_start_of_the_build_from_air_trace_since() {
    let on_team_city = ("TEAMCITY_VERSION", "2026.3");

    let later = publish_fixture();
    let (exit, stderr) = publish_with(later.path(), &[on_team_city, ("AIR_TRACE_SINCE", "2999-01-01T00:00:00Z")]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert!(
        stderr.contains("a test.xml from 2999-01-01T00:00:00Z on counts, as AIR_TRACE_SINCE says"),
        "{stderr}"
    );
    assert!(
        stderr.contains("1 test(s) skipped, because their test.xml is older than "),
        "{stderr}"
    );
    assert!(stderr.contains("0 bundle zip(s) from 1 test.xml file(s)"), "{stderr}");

    let unset = publish_fixture();
    let (exit, stderr) = publish_with(unset.path(), &[on_team_city]);
    assert_eq!(exit, Some(0), "{stderr}");
    assert_eq!(
        stderr
            .matches("neither --since nor AIR_TRACE_SINCE names the start of the build")
            .count(),
        1,
        "{stderr}"
    );
    assert!(stderr.contains("1 bundle zip(s) from 1 test.xml file(s)"), "{stderr}");
}
