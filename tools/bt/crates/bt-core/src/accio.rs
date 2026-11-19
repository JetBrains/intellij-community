//! The answers of accio that bt reads: `accio symbol` and `accio modules`, as `--format json` prints them.
//!
//! Only the fields that bt acts on are read, and every other field is ignored. The source of the shapes is
//! `crates/core/src/module_graph.rs` and the `symbol` answer of accio. [`crate::runtime::Runtime::accio`] runs the
//! call, and the functions here read its text.

use refusal::Refusal;
use serde::Deserialize;

use crate::exit::INFRA;

/// The refusal code for an accio call that failed, or for an answer of accio that bt cannot use.
pub const ACCIO_FAILED: &str = "accio_failed";

/// A refusal of [`ACCIO_FAILED`] with exit 6: the run said nothing about the tests.
pub fn accio_failed(message: impl Into<String>) -> Refusal {
    Refusal::new(ACCIO_FAILED, INFRA, message)
}

/// The answer of `accio symbol` for one name.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct SymbolAnswer {
    #[serde(default)]
    pub hits: Vec<SymbolHit>,
    /// `false` when the limit cut the hits.
    #[serde(default)]
    pub complete: bool,
    /// The note of accio, such as the `did you mean` text of a name without a hit.
    #[serde(default)]
    pub note: Option<String>,
    #[serde(default)]
    available: Option<bool>,
}

/// One declaration that `accio symbol` found.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct SymbolHit {
    /// The repo-relative path of the declaring file.
    pub file: String,
    pub line: u32,
    pub kind: String,
    pub name: String,
    /// The FQN in the binary form of the runner, `a.b.Outer$Inner`. Only a Kotlin or a Java type has one.
    #[serde(default)]
    pub fqn: Option<String>,
    /// The FQN of the outermost type, `a.b.Outer`. Only a nested type has one.
    #[serde(default)]
    pub top_level: Option<String>,
}

/// The answer of `accio modules --paths`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct ModulesAnswer {
    #[serde(default)]
    pub modules: Vec<ModuleEntry>,
    /// The root of each asked path, in the order of the call. A path outside every module root has the root `""`.
    #[serde(default)]
    pub paths: Vec<PathRoot>,
    /// `false` when accio did not read some build files whole. A test target never comes from such a file.
    #[serde(default)]
    pub complete: bool,
    #[serde(default)]
    pub note: Option<String>,
    #[serde(default)]
    available: Option<bool>,
}

impl ModulesAnswer {
    /// The module root that holds a repo-relative path: the root that accio names for the path, else the longest
    /// module root of the answer above the path.
    pub fn entry_of(&self, path: &str) -> Option<&ModuleEntry> {
        if let Some(named) = self.paths.iter().find(|named| named.path == path) {
            return self.modules.iter().find(|entry| entry.root == named.root);
        }
        self.modules
            .iter()
            .filter(|entry| {
                entry.root.is_empty() || path == entry.root || path.strip_prefix(&entry.root).is_some_and(|rest| rest.starts_with('/'))
            })
            .max_by_key(|entry| entry.root.len())
    }
}

/// The module root of one asked path.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct PathRoot {
    pub path: String,
    pub root: String,
}

/// One module root: its JPS modules and the targets of its `BUILD.bazel`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct ModuleEntry {
    /// The repo-relative directory.
    pub root: String,
    /// The main `.iml` of the root, with its test module nested when the test module names it.
    #[serde(default)]
    pub jps: Option<JpsModule>,
    /// The other `.iml` files of the root, sorted by file.
    #[serde(default)]
    pub jps_more: Vec<JpsModule>,
    /// `None` when the root has no `BUILD.bazel`.
    #[serde(default)]
    pub bazel: Option<BazelFile>,
}

impl ModuleEntry {
    /// Every JPS module of the root: the main module, the other modules, and the test module of each.
    pub fn jps_modules(&self) -> impl Iterator<Item = &JpsModule> {
        self.jps
            .iter()
            .chain(self.jps_more.iter())
            .flat_map(|module| std::iter::once(module).chain(module.test.as_deref()))
    }

    /// The JPS module whose test source root holds a repo-relative file, or `None` for a file outside every test
    /// source root of the root.
    pub fn test_module_of(&self, file: &str) -> Option<&JpsModule> {
        self.jps_modules()
            .find(|module| module.roots.iter().any(|root| root.holds_test_sources() && root.contains(file)))
    }

    /// The targets of the `BUILD.bazel`, empty for a root without one.
    pub fn targets(&self) -> &[BazelTarget] {
        self.bazel.as_ref().map_or(&[], |bazel| bazel.targets.as_slice())
    }
}

/// One JPS module.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct JpsModule {
    pub name: String,
    #[serde(default)]
    pub roots: Vec<SourceRoot>,
    #[serde(default)]
    pub test: Option<Box<Self>>,
}

/// One source root of a JPS module.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct SourceRoot {
    /// The repo-relative directory.
    pub path: String,
    /// `java-resource` or `java-test-resource` for a resource root, `None` for a source root.
    #[serde(default)]
    pub kind: Option<String>,
    #[serde(default)]
    pub test: bool,
}

impl SourceRoot {
    /// Whether the root holds test sources: a test root that is not a resource root.
    pub fn holds_test_sources(&self) -> bool {
        self.test && !self.kind.as_deref().is_some_and(|kind| kind.contains("resource"))
    }

    /// Whether a repo-relative file is under this root.
    pub fn contains(&self, file: &str) -> bool {
        file.strip_prefix(&self.path).is_some_and(|rest| rest.starts_with('/'))
    }
}

/// The `BUILD.bazel` of a module root.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct BazelFile {
    #[serde(default)]
    pub targets: Vec<BazelTarget>,
}

/// One rule call of a `BUILD.bazel`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct BazelTarget {
    /// `None` for a rule call without a literal name.
    #[serde(default)]
    pub name: Option<String>,
    pub kind: String,
    /// The JPS module of the rule: its `module_name` attribute, or the `build <module>` section of the generator.
    #[serde(default)]
    pub module_name: Option<String>,
    /// The keys of the `env` attribute.
    #[serde(default)]
    pub env_keys: Vec<String>,
    #[serde(default)]
    pub deps: Vec<Dependency>,
}

impl BazelTarget {
    /// Whether the target pins `JB_TEST_JUNIT5_FILTERS`, so it runs a named subset of its test library.
    pub fn is_filtered(&self) -> bool {
        self.env_keys.iter().any(|key| key == "JB_TEST_JUNIT5_FILTERS")
    }

    /// The targets that one attribute of the rule names.
    pub fn deps_of<'a>(&'a self, attribute: &'a str) -> impl Iterator<Item = &'a DependencyTarget> {
        self.deps
            .iter()
            .filter(move |dependency| dependency.attr == attribute)
            .flat_map(|dependency| dependency.targets.iter())
    }
}

/// One label of a dependency attribute.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct Dependency {
    pub label: String,
    pub attr: String,
    /// The targets that the label names, each by its module root and its name.
    #[serde(default)]
    pub targets: Vec<DependencyTarget>,
}

/// One target that a label names.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub struct DependencyTarget {
    pub root: String,
    pub name: String,
}

/// The answers of one `accio symbol <name>...` call, one per name in the order of the names.
///
/// An answer of another length, an answer that accio marks `available: false`, and a text that is no answer are
/// refused.
pub fn parse_symbols(text: &str, names: &[&str]) -> Result<Vec<SymbolAnswer>, Refusal> {
    let answers: Vec<SymbolAnswer> =
        serde_json::from_str(text).map_err(|error| accio_failed(format!("accio symbol printed an answer that bt cannot read: {error}")))?;
    if answers.len() != names.len() {
        return Err(accio_failed(format!(
            "accio symbol answered {} objects for {} names",
            answers.len(),
            names.len()
        )));
    }
    if let Some(unavailable) = answers.iter().find(|answer| answer.available == Some(false)) {
        return Err(unavailable_refusal("symbol", unavailable.note.as_deref()));
    }
    Ok(answers)
}

/// The answer of one `accio modules --paths` call. An answer that accio marks `available: false` and a text that is
/// no answer are refused.
pub fn parse_modules(text: &str) -> Result<ModulesAnswer, Refusal> {
    let answer: ModulesAnswer = serde_json::from_str(text)
        .map_err(|error| accio_failed(format!("accio modules printed an answer that bt cannot read: {error}")))?;
    if answer.available == Some(false) {
        return Err(unavailable_refusal("modules", answer.note.as_deref()));
    }
    Ok(answer)
}

fn unavailable_refusal(command: &str, note: Option<&str>) -> Refusal {
    accio_failed(format!(
        "accio {command} has no index of this checkout: {}",
        note.unwrap_or("accio gave no reason")
    ))
}

#[cfg(test)]
mod tests;
