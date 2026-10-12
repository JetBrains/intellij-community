//! Selector classification and resolution: what a selector names, and the targets and filter it runs.

use std::collections::HashMap;
use std::fmt;
use std::str::FromStr;

use refusal::Refusal;

use crate::areas::AREAS_FILE;
use crate::exit::fail_usage;
use crate::modules::resolve_module;
use crate::runtime::{Platform, Runtime};
use crate::scan::{Candidate, Index, ResolutionInputs, TestRoot, derive_package, read_dir_or_none, read_text};
use crate::suites::{AffectedSuite, resolve_suite_run};
use crate::{paths, regex, runtime::repo_file};

/// How a selector was read. The kind decides how much work resolution costs: a label and a pattern already name
/// their targets, a directory needs one listing, a flow and a suite read the committed suite documents, and only a
/// name or a package makes the tree scan happen.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum SelectorKind {
    Label,
    Pattern,
    Dir,
    SimpleName,
    Fqn,
    Package,
    /// A flow id, `flow-` and lower-case words joined by hyphens. It selects every generated suite whose scenarios
    /// tell or walk the flow; see [`crate::named_suites`].
    Flow,
    /// A generated or authored suite id, the base name of one committed suite document.
    Suite,
    /// A JPS module name, from `--module`. [`Selector::classify`] never answers it, because a module name reads as a
    /// package. [`crate::modules::resolve_module`] resolves it.
    Module,
}

impl SelectorKind {
    /// Whether the kind selects suites rather than test targets. Such a selector resolves to one lane and a class
    /// filter per suite, and `--lane` may settle which lane.
    pub const fn names_suites(self) -> bool {
        matches!(self, Self::Flow | Self::Suite)
    }

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Label => "label",
            Self::Pattern => "pattern",
            Self::Dir => "dir",
            Self::SimpleName => "simpleName",
            Self::Fqn => "fqn",
            Self::Package => "package",
            Self::Flow => "flow",
            Self::Suite => "suite",
            Self::Module => "module",
        }
    }
}

impl fmt::Display for SelectorKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// One parsed selector.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Selector {
    pub kind: SelectorKind,
    /// A class simple name, a class FQN, a package, a directory path, a bazel label, a flow id, a suite id or a
    /// module name, depending on the kind.
    pub name: String,
    /// The single test method to run. `Foo#` is not a selector this accepts, so there is no empty method.
    pub method: Option<String>,
}

impl Selector {
    /// A selector with no method.
    pub fn new(kind: SelectorKind, name: impl Into<String>) -> Self {
        Self {
            kind,
            name: name.into(),
            method: None,
        }
    }

    /// Reads one selector without touching the filesystem.
    pub fn classify(raw: &str) -> Result<Self, Refusal> {
        if raw.starts_with("//") {
            let kind = if raw.ends_with("/...") || raw.contains('*') {
                SelectorKind::Pattern
            } else {
                SelectorKind::Label
            };
            return Ok(Self::new(kind, raw));
        }

        let (head, method) = match raw.split_once('#') {
            Some((head, tail)) => {
                // Kotlin backticked test names carry spaces and punctuation, so the method pattern is wide.
                if !regex!(r"^[A-Za-z_$][A-Za-z0-9_$ '`,.()\[\]-]*$").is_match(tail) {
                    return Err(fail_usage(format!("Invalid test method name: {tail}")));
                }
                (head, Some(tail.to_owned()))
            }
            None => (raw, None),
        };

        // A directory is what an agent has in hand after editing a module, so it is accepted verbatim rather than
        // making it remember the `//` prefix and the `/...` suffix; [`resolve_selector`] checks and normalizes it.
        //
        // A backslash counts on every platform: it is what a Windows shell completes a path to, and it appears in
        // no other kind of selector, since a class name, an FQN and a package name are all identifiers.
        if head.contains(['/', '\\']) {
            if method.is_some() {
                return Err(fail_usage("A directory selector cannot carry a #method"));
            }
            return Ok(Self::new(SelectorKind::Dir, head));
        }

        // A flow id and a suite id are lower-case words joined by hyphens, and a suite id has at least two words.
        // The hyphen keeps both apart from every other kind: no class, FQN or package name can hold one, a label
        // starts with `//`, and a directory holds a separator. A flow id owns the `flow-` prefix, which no
        // generated suite id carries.
        //
        // A suite runs all of its scenarios, so neither kind can select one method. One scenario alone is left out
        // on purpose: a suite's scenarios share one managed-agent chain (ADR 0108).
        let kind = if is_flow_id(head) {
            Some(SelectorKind::Flow)
        } else if regex!(r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)+$").is_match(head) {
            Some(SelectorKind::Suite)
        } else {
            None
        };
        if let Some(kind) = kind {
            if method.is_some() {
                return Err(fail_usage(format!("A {kind} selector cannot carry a #method")));
            }
            return Ok(Self::new(kind, head));
        }

        if regex!(r"^[A-Z][A-Za-z0-9_]*$").is_match(head) {
            return Ok(Self {
                kind: SelectorKind::SimpleName,
                name: head.to_owned(),
                method,
            });
        }
        if regex!(r"^(?:[a-z][A-Za-z0-9_]*\.)+[A-Z][A-Za-z0-9_]*$").is_match(head) {
            return Ok(Self {
                kind: SelectorKind::Fqn,
                name: head.to_owned(),
                method,
            });
        }
        if is_package_name(head) {
            if method.is_some() {
                return Err(fail_usage("A package selector cannot carry a #method"));
            }
            return Ok(Self::new(SelectorKind::Package, head));
        }
        Err(fail_usage(format!(
            "Cannot interpret selector: {raw}\n\
             Expected ClassName, ClassName#method, a.b.c.ClassName, a.b.c (package), a/directory/path, \
             //pkg:target, //pkg/..., flow-id, or suite-id"
        )))
    }
}

impl FromStr for Selector {
    type Err = Refusal;

    fn from_str(raw: &str) -> Result<Self, Refusal> {
        Self::classify(raw)
    }
}

pub fn is_flow_id(value: &str) -> bool {
    regex!(r"^flow(?:-[a-z0-9]+)+$").is_match(value)
}

fn is_package_name(value: &str) -> bool {
    regex!(r"^[a-z][A-Za-z0-9_]*(?:\.[a-z][A-Za-z0-9_]*)*$").is_match(value)
}

/// The nearest known names to a wanted one, closest first, ties by name.
///
/// The threshold scales with the wanted name's length, because a one-character typo in `FooTest` and in
/// `AgentPromptChangesTreeContextContributorTest` are equally likely and only the second can absorb three.
pub fn suggest_names<'k>(wanted: &str, known: impl IntoIterator<Item = &'k str>, limit: usize) -> Vec<String> {
    let threshold = (wanted.len() / 4).max(2);
    let wanted = wanted.to_lowercase();
    let mut matches: Vec<(usize, &str)> = known
        .into_iter()
        .map(|name| (strsim::levenshtein(&wanted, &name.to_lowercase()), name))
        .filter(|(distance, _)| *distance <= threshold)
        .collect();
    matches.sort_unstable();
    matches.into_iter().take(limit).map(|(_, name)| name.to_owned()).collect()
}

/// What a selector became: the targets to run and the filter to run them with.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Resolution {
    /// One label for a resolved class or package, one wildcard pattern, or an explicit target list.
    pub labels: Vec<String>,
    /// A `--test_filter` value: a class FQN, optionally `#method`.
    pub filter: Option<String>,
    /// Goes through `JB_TEST_JUNIT5_FILTERS=include-package=` instead, the only field that can express a package.
    pub include_package: Option<String>,
    /// More `JB_TEST_JUNIT5_FILTERS` entries, one `include-classname=` per suite a flow or a suite selector names.
    /// [`crate::lanes::build_bazel_args`] joins them with the package into one variable, because a second `--test_env` of
    /// the same name replaces the first.
    pub junit5_filters: Vec<String>,
    /// The suites the filters select, for the dry run and the digest to name.
    pub suites: Vec<AffectedSuite>,
    /// The lane a flow or a suite selector settled on. Its lane spec decides the run's flags and its shard count,
    /// exactly as `--lane` would.
    pub lane: Option<String>,
    /// The run can hit more than one test target, so no filter may be attached.
    pub multi_target: bool,
}

/// Splits an explicit `--filter` value into the field that can express it: `(filter, include_package)`.
///
/// `--test_filter` understands a class FQN or `FQN#method` only, so an all-lowercase dotted value is a package and
/// has to go through `JB_TEST_JUNIT5_FILTERS=include-package=` instead; otherwise it would match no class and the
/// run would land on the "filter matched nothing" exit.
pub fn as_filter_or_package(value: Option<&str>) -> (Option<String>, Option<String>) {
    match value {
        None => (None, None),
        Some(value) if is_package_name(value) => (None, Some(value.to_owned())),
        Some(value) => (Some(value.to_owned()), None),
    }
}

/// The class simple name a name selector asks for.
pub fn wanted_simple_name(selector: &Selector) -> &str {
    match selector.kind {
        SelectorKind::Fqn => selector.name.rsplit_once('.').map_or(&*selector.name, |(_, name)| name),
        _ => &selector.name,
    }
}

/// Turns a selector into the targets and filter to run.
pub fn resolve_selector(runtime: &dyn Runtime, selector: &Selector, inputs: &ResolutionInputs<'_>) -> Result<Resolution, Refusal> {
    match selector.kind {
        // Before any tree scan: a label and a pattern already name their targets.
        SelectorKind::Label | SelectorKind::Pattern => {
            return Ok(Resolution {
                labels: vec![selector.name.clone()],
                multi_target: selector.kind == SelectorKind::Pattern,
                ..Resolution::default()
            });
        }
        SelectorKind::Dir => {
            let dir = repo_relative_dir(runtime, &selector.name)?;
            return Ok(Resolution {
                labels: vec![format!("//{dir}/...")],
                multi_target: true,
                ..Resolution::default()
            });
        }
        // Before the tree scan too: the module list and one BUILD.bazel answer it.
        SelectorKind::Module => return resolve_module(runtime, inputs.areas(), &selector.name),
        SelectorKind::Package | SelectorKind::SimpleName | SelectorKind::Fqn if inputs.areas().is_empty() => {
            return Err(fail_usage(format!(
                "{} is a {} selector, and {AREAS_FILE} names no area to scan for it; pass a //label or a \
                 //pkg/... pattern",
                selector.name, selector.kind
            )));
        }
        SelectorKind::Package => return resolve_package(runtime, selector, inputs),
        // Before the tree scan too: the committed suite documents answer both kinds.
        SelectorKind::Flow | SelectorKind::Suite => {
            return resolve_suite_run(runtime, inputs.areas().with_catalog()?, selector, None);
        }
        SelectorKind::SimpleName | SelectorKind::Fqn => {}
    }

    let index = inputs.index()?;
    let simple_name = wanted_simple_name(selector);
    let mut candidates = index.get(simple_name).cloned().unwrap_or_default();
    if candidates.is_empty() {
        // The @Nested / secondary-class fallback: the filename told us nothing, so read every indexed file.
        candidates = scan_for_type(runtime, index, simple_name)?;
    }
    if selector.kind == SelectorKind::Fqn && candidates.len() > 1 {
        let expected_package = selector.name.rsplit_once('.').map_or("", |(package, _)| package);
        let mut narrowed = Vec::new();
        for candidate in &candidates {
            if derive_package(&read_text(runtime, &candidate.file)?) == expected_package {
                narrowed.push(candidate.clone());
            }
        }
        // An FQN that narrows to nothing keeps the wide list, so the answer is the ambiguity refusal naming every
        // real candidate rather than "no such class" for a class that plainly exists.
        if !narrowed.is_empty() {
            candidates = narrowed;
        }
    }

    if candidates.is_empty() {
        let suggestions = suggest_names(simple_name, index.keys().map(String::as_str), 3);
        let hint = if suggestions.is_empty() {
            "  (no similar name found; @Nested and secondary top-level classes need a full FQN)".to_owned()
        } else {
            suggestions
                .iter()
                .map(|name| format!("  did you mean  {name}"))
                .collect::<Vec<_>>()
                .join("\n")
        };
        return Err(fail_usage(format!(
            "No test class named {simple_name} under {}\n{hint}",
            inputs.areas().dirs().join(", ")
        )));
    }

    // Keyed by FQN and kept in candidate order, which is what the caller is asked to copy a name out of.
    let mut order: Vec<String> = Vec::new();
    let mut by_fqn: HashMap<String, Candidate> = HashMap::new();
    for candidate in candidates {
        let package = derive_package(&read_text(runtime, &candidate.file)?);
        let fqn = if package.is_empty() {
            candidate.simple_name.clone()
        } else {
            format!("{package}.{}", candidate.simple_name)
        };
        if !by_fqn.contains_key(&fqn) {
            order.push(fqn.clone());
        }
        by_fqn.insert(fqn, candidate);
    }
    if order.len() > 1 {
        let listed: Vec<String> = order
            .iter()
            .map(|fqn| format!("  {fqn}\n      {}", by_fqn[fqn].root.label))
            .collect();
        return Err(fail_usage(format!(
            "{} classes named {simple_name}; pass the fully-qualified name:\n{}",
            order.len(),
            listed.join("\n")
        )));
    }

    let fqn = &order[0];
    let filter = match &selector.method {
        Some(method) => format!("{fqn}#{method}"),
        None => fqn.clone(),
    };
    Ok(Resolution {
        labels: vec![by_fqn[fqn].root.label.clone()],
        filter: Some(filter),
        ..Resolution::default()
    })
}

/// The roots whose declared package prefix covers a package.
pub fn roots_declaring<'a>(roots: &'a [TestRoot], package: &str) -> Vec<&'a TestRoot> {
    roots
        .iter()
        .filter(|root| {
            root.package_prefix
                .as_deref()
                .is_some_and(|prefix| prefix == package || package.strip_prefix(prefix).is_some_and(|rest| rest.starts_with('.')))
        })
        .collect()
}

fn resolve_package(runtime: &dyn Runtime, selector: &Selector, inputs: &ResolutionInputs<'_>) -> Result<Resolution, Refusal> {
    let roots = inputs.roots()?;
    let mut resolved: Vec<TestRoot> = roots_declaring(roots, &selector.name).into_iter().cloned().collect();
    if resolved.is_empty() {
        // No declared prefix covers it, so fall back to what the files themselves say. This is what makes a
        // package selector work for a root whose .iml declares no prefix at all.
        resolved = roots_by_scanned_package(runtime, roots, inputs.index()?, &selector.name)?;
    }
    let Some(first) = resolved.first() else {
        return Err(fail_usage(format!(
            "No test source root under {} contains package {}",
            inputs.areas().dirs().join(", "),
            selector.name
        )));
    };
    if resolved.iter().any(|root| root.label != first.label) {
        let listed: Vec<String> = resolved.iter().map(|root| format!("  {}", root.label)).collect();
        return Err(fail_usage(format!(
            "Package {} spans {} test targets; run one explicitly:\n{}",
            selector.name,
            resolved.len(),
            listed.join("\n")
        )));
    }
    Ok(Resolution {
        labels: vec![first.label.clone()],
        include_package: Some(selector.name.clone()),
        ..Resolution::default()
    })
}

/// Resolves a directory selector against the repository root, not the process working directory: under `bt.cmd`
/// the working directory is a runfiles tree, and a bazel target pattern is repo-relative anyway. A path that does
/// not exist is a usage refusal rather than an empty pattern, because a typo would otherwise become a silent
/// zero-target run reported as "no tests".
pub fn repo_relative_dir(runtime: &dyn Runtime, raw: &str) -> Result<String, Refusal> {
    // A trailing `/...` is bazel's wildcard rather than part of the path, and a Windows shell spells it with the
    // other separator.
    let trimmed = raw
        .strip_suffix("\\...")
        .or_else(|| raw.strip_suffix("/..."))
        .unwrap_or_else(|| raw.trim_end_matches(['/', '\\']));
    let root = runtime.repo_root().to_string_lossy();
    let relative = repo_relative_path(runtime.platform(), &root, trimmed)
        .ok_or_else(|| fail_usage(format!("Directory selector {raw} is outside the repository at {root}")))?;
    // A successful listing is the existence check, and it also tells a file apart from a directory.
    if read_dir_or_none(runtime, &relative).is_none() {
        let what = if runtime.exists(&repo_file(runtime, &relative)) {
            "Not a directory"
        } else {
            "No such directory"
        };
        return Err(fail_usage(format!(
            "{what}: {relative}\n  (a directory selector is resolved against the repository root; pass a class \
             name or an FQN\n   to run a single test)"
        )));
    }
    Ok(relative)
}

/// Where a path sits inside the repository, as a forward-slash relative path, or `None` when it sits outside it.
///
/// Pure string work in both dialects: `std::path` follows the host, which made this decision untestable and wrong
/// in both directions (an absolute POSIX selector was joined onto the repository root on Windows instead of being
/// refused, and no Windows case could be driven from anywhere else).
fn repo_relative_path(platform: Platform, root: &str, raw: &str) -> Option<String> {
    match platform {
        Platform::Windows => {
            // Either separator, a drive letter, and a rooted-but-driveless path such as `\plugins\air`, compared
            // without case.
            let clean_root = distpath::clean(&root.replace('\\', "/"));
            let raw = raw.replace('\\', "/");
            let absolute = if paths::has_windows_drive(&raw) {
                distpath::clean(&raw)
            } else if raw.starts_with('/') {
                // `\plugins\air` names the current drive, and the repository's is the only drive this wrapper has
                // an opinion about.
                let volume = if paths::has_windows_drive(&clean_root) {
                    &clean_root[..2]
                } else {
                    ""
                };
                distpath::clean(&format!("{volume}{raw}"))
            } else {
                distpath::join(&clean_root, &raw)
            };
            repo_tail(&clean_root, &absolute, true)
        }
        Platform::Darwin | Platform::Linux => {
            let clean_root = distpath::clean(root);
            let absolute = if raw.starts_with('/') {
                distpath::clean(raw)
            } else {
                distpath::join(&clean_root, raw)
            };
            repo_tail(&clean_root, &absolute, false)
        }
    }
}

/// The part of an absolute path below the root. Both dialects share it, so "inside the repository" keeps one
/// definition.
///
/// The root itself has no tail, and neither does a path outside it: both are outside what a target pattern can name.
///
/// `fold_case` is how Windows compares a path, and it applies to the root prefix, which is discarded. Without it
/// `c:\repo\plugins\air` was refused as outside a repository at `C:\repo`. The tail is answered verbatim, so a
/// caller who mis-cased *that* half gets a target pattern naming no package, as a relative selector would.
fn repo_tail(clean_root: &str, absolute: &str, fold_case: bool) -> Option<String> {
    let prefix = format!("{clean_root}/");
    let head = absolute.get(..prefix.len())?;
    let inside = if fold_case {
        head.to_lowercase() == prefix.to_lowercase()
    } else {
        head == prefix
    };
    let tail = &absolute[prefix.len()..];
    (inside && !tail.is_empty()).then(|| tail.to_owned())
}

fn scan_for_type(runtime: &dyn Runtime, index: &Index, simple_name: &str) -> Result<Vec<Candidate>, Refusal> {
    let all: Vec<&Candidate> = index.values().flatten().collect();
    let declaration = crate::scan::type_declaration_pattern(simple_name);
    // The whole index, so the files are read concurrently: this is the fallback, and the only path that reads
    // every indexed source.
    let verdicts = crate::runtime::par_map(&all, |candidate| {
        read_text(runtime, &candidate.file).map(|text| declaration.is_match(&text))
    });
    let mut declaring = Vec::new();
    for (candidate, declares) in all.into_iter().zip(verdicts) {
        if declares? {
            // The name the caller asked for wins over the filename, because that is the class the filter names.
            declaring.push(Candidate {
                simple_name: simple_name.to_owned(),
                ..candidate.clone()
            });
        }
    }
    Ok(declaring)
}

fn roots_by_scanned_package(runtime: &dyn Runtime, roots: &[TestRoot], index: &Index, package: &str) -> Result<Vec<TestRoot>, Refusal> {
    let mut matching_dirs: Vec<&str> = Vec::new();
    for candidate in index.values().flatten() {
        let src_dir = candidate.root.src_dir.as_str();
        if matching_dirs.contains(&src_dir) || !candidate.file.starts_with(&format!("{src_dir}/")) {
            continue;
        }
        let file_package = derive_package(&read_text(runtime, &candidate.file)?);
        if file_package == package || file_package.strip_prefix(package).is_some_and(|rest| rest.starts_with('.')) {
            matching_dirs.push(src_dir);
        }
    }
    Ok(roots
        .iter()
        .filter(|root| matching_dirs.contains(&root.src_dir.as_str()))
        .cloned()
        .collect())
}

#[cfg(test)]
mod tests;
