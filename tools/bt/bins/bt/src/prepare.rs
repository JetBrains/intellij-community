//! `bt --host <ssh alias> --prepare`: makes another machine of the user ready for `bt --host`, and says what is
//! missing. The user runs it in a terminal. Each step is idempotent and reports `ok`, `done` or `missing`. A tool that
//! only some lanes need reports `absent` instead of `missing`. With `--dry-run` it changes nothing.
//!
//! 1. ssh: the alias answers in batch mode.
//! 2. The main repository on the host: created and given the master history when it is missing, and moved to the
//!    local master when its master is clean and an ancestor.
//! 3. `bd`: the local binary, copied when the host has another build, and `~/.local/bin` on the PATH of `~/.zshenv`.
//!    The SHA-256 of the binary tells the build, because a new build can keep its version.
//! 4. Tools: git, Xcode, rustup, docker, tmux. Reported only, never installed.
//! 5. The agent: `yonderd`, built here with Bazel, goes to the host through `yonder prepare` of `yonder-client`. It
//!    adds this client to the allow list of the agent and writes the host file, so a run needs no ssh. With
//!    `--dry-run`, the step only asks the agent for its hello.
//! 6. Credentials: the netrc entries of the Bazel services, the Maven servers of the Space repositories, and the
//!    expiry marker of the token of each server, which `IJPrivatePackagesAuthorizer` reads. An expired or missing local
//!    marker asks for a renewed local token first. The copy needs a yes at the terminal or `--yes`, and it
//!    refuses without a terminal, so an agent cannot start it. Then the remote cache must answer 404, not 401.
//! 7. With `--take-lease`, the warm lease of this checkout, so the first run does not wait for `bd lease get`.

use std::ffi::OsString;
use std::fmt::Write as _;
use std::path::PathBuf;

use bt_core::exit;
use bt_core::lanes::bazel_command;
use bt_core::runtime::{Platform, Runtime, STOPPED_IDLE, SpawnResult, repo_file};
use yonder_client::PrepareRequest;
use yonder_link::DEFAULT_PORT;

use crate::credentials::{
    Format, NETRC, NetrcFormat, REMOTE_CACHE_OPTION, SETTINGS_XML, SettingsFormat, copy_entries, service_hosts, services,
    space_repository_ids,
};
use crate::digest::tail_lines;
use crate::execute::Outcome;
use crate::remote::{DEFAULT_HOST_REPO, Host, HostLink, holder};
use crate::ssh::{COPY_IDLE, host_path_word, host_repo, push_url, remote_call, scp, ssh, ssh_git_config, watched_git};

/// What `--prepare` may do.
pub(crate) struct Prepare {
    /// `false` for `--dry-run`: check only.
    pub(crate) change: bool,
    /// `--yes`: copy the credentials without the question.
    pub(crate) yes: bool,
    /// `--take-lease`.
    pub(crate) take_lease: bool,
}

/// The outcome of one step.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Status {
    Ok,
    Done,
    Missing,
    /// An optional tool that the host does not have.
    Absent,
}

impl Status {
    const fn word(self) -> &'static str {
        match self {
            Self::Ok => "ok",
            Self::Done => "done",
            Self::Missing => "missing",
            Self::Absent => "absent",
        }
    }
}

/// The lines of the report, in step order.
#[derive(Default)]
struct Report {
    lines: Vec<(Status, String, String)>,
}

impl Report {
    fn add(&mut self, status: Status, step: &str, detail: impl Into<String>) {
        self.lines.push((status, step.to_owned(), detail.into()));
    }

    fn has_missing(&self) -> bool {
        self.lines.iter().any(|(status, _, _)| *status == Status::Missing)
    }

    fn text(&self) -> String {
        let mut text = String::new();
        for (status, step, detail) in &self.lines {
            let _ = writeln!(text, "{:<8}{step}: {detail}", status.word());
        }
        text.trim_end().to_owned()
    }
}

/// The Bazel target of the agent, from a community root. An ultimate root names it in the module `@community`.
const AGENT_TARGET: &str = "//tools/yonder:yonderd";

/// The base name of the agent binary.
const AGENT_NAME: &str = "yonderd";

/// How long the agent build may write nothing before it is stopped. The runtime bounds a quiet child, not a whole
/// one; a Bazel build that writes progress goes on.
const AGENT_BUILD_LIMIT: std::time::Duration = std::time::Duration::from_secs(30 * 60);

/// How much of a failed agent build the report shows.
const BUILD_TAIL_LINES: usize = 20;

/// The empty key that the remote cache check asks for: a valid key that no cache holds.
const ABSENT_CACHE_KEY: &str = "0000000000000000000000000000000000000000000000000000000000000000";

/// The directory of the token expiry markers of `IJPrivatePackagesAuthorizer`, relative to the home directory on
/// macOS. Each marker is an empty file whose modification time is the expiry of one token.
const TOKEN_MARKERS: &str = "Library/Caches/JetBrains/private-packages-authorizer/token-expiration";

/// The tools of the tool step, and whether a run needs each one. Docker and tmux serve only some UI lanes.
const TOOLS: [(&str, &str, bool); 5] = [
    ("git", "git", true),
    ("Xcode", "xcode-select -p", true),
    ("rustup", "rustup", false),
    ("docker", "docker", false),
    ("tmux", "tmux", false),
];

pub(crate) fn prepare_host(host: &Host<'_>, prepare: &Prepare, runtime: &dyn Runtime, link: &dyn HostLink) -> Outcome {
    let mut report = Report::default();
    if check_ssh(runtime, host.alias, &mut report) {
        prepare_repository(runtime, host, prepare, &mut report);
        prepare_bd(runtime, host.alias, prepare, &mut report);
        check_tools(runtime, host.alias, &mut report);
        prepare_agent(runtime, host, prepare, link, &mut report);
        prepare_credentials(runtime, host.alias, prepare, &mut report);
        if prepare.take_lease {
            take_lease(runtime, host, prepare, link, &mut report);
        }
    }
    Outcome {
        exit_code: if report.has_missing() { exit::INFRA } else { exit::GREEN },
        text: report.text(),
        json: None,
    }
}

fn check_ssh(runtime: &dyn Runtime, alias: &str, report: &mut Report) -> bool {
    let answered = remote_call(runtime, &ssh(alias, "true"));
    if answered.exit_code == 0 {
        report.add(Status::Ok, "ssh", format!("{alias} answers in batch mode"));
        return true;
    }
    let said = answered.output.lines().next().unwrap_or_default().trim().to_owned();
    report.add(
        Status::Missing,
        "ssh",
        format!(
            "{alias} does not answer in batch mode ({said}). In the `Host {alias}` block of ~/.ssh/config, set `AddressFamily inet`, \
             and set `IdentitiesOnly yes` only together with an `IdentityFile`"
        ),
    );
    false
}

fn prepare_repository(runtime: &dyn Runtime, host: &Host<'_>, prepare: &Prepare, report: &mut Report) {
    const STEP: &str = "repository";
    let repo = host_path_word(host_repo(host));
    let local = local_git(runtime, &["rev-parse", "--verify", "-q", "refs/heads/master"]);
    let local_master = local.output.trim().to_owned();
    if local.exit_code != 0 || local_master.is_empty() {
        report.add(Status::Missing, STEP, "this checkout has no master branch to push");
        return;
    }
    let probed = remote_call(
        runtime,
        &ssh(
            host.alias,
            &format!(
                "if [ -d {repo}/.git ]; then git -C {repo} rev-parse --verify -q refs/heads/master || echo no-master; else echo absent; fi"
            ),
        ),
    );
    let host_master = probed.output.trim();
    if probed.exit_code != 0 {
        report.add(Status::Missing, STEP, format!("cannot probe {} on {}", host_repo(host), host.alias));
        return;
    }
    match host_master {
        "absent" => create_repository(runtime, host, &repo, &local_master, prepare, report),
        "no-master" => report.add(
            Status::Missing,
            STEP,
            format!("{} on {} has no master branch", host_repo(host), host.alias),
        ),
        _ if host_master == local_master => report.add(
            Status::Ok,
            STEP,
            format!("master of {} is at {}", host_repo(host), short(&local_master)),
        ),
        _ => move_master(runtime, host, &repo, host_master, &local_master, prepare, report),
    }
}

fn create_repository(runtime: &dyn Runtime, host: &Host<'_>, repo: &str, local_master: &str, prepare: &Prepare, report: &mut Report) {
    const STEP: &str = "repository";
    let size = pack_size_mib(runtime);
    if !prepare.change {
        report.add(
            Status::Missing,
            STEP,
            format!(
                "{} on {} is missing; --prepare creates it and pushes about {size} MiB",
                host_repo(host),
                host.alias
            ),
        );
        return;
    }
    let created = remote_call(
        runtime,
        &ssh(
            host.alias,
            &format!(
                "git init -q {repo} && git -C {repo} config feature.manyFiles true && git -C {repo} config core.untrackedCache true \
                 && git -C {repo} config core.fsmonitor false"
            ),
        ),
    );
    if created.exit_code != 0 {
        report.add(
            Status::Missing,
            STEP,
            format!("cannot create {} on {}: {}", host_repo(host), host.alias, created.output.trim()),
        );
        return;
    }
    // A Wi-Fi link moves about 4 MB/s.
    runtime.write_error(&format!(
        "pushing the master history to {}: about {size} MiB, which takes about {} min over Wi-Fi",
        host.alias,
        size / 4 / 60 + 1
    ));
    if push_sync_and_move(runtime, host, repo, local_master, "checkout -q -B master sync", report) {
        report.add(
            Status::Done,
            STEP,
            format!("created {} on {} at {}", host_repo(host), host.alias, short(local_master)),
        );
    }
}

fn move_master(
    runtime: &dyn Runtime,
    host: &Host<'_>,
    repo: &str,
    host_master: &str,
    local_master: &str,
    prepare: &Prepare,
    report: &mut Report,
) {
    const STEP: &str = "repository";
    let ancestor = local_git(runtime, &["merge-base", "--is-ancestor", host_master, local_master]);
    if ancestor.exit_code != 0 {
        report.add(
            Status::Missing,
            STEP,
            format!(
                "master {} of {} is not an ancestor of the local master; move it by hand",
                short(host_master),
                host.alias
            ),
        );
        return;
    }
    let status = remote_call(
        runtime,
        &ssh(host.alias, &format!("git -C {repo} status --porcelain --untracked-files=no")),
    );
    if status.exit_code != 0 || !status.output.trim().is_empty() {
        report.add(
            Status::Missing,
            STEP,
            format!("{} on {} has local changes, so its master stays", host_repo(host), host.alias),
        );
        return;
    }
    if !prepare.change {
        report.add(
            Status::Missing,
            STEP,
            format!(
                "master of {} is {} behind {}; --prepare moves it",
                host.alias,
                short(host_master),
                short(local_master)
            ),
        );
        return;
    }
    if push_sync_and_move(runtime, host, repo, local_master, "merge -q --ff-only sync", report) {
        report.add(
            Status::Done,
            STEP,
            format!(
                "moved master of {} from {} to {}",
                host.alias,
                short(host_master),
                short(local_master)
            ),
        );
    }
}

/// Pushes the local master to `refs/heads/sync` on the host, then runs the git command that moves the host master.
fn push_sync_and_move(
    runtime: &dyn Runtime,
    host: &Host<'_>,
    repo: &str,
    local_master: &str,
    git_command: &str,
    report: &mut Report,
) -> bool {
    const STEP: &str = "repository";
    let ssh_config = ssh_git_config();
    let url = push_url(host.alias, host_repo(host));
    let pushed = watched_git(
        runtime,
        &[
            "-c",
            &ssh_config,
            "push",
            "--progress",
            &url,
            &format!("{local_master}:refs/heads/sync"),
        ],
    );
    if pushed.exit_code != 0 {
        report.add(
            Status::Missing,
            STEP,
            format!("cannot push master to {url}: {}", pushed.output.trim()),
        );
        return false;
    }
    let moved = remote_call(runtime, &ssh(host.alias, &format!("git -C {repo} {git_command}")));
    if moved.exit_code != 0 {
        report.add(
            Status::Missing,
            STEP,
            format!("cannot move master on {}: {}", host.alias, moved.output.trim()),
        );
        return false;
    }
    true
}

fn prepare_bd(runtime: &dyn Runtime, alias: &str, prepare: &Prepare, report: &mut Report) {
    const STEP: &str = "bd";
    let Some(home) = runtime.home_dir() else {
        report.add(Status::Missing, STEP, "no home directory here");
        return;
    };
    let local_bd: PathBuf = home.join(".local/bin/bd");
    let local_version = runtime
        .exists(&local_bd)
        .then(|| version_of(&runtime.spawn(&[local_bd.clone().into_os_string(), "--version".into()], None).output))
        .flatten();
    let local_sha = sha_of(
        &runtime
            .spawn(
                &["shasum".into(), "-a".into(), "256".into(), local_bd.clone().into_os_string()],
                None,
            )
            .output,
    );
    let host_probe = remote_call(
        runtime,
        &ssh(
            alias,
            "uname -sm; ~/.local/bin/bd --version 2>/dev/null; shasum -a 256 ~/.local/bin/bd 2>/dev/null",
        ),
    );
    let mut lines = host_probe.output.lines();
    let host_platform = lines.next().unwrap_or_default().trim().to_owned();
    let host_answer = lines.collect::<Vec<_>>().join("\n");
    let host_version = version_of(&host_answer);
    let host_sha = sha_of(&host_answer);
    let Some(local_version) = local_version else {
        report.add(Status::Missing, STEP, format!("no bd at {}", local_bd.display()));
        return;
    };
    // Without a hash on one side, the version decides, as before the hash.
    let same = match (&local_sha, &host_sha) {
        (Some(local), Some(host)) => local == host,
        _ => host_version.as_deref() == Some(local_version.as_str()),
    };
    let local_build = build_name(&local_version, local_sha.as_deref());
    if same {
        report.add(Status::Ok, STEP, format!("bd {local_build}"));
    } else {
        let local_platform = local_platform(runtime);
        let had = host_version.map_or_else(|| "none".to_owned(), |version| build_name(&version, host_sha.as_deref()));
        if host_platform != local_platform {
            report.add(
                Status::Missing,
                STEP,
                format!("{alias} has bd {had} on {host_platform}, and this {local_platform} bd does not run there; install bd there"),
            );
        } else if !prepare.change {
            report.add(
                Status::Missing,
                STEP,
                format!("{alias} has bd {had}; --prepare copies {local_build}"),
            );
        } else if copy_bd(runtime, alias, &local_bd) {
            report.add(
                Status::Done,
                STEP,
                format!("copied bd {local_build} to ~/.local/bin/bd (was {had})"),
            );
        } else {
            report.add(Status::Missing, STEP, format!("cannot copy bd to {alias}"));
        }
    }
    let path = remote_call(
        runtime,
        &ssh(alias, r#"case ":$PATH:" in *":$HOME/.local/bin:"*) echo on ;; *) echo off ;; esac"#),
    );
    match (path.output.trim(), prepare.change) {
        ("on", _) => report.add(Status::Ok, "PATH", "~/.local/bin is on the PATH of the login shell"),
        (_, false) => report.add(
            Status::Missing,
            "PATH",
            "~/.local/bin is not on the PATH; --prepare adds it to ~/.zshenv",
        ),
        (_, true) => {
            let added = remote_call(
                runtime,
                &ssh(alias, r#"printf '%s\n' 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshenv"#),
            );
            if added.exit_code == 0 {
                report.add(Status::Done, "PATH", "added ~/.local/bin to the PATH in ~/.zshenv");
            } else {
                report.add(Status::Missing, "PATH", "cannot write ~/.zshenv");
            }
        }
    }
}

fn copy_bd(runtime: &dyn Runtime, alias: &str, local_bd: &std::path::Path) -> bool {
    let staged = ".local/bin/bd.bt-new";
    let made = remote_call(runtime, &ssh(alias, "mkdir -p .local/bin"));
    let mut copy = scp(&["-q"]);
    copy.push(local_bd.as_os_str().to_owned());
    copy.push(format!("{alias}:{staged}").into());
    made.exit_code == 0
        && runtime.spawn_watched(&copy, COPY_IDLE).exit_code == 0
        && runtime
            .spawn(&ssh(alias, &format!("chmod 755 {staged} && mv -f {staged} .local/bin/bd")), None)
            .exit_code
            == 0
}

/// The version of a `bd --version` answer: `{"schema":2,"version":"0.29.0",…}`.
fn version_of(text: &str) -> Option<String> {
    bt_core::regex!(r#""version":"([^"]+)""#)
        .captures(text)
        .map(|found| found[1].to_owned())
}

/// The SHA-256 of a `shasum -a 256` answer: the 64 hex digits at the start of a line.
fn sha_of(text: &str) -> Option<String> {
    bt_core::regex!(r"(?m)^([0-9a-f]{64})\s")
        .captures(text)
        .map(|found| found[1].to_owned())
}

/// A build of bd as the report names it: the version, and the first 12 hex digits of the hash when it is known.
fn build_name(version: &str, sha: Option<&str>) -> String {
    match sha {
        Some(sha) => format!("{version} ({})", &sha[..12]),
        None => version.to_owned(),
    }
}

/// This machine as `uname -sm` names it.
fn local_platform(runtime: &dyn Runtime) -> String {
    let named = runtime.spawn(&["uname".into(), "-sm".into()], None);
    if named.exit_code == 0 {
        return named.output.trim().to_owned();
    }
    match runtime.platform() {
        Platform::Windows => "Windows".to_owned(),
        Platform::Darwin => "Darwin".to_owned(),
        Platform::Linux => "Linux".to_owned(),
    }
}

fn check_tools(runtime: &dyn Runtime, alias: &str, report: &mut Report) {
    let probes: Vec<String> = TOOLS
        .iter()
        .map(|(name, probe, _)| {
            let check = if probe.contains(' ') {
                format!("{probe} >/dev/null 2>&1")
            } else {
                format!("command -v {probe} >/dev/null 2>&1")
            };
            format!("if {check}; then echo '{name} yes'; else echo '{name} no'; fi")
        })
        .collect();
    let answered = remote_call(runtime, &ssh(alias, &probes.join("; ")));
    for (name, _, needed) in TOOLS {
        let present = answered.output.lines().any(|line| line.trim() == format!("{name} yes"));
        match (present, needed) {
            (true, _) => report.add(Status::Ok, "tool", format!("{name} is there")),
            (false, true) => report.add(Status::Missing, "tool", format!("{name} is not there; install it on {alias}")),
            (false, false) => report.add(Status::Absent, "tool", format!("{name} is not there; only some lanes need it")),
        }
    }
}

/// What the host lacks of the credentials of the checkout.
struct CredentialGaps {
    hosts: Vec<String>,
    servers: Vec<String>,
    markers: Vec<String>,
}

fn prepare_credentials(runtime: &dyn Runtime, alias: &str, prepare: &Prepare, report: &mut Report) {
    let services = services(runtime);
    let hosts = service_hosts(&services);
    let servers = space_repository_ids(runtime);
    let gaps = credential_gaps(runtime, alias, &hosts, &servers, report);
    let missing = !gaps.hosts.is_empty() || !gaps.servers.is_empty() || !gaps.markers.is_empty();
    if missing {
        copy_credentials(runtime, alias, prepare, &gaps, report);
    }
    for service in services.iter().filter(|service| service.option == REMOTE_CACHE_OPTION) {
        let probe = format!(
            "curl -s --netrc -o /dev/null -w '%{{http_code}}' https://{}/ac/{ABSENT_CACHE_KEY}",
            service.host
        );
        let answered = remote_call(runtime, &ssh(alias, &probe));
        let code = answered.output.trim();
        let status = if matches!(code, "200" | "404") {
            Status::Ok
        } else {
            Status::Missing
        };
        report.add(status, "remote cache", format!("{} answers {code} on {alias}", service.host));
    }
}

/// Reports the host and server names that the host has and lacks. It reads names only, never a value.
fn credential_gaps(runtime: &dyn Runtime, alias: &str, hosts: &[String], servers: &[String], report: &mut Report) -> CredentialGaps {
    let machines = remote_call(
        runtime,
        &ssh(
            alias,
            r#"awk '{for (i = 1; i < NF; i++) if ($i == "machine") print $(i + 1)}' ~/.netrc 2>/dev/null"#,
        ),
    );
    let known_machines: Vec<&str> = machines.output.lines().map(str::trim).collect();
    let gap_hosts: Vec<String> = hosts
        .iter()
        .filter(|host| !known_machines.contains(&host.as_str()))
        .cloned()
        .collect();
    for host in hosts {
        let status = if gap_hosts.contains(host) { Status::Missing } else { Status::Ok };
        report.add(
            status,
            "netrc",
            format!("~/.netrc on {alias} has an entry for {host}: {}", status == Status::Ok),
        );
    }

    let ids = remote_call(
        runtime,
        &ssh(alias, r"sed -n 's:.*<id>\(.*\)</id>.*:\1:p' ~/.m2/settings.xml 2>/dev/null"),
    );
    let known_ids: Vec<&str> = ids.output.lines().map(str::trim).collect();
    let gap_servers: Vec<String> = servers.iter().filter(|id| !known_ids.contains(&id.as_str())).cloned().collect();
    if !servers.is_empty() {
        if gap_servers.is_empty() {
            report.add(
                Status::Ok,
                "Maven",
                format!("~/.m2/settings.xml on {alias} has all {} Space servers", servers.len()),
            );
        } else {
            report.add(
                Status::Missing,
                "Maven",
                format!("~/.m2/settings.xml on {alias} has no server for {}", gap_servers.join(", ")),
            );
        }
    }

    let gap_markers = marker_gaps(runtime, alias, servers, report);
    CredentialGaps {
        hosts: gap_hosts,
        servers: gap_servers,
        markers: gap_markers,
    }
}

/// The token expiry marker of the servers that share one token.
struct Marker {
    /// The file name: the first 20 hex digits of the SHA-256 of the token, as `IJPrivatePackagesAuthorizer` names it.
    name: String,
    servers: Vec<String>,
}

/// The expiry markers of the tokens of the Space servers, from the local settings.xml. The hash takes the token on its
/// stdin, so the token stays off every command line.
fn token_markers(runtime: &dyn Runtime, servers: &[String]) -> Vec<Marker> {
    let Some(home) = runtime.home_dir() else {
        return Vec::new();
    };
    let settings = runtime.read_text_file(&home.join(SETTINGS_XML)).unwrap_or_default();
    let mut by_token: Vec<(String, Vec<String>)> = Vec::new();
    for server in servers {
        let Some(range) = SettingsFormat.find(&settings, server) else {
            continue;
        };
        let Some(token) = bt_core::regex!(r"<password>\s*([^<]*?)\s*</password>").captures(&settings[range]) else {
            continue;
        };
        match by_token.iter_mut().find(|(known, _)| known == &token[1]) {
            Some((_, ids)) => ids.push(server.clone()),
            None => by_token.push((token[1].to_owned(), vec![server.clone()])),
        }
    }
    let hash: Vec<OsString> = ["shasum", "-a", "256"].into_iter().map(OsString::from).collect();
    by_token
        .into_iter()
        .filter_map(|(token, servers)| {
            let hashed = runtime.spawn_with_input(&hash, &token);
            let name = hashed.output.get(..20).filter(|_| hashed.exit_code == 0)?.to_owned();
            Some(Marker { name, servers })
        })
        .collect()
}

/// Reports the expiry marker of each token: a local one that is missing or in the past means that the local token
/// must be renewed first. Answers the markers that the host lacks.
fn marker_gaps(runtime: &dyn Runtime, alias: &str, servers: &[String], report: &mut Report) -> Vec<String> {
    const STEP: &str = "token markers";
    let markers = token_markers(runtime, servers);
    let Some(home) = runtime.home_dir().filter(|_| !markers.is_empty()) else {
        return Vec::new();
    };
    let listed = remote_call(runtime, &ssh(alias, &format!("ls -1 {TOKEN_MARKERS} 2>/dev/null")));
    let known: Vec<&str> = listed.output.lines().map(str::trim).collect();
    let now = runtime.now_ms() / 1000;
    let mut gaps = Vec::new();
    for marker in markers {
        let ids = match marker.servers.as_slice() {
            [first, second, third, _, ..] => format!("{first}, {second}, {third} and {} more servers", marker.servers.len() - 3),
            servers => servers.join(", "),
        };
        let path = home.join(TOKEN_MARKERS).join(&marker.name);
        let stat = runtime.spawn(&[OsString::from("stat"), "-f".into(), "%m".into(), path.into_os_string()], None);
        let expiry = stat.output.trim().parse::<u64>().ok().filter(|_| stat.exit_code == 0);
        match expiry {
            None => report.add(
                Status::Missing,
                STEP,
                format!("this machine has no expiry marker for the token of {ids}; renew the local token first (run ./community/tools/bt.cmd here once)"),
            ),
            Some(expiry) if expiry <= now => report.add(
                Status::Missing,
                STEP,
                format!("the local token of {ids} has expired; renew it first (run ./community/tools/bt.cmd here once)"),
            ),
            Some(_) if known.contains(&marker.name.as_str()) => {
                report.add(Status::Ok, STEP, format!("{alias} has the expiry marker of the token of {ids}"));
            }
            Some(_) => {
                report.add(Status::Missing, STEP, format!("{alias} lacks the expiry marker of the token of {ids}"));
                gaps.push(marker.name);
            }
        }
    }
    gaps
}

fn copy_credentials(runtime: &dyn Runtime, alias: &str, prepare: &Prepare, gaps: &CredentialGaps, report: &mut Report) {
    const STEP: &str = "credentials";
    if !prepare.change {
        report.add(Status::Missing, STEP, "--prepare copies the missing entries after a yes");
        return;
    }
    let mut question = format!("Copy to {alias}:");
    if !gaps.hosts.is_empty() {
        let _ = write!(question, " the ~/.netrc entries of {};", gaps.hosts.join(", "));
    }
    if !gaps.servers.is_empty() {
        let _ = write!(question, " the ~/.m2/settings.xml servers {};", gaps.servers.join(", "));
    }
    if !gaps.markers.is_empty() {
        let _ = write!(question, " {} token expiry markers;", gaps.markers.len());
    }
    let confirmed = if prepare.yes {
        Some(true)
    } else {
        runtime.confirm(question.trim_end_matches(';'))
    };
    match confirmed {
        None => {
            report.add(
                Status::Missing,
                STEP,
                format!("the stdin is not a terminal, so nothing is copied; run `./community/tools/bt.cmd --host {alias} --prepare` in a terminal"),
            );
            return;
        }
        Some(false) => {
            report.add(Status::Missing, STEP, "not copied: the answer was no");
            return;
        }
        Some(true) => {}
    }
    let id = format!("prepare-{}", runtime.now_ms());
    let mut lines = Vec::new();
    if !gaps.hosts.is_empty() {
        lines.extend(copy_entries(runtime, alias, &id, NETRC, &gaps.hosts, &NetrcFormat));
    }
    if !gaps.servers.is_empty() {
        lines.extend(copy_entries(runtime, alias, &id, SETTINGS_XML, &gaps.servers, &SettingsFormat));
    }
    if !gaps.markers.is_empty() {
        lines.push(copy_markers(runtime, alias, &gaps.markers));
    }
    for line in lines {
        let status = if line.starts_with("synced") || line.starts_with("copied") || line.starts_with("current") {
            Status::Done
        } else {
            Status::Missing
        };
        report.add(status, STEP, line);
    }
}

/// Copies the empty marker files with their modification times, which are the token expiries.
fn copy_markers(runtime: &dyn Runtime, alias: &str, markers: &[String]) -> String {
    let Some(home) = runtime.home_dir() else {
        return "no home directory here".to_owned();
    };
    let made = remote_call(runtime, &ssh(alias, &format!("mkdir -p {TOKEN_MARKERS}")));
    let mut copy = scp(&["-pq"]);
    copy.extend(markers.iter().map(|marker| home.join(TOKEN_MARKERS).join(marker).into_os_string()));
    copy.push(format!("{alias}:{TOKEN_MARKERS}/").into());
    if made.exit_code == 0 && runtime.spawn_watched(&copy, COPY_IDLE).exit_code == 0 {
        format!("copied {} token expiry markers", markers.len())
    } else {
        "cannot copy the token expiry markers".to_owned()
    }
}

fn take_lease(runtime: &dyn Runtime, host: &Host<'_>, prepare: &Prepare, link: &dyn HostLink, report: &mut Report) {
    const STEP: &str = "lease";
    if !prepare.change {
        report.add(
            Status::Missing,
            STEP,
            "--take-lease takes the warm lease of this checkout without --dry-run",
        );
        return;
    }
    let holder = holder(runtime);
    match link.lease(host.alias, host.repo, &holder) {
        Ok(leased) => {
            let fresh = if leased.fresh { "new" } else { "warm" };
            report.add(
                Status::Done,
                STEP,
                format!("the lease {} of {holder} is {} ({fresh})", leased.lease_id, leased.slot),
            );
        }
        Err(refusal) => report.add(Status::Missing, STEP, format!("cannot take the lease: {}", refusal.message)),
    }
}

/// The agent step: a hello for `--dry-run`, else the build of `yonderd` here and `yonder prepare`.
fn prepare_agent(runtime: &dyn Runtime, host: &Host<'_>, prepare: &Prepare, link: &dyn HostLink, report: &mut Report) {
    const STEP: &str = "agent";
    if !prepare.change {
        match link.hello(host.alias) {
            Ok(agent) => report.add(Status::Ok, STEP, agent_text(&agent)),
            Err(refusal) => report.add(Status::Missing, STEP, format!("{}; --prepare installs it", refusal.message)),
        }
        return;
    }
    let agent = match build_agent(runtime) {
        Ok(agent) => agent,
        Err(said) => {
            report.add(Status::Missing, STEP, said);
            return;
        }
    };
    let name = client_name(runtime);
    let request = PrepareRequest {
        alias: host.alias,
        agent: &agent,
        port: DEFAULT_PORT,
        repo: host.repo.unwrap_or(DEFAULT_HOST_REPO),
        name: &name,
    };
    match link.prepare(&request) {
        Ok(prepared) => {
            let (status, copied) = if prepared.copied {
                (Status::Done, "copied to ~/.local/bin/yonderd; ")
            } else {
                (Status::Ok, "")
            };
            report.add(
                status,
                STEP,
                format!(
                    "{copied}{}, launchd {}, host file {}",
                    agent_text(&prepared.agent),
                    prepared.launchd,
                    prepared.host_file.display()
                ),
            );
        }
        Err(refusal) => report.add(Status::Missing, STEP, refusal.message),
    }
}

fn agent_text(agent: &yonder_client::AgentInfo) -> String {
    format!(
        "yonderd {} answers on {} ({} {})",
        agent.version, agent.address, agent.os, agent.arch
    )
}

/// Builds `yonderd` with the Bazel launcher of bt, and answers the binary that the build event file names.
fn build_agent(runtime: &dyn Runtime) -> Result<PathBuf, String> {
    let label = if runtime.exists(&repo_file(runtime, "community/tools/yonder")) {
        format!("@community{AGENT_TARGET}")
    } else {
        AGENT_TARGET.to_owned()
    };
    let events = runtime.temp_file("bt-yonderd-events");
    let args: Vec<String> = [
        "build",
        "--config=bt",
        &format!("--build_event_json_file={}", events.display()),
        "--nobuild_event_json_file_path_conversion",
        &label,
    ]
    .map(str::to_owned)
    .to_vec();
    let command = bazel_command(runtime.repo_root(), runtime.platform(), &args);
    let shown = command.iter().map(|part| part.to_string_lossy()).collect::<Vec<_>>().join(" ");
    runtime.write_error(&format!("> {shown}"));
    let built = runtime.spawn_watched(&command, AGENT_BUILD_LIMIT);
    let binary = runtime.read_lines(&events).find_map(|line| built_file(&line, AGENT_NAME));
    let _ = runtime.remove(&events);
    if built.exit_code == STOPPED_IDLE {
        return Err(format!(
            "`{shown}` wrote nothing for {} min and was stopped",
            AGENT_BUILD_LIMIT.as_secs() / 60
        ));
    }
    if built.exit_code != 0 {
        return Err(format!("`{shown}` failed:\n{}", tail_lines(&built.output, BUILD_TAIL_LINES)));
    }
    binary.ok_or_else(|| format!("the build of {label} named no {AGENT_NAME} file"))
}

/// The local file with the base name `name` that one line of a build event file names: the build names its outputs
/// as `"uri":"file://<path>"`.
fn built_file(line: &str, name: &str) -> Option<PathBuf> {
    bt_core::regex!(r#""uri":"file://([^"]+)""#)
        .captures_iter(line)
        .map(|found| found[1].to_owned())
        .filter(|path| path.rsplit('/').next() == Some(name))
        .last()
        .map(PathBuf::from)
}

/// The name of this client in the allow list of the agent: `bt on <host name>`.
fn client_name(runtime: &dyn Runtime) -> String {
    let named = runtime.spawn(&["hostname".into()], None);
    match named.output.trim() {
        name if named.exit_code == 0 && !name.is_empty() => format!("bt on {name}"),
        _ => "bt".to_owned(),
    }
}

fn local_git(runtime: &dyn Runtime, args: &[&str]) -> SpawnResult {
    let command: Vec<OsString> = std::iter::once("git").chain(args.iter().copied()).map(OsString::from).collect();
    runtime.spawn(&command, None)
}

/// The pack size of this repository in MiB, from `git count-objects -v`.
fn pack_size_mib(runtime: &dyn Runtime) -> u64 {
    let counted = local_git(runtime, &["count-objects", "-v"]);
    counted
        .output
        .lines()
        .find_map(|line| line.strip_prefix("size-pack: "))
        .and_then(|kib| kib.trim().parse::<u64>().ok())
        .map_or(0, |kib| kib / 1024)
}

fn short(commit: &str) -> &str {
    commit.get(..12).unwrap_or(commit)
}

#[cfg(test)]
mod tests;
