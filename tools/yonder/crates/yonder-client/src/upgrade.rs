//! Updates the agent of a host over the link, without ssh, so a background session can do it:
//!
//! 1. The hello names the platform, the features and the binary of the agent.
//! 2. The local binary is `--agent`, or the build of [`Client::build_agent`]. It must be of the platform of the host, and
//!    the binary that the agent runs already is current, unless `force`.
//! 3. An agent with [`feature::UPGRADE`] gets [`Request::Upgrade`] with the binary.
//! 4. An agent without the feature cannot read the request. The binary then goes to [`BOOTSTRAP_DIR`] of the working
//!    tree, a run carries it to a slot of the host, and the new binary installs itself there with `yonderd upgrade`,
//!    with the same checks and the same watchdog. The directory goes after the run. So the first upgrade of such a host
//!    works without ssh too.
//! 5. The client connects again until the hello names the new binary. A hello of the previous binary at the end means
//!    that the watchdog restored it.

use std::fs;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, Instant};

use serde::Deserialize;
use yonder_link::proto::{Reply, Request, feature};

use crate::config::check_alias;
use crate::host::AgentInfo;
use crate::prepare::sha256_hex;
use crate::process::Call;
use crate::run::{RunOptions, RunRequest};
use crate::{Client, Refusal, code, no_monorepo, refused, size_text};

/// The directory of the working tree that carries the binary of a bootstrap run: untracked, and not ignored, so the
/// commit of the working tree holds it.
pub const BOOTSTRAP_DIR: &str = "yonder-upgrade";

/// The command of a bootstrap run, in the slot root on the host, with the binary as `$0`. A client on Windows writes no
/// mode, so the command gives the binary its mode first.
pub const BOOTSTRAP_COMMAND: &str = r#"chmod 0755 "$0" && exec "$0" upgrade "$0" "$@""#;

/// The deadline of the local git call of a bootstrap.
const GIT_DEADLINE: Duration = Duration::from_secs(30);

/// The longest wait between two hellos after an upgrade.
const MAX_POLL: Duration = Duration::from_secs(5);

/// What `upgrade` does.
#[derive(Clone, Copy, Debug)]
pub struct UpgradeRequest<'a> {
    /// The alias of the host file.
    pub alias: &'a str,
    /// A local `yonderd` binary for the host, or `None` for the Bazel build in `checkout`.
    pub agent: Option<&'a Path>,
    /// The root of the local checkout of the monorepo: the build of the agent, and the commit of a bootstrap run.
    pub checkout: Option<&'a Path>,
    /// The lease holder of a bootstrap run.
    pub holder: &'a str,
    /// Send the binary that the agent runs already too, and let the agent take a binary of a lower version.
    pub force: bool,
}

/// How the new binary came to the host.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum UpgradeWay {
    /// The agent runs the binary already.
    Current,
    /// [`Request::Upgrade`] over the link.
    Request,
    /// A bootstrap run with this id, which carried the binary in [`BOOTSTRAP_DIR`].
    Bootstrap(String),
}

/// What `upgrade` did.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Upgraded {
    pub way: UpgradeWay,
    /// The binary that ran before, when the agent named it.
    pub previous_sha256: Option<String>,
    pub sha256: String,
    pub version: String,
    /// The hello of the new agent.
    pub agent: AgentInfo,
}

/// The line of `yonderd upgrade`, and the fields of [`Reply::Upgraded`].
#[derive(Deserialize)]
struct Installed {
    previous_sha256: String,
    sha256: String,
}

impl Client<'_> {
    pub fn upgrade(&self, request: &UpgradeRequest<'_>) -> Result<Upgraded, Refusal> {
        let alias = request.alias;
        check_alias(alias)?;
        let mut host = self.host(alias)?;
        let (channel, info) = host.connect()?;
        channel.close();
        if info.os != "macos" {
            return Err(refused(
                code::AGENT_FAILED,
                format!(
                    "the agent on {alias} runs on {}, and only launchd on macOS starts an upgraded agent: `yonder prepare {alias}` installs a new one",
                    info.os
                ),
            ));
        }
        let agent = match (request.agent, request.checkout) {
            (Some(agent), _) => agent.to_path_buf(),
            (None, Some(checkout)) => self.build_agent(checkout)?,
            (None, None) => return Err(no_monorepo("the working directory is in no git checkout")),
        };
        let binary = fs::read(&agent).map_err(|error| {
            refused(
                code::AGENT_FAILED,
                format!("cannot read the agent binary {}: {error}", agent.display()),
            )
        })?;
        check_platform(&binary, &agent, &info)?;
        let sha256 = sha256_hex(&binary);
        if !request.force && host.binary_sha256() == Some(sha256.as_str()) {
            return Ok(Upgraded {
                way: UpgradeWay::Current,
                previous_sha256: Some(sha256.clone()),
                sha256,
                version: info.version.clone(),
                agent: info,
            });
        }
        if !host.features().iter().any(|known| known == feature::UPGRADE) {
            return self.bootstrap(request, &binary);
        }
        (self.say)(&format!("yonder: sending yonderd ({}) to {alias}", size_text(binary.len() as u64)));
        let (mut channel, _) = host.connect()?;
        let upgrade = Request::Upgrade {
            size: binary.len() as u64,
            sha256,
            force: request.force,
        };
        channel
            .send(&upgrade)
            .and_then(|()| channel.send_stream(&mut binary.as_slice(), &mut |_| {}))
            .map_err(|error| host.link_refusal(&error))?;
        let installed = match host.recv(&mut channel, self.timing.read)? {
            Reply::Upgraded {
                previous_sha256, sha256, ..
            } => Installed { previous_sha256, sha256 },
            Reply::Refused { code, message } => return Err(host.agent_refusal(code, &message)),
            reply => return Err(host.unexpected(&reply)),
        };
        channel.close();
        self.wait_for_new(alias, UpgradeWay::Request, installed)
    }

    /// The upgrade of an agent without [`feature::UPGRADE`]: a run that carries `binary` in [`BOOTSTRAP_DIR`] of the
    /// working tree, and runs `yonderd upgrade` of it in a slot of the host.
    fn bootstrap(&self, request: &UpgradeRequest<'_>, binary: &[u8]) -> Result<Upgraded, Refusal> {
        let alias = request.alias;
        let checkout = request.checkout.ok_or_else(|| {
            refused(
                code::USAGE,
                format!(
                    "the working directory is in no git checkout, and the agent on {alias} knows no upgrade request, so a run of a checkout carries the binary: run `yonder upgrade {alias}` in a checkout of the repository of the host file"
                ),
            )
        })?;
        let shipped = Shipped::write(self, checkout, binary)?;
        (self.say)(&format!(
            "yonder: the agent on {alias} knows no upgrade request, so a run carries the binary in {BOOTSTRAP_DIR}/ of this working tree and installs it with `yonderd upgrade`"
        ));
        let mut command: Vec<String> = ["/bin/sh", "-c", BOOTSTRAP_COMMAND, &format!("{BOOTSTRAP_DIR}/yonderd")]
            .into_iter()
            .map(str::to_owned)
            .collect();
        if request.force {
            command.push("--force".to_owned());
        }
        let run = RunRequest {
            checkout,
            repo: None,
            holder: request.holder,
            command: &command,
            fetch: &[],
        };
        let options = RunOptions {
            working_tree: true,
            nice: None,
            outputs: false,
            apply: false,
            repo_by_name: false,
        };
        // The line of `yonderd upgrade` comes in the stdout of the outcome, not in the stdout of this client.
        let collecting = Client {
            config: self.config.clone(),
            stdout: None,
            ..*self
        };
        let outcome = collecting.run_with(alias, &run, &options);
        drop(shipped);
        let outcome = outcome?;
        if outcome.code != 0 {
            return Err(refused(
                code::AGENT_FAILED,
                format!(
                    "the bootstrap run {} on {alias} exited with {}; `yonder attach {alias} {}` shows its output again",
                    outcome.run, outcome.code, outcome.run
                ),
            ));
        }
        let installed: Installed = outcome
            .stdout
            .lines()
            .rev()
            .find_map(|line| serde_json::from_str(line).ok())
            .ok_or_else(|| {
                refused(
                    code::AGENT_FAILED,
                    format!(
                        "the bootstrap run {} on {alias} printed no line of `yonderd upgrade`: {}",
                        outcome.run,
                        outcome.stdout.trim()
                    ),
                )
            })?;
        self.wait_for_new(alias, UpgradeWay::Bootstrap(outcome.run), installed)
    }

    /// Connects again until the hello names the new binary, up to the upgrade time. Refuses with
    /// [`code::ROLLED_BACK`] when the previous binary answers at the end.
    fn wait_for_new(&self, alias: &str, way: UpgradeWay, installed: Installed) -> Result<Upgraded, Refusal> {
        let short = |sha256: &str| sha256.get(..12).unwrap_or(sha256).to_owned();
        (self.say)(&format!(
            "yonder: {alias} installed yonderd {}; waiting for the new agent to answer",
            short(&installed.sha256)
        ));
        let started = Instant::now();
        let mut backoff = self.timing.first_backoff;
        let mut answered: Option<Option<String>> = None;
        let mut lost = None;
        loop {
            let mut host = self.host(alias)?;
            match host.connect() {
                Ok((channel, info)) => {
                    channel.close();
                    if host.binary_sha256() == Some(installed.sha256.as_str()) {
                        return Ok(Upgraded {
                            way,
                            previous_sha256: Some(installed.previous_sha256),
                            sha256: installed.sha256,
                            version: info.version.clone(),
                            agent: info,
                        });
                    }
                    answered = Some(host.binary_sha256().map(str::to_owned));
                }
                Err(refusal)
                    if [code::UNREACHABLE, code::LINK, code::TIMED_OUT]
                        .iter()
                        .any(|lost| refusal.code == *lost) =>
                {
                    lost = Some(refusal);
                }
                Err(refusal) => return Err(refusal),
            }
            if started.elapsed() >= self.timing.upgrade {
                break;
            }
            thread::sleep(backoff);
            backoff = (backoff * 2).min(MAX_POLL.min(self.timing.max_backoff));
        }
        let waited = self.timing.upgrade.as_secs();
        let log =
            format!("~/.cache/yonderd/upgrade.json on {alias} has the outcome of the watchdog, and ~/Library/Logs/yonderd.log its lines");
        Err(match answered {
            Some(Some(binary)) if binary == installed.previous_sha256 => refused(
                code::ROLLED_BACK,
                format!(
                    "the new agent {} did not listen, so the watchdog brought back the previous agent {}; {log}",
                    short(&installed.sha256),
                    short(&binary)
                ),
            ),
            Some(binary) => refused(
                code::AGENT_FAILED,
                format!(
                    "the agent on {alias} answers with the binary {}, not the new {}, {waited} s after the upgrade; {log}",
                    binary.as_deref().map_or_else(|| "of an older build".to_owned(), short),
                    short(&installed.sha256)
                ),
            ),
            None => {
                let said = lost.map_or_else(String::new, |refusal| format!(": {}", refusal.message));
                refused(
                    code::UNREACHABLE,
                    format!("no agent answers on {alias} {waited} s after the upgrade{said}; {log}"),
                )
            }
        })
    }
}

/// The binary in [`BOOTSTRAP_DIR`] of a checkout for one bootstrap run. The directory goes when the value drops.
struct Shipped {
    dir: PathBuf,
}

impl Shipped {
    /// Writes `binary` as `yonderd` into a new [`BOOTSTRAP_DIR`] of `checkout`. Refuses a directory
    /// that is there already, and a path that git ignores, because the commit of the working tree would leave it out.
    fn write(client: &Client<'_>, checkout: &Path, binary: &[u8]) -> Result<Self, Refusal> {
        let dir = checkout.join(BOOTSTRAP_DIR);
        if dir.exists() {
            return Err(refused(
                code::USAGE,
                format!("{} is there already: remove it, and run the upgrade again", dir.display()),
            ));
        }
        let path = format!("{BOOTSTRAP_DIR}/yonderd");
        let checkout_text = checkout.to_string_lossy();
        let ignored = client.child(&Call::new(
            "git",
            &["-C", &checkout_text, "check-ignore", "-q", "--", &path],
            GIT_DEADLINE,
        ));
        match ignored.code {
            Some(1) => {}
            Some(0) => {
                return Err(refused(
                    code::USAGE,
                    format!("git ignores {path} in {checkout_text}, so a run would not carry the binary"),
                ));
            }
            _ => {
                return Err(refused(
                    code::GIT_FAILED,
                    format!("`git check-ignore {path}` failed: {}", ignored.said()),
                ));
            }
        }
        let shipped = Self { dir };
        let file = shipped.dir.join("yonderd");
        fs::create_dir(&shipped.dir)
            .and_then(|()| fs::write(&file, binary))
            .map_err(|error| refused(code::CONFIG, format!("cannot write {}: {error}", file.display())))?;
        Ok(shipped)
    }
}

impl Drop for Shipped {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.dir);
    }
}

/// Refuses a binary that is not of the platform of the host. A universal Mach-O binary runs on both architectures.
fn check_platform(binary: &[u8], path: &Path, host: &AgentInfo) -> Result<(), Refusal> {
    match binary_platform(binary) {
        Platform::Universal => Ok(()),
        Platform::Of(os, arch) if os == host.os && arch == host.arch => Ok(()),
        Platform::Of(os, arch) => Err(refused(
            code::AGENT_FAILED,
            format!(
                "{} is a binary of {os} {arch}, and the host runs {} {}",
                path.display(),
                host.os,
                host.arch
            ),
        )),
        Platform::Unknown => Err(refused(
            code::AGENT_FAILED,
            format!("{} is no Mach-O or ELF binary of aarch64 or x86_64", path.display()),
        )),
    }
}

/// The platform of a binary from its header, in the names of `std::env::consts`.
#[derive(Debug, PartialEq, Eq)]
enum Platform {
    Of(&'static str, &'static str),
    Universal,
    Unknown,
}

fn binary_platform(binary: &[u8]) -> Platform {
    let u32_at = |at: usize| {
        binary
            .get(at..at + 4)
            .map(|bytes| u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    };
    let u16_at = |at: usize| binary.get(at..at + 2).map(|bytes| u16::from_le_bytes([bytes[0], bytes[1]]));
    match binary.get(..4) {
        Some([0xcf, 0xfa, 0xed, 0xfe]) => match u32_at(4) {
            Some(0x0100_000c) => Platform::Of("macos", "aarch64"),
            Some(0x0100_0007) => Platform::Of("macos", "x86_64"),
            _ => Platform::Unknown,
        },
        Some([0xca, 0xfe, 0xba, 0xbe]) => Platform::Universal,
        Some([0x7f, b'E', b'L', b'F']) => match u16_at(18) {
            Some(0xb7) => Platform::Of("linux", "aarch64"),
            Some(0x3e) => Platform::Of("linux", "x86_64"),
            _ => Platform::Unknown,
        },
        _ => Platform::Unknown,
    }
}

#[cfg(test)]
mod tests;
