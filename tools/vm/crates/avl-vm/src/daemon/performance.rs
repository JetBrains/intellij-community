//! The IJ Perf plan of an iteration of the lane daemon: the series that the lane document names, and the build of the
//! IDE. The iteration publishes it before the run, and the results write the files of the iteration from it.
//! `avl_junit::performance::plan` makes the plan, and `lane-run` calls it too.

use std::path::Path;

use avl_junit::performance::{self, Plan};
use avl_wire::program::read_lane_documents;

use crate::daemon::build::PreparedBuild;

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
    let documents = read_lane_documents(Path::new(&input.host.path))?;
    performance::plan(&documents, lane, prep.ide_build.clone())
}
