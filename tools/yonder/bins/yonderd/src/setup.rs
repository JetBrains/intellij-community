//! `yonderd setup`: the client runs it over ssh while the user is at the host. It creates the identity of the agent,
//! adds the client to the allow list, writes the LaunchAgent, and starts or restarts `serve` under launchd. On a host
//! without launchd it does not start the agent. It also moves each lease file of `remote.sh` of BT into the lease
//! directory of the agent, when the agent has no file of that holder, so a warm lease of `bt --host` stays warm.

use std::fs;
use std::io::Write;
use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{Duration, Instant};

use serde::Serialize;
use yonder_link::{AllowList, Fingerprint, Identity};

use crate::child;
use crate::files::{private_dir, write_private};
use crate::home::{Home, LAUNCHD_LABEL};
use crate::lease;
use crate::refused::{AGENT_ERROR, LAUNCHD_FAILED, Refusal, refused};

/// The deadline of each launchctl call.
const LAUNCHCTL: Duration = Duration::from_secs(30);

/// How long a reload waits for launchd to unload the old service.
const UNLOAD_WAIT: Duration = Duration::from_secs(10);

/// What `setup` takes.
#[derive(Clone, Debug)]
pub(crate) struct Setup<'a> {
    pub(crate) client: Fingerprint,
    pub(crate) name: &'a str,
    pub(crate) port: u16,
    /// The agent binary that launchd runs.
    pub(crate) exe: &'a Path,
    /// The launchctl program. A test gives a fake one.
    pub(crate) launchctl: &'a Path,
    /// The `PATH` of the setup process. The agent gets it after the usual tool directories.
    pub(crate) path: &'a str,
    /// How long `setup` waits for the started agent to listen on its port. Zero does not wait, for a test with a fake
    /// launchctl.
    pub(crate) listen_wait: Duration,
}

/// The line that `setup` prints on stdout.
#[derive(Debug, PartialEq, Eq, Serialize)]
pub(crate) struct SetupLine {
    pub(crate) fingerprint: String,
    pub(crate) port: u16,
    /// `bootstrapped`, `restarted`, or `none` on a host without launchd.
    pub(crate) launchd: &'static str,
}

/// What launchd needs to run the current plist.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum LaunchdStep {
    /// The service is not loaded: load it.
    Bootstrap,
    /// The service is loaded with another plist: unload it, then load it.
    Reload,
    /// The service is loaded with this plist: restart it, so it runs the binary on disk.
    Kickstart,
}

impl LaunchdStep {
    pub(crate) const fn of(loaded: bool, plist_changed: bool) -> Self {
        match (loaded, plist_changed) {
            (false, _) => Self::Bootstrap,
            (true, true) => Self::Reload,
            (true, false) => Self::Kickstart,
        }
    }
}

pub(crate) fn setup(home: &Home, setup: &Setup<'_>, out: &mut dyn Write) -> Result<(), Refusal> {
    let identity_dir = home.identity_dir();
    let identity = Identity::load_or_create(&identity_dir).map_err(|error| {
        refused(
            AGENT_ERROR,
            format!("cannot load the identity in {}: {error}", identity_dir.display()),
        )
    })?;
    let private = |dir: PathBuf| private_dir(&dir).map_err(|error| refused(AGENT_ERROR, format!("cannot make {}: {error}", dir.display())));
    private(identity_dir)?;
    private(home.cache_dir())?;
    lease::migrate(&home.bt_remote_leases_dir(), &home.leases_dir())
        .map_err(|error| refused(AGENT_ERROR, format!("cannot move the lease files of bt --host: {error}")))?;
    let allow_file = home.allow_file();
    let mut allow = AllowList::read(&allow_file).map_err(|error| refused(AGENT_ERROR, error.to_string()))?;
    if allow.add(setup.client, setup.name) {
        write_private(&allow_file, allow.text().as_bytes())
            .map_err(|error| refused(AGENT_ERROR, format!("cannot write {}: {error}", allow_file.display())))?;
    }
    let launchd = if cfg!(target_os = "macos") {
        start_launchd(home, setup)?
    } else {
        "none"
    };
    // launchd starts the agent later than launchctl returns, and later still on a loaded host or after a restart that
    // its throttle holds back. So the client gets the line only when the agent takes connections.
    if launchd != "none" && !setup.listen_wait.is_zero() {
        wait_for_listen(setup.port, setup.listen_wait).map_err(|message| refused(LAUNCHD_FAILED, message))?;
    }
    let line = SetupLine {
        fingerprint: identity.fingerprint().to_string(),
        port: setup.port,
        launchd,
    };
    let json = serde_json::to_string(&line).map_err(|error| refused(AGENT_ERROR, error.to_string()))?;
    writeln!(out, "{json}").map_err(|error| refused(AGENT_ERROR, format!("cannot write stdout: {error}")))
}

/// Waits until a TCP connect to the port on the loopback address succeeds.
pub(crate) fn wait_for_listen(port: u16, wait: Duration) -> Result<(), String> {
    let address = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
    let started = Instant::now();
    loop {
        if TcpStream::connect_timeout(&address, Duration::from_secs(1)).is_ok() {
            return Ok(());
        }
        if started.elapsed() >= wait {
            return Err(format!(
                "launchd started the agent, but nothing listens on {address} after {} s; read ~/Library/Logs/yonderd.log",
                wait.as_secs()
            ));
        }
        std::thread::sleep(Duration::from_millis(200));
    }
}

fn start_launchd(home: &Home, setup: &Setup<'_>) -> Result<&'static str, Refusal> {
    let plist_file = home.launch_agent_file();
    let log_file = home.launchd_log_file();
    let plist = plist_text(setup.exe, setup.port, &log_file, &agent_path(home.dir(), setup.path));
    let changed = fs::read_to_string(&plist_file).ok().as_deref() != Some(plist.as_str());
    if changed {
        let written = [plist_file.parent(), log_file.parent()]
            .into_iter()
            .flatten()
            .try_for_each(fs::create_dir_all)
            .and_then(|()| write_private(&plist_file, plist.as_bytes()));
        written.map_err(|error| refused(AGENT_ERROR, format!("cannot write {}: {error}", plist_file.display())))?;
    }
    let (domain, service) = service();
    let launchctl = |args: &[&str]| launchctl(setup.launchctl, args);
    let plist_arg = plist_file.to_string_lossy();
    let loaded = || launchctl(&["print", &service]).is_ok();
    let step = LaunchdStep::of(loaded(), changed);
    let started = match step {
        LaunchdStep::Kickstart => launchctl(&["kickstart", "-k", &service]).map(|()| "restarted"),
        LaunchdStep::Reload => launchctl(&["bootout", &service]).and_then(|()| {
            let unloading = Instant::now();
            while loaded() && unloading.elapsed() < UNLOAD_WAIT {
                std::thread::sleep(Duration::from_millis(200));
            }
            launchctl(&["bootstrap", &domain, &plist_arg]).map(|()| "bootstrapped")
        }),
        LaunchdStep::Bootstrap => launchctl(&["bootstrap", &domain, &plist_arg]).map(|()| "bootstrapped"),
    };
    started.map_err(|message| refused(LAUNCHD_FAILED, message))
}

/// The launchd domain of the user, `gui/<uid>`, and the service of the agent in it.
pub(crate) fn service() -> (String, String) {
    // SAFETY: getuid has no preconditions and cannot fail.
    let uid = unsafe { libc::getuid() };
    let domain = format!("gui/{uid}");
    let service = format!("{domain}/{LAUNCHD_LABEL}");
    (domain, service)
}

/// Runs `launchctl <args>` with the program `program` under the deadline of a launchctl call.
pub(crate) fn launchctl(program: &Path, args: &[&str]) -> Result<(), String> {
    let output = child::run(Command::new(program).args(args), LAUNCHCTL, &child::never).map_err(|error| error.to_string())?;
    if output.status.success() {
        Ok(())
    } else {
        Err(format!(
            "launchctl {} failed: {} ({})",
            args.join(" "),
            output.diagnostic(),
            output.status
        ))
    }
}

/// The `PATH` of the agent: the tool directories of the user and of Homebrew first, then `current`, then the system
/// directories, each once. launchd starts a job with only the system directories, and bd and git live elsewhere.
pub(crate) fn agent_path(home: &Path, current: &str) -> String {
    let local_bin = home.join(".local/bin").to_string_lossy().into_owned();
    let mut entries: Vec<&str> = Vec::new();
    let first = [local_bin.as_str(), "/opt/homebrew/bin", "/usr/local/bin"];
    let last = ["/usr/bin", "/bin", "/usr/sbin", "/sbin"];
    for entry in first.into_iter().chain(current.split(':')).chain(last) {
        if !entry.is_empty() && !entries.contains(&entry) {
            entries.push(entry);
        }
    }
    entries.join(":")
}

/// The LaunchAgent: `serve --port <port>` at login and again after each exit, with its output in `log`.
/// `AbandonProcessGroup` keeps a run alive when launchd restarts the agent; each supervisor has a session of its own
/// too.
pub(crate) fn plist_text(exe: &Path, port: u16, log: &Path, path: &str) -> String {
    let exe = xml_escape(&exe.to_string_lossy());
    let log = xml_escape(&log.to_string_lossy());
    let path = xml_escape(path);
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>{LAUNCHD_LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>{exe}</string>
    <string>serve</string>
    <string>--port</string>
    <string>{port}</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key>
    <string>{path}</string>
  </dict>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>AbandonProcessGroup</key>
  <true/>
  <key>StandardOutPath</key>
  <string>{log}</string>
  <key>StandardErrorPath</key>
  <string>{log}</string>
</dict>
</plist>
"#
    )
}

fn xml_escape(text: &str) -> String {
    text.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;")
}

/// The values of a plist of [`plist_text`]: what launchd runs, and how.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct PlistValues {
    pub(crate) exe: PathBuf,
    pub(crate) port: u16,
    pub(crate) log: PathBuf,
    pub(crate) path: String,
}

impl PlistValues {
    /// The values of `plist`, or `None` when it is not of the shape of [`plist_text`]: the program, `serve`, `--port`
    /// and the port as its arguments, a `PATH` and a log.
    pub(crate) fn parse(plist: &str) -> Option<Self> {
        let after = |key: &str| plist.split_once(&format!("<key>{key}</key>")).map(|(_, rest)| rest);
        let (arguments, _) = after("ProgramArguments")?.split_once("</array>")?;
        let arguments: Vec<String> = arguments.split("<string>").skip(1).filter_map(string_value).collect();
        let [exe, serve, flag, port] = arguments.as_slice() else {
            return None;
        };
        if exe.is_empty() || serve != "serve" || flag != "--port" {
            return None;
        }
        let first_string = |key: &str| after(key)?.split_once("<string>").and_then(|(_, rest)| string_value(rest));
        Some(Self {
            exe: PathBuf::from(exe),
            port: port.parse().ok()?,
            log: PathBuf::from(first_string("StandardOutPath")?),
            path: first_string("PATH")?,
        })
    }

    /// The plist that `setup` writes for these values.
    pub(crate) fn text(&self) -> String {
        plist_text(&self.exe, self.port, &self.log, &self.path)
    }
}

/// The text of a `<string>` element from its start up to `</string>`, unescaped.
fn string_value(rest: &str) -> Option<String> {
    let (value, _) = rest.split_once("</string>")?;
    Some(value.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"))
}

#[cfg(test)]
mod tests;
