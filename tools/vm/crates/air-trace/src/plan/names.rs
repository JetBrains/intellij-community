//! Names: what a bare word reaches, and what a test case reaches.

use std::fs;
use std::path::Path;
use std::process::Command;

use avl_base::format::first_line;
use bt_core::{Selector, SelectorKind};

use super::{Kind, Planner, Unmapped, reason};
use crate::{Exit, Refusal};
use bt_core::catalog::{SuiteDocument, SuiteProfile};

/// One JUnit case, or one failure of a report, as a class and a name.
pub(crate) struct TestCase {
    pub class_name: String,
    pub name: String,
}

impl Planner<'_> {
    /// Plans a bare name.
    ///
    /// Every relation of the catalog is asked and the union is planned, because one word is routinely several
    /// things at once: a suite and its scenario, a step and the operation of the same name. Only a name the catalog
    /// does not hold at all goes further, to the test-class selector and then to the checkout's file names, and in
    /// that order, because a class name is also a valid file stem and the selector is the narrower answer.
    pub(crate) fn read_name(&mut self, name: &str) -> anyhow::Result<()> {
        let loaded = self.catalog()?;
        if name.starts_with("flow-") && (loaded.knows_flow(name) || self.flow_text_exists(name)) {
            return self.read_flow(name, name);
        }
        let simple = simple_class_name(name);
        let mut matches = loaded.scenarios_named(name);
        if simple != name {
            matches.extend(loaded.scenarios_named(simple));
        }
        let count = self.add_matches(&matches);
        if count > 0 {
            self.read(name, Kind::Name, format!("{count} scenario(s)"));
            return Ok(());
        }
        if let Ok(selector) = Selector::classify(name)
            && matches!(selector.kind, SelectorKind::SimpleName | SelectorKind::Fqn)
            && selector.method.is_none()
        {
            self.resolve_class(name, name);
            return Ok(());
        }
        // A name with a directory in it is a path the checkout does not have - a deleted or mistyped file - and the
        // join answers it with `path_unreadable`, as `vm.cmd suites` would. Only a bare file name is looked up, since
        // looking up the base name of a missing path would plan some other file that happens to share it.
        if name.contains('/') {
            self.read(name, Kind::Path, name);
            self.paths.push(name.to_owned());
            return Ok(());
        }
        if name.contains('.') {
            return self.read_file_name(name, name, None);
        }
        self.unmapped(Unmapped::new(
            name,
            reason::UNKNOWN_NAME,
            "no flow, suite, scenario, step, operation or check has this id, and it is not a class or a file",
        ));
        Ok(())
    }

    /// Whether the checkout has the flow's text, which is how a flow with no scenario at all is still recognized as
    /// a flow rather than as an unknown name.
    fn flow_text_exists(&self, flow: &str) -> bool {
        let Ok(catalog) = avl_affected::air_area().catalog() else {
            return false;
        };
        catalog
            .flow_text_dir
            .split('/')
            .fold(self.repo_root.clone(), |path, segment| path.join(segment))
            .join(format!("{flow}.txt"))
            .exists()
    }

    /// Plans a test class the catalog does not generate, which is an authored journey or a class in no UI lane at
    /// all.
    ///
    /// Resolved by `bt`'s selector through the runtime `vm.cmd run <Class>` uses, so the planner and the run agree
    /// on which target a class is in. The target decides the lane: a target a UI lane runs is that lane's, and a
    /// class in any other target runs no IDE and records no trace.
    pub(crate) fn resolve_class(&mut self, class_name: &str, given: &str) {
        let selector = match Selector::classify(class_name) {
            Ok(selector) => selector,
            Err(refusal) => {
                let detail = first_line(&refusal.message).to_owned();
                self.unmapped(Unmapped::new(given, reason::UNKNOWN_NAME, detail));
                return;
            }
        };
        let resolution = match bt_core::resolve_selector(self.runtime, &selector, self.selectors) {
            Ok(resolution) => resolution,
            Err(refusal) => {
                let detail = refusal.message.trim().to_owned();
                self.unmapped(Unmapped::new(given, reason::UNKNOWN_NAME, detail));
                return;
            }
        };
        if resolution.labels.len() != 1 || resolution.multi_target {
            let detail = format!("resolves to {}", resolution.labels.join(", "));
            self.unmapped(Unmapped::new(given, reason::NOT_A_UI_LANE_CLASS, detail));
            return;
        }
        let label = &resolution.labels[0];
        let simple = simple_class_name(class_name);
        if let Some(lane) = avl_affected::ui_lane_of_label(label).map(|lane| lane.name) {
            self.add_class(simple, lane);
            self.read(given, Kind::Name, format!("the {lane} class {simple}"));
        } else {
            let detail = format!("resolves to {label}, which launches no IDE");
            self.unmapped(Unmapped::new(given, reason::NOT_A_UI_LANE_CLASS, detail));
        }
    }

    /// Plans a file named without a directory: a dropped file, or a typed name the working directory does not
    /// hold.
    ///
    /// The checkout's tracked files with that name are the candidates. A dropped file's bytes pick among them when
    /// they can, because two files named `AirSessionView.kt` are two different answers and the one whose bytes were
    /// dropped is the one meant; when none matches, every candidate is planned and the note says so.
    pub(crate) fn read_file_name(&mut self, name: &str, given: &str, content: Option<&[u8]>) -> anyhow::Result<()> {
        let content = content.filter(|content| !content.is_empty());
        let candidates = match (self.list_files)(&self.repo_root, name) {
            Ok(candidates) => candidates,
            Err(error) => {
                self.unmapped(Unmapped::new(given, reason::NO_SUCH_FILE, error.message));
                return Ok(());
            }
        };
        if candidates.is_empty() {
            self.unmapped(Unmapped::new(
                given,
                reason::NO_SUCH_FILE,
                "no tracked file of the checkout has this name",
            ));
            return Ok(());
        }
        let mut chosen = candidates.clone();
        if let Some(content) = content
            && candidates.len() > 1
        {
            let identical: Vec<String> = candidates
                .iter()
                .filter(|candidate| fs::read(self.checkout_file(candidate)).is_ok_and(|on_disk| on_disk == content))
                .cloned()
                .collect();
            if identical.is_empty() {
                self.note(format!(
                    "none of the {} files named {name} has the dropped bytes; every one of them was planned",
                    candidates.len()
                ));
            } else {
                chosen = identical;
            }
        }
        if chosen.len() > 1 {
            self.note(format!("{given} names {} files: {}", chosen.len(), chosen.join(", ")));
        }
        for candidate in chosen {
            if content.is_some() {
                // Already classified from its bytes, so it is a source file: the join is all that is left.
                self.read(given, Kind::Path, candidate.clone());
                self.paths.push(candidate);
                continue;
            }
            let absolute = self.checkout_file(&candidate);
            self.read_disk(&absolute, &candidate)?;
        }
        Ok(())
    }

    fn checkout_file(&self, relative: &str) -> std::path::PathBuf {
        relative.split('/').fold(self.repo_root.clone(), |path, segment| path.join(segment))
    }

    /// Plans test cases, which is what a JUnit document and a report both come down to.
    ///
    /// A case of a generated class is its scenario: the scenario runner names each JUnit node after its scenario, so
    /// a case named after a profile is that profile. A case of a generated class that names no scenario is the suite
    /// failing around them - the factory, a reset, a class-level failure - and plans the whole suite. A case of any
    /// other class is resolved as a class.
    pub(crate) fn plan_cases(&mut self, given: &str, cases: &[TestCase]) -> anyhow::Result<()> {
        let loaded = self.catalog()?;
        let mut resolved = std::collections::HashSet::new();
        for entry in cases {
            let simple = simple_class_name(&entry.class_name);
            let Some(owner) = loaded.by_class(simple) else {
                if resolved.insert(simple.to_owned()) {
                    self.resolve_class(&entry.class_name, &entry.class_name);
                }
                continue;
            };
            if let Some(scenario) = scenario_of_case(owner, &entry.name) {
                self.add_scenario(owner, scenario);
                continue;
            }
            for scenario in &owner.profiles {
                self.add_scenario(owner, scenario);
            }
            if !entry.name.is_empty() {
                self.note(format!(
                    "{given}: {:?} names no scenario of {simple}, so every scenario of the class was planned",
                    entry.name
                ));
            }
        }
        Ok(())
    }
}

/// The tracked files with this base name, checkout-relative.
///
/// `git ls-files` and not a walk: the checkout is far too large to walk per question, and the index already answers
/// it. A pathspec's `*` crosses `/`, so `*/<name>` finds the name in any directory; the characters a pathspec would
/// read as a pattern are escaped, so a name is always matched literally.
pub(crate) fn git_list_files(repo_root: &Path, name: &str) -> Result<Vec<String>, Refusal> {
    let escaped = name
        .replace('\\', r"\\")
        .replace('*', r"\*")
        .replace('?', r"\?")
        .replace('[', r"\[");
    let output = Command::new("git")
        .arg("-C")
        .arg(repo_root)
        .args(["ls-files", "-z", "--"])
        .arg(&escaped)
        .arg(format!("*/{escaped}"))
        .output()
        .map_err(|error| git_failed(format!("git ls-files could not answer: {error}")))?;
    if !output.status.success() {
        return Err(git_failed(format!(
            "git ls-files could not answer: {}: {}",
            output.status,
            String::from_utf8_lossy(&output.stderr).trim()
        )));
    }
    Ok(String::from_utf8_lossy(&output.stdout)
        .split('\0')
        .filter(|entry| !entry.is_empty() && entry.rsplit('/').next() == Some(name))
        .map(str::to_owned)
        .collect())
}

/// `git ls-files` did not answer.
fn git_failed(message: String) -> Refusal {
    Refusal::new("git_ls_files_failed", Exit::Broken, message)
}

/// The scenario a JUnit case is the node of: the profile it is named after, or, for a display name that decorates
/// it, the longest profile name it contains.
fn scenario_of_case<'s>(owner: &'s SuiteDocument, case_name: &str) -> Option<&'s SuiteProfile> {
    if let Some(exact) = owner.profiles.iter().find(|scenario| scenario.name == case_name) {
        return Some(exact);
    }
    owner
        .profiles
        .iter()
        .filter(|scenario| case_name.contains(&scenario.name))
        .fold(None, |best: Option<&SuiteProfile>, scenario| match best {
            Some(best) if best.name.len() >= scenario.name.len() => Some(best),
            _ => Some(scenario),
        })
}

/// A class's simple name: the FQN's last segment, and the outer class of a nested one, because a test is selected
/// by its top-level class.
pub(crate) fn simple_class_name(class_name: &str) -> &str {
    let simple = class_name.rsplit('.').next().unwrap_or(class_name);
    simple.split('$').next().unwrap_or(simple)
}
