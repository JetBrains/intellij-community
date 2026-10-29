//! The agent skill of yonder: `skill/SKILL.md` and the `API.md` documents of this build, which `yonder skill install`
//! writes into `~/.claude/skills/yonder/`. The binary carries the files, so the install works outside the monorepo.
//!
//! The marker `.yonder-skill.sha256` in the skill directory lists the SHA-256 of each file as the install wrote it, in
//! the format of `shasum -a 256`. So an install tells a copy of another build, which it replaces, from a copy with
//! local edits, which it keeps unless `--force`. The build of a copy is the SHA-256 of its marker, so an answer names
//! the build that it replaced. Two builds have no order: the copy of the running binary always wins.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use yonder_client::{Exit, Refusal, code};
use yonder_link::digest::{Sha256, is_sha256};

/// The files of the skill: the path in the skill directory and the text of this build.
const FILES: [(&str, &str); 4] = [
    ("SKILL.md", include_str!("../../../skill/SKILL.md")),
    ("references/agent.md", include_str!("../../yonderd/API.md")),
    ("references/client.md", include_str!("../../../crates/yonder-client/API.md")),
    ("references/link.md", include_str!("../../../crates/yonder-link/API.md")),
];

/// The marker of an installed copy.
const MARKER: &str = ".yonder-skill.sha256";

/// The directory of the references in the skill directory.
const REFERENCES: &str = "references/";

/// The suffix of a file while it is written. The rename gives it its name.
const PART: &str = ".yonder-part";

/// The hex digits of a build in an answer.
const BUILD_DIGITS: usize = 12;

/// The refusal of an install over a copy with local edits.
pub(crate) const SKILL_EDITED: &str = "skill_edited";

/// The text of `SKILL.md` of this build.
pub(crate) const SKILL: &str = FILES[0].1;

/// The skill directory of the user: `~/.claude/skills/yonder`, or `None` without a home directory.
pub(crate) fn user_dir() -> Option<PathBuf> {
    std::env::home_dir()
        .filter(|home| !home.as_os_str().is_empty())
        .map(|home| home.join(".claude").join("skills").join("yonder"))
}

/// The build of the skill of this binary: the first hex digits of the SHA-256 of its marker.
pub(crate) fn build() -> String {
    build_of(marker_text().as_bytes())
}

/// What an install did.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Installed {
    /// The directory had no copy.
    Fresh,
    /// The directory had an unedited copy of the build that this field names.
    Replaced(String),
    /// The directory had a copy with local edits, and the install had `force`.
    ReplacedEdited,
    /// The directory has the files of this build.
    Current,
}

/// The copy of the skill in a skill directory.
#[derive(Debug, PartialEq, Eq)]
enum Found {
    /// No file of the skill and no marker.
    Missing,
    /// The files of this build.
    Current,
    /// The files of another build, as its marker lists them: the paths of that build, and the build.
    Other { paths: Vec<String>, build: String },
    /// Files that differ from their marker, or files without a marker. The text names them.
    Edited(String),
}

/// Writes the skill of this build into `dir`. A copy with local edits refuses with `skill_edited`, unless `force`.
pub(crate) fn install(dir: &Path, force: bool) -> Result<Installed, Refusal> {
    match found_in(dir).map_err(|error| file_refusal(dir, &error))? {
        Found::Current => Ok(Installed::Current),
        Found::Missing => write(dir, &[]).map(|()| Installed::Fresh),
        Found::Other { paths, build } => write(dir, &paths).map(|()| Installed::Replaced(build)),
        Found::Edited(_) if force => write(dir, &marked(dir)).map(|()| Installed::ReplacedEdited),
        Found::Edited(edits) => Err(Refusal::new(SKILL_EDITED, Exit::Refused, edited_text(dir, &edits))),
    }
}

/// Replaces a copy of another build in `dir` with the one of this build, for `yonder upgrade`. A missing copy stays
/// missing, and a copy with local edits stays. Answers the line to say, or `None` when nothing changed.
pub(crate) fn refresh(dir: &Path) -> Option<String> {
    match found_in(dir) {
        Ok(Found::Missing | Found::Current) => None,
        Ok(Found::Other { paths, build: old }) => Some(write(dir, &paths).map_or_else(
            |refusal| warning(&refusal.message),
            |()| {
                format!(
                    "yonder: replaced the skill of build {old} with build {} in {}",
                    build(),
                    dir.display()
                )
            },
        )),
        Ok(Found::Edited(edits)) => Some(format!("yonder: {}", edited_text(dir, &edits))),
        Err(error) => Some(warning(&file_refusal(dir, &error).message)),
    }
}

/// The answer of `yonder skill install`.
pub(crate) fn installed_text(dir: &Path, installed: &Installed) -> String {
    let (dir, build) = (dir.display(), build());
    match installed {
        Installed::Fresh => format!("installed the yonder skill of build {build} in {dir}"),
        Installed::Replaced(old) => format!("replaced the yonder skill of build {old} with build {build} in {dir}"),
        Installed::ReplacedEdited => format!("replaced the edited yonder skill with build {build} in {dir}"),
        Installed::Current => format!("the yonder skill of build {build} in {dir} is current"),
    }
}

fn edited_text(dir: &Path, edits: &str) -> String {
    format!(
        "the skill in {} has local edits ({edits}), so it stays; `yonder skill install --force` replaces it",
        dir.display()
    )
}

fn warning(message: &str) -> String {
    format!("yonder: warning: {message}")
}

fn file_refusal(dir: &Path, error: &io::Error) -> Refusal {
    Refusal::new(
        code::CONFIG,
        Exit::Refused,
        format!("cannot read or write the skill in {}: {error}", dir.display()),
    )
}

/// The copy in `dir`. The files of this build with their text count as current, with a marker or without one.
fn found_in(dir: &Path) -> io::Result<Found> {
    let mut present = false;
    let mut current = true;
    for (path, text) in FILES {
        match read(&dir.join(path))? {
            Some(bytes) => {
                present = true;
                current &= bytes == text.as_bytes();
            }
            None => current = false,
        }
    }
    if current {
        return Ok(Found::Current);
    }
    let Some(bytes) = read(&dir.join(MARKER))? else {
        return Ok(if present {
            Found::Edited(format!("no {MARKER}"))
        } else {
            Found::Missing
        });
    };
    let marker = parse_marker(&String::from_utf8_lossy(&bytes));
    if marker.is_empty() {
        return Ok(Found::Edited(format!("{MARKER} lists no file")));
    }
    let mut edited = Vec::new();
    for (path, sha256) in &marker {
        match read(&dir.join(path))? {
            Some(bytes) if sha256_of(&bytes) == *sha256 => {}
            Some(_) => edited.push(path.clone()),
            None => edited.push(format!("{path} is missing")),
        }
    }
    if !edited.is_empty() {
        return Ok(Found::Edited(edited.join(", ")));
    }
    Ok(Found::Other {
        paths: marker.into_keys().collect(),
        build: build_of(&bytes),
    })
}

/// The paths that the marker of `dir` lists. None without a readable marker.
fn marked(dir: &Path) -> Vec<String> {
    fs::read_to_string(dir.join(MARKER)).map_or_else(|_| Vec::new(), |marker| parse_marker(&marker).into_keys().collect())
}

/// The marker of this build: one line `<sha256>  <path>` per file.
fn marker_text() -> String {
    FILES
        .iter()
        .map(|(path, text)| format!("{}  {path}\n", sha256_of(text.as_bytes())))
        .collect()
}

fn build_of(marker: &[u8]) -> String {
    let mut build = sha256_of(marker);
    build.truncate(BUILD_DIGITS);
    build
}

/// Writes the files of this build, then the marker, each through a part file beside it. Removes each path of `old`
/// that this build lacks.
fn write(dir: &Path, old: &[String]) -> Result<(), Refusal> {
    let refusal = |error: io::Error| file_refusal(dir, &error);
    for (path, text) in FILES {
        write_whole(&dir.join(path), text.as_bytes()).map_err(refusal)?;
    }
    for path in old {
        if FILES.iter().all(|(own, _)| own != path) {
            match fs::remove_file(dir.join(path)) {
                Ok(()) => {}
                Err(error) if error.kind() == io::ErrorKind::NotFound => {}
                Err(error) => return Err(refusal(error)),
            }
        }
    }
    write_whole(&dir.join(MARKER), marker_text().as_bytes()).map_err(refusal)
}

/// The lines `<sha256>  <path>` of a marker, by path. A line of another shape, or a path that no skill file can
/// have, counts as no line.
fn parse_marker(text: &str) -> BTreeMap<String, String> {
    text.lines()
        .filter_map(|line| line.split_once("  "))
        .filter(|(sha256, path)| is_sha256(sha256) && is_skill_path(path))
        .map(|(sha256, path)| (path.to_owned(), sha256.to_owned()))
        .collect()
}

/// Whether a path can name a file of the skill: `SKILL.md`, or `references/<name>.md` with a name of ASCII letters,
/// digits, `.`, `_` and `-` that does not start with a dot.
fn is_skill_path(path: &str) -> bool {
    if path == "SKILL.md" {
        return true;
    }
    path.strip_prefix(REFERENCES)
        .and_then(|name| name.strip_suffix(".md"))
        .is_some_and(|stem| {
            !stem.is_empty()
                && !stem.starts_with('.')
                && stem
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
        })
}

fn sha256_of(bytes: &[u8]) -> String {
    let mut digest = Sha256::default();
    digest.update(bytes);
    digest.hex()
}

/// The bytes of `path`, or `None` when it is missing.
fn read(path: &Path) -> io::Result<Option<Vec<u8>>> {
    match fs::read(path) {
        Ok(bytes) => Ok(Some(bytes)),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error),
    }
}

fn write_whole(path: &Path, bytes: &[u8]) -> io::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut part = path.as_os_str().to_owned();
    part.push(PART);
    fs::write(&part, bytes)?;
    fs::rename(&part, path)
}

#[cfg(test)]
mod tests;
