//! `air-trace serve --teamcity`: the bundles of a TeamCity build, from its `bazel.logs.zip`.
//!
//! A build is resolved through the REST API. A composite build stands for the builds of its snapshot dependencies,
//! level by level, until each is a build that ran: a shard of the Air UI tests. Each such build's `bazel.logs.zip`
//! is downloaded once into the cache, `<cache>/tc-<server hash>-<build id>/bazel.logs.zip`, and becomes one root with
//! the build as its origin. A build that fails to resolve or to download is a root with its error, and the other
//! builds still serve.
//!
//! The server talks to TeamCity through a program, so it needs no TLS stack of its own. For the server of
//! `--teamcity-url`, with `TEAMCITY_TOKEN` set, it runs `curl` with the token as a bearer header, given on the
//! standard input so that no process listing shows it. For any other server, and without the token, it runs
//! `teamcity api`, the TeamCity CLI, which signs the request with the login it keeps for that server. So the token
//! goes to no server but the one it was set for. A server is `https`, or `http` on a loopback host.

use std::collections::HashSet;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::SystemTime;

use avl_trace_tools::discover::{Origin, Root, RootKind};
use serde::Deserialize;
use sha2::{Digest, Sha256};

use super::files::set_modified;

use crate::{Exit, Refusal, usage};

/// The TeamCity server of a build given by its id alone.
pub(crate) const DEFAULT_TEAMCITY_URL: &str = "https://buildserver.labs.intellij.net";

/// The artifact that holds the testlogs of a Bazel build.
pub(crate) const LOGS_ARTIFACT: &str = "bazel.logs.zip";

/// The refusal code of a build that cannot be resolved or downloaded.
const TEAMCITY_FAILED: &str = "teamcity_failed";

/// Bounds the builds one resolution reads: a composite chain of the Air tests has seven.
const MAX_BUILDS: usize = 64;

/// The fields of a build that a resolution reads, for the build and for each of its snapshot dependencies.
const BUILD_FIELDS: &str = "id,number,status,state,composite,webUrl,buildType(id,name),\
     snapshot-dependencies(build(id,number,status,state,composite,webUrl,buildType(id,name)))";

/// A build that a `--teamcity` value names: the server and the build id.
#[derive(Clone, PartialEq, Eq, Debug)]
pub(crate) struct BuildRef {
    pub server: String,
    pub id: u64,
}

/// Reads `--teamcity-url`: `https://<host>`, or `http://<host>` for a loopback host, with no path. Answers it as
/// `<scheme>://<authority>` in lower case.
pub(crate) fn parse_server(given: &str) -> Result<String, Refusal> {
    let refuse = |why: &str| usage(format!("--teamcity-url {given}: {why}"));
    let (scheme, rest) = given
        .trim()
        .split_once("://")
        .ok_or_else(|| refuse("not a URL such as https://buildserver.labs.intellij.net"))?;
    let authority = rest.trim_end_matches('/');
    if authority.is_empty() || authority.contains(['/', '?', '#', '@']) {
        return Err(refuse("give the scheme and the host only"));
    }
    server_of(scheme, authority).map_err(|why| refuse(&why))
}

/// The server of a scheme and an authority, in lower case, refused unless it is `https` or a loopback `http`.
fn server_of(scheme: &str, authority: &str) -> Result<String, String> {
    let scheme = scheme.to_ascii_lowercase();
    let authority = authority.to_ascii_lowercase();
    match scheme.as_str() {
        "https" => {}
        "http" if is_loopback(&authority) => {}
        "http" => return Err("plain http is for a loopback host only; use https".to_owned()),
        _ => return Err("not an http or https URL".to_owned()),
    }
    Ok(format!("{scheme}://{authority}"))
}

/// Whether an authority names this machine: `localhost`, an address of 127.0.0.0/8, or `[::1]`, with or without a
/// port.
fn is_loopback(authority: &str) -> bool {
    let host = if let Some(bracketed) = authority.strip_prefix('[') {
        bracketed.split_once(']').map_or(bracketed, |(host, _)| host)
    } else {
        authority.split_once(':').map_or(authority, |(host, _)| host)
    };
    host == "localhost" || host == "::1" || host.parse::<std::net::Ipv4Addr>().is_ok_and(|address| address.is_loopback())
}

/// Reads a `--teamcity` value: a build id, or the URL of a build page on a server. A URL names its build by the last
/// number of its path, such as `/buildConfiguration/<type>/1100229901`, or by `buildId=` in its query. The default
/// server is one that [parse_server] answered.
pub(crate) fn parse_build(given: &str, default_server: &str) -> Result<BuildRef, Refusal> {
    let given = given.trim();
    let refuse = || {
        usage(format!(
            "--teamcity {given}: not a build id or the URL of a build page, such as {DEFAULT_TEAMCITY_URL}/build/1100229901"
        ))
    };
    if let Ok(id) = given.parse::<u64>() {
        return Ok(BuildRef {
            server: default_server.to_owned(),
            id,
        });
    }
    let Some((scheme, rest)) = given.split_once("://") else {
        return Err(refuse());
    };
    let (authority, tail) = rest.split_at(rest.find(['/', '?', '#']).unwrap_or(rest.len()));
    if authority.is_empty() || authority.contains('@') {
        return Err(refuse());
    }
    let server = server_of(scheme, authority).map_err(|why| usage(format!("--teamcity {given}: {why}")))?;
    let (path, query) = tail.split_once('?').unwrap_or((tail, ""));
    let (path, _) = path.split_once('#').unwrap_or((path, ""));
    let query = query.split_once('#').map_or(query, |(query, _)| query);
    let from_query = query
        .split('&')
        .find_map(|pair| pair.strip_prefix("buildId="))
        .and_then(|id| id.parse::<u64>().ok());
    let from_path = path
        .split('/')
        .rev()
        .find(|segment| !segment.is_empty())
        .and_then(|segment| segment.parse::<u64>().ok());
    let id = from_query.or(from_path).ok_or_else(refuse)?;
    Ok(BuildRef { server, id })
}

// --- the REST API ------------------------------------------------------------------------------------------------

/// The REST API of one TeamCity server: a GET of a path under `/app/rest/`, into memory or into a file.
pub(crate) trait Rest {
    fn get(&self, path: &str) -> Result<Vec<u8>, Refusal>;
    fn download(&self, path: &str, to: &Path) -> Result<(), Refusal>;
    /// The program that makes the requests, for a test and a log line.
    fn program(&self) -> &'static str;
}

/// The client of a server: `curl` with `TEAMCITY_TOKEN` when the server is `token_server`, the server of
/// `--teamcity-url`, and the TeamCity CLI otherwise. Both servers are ones that [parse_server] or [parse_build]
/// answered.
pub(crate) fn client(server: &str, token_server: &str, variable: &dyn Fn(&str) -> Option<String>) -> Box<dyn Rest> {
    match token(variable).filter(|_| server == token_server) {
        Some(token) => Box::new(Curl {
            program: PathBuf::from("curl"),
            server: server.to_owned(),
            config: curl_config(&token),
            max_time: None,
        }),
        None => Box::new(TeamCityCli { server: server.to_owned() }),
    }
}

/// The token of `TEAMCITY_TOKEN`, when it is set and not blank.
fn token(variable: &dyn Fn(&str) -> Option<String>) -> Option<String> {
    variable("TEAMCITY_TOKEN")
        .map(|token| token.trim().to_owned())
        .filter(|token| !token.is_empty())
}

/// The client of a build step for the server that its agent talks to. It is `curl` with the build's own credentials,
/// `teamcity.auth.userId` and `teamcity.auth.password`. TeamCity gives them to each build for its REST API. A request
/// has 60 seconds.
pub(crate) fn agent_client(server: &str, user: &str, password: &str) -> Box<dyn Rest> {
    Box::new(agent_curl(PathBuf::from("curl"), server, user, password))
}

/// The `curl` of [agent_client], with the program to run.
fn agent_curl(program: PathBuf, server: &str, user: &str, password: &str) -> Curl {
    Curl {
        program,
        server: server.trim_end_matches('/').to_owned(),
        config: format!("user = \"{}:{}\"\n", config_escape(user), config_escape(password)),
        max_time: Some(60),
    }
}

struct Curl {
    /// `curl`, or a fake of it in a test.
    program: PathBuf,
    server: String,
    /// The configuration that carries the credentials, which `curl` reads on its standard input.
    config: String,
    /// The longest time of one request in seconds, or `None` for no limit.
    max_time: Option<u32>,
}

/// The `curl` configuration that carries the token: one header line, with the quote and the backslash escaped as a
/// configuration file spells them, and no line break.
fn curl_config(token: &str) -> String {
    format!("header = \"Authorization: Bearer {}\"\n", config_escape(token))
}

/// A value of a `curl` configuration line: the quote and the backslash escaped, and no line break.
fn config_escape(value: &str) -> String {
    value.replace(['\r', '\n'], "").replace('\\', "\\\\").replace('"', "\\\"")
}

impl Curl {
    fn run(&self, path: &str, output: Option<&Path>) -> Result<Vec<u8>, Refusal> {
        let url = format!("{}{path}", self.server);
        let mut command = Command::new(&self.program);
        command.args([
            "--fail",
            "--silent",
            "--show-error",
            "--location",
            "--globoff",
            "--connect-timeout",
            "30",
            "--config",
            "-",
        ]);
        command.args(["-H", "Accept: application/json"]);
        if let Some(output) = output {
            command.arg("--output").arg(output);
        }
        if let Some(seconds) = self.max_time {
            command.args(["--max-time", &seconds.to_string()]);
        }
        command.arg(&url);
        // The credentials go through the standard input, so no process listing shows them.
        run_program(command, Some(self.config.as_bytes()), true, &format!("curl {url}"))
    }
}

impl Rest for Curl {
    fn get(&self, path: &str) -> Result<Vec<u8>, Refusal> {
        self.run(path, None)
    }

    fn download(&self, path: &str, to: &Path) -> Result<(), Refusal> {
        self.run(path, Some(to)).map(drop)
    }

    fn program(&self) -> &'static str {
        "curl"
    }
}

struct TeamCityCli {
    server: String,
}

impl TeamCityCli {
    fn command(&self, path: &str) -> Command {
        let mut command = Command::new("teamcity");
        // The CLI reads TEAMCITY_TOKEN too, and the token is not for this server.
        command
            .args(["api", path, "--raw", "--no-input"])
            .env("TEAMCITY_URL", &self.server)
            .env_remove("TEAMCITY_TOKEN");
        command
    }
}

impl Rest for TeamCityCli {
    fn get(&self, path: &str) -> Result<Vec<u8>, Refusal> {
        run_program(self.command(path), None, true, &format!("teamcity api {path}"))
    }

    fn download(&self, path: &str, to: &Path) -> Result<(), Refusal> {
        let file = fs::File::create(to).map_err(|error| failed(format!("cannot write {}: {error}", to.display())))?;
        let mut command = self.command(path);
        command.stdout(file);
        run_program(command, None, false, &format!("teamcity api {path}")).map(drop)
    }

    fn program(&self) -> &'static str {
        "teamcity api"
    }
}

/// Runs a client program, and answers its standard output when `capture` is set; without it, the caller gave the
/// output its file. A program that is not there, or that fails, is a refusal that names it and says what it printed.
fn run_program(mut command: Command, input: Option<&[u8]>, capture: bool, what: &str) -> Result<Vec<u8>, Refusal> {
    command.stdin(if input.is_some() { Stdio::piped() } else { Stdio::null() });
    command.stderr(Stdio::piped());
    if capture {
        command.stdout(Stdio::piped());
    }
    let program = command.get_program().to_string_lossy().into_owned();
    let mut child = command.spawn().map_err(|error| {
        let hint = if program == "teamcity" {
            "; install the TeamCity CLI and run `teamcity auth login`, or set TEAMCITY_TOKEN for the server of --teamcity-url"
        } else {
            ""
        };
        failed(format!("cannot run {program}: {error}{hint}"))
    })?;
    if let (Some(input), Some(mut stdin)) = (input, child.stdin.take()) {
        stdin
            .write_all(input)
            .map_err(|error| failed(format!("{what}: cannot write the standard input: {error}")))?;
    }
    let output = child.wait_with_output().map_err(|error| failed(format!("{what}: {error}")))?;
    if !output.status.success() {
        // One line: the refusal of a root is one line of the listing and the log.
        let printed = String::from_utf8_lossy(&output.stderr)
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ");
        let printed = printed.as_str();
        let printed = if printed.is_empty() {
            String::from_utf8_lossy(&output.stdout).trim().chars().take(400).collect()
        } else {
            printed.chars().take(400).collect::<String>()
        };
        return Err(failed(format!("{what} failed ({}): {printed}", output.status)));
    }
    Ok(output.stdout)
}

fn failed(message: impl Into<String>) -> Refusal {
    Refusal::new(TEAMCITY_FAILED, Exit::Broken, message)
}

// --- builds ------------------------------------------------------------------------------------------------------

/// A build as the REST API describes it.
#[derive(Deserialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Build {
    pub id: u64,
    #[serde(default)]
    pub number: String,
    #[serde(default)]
    pub status: String,
    #[serde(default)]
    pub state: String,
    #[serde(default)]
    pub composite: bool,
    #[serde(default)]
    pub web_url: String,
    #[serde(default)]
    pub build_type: BuildType,
    #[serde(default, rename = "snapshot-dependencies")]
    pub snapshot_dependencies: Builds,
}

#[derive(Deserialize, Clone, PartialEq, Eq, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub(crate) struct BuildType {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub name: String,
}

#[derive(Deserialize, Clone, PartialEq, Eq, Debug, Default)]
pub(crate) struct Builds {
    #[serde(default)]
    pub build: Vec<Build>,
}

#[derive(Deserialize)]
struct Artifacts {
    #[serde(default)]
    file: Vec<Artifact>,
}

#[derive(Deserialize)]
struct Artifact {
    name: String,
    #[serde(default)]
    size: Option<u64>,
}

impl Build {
    /// The build as the origin of its bundles: the name of its configuration, such as `Air Plugin UI Tests / 1`, its
    /// id and its page.
    pub(crate) fn origin(&self) -> Origin {
        Origin {
            name: self.build_type.name.clone(),
            id: self.id.to_string(),
            url: (!self.web_url.is_empty()).then(|| self.web_url.clone()),
        }
    }

    /// The build as one line of the start-up output.
    fn describe(&self) -> String {
        let name = if self.build_type.name.is_empty() {
            &self.build_type.id
        } else {
            &self.build_type.name
        };
        let verdict: Vec<String> = [self.state.clone(), self.status.to_lowercase()]
            .into_iter()
            .filter(|part| !part.is_empty())
            .collect();
        let mut facts = self.id.to_string();
        if !verdict.is_empty() {
            facts.push_str(", ");
            facts.push_str(&verdict.join(" "));
        }
        format!("{name} #{} ({facts})", self.number)
    }
}

fn read_build(rest: &dyn Rest, id: u64) -> Result<Build, Refusal> {
    let body = rest.get(&format!("/app/rest/builds/id:{id}?fields={BUILD_FIELDS}"))?;
    serde_json::from_slice(&body).map_err(|error| failed(format!("build {id}: the REST answer does not read: {error}")))
}

/// The builds that a build stands for: itself when it ran, and the builds that ran under it when it is composite,
/// in the order of the snapshot dependencies, each once.
pub(crate) fn leaf_builds(rest: &dyn Rest, id: u64) -> Result<Vec<Build>, Refusal> {
    let mut leaves = Vec::new();
    let mut seen = HashSet::new();
    let mut reads = 0;
    let mut pending = vec![read_build(rest, id)?];
    while let Some(build) = pending.pop() {
        if !seen.insert(build.id) {
            continue;
        }
        if !build.composite {
            leaves.push(build);
            continue;
        }
        let mut children = Vec::new();
        for dependency in &build.snapshot_dependencies.build {
            if dependency.composite {
                reads += 1;
                if reads > MAX_BUILDS {
                    return Err(failed(format!("build {id} stands for more than {MAX_BUILDS} composite builds")));
                }
                children.push(read_build(rest, dependency.id)?);
            } else {
                children.push(dependency.clone());
            }
        }
        // A stack: the first dependency is taken first.
        pending.extend(children.into_iter().rev());
    }
    Ok(leaves)
}

/// What [fetch_logs] did with one build.
#[derive(PartialEq, Eq, Debug)]
pub(crate) enum Fetched {
    Downloaded(PathBuf),
    Cached(PathBuf),
    /// The build published no `bazel.logs.zip`.
    Missing,
}

/// The directory of a build of a server in the cache: `tc-<server hash>-<build id>`, because two servers can have
/// builds of one id.
pub(crate) fn build_dir(cache_dir: &Path, server: &str, id: u64) -> PathBuf {
    let digest = Sha256::digest(server.as_bytes());
    cache_dir.join(format!("tc-{}-{id}", hex::encode(&digest[..4])))
}

/// The `bazel.logs.zip` of one build of `server` in the cache, downloaded unless a complete copy is there. A copy is
/// complete when it has the size the artifact listing gives. Each use sets the time of the build's directory, so the
/// prune of the cache by age keeps what is read. `log` gets a line before a download.
pub(crate) fn fetch_logs(rest: &dyn Rest, build: &Build, server: &str, cache_dir: &Path, log: &dyn Fn(String)) -> Result<Fetched, Refusal> {
    let listing = rest.get(&format!(
        "/app/rest/builds/id:{}/artifacts/children/?fields=file(name,size)",
        build.id
    ))?;
    let artifacts: Artifacts = serde_json::from_slice(&listing)
        .map_err(|error| failed(format!("build {}: the artifact listing does not read: {error}", build.id)))?;
    let Some(artifact) = artifacts.file.iter().find(|file| file.name == LOGS_ARTIFACT) else {
        return Ok(Fetched::Missing);
    };
    let dir = build_dir(cache_dir, server, build.id);
    let target = dir.join(LOGS_ARTIFACT);
    let io_failed = |error: std::io::Error| failed(format!("cannot write {}: {error}", target.display()));
    if let (Ok(info), Some(size)) = (fs::metadata(&target), artifact.size)
        && info.len() == size
    {
        let _ = set_modified(&dir, SystemTime::now());
        return Ok(Fetched::Cached(target));
    }
    let size = artifact
        .size
        .map_or_else(String::new, |size| format!(", {} MB", size.div_ceil(1 << 20)));
    log(format!("    {}: downloading {LOGS_ARTIFACT}{size}", build.describe()));
    fs::create_dir_all(&dir).map_err(io_failed)?;
    let temporary = tempfile::Builder::new()
        .prefix(".bazel.logs-")
        .tempfile_in(&dir)
        .map_err(io_failed)?;
    rest.download(
        &format!("/app/rest/builds/id:{}/artifacts/files/{LOGS_ARTIFACT}", build.id),
        temporary.path(),
    )?;
    let written = fs::metadata(temporary.path()).map_err(io_failed)?.len();
    if let Some(size) = artifact.size
        && written != size
    {
        return Err(failed(format!(
            "build {}: the download of {LOGS_ARTIFACT} has {written} bytes, the listing says {size}",
            build.id
        )));
    }
    avl_base::fs::move_into_place(temporary, &target).map_err(io_failed)?;
    Ok(Fetched::Downloaded(target))
}

/// The roots of the `--teamcity` values: one for each build that ran and published its `bazel.logs.zip`. A build
/// that cannot be resolved or downloaded is a root with its error, and the other builds still serve. Only a value
/// that is no build is refused. `log` gets one line for each build.
pub(crate) fn teamcity_roots(
    given: &[String],
    default_server: &str,
    cache_dir: &Path,
    client: &dyn Fn(&str) -> Box<dyn Rest>,
    log: &dyn Fn(String),
) -> Result<Vec<Root>, Refusal> {
    let builds = given
        .iter()
        .map(|value| parse_build(value, default_server))
        .collect::<Result<Vec<_>, _>>()?;
    let failed_root = |path: PathBuf, origin: Option<Origin>, refused: Refusal| Root {
        error: Some(refused.message),
        origin,
        ..Root::new(RootKind::TeamCity, path)
    };
    let mut roots = Vec::new();
    for build in builds {
        let rest = client(&build.server);
        let leaves = match leaf_builds(rest.as_ref(), build.id) {
            Ok(leaves) => leaves,
            Err(refused) => {
                log(format!("  teamcity {} ({}): {}", build.id, rest.program(), refused.message));
                let origin = Origin {
                    name: format!("TeamCity build {}", build.id),
                    id: build.id.to_string(),
                    url: None,
                };
                roots.push(failed_root(build_dir(cache_dir, &build.server, build.id), Some(origin), refused));
                continue;
            }
        };
        log(format!(
            "  teamcity {} ({}): {} build(s) ran",
            build.id,
            rest.program(),
            leaves.len()
        ));
        for leaf in leaves {
            match fetch_logs(rest.as_ref(), &leaf, &build.server, cache_dir, log) {
                Ok(Fetched::Downloaded(path) | Fetched::Cached(path)) => {
                    log(format!("    {}: {}", leaf.describe(), path.display()));
                    roots.push(Root {
                        origin: Some(leaf.origin()),
                        ..Root::new(RootKind::TeamCity, path)
                    });
                }
                Ok(Fetched::Missing) => log(format!("    {}: no {LOGS_ARTIFACT}", leaf.describe())),
                Err(refused) => {
                    log(format!("    {}: {}", leaf.describe(), refused.message));
                    let path = build_dir(cache_dir, &build.server, leaf.id).join(LOGS_ARTIFACT);
                    roots.push(failed_root(path, Some(leaf.origin()), refused));
                }
            }
        }
    }
    Ok(roots)
}

#[cfg(test)]
mod tests;
