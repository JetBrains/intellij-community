//! Selector classification and resolution: what a selector names, and the targets and filter it runs.

use std::collections::HashMap;
use std::fmt;
use std::str::FromStr;
use std::sync::{Arc, Mutex, PoisonError};

use refusal::Refusal;

use crate::accio::{ModulesAnswer, SymbolAnswer, SymbolHit, accio_failed, parse_modules, parse_symbols};
use crate::areas::Areas;
use crate::catalog::read_suite_documents;
use crate::exit::fail_usage;
use crate::modules::{Layout, MODULE_WITHOUT_TEST_TARGET, check_migrated, module_test_target, resolve_module};
use crate::runtime::{Platform, Runtime, read_dir_or_none};
use crate::suites::{AffectedSuite, resolve_suite_run};
use crate::{paths, regex, runtime::repo_file};

/// How a selector was read. The kind decides how much work resolution costs: a label and a pattern already name
/// their targets, a directory needs one listing, a flow and a suite read the committed suite documents, and only a
/// class name asks accio.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum SelectorKind {
    Label,
    Pattern,
    Dir,
    SimpleName,
    Fqn,
    /// A flow id, `flow-` and lower-case words joined by hyphens. It selects every generated suite whose scenarios
    /// tell or walk the flow; see [`crate::named_suites`].
    Flow,
    /// A generated or authored suite id, the base name of one committed suite document.
    Suite,
    /// A JPS module name, from `--module`. [`Selector::classify`] never answers it, because a module name reads as a
    /// package, which it refuses. [`crate::modules::resolve_module`] resolves it.
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
    /// A class simple name, a class FQN, a directory path, a bazel label, a flow id, a suite id or a module name,
    /// depending on the kind.
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
        if is_target_pattern(raw) {
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
        // A JPS module name and a package are both dotted lower-case words, so neither is a selector of its own.
        if is_package_name(head) {
            return Err(fail_usage(format!(
                "Cannot interpret selector: {raw}\n\
                 bt takes no package selector. For a JPS module, pass --module {head}. For a package, pass \
                 --module <module> --filter {head}"
            )));
        }
        Err(fail_usage(format!(
            "Cannot interpret selector: {raw}\n\
             Expected ClassName, ClassName#method, a.b.c.ClassName, a/directory/path, //pkg:target, \
             @repo//pkg:target, //pkg/..., flow-id, or suite-id"
        )))
    }
}

/// Whether a value is a bazel label or a target pattern from the root of a repository: `//pkg:target`, or
/// `@repo//pkg:target` of another repository. The hint of a module outside the migrated list names the second form. No
/// other kind of selector starts with `//` or `@`.
pub fn is_target_pattern(value: &str) -> bool {
    value.starts_with("//") || value.starts_with('@')
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
    /// What the caller must know about the resolution, such as the outer class that the filter of a nested class
    /// names. Each note is one line.
    pub notes: Vec<String>,
}

/// Splits an explicit `--filter` value into the field that can express it: `(filter, include_package)`.
///
/// `--test_filter` understands a class FQN or `FQN#method` only, so a dotted value whose segments start with a
/// lower-case letter is a package and has to go through `JB_TEST_JUNIT5_FILTERS=include-package=` instead; otherwise it would match no class and the
/// run would land on the "filter matched nothing" exit.
pub fn as_filter_or_package(value: Option<&str>) -> (Option<String>, Option<String>) {
    match value {
        None => (None, None),
        Some(value) if is_package_name(value) => (None, Some(value.to_owned())),
        Some(value) => (Some(value.to_owned()), None),
    }
}

/// The hint of a run that found no test, from the `--filter` value of the caller. It names the form of the filter that
/// the JUnit runner reads. It changes nothing of the run. `None` for a run without `--filter`, and for an FQN
/// `Class#method`, because the cause line of the digest already names the `#method` part.
///
/// The runner reads `--test_filter` as a class name prefix whose last segment starts with an upper-case letter, or as
/// one `Class#method`. It throws on a wildcard and a regex, and a list matches no class. It loads the class of a
/// `Class#method` by its name, so the class needs its FQN. Jupiter finds an `@Nested` class only through its outer
/// class, so the prefix `a.Outer$Inner` runs no test, but `a.Outer$Inner#method` runs. The runner names a nested
/// class `a.Outer$Inner`, so `a.Outer.Inner` matches no class. A package filter also runs the subpackages.
pub fn no_tests_hint(filter: Option<&str>) -> Option<String> {
    const PACKAGE_LINE: &str = "take the package from the package line of the file, not from its directory";
    const OUTER_CLASS: &str = "the runner finds an @Nested class only through its outer class";
    let value = filter?;
    if is_package_name(value) {
        return Some(format!(
            "a package filter runs the classes of that package and its subpackages: {PACKAGE_LINE}"
        ));
    }
    let (class, method) = value.split_once('#').map_or((value, None), |(class, method)| (class, Some(method)));
    if !regex!(r"^(?:[A-Za-z_][A-Za-z0-9_]*\.)*[A-Z][A-Za-z0-9_$]*$").is_match(class) {
        if let Some(package) = value.strip_suffix(".*").filter(|package| is_package_name(package)) {
            return Some(format!(
                "--filter takes no wildcard. To run every class of the package, pass --filter {package}"
            ));
        }
        return Some(
            "--filter takes a class, a Class#method or a package. It takes no wildcard, no regex and no list. Run each \
             class in its own call, or pass the package of the classes"
                .to_owned(),
        );
    }
    let nested = nested_class_with_dots(class);
    match method {
        Some(_) if !class.contains('.') => Some(format!(
            "a Class#method needs the FQN of the class, because the runner loads the class by its name: {PACKAGE_LINE}"
        )),
        Some(method) => nested.map(|nested| {
            format!("a nested class takes $ after its outer class. If {class} is a nested class, pass --filter '{nested}#{method}'")
        }),
        None => {
            if let Some((outer, _)) = class.split_once('$') {
                return Some(format!("{OUTER_CLASS}. To run it, pass --filter {outer}"));
            }
            if let Some((outer, _)) = nested.as_deref().and_then(|nested| nested.split_once('$')) {
                return Some(format!("{OUTER_CLASS}. If {class} is a nested class, pass --filter {outer}"));
            }
            Some(format!("a class filter is a prefix of the class names: {PACKAGE_LINE}"))
        }
    }
}

/// The `$` form of a class with a dot after its outer class, as `a.Outer$Inner` for `a.Outer.Inner`. `None` for every
/// other value.
///
/// The outer class is the first segment that starts with an upper-case letter and has a lower-case letter. A package
/// segment can start with an upper-case letter too, but it has no lower-case letter, as `IDEA_355419` of a Spring test.
fn nested_class_with_dots(class: &str) -> Option<String> {
    let segments: Vec<&str> = class.split('.').collect();
    let outer = segments.iter().position(|segment| {
        segment.starts_with(|first: char| first.is_ascii_uppercase()) && segment.contains(|letter: char| letter.is_ascii_lowercase())
    })?;
    if outer == segments.len() - 1 {
        return None;
    }
    Some(format!("{}${}", segments[..=outer].join("."), segments[outer + 1..].join("$")))
}

/// The class simple name a name selector asks for.
fn wanted_simple_name(selector: &Selector) -> &str {
    match selector.kind {
        SelectorKind::Fqn => selector.name.rsplit_once('.').map_or(&*selector.name, |(_, name)| name),
        _ => &selector.name,
    }
}

/// The `--limit` of a `symbol` call: a name with more classes is refused, never cut.
pub const SYMBOL_LIMIT: usize = 200;

/// The accio answers of one resolution, read once each: the `symbol` answers by name and the `modules` answers by
/// path. A failed call is kept as its refusal, so a second lookup of the same name or path makes no second call.
pub struct ResolutionInputs<'r> {
    runtime: &'r dyn Runtime,
    areas: &'r Areas,
    symbols: Mutex<HashMap<String, Result<Arc<SymbolAnswer>, Refusal>>>,
    modules: Mutex<HashMap<String, Result<Arc<ModulesAnswer>, Refusal>>>,
}

impl<'r> ResolutionInputs<'r> {
    /// The empty memo. Nothing is asked until a lookup needs it.
    pub fn new(runtime: &'r dyn Runtime, areas: &'r Areas) -> Self {
        ResolutionInputs {
            runtime,
            areas,
            symbols: Mutex::new(HashMap::new()),
            modules: Mutex::new(HashMap::new()),
        }
    }

    /// The areas that resolution reads its lanes, its catalog and the module rule from.
    pub const fn areas(&self) -> &'r Areas {
        self.areas
    }

    /// Asks accio once for the classes of every name that the memo does not hold yet: one batch
    /// `symbol <name>... --kind class --limit 200` call, and no call when every name is known. A planner that
    /// resolves many classes calls it once, before its first [`resolve_selector`].
    ///
    /// The answer objects name no query, so they are matched to the names by position. The limit and `complete` apply
    /// to each name on its own. A failed call is the refusal of each of its names.
    pub fn prefetch_symbols(&self, names: &[&str]) {
        let missing: Vec<&str> = {
            let known = lock(&self.symbols);
            let mut missing: Vec<&str> = Vec::new();
            for name in names {
                if !known.contains_key(*name) && !missing.contains(name) {
                    missing.push(name);
                }
            }
            missing
        };
        if missing.is_empty() {
            return;
        }
        let limit = SYMBOL_LIMIT.to_string();
        let mut args = vec!["symbol"];
        args.extend(&missing);
        args.extend(["--kind", "class", "--limit", &limit]);
        let answered = self.runtime.accio(&args).and_then(|text| parse_symbols(&text, &missing));
        let mut known = lock(&self.symbols);
        match answered {
            Ok(answers) => {
                for (name, answer) in missing.iter().zip(answers) {
                    known.insert((*name).to_owned(), Ok(Arc::new(answer)));
                }
            }
            Err(refusal) => {
                for name in &missing {
                    known.insert((*name).to_owned(), Err(refusal.clone()));
                }
            }
        }
    }

    /// The `symbol` answer of one name.
    pub fn symbols(&self, name: &str) -> Result<Arc<SymbolAnswer>, Refusal> {
        self.prefetch_symbols(&[name]);
        lock(&self.symbols)
            .get(name)
            .cloned()
            .unwrap_or_else(|| Err(accio_failed(format!("accio gave no symbol answer for {name}"))))
    }

    /// Asks accio once for the module roots of every path that the memo does not hold yet: one
    /// `modules --paths <path>...` call.
    fn prefetch_modules(&self, paths: &[&str]) {
        let missing: Vec<&str> = {
            let known = lock(&self.modules);
            let mut missing: Vec<&str> = Vec::new();
            for path in paths {
                if !known.contains_key(*path) && !missing.contains(path) {
                    missing.push(path);
                }
            }
            missing
        };
        if missing.is_empty() {
            return;
        }
        let mut args = vec!["modules", "--paths"];
        args.extend(&missing);
        let answered = self.runtime.accio(&args).and_then(|text| parse_modules(&text)).map(Arc::new);
        let mut known = lock(&self.modules);
        for path in missing {
            known.insert(path.to_owned(), answered.clone());
        }
    }

    /// The `modules` answer that holds one path.
    fn modules_of(&self, path: &str) -> Result<Arc<ModulesAnswer>, Refusal> {
        self.prefetch_modules(&[path]);
        lock(&self.modules)
            .get(path)
            .cloned()
            .unwrap_or_else(|| Err(accio_failed(format!("accio gave no modules answer for {path}"))))
    }
}

fn lock<T>(mutex: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}

/// Turns a selector into the targets and filter to run.
pub fn resolve_selector(runtime: &dyn Runtime, selector: &Selector, inputs: &ResolutionInputs<'_>) -> Result<Resolution, Refusal> {
    match selector.kind {
        // No accio call: a label and a pattern already name their targets.
        SelectorKind::Label | SelectorKind::Pattern => Ok(Resolution {
            labels: vec![checkout_label(runtime, &selector.name)],
            multi_target: selector.kind == SelectorKind::Pattern,
            ..Resolution::default()
        }),
        SelectorKind::Dir => Ok(Resolution {
            labels: vec![directory_pattern(runtime, &selector.name)?],
            multi_target: true,
            ..Resolution::default()
        }),
        SelectorKind::Module => resolve_module(runtime, inputs.areas(), &selector.name),
        // The committed suite documents answer both kinds.
        SelectorKind::Flow | SelectorKind::Suite => resolve_suite_run(runtime, inputs.areas().with_catalog()?, selector, None),
        SelectorKind::SimpleName | SelectorKind::Fqn => resolve_class(runtime, selector, inputs),
    }
}

/// One class that a name can mean: its FQN, the outer class of a nested class, and its test label or the refusal of
/// its module.
struct Candidate {
    fqn: String,
    top_level: Option<String>,
    label: Result<String, Refusal>,
}

/// Resolves a class name or an FQN, in the whole repository.
///
/// The classes come from the committed suite documents and from one `accio symbol` call. A class of a suite document
/// runs in the lane of the document. Any other class runs in the test target of the module whose test source root
/// holds its file ([`module_test_target`]), under the module rule of `--module` ([`check_migrated`]). One
/// `accio modules` call gives the modules of every file. A class outside every test source root is no candidate.
fn resolve_class(runtime: &dyn Runtime, selector: &Selector, inputs: &ResolutionInputs<'_>) -> Result<Resolution, Refusal> {
    let simple_name = wanted_simple_name(selector);
    let documented = documented_classes(runtime, inputs.areas(), simple_name)?;
    let symbols = inputs.symbols(&selector.name)?;
    if !symbols.complete {
        return Err(fail_usage(format!(
            "accio finds more than {SYMBOL_LIMIT} classes named {}; pass the fully-qualified name",
            selector.name
        )));
    }
    // A class of the same name in another language, such as a Python `SampleTest`, is no JUnit class.
    let hits: Vec<&SymbolHit> = symbols.hits.iter().filter(|hit| hit.is_jvm()).collect();
    let files: Vec<&str> = hits.iter().map(|hit| hit.file.as_str()).collect();
    inputs.prefetch_modules(&files);

    // Keyed by FQN and kept in the order of accio, which is what the caller is asked to copy a name out of.
    let mut found: Vec<Candidate> = Vec::new();
    let mut outside: Vec<&str> = Vec::new();
    for hit in hits {
        let Some(fqn) = hit.fqn.clone() else {
            return Err(accio_failed(format!(
                "accio names no fqn for the class {} of {}; bt needs an accio that answers it",
                hit.name, hit.file
            )));
        };
        let answer = inputs.modules_of(&hit.file)?;
        let Some((entry, owner)) = answer
            .entry_of(&hit.file)
            .and_then(|entry| Some((entry, entry.test_module_of(&hit.file)?)))
        else {
            outside.push(&hit.file);
            continue;
        };
        let layout = Layout::detect(runtime);
        let label = match module_test_target(entry, &owner.name)? {
            Some(name) => {
                let label = layout.label(&entry.root, name);
                check_migrated(runtime, inputs.areas(), &owner.name, &entry.root, &label, &fqn).map(|()| label)
            }
            None => Err(Refusal::new(
                MODULE_WITHOUT_TEST_TARGET,
                crate::exit::USAGE,
                format!(
                    "module {} of {fqn} has no test target in {}. Run: ./tests.cmd --module {} --test {fqn}",
                    owner.name,
                    if entry.root.is_empty() {
                        "BUILD.bazel".to_owned()
                    } else {
                        format!("{}/BUILD.bazel", entry.root)
                    },
                    owner.name
                ),
            )),
        };
        if !found.iter().any(|known| known.fqn == fqn) {
            found.push(Candidate {
                fqn,
                top_level: hit.top_level.clone(),
                label,
            });
        }
    }
    for (fqn, label) in documented {
        match found.iter_mut().find(|known| known.fqn == fqn) {
            Some(known) => known.label = Ok(label),
            None => found.push(Candidate {
                fqn,
                top_level: None,
                label: Ok(label),
            }),
        }
    }
    if selector.kind == SelectorKind::Fqn && found.len() > 1 {
        // An FQN that narrows to nothing keeps the wide list, so the answer is the ambiguity refusal naming every
        // real candidate rather than "no such class" for a class that plainly exists.
        if found.iter().any(|candidate| candidate.fqn.replace('$', ".") == selector.name) {
            found.retain(|candidate| candidate.fqn.replace('$', ".") == selector.name);
        }
    }

    if found.is_empty() {
        let hint = if outside.is_empty() {
            symbols
                .note
                .as_deref()
                .map_or_else(|| "  (accio names no similar class)".to_owned(), |note| format!("  accio: {note}"))
        } else {
            format!("  accio finds it only outside a test source root: {}", outside.join(", "))
        };
        return Err(fail_usage(format!("No test class named {}\n{hint}", selector.name)));
    }

    if found.len() > 1 {
        let listed: Vec<String> = found
            .iter()
            .map(|candidate| {
                let label = match &candidate.label {
                    Ok(label) => label.clone(),
                    Err(refusal) => refusal.message.lines().next().unwrap_or_default().to_owned(),
                };
                format!("  {}\n      {label}", candidate.fqn)
            })
            .collect();
        return Err(fail_usage(format!(
            "{} classes named {simple_name}; pass the fully-qualified name:\n{}",
            found.len(),
            listed.join("\n")
        )));
    }

    let Candidate { fqn, top_level, label } = found.swap_remove(0);
    let label = label?;
    let mut notes = Vec::new();
    let filter = match (&selector.method, top_level) {
        (Some(method), _) => format!("{fqn}#{method}"),
        (None, Some(top_level)) => {
            notes.push(format!(
                "{fqn} is a nested class, so the filter is its outer class {top_level}: the runner finds an @Nested \
                 class only through its outer class"
            ));
            top_level
        }
        (None, None) => fqn,
    };
    Ok(Resolution {
        labels: vec![label],
        filter: Some(filter),
        notes,
        ..Resolution::default()
    })
}

/// The classes named `simple_name` that a committed suite document names, each as its FQN and the test label of the
/// lane of its document. The class of a UI lane needs no test target of its module: the lane runs it.
///
/// An area set without a suite catalog, or a catalog directory that holds no document yet, names no class. A
/// document that cannot be read is refused, as every reader of the catalog refuses it.
fn documented_classes(runtime: &dyn Runtime, areas: &Areas, simple_name: &str) -> Result<Vec<(String, String)>, Refusal> {
    let Some(catalog) = areas.iter().find_map(|area| area.lanes().catalog()) else {
        return Ok(Vec::new());
    };
    let holds_documents = read_dir_or_none(runtime, catalog.flow_profile_dir)
        .is_some_and(|entries| entries.iter().any(|entry| !entry.is_dir && entry.name.ends_with(".json")));
    if !holds_documents {
        return Ok(Vec::new());
    }
    let area = areas.with_catalog()?;
    Ok(read_suite_documents(runtime, area)?
        .iter()
        .filter(|document| document.test_class_name == simple_name)
        .filter_map(|document| {
            let label = area.lanes().by_suite_lane(&document.lane)?.test_label.clone()?;
            Some((document.test_class().to_owned(), label))
        })
        .collect())
}

/// The recursive target pattern of a directory selector. In the ultimate root, the root `.bazelignore` hides
/// `community/`, so a directory under it is a package of the `@community` repository.
pub fn directory_pattern(runtime: &dyn Runtime, raw: &str) -> Result<String, Refusal> {
    let dir = repo_relative_dir(runtime, raw)?;
    Ok(Layout::detect(runtime).pattern(&dir))
}

/// A label or a target pattern as the checkout root runs it. In the ultimate root, the root `.bazelignore` hides
/// `community/`: bazel only warns that `//community/tools/vm/...` was filtered out, and it builds nothing. So such a
/// value becomes its `@community` spelling, as a directory selector does. Every other value stays as it is.
pub fn checkout_label(runtime: &dyn Runtime, raw: &str) -> String {
    // Only a value that can name the community directory costs the layout probe.
    if !raw.starts_with("//community") {
        return raw.to_owned();
    }
    Layout::detect(runtime).community_label(raw).unwrap_or_else(|| raw.to_owned())
}

/// Refuses a label or a target pattern of the root repository whose package directory is not in the checkout.
///
/// `bt` runs every pattern in the checkout root, which `bt.cmd` finds from its own path, also when the working
/// directory is `community/` of the ultimate root. There `//tools/vm/...` names a directory of the community
/// repository, and the refusal names its `@community` spelling. A label of another repository, a package with a
/// wildcard and the root package are not checked.
pub fn check_package_dir(runtime: &dyn Runtime, label: &str) -> Result<(), Refusal> {
    let Some(body) = label.strip_prefix("//") else {
        return Ok(());
    };
    let path = body.split_once(':').map_or(body, |(path, _)| path);
    let dir = if path == "..." {
        ""
    } else {
        path.strip_suffix("/...").unwrap_or(path)
    };
    if dir.is_empty() || dir.contains('*') || runtime.exists(&repo_file(runtime, dir)) {
        return Ok(());
    }
    let root = runtime.repo_root().to_string_lossy();
    let community = format!("community/{dir}");
    let hint = if Layout::detect(runtime) == Layout::Ultimate && runtime.exists(&repo_file(runtime, &community)) {
        format!(";\n   for {community}, pass @community//{body}")
    } else {
        String::new()
    };
    Err(fail_usage(format!(
        "No such directory: {dir}\n  (bt runs every target pattern in the checkout root {root}, whatever the working \
         directory{hint})"
    )))
}

/// Resolves a directory selector against the repository root, not the process working directory: under `bt.cmd`
/// the working directory is a runfiles tree, and a bazel target pattern is repo-relative anyway. A path that does
/// not exist is a usage refusal rather than an empty pattern, because a typo would otherwise become a silent
/// zero-target run reported as "no tests".
fn repo_relative_dir(runtime: &dyn Runtime, raw: &str) -> Result<String, Refusal> {
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

#[cfg(test)]
mod tests;
