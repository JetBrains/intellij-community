//! The TeamCity build that `teamcity-publish` runs in, as the agent's property files name it.
//!
//! The agent sets `TEAMCITY_BUILD_PROPERTIES_FILE` for each build step. That file holds the build's own properties, such
//! as `teamcity.buildType.id` and `teamcity.buildConfName`. Its `teamcity.configuration.properties.file` names a second
//! file with the configuration parameters, such as `teamcity.build.id` and `teamcity.serverUrl`. Both files are Java
//! properties files.
//!
//! The build's file also names the revision of the build, `build.vcs.number`, and the file of its changed files,
//! `teamcity.build.changedFiles.file`. The paths in that file are relative to `teamcity.build.checkoutDir`. A build of
//! one VCS root has all three. TeamCity leaves out the file of the changed files for a build without changes.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use avl_report::teamcity::is_build_step;
use avl_trace_tools::discover::Origin;

/// The variable of the agent that names the file of the build's properties.
const BUILD_PROPERTIES_FILE: &str = "TEAMCITY_BUILD_PROPERTIES_FILE";
/// The variable of a JetBrains build that names the build's page, `<server>/build/<build id>`.
const BUILD_URL: &str = "BUILD_URL";
/// The property of the build's file that names the file of the configuration parameters.
const CONFIGURATION_FILE: &str = "teamcity.configuration.properties.file";
/// The property of the build's file that names the file of the changed files of the build.
pub(crate) const CHANGED_FILES: &str = "teamcity.build.changedFiles.file";

/// The build that the step runs in.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Build {
    /// The root of the server that a person opens, without a slash at the end.
    pub server: String,
    pub build_type_id: String,
    pub id: String,
    /// The name of the build configuration, such as `Air Plugin UI Tests / 1`.
    pub name: String,
    /// The revision of the build, `build.vcs.number`.
    pub revision: Option<String>,
    /// The file of the changed files of the build, [CHANGED_FILES].
    pub changed_files: Option<PathBuf>,
    /// The checkout directory of the build, `teamcity.build.checkoutDir`. The paths of the changed files are relative
    /// to it.
    pub checkout_dir: Option<PathBuf>,
}

impl Build {
    /// The build of a step that runs on TeamCity. It is `Ok(None)` when the step is not a build step of TeamCity
    /// ([is_build_step]). Else it is the build that the two property files name, or why they do not name one. The
    /// server is the root of `BUILD_URL` when that is `<root>/build/<build id>`, else `teamcity.serverUrl`.
    pub(crate) fn of_step(variable: &dyn Fn(&str) -> Option<String>) -> Result<Option<Self>, String> {
        if !is_build_step(variable) {
            return Ok(None);
        }
        let properties = StepProperties::read(variable)?;
        let property = |key: &str| properties.get(key);
        let id = property("teamcity.build.id")?;
        let server = match build_url_root(variable, &id) {
            Some(root) => root,
            None => property("teamcity.serverUrl")?,
        };
        Ok(Some(Self {
            server: server.trim_end_matches('/').to_owned(),
            build_type_id: property("teamcity.buildType.id")?,
            name: property("teamcity.buildConfName")?,
            id,
            revision: properties.optional("build.vcs.number"),
            changed_files: properties.optional(CHANGED_FILES).map(PathBuf::from),
            checkout_dir: properties.optional("teamcity.build.checkoutDir").map(PathBuf::from),
        }))
    }

    /// Why the server of the build is not the root of `BUILD_URL`, when it is not. Then it is `teamcity.serverUrl`, which
    /// can be a proxy of the agent that a person cannot open.
    pub(crate) fn server_fallback(&self, variable: &dyn Fn(&str) -> Option<String>) -> Option<String> {
        build_url_root(variable, &self.id).is_none().then(|| {
            format!(
                "{BUILD_URL} does not name the page of build {}, so the origin uses teamcity.serverUrl {}",
                self.id, self.server
            )
        })
    }

    /// The build as the origin of its bundles, with the page of the build, as `serve --teamcity` names a build.
    pub(crate) fn origin(&self) -> Origin {
        Origin {
            name: self.name.clone(),
            id: self.id.clone(),
            url: Some(format!("{}/buildConfiguration/{}/{}", self.server, self.build_type_id, self.id)),
        }
    }
}

/// The properties of the two files of the agent for a step on TeamCity.
struct StepProperties {
    /// The file that `TEAMCITY_BUILD_PROPERTIES_FILE` names.
    file: String,
    properties: BTreeMap<String, String>,
}

impl StepProperties {
    fn read(variable: &dyn Fn(&str) -> Option<String>) -> Result<Self, String> {
        let file = variable(BUILD_PROPERTIES_FILE)
            .filter(|file| !file.trim().is_empty())
            .ok_or_else(|| format!("{BUILD_PROPERTIES_FILE} is not set"))?;
        let mut properties = read_properties(Path::new(&file))?;
        if let Some(configuration) = properties.get(CONFIGURATION_FILE).cloned() {
            for (key, value) in read_properties(Path::new(&configuration))? {
                properties.entry(key).or_insert(value);
            }
        }
        Ok(Self { file, properties })
    }

    /// The value of `key`, or why there is none.
    fn get(&self, key: &str) -> Result<String, String> {
        self.properties
            .get(key)
            .filter(|value| !value.trim().is_empty())
            .cloned()
            .ok_or_else(|| format!("the build properties of {} name no {key}", self.file))
    }

    /// The value of `key`, or `None` when the files name none.
    fn optional(&self, key: &str) -> Option<String> {
        self.get(key).ok()
    }
}

/// The entries of a properties file, or why it does not read.
fn read_properties(path: &Path) -> Result<BTreeMap<String, String>, String> {
    let content = fs::read(path).map_err(|error| format!("cannot read {}: {error}", path.display()))?;
    Ok(parse_properties(&String::from_utf8_lossy(&content)))
}

/// The entries of a Java properties file. A line that starts with `#` or `!` is a comment. A line that ends in an odd
/// number of `\` goes on in the next line. The key ends at the first `=`, `:` or white space that no `\` escapes.
/// The escapes are `\t`, `\n`, `\r`, `\f`, `\uXXXX`, and `\` before any other character for the character itself. A
/// later entry of one key replaces the earlier one.
pub(super) fn parse_properties(text: &str) -> BTreeMap<String, String> {
    let mut entries = BTreeMap::new();
    let mut lines = text.lines();
    while let Some(line) = lines.next() {
        let mut logical = line.trim_start_matches(WHITE_SPACE).to_owned();
        if logical.is_empty() || logical.starts_with(['#', '!']) {
            continue;
        }
        while logical.bytes().rev().take_while(|byte| *byte == b'\\').count() % 2 == 1 {
            logical.pop();
            match lines.next() {
                Some(next) => logical.push_str(next.trim_start_matches(WHITE_SPACE)),
                None => break,
            }
        }
        let (key, value) = split_entry(&logical);
        entries.insert(unescape(key), unescape(value));
    }
    entries
}

/// The white space of a properties file.
const WHITE_SPACE: [char; 3] = [' ', '\t', '\x0c'];

/// The key and the value of one logical line. The value starts after the separator and the white space around it.
fn split_entry(line: &str) -> (&str, &str) {
    let mut escaped = false;
    for (index, character) in line.char_indices() {
        if escaped {
            escaped = false;
        } else if character == '\\' {
            escaped = true;
        } else if character == '=' || character == ':' || WHITE_SPACE.contains(&character) {
            let rest = line[index..].trim_start_matches(WHITE_SPACE);
            let rest = rest.strip_prefix(['=', ':']).unwrap_or(rest);
            return (&line[..index], rest.trim_start_matches(WHITE_SPACE));
        }
    }
    (line, "")
}

/// The text of a key or a value without its escapes. The `\u` escapes in a row decode together, so a surrogate pair
/// is one character, as Java writes a character outside the basic plane. A unit that does not decode is U+FFFD.
fn unescape(text: &str) -> String {
    let mut unescaped = String::with_capacity(text.len());
    let mut units: Vec<u16> = Vec::new();
    let flush = |unescaped: &mut String, units: &mut Vec<u16>| {
        unescaped.extend(char::decode_utf16(units.drain(..)).map(|decoded| decoded.unwrap_or(char::REPLACEMENT_CHARACTER)));
    };
    let mut characters = text.chars().peekable();
    while let Some(character) = characters.next() {
        if character == '\\' && characters.peek() == Some(&'u') {
            let digits: String = characters.clone().skip(1).take(4).collect();
            if digits.len() == 4
                && digits.chars().all(|digit| digit.is_ascii_hexdigit())
                && let Ok(unit) = u16::from_str_radix(&digits, 16)
            {
                units.push(unit);
                characters.nth(4);
                continue;
            }
        }
        flush(&mut unescaped, &mut units);
        if character != '\\' {
            unescaped.push(character);
            continue;
        }
        match characters.next() {
            Some('t') => unescaped.push('\t'),
            Some('n') => unescaped.push('\n'),
            Some('r') => unescaped.push('\r'),
            Some('f') => unescaped.push('\x0c'),
            Some(other) => unescaped.push(other),
            None => {}
        }
    }
    flush(&mut unescaped, &mut units);
    unescaped
}

/// The root of the server in `BUILD_URL`, when that is the page of the build `id`: `<root>/build/<id>`, with or without a
/// slash at the end.
fn build_url_root(variable: &dyn Fn(&str) -> Option<String>, id: &str) -> Option<String> {
    let url = variable(BUILD_URL)?;
    url.trim_end_matches('/').strip_suffix(&format!("/build/{id}")).map(str::to_owned)
}

#[cfg(test)]
mod tests;
