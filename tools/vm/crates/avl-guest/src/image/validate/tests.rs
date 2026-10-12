//! The coverage the shell version could not have.
//!
//! `validate-guest.sh` was a `[[ ... ]]` cascade under `set -euo pipefail`: a failing check produced a nonzero exit
//! from an unnamed line and no message, and reaching one needed a macOS guest and a 3-minute Packer run. Every
//! refusal below is reached in milliseconds, on any host, against a stubbed image.

use avl_wire::verb::AgentVerb;
use std::collections::HashMap;
use std::fs;
use std::os::unix::fs::PermissionsExt;

use avl_wire::supervisor::AgentExit;
use pretty_assertions::assert_eq;

use super::*;
use crate::reply::verb_refusal_code;
use crate::testing::run_agent;

// --- the verdicts ----------------------------------------------------------------------------------------------

// Each verdict passes the pinned answer, and refuses any other answer with its own code and the answer quoted.
#[test]
fn each_verdict_names_what_the_guest_answered() {
    fn code_of(refusal: &AgentRefusal) -> &str {
        &refusal.code
    }
    macos_verdict("15.6", "15.6").unwrap();
    assert_eq!(code_of(&macos_verdict("15.6", "15.5").unwrap_err()), "image_macos_version_mismatch");
    architecture_verdict("arm64").unwrap();
    assert_eq!(code_of(&architecture_verdict("x86_64").unwrap_err()), "image_architecture_mismatch");
    node_verdict(24, "/opt/node", "v24.10.0").unwrap();
    assert_eq!(
        code_of(&node_verdict(24, "/opt/node", "v240.1.0").unwrap_err()),
        "image_node_version_mismatch"
    );
    junie_verdict("1.2.3", "/bin/junie", "junie 1.2.3").unwrap();
    assert_eq!(
        code_of(&junie_verdict("1.2.3", "/bin/junie", "junie 1.2.4").unwrap_err()),
        "image_agent_version_mismatch"
    );
    autologin_verdict(IMAGE_ACCOUNT).unwrap();
    assert_eq!(code_of(&autologin_verdict("").unwrap_err()), "image_autologin_not_configured");
    screen_lock_verdict("screenLock delay is off").unwrap();
    assert_eq!(code_of(&screen_lock_verdict("immediate").unwrap_err()), "image_screen_lock_enabled");
    audit_verdict(None, "clean").unwrap();
    assert_eq!(
        code_of(&audit_verdict(Some("exit 1"), "a finding").unwrap_err()),
        "image_audit_refused"
    );
}

/// A whole macOS image: a temporary directory standing in for the guest's filesystem, and a table of replies keyed
/// by how a command reads (the tool's base name and its arguments). An unstubbed command panics: an error reply
/// would surface as `image_tool_not_runnable` and let a case pass while testing something else.
struct StubbedImage {
    root: PathBuf,
    stat: Stat,
    replies: HashMap<String, Answer>,
    calls: Vec<String>,
}

impl ImageSurface for StubbedImage {
    fn stat(&self) -> &Stat {
        &self.stat
    }

    fn run(&mut self, argv: &[&str]) -> Answer {
        let program = Path::new(argv[0]).file_name().unwrap().to_string_lossy();
        let label = std::iter::once(program.as_ref())
            .chain(argv[1..].iter().copied())
            .collect::<Vec<_>>()
            .join(" ");
        self.calls.push(label.clone());
        self.replies
            .get(&label)
            .cloned()
            .unwrap_or_else(|| panic!("the image was asked to run {label:?}, which no stub answers"))
    }
}

/// The audit is invoked through `sudo`, so its label carries `sudo` as the tool and the script as an argument.
fn audit_call() -> String {
    format!(
        "sudo {} --allow-ssh-host-keys --allow-sensitive-tcc",
        installed_worker_script(AUDIT_SCRIPT_NAME)
    )
}

fn pins() -> ImagePins {
    ImagePins {
        macos_version: "26.6.2".to_owned(),
        node_major: 24,
        junie_version: "2777.8".to_owned(),
    }
}

struct Fixture {
    _root: tempfile::TempDir,
    validator: ImageValidator<StubbedImage>,
}

impl Fixture {
    /// An image every check passes on, answering what a real golden image answers. Every case is this, minus one
    /// thing.
    fn healthy() -> Self {
        let root = tempfile::tempdir().unwrap();
        let rerooted = root.path().to_path_buf();
        let image = StubbedImage {
            root: rerooted.clone(),
            stat: Box::new(move |path| fs::metadata(rerooted.join(path.strip_prefix("/").unwrap_or(path)))),
            replies: HashMap::new(),
            calls: Vec::new(),
        };
        let mut fixture = Self {
            _root: root,
            validator: ImageValidator::new(&pins(), image),
        };
        fixture.write_executable(&format!("{}/node", node_bin_directory(24)));
        for tool in ["git", "npm", "tart-guest-agent"] {
            fixture.write_executable(&format!("/opt/homebrew/bin/{tool}"));
        }
        // Junie installs its own shim, which is why the search path carries a directory the system path does not.
        fixture.write_executable(&format!("{}/junie", local_bin_directory(Path::new(IMAGE_HOME))));
        fixture.write_file(TART_GUEST_DAEMON_PLIST, 0o644, "<plist/>\n");
        fixture.write_file(TART_GUEST_AGENT_PLIST, 0o644, "<plist/>\n");
        fixture.write_file(KCPASSWORD_PATH, 0o600, "encoded-admin-password");
        for script in &WORKER_SCRIPTS {
            fixture.write_executable(&installed_worker_script(script.installed));
        }
        for (label, output) in [
            ("sw_vers -productVersion", "26.6.2\n"),
            ("uname -m", "arm64\n"),
            ("node --version", "v24.8.0\n"),
            ("git --version", "git version 2.51.0\n"),
            ("npm --version", "11.6.0\n"),
            ("junie --version", "Junie 26.8.17 (2777.8)\n"),
            ("defaults read /Library/Preferences/com.apple.loginwindow autoLoginUser", "admin\n"),
            ("sysadminctl -screenLock status", "screenLock is off.\n"),
            (
                "pmset -g custom",
                "AC Power:\n lidwake              1\n autopoweroff         0\n standby              0\n \
                 powernap             0\n displaysleep         0\n sleep                0\n disksleep            0\n",
            ),
        ] {
            fixture.reply(label, output);
        }
        fixture.reply(&audit_call(), "public image audit passed\n");
        fixture
    }

    fn image(&mut self) -> &mut StubbedImage {
        &mut self.validator.surface
    }

    fn rooted(&self, guest_path: &str) -> PathBuf {
        self.validator.surface.root.join(guest_path.trim_start_matches('/'))
    }

    fn write_file(&self, guest_path: &str, mode: u32, content: &str) {
        let path = self.rooted(guest_path);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, content).unwrap();
        fs::set_permissions(&path, fs::Permissions::from_mode(mode)).unwrap();
    }

    fn write_executable(&self, guest_path: &str) {
        self.write_file(guest_path, 0o755, "#!/bin/sh\n");
    }

    fn remove(&self, guest_path: &str) {
        let path = self.rooted(guest_path);
        if path.is_dir() {
            fs::remove_dir_all(path).unwrap();
        } else {
            fs::remove_file(path).unwrap();
        }
    }

    fn reply(&mut self, label: &str, output: &str) {
        self.image().replies.insert(
            label.to_owned(),
            Answer {
                output: output.to_owned(),
                failure: None,
            },
        );
    }

    fn fail_with(&mut self, label: &str, output: &str, failure: &str) {
        self.image().replies.insert(
            label.to_owned(),
            Answer {
                output: output.to_owned(),
                failure: Some(failure.to_owned()),
            },
        );
    }

    fn validate(&mut self) -> Result<ImageReport, AgentRefusal> {
        self.validator.validate()
    }
}

fn junie_shim() -> String {
    format!("{}/junie", local_bin_directory(Path::new(IMAGE_HOME)))
}

// --- the happy path -------------------------------------------------------------------------------------------

#[test]
fn a_healthy_image_is_validated_and_reports_what_it_found() {
    let mut fixture = Fixture::healthy();
    let report = fixture.validate().unwrap();
    assert_eq!(
        serde_json::to_value(&report).unwrap(),
        serde_json::json!({
            "macOS": "26.6.2",
            "architecture": "arm64",
            "node": "v24.8.0",
            "junie": "Junie 26.8.17 (2777.8)",
            "tartGuestAgent": "/opt/homebrew/bin/tart-guest-agent",
            "audit": "public image audit passed",
        })
    );
}

/// The audit's two flags are a security decision, so the argv is pinned: exactly them, once.
#[test]
fn the_public_image_audit_is_invoked_with_exactly_its_two_allow_flags() {
    let mut fixture = Fixture::healthy();
    fixture.validate().unwrap();
    let audits: Vec<&String> = fixture
        .validator
        .surface
        .calls
        .iter()
        .filter(|call| call.contains(AUDIT_SCRIPT_NAME))
        .collect();
    assert_eq!(audits, [&audit_call()]);
}

/// Every script `provision-image` installs is one this verb checks and can say what it is for.
#[test]
fn every_installed_worker_script_is_described() {
    for script in &WORKER_SCRIPTS {
        assert!(!script.purpose.is_empty(), "{} has no purpose", script.installed);
    }
    assert!(WORKER_SCRIPTS.iter().any(|script| script.installed == AUDIT_SCRIPT_NAME));
}

/// The audit is last, after everything local: an image refused for its identity does not spend the audit's sweep.
#[test]
fn the_audit_runs_only_after_every_local_check_passed() {
    let mut fixture = Fixture::healthy();
    fixture.reply("uname -m", "x86_64\n");
    fixture.validate().unwrap_err();
    assert!(
        !fixture.validator.surface.calls.iter().any(|call| call.contains(AUDIT_SCRIPT_NAME)),
        "{:?}",
        fixture.validator.surface.calls
    );
}

// --- every refusal path ---------------------------------------------------------------------------------------

#[test]
fn every_check_refuses_with_its_own_code() {
    type Damage = fn(&mut Fixture);
    let cases: [(&str, Damage, &str); 31] = [
        (
            "the base image moved to another macOS",
            |f| f.reply("sw_vers -productVersion", "26.7.0\n"),
            "image_macos_version_mismatch",
        ),
        (
            "sw_vers does not run",
            |f| f.fail_with("sw_vers -productVersion", "", "exit status: 127"),
            "image_tool_not_runnable",
        ),
        (
            "the guest is not Apple silicon",
            |f| f.reply("uname -m", "x86_64\n"),
            "image_architecture_mismatch",
        ),
        (
            "the base stopped shipping node",
            |f| f.remove(&format!("{}/node", node_bin_directory(24))),
            "image_base_tool_missing",
        ),
        (
            "the base stopped shipping git",
            |f| f.remove("/opt/homebrew/bin/git"),
            "image_base_tool_missing",
        ),
        (
            "the Tart guest agent is absent",
            |f| f.remove("/opt/homebrew/bin/tart-guest-agent"),
            "image_base_tool_missing",
        ),
        (
            "a base tool is on the search path but not executable",
            |f| f.write_file("/opt/homebrew/bin/npm", 0o644, "#!/bin/sh\n"),
            "image_base_tool_missing",
        ),
        (
            "node is another major",
            |f| f.reply("node --version", "v22.19.0\n"),
            "image_node_version_mismatch",
        ),
        (
            "npm is a symlink into a keg that was pruned",
            |f| f.fail_with("npm --version", "", "exit status: 127"),
            "image_tool_not_runnable",
        ),
        (
            "the guest daemon plist is absent",
            |f| f.remove(TART_GUEST_DAEMON_PLIST),
            "image_guest_agent_plist_missing",
        ),
        (
            "the guest agent plist is absent",
            |f| f.remove(TART_GUEST_AGENT_PLIST),
            "image_guest_agent_plist_missing",
        ),
        (
            "a plist path is a directory rather than a file",
            |f| {
                f.remove(TART_GUEST_AGENT_PLIST);
                fs::create_dir_all(f.rooted(TART_GUEST_AGENT_PLIST)).unwrap();
            },
            "image_guest_agent_plist_missing",
        ),
        (
            "the Junie shim was never installed",
            |f| f.remove(&junie_shim()),
            "image_agent_missing",
        ),
        (
            "the Junie shim self-updated past its pin",
            |f| f.reply("junie --version", "Junie 26.9.1 (2801.4)\n"),
            "image_agent_version_mismatch",
        ),
        // The one the shell version accepted: a pin that is a prefix of a longer version.
        (
            "the Junie shim answers a longer version the pin is a prefix of",
            |f| f.reply("junie --version", "Junie 26.8.17 (2777.81)\n"),
            "image_agent_version_mismatch",
        ),
        (
            "an agent CLI is installed but does not run",
            |f| f.fail_with("junie --version", "", "exit status: 1"),
            "image_tool_not_runnable",
        ),
        (
            "autologin names another user",
            |f| {
                f.reply("defaults read /Library/Preferences/com.apple.loginwindow autoLoginUser", "test\n");
            },
            "image_autologin_not_configured",
        ),
        (
            "autologin is configured in the plist alone",
            |f| f.remove(KCPASSWORD_PATH),
            "image_autologin_password_missing",
        ),
        (
            "the autologin password file is empty",
            |f| f.write_file(KCPASSWORD_PATH, 0o600, ""),
            "image_autologin_password_missing",
        ),
        (
            "the screen lock is still on",
            |f| {
                f.reply("sysadminctl -screenLock status", "screenLock is on with 0 seconds delay.\n");
            },
            "image_screen_lock_enabled",
        ),
        (
            "sleep is not disabled",
            |f| f.reply("pmset -g custom", "AC Power:\n displaysleep 0\n sleep 10\n"),
            "image_sleep_enabled",
        ),
        (
            "display sleep is not disabled",
            |f| f.reply("pmset -g custom", "AC Power:\n displaysleep 5\n sleep 0\n"),
            "image_sleep_enabled",
        ),
        (
            "a sleep setting is not a number",
            |f| {
                f.reply("pmset -g custom", "AC Power:\n displaysleep 0\n sleep never\n");
            },
            "image_sleep_enabled",
        ),
        // The quiet failure: with no `sleep` line at all, a check that only refuses non-zero values passes forever.
        (
            "pmset reports no sleep setting",
            |f| {
                f.reply("pmset -g custom", "AC Power:\n displaysleep 0\n disksleep 0\n");
            },
            "image_power_settings_unreadable",
        ),
        (
            "pmset reports no display sleep setting",
            |f| f.reply("pmset -g custom", "AC Power:\n sleep 0\n"),
            "image_power_settings_unreadable",
        ),
        (
            "pmset does not run",
            |f| f.fail_with("pmset -g custom", "", "exit status: 1"),
            "image_tool_not_runnable",
        ),
        (
            "the base started shipping bazel",
            |f| f.write_executable("/opt/homebrew/bin/bazel"),
            "image_forbidden_tool_present",
        ),
        (
            "bazelisk reached the image",
            |f| f.write_executable("/usr/local/bin/bazelisk"),
            "image_forbidden_tool_present",
        ),
        (
            "peekaboo reached the image",
            |f| f.write_executable("/opt/homebrew/bin/peekaboo"),
            "image_forbidden_tool_present",
        ),
        (
            "a helper script is not executable",
            |f| {
                f.write_file(&installed_worker_script(WORKER_SCRIPTS[0].installed), 0o644, "#!/bin/zsh\n");
            },
            "image_helper_not_executable",
        ),
        (
            "the public-image audit refuses the image",
            |f| {
                f.fail_with(
                    &audit_call(),
                    "credential-bearing file must not be baked into the image: /Users/admin/.netrc\n",
                    "exit status: 1",
                );
            },
            "image_audit_refused",
        ),
    ];
    for (name, damage, code) in cases {
        let mut fixture = Fixture::healthy();
        damage(&mut fixture);
        let refusal = fixture.validate().expect_err(name);
        assert_eq!(refusal.code, code, "{name}: {}", refusal.message);
        // One status for the whole family: Packer treats every nonzero status alike.
        assert_eq!(refusal.exit, AgentExit::Refused, "{name}");
    }
    // A helper that was never installed is the same refusal as one that is not executable.
    let mut fixture = Fixture::healthy();
    fixture.remove(&installed_worker_script(WORKER_SCRIPTS[2].installed));
    assert_eq!(fixture.validate().unwrap_err().code, "image_helper_not_executable");
}

/// A refusal names the object it looked at and the value it found: the shell version printed neither.
#[test]
fn a_refusal_names_what_it_checked_and_what_it_found() {
    type Damage = fn(&mut Fixture);
    let cases: [(&str, Damage, &[&str]); 6] = [
        (
            "macOS drift",
            |f| f.reply("sw_vers -productVersion", "26.7.0\n"),
            &["26.6.2", "26.7.0"],
        ),
        (
            "a missing agent",
            |f| f.remove(&junie_shim()),
            &["junie", "/Users/admin/.local/bin", "/opt/homebrew/bin"],
        ),
        (
            "an agent at the wrong build",
            |f| f.reply("junie --version", "Junie 26.9.1 (2801.4)\n"),
            &["junie", "2777.8", "2801.4"],
        ),
        (
            "a helper that is not executable",
            |f| {
                f.write_file(&installed_worker_script(AUDIT_SCRIPT_NAME), 0o644, "#!/bin/zsh\n");
            },
            &["/usr/local/sbin/air-audit-public-image", "publishability gate"],
        ),
        (
            "a forbidden tool",
            |f| f.write_executable("/usr/local/bin/bazel"),
            &["bazel", "/usr/local/bin/bazel"],
        ),
        (
            "the audit's own finding",
            |f| {
                f.fail_with(
                    &audit_call(),
                    "worker-private storage must not be baked into the golden image\n",
                    "exit status: 1",
                );
            },
            &["worker-private storage must not be baked into the golden image"],
        ),
    ];
    for (name, damage, mentions) in cases {
        let mut fixture = Fixture::healthy();
        damage(&mut fixture);
        let refusal = fixture.validate().expect_err(name);
        for fragment in mentions {
            assert!(refusal.message.contains(fragment), "{name}: {fragment}: {}", refusal.message);
        }
    }
}

/// A quoted answer cannot bury the message that named it; an empty one is named as nothing; the cap lands on a
/// character boundary.
#[test]
fn a_quoted_answer_is_capped_and_an_empty_one_is_named() {
    let long = quote_output(&"x".repeat(MAX_QUOTED_OUTPUT * 2));
    assert!(long.len() <= MAX_QUOTED_OUTPUT + 64, "{}", long.len());
    assert!(long.contains("truncated"), "{long}");
    assert_eq!(quote_output(""), "nothing");
    assert_eq!(quote_output("a\nb"), "\"a\\nb\"");
    // A multi-byte character straddling the cap is dropped whole rather than split.
    let straddling = format!("{}é{}", "x".repeat(MAX_QUOTED_OUTPUT - 1), "y".repeat(10));
    let quoted = quote_output(&straddling);
    assert!(quoted.starts_with(&format!("\"{}…", "x".repeat(MAX_QUOTED_OUTPUT - 1))), "{quoted}");
}

// --- the search path ------------------------------------------------------------------------------------------

/// The finished image's path: the pinned keg first, the Junie shim's directory second, then the system path.
#[test]
fn the_search_path_is_the_finished_images_path() {
    let path = search_path(26);
    assert_eq!(path[0], "/opt/homebrew/opt/node@26/bin");
    assert_eq!(path[1], "/Users/admin/.local/bin");
    assert_eq!(path[2..], SYSTEM_PATH.map(str::to_owned));
}

// --- the version match ----------------------------------------------------------------------------------------

#[test]
fn a_version_matches_only_as_a_whole_token() {
    for (output, version) in [
        ("agent-cli 0.147.0", "0.147.0"),
        ("0.85.1", "0.85.1"),
        ("Junie 26.8.17 (2777.8)", "2777.8"),
        ("0.85.1\n", "0.85.1"),
        ("version=0.85.1;build=9", "0.85.1"),
        // A later occurrence is found after an earlier one that is part of a longer version.
        ("0.85.10 then 0.85.1", "0.85.1"),
    ] {
        assert!(contains_version_token(output, version), "{output:?} should match {version:?}");
    }
    for (output, version) in [
        // The false accept the shell's `*"$PIN"*` had.
        ("0.85.10", "0.85.1"),
        ("10.85.1", "0.85.1"),
        ("agent-cli 0.147.01", "0.147.0"),
        ("Junie 26.8.17 (27778)", "2777.8"),
        ("", "0.85.1"),
        ("0.85.1", ""),
    ] {
        assert!(!contains_version_token(output, version), "{output:?} should not match {version:?}");
    }
}

/// The verb's own refusal code, and the verb end to end on this host, which is no pinned image: it refuses under
/// an `image_` code with the family's status, in this verb's envelope.
#[test]
fn the_verb_refuses_under_its_own_name() {
    assert_eq!(verb_refusal_code(AgentVerb::ValidateImage), "guest_validate_image_failed");
    let answered = run_agent(
        &[
            AgentVerb::ValidateImage.as_str(),
            "--macos-version",
            "0.0-never",
            "--node-major",
            "24",
            "--junie-version",
            "1",
        ],
        b"",
    );
    assert_eq!(answered.exit, 70, "{}", answered.stderr);
    assert!(answered.code().starts_with("image_"), "{}", answered.stderr);
    assert_eq!(answered.failure()["command"], AgentVerb::ValidateImage.as_str());
}

/// The live surface merges both streams in order and reports a failure with what was said before it.
#[test]
fn the_live_surface_answers_both_streams_and_how_a_command_ended() {
    let mut live = LiveImage::new(vec!["/usr/bin".to_owned(), "/bin".to_owned()]);
    let answer = live.run(&["/bin/sh", "-c", "echo out; echo err >&2; echo \"$PATH\""]);
    assert_eq!(answer.output, "out\nerr\n/usr/bin:/bin\n");
    assert!(answer.failure.is_none());
    let answer = live.run(&["/bin/sh", "-c", "echo finding >&2; exit 3"]);
    assert_eq!(answer.output, "finding\n");
    assert!(answer.failure.unwrap().contains('3'));
    let answer = live.run(&["/nonexistent/air/tool"]);
    assert!(answer.failure.is_some());
}
