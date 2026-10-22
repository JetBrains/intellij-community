//! The Central provisioning of a live run over a fake Central CLI: the order of its subcommands, the login on stdin
//! only, the strict failures, and the teardown.
//!
//! The fake records the arguments, the `HOME` and the length of the stdin of each call, never the content. Only a
//! failing import echoes its stdin to stderr, its lines joined, so the redaction of the failure has a secret to remove.
//! The secret is a multi-line export that no token-like run covers, so only the redaction of its own parts removes it.

use std::fs;
use std::io;
use std::os::unix::fs::{PermissionsExt, symlink};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;

use super::*;

const VERSION: &str = "1.12.1-alpha.410";
const LOGIN: &str = "AIR_LIVE_CENTRAL_LOGIN";

/// The one field of [`SECRET`] that a token scan would match: letters and digits, too short for a token-like run.
const SESSION_TOKEN: &str = "s3ss10n-k3y-abc123def";

/// A fake login export: a document of short parts, so no token-like run covers it whole.
const SECRET: &str = "{\n  \"server\": \"central example host\",\n  \"session\": \"s3ss10n-k3y-abc123def\"\n}";

/// The status text of `central 1.12.1-alpha.406` with its colours, the proxy running. No secret is in it.
const RUNNING_STATUS: &str = "\u{1b}[1;32m⣿\u{1b}[0m Auth       JetBrains Team\n\
                              \u{1b}[1;32m⣿\u{1b}[0m Proxy      \u{1b}[32mrunning\u{1b}[0m on port 19516 · pid 54711 · up 14h 27m\n\
                              \u{1b}[1;32m⣿\u{1b}[0m Version    1.12.1-alpha.406\n\nAgents\n";

/// Written by hand: the Proxy line of a stopped proxy was not observed.
const STOPPED_STATUS: &str = "\u{1b}[1;32m⣿\u{1b}[0m Auth       JetBrains Team\n\
                              \u{1b}[1;31m⣿\u{1b}[0m Proxy      \u{1b}[31mstopped\u{1b}[0m\n";

/// A Central CLI that records `<arguments> stdin=<length> HOME=<home>` for each call. A `fail-<step>` or `hang-<step>`
/// file beside it makes that step fail or hang, and `hang-import` hangs before it reads its stdin. `status` reports the
/// proxy on the port of the file `status-port`, 19516 without it.
const FAKE_CENTRAL: &str = r#"#!/bin/sh
if [ "$1 $2" = "login import" ] && [ -f '{dir}/hang-import' ]; then exec sleep 20; fi
input=$(cat)
length=$(printf %s "$input" | wc -c | tr -d ' ')
echo "$* stdin=$length HOME=$HOME" >> '{dir}/calls.log'
case "$1 $2" in
  "login import") if [ -f '{dir}/fail-import' ]; then echo "rejected $(printf %s "$input" | tr '\n' ' ')" >&2; exit 4; fi ;;
  "proxy start") if [ -f '{dir}/hang-proxy' ]; then exec sleep 20; fi ;;
  "proxy stop") if [ -f '{dir}/fail-stop' ]; then echo "no proxy" >&2; exit 6; fi ;;
  "add junie") if [ -f '{dir}/fail-add-junie' ]; then echo "junie is not installed" >&2; exit 5; fi ;;
  "status ") port=19516; if [ -f '{dir}/status-port' ]; then port=$(cat '{dir}/status-port'); fi
    printf '\033[1;32m⣿\033[0m Proxy      \033[32mrunning\033[0m on port %s · pid 1\n' "$port" ;;
esac
exit 0
"#;

/// The process runner with the bound of [`Central`] for the steps that start with `step` only. Every other step gets the
/// bound of a real run, so the time that a loaded machine takes to start the fake never counts against the short bound.
struct BoundOnly(&'static str);

impl CentralRunner for BoundOnly {
    fn run(&mut self, argv: &[String], home: &Path, stdin: Option<&[u8]>, output: StepOutput, timeout: Duration) -> io::Result<Outcome> {
        let bounded = argv.get(1..).is_some_and(|arguments| arguments.join(" ").starts_with(self.0));
        ProcessRunner.run(argv, home, stdin, output, if bounded { timeout } else { STEP_TIMEOUT })
    }
}

/// What the fake process sweep was asked to end. It answers one Junie gateway each time.
#[derive(Default)]
struct Sweeps(Vec<Vec<String>>);

impl AgentProcesses for Sweeps {
    fn stop(&mut self, names: &[&str], _timeout: Duration) -> Vec<String> {
        self.0.push(names.iter().map(|name| (*name).to_owned()).collect());
        vec!["junie pid 2680".to_owned()]
    }
}

struct Fixture {
    directory: tempfile::TempDir,
    account: GuestAccount,
    cli: PathBuf,
    agents: Vec<String>,
}

impl Fixture {
    fn new() -> Self {
        let directory = tempfile::tempdir().unwrap();
        let home = directory.path().join("account");
        let secrets = directory.path().join("secrets");
        fs::create_dir_all(&home).unwrap();
        fs::create_dir_all(&secrets).unwrap();
        let account = GuestAccount::of_lane_daemon(|name| environment(name, &home, &secrets), true)
            .unwrap()
            .expect("the marker names a guest account");
        let cli = directory.path().join("central");
        fs::write(&cli, FAKE_CENTRAL.replace("{dir}", &directory.path().display().to_string())).unwrap();
        fs::set_permissions(&cli, fs::Permissions::from_mode(0o700)).unwrap();
        Self {
            directory,
            account,
            cli,
            agents: ["codex", "claude-code", "junie"].map(str::to_owned).to_vec(),
        }
    }

    fn central(&self, step_timeout: Duration) -> Central<'_, ProcessRunner, Sweeps> {
        self.central_with(step_timeout, ProcessRunner)
    }

    fn central_with<R: CentralRunner>(&self, step_timeout: Duration, runner: R) -> Central<'_, R, Sweeps> {
        Central {
            account: &self.account,
            cli: &self.cli,
            pinned_version: VERSION,
            port: 19516,
            agents: &self.agents,
            step_timeout,
            runner,
            processes: Sweeps::default(),
        }
    }

    fn home(&self) -> &Path {
        self.account.home()
    }

    /// Makes the fake fail or hang at a step, or report another port.
    fn flag(&self, name: &str, content: &str) {
        fs::write(self.directory.path().join(name), content).unwrap();
    }

    fn login(&self, content: &[u8]) -> PathBuf {
        let login = self.account.input(LOGIN);
        fs::write(&login, content).unwrap();
        login
    }

    fn calls(&self) -> Vec<String> {
        fs::read_to_string(self.directory.path().join("calls.log"))
            .map(|text| text.lines().map(str::to_owned).collect())
            .unwrap_or_default()
    }

    /// The calls without the stdin length and the home.
    fn steps(&self) -> Vec<String> {
        self.calls()
            .iter()
            .map(|call| call.split(" stdin=").next().unwrap_or_default().to_owned())
            .collect()
    }

    fn put(&self, entry: &str, content: &str) {
        let path = self.home().join(entry);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, content).unwrap();
    }
}

fn environment(name: &str, home: &Path, secrets: &Path) -> Option<String> {
    match name {
        RUN_SECRETS_VARIABLE => Some(secrets.display().to_string()),
        "HOME" => Some(home.display().to_string()),
        _ => None,
    }
}

fn notes_of(run: impl FnOnce(&mut dyn FnMut(&str))) -> Vec<String> {
    let mut notes = Vec::new();
    run(&mut |line| notes.push(line.to_owned()));
    notes
}

// The login reaches the CLI on stdin alone: argv, the notes and the record of the fake never hold it.
#[test]
fn the_provisioning_pins_the_configuration_imports_on_stdin_starts_the_proxy_and_wires_every_agent() {
    let fixture = Fixture::new();
    let login = fixture.login(SECRET.as_bytes());
    let mut central = fixture.central(STEP_TIMEOUT);
    let mut provisioned = None;

    let notes = notes_of(|note| provisioned = Some(central.provision(&login, note).unwrap()));

    let home = fixture.home().display().to_string();
    let expected: Vec<String> = [
        format!("config set release-track {VERSION} stdin=0"),
        "config set google-analytics off stdin=0".to_owned(),
        "config set port 19516 stdin=0".to_owned(),
        format!("login import stdin={}", SECRET.len()),
        "proxy start stdin=0".to_owned(),
        "add codex stdin=0".to_owned(),
        "add claude-code stdin=0".to_owned(),
        "add junie stdin=0".to_owned(),
        "status stdin=0".to_owned(),
    ]
    .into_iter()
    .map(|call| format!("{call} HOME={home}"))
    .collect();
    assert_eq!(fixture.calls(), expected);
    assert_eq!(provisioned.unwrap().port, 19516);
    assert!(
        notes.contains(&"central provisioning: login import ok, proxy :19516, add codex|claude-code|junie ok".to_owned()),
        "{notes:?}"
    );
    assert!(!login.exists(), "the login input is used once");
    let record = fs::read_to_string(fixture.directory.path().join("calls.log")).unwrap();
    assert!(!record.contains(SESSION_TOKEN), "the secret is never on argv");
    assert!(!notes.join("\n").contains(SESSION_TOKEN), "{notes:?}");
}

// A failure names the step and quotes the last stderr line with every part of the login redacted.
#[test]
fn a_failed_import_names_the_step_and_its_exit_code_redacts_the_secret_and_still_deletes_the_input() {
    let fixture = Fixture::new();
    let login = fixture.login(SECRET.as_bytes());
    fixture.flag("fail-import", "");
    let mut central = fixture.central(STEP_TIMEOUT);

    let mut failure = None;
    let notes = notes_of(|note| failure = central.provision(&login, note).err());

    let message = failure.expect("the import fails").message;
    assert!(
        message.contains("central provisioning failed at `central login import`: exit 4; stderr: rejected {   <redacted>   <redacted> }"),
        "{message}"
    );
    assert!(
        !message.contains(SESSION_TOKEN) && !message.contains("central example host"),
        "{message}"
    );
    assert_eq!(
        fixture.steps().last_chunk::<2>(),
        Some(&["config set port 19516".to_owned(), "login import".to_owned()]),
        "nothing runs after the failed step"
    );
    assert!(!login.exists());
    assert!(!notes.join("\n").contains(SESSION_TOKEN), "{notes:?}");
}

#[test]
fn a_failed_add_names_its_agent() {
    let fixture = Fixture::new();
    let login = fixture.login(SECRET.as_bytes());
    fixture.flag("fail-add-junie", "");

    let failure = fixture.central(STEP_TIMEOUT).provision(&login, &mut |_| {}).unwrap_err();

    assert_eq!(
        failure.message,
        "central provisioning failed at `central add junie`: exit 5; stderr: junie is not installed"
    );
    assert_eq!(failure.code, CENTRAL_CODE);
}

#[test]
fn a_step_that_outlives_its_bound_fails_by_its_name() {
    let fixture = Fixture::new();
    let login = fixture.login(SECRET.as_bytes());
    fixture.flag("hang-proxy", "");

    let failure = fixture
        .central_with(Duration::from_secs(1), BoundOnly("proxy start"))
        .provision(&login, &mut |_| {})
        .unwrap_err();

    assert_eq!(
        failure.message,
        "central provisioning failed at `central proxy start`: no exit within 1 s"
    );
}

#[test]
fn an_empty_login_input_fails_before_the_import_runs() {
    let fixture = Fixture::new();
    let login = fixture.login(b"  \n");

    let failure = fixture.central(STEP_TIMEOUT).provision(&login, &mut |_| {}).unwrap_err();

    assert!(failure.message.ends_with("is empty"), "{}", failure.message);
    assert!(!fixture.steps().contains(&"login import".to_owned()));
    assert!(!login.exists());
}

// `central add` writes plugins and caches beside the wiring, so each home goes whole, and the state goes after the stop.
#[test]
fn the_teardown_stops_the_proxy_and_deletes_the_accounts_central_and_agent_homes_whole_never_logging_out() {
    let fixture = Fixture::new();
    let outside = fixture.directory.path().join("outside.txt");
    fs::write(&outside, "not the account's").unwrap();
    for entry in [
        ".jetbrains-central/tokens.enc",
        ".claude/plugins/x.json",
        ".codex/plugins/cache/y",
        ".junie/extensions/z",
        ".cache/jetbrains-central/w",
        ".cache/other/keep",
    ] {
        fixture.put(entry, "fake");
    }
    fixture.put(".claude.json", "{}");
    symlink(&outside, fixture.home().join(".claude/linked")).unwrap();
    let mut central = fixture.central(STEP_TIMEOUT);

    let mut removed = Vec::new();
    let notes = notes_of(|note| removed = central.tear_down(note).unwrap());

    assert_eq!(fixture.calls(), [format!("proxy stop stdin=0 HOME={}", fixture.home().display())]);
    assert_eq!(
        central.processes.0,
        [AGENT_PROCESS_NAMES.map(str::to_owned).to_vec()],
        "the leftover processes end after the proxy stop, before the homes go"
    );
    assert!(
        notes
            .iter()
            .any(|note| note.contains("ended 1 leftover agent process(es): junie pid 2680")),
        "{notes:?}"
    );
    assert_eq!(removed, TEARDOWN_ENTRIES.map(|entry| fixture.home().join(entry)));
    for entry in TEARDOWN_ENTRIES {
        assert!(fixture.home().join(entry).symlink_metadata().is_err(), "{entry}");
    }
    assert!(fixture.home().join(".cache/other/keep").exists(), "only the cache of Central goes");
    assert_eq!(
        fs::read_to_string(&outside).unwrap(),
        "not the account's",
        "a link is deleted, never followed"
    );
    assert_eq!(
        central.tear_down(&mut |_| {}).unwrap(),
        Vec::<PathBuf>::new(),
        "a second close finds nothing"
    );
    assert_eq!(fixture.calls().len(), 1, "and stops nothing");
}

#[test]
fn a_proxy_that_does_not_stop_is_reported_and_the_teardown_still_deletes() {
    let fixture = Fixture::new();
    fs::create_dir_all(fixture.home().join(".jetbrains-central")).unwrap();
    fixture.flag("fail-stop", "");
    let mut central = fixture.central(STEP_TIMEOUT);

    let mut removed = Vec::new();
    let notes = notes_of(|note| removed = central.tear_down(note).unwrap());

    assert_eq!(removed, [fixture.home().join(".jetbrains-central")]);
    assert!(notes.contains(&"central proxy stop answered exit 6".to_owned()), "{notes:?}");
}

// A daemon that was killed before its close leaves a running proxy and a login. The next provisioning starts from nothing.
#[test]
fn the_provisioning_first_tears_down_what_an_earlier_run_left() {
    let fixture = Fixture::new();
    fixture.put(".jetbrains-central/tokens.enc", "stale");
    let login = fixture.login(SECRET.as_bytes());
    let mut central = fixture.central(STEP_TIMEOUT);

    let notes = notes_of(|note| {
        central.provision(&login, note).unwrap();
    });

    assert!(fixture.calls()[0].starts_with("proxy stop"), "{:?}", fixture.calls());
    assert!(
        !fixture.home().join(".jetbrains-central/tokens.enc").exists(),
        "the fake writes no token"
    );
    assert!(
        notes[0].starts_with("central provisioning: ended 1 leftover agent process(es): "),
        "{notes:?}"
    );
    assert!(
        notes[1].starts_with("central provisioning: removed what an earlier run left: "),
        "{notes:?}"
    );
}

// A developer host has no marker, so it has no guest account and no Central to provision or tear down.
#[test]
fn only_the_lane_daemon_of_a_linux_guest_has_a_guest_account() {
    let home = Path::new("/home/air");
    let secrets = Path::new("/dev/shm/air-run-secrets");
    assert_eq!(
        GuestAccount::of_lane_daemon(|name| (name == "HOME").then(|| "/home/air".to_owned()), true),
        Ok(None)
    );
    let blank = |name: &str| (name == RUN_SECRETS_VARIABLE).then(|| " ".to_owned());
    assert_eq!(GuestAccount::of_lane_daemon(blank, true), Ok(None));
    let on_mac = GuestAccount::of_lane_daemon(|name| environment(name, home, secrets), false).unwrap_err();
    assert!(on_mac.message.contains("a Linux guest only"), "{}", on_mac.message);
    let account = GuestAccount::of_lane_daemon(|name| environment(name, home, secrets), true)
        .unwrap()
        .unwrap();
    assert_eq!(account.home(), home);
    assert_eq!(account.input(LOGIN), secrets.join(LOGIN));
}

#[test]
fn an_account_home_that_is_not_absolute_below_the_root_is_refused() {
    for home in ["relative", "/"] {
        let refusal = GuestAccount::of_lane_daemon(|name| environment(name, Path::new(home), Path::new("/secrets")), true).unwrap_err();
        assert!(
            refusal.message.contains("an absolute directory below the root"),
            "{}",
            refusal.message
        );
    }
}

#[test]
fn a_proxy_on_another_port_fails_the_provisioning() {
    let fixture = Fixture::new();
    let login = fixture.login(SECRET.as_bytes());
    fixture.flag("status-port", "19517");

    let failure = fixture.central(STEP_TIMEOUT).provision(&login, &mut |_| {}).unwrap_err();

    assert_eq!(
        failure.message,
        "central provisioning: `central status` reports the proxy on port 19517, the pinned port is 19516"
    );
}

#[test]
fn the_status_parser_reads_the_port_of_a_running_proxy_through_the_colours() {
    assert_eq!(running_proxy_port(RUNNING_STATUS), Some(19516));
}

// The stopped text is an assumption: only a running proxy was observed. The parser decides by text alone.
#[test]
fn the_status_parser_answers_no_proxy_when_its_line_says_stopped() {
    assert_eq!(running_proxy_port(STOPPED_STATUS), None);
}

// The secret here has spaces, so no token-like run covers it: only its own redaction removes it.
#[test]
fn the_failure_line_is_the_last_stderr_line_with_the_secret_and_every_token_like_run_redacted() {
    let secret = "central login export of a developer";
    let stderr = format!("\u{1b}[31merror\u{1b}[0m first\nimport of {secret} failed, token abcdefghijklmnopqrstuvwxyz0123\n\n");

    assert_eq!(
        last_line(&stderr, &SecretParts::of(secret)).as_deref(),
        Some("import of <redacted> failed, token <redacted>")
    );
    assert_eq!(
        last_line(&format!("import of {secret} failed"), &SecretParts::default()).as_deref(),
        Some("import of central login export of a developer failed"),
        "no secret, no redaction of it"
    );
    assert_eq!(last_line("  \n", &SecretParts::of(secret)), None);
}

// A CLI that prints one field of an export prints no line of it whole: each line and each token is redacted.
#[test]
fn a_line_or_a_token_of_a_multi_line_export_is_redacted_wherever_it_appears() {
    let export = SecretParts::of("{\n  \"refresh\": \"r3fr3sh-t0ken-value\",\n  \"user\": \"someone at example\"\n}\n");

    assert_eq!(
        last_line("bad refresh r3fr3sh-t0ken-value for \"user\": \"someone at example\"", &export).as_deref(),
        Some("bad refresh <redacted> for <redacted>")
    );
    assert_eq!(
        last_line("short parts stay: refresh, user, {", &export).as_deref(),
        Some("short parts stay: refresh, user, {")
    );
}

// A child that never reads its stdin cannot block the write: the import is bounded like every other step.
#[test]
fn an_import_whose_child_never_reads_its_stdin_fails_within_its_bound() {
    let fixture = Fixture::new();
    fixture.flag("hang-import", "");
    let login = fixture.login(&vec![b'x'; 4 << 20]);
    let started = Instant::now();

    let failure = fixture
        .central_with(Duration::from_secs(1), BoundOnly("login import"))
        .provision(&login, &mut |_| {})
        .unwrap_err();

    assert_eq!(
        failure.message,
        "central provisioning failed at `central login import`: no exit within 1 s"
    );
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
}

// One path that cannot go must not keep the login: the rest is deleted, and every failure is refused at the end.
#[test]
fn the_teardown_deletes_past_a_path_it_cannot_delete_and_reports_it_at_the_end() {
    let fixture = Fixture::new();
    fixture.put(".codex/locked/kept", "fake");
    fixture.put(".jetbrains-central/tokens.enc", "fake");
    fs::create_dir_all(fixture.home().join(".junie/extensions")).unwrap();
    let locked = fixture.home().join(".codex/locked");
    fs::set_permissions(&locked, fs::Permissions::from_mode(0o000)).unwrap();
    let readable = fs::read_dir(&locked).is_ok();

    let result = fixture.central(STEP_TIMEOUT).tear_down(&mut |_| {});

    fs::set_permissions(&locked, fs::Permissions::from_mode(0o700)).unwrap();
    if readable {
        // This account reads a directory without permissions: it is root, and nothing here can fail.
        return;
    }
    let message = result.unwrap_err().message;
    assert!(message.contains("the teardown left"), "{message}");
    assert!(message.contains(&format!("{} (permission denied)", locked.display())), "{message}");
    assert!(!fixture.home().join(".jetbrains-central").exists(), "deleted before the failure");
    assert!(!fixture.home().join(".junie").exists(), "deleted after the failure");
}

// `.cache/jetbrains-central` below a linked `.cache` belongs to another directory, so the teardown leaves and refuses it.
#[test]
fn the_teardown_never_follows_a_link_on_the_way_to_an_entry() {
    let fixture = Fixture::new();
    let elsewhere = fixture.directory.path().join("elsewhere/jetbrains-central");
    fs::create_dir_all(&elsewhere).unwrap();
    fs::write(elsewhere.join("w"), "not the account's").unwrap();
    symlink(elsewhere.parent().unwrap(), fixture.home().join(".cache")).unwrap();
    fs::create_dir_all(fixture.home().join(".claude")).unwrap();

    let message = fixture.central(STEP_TIMEOUT).tear_down(&mut |_| {}).unwrap_err().message;

    let entry = fixture.home().join(".cache/jetbrains-central");
    let link = fixture.home().join(".cache");
    assert!(
        message.contains(&format!("{} (not followed through the link {})", entry.display(), link.display())),
        "{message}"
    );
    assert_eq!(fs::read_to_string(elsewhere.join("w")).unwrap(), "not the account's");
    assert!(!fixture.home().join(".claude").exists(), "the other entries go");
}

// The sweep never ends this process or an ancestor, the lane daemon and the supervisor among them, and a name that no
// process has ends nothing.
#[test]
fn the_process_sweep_spares_this_process_and_ends_no_unknown_name() {
    let own = i32::try_from(std::process::id()).unwrap();
    assert_eq!(ancestors().first(), Some(&own));
    assert_eq!(
        LiveAgentProcesses.stop(&["air-live-no-such-agent"], Duration::from_secs(1)),
        Vec::<String>::new()
    );
}

// The sweep finds a process by the file name of its executable, as `/proc/<pid>/exe` names it, and ends it.
#[cfg(target_os = "linux")]
#[test]
fn the_process_sweep_ends_a_process_of_the_account_by_the_name_of_its_executable() {
    let directory = tempfile::tempdir().unwrap();
    let agent = directory.path().join("air-live-fake-agent");
    fs::copy("/bin/sleep", &agent).unwrap();
    // A thread of another test that forks while the copy is open makes the exec fail with ETXTBSY for a moment.
    let mut spawned = Command::new(&agent).arg("30").spawn();
    for _ in 0..20 {
        match &spawned {
            Err(error) if error.raw_os_error() == Some(nix::errno::Errno::ETXTBSY as i32) => {
                thread::sleep(Duration::from_millis(50));
                spawned = Command::new(&agent).arg("30").spawn();
            }
            _ => break,
        }
    }
    let mut child = spawned.unwrap();
    thread::sleep(Duration::from_millis(200));
    if child.try_wait().unwrap().is_some() {
        // A multi-call `sleep` picks its applet by the name, and a copy under another name exits at once.
        return;
    }

    let ended = LiveAgentProcesses.stop(&["air-live-fake-agent"], Duration::from_secs(5));

    assert_eq!(ended, [format!("air-live-fake-agent pid {}", child.id())]);
    assert!(child.wait().unwrap().signal().is_some(), "the sweep killed it");
}
