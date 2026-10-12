//! The golden image's provisioner, tested by its transcript.
//!
//! The verb runs about twenty commands in one order against a macOS guest, so what can be checked here is the
//! transcript: which commands, with which argv, in which order, and which ones are skipped when an earlier one
//! refuses. That is where the defects a port could introduce would live - a credential sweep that moved after an
//! install, a version interpolated into the wrong package, a `sudo` on the write that must not have one.

use avl_wire::verb::AgentVerb;
use std::fs;
use std::path::Path;

use avl_wire::supervisor::AgentExit;
use pretty_assertions::assert_eq;

use super::*;
use crate::image::WorkerScript;
use crate::reply::verb_refusal_code;
use crate::step::tests_support::Transcript;
use crate::testing::run_agent;

fn pins() -> ImagePins {
    ImagePins {
        macos_version: "26.6.2".to_owned(),
        node_major: 24,
        junie_version: "2777.8".to_owned(),
    }
}

fn curl() -> String {
    format!("curl -fsSL {JUNIE_INSTALLER_URL}")
}

const INSTALLER: &str = "#!/bin/sh\necho installing junie\n";

struct Fixture {
    _home: tempfile::TempDir,
    _uploads: tempfile::TempDir,
    provisioner: ImageProvisioner<Transcript>,
}

impl Fixture {
    /// A provisioner whose runner is recorded, whose base has every executable, and whose two directories are
    /// temporary, so the steps that really do touch a filesystem touch one the test owns.
    fn new() -> Self {
        let (home, uploads) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
        let mut transcript = Transcript::default();
        for (line, answer) in [
            ("sw_vers -productVersion".to_owned(), "26.6.2\n"),
            ("node --version".to_owned(), "v24.9.0\n"),
            (curl(), INSTALLER),
        ] {
            transcript.answers.insert(line, answer.to_owned());
        }
        let mut provisioner = ImageProvisioner::new(&pins(), transcript);
        provisioner.home = home.path().to_path_buf();
        provisioner.uploads = uploads.path().to_path_buf();
        provisioner.path = provisioner.initial_path();
        provisioner.look_path = Box::new(|_, _| true);
        Self {
            _home: home,
            _uploads: uploads,
            provisioner,
        }
    }

    fn home(&self) -> PathBuf {
        self.provisioner.home.clone()
    }

    fn uploads(&self) -> PathBuf {
        self.provisioner.uploads.clone()
    }

    fn transcript(&mut self) -> &mut Transcript {
        &mut self.provisioner.runner
    }

    fn lines(&self) -> Vec<String> {
        self.provisioner.runner.lines()
    }

    fn sweep_line(&self) -> String {
        let home = self.home();
        std::iter::once("sudo rm -f --".to_owned())
            .chain(
                INHERITED_CREDENTIAL_FILES
                    .iter()
                    .map(|name| home.join(name).to_string_lossy().into_owned()),
            )
            .collect::<Vec<_>>()
            .join(" ")
    }
}

fn write(path: &Path, content: &str) {
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, content).unwrap();
}

// --- the argument contract ------------------------------------------------------------------------------------

/// The scripts opened with three `: "${AIR_…:?}"` guards, and every one is a required flag: a pin that silently
/// defaulted would produce an image nobody asked for and a `validate-image` that agreed with it.
#[test]
fn every_version_pin_is_required() {
    let complete = [("--macos-version", "26.6.2"), ("--node-major", "24"), ("--junie-version", "2777.8")];
    for omitted in 0..complete.len() {
        let mut argv = vec![AgentVerb::ProvisionImage.as_str()];
        for (index, (flag, value)) in complete.iter().enumerate() {
            if index != omitted {
                argv.extend([*flag, *value]);
            }
        }
        let answered = run_agent(&argv, b"");
        assert_eq!(
            (answered.exit, answered.code()),
            (64, "usage".to_owned()),
            "{} omitted",
            complete[omitted].0
        );
        assert_eq!(answered.failure()["command"], AgentVerb::ProvisionImage.as_str());
    }
}

/// A pin the parser reads wrong produces an image nobody asked for, so no argv that could mean two things is
/// tolerated: an unknown flag, a repeated one, an empty value, a missing value, a positional token, a major that is
/// not a positive number.
#[test]
fn an_argv_that_could_mean_two_things_is_refused() {
    let cases: [(&str, &[&str]); 7] = [
        (
            "an unknown flag",
            &[
                "--macos-version",
                "26.6.2",
                "--junie",
                "2777.8",
                "--node-major",
                "24",
                "--junie-version",
                "2777.8",
            ],
        ),
        (
            "a repeated flag",
            &[
                "--macos-version",
                "26.6.2",
                "--macos-version",
                "26.6.1",
                "--node-major",
                "24",
                "--junie-version",
                "2777.8",
            ],
        ),
        (
            "an empty value",
            &["--macos-version", "", "--node-major", "24", "--junie-version", "2777.8"],
        ),
        (
            "a value that is not there",
            &["--macos-version", "26.6.2", "--node-major", "24", "--junie-version"],
        ),
        (
            "a positional token",
            &[
                "26.6.2",
                "--macos-version",
                "26.6.2",
                "--node-major",
                "24",
                "--junie-version",
                "2777.8",
            ],
        ),
        (
            "a node major that is not a number",
            &[
                "--macos-version",
                "26.6.2",
                "--node-major",
                "twenty-four",
                "--junie-version",
                "2777.8",
            ],
        ),
        (
            "a node major that is not positive",
            &["--macos-version", "26.6.2", "--node-major", "0", "--junie-version", "2777.8"],
        ),
    ];
    for (name, argv) in cases {
        let args: Vec<&str> = std::iter::once(AgentVerb::ProvisionImage.as_str())
            .chain(argv.iter().copied())
            .collect();
        let answered = run_agent(&args, b"");
        assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{name}");
    }
}

// --- the pinned Node major ------------------------------------------------------------------------------------

/// The major used to live in three places; both derivations now come from the pin, so the PATH and the login
/// profile cannot describe different Nodes.
#[test]
fn the_node_major_reaches_every_place_it_is_spelled() {
    assert_eq!(node_bin_directory(24), "/opt/homebrew/opt/node@24/bin");
    let mut fixture = Fixture::new();
    fixture.provisioner.pins.node_major = 26;
    assert_eq!(fixture.provisioner.initial_path()[0], "/opt/homebrew/opt/node@26/bin");
    let block = fixture.provisioner.login_profile_block();
    assert!(block.contains("/opt/homebrew/opt/node@26/bin"), "{block}");
    assert!(!block.contains("node@24"), "{block}");
}

/// The PATH is a replacement, not an addition: every step runs with exactly the image's PATH and the three
/// variables that keep Homebrew and npm from changing the image behind the pins, whatever Packer's shell had.
#[test]
fn the_exported_path_replaces_whatever_packer_had() {
    let environment = image_environment(&[node_bin_directory(24), "/usr/bin".to_owned()]);
    assert_eq!(
        environment,
        [
            ("PATH", "/opt/homebrew/opt/node@24/bin:/usr/bin"),
            ("HOMEBREW_NO_AUTO_UPDATE", "1"),
            ("HOMEBREW_NO_INSTALL_CLEANUP", "1"),
            ("npm_config_update_notifier", "false"),
        ]
        .map(|(name, value)| (name.to_owned(), value.to_owned()))
    );
    let mut fixture = Fixture::new();
    fixture.provisioner.provision().unwrap();
    let initial = image_environment(&fixture.provisioner.initial_path());
    let first = &fixture.provisioner.runner.steps[0];
    assert_eq!(first.command_line(), "sw_vers -productVersion");
    assert_eq!(first.env, initial);
    // No step runs without the image's own environment.
    for step in &fixture.provisioner.runner.steps {
        assert_eq!(step.env[0].0, "PATH", "{}", step.command_line());
        assert_eq!(step.env[1..4], initial[1..4], "{}", step.command_line());
    }
}

// --- refusing a base that is not the pinned one ---------------------------------------------------------------

#[test]
fn a_base_at_the_wrong_macos_version_is_refused() {
    let mut fixture = Fixture::new();
    fixture
        .transcript()
        .answers
        .insert("sw_vers -productVersion".to_owned(), "26.6.1\n".to_owned());
    let refused = fixture.provisioner.require_expected_base().unwrap_err();
    assert_eq!(refused.message, "expected macOS 26.6.2, found 26.6.1");
}

/// `sw_vers` ends its answer with a newline; comparing the raw bytes would refuse every correct base there is.
#[test]
fn the_product_version_is_compared_without_its_trailing_newline() {
    Fixture::new().provisioner.require_expected_base().unwrap();
}

/// The refusal names the executable: "tart-guest-agent is missing" means the pool would have been unreachable.
#[test]
fn a_missing_base_executable_is_refused_by_name() {
    for executable in REQUIRED_BASE_EXECUTABLES {
        let mut fixture = Fixture::new();
        fixture.provisioner.look_path = Box::new(move |name, _| name != executable);
        let refused = fixture.provisioner.require_expected_base().unwrap_err();
        assert!(refused.message.ends_with(&format!(": {executable}")), "{}", refused.message);
    }
    // Resolved against the image's PATH, not this process's.
    let mut fixture = Fixture::new();
    fixture.provisioner.look_path = Box::new(|_, path| path[0] == "/opt/homebrew/opt/node@24/bin");
    fixture.provisioner.require_expected_base().unwrap();
}

/// The trailing dot in the `v24.` prefix is the whole check.
#[test]
fn a_node_major_is_matched_as_a_whole_component() {
    let mut fixture = Fixture::new();
    fixture
        .transcript()
        .answers
        .insert("node --version".to_owned(), "v240.1.0\n".to_owned());
    assert!(fixture.provisioner.require_expected_base().is_err());
    fixture
        .transcript()
        .answers
        .insert("node --version".to_owned(), "v24.9.0\n".to_owned());
    fixture.provisioner.require_expected_base().unwrap();
}

// --- the credential sweep -------------------------------------------------------------------------------------

/// An inherited `.netrc` left in place until the end could authenticate the Junie download, so the sweep comes
/// before the first install.
#[test]
fn credentials_are_swept_before_anything_is_installed() {
    let mut fixture = Fixture::new();
    fixture.provisioner.provision().unwrap();
    let transcript = &fixture.provisioner.runner;
    let sweep = transcript.index_of("sudo rm -f --").unwrap();
    let junie = transcript.index_of("curl -fsSL").unwrap();
    assert!(sweep < junie, "{:?}", transcript.lines());
}

/// Every path the script listed is still swept. The list is spelled out here rather than read from the one under
/// test: a test that read it would pass no matter what was removed from it.
#[test]
fn every_known_credential_path_is_swept() {
    let mut fixture = Fixture::new();
    fixture.provisioner.remove_inherited_credentials().unwrap();
    let transcript = &fixture.provisioner.runner;
    assert_eq!(transcript.steps.len(), 1);
    let argv = &transcript.steps[0].argv;
    assert_eq!(argv[..4], ["sudo", "rm", "-f", "--"]);
    for name in [
        ".netrc",
        ".npmrc",
        ".gitconfig",
        ".git-credentials",
        ".ssh/authorized_keys",
        ".codex/auth.json",
        ".pi/agent/auth.json",
        ".claude/.credentials.json",
        ".local/share/opencode/auth.json",
        ".config/codex/auth.json",
        ".config/gh/hosts.yml",
        ".docker/config.json",
        ".config/containers/auth.json",
        ".config/gcloud/application_default_credentials.json",
        ".config/gcloud/credentials.db",
        ".aws/credentials",
        ".azure/accessTokens.json",
        ".azure/msal_token_cache.json",
        ".kube/config",
    ] {
        let wanted = fixture.home().join(name).to_string_lossy().into_owned();
        assert!(argv.contains(&wanted), "{name} is not swept");
    }
}

/// No match contributes no argument (zsh's `(N)`), and every `id_*` there is swept; `known_hosts` is not.
#[test]
fn the_private_key_sweep_takes_what_is_there_and_tolerates_what_is_not() {
    let mut fixture = Fixture::new();
    fixture.provisioner.remove_inherited_credentials().unwrap();
    let argv = fixture.provisioner.runner.steps[0].argv.clone();
    assert!(!argv.iter().any(|argument| argument.contains("id_")), "{argv:?}");

    let mut fixture = Fixture::new();
    let ssh = fixture.home().join(".ssh");
    for name in ["id_ed25519", "id_ed25519.pub", "id_rsa", "known_hosts"] {
        write(&ssh.join(name), "x");
    }
    fixture.provisioner.remove_inherited_credentials().unwrap();
    let argv = &fixture.provisioner.runner.steps[0].argv;
    let keys: Vec<&String> = argv.iter().filter(|argument| argument.contains("/.ssh/id_")).collect();
    let wanted: Vec<String> = ["id_ed25519", "id_ed25519.pub", "id_rsa"]
        .map(|name| ssh.join(name).to_string_lossy().into_owned())
        .to_vec();
    assert_eq!(keys, wanted.iter().collect::<Vec<_>>());
    assert!(!argv.iter().any(|argument| argument.contains("known_hosts")));
}

// --- the installs ---------------------------------------------------------------------------------------------

/// No agent CLI comes from npm: Codex and Pi are Bazel-declared test runtimes, so an `npm install` here would be a
/// second answer to which build a lane drives (flow-ui-scenarios: VM preparation installs and probes no agent CLI).
#[test]
fn no_agent_is_installed_from_npm() {
    let mut fixture = Fixture::new();
    fixture.provisioner.provision().unwrap();
    let installs: Vec<String> = fixture
        .lines()
        .into_iter()
        .filter(|line| line.starts_with("npm install") || line.starts_with("npm i "))
        .collect();
    assert!(installs.is_empty(), "{installs:?}");
}

/// The installer body is downloaded whole and only then handed to a shell, with the pinned `JUNIE_VERSION` on
/// top of the image's environment.
#[test]
fn the_junie_installer_is_downloaded_whole_before_a_shell_sees_it() {
    let mut fixture = Fixture::new();
    fixture.provisioner.install_junie().unwrap();
    let transcript = &fixture.provisioner.runner;
    assert_eq!(transcript.lines(), [curl(), "bash".to_owned()]);
    assert_eq!(transcript.steps[0].output, crate::step::Output::Capture);
    let shell = &transcript.steps[1];
    assert_eq!(shell.stdin.as_deref(), Some(INSTALLER.as_bytes()));
    let mut wanted = transcript.steps[0].env.clone();
    wanted.push(("JUNIE_VERSION".to_owned(), "2777.8".to_owned()));
    assert_eq!(shell.env, wanted);
}

/// A failed download never reaches a shell.
#[test]
fn a_failed_junie_download_never_starts_a_shell() {
    let mut fixture = Fixture::new();
    fixture.transcript().fails.insert(curl());
    assert!(fixture.provisioner.install_junie().is_err());
    assert!(!fixture.provisioner.runner.ran("bash"));
}

/// `curl -f` refuses an HTTP error but not a 200 with nothing in it.
#[test]
fn an_empty_junie_installer_is_refused_here_rather_than_three_provisioners_later() {
    let mut fixture = Fixture::new();
    fixture.transcript().answers.insert(curl(), "\n  \n".to_owned());
    let refused = fixture.provisioner.install_junie().unwrap_err();
    assert!(refused.message.contains("empty installer"), "{}", refused.message);
    assert!(!fixture.provisioner.runner.ran("bash"));
}

/// The shim's directory joins the PATH only after the installer wrote it, and first.
#[test]
fn the_shim_directory_is_exported_after_the_installer_wrote_it() {
    let mut fixture = Fixture::new();
    fixture.provisioner.install_junie().unwrap();
    let shim = local_bin_directory(&fixture.home());
    for step in &fixture.provisioner.runner.steps {
        assert!(!step.env[0].1.contains(&shim), "{}", step.command_line());
    }
    assert_eq!(fixture.provisioner.path[0], shim);
    assert_eq!(fixture.provisioner.path[1..], fixture.provisioner.initial_path()[..]);
}

// --- the unattended session -----------------------------------------------------------------------------------

/// Which write takes `sudo` is decided by the file it writes, and both mistakes are quiet.
#[test]
fn only_the_system_wide_settings_are_written_as_root() {
    let mut fixture = Fixture::new();
    fixture.provisioner.enforce_unattended_session().unwrap();
    assert_eq!(
        fixture.lines(),
        [
            "sudo defaults write /Library/Preferences/com.apple.loginwindow autoLoginUser admin",
            "sudo defaults write /Library/Preferences/com.apple.screensaver loginWindowIdleTime -int 0",
            "defaults -currentHost write com.apple.screensaver idleTime -int 0",
            "defaults -currentHost write com.apple.screensaver askForPassword -int 0",
            "defaults -currentHost write com.apple.screensaver askForPasswordDelay -int 0",
            "sudo pmset -a sleep 0 displaysleep 0 disksleep 0 powernap 0 standby 0 autopoweroff 0",
            "sysadminctl -screenLock off -password admin",
        ]
    );
}

// --- the worker scripts ---------------------------------------------------------------------------------------

fn install_line(uploads: &Path, script: &WorkerScript) -> String {
    format!(
        "sudo install -o root -g wheel -m 0755 {} /usr/local/sbin/{}",
        uploads.join(script.upload).display(),
        script.installed
    )
}

/// Root-owned and 0755, then the uploads removed, only once every copy exists.
#[test]
fn the_worker_scripts_are_installed_as_root_and_their_uploads_then_removed() {
    let mut fixture = Fixture::new();
    let uploads = fixture.uploads();
    for script in &WORKER_SCRIPTS {
        write(&uploads.join(script.upload), "#!/bin/sh\n");
    }
    fixture.provisioner.install_worker_scripts().unwrap();
    let mut wanted = vec!["sudo install -d -o root -g wheel -m 0755 /usr/local/sbin".to_owned()];
    wanted.extend(WORKER_SCRIPTS.iter().map(|script| install_line(&uploads, script)));
    assert_eq!(fixture.lines(), wanted);
    for script in &WORKER_SCRIPTS {
        assert!(!uploads.join(script.upload).exists(), "{} survived", script.upload);
    }
}

/// A failed install leaves its source in place, because the source is the only copy.
#[test]
fn a_failed_install_leaves_the_upload_behind() {
    let mut fixture = Fixture::new();
    let uploads = fixture.uploads();
    let upload = uploads.join(WORKER_SCRIPTS[0].upload);
    write(&upload, "#!/bin/sh\n");
    let failing = install_line(&uploads, &WORKER_SCRIPTS[0]);
    fixture.transcript().fails.insert(failing);
    assert!(fixture.provisioner.install_worker_scripts().is_err());
    assert!(upload.exists());
}

/// `rm -f`: a missing upload is not a failure.
#[test]
fn a_removal_tolerates_a_missing_upload() {
    Fixture::new().provisioner.install_worker_scripts().unwrap();
}

// --- the login profile ----------------------------------------------------------------------------------------

/// The marker is the whole idempotence mechanism: appended once, never again, with its own leading blank line.
#[test]
fn the_toolchain_block_is_appended_exactly_once() {
    let fixture = Fixture::new();
    for _ in 0..3 {
        fixture.provisioner.write_login_profile().unwrap();
    }
    let content = fs::read_to_string(fixture.home().join(".zprofile")).unwrap();
    assert_eq!(content.matches(LOGIN_PROFILE_MARKER).count(), 1, "{content}");
    assert!(content.starts_with(&format!("\n{LOGIN_PROFILE_MARKER}\n")), "{content:?}");
    assert!(content.contains(r#"eval "$(/opt/homebrew/bin/brew shellenv)""#), "{content}");
    assert_eq!(content, fixture.provisioner.login_profile_block());
}

/// A profile the base already had is added to, not replaced.
#[test]
fn an_existing_profile_is_preserved() {
    let fixture = Fixture::new();
    let path = fixture.home().join(".zprofile");
    fs::write(&path, "export EDITOR=vi\n").unwrap();
    fixture.provisioner.write_login_profile().unwrap();
    let content = fs::read_to_string(&path).unwrap();
    assert!(content.starts_with("export EDITOR=vi\n"), "{content}");
    assert!(content.ends_with(&fixture.provisioner.login_profile_block()), "{content}");
}

/// A comment *about* the marker is not the marker.
#[test]
fn a_line_that_merely_mentions_the_marker_is_not_the_marker() {
    let fixture = Fixture::new();
    let path = fixture.home().join(".zprofile");
    fs::write(&path, "# see the # AIR VM toolchain block below\n").unwrap();
    fixture.provisioner.write_login_profile().unwrap();
    let content = fs::read_to_string(&path).unwrap();
    assert!(content.contains(&format!("\n{LOGIN_PROFILE_MARKER}\n")), "{content}");
}

// --- the residue sweep ----------------------------------------------------------------------------------------

/// Keep the packages, drop the record of how they got there; the cache clean's chatter is discarded.
#[test]
fn the_residue_sweep_removes_the_record_of_the_build() {
    let mut fixture = Fixture::new();
    let home = fixture.home();
    for name in RESIDUE_DIRECTORIES {
        write(&home.join(name).join("entry"), "x");
    }
    for name in RESIDUE_FILES {
        write(&home.join(name), "npm install\n");
    }
    fixture.provisioner.remove_provisioning_residue().unwrap();
    for name in RESIDUE_DIRECTORIES.iter().chain(&RESIDUE_FILES) {
        assert!(!home.join(name).exists(), "{name} survived the residue sweep");
    }
    let clean = &fixture.provisioner.runner.steps[0];
    assert_eq!(clean.command_line(), "npm cache clean --force");
    assert_eq!(clean.output, crate::step::Output::Silent);
}

/// The redirection hides the output, not the failure.
#[test]
fn a_failed_cache_clean_fails_the_build() {
    let mut fixture = Fixture::new();
    fixture.transcript().fails.insert("npm cache clean --force".to_owned());
    assert!(fixture.provisioner.remove_provisioning_residue().is_err());
}

// --- the whole verb -------------------------------------------------------------------------------------------

/// The script, in the script's order: everything the zsh did, once each, in its sequence - and no agent CLI step
/// anywhere in it.
#[test]
fn the_verb_runs_the_scripts_commands_in_the_scripts_order() {
    let mut fixture = Fixture::new();
    fixture.provisioner.provision().unwrap();
    let uploads = fixture.uploads();
    let mut wanted = vec![
        "sw_vers -productVersion".to_owned(),
        "node --version".to_owned(),
        fixture.sweep_line(),
        curl(),
        "bash".to_owned(),
    ];
    wanted.extend(
        [
            "sudo defaults write /Library/Preferences/com.apple.loginwindow autoLoginUser admin",
            "sudo defaults write /Library/Preferences/com.apple.screensaver loginWindowIdleTime -int 0",
            "defaults -currentHost write com.apple.screensaver idleTime -int 0",
            "defaults -currentHost write com.apple.screensaver askForPassword -int 0",
            "defaults -currentHost write com.apple.screensaver askForPasswordDelay -int 0",
            "sudo pmset -a sleep 0 displaysleep 0 disksleep 0 powernap 0 standby 0 autopoweroff 0",
            "sysadminctl -screenLock off -password admin",
            "sudo install -d -o root -g wheel -m 0755 /usr/local/sbin",
        ]
        .map(str::to_owned),
    );
    for (upload, installed) in [
        ("air-init-worker-storage.sh", "air-init-worker-storage"),
        ("air-audit-public-image.sh", "air-audit-public-image"),
        ("air-ensure-ssh-host-keys.sh", "air-ensure-ssh-host-keys"),
    ] {
        wanted.push(format!(
            "sudo install -o root -g wheel -m 0755 {} /usr/local/sbin/{installed}",
            uploads.join(upload).display()
        ));
    }
    wanted.push("npm cache clean --force".to_owned());
    assert_eq!(fixture.lines(), wanted);
    // The environment is the initial one up to the installer, and leads with the shim afterwards.
    let transcript = &fixture.provisioner.runner;
    let shim = local_bin_directory(&fixture.home());
    assert!(!transcript.step("bash").env[0].1.starts_with(&shim));
    assert!(transcript.step("sysadminctl").env[0].1.starts_with(&shim));
}

/// A refusal stops the verb where it stands: nothing is installed into an account whose inherited credentials are
/// still in place.
#[test]
fn a_refused_sweep_installs_nothing() {
    let mut fixture = Fixture::new();
    let sweep = fixture.sweep_line();
    fixture.transcript().fails.insert(sweep);
    fixture.provisioner.provision().unwrap_err();
    assert!(!fixture.provisioner.runner.ran("curl"), "{:?}", fixture.lines());
}

/// The reply echoes the pins the verb was given, under the names the envelope carries.
#[test]
fn the_reply_echoes_the_pins_it_was_given() {
    let mut fixture = Fixture::new();
    let reply = fixture.provisioner.provision().unwrap();
    assert_eq!(
        serde_json::to_value(reply).unwrap(),
        serde_json::json!({"macosVersion": "26.6.2", "nodeMajor": 24, "junieVersion": "2777.8"})
    );
}

/// `usage` at 64 for an argv the verb cannot act on, and the verb-derived code at 70 for everything that went
/// wrong afterwards: the host reads 64 as "an agent older than this controller". End to end on this host, which is
/// no pinned base: `sw_vers` answers another version or does not run, and either way it is the verb's refusal.
#[test]
fn the_refusal_vocabulary_is_the_one_the_host_reads() {
    assert_eq!(verb_refusal_code(AgentVerb::ProvisionImage), "guest_provision_image_failed");
    let usage = run_agent(&[AgentVerb::ProvisionImage.as_str(), "--no-such-flag"], b"");
    assert_eq!((usage.exit, usage.code()), (64, "usage".to_owned()));
    let refused = AgentRefusal::for_verb(AgentVerb::ProvisionImage, "expected macOS 1, found 2");
    assert_eq!(
        (refused.code.as_ref(), refused.exit),
        ("guest_provision_image_failed", AgentExit::Refused)
    );
    let answered = run_agent(
        &[
            AgentVerb::ProvisionImage.as_str(),
            "--macos-version",
            "0.0-never",
            "--node-major",
            "24",
            "--junie-version",
            "1",
        ],
        b"",
    );
    assert_eq!((answered.exit, answered.code()), (70, "guest_provision_image_failed".to_owned()));
}
