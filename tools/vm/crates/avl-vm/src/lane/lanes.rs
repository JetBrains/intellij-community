//! What a `--lane` or a selector becomes. The UI lanes themselves are `avl_affected::lanes`.

use avl_affected::{air_areas, ui_lane, ui_lane_of_label, ui_lanes};
use avl_base::{Config, Exit, Refusal, Reporter};
use bt_core::{ResolutionInputs, Selector, resolve_selector};

use crate::daemon::run::selector_to_run_request;
use crate::lane::runtime::bt_runtime;
use avl_base::RefusalExt;

/// What a `--lane` or a selector became: the lane's content label, and the selectors and filters that the run request
/// gets.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Selection {
    pub label: String,
    /// `None` for a selector, which names no lane at all. The lane daemon branches on it: a lane iteration runs the
    /// documents of its lane, and a selector iteration narrows them by class filter.
    pub lane: Option<String>,
    /// The JUnit selectors of the run request: the class or the method that a selector names. Empty for a whole lane.
    pub selectors: Vec<String>,
    /// The JUnit5 filters of the run request, `<option>=<value>`: the class filters of the suites, or the filter of a
    /// selector. Empty for a whole lane.
    pub junit5_filters: Vec<String>,
}

/// Answers what one UI lane runs, and refuses a lane a VM worker cannot. The lane daemon selects the documents of the
/// lane by its name, so a whole lane has no filter.
pub(crate) fn lane_selection(lane: &str) -> Result<Selection, Refusal> {
    let ui = ui_lane(lane)?;
    Ok(Selection {
        label: ui.label.to_owned(),
        lane: Some(ui.name.to_owned()),
        selectors: Vec::new(),
        junit5_filters: Vec::new(),
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
    // The same selection Bazel would apply: the test filter wins over the package of the target.
    let (selectors, junit5_filters) = match resolution.filter.filter(|filter| !filter.is_empty()) {
        Some(filter) => selector_to_run_request(&filter),
        None => (
            Vec::new(),
            resolution
                .include_package
                .map(|package| format!("include-package={package}"))
                .into_iter()
                .collect(),
        ),
    };
    Ok(Selection {
        label,
        lane: None,
        selectors,
        junit5_filters,
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
