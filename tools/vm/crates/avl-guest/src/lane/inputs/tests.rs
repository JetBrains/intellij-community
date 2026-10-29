use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;
use crate::lane::testing::guest_launch_inputs;

fn write(path: &Path, content: &str, executable: bool) {
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, content).unwrap();
    if executable {
        fs::set_permissions(path, fs::Permissions::from_mode(0o755)).unwrap();
    }
}

// The home of the distribution is `home.path` of the IDE config, relative to the directory of the config, and its JBR
// is the production place below that home. A distribution without its JBR is refused.
#[test]
fn the_distribution_home_and_its_jbr_come_from_the_ide_config() {
    let root = TempDir::new().unwrap();
    let config = root.path().join("_main/ide/idea_air_lane_dist_ide_config.properties");
    write(&config, "main.class.name=com.intellij.idea.Main\nhome.path=../dist/./home\n", false);
    let home = distribution_home(&config).unwrap();
    assert_eq!(home, root.path().join("_main/dist/home"));
    let refusal = distribution_java_home(&home).unwrap_err();
    assert!(refusal.message.contains("has no JBR"), "{}", refusal.message);
    let java_home = if cfg!(target_os = "macos") {
        home.join("jbr/Contents/Home")
    } else {
        home.join("jbr")
    };
    write(&java_home.join("bin/java"), "", true);
    assert_eq!(distribution_java_home(&home).unwrap(), java_home);
    write(&config, "main.class.name=com.intellij.idea.Main\n", false);
    let refusal = distribution_home(&config).unwrap_err();
    assert!(refusal.message.starts_with("'home.path' is missing from"), "{}", refusal.message);
}

// A flag `-Dair.test.<name>.runtime` names the manifest of one CLI runtime, and the recorder flag beside it declares no
// runtime. The identity of a runtime hashes the manifest, the interpreter and the lock, in that order, and its command
// is the interpreter and the entry.
#[test]
fn a_runtime_flag_names_the_manifest_of_one_cli_runtime() {
    let root = TempDir::new().unwrap();
    let runfiles = root.path();
    write(&runfiles.join("_main/tools/node"), "node", true);
    write(&runfiles.join("_main/tools/pi/package/cli.js"), "pi", false);
    write(&runfiles.join("_main/tools/pi/pnpm-lock.yaml"), "lock-pi", false);
    let pi_manifest =
        "version=1.0\npackage=_main/tools/pi/package\nentry=cli.js\nlock=_main/tools/pi/pnpm-lock.yaml\nnode=_main/tools/node\n";
    write(&runfiles.join("_main/tools/pi/runtime.properties"), pi_manifest, false);
    write(&runfiles.join("_main/tools/codex/bin/codex"), "codex", true);
    write(&runfiles.join("_main/MODULE.bazel"), "lock-codex", false);
    let codex_manifest = "version=0.9\npackage=_main/tools/codex/bin\nentry=codex\nlock=_main/MODULE.bazel\n";
    write(&runfiles.join("_main/tools/codex/runtime.properties"), codex_manifest, false);
    let flags = [
        "-Dair.trace.recorder=${RUNFILES_ROOT}/community+/tools/vm/air-trace-record".to_owned(),
        "-Dair.test.pi.runtime=${RUNFILES_ROOT}/_main/tools/pi/runtime.properties".to_owned(),
        "-Dair.test.codex.runtime=${RUNFILES_ROOT}/_main/tools/codex/runtime.properties".to_owned(),
    ];
    let runtimes = declared_runtimes(&flags, runfiles).unwrap();
    let path = |relative: &str| runfiles.join(relative).display().to_string();
    assert_eq!(
        runtimes,
        [
            CliRuntime {
                name: "pi".to_owned(),
                command: vec![path("_main/tools/node"), path("_main/tools/pi/package/cli.js")],
                identity: sha256_hex(format!("{pi_manifest}nodelock-pi").as_bytes()),
                version: Some("1.0".to_owned()),
            },
            CliRuntime {
                name: "codex".to_owned(),
                command: vec![path("_main/tools/codex/bin/codex")],
                identity: sha256_hex(format!("{codex_manifest}lock-codex").as_bytes()),
                version: Some("0.9".to_owned()),
            },
        ]
    );
    let inputs = LaunchInputs {
        runtimes,
        ..guest_launch_inputs()
    };
    let named = inputs.runtimes_named(&["codex".to_owned()]).unwrap();
    assert_eq!(named.iter().map(|runtime| runtime.name.as_str()).collect::<Vec<_>>(), ["codex"]);
    let refusal = inputs.runtimes_named(&["claude".to_owned()]).unwrap_err();
    assert!(
        refusal.message.contains("has no flag -Dair.test.claude.runtime="),
        "{}",
        refusal.message
    );
}

// An entry that the manifest names and that is not there, or a native entry that cannot run, is refused by name.
#[test]
fn a_runtime_whose_entry_cannot_run_is_refused() {
    let root = TempDir::new().unwrap();
    let runfiles = root.path();
    write(&runfiles.join("_main/codex/bin/codex"), "codex", false);
    write(&runfiles.join("_main/codex/lock"), "lock", false);
    write(
        &runfiles.join("_main/codex/runtime.properties"),
        "version=1\npackage=_main/codex/bin\nentry=codex\nlock=_main/codex/lock\n",
        false,
    );
    let flags = ["-Dair.test.codex.runtime=${RUNFILES_ROOT}/_main/codex/runtime.properties".to_owned()];
    let refusal = declared_runtimes(&flags, runfiles).unwrap_err();
    assert!(refusal.message.contains("entry point of 'codex'"), "{}", refusal.message);
    write(&runfiles.join("_main/codex/runtime.properties"), "version=1\nentry=codex\n", false);
    let refusal = declared_runtimes(&flags, runfiles).unwrap_err();
    assert!(refusal.message.contains("has no 'package' input"), "{}", refusal.message);
}

// The lane flags name the recorder, and they let it read the screen and write the Allure results only when they say so.
// `-Dair.flow.trace=off` turns the trace off whatever else they name.
#[test]
fn the_trace_flags_name_the_recorder_the_screen_and_the_allure_results() {
    let runfiles = Path::new("/runfiles");
    let recorder = "-Dair.trace.recorder=${RUNFILES_ROOT}/community+/tools/vm/air-trace-record".to_owned();
    let plain = trace_inputs(std::slice::from_ref(&recorder), runfiles);
    assert_eq!(
        plain,
        TraceInputs {
            recorder: Some(runfiles.join("community+/tools/vm/air-trace-record")),
            ..TraceInputs::default()
        }
    );
    let asked = trace_inputs(&[recorder.clone(), SCREEN_FLAG.to_owned(), ALLURE_FLAG.to_owned()], runfiles);
    assert_eq!((asked.screen, asked.allure), (true, true));
    let off = trace_inputs(&[recorder, TRACE_OFF_FLAG.to_owned()], runfiles);
    assert_eq!(off.recorder, None);
}

// The descriptor of a Linux guest names the platform of the guest that reads it.
#[test]
fn the_guest_platform_is_a_platform_of_the_lane_descriptors() {
    assert!(
        ["darwin_aarch64", "darwin_x64", "linux_aarch64", "linux_x64"].contains(&guest_platform()),
        "{}",
        guest_platform()
    );
}
