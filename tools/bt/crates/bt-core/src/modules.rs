//! The `--module` selector: a JPS module name, resolved to the `jps_test` target of its directory.
//!
//! Three files of the checkout answer it:
//!
//! - `.idea/modules.xml` at the checkout root gives the directory of the module's `.iml`.
//! - The `BUILD.bazel` of that directory gives the `jps_test` target that runs the tests of the module
//!   ([`module_jps_test_name`]).
//! - The migrated list gives the modules whose tests run under Bazel. It is the list that `tests.cmd` reads, and
//!   [`MigratedList`] reads it with the rules of `BazelMigratedTestModules.kt`.
//!
//! A module of the migrated list resolves, and so does a module inside an area. Every other module is refused with
//! the `tests.cmd` command that runs it.

use refusal::Refusal;
use serde_json::json;

use crate::areas::Areas;
use crate::details::WithDetails;
use crate::exit::{USAGE, fail_infra};
use crate::regex;
use crate::runtime::{Runtime, repo_file};
use crate::scan::{module_jps_test_name, read_text, read_text_or_none};
use crate::selector::{Resolution, suggest_names};

/// The module list of the checkout, relative to the checkout root.
pub const MODULES_FILE: &str = ".idea/modules.xml";

/// The refusal code for a module that the module list does not name.
pub const MODULE_UNKNOWN: &str = "module_unknown";

/// The refusal code for a module without a `jps_test` target of its own.
pub const MODULE_WITHOUT_TEST_TARGET: &str = "module_without_test_target";

/// The refusal code for a module whose tests do not run under Bazel yet.
pub const MODULE_NOT_MIGRATED: &str = "module_not_migrated";

/// The two checkout layouts. They differ in the path of the migrated list, the repository of a community label and
/// the path of `bt.cmd`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Layout {
    /// The ultimate root, with the community repository in `community/`.
    Ultimate,
    /// A community checkout, which is its own root.
    Community,
}

impl Layout {
    /// The ultimate root holds `community/MODULE.bazel`. A community checkout has no `community/` directory.
    fn detect(runtime: &dyn Runtime) -> Self {
        if runtime.exists(&repo_file(runtime, "community/MODULE.bazel")) {
            Self::Ultimate
        } else {
            Self::Community
        }
    }

    const fn migrated_list_file(self) -> &'static str {
        match self {
            Self::Ultimate => "community/build/bazel-migrated-test-modules.txt",
            Self::Community => "build/bazel-migrated-test-modules.txt",
        }
    }

    const fn wrapper(self) -> &'static str {
        match self {
            Self::Ultimate => "./community/tools/bt.cmd",
            Self::Community => "./tools/bt.cmd",
        }
    }

    /// The label of a target in a repo-relative directory. In the ultimate root, a directory under `community/`
    /// belongs to the `@community` repository.
    fn label(self, dir: &str, name: &str) -> String {
        let community = match self {
            Self::Ultimate if dir == "community" => Some(""),
            Self::Ultimate => dir.strip_prefix("community/"),
            Self::Community => None,
        };
        match community {
            Some(rest) => format!("@community//{rest}:{name}"),
            None => format!("//{dir}:{name}"),
        }
    }
}

/// The patterns of the migrated list. A pattern is a module name, or a module name prefix with one trailing `*`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct MigratedList {
    patterns: Vec<String>,
}

impl MigratedList {
    /// Reads the text of the list. A blank line and a line that starts with `#` are skipped. A line that is not a
    /// pattern and a duplicate pattern are refused, and the refusal names `file:line`.
    pub fn parse(text: &str, file: &str) -> Result<Self, Refusal> {
        let mut patterns: Vec<String> = Vec::new();
        for (index, line) in text.lines().enumerate() {
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let number = index + 1;
            if !regex!(r"^[A-Za-z0-9._-]+\*?$").is_match(line) {
                return Err(fail_infra(format!(
                    "{file}:{number}: '{line}' is not a module name or a module name prefix with '*' at the end"
                )));
            }
            if patterns.iter().any(|pattern| pattern == line) {
                return Err(fail_infra(format!("{file}:{number}: duplicate pattern '{line}'")));
            }
            patterns.push(line.to_owned());
        }
        Ok(Self { patterns })
    }

    /// Whether a pattern of the list matches the module.
    pub fn contains(&self, module: &str) -> bool {
        self.patterns.iter().any(|pattern| match pattern.strip_suffix('*') {
            Some(prefix) => module.starts_with(prefix),
            None => pattern == module,
        })
    }
}

/// Every module of the module list, as `(directory, module name)`. The directory is repo-relative, and it is empty
/// for a module at the checkout root.
fn listed_modules(modules_xml: &str) -> impl Iterator<Item = (&str, &str)> {
    regex!(r#"\bfilepath="\$PROJECT_DIR\$/([^"]+)\.iml""#)
        .captures_iter(modules_xml)
        .filter_map(|found| {
            let path = found.get(1)?.as_str();
            Some(path.rsplit_once('/').unwrap_or(("", path)))
        })
}

/// A repo-relative file of a repo-relative directory.
fn file_in(dir: &str, file: &str) -> String {
    if dir.is_empty() { file.to_owned() } else { format!("{dir}/{file}") }
}

/// Resolves a JPS module to its `jps_test` label, with no filter.
///
/// The module list and the `BUILD.bazel` of the module are read. The migrated list is read only for a module that no
/// area owns.
pub fn resolve_module(runtime: &dyn Runtime, areas: &Areas, module: &str) -> Result<Resolution, Refusal> {
    let modules_xml = read_text(runtime, MODULES_FILE)?;
    let Some(dir) = listed_modules(&modules_xml).find_map(|(dir, name)| (name == module).then_some(dir)) else {
        let suggestions = suggest_names(module, listed_modules(&modules_xml).map(|(_, name)| name), 3);
        let hint: String = suggestions.iter().map(|name| format!("\n  did you mean  {name}")).collect();
        return Err(Refusal::new(
            MODULE_UNKNOWN,
            USAGE,
            format!("module {module} is not in {MODULES_FILE}{hint}"),
        ));
    };

    let build_file = file_in(dir, "BUILD.bazel");
    let without_target = |reason: String| {
        Refusal::new(
            MODULE_WITHOUT_TEST_TARGET,
            USAGE,
            format!("module {module} has no test target: {reason}. Run: ./tests.cmd --module {module} --test <FQN>"),
        )
    };
    let Some(build_text) = read_text_or_none(runtime, &build_file) else {
        return Err(without_target(format!("{build_file} does not exist")));
    };
    let Some(name) = module_jps_test_name(&build_text, module)? else {
        return Err(without_target(format!("{build_file} declares no jps_test that runs the module")));
    };

    let layout = Layout::detect(runtime);
    let label = layout.label(dir, &name);
    if !areas.iter().any(|area| area.owns(dir)) {
        let list_file = layout.migrated_list_file();
        let list = MigratedList::parse(&read_text(runtime, list_file)?, list_file)?;
        if !list.contains(module) {
            return Err(Refusal::new(
                MODULE_NOT_MIGRATED,
                USAGE,
                format!(
                    "the tests of module {module} do not run under Bazel yet ({list_file}). Run: ./tests.cmd --module \
                     {module} --test <FQN>. The label {label} still runs on its own: {} {label} --filter <FQN>",
                    layout.wrapper()
                ),
            )
            .with_details(json!({ "module": module, "label": label, "migratedList": list_file })));
        }
    }
    Ok(Resolution {
        labels: vec![label],
        multi_target: false,
        ..Resolution::default()
    })
}

#[cfg(test)]
mod tests;
