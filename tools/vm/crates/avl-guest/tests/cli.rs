//! The process-level tests of `vm-guest-agent`, the binary that the controller runs inside a worker over an exec
//! channel.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_vm-guest-agent`. Cargo sets it when it compiles the
//! test, and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn agent() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_vm-guest-agent")
        .or_else(|| option_env!("CARGO_BIN_EXE_vm-guest-agent").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_vm-guest-agent");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// The agent as a command whose environment holds no shard variable and no timeout of the Bazel test that runs this file.
/// Bazel may split this test into shards, and `lane-run` refuses a run that Bazel splits. `lane-run` also takes the
/// timeout of a Bazel test as its budget, and the timeout of this test is not the budget of a lane.
fn agent_command() -> Command {
    let mut command = Command::new(agent());
    for name in ["TEST_TOTAL_SHARDS", "TEST_SHARD_INDEX", "TEST_SHARD_STATUS_FILE", "TEST_TIMEOUT"] {
        command.env_remove(name);
    }
    command
}

/// Runs the agent with `args`, as [`avl_testkit::answer`] runs a command.
fn answer(args: &[&str]) -> (Option<i32>, String, String) {
    avl_testkit::answer(agent_command().args(args))
}

/// A verb the agent does not have is the `usage` envelope on stderr with exit 64, which the host reads as "this
/// agent is older than its controller". Nothing goes to stdout, so the host never reads it as a reply.
#[test]
fn an_unknown_verb_is_the_usage_envelope_with_exit_64() {
    let (exit, stdout, stderr) = answer(&["no-such-verb"]);
    assert_eq!((exit, stdout.as_str()), (Some(64), ""), "{stderr}");
    let envelope: serde_json::Value = serde_json::from_str(&stderr).expect("one JSON envelope on stderr");
    assert_eq!(
        (&envelope["ok"], &envelope["command"], &envelope["error"]["code"]),
        (
            &serde_json::json!(false),
            &serde_json::json!("no-such-verb"),
            &serde_json::json!("usage")
        ),
        "{stderr}"
    );
}

/// `--help` is the usage on stdout with exit 0, and names the agent.
#[test]
fn help_is_the_usage_on_stdout_with_exit_0() {
    let (exit, stdout, stderr) = answer(&["--help"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    assert!(stdout.contains("vm-guest-agent"), "{stdout}");
}

/// `contract` answers one JSON document on stdout with exit 0: the wire of this binary and the digest of its bytes.
#[test]
fn contract_answers_one_document_on_stdout() {
    let (exit, stdout, stderr) = answer(&["contract"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    assert_eq!(stdout.lines().count(), 1, "{stdout}");
    let contract: serde_json::Value = serde_json::from_str(&stdout).expect("one JSON document");
    assert!(contract.is_object(), "{stdout}");
}

/// One HTTP/1.1 exchange with the control channel on `port`: the status and the body, read to the end of the
/// connection within `within`. A chunked body is decoded.
fn exchange(port: u16, token: &str, method: &str, target: &str, body: &[u8], within: std::time::Duration) -> (u16, Vec<u8>) {
    use std::io::{Read, Write};
    let mut stream = std::net::TcpStream::connect(("127.0.0.1", port)).unwrap();
    stream.set_read_timeout(Some(within)).unwrap();
    let head = format!(
        "{method} {target} HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Air-Daemon-Token: {token}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
        body.len()
    );
    stream.write_all(head.as_bytes()).unwrap();
    stream.write_all(body).unwrap();
    let mut answer = Vec::new();
    stream
        .read_to_end(&mut answer)
        .unwrap_or_else(|error| panic!("{method} {target} got no whole answer within {within:?}: {error}"));
    let end = answer.windows(4).position(|window| window == b"\r\n\r\n").expect("an answer head");
    let head = String::from_utf8_lossy(&answer[..end]).into_owned();
    let status = head.split(' ').nth(1).and_then(|status| status.parse().ok()).expect("a status");
    let mut rest = &answer[end + 4..];
    if !head.to_ascii_lowercase().contains("transfer-encoding: chunked") {
        return (status, rest.to_vec());
    }
    let mut body = Vec::new();
    loop {
        let line_end = rest.windows(2).position(|pair| pair == b"\r\n").expect("a chunk size");
        let size = usize::from_str_radix(std::str::from_utf8(&rest[..line_end]).unwrap(), 16).unwrap();
        rest = &rest[line_end + 2..];
        if size == 0 {
            return (status, body);
        }
        body.extend_from_slice(&rest[..size]);
        rest = &rest[size + 2..];
    }
}

/// The lowercase hex SHA-256 of `bytes`.
fn sha256(bytes: &[u8]) -> String {
    use sha2::Digest as _;
    hex::encode(sha2::Sha256::digest(bytes))
}

/// Writes `content` to `path`, with the mode 0755 when `executable`.
fn put(path: &std::path::Path, content: &[u8], executable: bool) {
    use std::os::unix::fs::PermissionsExt;
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(path, content).unwrap();
    if executable {
        std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755)).unwrap();
    }
}

/// The JBR platform of the descriptor that this binary reads, as `lane::inputs::guest_platform` names it.
fn guest_platform() -> &'static str {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("macos", "aarch64") => "darwin_aarch64",
        ("macos", _) => "darwin_x64",
        (_, "aarch64") => "linux_aarch64",
        _ => "linux_x64",
    }
}

/// A lane daemon process, killed when the test ends early.
struct Daemon(std::process::Child);

impl Drop for Daemon {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}

/// The real binary serves a run of a lane end to end: the run thread writes its log lines on standard error beside the
/// main thread, and the stream ends with a summary. The project archive is missing, so the IDE cannot launch, and the
/// run fails at once. Then `/shutdown` ends the process with exit 0.
///
/// The test runs the binary, because the stream setup of `main` decides whether a thread other than the main one can
/// write on standard error. A lock of standard error for the whole run blocked the run thread at its first log line,
/// and the run hung with no record.
/// The runfiles tree of a lane under `root`: a distribution config, a JBR whose `java` exits 1, the IDE flags, the two
/// declared CLI runtimes and the runtime descriptor. The lane inputs are files of the tree, which a test writes itself.
/// Answers the runfiles root and the descriptor.
fn lane_runfiles(root: &std::path::Path) -> (PathBuf, PathBuf) {
    let runfiles = root.join("runfiles");
    let main = runfiles.join("_main");
    put(
        &main.join("dist/ide.properties"),
        b"main.class.name=com.intellij.idea.Main\nhome.path=home\n",
        false,
    );
    let java_home = if cfg!(target_os = "macos") {
        "dist/home/jbr/Contents/Home"
    } else {
        "dist/home/jbr"
    };
    put(&main.join(java_home).join("bin/java"), b"#!/bin/sh\nexit 1\n", true);
    put(&main.join("ide/flags.txt"), b"-Xmx1g\n", false);
    for name in ["codex", "pi"] {
        put(&main.join(format!("tools/{name}/bin/{name}")), b"#!/bin/sh\n", true);
        put(&main.join(format!("tools/{name}/lock")), name.as_bytes(), false);
        let manifest = format!("version=1\npackage=_main/tools/{name}/bin\nentry={name}\nlock=_main/tools/{name}/lock\n");
        put(&main.join(format!("tools/{name}/runtime.properties")), manifest.as_bytes(), false);
    }
    let file =
        |logical: &str| serde_json::json!({"execPath": format!("bazel-out/bin/{logical}"), "logicalPath": logical, "owner": "//test"});
    let descriptor = serde_json::json!({
        "schemaVersion": avl_wire::runtime::SCHEMA_VERSION,
        "kind": "air-ui-daemon-runtime",
        "staticJvmFlags": [
            "-Dair.test.codex.runtime=${RUNFILES_ROOT}/_main/tools/codex/runtime.properties",
            "-Dair.test.pi.runtime=${RUNFILES_ROOT}/_main/tools/pi/runtime.properties",
        ],
        "devDist": {"config": file("_main/dist/ide.properties"), "fingerprint": file("_main/dist/fingerprint.txt"), "home": file("_main/dist/home")},
        "jbr": {"platform": guest_platform()},
        "ide": {
            "fakeAgentJar": file("_main/agent.jar"),
            "flagsFile": file("_main/ide/flags.txt"),
            "laneDocuments": file("_main/documents.jar"),
            "plugins": [],
            "projectArchive": file("_main/missing-project.zip"),
            "projectRoot": "plain-project",
            "suiteDocuments": file("_main/documents.jar"),
        },
        "data": [],
    });
    let descriptor_path = main.join("ui_daemon.runtime.json");
    put(&descriptor_path, descriptor.to_string().as_bytes(), false);
    (runfiles, descriptor_path)
}

/// The documents jar of a lane: the smallest lane document of `UI` that the daemon launches as it states, with no agent,
/// the two CLI runtimes of the descriptor and the two services that the daemon serves, and each suite document of
/// `suites` under `flow-profiles/<name>.json`.
fn documents_jar(suites: &[(&str, serde_json::Value)]) -> Vec<u8> {
    use std::io::Write as _;
    let lane = serde_json::json!({
        "schemaVersion": 2, "lane": "UI", "launchProperties": [], "requirements": [],
        "agents": [], "registryAgents": [], "disabledPluginIds": [], "cliRuntimes": ["codex", "pi"],
        "services": {"codexModel": "mock-responses port=31500 reply=Air UI lane Codex reply", "jetBrainsCloud": "jba-jcp-emulator-1"},
    });
    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    let options = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);
    writer.start_file("lanes/ui.json", options).unwrap();
    writer.write_all(lane.to_string().as_bytes()).unwrap();
    writer.start_file(avl_wire::program::FLOW_VOCABULARY_FILE, options).unwrap();
    writer.write_all(b"{\"actions\":{\n}}\n").unwrap();
    for (name, suite) in suites {
        writer.start_file(format!("flow-profiles/{name}.json"), options).unwrap();
        writer.write_all(suite.to_string().as_bytes()).unwrap();
    }
    writer.finish().unwrap().into_inner()
}

/// The suite document `rename-session` of the lane `UI`, with no scenario.
fn rename_session() -> serde_json::Value {
    serde_json::json!({
        "schemaVersion": 2, "suite": "rename-session", "lane": "UI", "testClassName": "AirRenameSessionGeneratedFlowUiTest",
        "restartsIde": false, "labels": [], "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {}, "assertions": {}}, "profiles": [],
    })
}

#[test]
fn the_lane_daemon_serves_a_run_that_logs_from_its_run_thread_and_ends_with_a_summary() {
    use std::time::{Duration, Instant};
    let root = tempfile::tempdir().unwrap();
    let (runfiles, descriptor_path) = lane_runfiles(root.path());

    // Below the ephemeral floor, so no parallel bind of port 0 takes it before the daemon binds it.
    let port = avl_testkit::free_port_below_ephemeral_floor();
    let state_dir = root.path().join("state");
    let log = root.path().join("run.log");
    let path = |path: &std::path::Path| path.display().to_string();
    let mut daemon = Daemon(
        agent_command()
            .args(["lane-daemon", "--state-dir", &path(&state_dir), "--port", &port.to_string()])
            .args(["--launch-digest", "launch-1", "--descriptor", &path(&descriptor_path)])
            .args(["--runfiles-root", &path(&runfiles), "--ide-root", &path(&root.path().join("ide"))])
            .args(["--home", &path(&root.path().join("home"))])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(std::fs::File::create(&log).unwrap())
            .spawn()
            .unwrap(),
    );
    let state_file = state_dir.join("daemon.json");
    let deadline = Instant::now() + Duration::from_secs(30);
    while !state_file.is_file() {
        assert!(
            daemon.0.try_wait().unwrap().is_none(),
            "the daemon exited: {}",
            std::fs::read_to_string(&log).unwrap()
        );
        assert!(Instant::now() < deadline, "no state file within 30 s");
        std::thread::sleep(Duration::from_millis(50));
    }
    let state: serde_json::Value = serde_json::from_slice(&std::fs::read(&state_file).unwrap()).unwrap();
    let token = state["token"].as_str().unwrap().to_owned();
    let call = |method: &str, target: &str, body: &[u8]| exchange(port, &token, method, target, body, Duration::from_secs(60));

    let documents = documents_jar(&[("rename-session", rename_session())]);
    let agent_jar = b"agent jar".to_vec();
    let (bridge, resident) = (b"bridge".to_vec(), b"resident".to_vec());
    for content in [&documents, &agent_jar, &bridge, &resident] {
        assert_eq!(call("PUT", &format!("/jars/{}", sha256(content)), content).0, 200);
    }
    let request = serde_json::json!({
        "selectors": [], "junit5Filters": [], "productStamp": "product-1", "launchDigest": "launch-1",
        "activeExecutionTimeoutSec": 1800, "progressGapTimeoutSec": 300,
        "idePlugins": [
            {"destination": "air-integrationTests-bridge/lib/b.jar", "sha256": sha256(&bridge)},
            {"destination": "air-integrationTests-bridge-resident/lib/r.jar", "sha256": sha256(&resident)},
        ],
        "lane": "ui",
        "laneInputs": {"fakeAgentJar": sha256(&agent_jar), "laneDocuments": sha256(&documents), "suiteDocuments": sha256(&documents)},
    });
    let (status, stream) = call("POST", "/run", request.to_string().as_bytes());
    let stream = String::from_utf8(stream).unwrap();
    let events: Vec<String> = stream
        .lines()
        .map(|line| {
            serde_json::from_str::<serde_json::Value>(line).unwrap()["event"]
                .as_str()
                .unwrap()
                .to_owned()
        })
        .collect();
    assert_eq!(status, 200);
    assert_eq!(events.first().map(String::as_str), Some("runStarted"), "{stream}");
    assert_eq!(events.last().map(String::as_str), Some("summary"), "{stream}");
    assert!(events.iter().any(|event| event == "runFailed"), "{stream}");
    let written = std::fs::read_to_string(&log).unwrap();
    assert!(
        written.contains("lane ui: 1 suite document"),
        "the run thread wrote no log line:\n{written}"
    );

    assert_eq!(call("POST", "/shutdown", b"").0, 200);
    let deadline = Instant::now() + Duration::from_secs(30);
    let exit = loop {
        if let Some(exit) = daemon.0.try_wait().unwrap() {
            break exit;
        }
        assert!(Instant::now() < deadline, "the daemon did not exit after /shutdown");
        std::thread::sleep(Duration::from_millis(50));
    };
    assert_eq!(exit.code(), Some(0), "{}", std::fs::read_to_string(&log).unwrap());
}

/// `lane-run --plan-only` proves a lane rule and its runfiles with no lane IDE. It stages the inputs of the runfiles,
/// selects the documents, and writes the plan: the key of the lane IDE and the suites in run order, the suite of a bare
/// IDE last. It launches no IDE, so no context of a launch key appears under the run directory. A selection of bare
/// suites alone needs no lane IDE, so its plan names no lane key. The JVM flags of a TeamCity UI job split the lane
/// between its runners, and an unknown flag is refused.
#[test]
fn lane_run_plan_only_writes_the_plan_and_launches_no_ide() {
    let root = tempfile::tempdir().unwrap();
    let (runfiles, descriptor) = lane_runfiles(root.path());
    let main = runfiles.join("_main");
    let startup = serde_json::json!({
        "schemaVersion": 2, "suite": "startup-class-loading", "lane": "UI", "testClassName": "AirStartupClassLoadingBudgetUiTest",
        "authored": true, "launch": {"profile": "bare", "key": "air-startup-class-loading"},
        "restartsIde": false, "labels": [], "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {}, "assertions": {}},
        "profiles": [{"name": "within budget", "fixture": "bare", "flags": {}, "setups": [], "steps": [], "program": []}],
    });
    let jar = documents_jar(&[("startup-class-loading", startup), ("rename-session", rename_session())]);
    put(&main.join("documents.jar"), &jar, false);
    put(&main.join("agent.jar"), b"agent jar", false);
    put(&main.join("dist/fingerprint.txt"), b"product-1", false);
    let run_root = root.path().join("run");
    let path = |path: &std::path::Path| path.display().to_string();
    let plan = |extra: &[&str]| {
        let mut args = vec!["lane-run", "--lane", "ui", "--plan-only"];
        let (descriptor, runfiles, run_root) = (path(&descriptor), path(&runfiles), path(&run_root));
        args.extend([
            "--descriptor",
            &descriptor,
            "--runfiles-root",
            &runfiles,
            "--runtime-root",
            &run_root,
        ]);
        args.extend(extra);
        let (code, stdout, stderr) = answer(&args);
        assert_eq!(code, Some(0), "{stderr}");
        serde_json::from_str::<serde_json::Value>(&stdout).unwrap_or_else(|error| panic!("{error}: {stdout}"))
    };
    let whole = plan(&[]);
    assert_eq!(whole["lane"], "ui");
    assert!(whole["laneKey"].as_str().unwrap().starts_with("air-ui-"), "{whole}");
    assert_eq!(whole["productStamp"], sha256(b"product-1"));
    assert_eq!(whole["laneInputs"]["suiteDocuments"], sha256(&jar));
    assert!(whole["cliRuntimes"].is_array(), "the plan names the declared CLI runtimes: {whole}");
    let suites: Vec<(&str, Option<&str>)> = whole["suites"]
        .as_array()
        .unwrap()
        .iter()
        .map(|suite| (suite["testClassName"].as_str().unwrap(), suite["bareLaunchKey"].as_str()))
        .collect();
    assert_eq!(
        suites,
        [
            ("AirRenameSessionGeneratedFlowUiTest", None),
            ("AirStartupClassLoadingBudgetUiTest", Some("air-startup-class-loading")),
        ]
    );
    let contexts: Vec<_> = std::fs::read_dir(run_root.join("ide")).unwrap().collect();
    assert!(contexts.is_empty(), "the plan launched an IDE: {contexts:?}");
    let bare_only = plan(&["--selector", "AirStartupClassLoadingBudgetUiTest"]);
    assert_eq!(bare_only["laneKey"], serde_json::Value::Null, "{bare_only}");
    assert_eq!(bare_only["suites"].as_array().unwrap().len(), 1);

    // The argv of the UI job of TeamCity build 1097187017: each of its two runners plans its own share of the lane.
    let runner = |index: &str| {
        let index = format!("--jvm_flag=-Didea.test.runner.index={index}");
        let shared = plan(&[
            "--jvm_flag=-Djava.awt.headless=false",
            &index,
            "--jvm_flag=-Didea.test.runners.count=2",
            "--jvm_flag=-Didea.bucketing.season=AirPluginGuiChatTests",
            "--jvm_flag=-Didea.tests.duration.bucketing.enabled=true",
        ]);
        let classes: Vec<String> = shared["suites"]
            .as_array()
            .unwrap()
            .iter()
            .map(|suite| suite["testClassName"].as_str().unwrap().to_owned())
            .collect();
        classes
    };
    assert_eq!(runner("0"), ["AirRenameSessionGeneratedFlowUiTest"]);
    assert_eq!(runner("1"), ["AirStartupClassLoadingBudgetUiTest"]);
    let mut args = vec!["lane-run", "--lane", "ui", "--plan-only", "--jvm_flag", "-Dunknown.property=1"];
    let descriptor = path(&descriptor);
    args.extend(["--descriptor", &descriptor]);
    let (code, stdout, stderr) = answer(&args);
    assert_ne!(code, Some(0), "{stdout}{stderr}");
    assert!(
        stderr.contains("lane_run_jvm_flag") && stderr.contains("unknown.property"),
        "{stdout}{stderr}"
    );
}

/// `lane-run` writes its run stream on standard output from the run thread and from the watchdog thread. The agent held
/// the lock of standard output for its whole run, so the first record of the watchdog thread blocked inside the stream,
/// with the lock of the stream held, and the run thread blocked at its next record. On TeamCity each lane then hung until
/// the hard stop of its budget. Here a bare IDE that never answers its bridge holds the run thread, and the watchdog ends
/// the run after a progress gap of 1 s, so the watchdog thread writes `watchdogExpired`. The kill of the watchdog ends
/// the run of the IDE, and the readiness wait sees that end, so the run ends with exit 1 well before the 600 s of the
/// gate.
///
/// The test runs the binary, because the stream setup of `main` decides whether a thread other than the main one can
/// write on standard output.
#[test]
fn lane_run_writes_the_record_of_its_watchdog_thread_on_standard_output() {
    use std::time::{Duration, Instant};
    let root = tempfile::tempdir().unwrap();
    let (runfiles, descriptor) = lane_runfiles(root.path());
    let main = runfiles.join("_main");
    let java_home = if cfg!(target_os = "macos") {
        "dist/home/jbr/Contents/Home"
    } else {
        "dist/home/jbr"
    };
    // An IDE that runs and never answers its bridge, in a distribution that the launch reads.
    put(&main.join(java_home).join("bin/java"), b"#!/bin/sh\nexec sleep 120\n", true);
    let (product_info, vm_options) = if cfg!(target_os = "macos") {
        ("dist/home/Resources/product-info.json", "../bin/idea64.vmoptions")
    } else {
        ("dist/home/product-info.json", "bin/idea64.vmoptions")
    };
    let launch = serde_json::json!({"launch": [{"vmOptionsFilePath": vm_options}]});
    put(&main.join(product_info), launch.to_string().as_bytes(), false);
    put(&main.join("dist/home/bin/idea64.vmoptions"), b"-Xmx1g\n", false);
    put(&main.join("dist/home/bin/idea.properties"), b"", false);
    put(&main.join("dist/home/core-classpath.txt"), b"lib/a.jar\n", false);
    let file =
        |logical: &str| serde_json::json!({"execPath": format!("bazel-out/bin/{logical}"), "logicalPath": logical, "owner": "//test"});
    let mut document: serde_json::Value = serde_json::from_slice(&std::fs::read(&descriptor).unwrap()).unwrap();
    document["ide"]["plugins"] = serde_json::json!([
        {"destination": "air-integrationTests-bridge/lib/b.jar", "file": file("_main/bridge.jar")},
        {"destination": "air-integrationTests-bridge-resident/lib/r.jar", "file": file("_main/resident.jar")},
    ]);
    document["ide"]["projectArchive"] = file("_main/project.zip");
    put(&descriptor, document.to_string().as_bytes(), false);
    put(&main.join("bridge.jar"), b"bridge", false);
    put(&main.join("resident.jar"), b"resident", false);
    put(&main.join("project.zip"), &project_zip(), false);
    let startup = serde_json::json!({
        "schemaVersion": 2, "suite": "startup-class-loading", "lane": "UI", "testClassName": "AirStartupClassLoadingBudgetUiTest",
        "authored": true, "launch": {"profile": "bare", "key": "air-startup-class-loading"},
        "restartsIde": false, "labels": [{"name": "testClass", "value": "air.AirStartupClassLoadingBudgetUiTest"}],
        "modules": [], "implementationFlows": [], "vocabulary": {"runnerAnswers": [], "actions": {}, "assertions": {}},
        "profiles": [{"name": "within budget", "fixture": "bare", "flags": {}, "setups": [], "steps": [], "program": []}],
    });
    put(
        &main.join("documents.jar"),
        &documents_jar(&[("startup-class-loading", startup)]),
        false,
    );
    put(&main.join("agent.jar"), b"agent jar", false);
    put(&main.join("dist/fingerprint.txt"), b"product-1", false);

    let (stdout_path, stderr_path) = (root.path().join("stdout.txt"), root.path().join("stderr.txt"));
    let path = |path: &std::path::Path| path.display().to_string();
    let mut command = agent_command();
    command
        .args(["lane-run", "--lane", "ui", "--progress-gap-timeout-sec", "1"])
        .args(["--descriptor", &path(&descriptor), "--runfiles-root", &path(&runfiles)])
        .args(["--runtime-root", &path(&root.path().join("run"))])
        // The JUnit XML and the hang report of this run are not the files of the Bazel test that runs this file.
        .env_remove("XML_OUTPUT_FILE")
        .env_remove("TEST_UNDECLARED_OUTPUTS_DIR")
        .stdin(Stdio::null())
        .stdout(std::fs::File::create(&stdout_path).unwrap())
        .stderr(std::fs::File::create(&stderr_path).unwrap());
    if cfg!(target_os = "linux") {
        // A worker of the controller: no probe of the host, and a display for the launch.
        command
            .env(avl_wire::daemon::RUN_SECRETS_VARIABLE, root.path().join("secrets"))
            .env("DISPLAY", ":0");
    }
    // Dropped at the end, which kills a run that did not end.
    let mut run = Daemon(command.spawn().unwrap());
    let read = |path: &std::path::Path| std::fs::read_to_string(path).unwrap_or_default();
    let deadline = Instant::now() + Duration::from_secs(60);
    while !read(&stdout_path).contains(r#""event":"watchdogExpired""#) {
        assert!(
            run.0.try_wait().unwrap().is_none(),
            "lane-run ended before its watchdog wrote\nstdout:\n{}\nstderr:\n{}",
            read(&stdout_path),
            read(&stderr_path)
        );
        assert!(
            Instant::now() < deadline,
            "the watchdog thread wrote no record on standard output within 60 s\nstdout:\n{}\nstderr:\n{}",
            read(&stdout_path),
            read(&stderr_path)
        );
        std::thread::sleep(Duration::from_millis(100));
    }
    let exit = loop {
        if let Some(exit) = run.0.try_wait().unwrap() {
            break exit;
        }
        assert!(
            Instant::now() < deadline,
            "lane-run did not end after its watchdog killed the IDE\nstdout:\n{}\nstderr:\n{}",
            read(&stdout_path),
            read(&stderr_path)
        );
        std::thread::sleep(Duration::from_millis(100));
    };
    let stdout = read(&stdout_path);
    assert_eq!(exit.code(), Some(1), "stdout:\n{stdout}\nstderr:\n{}", read(&stderr_path));
    assert!(
        stdout.contains("the process of the IDE ended"),
        "the bare IDE did not fail on the end of its run\nstdout:\n{stdout}"
    );
}

/// The archive of the plain project of a lane: one directory and one file in it.
fn project_zip() -> Vec<u8> {
    use std::io::Write as _;
    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    let options = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);
    writer.add_directory("plain-project/", options).unwrap();
    writer.start_file("plain-project/README.md", options).unwrap();
    writer.write_all(b"plain project\n").unwrap();
    writer.finish().unwrap().into_inner()
}
