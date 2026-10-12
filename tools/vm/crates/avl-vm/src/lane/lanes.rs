//! What a `--lane` or a selector becomes. The UI lanes themselves are `avl_affected::lanes`.

use std::collections::BTreeMap;
use std::sync::LazyLock;

use avl_affected::{air_area, air_areas, ui_lane, ui_lane_of_label, ui_lanes};
use avl_base::{Config, Exit, Refusal, Reporter};
use bt_core::{ResolutionInputs, Selector, resolve_selector};
use regex::Regex;

use crate::lane::runtime::bt_runtime;
use avl_base::RefusalExt;

/// What a `--lane` or a selector became: the lane's content label, and the test JVM's filters.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Selection {
    pub label: String,
    /// `None` for a selector, which names no lane at all. The daemon branches on it: a lane iteration is selected
    /// by tag, a selector iteration by class filter.
    pub lane: Option<String>,
    /// The environment for the guest test JVM: lane JUnit filters, or the resolved class filter.
    pub test_env: BTreeMap<String, String>,
}

/// The variable a JUnit filter travels in, for the daemon's runner and for `JUnit5BazelRunner` alike.
pub(crate) const JUNIT5_FILTERS: &str = "JB_TEST_JUNIT5_FILTERS";

/// Answers what one UI lane runs, and refuses a lane a VM worker cannot.
pub(crate) fn lane_selection(lane: &str) -> Result<Selection, Refusal> {
    let ui = ui_lane(lane)?;
    let mut test_env = BTreeMap::from([(JUNIT5_FILTERS.to_owned(), format!("include-tag={}", ui.junit_tag))]);
    // Bazel's `--test_env=NAME=VALUE` form: a variable the lane sets *by value*. The valueless form forwards the
    // client's own variable, and a host value names a machine the guest is not.
    static ASSIGNMENT: LazyLock<Regex> = LazyLock::new(|| {
        // An invariant: a literal pattern, exercised by the tests.
        Regex::new(r"^--test_env=([A-Za-z_][A-Za-z0-9_]*)=(.*)$").expect("a literal pattern compiles")
    });
    for extra in air_area().lanes().get(lane).map(|spec| spec.extra.as_slice()).unwrap_or_default() {
        if let Some(assignment) = ASSIGNMENT.captures(extra) {
            test_env.insert(assignment[1].to_owned(), assignment[2].to_owned());
        }
    }
    Ok(Selection {
        label: ui.label.to_owned(),
        lane: Some(ui.name.to_owned()),
        test_env,
    })
}

/// Resolves a BT selector and refuses anything that is not one IDE UI target.
///
/// The runtime it resolves through cannot spawn, so this reaches no Bazel and no VM: the refusal below is decided
/// from the checkout alone.
pub(crate) fn resolve_selector_selection(settings: &Config, selector: &str, reporter: &Reporter) -> Result<Selection, Refusal> {
    let runtime = bt_runtime(settings.host_repo()?, reporter);
    let classified = Selector::classify(selector).map_err(as_usage)?;
    let inputs = ResolutionInputs::new(&runtime, air_areas());
    let resolution = resolve_selector(&runtime, &classified, &inputs).map_err(as_usage)?;
    let label = match resolution.labels.as_slice() {
        [label] if !resolution.multi_target && ui_lane_of_label(label).is_some() => label.clone(),
        labels => {
            return Err(Refusal::usage(format!(
                "{selector} resolves to {}; a VM worker runs only the IDE UI targets ({})",
                labels.join(", "),
                ui_lanes().iter().map(|lane| lane.label).collect::<Vec<_>>().join(", ")
            )));
        }
    };
    let mut test_env = BTreeMap::new();
    // The same selection Bazel would apply: `--test_filter` travels as TESTBRIDGE_TEST_ONLY.
    if let Some(filter) = resolution.filter {
        test_env.insert("TESTBRIDGE_TEST_ONLY".to_owned(), filter);
    }
    if let Some(package) = resolution.include_package {
        test_env.insert(JUNIT5_FILTERS.to_owned(), format!("include-package={package}"));
    }
    Ok(Selection {
        label,
        lane: None,
        test_env,
    })
}

/// Folds a `bt` refusal into the controller's `usage`.
///
/// A selector this controller declines to run is the caller's invocation being wrong whichever way `bt` declined it.
/// A refusal that the bridge maps to the controller's usage exit passes through unchanged, code and details included:
/// `no_affected_suite` for a flow no suite covers is the same answer on the host and here. Every other refusal of
/// `bt`, such as `bt_infra`, becomes `usage` with its message.
pub(crate) fn as_usage(refused: bt_core::Refusal) -> Refusal {
    let refusal = avl_affected::refusal(refused);
    if refusal.exit == Exit::USAGE {
        refusal
    } else {
        Refusal::usage(refusal.message)
    }
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
