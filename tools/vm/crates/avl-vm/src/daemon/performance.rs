//! The IJ Perf plan of an iteration of the lane daemon: the series that the lane document names, and the build of the
//! IDE. The iteration publishes it before the run, and the results write the files of the iteration from it.

use std::path::Path;

use avl_report::performance::Plan;
use avl_wire::program::lane::parse_lane_document;
use avl_wire::program::read_lane_documents;

use crate::daemon::build::PreparedBuild;

#[cfg(test)]
mod tests;

/// The key of the lane documents among the lane inputs of the descriptor.
const LANE_DOCUMENTS: &str = "laneDocuments";

/// The plan of `lane` from the lane documents that `prep` pushes, `None` for a lane whose document names no series, or
/// why the plan cannot be made.
pub(crate) fn plan(prep: &PreparedBuild, lane: &str) -> Result<Option<Plan>, String> {
    let input = prep
        .lane_inputs
        .iter()
        .find(|input| input.field == LANE_DOCUMENTS)
        .ok_or_else(|| format!("the descriptor names no lane input {LANE_DOCUMENTS}"))?;
    plan_of(Path::new(&input.host.path), lane, &prep.ide_build)
}

/// [`plan`] over the lane documents at `lane_documents`, a directory or a jar, and the IDE build `ide_build`.
fn plan_of(lane_documents: &Path, lane: &str, ide_build: &Result<String, String>) -> Result<Option<Plan>, String> {
    let documents = read_lane_documents(lane_documents)?;
    let name = format!("{lane}.json");
    let content = documents.get(&name).ok_or_else(|| format!("the lane documents hold no {name}"))?;
    let document = parse_lane_document(content).map_err(|problem| format!("the lane document {name} {problem}"))?;
    if document.performance_series.is_empty() {
        return Ok(None);
    }
    Ok(Some(Plan {
        ide_build: ide_build.clone()?,
        series: document.performance_series,
    }))
}
