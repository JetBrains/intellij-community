use std::net::TcpListener;
use std::os::unix::fs::PermissionsExt;
use std::time::Instant;

use pretty_assertions::assert_eq;
use yonder_link::proto::MAX_BINARY;

use super::*;
use crate::setup::plist_text;

/// A temporary home with a LaunchAgent whose binary is `bin/yonderd`, and a fake launchctl that writes its calls. The
/// port of the LaunchAgent takes connections until [`World::close_port`].
struct World {
    dir: tempfile::TempDir,
    home: Home,
    port: Option<TcpListener>,
}

impl World {
    fn new() -> Self {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let home = Home::new(dir.path().join("home"));
        fs::create_dir_all(dir.path().join("bin")).expect("the bin directory");
        let listener = TcpListener::bind("127.0.0.1:0").expect("a port");
        let port = listener.local_addr().expect("the address").port();
        let world = Self {
            dir,
            home,
            port: Some(listener),
        };
        let plist = world.home.launch_agent_file();
        fs::create_dir_all(plist.parent().expect("a parent")).expect("the LaunchAgents directory");
        let text = plist_text(&world.target(), port, &world.home.launchd_log_file(), "/usr/bin");
        fs::write(&plist, text).expect("the plist");
        script(&world.launchctl(), &format!("echo \"$*\" >>{}\n", world.calls_file().display()));
        world
    }

    /// Stops the listener on the port of the LaunchAgent.
    fn close_port(&mut self) {
        self.port = None;
    }

    fn outcome(&self) -> Option<Outcome> {
        serde_json::from_slice(&fs::read(self.home.upgrade_file()).ok()?).ok()
    }

    fn target(&self) -> PathBuf {
        self.dir.path().join("bin/yonderd")
    }

    fn launchctl(&self) -> PathBuf {
        self.dir.path().join("launchctl")
    }

    fn calls_file(&self) -> PathBuf {
        self.dir.path().join("launchctl-calls")
    }

    fn launchctl_calls(&self) -> String {
        fs::read_to_string(self.calls_file()).unwrap_or_default()
    }

    /// The file where a fake agent binary notes each watchdog that it runs.
    fn watchdogs_file(&self) -> PathBuf {
        self.dir.path().join("watchdogs")
    }

    /// The text of a fake agent binary of the version `name`: its `version` line has `features`, its `check` finds the
    /// LaunchAgent the same, and its `watchdog` notes its name.
    fn agent_text(&self, name: &str, features: &str) -> String {
        self.agent_text_with(name, features, "same")
    }

    /// [`World::agent_text`] whose `check` answers `plist` for the LaunchAgent.
    fn agent_text_with(&self, name: &str, features: &str, plist: &str) -> String {
        let line = format!(r#"{{"version":"{name}","schema_version":{SCHEMA_VERSION},"features":[{features}]"#);
        format!(
            "#!/bin/sh\ncase $1 in\n  version) echo '{line}}}' ;;\n  check) echo '{line},\"plist\":\"{plist}\"}}' ;;\n  watchdog) echo \"{name} $*\" >>{} ;;\nesac\n",
            self.watchdogs_file().display()
        )
    }

    /// The install of `staged` over the target, with a watchdog that waits 5 s.
    fn install(&self, staged: Staged) -> Result<Upgraded, Refusal> {
        self.install_forced(staged, false)
    }

    fn install_forced(&self, staged: Staged, force: bool) -> Result<Upgraded, Refusal> {
        let (target, launchctl) = (self.target(), self.launchctl());
        Install {
            home: &self.home,
            target: &target,
            launchctl: &launchctl,
            wait: Duration::from_secs(5),
            force,
        }
        .run(staged)
    }

    /// A staged binary of `text` for the target.
    fn stage(&self, text: &str) -> Staged {
        let mut staging = Staging::create(&self.target(), MAX_BINARY).expect("a staging");
        staging.write_all(text.as_bytes()).expect("written");
        staging.finish().expect("staged")
    }

    /// The names of the files beside the target.
    fn bin_files(&self) -> Vec<String> {
        let mut names: Vec<String> = fs::read_dir(self.dir.path().join("bin"))
            .expect("the bin directory")
            .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
            .collect();
        names.sort();
        names
    }
}

fn script(path: &Path, body: &str) {
    write_executable(path, &format!("#!/bin/sh\n{body}"));
}

fn write_executable(path: &Path, text: &str) {
    fs::write(path, text).expect("a binary");
    fs::set_permissions(path, Permissions::from_mode(0o755)).expect("executable");
}

const WITH_UPGRADE: &str = r#""config","upgrade""#;

fn sha256_of(text: &str) -> String {
    let mut digest = Sha256::default();
    digest.update(text.as_bytes());
    digest.hex()
}

/// Waits up to 10 s for `path` to hold a line.
fn wait_for_line(path: &Path) -> String {
    let started = Instant::now();
    loop {
        let text = fs::read_to_string(path).unwrap_or_default();
        if text.ends_with('\n') || started.elapsed() > Duration::from_secs(10) {
            return text;
        }
        thread::sleep(Duration::from_millis(20));
    }
}

#[test]
fn an_install_keeps_the_previous_binary_swaps_the_new_one_in_and_starts_the_watchdog_from_the_previous_one() {
    let world = World::new();
    let old = world.agent_text("old", WITH_UPGRADE);
    write_executable(&world.target(), &old);
    fs::create_dir_all(world.home.cache_dir()).expect("the cache");
    write_listening(&world.home, &sha256_of(&old)).expect("the listening file of the old serve");

    let new = world.agent_text("new", WITH_UPGRADE);
    let staged = world.stage(&new);
    staged.check(new.len() as u64, &sha256_of(&new)).expect("the binary of the request");
    let upgraded = world.install(staged).expect("installed");

    assert_eq!(
        upgraded,
        Upgraded {
            previous_sha256: sha256_of(&old),
            sha256: sha256_of(&new),
            version: "new".to_owned(),
        }
    );
    assert_eq!(fs::read_to_string(world.target()).expect("the target"), new);
    assert_eq!(fs::read_to_string(previous_of(&world.target())).expect("the previous binary"), old);
    assert_eq!(world.bin_files(), ["yonderd", "yonderd.previous"]);
    assert_eq!(
        fs::metadata(world.target()).expect("the target").permissions().mode() & 0o777,
        BINARY_MODE
    );
    assert!(
        read_listening(&world.home).is_none(),
        "only a later serve writes the listening file"
    );
    assert_eq!(
        wait_for_line(&world.watchdogs_file()),
        format!(
            "old watchdog {} --sha256 {} --wait-ms 5000 --launchctl {}\n",
            world.target().display(),
            sha256_of(&new),
            world.launchctl().display()
        )
    );
    assert_eq!(world.launchctl_calls(), "");
}

#[test]
fn a_previous_binary_without_the_feature_leaves_the_watchdog_to_the_new_binary() {
    let world = World::new();
    script(&world.target(), "echo '{\"version\":\"0.1.0\",\"schema_version\":2}'\n");
    let new = world.agent_text("new", WITH_UPGRADE);
    world.install(world.stage(&new)).expect("installed");
    let line = wait_for_line(&world.watchdogs_file());
    assert!(line.starts_with("new watchdog "), "{line}");
}

#[test]
fn a_binary_of_another_size_or_digest_is_refused_and_its_staged_file_goes() {
    let world = World::new();
    let mut staging = Staging::create(&world.target(), 3).expect("a staging");
    staging.write_all(b"abcdef").expect("the rest is dropped");
    let staged = staging.finish().expect("staged");
    let refusal = staged.check(3, &sha256_of("abc")).expect_err("6 bytes came");
    assert_eq!(refusal.code, BAD_BINARY);
    assert_eq!(refusal.message, "the binary has 6 bytes, not the 3 of the request");
    drop(staged);

    let staged = world.stage("abc");
    let refusal = staged.check(3, &"0".repeat(64)).expect_err("another digest");
    assert_eq!(refusal.code, BAD_BINARY);
    assert!(refusal.message.contains(&sha256_of("abc")), "{}", refusal.message);
    drop(staged);
    assert!(world.bin_files().is_empty(), "{:?}", world.bin_files());
}

#[test]
fn a_binary_that_does_not_run_here_as_an_upgradable_agent_is_refused_and_the_target_stays() {
    let world = World::new();
    let old = world.agent_text("old", WITH_UPGRADE);
    fs::write(world.target(), &old).expect("the target");
    let cases = [
        // macOS refuses the exec, or a shell runs the text and fails.
        ("\u{7f}ELF not a binary\n".to_owned(), " check --plist "),
        ("#!/bin/sh\necho broken >&2\nexit 3\n".to_owned(), "broken"),
        ("#!/bin/sh\necho no line\n".to_owned(), "printed no version line"),
        (
            "#!/bin/sh\necho '{\"version\":\"9\",\"schema_version\":1,\"features\":[\"upgrade\"]}'\n".to_owned(),
            "the schema 1",
        ),
        (world.agent_text("plain", r#""config""#), "has no feature upgrade"),
    ];
    for (text, said) in cases {
        let refusal = world.install(world.stage(&text)).expect_err("a bad binary");
        assert_eq!(refusal.code, BAD_BINARY, "{text}");
        assert!(refusal.message.contains(said), "{text}: {}", refusal.message);
        assert_eq!(fs::read_to_string(world.target()).expect("the target"), old);
        assert_eq!(world.bin_files(), ["yonderd"]);
    }
    for plist in ["changed", "unknown"] {
        let refusal = world
            .install(world.stage(&world.agent_text_with("other", WITH_UPGRADE, plist)))
            .expect_err("another LaunchAgent");
        assert_eq!(refusal.code, PLIST_CHANGED, "{plist}");
        assert!(refusal.message.ends_with("`yonder prepare` installs it"), "{}", refusal.message);
        assert_eq!(world.bin_files(), ["yonderd"]);
    }
}

#[test]
fn a_lower_version_needs_force_and_a_living_watchdog_refuses_the_next_upgrade() {
    let world = World::new();
    write_executable(&world.target(), &world.agent_text("2.0.0", WITH_UPGRADE));
    let older = world.agent_text("1.9.0", WITH_UPGRADE);
    let refusal = world.install(world.stage(&older)).expect_err("a downgrade");
    assert_eq!(refusal.code, DOWNGRADE);
    assert_eq!(
        refusal.message,
        "the binary 1.9.0 is older than the agent 2.0.0; `--force` installs it"
    );
    assert_eq!(world.bin_files(), ["yonderd"]);

    let lock = hold_watch_lock(&world.home).expect("the lock of a watchdog");
    let refusal = world.install_forced(world.stage(&older), true).expect_err("a watchdog lives");
    assert_eq!(refusal.code, UPGRADE_FAILED);
    assert!(
        refusal.message.starts_with("the watchdog of an earlier upgrade still waits"),
        "{}",
        refusal.message
    );
    drop(lock);
    let upgraded = world.install_forced(world.stage(&older), true).expect("forced");
    assert_eq!(upgraded.version, "1.9.0");
}

#[test]
fn only_three_numbers_above_zero_order_two_versions() {
    assert!(is_lower("1.9.0", "2.0.0"));
    assert!(is_lower("0.1.0", "0.1.1"));
    for (new, current) in [
        ("2.0.0", "1.9.0"),
        ("0.1.0", "0.1.0"),
        ("0.0.0", "0.1.0"),
        ("0.1.0", "0.0.0"),
        ("1.0", "2.0"),
        ("x", "1.0.0"),
    ] {
        assert!(!is_lower(new, current), "{new} {current}");
    }
}

#[test]
fn a_build_finds_the_launch_agent_of_its_setup_the_same() {
    let world = World::new();
    assert_eq!(plist_state(&world.home.launch_agent_file()), "same");
    let text = fs::read_to_string(world.home.launch_agent_file()).expect("the plist");
    fs::write(world.home.launch_agent_file(), text.replace("<true/>", "<false/>")).expect("another plist");
    assert_eq!(plist_state(&world.home.launch_agent_file()), "changed");
    fs::write(world.home.launch_agent_file(), "<plist/>").expect("a plist of another shape");
    assert_eq!(plist_state(&world.home.launch_agent_file()), "unknown");
    assert_eq!(plist_state(&world.dir.path().join("missing")), "unknown");
}

#[test]
fn the_watchdog_ends_when_a_live_serve_of_the_new_binary_listens() {
    let world = World::new();
    fs::create_dir_all(world.home.cache_dir()).expect("the cache");
    let sha256 = sha256_of("new");
    write_listening(&world.home, &sha256).expect("the listening file");
    assert_eq!(
        read_listening(&world.home),
        Some(Listening {
            sha256: sha256.clone(),
            pid: std::process::id(),
            version: env!("CARGO_PKG_VERSION").to_owned(),
        })
    );
    let mut said = Vec::new();
    let watched = watchdog(
        &world.home,
        &world.target(),
        &sha256,
        Duration::from_secs(5),
        &world.launchctl(),
        &mut |line| said.push(line.to_owned()),
    );
    assert_eq!(watched.expect("watched"), Watched::Listened(std::process::id()));
    let outcome = world.outcome().expect("the outcome");
    assert_eq!((outcome.sha256, outcome.outcome.as_str()), (sha256.clone(), "listened"));
    assert_eq!(
        said.last().map(String::as_str),
        Some(format!("watchdog: the binary {} listens with the pid {}", &sha256[..12], std::process::id()).as_str())
    );
    assert_eq!(world.launchctl_calls(), "");
}

#[test]
fn the_watchdog_restores_the_previous_binary_and_restarts_the_agent_when_no_live_serve_of_the_new_binary_listens() {
    let mut world = World::new();
    fs::create_dir_all(world.home.cache_dir()).expect("the cache");
    fs::write(world.target(), "new").expect("the new binary");
    fs::write(previous_of(&world.target()), "old").expect("the previous binary");
    // A pid that no process has: the child has ended and was reaped.
    let mut gone = Command::new("true").spawn().expect("true");
    let pid = gone.id();
    gone.wait().expect("reaped");
    let listening = Listening {
        sha256: sha256_of("new"),
        pid,
        version: "0.1.0".to_owned(),
    };
    write_private(&world.home.listening_file(), &serde_json::to_vec(&listening).expect("json")).expect("a stale listening file");

    let mut said = Vec::new();
    let watched = watchdog(
        &world.home,
        &world.target(),
        &sha256_of("new"),
        Duration::from_millis(300),
        &world.launchctl(),
        &mut |line| said.push(line.to_owned()),
    );
    assert_eq!(watched.expect("watched"), Watched::RolledBack { listens: true });
    assert_eq!(world.outcome().expect("the outcome").outcome, "rolled_back");
    assert_eq!(fs::read_to_string(world.target()).expect("the target"), "old");
    assert_eq!(world.bin_files(), ["yonderd"]);
    let (_, service) = service();
    assert_eq!(world.launchctl_calls(), format!("kickstart -k {service}\n"));
    assert!(
        said.iter().any(|line| line.contains("so the previous binary comes back")),
        "{said:?}"
    );

    // A live serve of the new binary whose port takes no connection does not count either, and the previous agent
    // that does not come back is in the outcome.
    world.close_port();
    fs::rename(world.target(), previous_of(&world.target())).expect("the previous binary");
    fs::write(world.target(), "new").expect("the new binary");
    write_listening(&world.home, &sha256_of("new")).expect("the listening file");
    let watched = watchdog(
        &world.home,
        &world.target(),
        &sha256_of("new"),
        Duration::from_millis(300),
        &world.launchctl(),
        &mut |_| {},
    );
    assert_eq!(watched.expect("watched"), Watched::RolledBack { listens: false });
    assert_eq!(world.outcome().expect("the outcome").outcome, "rolled_back_not_listening");
    assert_eq!(fs::read_to_string(world.target()).expect("the target"), "old");
}

#[test]
fn the_binary_of_the_launch_agent_is_the_program_of_its_plist() {
    let world = World::new();
    assert_eq!(launch_agent(&world.home).expect("the LaunchAgent").exe, world.target());
    fs::write(world.home.launch_agent_file(), "<plist/>").expect("a plist of another shape");
    let refusal = launch_agent(&world.home).expect_err("another shape");
    assert_eq!(refusal.code, UPGRADE_FAILED);
    assert!(
        refusal.message.contains("not of the shape that `setup` writes"),
        "{}",
        refusal.message
    );
    fs::remove_file(world.home.launch_agent_file()).expect("no plist");
    let refusal = launch_agent(&world.home).expect_err("no LaunchAgent");
    assert_eq!(refusal.code, UPGRADE_FAILED);
    assert!(refusal.message.contains("`yonder prepare` writes it"), "{}", refusal.message);
}
