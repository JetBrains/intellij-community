//! Test-root discovery and the class index, over the directories of the areas.
//!
//! Three invariants verified across the Air area keep this cheap, and an area must keep them too:
//! - every BUILD.bazel that declares `jps_test` declares exactly one unfiltered one, and it sits in the same
//!   directory as the .iml, so there is no walking up and no picking among targets;
//! - a test file's own `package` declaration is authoritative (it already equals the testSrc packagePrefix plus
//!   the relative directory), so the prefix is only a hint for package runs;
//! - every declared test source root is named `test`, `testSrc` or `tests`, so none of them can sit under a pruned
//!   directory, and one pruned walk can serve both discovery passes.
//!
//! The tree is scanned exactly once ([`scan_tree`]) and only when a selector actually needs it.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::sync::OnceLock;

use regex::Regex;

use crate::areas::{AREAS_FILE, Areas};
use crate::refusal::{Refusal, fail_infra, fail_usage};
use crate::regex;
use crate::runtime::{DirEntry, Runtime, par_map, repo_file};

/// One test source root that has a runnable target.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct TestRoot {
    /// The repo-relative directory holding the .iml and the BUILD.bazel.
    pub module_dir: String,
    /// The repo-relative test source root.
    pub src_dir: String,
    pub label: String,
    /// The shortened prefix the .iml declares, a hint for package selectors only. `None` when the root declares
    /// none, which is a different fact from declaring the empty prefix.
    pub package_prefix: Option<String>,
}

/// One source file that could hold the wanted class.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Candidate {
    pub simple_name: String,
    /// The repo-relative path of the source file.
    pub file: String,
    pub root: TestRoot,
}

/// The simple-name index, sorted by name.
pub type Index = BTreeMap<String, Vec<Candidate>>;

/// One `isTestSource` source folder an .iml declares.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ImlTestRoot {
    pub path: String,
    pub package_prefix: Option<String>,
}

const PRUNED_DIRS: &[&str] = &[
    "out",
    "node_modules",
    ".git",
    ".idea",
    "generated",
    "dist",
    "build",
];

/// The test source roots an .iml declares: `isTestSource="true"` source folders that are not resource roots. The
/// tests .iml of a module such as backend/session/runtime declares both a `java-test-resource` testData root and
/// the real testSrc root; only the latter holds sources.
pub fn parse_iml_test_roots(iml_text: &str) -> Vec<ImlTestRoot> {
    let source_folder = regex!(r"<sourceFolder\b([^>]*?)/?>");
    let test_source = regex!(r#"\bisTestSource\s*=\s*"true""#);
    let resource_type = regex!(r#"\btype\s*=\s*"[^"]*resource[^"]*""#);
    let module_dir_url = regex!(r#"\burl\s*=\s*"file://\$MODULE_DIR\$/([^"]+)""#);
    let package_prefix = regex!(r#"\bpackagePrefix\s*=\s*"([^"]*)""#);
    source_folder
        .captures_iter(iml_text)
        .filter_map(|folder| {
            let attributes = folder.get(1)?.as_str();
            if !test_source.is_match(attributes) || resource_type.is_match(attributes) {
                return None;
            }
            let path = module_dir_url
                .captures(attributes)?
                .get(1)?
                .as_str()
                .to_owned();
            // An empty `packagePrefix=""` is read as "no prefix": it declares the default package, which is not a
            // prefix any Air test class shares, and treating it as one would make every package selector match.
            let package_prefix = package_prefix
                .captures(attributes)
                .and_then(|prefix| prefix.get(1))
                .map(|prefix| prefix.as_str())
                .filter(|prefix| !prefix.is_empty())
                .map(str::to_owned);
            Some(ImlTestRoot {
                path,
                package_prefix,
            })
        })
        .collect()
}

/// The module's `jps_test` target name in a BUILD.bazel, or `None` when it declares none.
///
/// Sibling `jvm_library` and `*_test_lib` rules must not be mistaken for it, and the name is not derivable from the
/// module name (plugins/air/shared/core declares `ai-agent-core-tests_test`, not `air-...`).
///
/// A target that pins `JB_TEST_JUNIT5_FILTERS` is skipped: it runs a named subset of the same `_test_lib`, so it is
/// never the answer to "run this class", and resolving a class to the narrowed one would report zero tests for
/// every class outside its filter. No Air integration target pins one any more (each lane is its own module, ADR
/// 0037), but `plugins/air/sdd/wysiwyg` still does.
pub fn parse_jps_test_name(build_text: &str) -> Result<Option<String>, Refusal> {
    let marker = regex!(r"(?m)^jps_test\(");
    let rule_name = regex!(r#"(?:^|[\s,(])name\s*=\s*"([^"]+)""#);
    let names: Vec<String> = marker
        .find_iter(build_text)
        .map(|found| rule_body(build_text, found.end()))
        .filter(|body| !body.contains("JB_TEST_JUNIT5_FILTERS"))
        .filter_map(|body| Some(rule_name.captures(&body)?.get(1)?.as_str().to_owned()))
        .collect();
    match names.len() {
        0 => Ok(None),
        1 => Ok(names.into_iter().next()),
        count => Err(fail_infra(format!(
            "Expected one unfiltered jps_test per BUILD.bazel, found {count}: {}",
            names.join(", ")
        ))),
    }
}

/// The text of a rule's argument list, from just past its opening paren to the matching close, with `#` comments
/// blanked out so a commented-out `name = "..."` cannot be mistaken for the real one.
///
/// Scanned over bytes: every delimiter Starlark gives this scanner is ASCII, and a multi-byte character inside a
/// string or a comment cannot contain one.
fn rule_body(text: &str, start: usize) -> String {
    let bytes = text.as_bytes();
    let mut out = Vec::new();
    let mut depth = 1;
    let mut quote = None;
    let mut index = start;
    while index < bytes.len() {
        let char = bytes[index];
        if let Some(open) = quote {
            out.push(char);
            if char == b'\\' && index + 1 < bytes.len() {
                index += 1;
                out.push(bytes[index]);
            } else if char == open {
                quote = None;
            }
            index += 1;
            continue;
        }
        match char {
            b'"' | b'\'' => {
                out.push(char);
                quote = Some(char);
            }
            b'#' => {
                let stop = bytes[index..]
                    .iter()
                    .position(|&byte| byte == b'\n')
                    .map_or(bytes.len(), |offset| index + offset);
                out.resize(out.len() + (stop - index), b' ');
                index = stop;
                continue;
            }
            b'(' | b'[' | b'{' => {
                depth += 1;
                out.push(char);
            }
            b')' | b']' | b'}' => {
                depth -= 1;
                if depth == 0 {
                    break;
                }
                out.push(char);
            }
            _ => out.push(char),
        }
        index += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

/// The `package` declaration of a Kotlin or Java source file, or `""` when there is none, which is the default
/// package and a real answer rather than a failure.
///
/// `\r` is accepted before the end of line because `$` under `(?m)` matches only before a LF: without it a source
/// file with CRLF line endings would resolve to the default package and every class in it would read as absent.
pub fn derive_package(source_text: &str) -> String {
    let declaration = regex!(r"(?m)^[ \t]*package[ \t]+([A-Za-z0-9_.`]+)[ \t]*;?[ \t\r]*$");
    declaration
        .captures(source_text)
        .and_then(|found| found.get(1))
        .map(|name| name.as_str().replace('`', ""))
        .unwrap_or_default()
}

/// The pattern of a top-level class, object, interface or record declaration with this name. The name is quoted
/// even though every caller's name has passed a validating pattern: the cost is nothing, and forgetting it would
/// be a pattern that matches the wrong class.
pub(crate) fn type_declaration_pattern(simple_name: &str) -> Regex {
    let pattern = format!(
        r"(?m)^\s*(?:@\w[\w.]*(?:\([^)]*\))?\s*)*(?:(?:public|private|protected|internal|abstract|final|open|sealed|data|value|annotation|enum|static|inner)\s+)*(?:class|object|interface|record)\s+{}\b",
        regex::escape(simple_name)
    );
    // An invariant: the only variable part is escaped.
    Regex::new(&pattern).expect("the type declaration pattern compiles")
}

/// Whether the text declares a top-level type with this name. It is what catches a filename that differs from the
/// class name, which is how `@Nested` and secondary top-level classes are found at all.
pub fn declares_type(source_text: &str, simple_name: &str) -> bool {
    type_declaration_pattern(simple_name).is_match(source_text)
}

/// One pruned pass over the area directories.
#[derive(Clone, Debug, Default)]
pub struct TreeScan {
    /// The repo-relative path of every .iml outside a pruned directory.
    pub imls: Vec<String>,
    /// The repo-relative path of every .kt/.java file outside a pruned directory.
    pub sources: Vec<String>,
    /// Every directory the walk was able to read, so no root needs its own existence probe.
    pub dirs: HashSet<String>,
}

/// The simple name of a Kotlin or Java source path.
pub fn source_simple_name(path: &str) -> Option<&str> {
    regex!(r"([^/]+)\.(?:kt|java)$")
        .captures(path)
        .and_then(|found| found.get(1))
        .map(|name| name.as_str())
}

/// A repo-relative directory's listing, or `None` when it cannot be read. An empty directory is a real listing,
/// distinct from an unreadable one.
pub fn read_dir_or_none(runtime: &dyn Runtime, dir: &str) -> Option<Vec<DirEntry>> {
    runtime.read_dir(&repo_file(runtime, dir)).ok()
}

/// A repo-relative file that may legitimately not exist, saving the extra stat an `exists` probe would cost.
pub fn read_text_or_none(runtime: &dyn Runtime, file: &str) -> Option<String> {
    runtime.read_text_file(&repo_file(runtime, file)).ok()
}

/// A repo-relative file that must be readable: it was listed a moment ago.
pub fn read_text(runtime: &dyn Runtime, file: &str) -> Result<String, Refusal> {
    runtime
        .read_text_file(&repo_file(runtime, file))
        .map_err(|error| fail_infra(format!("{file} is unreadable: {error}")))
}

/// One pruned, breadth-first, concurrent pass over the directories; both discovery passes read its result.
pub fn scan_tree(runtime: &dyn Runtime, dirs: &[&str]) -> TreeScan {
    let mut scan = TreeScan::default();
    let mut frontier: Vec<String> = dirs.iter().map(|dir| (*dir).to_owned()).collect();
    while !frontier.is_empty() {
        let listings = par_map(&frontier, |dir| read_dir_or_none(runtime, dir));
        let mut next = Vec::new();
        for (dir, entries) in frontier.iter().zip(listings) {
            let Some(entries) = entries else { continue };
            scan.dirs.insert(dir.clone());
            for entry in entries {
                let path = format!("{dir}/{}", entry.name);
                if entry.is_dir {
                    // `bazel-*` is the convenience symlink farm bazel drops in a workspace; following it would walk
                    // the whole output base and index stale generated copies of these sources.
                    if !PRUNED_DIRS.contains(&entry.name.as_str())
                        && !entry.name.starts_with("bazel-")
                    {
                        next.push(path);
                    }
                } else if entry.name.ends_with(".iml") {
                    scan.imls.push(path);
                } else if source_simple_name(&entry.name).is_some() {
                    scan.sources.push(path);
                }
            }
        }
        frontier = next;
    }
    scan
}

/// Every test source root of the scan that has a runnable `jps_test` target.
pub fn collect_test_roots(
    runtime: &dyn Runtime,
    scan: &TreeScan,
) -> Result<Vec<TestRoot>, Refusal> {
    let declared = par_map(&scan.imls, |iml| {
        let text = read_text(runtime, iml)?;
        let module_dir = iml.rsplit_once('/').map_or("", |(dir, _)| dir).to_owned();
        Ok::<_, Refusal>((module_dir, parse_iml_test_roots(&text)))
    });
    let mut with_tests = Vec::new();
    for entry in declared {
        let (module_dir, roots) = entry?;
        if !roots.is_empty() {
            with_tests.push((module_dir, roots));
        }
    }

    // One BUILD.bazel read per module directory: several .iml files can share one.
    let mut module_dirs: Vec<&str> = Vec::new();
    for (module_dir, _) in &with_tests {
        if !module_dirs.contains(&module_dir.as_str()) {
            module_dirs.push(module_dir);
        }
    }
    let found = par_map(&module_dirs, |module_dir| {
        let Some(text) = read_text_or_none(runtime, &format!("{module_dir}/BUILD.bazel")) else {
            return Ok(None);
        };
        Ok::<_, Refusal>(parse_jps_test_name(&text)?.map(|name| format!("//{module_dir}:{name}")))
    });
    let mut labels: HashMap<&str, String> = HashMap::new();
    for (module_dir, label) in module_dirs.iter().zip(found) {
        if let Some(label) = label? {
            labels.insert(module_dir, label);
        }
    }

    let mut roots = Vec::new();
    let mut seen = HashSet::new();
    for (module_dir, declared_roots) in &with_tests {
        let Some(label) = labels.get(module_dir.as_str()) else {
            continue;
        };
        for root in declared_roots {
            let src_dir = format!("{module_dir}/{}", root.path);
            if !scan.dirs.contains(&src_dir) || !seen.insert(src_dir.clone()) {
                continue;
            }
            roots.push(TestRoot {
                module_dir: module_dir.clone(),
                src_dir,
                label: label.clone(),
                package_prefix: root.package_prefix.clone(),
            });
        }
    }
    Ok(roots)
}

/// Maps a simple name to its candidates, keyed off filenames only; no file contents are read here.
pub fn build_index(scan: &TreeScan, roots: &[TestRoot]) -> Index {
    let mut index = Index::new();
    for file in &scan.sources {
        let Some(simple_name) = source_simple_name(file) else {
            continue;
        };
        // A file under nested source roots belongs to each of them.
        for root in roots {
            if file.starts_with(&format!("{}/", root.src_dir)) {
                index
                    .entry(simple_name.to_owned())
                    .or_default()
                    .push(Candidate {
                        simple_name: simple_name.to_owned(),
                        file: file.clone(),
                        root: root.clone(),
                    });
            }
        }
    }
    index
}

/// The memoized, lazy tree scan.
///
/// Forcing either accessor scans the area directories, which a //label and a //pkg/... pattern must not pay for
/// (measured at ~250 ms of pure waste on the Air area). Only name and package selectors reach in here. Forcing
/// twice, from anywhere, scans once, and a failure is memoized with the answer.
pub struct ResolutionInputs<'r> {
    runtime: &'r dyn Runtime,
    areas: &'r Areas,
    scan: OnceLock<TreeScan>,
    roots: OnceLock<Result<Vec<TestRoot>, Refusal>>,
    index: OnceLock<Result<Index, Refusal>>,
}

impl<'r> ResolutionInputs<'r> {
    /// The lazy inputs. Nothing is read until [`ResolutionInputs::roots`] or [`ResolutionInputs::index`] is called.
    pub const fn new(runtime: &'r dyn Runtime, areas: &'r Areas) -> Self {
        ResolutionInputs {
            runtime,
            areas,
            scan: OnceLock::new(),
            roots: OnceLock::new(),
            index: OnceLock::new(),
        }
    }

    /// The areas resolution scans and reads its lanes and its catalog from.
    pub const fn areas(&self) -> &'r Areas {
        self.areas
    }

    fn scan(&self) -> &TreeScan {
        self.scan
            .get_or_init(|| scan_tree(self.runtime, &self.areas.dirs()))
    }

    /// Every test source root with a runnable target. A checkout with no area has nothing to scan, which is
    /// refused rather than answered as an empty tree: "no such class" would be the wrong reason.
    pub fn roots(&self) -> Result<&[TestRoot], Refusal> {
        self.roots
            .get_or_init(|| {
                if self.areas.is_empty() {
                    return Err(fail_usage(format!(
                        "{AREAS_FILE} names no area, so a name or a package selector has nothing to scan; pass a \
                         //label or a //pkg/... pattern"
                    )));
                }
                collect_test_roots(self.runtime, self.scan())
            })
            .as_deref()
            .map_err(Clone::clone)
    }

    /// The simple-name index.
    pub fn index(&self) -> Result<&Index, Refusal> {
        self.index
            .get_or_init(|| Ok(build_index(self.scan(), self.roots()?)))
            .as_ref()
            .map_err(Clone::clone)
    }
}

#[cfg(test)]
mod tests;
