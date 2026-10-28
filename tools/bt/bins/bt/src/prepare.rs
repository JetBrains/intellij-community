//! `bt --host <ssh alias> --prepare`: makes another machine of the user ready for `bt --host`, and says what is
//! missing. The user runs it in a terminal. Each step is idempotent and reports `ok`, `done` or `missing`. A tool that
//! only some lanes need reports `absent` instead of `missing`. With `--dry-run` it changes nothing.
//!
//! 1. ssh: the alias answers in batch mode.
//! 2. The main repository on the host: created and given the master history when it is missing, and moved to the
//!    local master when its master is clean and an ancestor. Its `core.fsmonitor` is on, also in a repository that
//!    an older prepare created with it off.
//! 3. PATH: `~/.local/bin`, where `bd-host setup` puts `bd-host`, on the PATH of `~/.zshenv`. The agent finds itself
//!    there from the login shell. The host needs no `bd`, because the agent links the lease pool of Branch Deck.
//! 4. Tools: git, Xcode, rustup, docker, tmux. Reported only, never installed.
//! 5. The agent: `bd-host setup <alias>` of Branch Deck. Over ssh, it copies the local `bd-host` to the host when the
//!    host has another build, adds this client to the allow list of the agent, starts the LaunchAgent, and writes the
//!    host file, so a run needs no ssh. Then the agent must answer a hello. With `--dry-run`, the step only asks the
//!    agent for its hello.
//! 6. Credentials: the netrc entries of the Bazel services, the Maven servers of the Space repositories, and the
//!    expiry marker of the token of each server, which `IJPrivatePackagesAuthorizer` reads. An expired or missing local
//!    marker asks for a renewed local token first. The copy needs a yes at the terminal or `--yes`, and it
//!    refuses without a terminal, so an agent cannot start it. Then the remote cache must answer 404, not 401.

use std::ffi::OsString;
use std::fmt::Write as _;

use bt_core::exit;
use bt_core::runtime::{Runtime, SpawnResult};

use crate::credentials::{
    Format, NETRC, NetrcFormat, REMOTE_CACHE_OPTION, SETTINGS_XML, SettingsFormat, copy_entries, service_hosts, services,
    space_repository_ids,
};
use crate::execute::Outcome;
use crate::remote::{DEFAULT_HOST_REPO, Host, HostHello, HostLink, HostSetup};
use crate::ssh::{COPY_IDLE, host_path_word, host_repo, push_url, remote_call, scp, ssh, ssh_git_config, watched_git};

/// What `--prepare` may do.
pub(crate) struct Prepare {
    /// `false` for `--dry-run`: check only.
    pub(crate) change: bool,
    /// `--yes`: copy the credentials without the question.
    pub(crate) yes: bool,
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
        prepare_path(runtime, host.alias, prepare, &mut report);
        check_tools(runtime, host.alias, &mut report);
        prepare_agent(runtime, host, prepare, link, &mut report);
        prepare_credentials(runtime, host.alias, prepare, &mut report);
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
        _ if host_master == local_master => {
            report.add(
                Status::Ok,
                STEP,
                format!("master of {} is at {}", host_repo(host), short(&local_master)),
            );
            prepare_fsmonitor(runtime, host, &repo, prepare, report);
        }
        _ => {
            move_master(runtime, host, &repo, host_master, &local_master, prepare, report);
            prepare_fsmonitor(runtime, host, &repo, prepare, report);
        }
    }
}

/// Turns on `core.fsmonitor` in an existing repository on the host. The give-back of a host slot (`bd lease host`)
/// runs one `status` after each run. Without fsmonitor that status reads every file of the slot: 18-19 s and about
/// 240 s of system CPU on the Studio for 1.4M files, against 2.5 s with it. The setting goes into the repository, not
/// onto one command, because a git without fsmonitor that writes the index drops the fsmonitor token of the index.
fn prepare_fsmonitor(runtime: &dyn Runtime, host: &Host<'_>, repo: &str, prepare: &Prepare, report: &mut Report) {
    const STEP: &str = "repository";
    let read = remote_call(
        runtime,
        &ssh(host.alias, &format!("git -C {repo} config --type=bool --get core.fsmonitor")),
    );
    let value = read.output.trim();
    if read.exit_code == 0 && value == "true" {
        report.add(Status::Ok, STEP, format!("core.fsmonitor is on in {}", host_repo(host)));
        return;
    }
    let was = if read.exit_code == 0 && !value.is_empty() { value } else { "unset" };
    if !prepare.change {
        report.add(
            Status::Missing,
            STEP,
            format!("core.fsmonitor is {was} in {}; --prepare turns it on", host_repo(host)),
        );
        return;
    }
    let set = remote_call(runtime, &ssh(host.alias, &format!("git -C {repo} config core.fsmonitor true")));
    if set.exit_code == 0 {
        report.add(
            Status::Done,
            STEP,
            format!("turned on core.fsmonitor in {} on {} (it was {was})", host_repo(host), host.alias),
        );
    } else {
        report.add(
            Status::Missing,
            STEP,
            format!("cannot turn on core.fsmonitor in {}: {}", host_repo(host), set.output.trim()),
        );
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
                 && git -C {repo} config core.fsmonitor true"
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

/// `~/.local/bin` on the PATH of the login shell of the host. `bd-host setup` puts `bd-host` there, and the agent
/// reads the login environment through it.
fn prepare_path(runtime: &dyn Runtime, alias: &str, prepare: &Prepare, report: &mut Report) {
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

/// The agent step: a hello for `--dry-run`, else `bd-host setup <alias>` and a hello.
fn prepare_agent(runtime: &dyn Runtime, host: &Host<'_>, prepare: &Prepare, link: &dyn HostLink, report: &mut Report) {
    const STEP: &str = "agent";
    if !prepare.change {
        match link.hello(host.alias) {
            Ok(hello) => report.add(Status::Ok, STEP, agent_text(host.alias, &hello)),
            Err(refusal) => report.add(Status::Missing, STEP, format!("{}; --prepare installs it", refusal.message)),
        }
        return;
    }
    let name = client_name(runtime);
    let request = HostSetup {
        alias: host.alias,
        repo: host.repo.unwrap_or(DEFAULT_HOST_REPO),
        name: &name,
    };
    let said = match link.setup(&request) {
        Ok(said) => said,
        Err(refusal) => {
            report.add(Status::Missing, STEP, refusal.message);
            return;
        }
    };
    let setup = if said.is_empty() {
        format!("bd-host setup {} ran", host.alias)
    } else {
        said.lines().map(str::trim).collect::<Vec<_>>().join("; ")
    };
    match link.hello(host.alias) {
        Ok(hello) => report.add(Status::Done, STEP, format!("{setup}; {}", agent_text(host.alias, &hello))),
        Err(refusal) => report.add(
            Status::Missing,
            STEP,
            format!("{setup}, but the agent does not answer: {}", refusal.message),
        ),
    }
}

/// The agent of a hello as the report names it, with the upgrade hint when it runs another build than `bd-host` here.
fn agent_text(alias: &str, hello: &HostHello) -> String {
    let agent = &hello.agent;
    let mut text = format!(
        "bd-host {} answers on {} ({} {})",
        agent.version, hello.address, agent.os, agent.arch
    );
    if !hello.same_build {
        let _ = write!(text, ", another build than here; `bd-host upgrade {alias}` replaces it");
    }
    text
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
