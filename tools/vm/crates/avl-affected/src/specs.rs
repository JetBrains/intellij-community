//! The spec relation: a path a spec owns reaches the suites the spec links as its tests.
//!
//! A spec under the spec directory names the source files it owns in its frontmatter `targets`, and the tests that
//! verify it in `[@test]` links (`community/.ai/spec/SPEC_GUIDE.md`). `AirSpecReferencesTest` keeps both resolving.
//! So when a link names a suite, the spec states that the suite tests the files the spec owns. An authored suite
//! has no route and no module, and this is the only relation that reaches one from a product path.
//!
//! A link names a suite through the same rule that answers a named path: it is the suite's document, or the source
//! of its test class. A link to a lane's generated file names no suite, because that file holds every suite of its
//! lane.

use bt_core::areas::Area;

use bt_core::runtime::{Runtime, par_map};
use bt_core::scan::{read_dir_or_none, read_text_or_none};
use regex::Regex;

use crate::regex;

/// The prefix an [`bt_core::AffectedSuite`] `via` entry of the spec relation carries.
pub(crate) const VIA_SPEC: &str = "spec:";

/// One spec that links at least one suite: what it owns, and the suites it links (indices into the documents).
pub(crate) struct SpecLink {
    /// The spec's repo-relative path, which is what the `spec:` entry names.
    pub path: String,
    targets: Vec<SpecTarget>,
    pub suites: Vec<usize>,
}

/// One frontmatter target, resolved against the spec's directory. A glob matches by its pattern. A plain path
/// matches itself, and every file under it when it names a directory.
enum SpecTarget {
    Glob(Regex),
    Plain(String),
}

impl SpecTarget {
    fn covers(&self, repo_path: &str) -> bool {
        match self {
            Self::Glob(pattern) => pattern.is_match(repo_path),
            Self::Plain(plain) => repo_path == plain || repo_path.strip_prefix(plain.as_str()).is_some_and(|rest| rest.starts_with('/')),
        }
    }
}

impl SpecLink {
    /// Whether any target of the spec covers the path.
    pub(crate) fn owns(&self, repo_path: &str) -> bool {
        self.targets.iter().any(|target| target.covers(repo_path))
    }
}

/// Every spec that links a suite, in walk order.
///
/// A lane table without `specDir` and a checkout without the directory answer no spec, and so does a spec that
/// cannot be read: the relation adds suites and never refuses, and `AirSpecReferencesTest` owns that every spec
/// parses and resolves.
pub(crate) fn read_spec_links(runtime: &dyn Runtime, area: &Area, own_suites: &(dyn Fn(&str) -> Vec<usize> + Sync)) -> Vec<SpecLink> {
    let mut specs = Vec::new();
    let mut frontier: Vec<String> = area.lanes().spec_dir().map(str::to_owned).into_iter().collect();
    while !frontier.is_empty() {
        let listings = par_map(&frontier, |dir| read_dir_or_none(runtime, dir));
        let mut next = Vec::new();
        for (dir, entries) in frontier.iter().zip(listings) {
            for entry in entries.into_iter().flatten() {
                let child = format!("{dir}/{}", entry.name);
                if entry.is_dir {
                    next.push(child);
                } else if entry.name.ends_with(".spec.md") {
                    specs.push(child);
                }
            }
        }
        frontier = next;
    }
    par_map(&specs, |spec| {
        read_text_or_none(runtime, spec).and_then(|text| parse_spec_link(spec, &text, own_suites))
    })
    .into_iter()
    .flatten()
    .collect()
}

/// The targets and the suite links of one spec, or `None` for a spec that links no suite or owns no path.
fn parse_spec_link(spec: &str, text: &str, own_suites: &dyn Fn(&str) -> Vec<usize>) -> Option<SpecLink> {
    let directory = distpath::dir(spec);
    // `AirSpecReferencesTest`'s own `[@test]` pattern, so the two read the same links.
    let test_link = regex!(r"\[@test]\s*\(?([^\s)]+)\)?");
    let mut suites = Vec::new();
    for link in test_link.captures_iter(text) {
        let Some(target) = link.get(1) else { continue };
        for suite in own_suites(&distpath::join(&directory, target.as_str())) {
            if !suites.contains(&suite) {
                suites.push(suite);
            }
        }
    }
    if suites.is_empty() {
        return None;
    }
    let targets: Vec<SpecTarget> = frontmatter_targets(text)
        .into_iter()
        .map(|target| {
            let resolved = distpath::join(&directory, &target);
            if target.contains(['*', '?']) {
                SpecTarget::Glob(glob_pattern(&resolved))
            } else {
                SpecTarget::Plain(resolved)
            }
        })
        .collect();
    if targets.is_empty() {
        return None;
    }
    Some(SpecLink {
        path: spec.to_owned(),
        targets,
        suites,
    })
}

/// The `targets` list of a spec's YAML frontmatter, read the way `AirSpecReferencesTest` reads it: the `- ` items
/// that follow a `targets:` line, up to the next key. No closing `---` line means no frontmatter.
fn frontmatter_targets(text: &str) -> Vec<String> {
    let mut lines = text.split('\n');
    if lines.next().map(str::trim) != Some("---") {
        return Vec::new();
    }
    let mut targets = Vec::new();
    let mut in_targets = false;
    for line in lines {
        let trimmed = line.trim();
        if trimmed == "---" {
            return targets;
        }
        if trimmed == "targets:" {
            in_targets = true;
        } else if in_targets && let Some(item) = trimmed.strip_prefix("- ") {
            targets.push(item.trim().to_owned());
        } else if in_targets && !trimmed.is_empty() {
            in_targets = false;
        }
    }
    Vec::new()
}

/// `AirRepoScan.globToRegex`: `**/` is any number of directories, `**` anything, `*` and `?` stay inside one path
/// segment.
pub(crate) fn glob_pattern(glob: &str) -> Regex {
    let mut pattern = String::from("^");
    let mut rest = glob;
    while let Some(char) = rest.chars().next() {
        if let Some(after) = rest.strip_prefix("**/") {
            pattern.push_str("(?:.*/)?");
            rest = after;
        } else if let Some(after) = rest.strip_prefix("**") {
            pattern.push_str(".*");
            rest = after;
        } else {
            match char {
                '*' => pattern.push_str("[^/]*"),
                '?' => pattern.push_str("[^/]"),
                other => pattern.push_str(&regex::escape(other.encode_utf8(&mut [0; 4]))),
            }
            rest = &rest[char.len_utf8()..];
        }
    }
    pattern.push('$');
    // An invariant: every literal character is escaped and the rest is fixed syntax.
    Regex::new(&pattern).expect("a glob pattern compiles")
}
