//! `report`: what the runs of this machine left on the host, read without a worker. `report time` is the one verb.
//!
//! A run leaves its journal under `<runtime root>/runs/<runId>`, the report of each iteration under the reports of its
//! worker, and the trace zips that the journal and the reports name. A verb here reads those files only, so it needs no
//! lease, no Bazel and no guest.

use avl_base::{Config, Outcome, Refusal};
use clap::Subcommand;

mod time;

pub(crate) use time::TimeArgs;

/// The `report` verbs.
#[derive(Subcommand, Debug)]
pub(crate) enum ReportVerb {
    /// Where the time of a run went: in the test cases, around them, and in the relaunches of the IDE.
    #[command(long_about = time::LONG_ABOUT)]
    Time(TimeArgs),
}

/// Answers one `report` verb from the files under the runtime root of `settings`.
pub(crate) fn command_report(settings: &Config, verb: ReportVerb) -> Result<Outcome, Refusal> {
    match verb {
        ReportVerb::Time(args) => time::command_time(&settings.runtime_root, &args),
    }
}
