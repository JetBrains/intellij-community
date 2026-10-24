//! The credentials that a `--host` machine needs for a run, for `--prepare`: which ones, and how one file of them is
//! merged into the file of the host. Only the person at the terminal starts the copy, never a run.
//!
//! Two files, and only the entries that the checkout names:
//!
//! - `~/.netrc`: the `machine` entry of each host of a remote cache, executor, downloader or event service that the
//!   Bazel configuration of the checkout names (`.bazelrc` and the files it imports).
//! - `~/.m2/settings.xml`: the `<server>` entry of each repository of `.idea/jarRepositories.xml` on the Space package
//!   host. `IJPrivatePackagesAuthorizer` asks for a browser login when one of them has no credential.
//!
//! The host file is read, merged here, and written back through the stdin of ssh, atomically and with mode 600. An
//! entry of another machine or server stays. A file that the merge leaves equal is not written. No message names a
//! login, a password or a line of either file: the messages name only a host or a server id.

use std::ffi::OsString;
use std::ops::Range;
use std::path::Path;

use bt_core::regex;
use bt_core::runtime::{Runtime, repo_file};

/// The repository Bazel configuration that names the remote services.
const BAZELRC: &str = ".bazelrc";

/// The Bazel options whose value is the URL of a remote service.
const SERVICE_OPTIONS: [&str; 4] = ["--remote_cache", "--remote_executor", "--remote_downloader", "--bes_backend"];

/// The option of a remote cache, whose answer `--prepare` checks.
pub(crate) const REMOTE_CACHE_OPTION: &str = "--remote_cache";

/// The Maven repositories of the checkout.
const JAR_REPOSITORIES: &str = ".idea/jarRepositories.xml";

/// The host of the Space package repositories, which need a credential.
const SPACE_PACKAGES_HOST: &str = "packages.jetbrains.team";

/// How deep `import` lines of a bazelrc are followed.
const MAX_IMPORT_DEPTH: usize = 4;

/// The two files, relative to the home directory on both machines.
pub(crate) const NETRC: &str = ".netrc";
pub(crate) const SETTINGS_XML: &str = ".m2/settings.xml";

/// One remote service of the Bazel configuration.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Service {
    pub(crate) option: String,
    pub(crate) host: String,
}

/// The remote services that the Bazel configuration of the checkout names, in order and once each.
pub(crate) fn services(runtime: &dyn Runtime) -> Vec<Service> {
    let mut services = Vec::new();
    collect_services(runtime, BAZELRC, 0, &mut services);
    services
}

/// The hosts of [`services`], once each.
pub(crate) fn service_hosts(services: &[Service]) -> Vec<String> {
    let mut hosts: Vec<String> = Vec::new();
    for service in services {
        if !hosts.contains(&service.host) {
            hosts.push(service.host.clone());
        }
    }
    hosts
}

fn collect_services(runtime: &dyn Runtime, relative: &str, depth: usize, services: &mut Vec<Service>) {
    let Ok(text) = runtime.read_text_file(&repo_file(runtime, relative)) else {
        return;
    };
    for line in text.lines() {
        let mut words = line.split_whitespace();
        match words.next() {
            Some("import" | "try-import") if depth < MAX_IMPORT_DEPTH => {
                if let Some(imported) = words.next().and_then(|path| path.strip_prefix("%workspace%/")) {
                    collect_services(runtime, imported, depth + 1, services);
                }
            }
            Some(command) if !command.starts_with('#') => {
                for word in words {
                    let Some((option, value)) = word.split_once('=') else {
                        continue;
                    };
                    if SERVICE_OPTIONS.contains(&option)
                        && let Some(host) = url_host(value)
                    {
                        let service = Service {
                            option: option.to_owned(),
                            host: host.to_owned(),
                        };
                        if !services.contains(&service) {
                            services.push(service);
                        }
                    }
                }
            }
            _ => {}
        }
    }
}

/// The host of a URL such as `https://cache.example.com/path` or `grpcs://cache.example.com:443`.
fn url_host(url: &str) -> Option<&str> {
    let (_, rest) = url.split_once("://")?;
    let authority = rest.split('/').next()?;
    let host = authority.rsplit('@').next()?.split(':').next()?;
    (!host.is_empty()).then_some(host)
}

/// The ids of the Maven repositories of the checkout on the Space package host.
pub(crate) fn space_repository_ids(runtime: &dyn Runtime) -> Vec<String> {
    let Ok(text) = runtime.read_text_file(&repo_file(runtime, JAR_REPOSITORIES)) else {
        return Vec::new();
    };
    let mut ids: Vec<String> = Vec::new();
    for block in regex!(r"(?s)<remote-repository>(.*?)</remote-repository>").captures_iter(&text) {
        let body = &block[1];
        let id = regex!(r#"<option name="id" value="([^"]*)""#).captures(body);
        let url = regex!(r#"<option name="url" value="([^"]*)""#).captures(body);
        if let (Some(id), Some(url)) = (id, url)
            && url[1].contains(SPACE_PACKAGES_HOST)
            && !ids.iter().any(|known| known == &id[1])
        {
            ids.push(id[1].to_owned());
        }
    }
    ids
}

/// One credential file: how its entries are found, and how one is put into the text of the host.
pub(crate) trait Format {
    /// The name of an entry in a message: "netrc entry" or "Maven server".
    fn noun(&self) -> &'static str;

    /// The byte range of the entry of `name` in `text`.
    fn find(&self, text: &str, name: &str) -> Option<Range<usize>>;

    /// `text` with `entry` added, or `None` when the text has no place for it.
    fn append(&self, text: &str, entry: &str) -> Option<String>;
}

/// What one merge did to one entry.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Merged {
    /// The host entry was added or replaced.
    Written,
    /// The host entry equals the local one.
    Current,
    /// This machine has no entry to copy.
    NoLocal,
    /// The host text has no place for the entry.
    NoPlace,
}

/// `host` with the local entry of `name` in place of its own entry, or after its entries.
pub(crate) fn merge(format: &dyn Format, host: &str, local: &str, name: &str) -> (String, Merged) {
    let Some(local_range) = format.find(local, name) else {
        return (host.to_owned(), Merged::NoLocal);
    };
    let entry = local[local_range].trim_end();
    match format.find(host, name) {
        Some(range) if host[range.clone()].trim_end() == entry => (host.to_owned(), Merged::Current),
        Some(range) => {
            let end = range.start + host[range.clone()].trim_end().len();
            (format!("{}{entry}{}", &host[..range.start], &host[end..]), Merged::Written)
        }
        None => match format.append(host, entry) {
            Some(merged) => (merged, Merged::Written),
            None => (host.to_owned(), Merged::NoPlace),
        },
    }
}

/// Copies the entries of `names` from the local file to the same file on the host, and answers one line per
/// outcome. A line names entries by host or id only.
pub(crate) fn copy_entries(runtime: &dyn Runtime, alias: &str, id: &str, file: &str, names: &[String], format: &dyn Format) -> Vec<String> {
    let noun = format.noun();
    let Some(home) = runtime.home_dir() else {
        return vec![format!("no home directory, so no {noun} is copied")];
    };
    let local = runtime.read_text_file(&home.join(file)).unwrap_or_default();
    // `LogLevel=ERROR` of the ssh options keeps the warnings of ssh out of the text, and a failed ssh exits nonzero.
    let read = crate::ssh::remote_call(runtime, &ssh(alias, &format!("cat {file} 2>/dev/null || true")));
    if read.exit_code != 0 {
        return vec![format!("cannot read ~/{file} on {alias}: ssh exited with {}", read.exit_code)];
    }
    let mut text = read.output;
    let mut by_outcome: [(Merged, Vec<&str>); 4] = [
        (Merged::Written, Vec::new()),
        (Merged::Current, Vec::new()),
        (Merged::NoLocal, Vec::new()),
        (Merged::NoPlace, Vec::new()),
    ];
    for name in names {
        let (merged, outcome) = merge(format, &text, &local, name);
        text = merged;
        if let Some((_, names)) = by_outcome.iter_mut().find(|(kind, _)| *kind == outcome) {
            names.push(name);
        }
    }
    let mut lines = Vec::new();
    for (outcome, names) in &by_outcome {
        if names.is_empty() {
            continue;
        }
        let names = names.join(", ");
        lines.push(match outcome {
            Merged::Written => format!("synced {noun} for {names}"),
            Merged::Current => format!("current {noun} for {names}"),
            Merged::NoLocal => format!("no local {noun} for {names}"),
            Merged::NoPlace => format!("~/{file} on {alias} has no place for the {noun} of {names}"),
        });
    }
    if by_outcome[0].1.is_empty() {
        return lines;
    }
    let wrote = runtime.spawn_with_input(&ssh(alias, &atomic_write_command(file, id)), &text);
    if wrote.exit_code != 0 {
        lines = vec![format!("cannot write ~/{file} on {alias}: ssh exited with {}", wrote.exit_code)];
    }
    lines
}

/// The shell text that replaces a file in the home directory of the host with the stdin, readable by its owner only.
pub(crate) fn atomic_write_command(path: &str, id: &str) -> String {
    let dir = Path::new(path)
        .parent()
        .map(|dir| dir.to_string_lossy().into_owned())
        .unwrap_or_default();
    let temp = format!("{path}.bt-remote-{id}");
    let mkdir = if dir.is_empty() {
        String::new()
    } else {
        format!("mkdir -p {dir} && ")
    };
    format!("umask 077 && {mkdir}cat > {temp} && chmod 600 {temp} && mv -f {temp} {path}")
}

/// A netrc: each `machine` or `default` token starts an entry that runs to the next one.
pub(crate) struct NetrcFormat;

impl Format for NetrcFormat {
    fn noun(&self) -> &'static str {
        "netrc entry"
    }

    fn find(&self, text: &str, name: &str) -> Option<Range<usize>> {
        let tokens = tokens(text);
        let index = tokens.windows(2).position(|pair| pair[0].1 == "machine" && pair[1].1 == name)?;
        let start = tokens[index].0;
        let end = tokens[index + 1..]
            .iter()
            .find(|(_, token)| matches!(*token, "machine" | "default"))
            .map_or(text.len(), |(next, _)| *next);
        Some(start..end)
    }

    fn append(&self, text: &str, entry: &str) -> Option<String> {
        let separator = if text.is_empty() || text.ends_with('\n') { "" } else { "\n" };
        Some(format!("{text}{separator}{entry}\n"))
    }
}

/// The whitespace-separated tokens of a text, with their byte offsets.
fn tokens(text: &str) -> Vec<(usize, &str)> {
    let mut tokens = Vec::new();
    let mut start = None;
    for (index, char) in text.char_indices() {
        match (char.is_whitespace(), start) {
            (true, Some(begin)) => {
                tokens.push((begin, &text[begin..index]));
                start = None;
            }
            (false, None) => start = Some(index),
            _ => {}
        }
    }
    if let Some(begin) = start {
        tokens.push((begin, &text[begin..]));
    }
    tokens
}

/// A Maven settings.xml: an entry is one `<server>` element, found by its `<id>`.
pub(crate) struct SettingsFormat;

/// The text of a settings.xml that the host does not have yet.
const EMPTY_SETTINGS: &str = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<settings>\n</settings>\n";

impl Format for SettingsFormat {
    fn noun(&self) -> &'static str {
        "Maven server"
    }

    fn find(&self, text: &str, name: &str) -> Option<Range<usize>> {
        regex!(r"(?s)<server>.*?</server>").find_iter(text).find_map(|found| {
            let id = regex!(r"<id>\s*([^<]*?)\s*</id>").captures(found.as_str())?;
            (&id[1] == name).then(|| found.range())
        })
    }

    fn append(&self, text: &str, entry: &str) -> Option<String> {
        let text = if text.trim().is_empty() { EMPTY_SETTINGS } else { text };
        if let Some(close) = text.find("</servers>") {
            let line = line_start(text, close);
            return Some(format!("{}    {entry}\n{}", &text[..line], &text[line..]));
        }
        let close = text.rfind("</settings>")?;
        let line = line_start(text, close);
        Some(format!(
            "{}  <servers>\n    {entry}\n  </servers>\n{}",
            &text[..line],
            &text[line..]
        ))
    }
}

/// The start of the line that holds `index`, when only blanks stand before it on that line; else `index` itself.
fn line_start(text: &str, index: usize) -> usize {
    let start = text[..index].rfind('\n').map_or(0, |newline| newline + 1);
    if text[start..index].trim().is_empty() { start } else { index }
}

fn ssh(alias: &str, command: &str) -> Vec<OsString> {
    crate::ssh::ssh(alias, command)
}

#[cfg(test)]
mod tests;
