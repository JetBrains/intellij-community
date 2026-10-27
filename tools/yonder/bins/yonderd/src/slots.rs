//! The host slots that a run borrows from the pool of Branch Deck, through `bd lease host` (`spec/cli/lease.spec.md`,
//! "Host slots", in the Branch Deck repository). A remote slot holds no state of its own: the commit comes from the
//! client, and the stage carries the outputs back. So a slot is lent to one run for that run only, and the host keeps a
//! fixed set of warm slots (`branchdeck.hostSlots`, 4 by default) that runs share.
//!
//! - A supervisor lends a slot for its run (`lend`, with its own pid: the lend lasts as long as the supervisor), and
//!   polls while the slots are lent to other runs; on every way out it gives the slot back (`give_back`), which also
//!   takes a run that got none out of the queue.
//! - The keeper of `serve` runs `reclaim` (what a gone process left) and `fill` (the missing slots) per repository.
//! - The prepare steps of `.yonder.toml` that succeeded in a slot are kept per slot (`Prepared`), by its lease id and
//!   the generation of its files: a give-back that discarded changes, or a clean, can delete what a step made, and
//!   Branch Deck moves the generation before the slot is idle again, so no next run sees a stale fingerprint.

use std::collections::BTreeMap;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use serde::Deserialize;

use crate::bd;
use crate::child::Stop;
use crate::files::{private_dir, write_private};

/// The deadline of a lend: no git, a read and a write of the lend table.
const LEND: Duration = Duration::from_secs(60);

/// The deadline of a give-back and of a reclaim: they stop the processes of a run, discard its changes, and can clean
/// or return a slot.
const GIVE_BACK: Duration = Duration::from_mins(10);

/// The deadline of a fill: a new slot can need a clone and a checkout of the monorepo.
const FILL: Duration = Duration::from_mins(30);

/// The output of `bd schema` that names the support of `bd lease host`.
const LEND_OUTPUT: &str = "lease_host_lend";

/// The slot lent to a run.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Lent {
    pub(crate) n: u32,
    pub(crate) slot: PathBuf,
    pub(crate) lease_id: String,
    /// The generation of the files of the slot: one more with each give-back that discarded changes and each clean.
    pub(crate) generation: u64,
    /// When the same holder used it last, in epoch milliseconds.
    pub(crate) last_used_by_holder_ms: Option<u64>,
}

/// No slot for the run yet.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Queued {
    /// The runs before this one in the queue.
    pub(crate) ahead: usize,
    /// Every host slot of the repository.
    pub(crate) slots: usize,
    /// The runs that have a slot: the number of the slot and the run.
    pub(crate) lent: Vec<(u32, String)>,
    /// The slots in quarantine, with the commands of the processes that keep them.
    pub(crate) quarantined: Vec<(u32, Vec<String>)>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Lend {
    Lent(Lent),
    Queued(Queued),
}

/// Lends a slot of the pool of `repo` to `run`, which `pid` runs, or puts the run in the queue. A repeat answers the
/// same slot. It is never stopped: a lend that a kill cut after the table recorded it would keep the slot lent.
pub(crate) fn lend(repo: &Path, run: &str, pid: u32, holder: &str) -> Result<Lend, String> {
    let pid = pid.to_string();
    let text = bd::host(
        repo,
        &["lend", "--run", run, "--pid", &pid, "--affinity", holder],
        LEND,
        &crate::child::never,
    )?;
    parse_lend(&text).ok_or_else(|| format!("bd lease host lend answered no slot and no place: {text}"))
}

/// What a give-back did.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct GaveBack {
    /// The slot given back; `None` when the run had none, and then it left the queue.
    pub(crate) n: Option<u32>,
    /// The processes of the run that the give-back stopped.
    pub(crate) stopped: usize,
    /// The commands of the processes that keep the slot in quarantine.
    pub(crate) quarantined: Vec<String>,
}

/// Gives back the slot of `run`, or takes the run out of the queue when it has none. It is never stopped, so a
/// signal to the supervisor does not leave the slot half given back.
pub(crate) fn give_back(repo: &Path, run: &str) -> Result<GaveBack, String> {
    let text = bd::host(repo, &["give-back", "--run", run], GIVE_BACK, &crate::child::never)?;
    parse_give_back(&text).ok_or_else(|| format!("bd lease host give-back answered no outcome: {text}"))
}

/// Takes up what no process acts on in the slots of `repo`. Answers the lines of what it did.
pub(crate) fn reclaim(repo: &Path) -> Result<Vec<String>, String> {
    let text = bd::host(repo, &["reclaim"], GIVE_BACK, &crate::child::never)?;
    Ok(parse_reclaim(&text))
}

/// Leases the missing slots of `repo`. Answers the numbers of the slots it added. `stop` kills the get.
pub(crate) fn fill(repo: &Path, stop: Stop<'_>) -> Result<Vec<u32>, String> {
    let text = bd::host(repo, &["fill"], FILL, stop)?;
    Ok(parse_fill(&text))
}

/// Whether the bd of the host has `bd lease host`. A positive answer is kept for the life of the process; a negative
/// one is asked again, so a Branch Deck installed later is seen.
pub(crate) fn supported() -> bool {
    static SUPPORTED: AtomicBool = AtomicBool::new(false);
    if SUPPORTED.load(Ordering::Relaxed) {
        return true;
    }
    let supported = bd::schema().is_some_and(|text| text.contains(&format!("\"{LEND_OUTPUT}\"")));
    if supported {
        SUPPORTED.store(true, Ordering::Relaxed);
    }
    supported
}

/// The refusal text of a host whose bd lacks `bd lease host`.
pub(crate) const UNSUPPORTED: &str =
    "bd on the host lacks `bd lease host`, which lends the host slots per run: install a Branch Deck with it on the host";

fn parse_lend(text: &str) -> Option<Lend> {
    #[derive(Deserialize)]
    struct Answer {
        state: String,
        slot: Option<Slot>,
        #[serde(default)]
        ahead: usize,
        #[serde(default)]
        slots: usize,
        #[serde(default)]
        lent: Vec<LentTo>,
        #[serde(default)]
        quarantined: Vec<Quarantined>,
    }
    #[derive(Deserialize)]
    struct Slot {
        n: u32,
        path: PathBuf,
        lease_id: String,
        #[serde(default)]
        generation: u64,
        last_used_by_affinity_ms: Option<u64>,
    }
    #[derive(Deserialize)]
    struct LentTo {
        n: u32,
        run: String,
    }
    #[derive(Deserialize)]
    struct Quarantined {
        n: u32,
        #[serde(default)]
        processes: Vec<Process>,
    }
    let answer: Answer = serde_json::from_str(text.lines().last()?).ok()?;
    match (answer.state.as_str(), answer.slot) {
        ("lent", Some(slot)) if !slot.lease_id.is_empty() => Some(Lend::Lent(Lent {
            n: slot.n,
            slot: slot.path,
            lease_id: slot.lease_id,
            generation: slot.generation,
            last_used_by_holder_ms: slot.last_used_by_affinity_ms,
        })),
        ("queued", _) => Some(Lend::Queued(Queued {
            ahead: answer.ahead,
            slots: answer.slots,
            lent: answer.lent.into_iter().map(|lent| (lent.n, lent.run)).collect(),
            quarantined: answer
                .quarantined
                .into_iter()
                .map(|slot| (slot.n, slot.processes.into_iter().map(|process| process.describe()).collect()))
                .collect(),
        })),
        _ => None,
    }
}

/// A process of the JSON of `bd lease host`.
#[derive(Deserialize)]
struct Process {
    pid: u32,
    command: String,
    #[serde(default)]
    argv: String,
}

impl Process {
    fn describe(&self) -> String {
        let what = if self.argv.is_empty() { &self.command } else { &self.argv };
        format!("{} ({what})", self.pid)
    }
}

fn parse_give_back(text: &str) -> Option<GaveBack> {
    #[derive(Deserialize)]
    struct Answer {
        n: Option<u32>,
        #[serde(default)]
        stopped: Vec<Process>,
        #[serde(default)]
        quarantined: Vec<Process>,
    }
    let answer: Answer = serde_json::from_str(text.lines().last()?).ok()?;
    Some(GaveBack {
        n: answer.n,
        stopped: answer.stopped.len(),
        quarantined: answer.quarantined.iter().map(Process::describe).collect(),
    })
}

fn parse_reclaim(text: &str) -> Vec<String> {
    #[derive(Deserialize)]
    struct Answer {
        #[serde(default)]
        reclaimed: Vec<Reclaimed>,
    }
    #[derive(Deserialize)]
    struct Reclaimed {
        n: u32,
        action: String,
        error: Option<String>,
    }
    let Some(answer) = text.lines().last().and_then(|line| serde_json::from_str::<Answer>(line).ok()) else {
        return Vec::new();
    };
    answer
        .reclaimed
        .into_iter()
        .map(|done| match done.error {
            Some(error) => format!("slot {}: {} failed: {error}", done.n, done.action),
            None => format!("slot {}: {}", done.n, done.action),
        })
        .collect()
}

fn parse_fill(text: &str) -> Vec<u32> {
    #[derive(Deserialize)]
    struct Answer {
        #[serde(default)]
        filled: Vec<Filled>,
    }
    #[derive(Deserialize)]
    struct Filled {
        n: u32,
    }
    text.lines()
        .last()
        .and_then(|line| serde_json::from_str::<Answer>(line).ok())
        .map_or_else(Vec::new, |answer| answer.filled.into_iter().map(|slot| slot.n).collect())
}

/// The fingerprints of the prepare steps that succeeded in one slot, in `<slots>/<lease id>.prepared`: a `generation=`
/// line and `prepared.<key>=<fingerprint>` lines. Another lease id is another slot, and another generation means that
/// a give-back discarded changes or a clean ran since, which can have deleted what a step made: both start empty.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Prepared {
    path: PathBuf,
    generation: u64,
    pub(crate) steps: BTreeMap<String, String>,
}

impl Prepared {
    /// The fingerprints of the slot `lease_id` in the generation `generation` of its files, in the directory `dir`.
    pub(crate) fn load(dir: &Path, lease_id: &str, generation: u64) -> Self {
        let path = dir.join(format!("{lease_id}.prepared"));
        let text = std::fs::read_to_string(&path).unwrap_or_default();
        let mut recorded = None;
        let mut steps = BTreeMap::new();
        for line in text.lines() {
            if let Some(value) = line.strip_prefix("generation=") {
                recorded = value.parse().ok();
            } else if let Some((key, fingerprint)) = line.strip_prefix("prepared.").and_then(|rest| rest.split_once('=')) {
                steps.insert(key.to_owned(), fingerprint.to_owned());
            }
        }
        if recorded != Some(generation) {
            steps.clear();
        }
        Self { path, generation, steps }
    }

    pub(crate) fn save(&self) -> io::Result<()> {
        if let Some(dir) = self.path.parent() {
            private_dir(dir)?;
        }
        let mut text = format!("generation={}\n", self.generation);
        for (key, fingerprint) in &self.steps {
            text.push_str(&format!("prepared.{key}={fingerprint}\n"));
        }
        write_private(&self.path, text.as_bytes())
    }
}

#[cfg(test)]
mod tests;
