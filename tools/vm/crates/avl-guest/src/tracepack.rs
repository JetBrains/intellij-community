//! `trace-pack-ready`: one daemon iteration's trace bundles into zips the controller pulls.
//!
//! The lane writes the bundles inside the guest, under the iteration's `air-traces` directory, and nothing on the
//! host can read that tree. So the controller runs this verb and pulls the file it writes. The zipping is
//! `air-trace pack`'s own code, [`avl_trace_tools::pack::pack`], not a second implementation: the server reads the pulled
//! zip exactly as it reads a host's.

use std::collections::HashSet;
use std::fs::{self, OpenOptions};
use std::io::{self, Write};

use avl_trace_tools::pack::{PackOptions, Report, pack};
use avl_wire::verb::AgentVerb;

use crate::cli::TracePackReadyArgs;
use crate::reply::AgentRefusal;
use crate::reply::AgentRefusalExt;

#[cfg(test)]
mod tests;

/// Packs the bundles the ledger does not name yet, and adds them to it.
///
/// The controller runs it after each test of an iteration, so a scenario's trace reaches the host while the lane
/// still runs. Without `--all` it takes only the finished bundles, the ones with a manifest: the recorder writes
/// the manifest last, so a bundle without one is still being written. The last call of an iteration passes
/// `--all`, so a bundle whose recorder was killed still arrives. When nothing is new it writes no zip, and the
/// report's destination is empty.
pub(crate) fn pack_ready(args: &TracePackReadyArgs) -> Result<Report, AgentRefusal> {
    let refuse = |error: &dyn std::fmt::Display| AgentRefusal::for_verb(AgentVerb::TracePackReady, error.to_string());
    let ledger = match fs::read_to_string(&args.ledger) {
        Ok(text) => text,
        Err(error) if error.kind() == io::ErrorKind::NotFound => String::new(),
        Err(error) => return Err(refuse(&error)),
    };
    let packed: HashSet<&str> = ledger.lines().filter(|line| !line.is_empty()).collect();
    let select = |bundle: &str, finished: bool| !packed.contains(bundle) && (finished || args.all);
    let options = PackOptions { select: Some(&select) };
    let report = pack(&args.source, &args.destination, &options).map_err(|error| refuse(&error))?;
    if !report.packed.is_empty() {
        // Written after the zip: a zip whose bundles the ledger does not name is packed again next time, and the
        // host then has one bundle twice. The other order would lose it.
        let mut lines = report.packed.join("\n");
        lines.push('\n');
        OpenOptions::new()
            .create(true)
            .append(true)
            .open(&args.ledger)
            .and_then(|mut file| file.write_all(lines.as_bytes()))
            .map_err(|error| refuse(&error))?;
    }
    Ok(report)
}
