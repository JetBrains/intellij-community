//! `flake`: K repeat trials of one selection, spread over the workers it could lease, folded into a per-class flake
//! rate.
//!
//! Why this is a verb of its own and not `run --repeat N`. `run` is one lock, one worker, one report, and every
//! caller reads it that way. A repeat *inside* it would be pinned to the single worker its receipt names and would
//! have to pick one of K reports to return; a repeat *outside* it is a new command wearing `run`'s name, whose failure
//! modes (`tests_failed` at exit 6 on the third of twelve trials) are the wrong answer to the question being asked.
//! Measuring intermittency is a different act from running a lane once, so it says so.
//!
//! The arithmetic is not here. [`avl_report::aggregate::flake_summary`] owns the buckets, the Wilson intervals, the
//! exclusion of unmeasurable trials and the honesty guard that refuses to publish a rate over a biased sample; this
//! module runs trials and hands them over. Two implementations of "what is a flake" would eventually disagree.
//!
//! - [`plan`]: the trial plan, the reset between trials, and the fold's inputs and text;
//! - [`command`]: the per-worker trial chains and the reply.

pub(crate) mod command;
pub(crate) mod plan;

use avl_wire::report::ResetPolicy;

use crate::daemon::RunArgs;

pub(crate) use command::command_flake;
pub(crate) use plan::{
    FLAKE_ORDER_PROBE_NOTE, TrialAssignment, TrialOutcome, plan_flake_trials, render_flake_text, reset_steps, summarize_trials,
};

/// What the caller may ask for between trials.
///
/// The spellings are about the IDE and the daemon, because that is what the reader of a `--reset` flag thinks
/// about; the aggregate records its own older vocabulary (`warm`, `fresh_ide`, `fresh_worker`) - see [`Reset::policy`].
#[derive(clap::ValueEnum, Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum Reset {
    /// Nothing between trials.
    None,
    /// Relaunches the IDE before every trial.
    #[default]
    FreshIde,
    /// Restarts the daemon and discards the guest's shared test home before every trial.
    Daemon,
}

impl Reset {
    /// What the aggregate calls the treatment. `daemon` is `fresh_worker` rather than a name of its own: from the
    /// measurement's point of view a worker whose daemon, IDE and shared test home have all been discarded is the
    /// freshest state this harness can produce, and a fourth policy name would only be two words for one treatment.
    pub(crate) const fn policy(self) -> ResetPolicy {
        match self {
            Self::None => ResetPolicy::Warm,
            Self::FreshIde => ResetPolicy::FreshIde,
            Self::Daemon => ResetPolicy::FreshWorker,
        }
    }

    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::None => "none",
            Self::FreshIde => "fresh-ide",
            Self::Daemon => "daemon",
        }
    }
}

/// `fresh-ide`, not `none`.
///
/// What is known, stated no wider than the observation: two *standalone* single-class iterations chained on one warm
/// daemon on `air-linux-1` - the second a different class from the first - failed the second class's `@BeforeAll`
/// with `Before all[timeout=30s]` and no test running, twice, and `--fresh-ide` cleared it. A later full `--lane ui`
/// on the same worker ran 23 of 23 green with the same suspected class mid-lane, so it did **not** reproduce in-lane.
/// The carrier is not characterised, and the default does not depend on characterising it: with an uncharacterised
/// carrier the conservative treatment is the one that does not attribute a defect to whichever trial happens to
/// follow it. An IDE launch is 10-12 s against a ~207 s lane, so the honest default is also the cheap one.
///
/// `none` stays reachable for the deliberate experiment, and `daemon` (~188 s) is the fallback for the other confound
/// this cannot reach: the lane IDE contexts under `$AIR_VM_DATA/ide`, shared across runs on a warm worker.
pub(crate) const DEFAULT_FLAKE_RESET: Reset = Reset::FreshIde;

/// Every `--reset` spelling, in the order the usage and the refusals list them.
pub(crate) fn flake_reset_names() -> Vec<&'static str> {
    <Reset as clap::ValueEnum>::value_variants()
        .iter()
        .map(|reset| reset.as_str())
        .collect()
}

/// `flake --lane LANE --trials K [--workers N] [--reset POLICY]` and every `run` option.
#[derive(clap::Args, Clone, Debug, PartialEq, Eq)]
pub(crate) struct FlakeArgs {
    /// How many trials to run.
    #[arg(long, value_name = "K", value_parser = clap::value_parser!(u16).range(1..=100))]
    pub trials: u16,
    /// How many workers to spread whole trials over; never more than the trials. One by default, because N workers
    /// is N IDEs contending for the host - a different execution context - and the one-worker baseline has to exist
    /// before any wider verdict is trusted.
    #[arg(
        long,
        value_name = "N",
        default_value_t = 1,
        value_parser = clap::value_parser!(u8).range(1..=99)
    )]
    pub workers: u8,
    /// What to reset before every trial.
    #[arg(long, value_enum, value_name = "POLICY", default_value_t = DEFAULT_FLAKE_RESET)]
    pub reset: Reset,
    #[command(flatten)]
    pub run: RunArgs,
}

impl FlakeArgs {
    /// How many workers the trials are spread over: `--workers`, never more than the trials. More workers than
    /// trials would leave a chain with nothing to run while still holding a worker nobody else can lease; the ceiling
    /// is silent because the caller asked for a trial count, not a worker count.
    pub(crate) fn worker_count(&self) -> u8 {
        self.workers.min(u8::try_from(self.trials).unwrap_or(u8::MAX))
    }

    /// `lane` for a `--lane` chain, which repeats a whole lane, and `selector` for one that repeats a selection
    /// standalone. See [`FLAKE_ORDER_PROBE_NOTE`].
    pub(crate) const fn selection_shape(&self) -> &'static str {
        if self.run.lane.is_some() { "lane" } else { "selector" }
    }

    /// The arguments as one command line, the way the run journal names a run.
    pub(crate) fn argv(&self) -> Vec<String> {
        let mut argv = vec![
            "--trials".to_owned(),
            self.trials.to_string(),
            "--workers".to_owned(),
            self.workers.to_string(),
            "--reset".to_owned(),
            self.reset.as_str().to_owned(),
        ];
        argv.extend(self.run.argv());
        argv
    }
}
