//! What earlier runs on this machine measured, so a run can say how long its work usually takes.
//!
//! The one source is the run journals ([`crate::journal`]). A journal holds every phase change and every daemon
//! record of its run, with the host's time on each line, so it gives three measurements:
//!
//! - a phase's elapsed time, from its `finished` record;
//! - a class's wall time in its iteration, from the end of the class before it (or the iteration's start) to its
//!   last `testFinished`. This includes the fixture time no test case reports, such as an IDE relaunch in
//!   `@BeforeAll`, which is the time a person waits for;
//! - the whole run, from `runStarted` to `runFinished`, for a run that ended with a verdict.
//!
//! A typical time is the median of the newest [`MAX_SAMPLES`] measurements, and it needs at least
//! [`MIN_SAMPLES`]: one measurement shows no spread. History is evidence and never a verdict: a journal that
//! cannot be read is skipped, and a run with no history shows no typical times.

use std::collections::{BTreeMap, HashMap};
use std::fs::{self, File};
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use avl_trace_tools::discover::VM_RUNS_DIR;
use avl_wire::progress::{Expected, Kind, Phase, PhaseChange, PhaseState, PhaseTypical, RunFinished, Typical};
use jiff::Timestamp;
use serde::Deserialize;
use serde_json::value::RawValue;

use crate::clock;
use crate::clock::millis;
use crate::journal::FILE_NAME;
use crate::refusal::Exit;

#[cfg(test)]
mod tests;

/// How many journals one read opens, newest first.
const MAX_JOURNALS: usize = 20;
/// How many of the newest measurements a typical time is the median of.
pub const MAX_SAMPLES: usize = 7;
/// The fewest measurements a typical time is given for.
pub const MIN_SAMPLES: usize = 2;
/// Bounds one journal line; a longer line ends the read of that journal.
const MAX_LINE_BYTES: u64 = 4 << 20;

/// What the newest journals under `<runtime_root>/runs` measured, for a run of `command` with `args`. The journal
/// of the run that reads it has no finished phase yet, so it adds nothing.
pub fn read(runtime_root: &Path, command: &str, args: &[String]) -> Expected {
    let mut samples = Samples::default();
    for path in newest_journals(&runtime_root.join(VM_RUNS_DIR)) {
        samples.merge(read_journal(&path, command, args));
    }
    samples.expected()
}

/// The journals under `runs_root`, newest first, at most [`MAX_JOURNALS`].
fn newest_journals(runs_root: &Path) -> Vec<PathBuf> {
    let Ok(entries) = fs::read_dir(runs_root) else {
        return Vec::new();
    };
    let mut journals: Vec<(PathBuf, SystemTime)> = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
        .filter_map(|entry| {
            let path = entry.path().join(FILE_NAME);
            let metadata = fs::metadata(&path).ok().filter(fs::Metadata::is_file)?;
            Some((path, metadata.modified().ok()?))
        })
        .collect();
    journals.sort_by_key(|(_, modified)| std::cmp::Reverse(*modified));
    journals.into_iter().take(MAX_JOURNALS).map(|(path, _)| path).collect()
}

/// The part of a journal record that history reads.
#[derive(Deserialize)]
struct Line {
    event: String,
    at: String,
    #[serde(default)]
    worker: Option<String>,
    #[serde(default)]
    data: Option<Box<RawValue>>,
}

/// The part of a `runStarted` record history reads.
#[derive(Deserialize)]
struct Started {
    #[serde(default)]
    command: String,
    #[serde(default)]
    args: Vec<String>,
}

/// The part of a forwarded daemon record history reads.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DaemonRecord {
    event: String,
    #[serde(default)]
    class_name: Option<String>,
}

/// The class spans of one daemon iteration of one worker, as its records arrive.
struct IterationSpans {
    boundary: Timestamp,
    order: Vec<String>,
    last: HashMap<String, Timestamp>,
}

impl IterationSpans {
    fn finish(self, samples: &mut Samples) {
        let mut previous = self.boundary;
        for class_name in self.order {
            let end = self.last[&class_name];
            if end > previous {
                samples.add_class(class_name, clock::elapsed(previous, end));
            }
            previous = end;
        }
    }
}

fn decode<'a, T: Deserialize<'a>>(data: Option<&'a RawValue>) -> Option<T> {
    serde_json::from_str(data?.get()).ok()
}

fn read_journal(path: &Path, command: &str, args: &[String]) -> Samples {
    let mut samples = Samples::default();
    let Ok(file) = File::open(path) else {
        return samples;
    };
    let mut reader = BufReader::new(file);
    let mut run_started: Option<Timestamp> = None;
    let mut same_command = false;
    // The started detail of each phase in flight, by worker and phase, for the phases whose key is their detail.
    let mut started: HashMap<(Option<String>, Phase), Option<String>> = HashMap::new();
    let mut iterations: BTreeMap<Option<String>, IterationSpans> = BTreeMap::new();
    let mut buffer = Vec::new();
    loop {
        buffer.clear();
        match (&mut reader).take(MAX_LINE_BYTES + 1).read_until(b'\n', &mut buffer) {
            Ok(0) | Err(_) => break,
            Ok(_) if buffer.len() as u64 > MAX_LINE_BYTES => break,
            Ok(_) => {}
        }
        let Ok(record) = serde_json::from_slice::<Line>(&buffer) else {
            continue;
        };
        let Ok(instant) = record.at.parse::<Timestamp>() else {
            continue;
        };
        let worker = record.worker;
        match record.event.as_str() {
            event if event == Kind::RunStarted.as_str() => {
                if let Some(data) = decode::<Started>(record.data.as_deref()) {
                    run_started = Some(instant);
                    same_command = data.command == command && data.args == args;
                }
            }
            event if event == Kind::Phase.as_str() => {
                let Some(change) = decode::<PhaseChange>(record.data.as_deref()) else {
                    continue;
                };
                let slot = (worker, change.phase);
                match change.state {
                    PhaseState::Started => {
                        started.insert(slot, change.detail);
                    }
                    PhaseState::Finished => {
                        let detail = started.remove(&slot).flatten();
                        samples.add_phase(
                            change.phase,
                            phase_key(change.phase, detail),
                            Duration::from_millis(change.elapsed_ms),
                        );
                    }
                    PhaseState::Failed => {
                        started.remove(&slot);
                    }
                    PhaseState::Progress => {}
                }
            }
            event if event == Kind::Run.as_str() => {
                let Some(data) = decode::<DaemonRecord>(record.data.as_deref()) else {
                    continue;
                };
                match data.event.as_str() {
                    "runStarted" => {
                        if let Some(spans) = iterations.remove(&worker) {
                            spans.finish(&mut samples);
                        }
                        iterations.insert(
                            worker,
                            IterationSpans {
                                boundary: instant,
                                order: Vec::new(),
                                last: HashMap::new(),
                            },
                        );
                    }
                    "testFinished" | "testSkipped" => {
                        let (Some(spans), Some(class_name)) =
                            (iterations.get_mut(&worker), data.class_name.filter(|name| !name.is_empty()))
                        else {
                            continue;
                        };
                        if !spans.last.contains_key(&class_name) {
                            spans.order.push(class_name.clone());
                        }
                        spans.last.insert(class_name, instant);
                    }
                    "summary" | "runFailed" => {
                        if let Some(spans) = iterations.remove(&worker) {
                            spans.finish(&mut samples);
                        }
                    }
                    _ => {}
                }
            }
            event if event == Kind::RunFinished.as_str() => {
                let Some(finished) = decode::<RunFinished>(record.data.as_deref()) else {
                    continue;
                };
                // A verdict, green or red, is a run that did its work. Any other exit stopped early, and its
                // length says nothing about how long the work takes.
                let verdict = finished.exit_code == 0 || finished.exit_code == i32::from(Exit::TESTS_FAILED);
                if let Some(run_started) = run_started.filter(|_| same_command && verdict) {
                    samples.run.push(clock::elapsed(run_started, instant));
                }
            }
            _ => {}
        }
    }
    // A journal that ends inside an iteration was a run that stopped. Its last class did not finish, and the
    // classes before it did.
    for spans in iterations.into_values() {
        spans.finish(&mut samples);
    }
    samples
}

/// The key a phase's typical time is kept under: its started detail for the iteration, whose cost depends on what
/// it runs, and nothing for every other phase.
fn phase_key(phase: Phase, detail: Option<String>) -> Option<String> {
    if phase == Phase::Iteration {
        detail.filter(|detail| !detail.is_empty())
    } else {
        None
    }
}

/// A phase and the key its typical time is kept under.
type PhaseSlot = (Phase, Option<String>);

/// The measurements of the journals read so far, newest journal first.
#[derive(Default)]
struct Samples {
    /// In the order each slot was first measured.
    phases: Vec<(PhaseSlot, Vec<Duration>)>,
    classes: BTreeMap<String, Vec<Duration>>,
    run: Vec<Duration>,
}

impl Samples {
    fn add_phase(&mut self, phase: Phase, key: Option<String>, elapsed: Duration) {
        let slot = (phase, key);
        match self.phases.iter_mut().find(|(known, _)| *known == slot) {
            Some((_, measured)) => measured.push(elapsed),
            None => self.phases.push((slot, vec![elapsed])),
        }
    }

    fn add_class(&mut self, class_name: String, elapsed: Duration) {
        self.classes.entry(class_name).or_default().push(elapsed);
    }

    /// Adds the measurements of one journal. Journals are read newest first, so the added ones are older.
    fn merge(&mut self, journal: Self) {
        for ((phase, key), measured) in journal.phases {
            for elapsed in measured {
                self.add_phase(phase, key.clone(), elapsed);
            }
        }
        for (class_name, measured) in journal.classes {
            self.classes.entry(class_name).or_default().extend(measured);
        }
        self.run.extend(journal.run);
    }

    fn expected(&self) -> Expected {
        Expected {
            run: typical_of(&self.run),
            phases: self
                .phases
                .iter()
                .filter_map(|((phase, key), measured)| {
                    Some(PhaseTypical {
                        phase: *phase,
                        key: key.clone(),
                        typical: typical_of(measured)?,
                    })
                })
                .collect(),
            classes: self
                .classes
                .iter()
                .filter_map(|(class_name, measured)| Some((class_name.clone(), typical_of(measured)?)))
                .collect(),
        }
    }
}

/// The median of the newest [`MAX_SAMPLES`] measurements, which come newest first. The median of an even count is
/// the lower middle one, so a typical time is always a time that was measured.
fn typical_of(measured: &[Duration]) -> Option<Typical> {
    if measured.len() < MIN_SAMPLES {
        return None;
    }
    let mut newest = measured[..measured.len().min(MAX_SAMPLES)].to_vec();
    newest.sort();
    let median = newest[(newest.len() - 1) / 2];
    Some(Typical {
        ms: millis(median),
        samples: u32::try_from(newest.len()).unwrap_or(u32::MAX),
    })
}
