//! The memory of the IDE at the gate: the sample that a run writes into [`MEMORY_FILE`], and its metrics.
//!
//! The sample holds the IDE process, every descendant of it, and every process whose responsible process is the IDE.
//! The IDE starts as its own responsible process, so a WebKit XPC process of one of its web views names its pid,
//! although the parent of that process is launchd. A process of another IDE names another responsible process, so the
//! sample holds no process of it. The record puts each process of the sample into one [`Group`]. A memory metric is
//! the physical footprint of a group in MB of 2^20 bytes, as Activity Monitor shows it.

use std::collections::{BTreeMap, BTreeSet};

use anyhow::{Context, bail};
use avl_host_sys::proc::{self, HostProcess};
use serde::{Deserialize, Serialize};

use super::trace::tenth;

#[cfg(test)]
mod tests;

/// The memory sample of a run, in the run directory.
pub(crate) const MEMORY_FILE: &str = "memory.json";

/// The physical footprint of the IDE process.
pub(crate) const MEMORY_IDE: &str = "memory.ide";
/// The physical footprint of the JCEF processes: `cef_server` and its helpers.
pub(crate) const MEMORY_JCEF: &str = "memory.jcef";
/// The physical footprint of the WebKit processes that work for the IDE.
pub(crate) const MEMORY_WEBKIT: &str = "memory.webkit";
/// The physical footprint of the other processes of the sample.
pub(crate) const MEMORY_OTHER: &str = "memory.other";
/// The physical footprint of every process of the sample.
pub(crate) const MEMORY_ALL: &str = "memory.all";
/// The resident size of the IDE process.
pub(crate) const MEMORY_IDE_RESIDENT: &str = "memory.ide.resident";
/// The resident size of every process of the sample. A shared page counts once per process.
pub(crate) const MEMORY_ALL_RESIDENT: &str = "memory.all.resident";
/// The processes of each group except the IDE.
pub(crate) const PROCESSES_JCEF: &str = "processes.jcef";
pub(crate) const PROCESSES_WEBKIT: &str = "processes.webkit";
pub(crate) const PROCESSES_OTHER: &str = "processes.other";

/// The memory metrics in the order of the digest.
pub(crate) const MEMORY_METRICS: [&str; 7] = [
    MEMORY_IDE,
    MEMORY_JCEF,
    MEMORY_WEBKIT,
    MEMORY_OTHER,
    MEMORY_ALL,
    MEMORY_IDE_RESIDENT,
    MEMORY_ALL_RESIDENT,
];

/// The process counts in the order of the digest.
pub(crate) const PROCESS_METRICS: [&str; 3] = [PROCESSES_JCEF, PROCESSES_WEBKIT, PROCESSES_OTHER];

/// The start of the name of a JCEF process: `cef_server`, and `cef_server Helper` with its kinds.
const JCEF_PREFIX: &str = "cef_server";
/// The start of the name of a WebKit XPC process: `com.apple.WebKit.WebContent`, `.GPU` and `.Networking`.
const WEBKIT_PREFIX: &str = "com.apple.WebKit.";

/// The memory of the IDE and of the processes that work for it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Sample {
    /// The pid of the IDE.
    pub(crate) pid: i32,
    /// The IDE first, then the other processes by pid.
    pub(crate) processes: Vec<SampledProcess>,
}

/// One process of a sample.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SampledProcess {
    pub(crate) pid: i32,
    pub(crate) parent_pid: i32,
    /// The responsible process. A process that the host cannot name it for has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) responsible_pid: Option<i32>,
    pub(crate) name: String,
    /// The path of the executable. A process that the host cannot name it for has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) path: Option<String>,
    /// The physical footprint, `ri_phys_footprint`.
    pub(crate) footprint_bytes: u64,
    /// The resident size, `ri_resident_size`.
    pub(crate) resident_bytes: u64,
}

/// The group of a process of a sample.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub(crate) enum Group {
    /// The IDE process.
    Ide,
    /// A process whose name starts with `cef_server`, or a descendant of one.
    Jcef,
    /// A process whose name starts with `com.apple.WebKit.`.
    WebKit,
    /// Every other process.
    Other,
}

/// Takes the sample of the IDE `pid` on this host. A process that ends while the sample reads it is left out. A table
/// without the IDE, or an IDE whose memory the host does not answer, is an error.
pub(crate) fn take(pid: u32) -> anyhow::Result<Sample> {
    let pid = i32::try_from(pid).with_context(|| format!("the pid {pid} is out of range"))?;
    let table = proc::host_processes().context("cannot read the process table")?;
    let mut processes = Vec::new();
    for process in members(&table, pid) {
        let memory = match proc::process_memory(process.pid) {
            Ok(memory) => memory,
            Err(error) if process.pid == pid => return Err(error).with_context(|| format!("cannot read the memory of the IDE {pid}")),
            Err(_) => continue,
        };
        processes.push(SampledProcess {
            pid: process.pid,
            parent_pid: process.parent_pid,
            responsible_pid: process.responsible_pid,
            name: process.name.clone(),
            path: proc::process_path(process.pid).ok(),
            footprint_bytes: memory.footprint_bytes,
            resident_bytes: memory.resident_bytes,
        });
    }
    if processes.first().is_none_or(|first| first.pid != pid) {
        bail!("the process table has no process {pid}");
    }
    Ok(Sample { pid, processes })
}

/// The processes of `table` that a sample of the IDE `pid` holds: the IDE first, then by pid its descendants and the
/// processes whose responsible process is the IDE.
pub(crate) fn members(table: &[HostProcess], pid: i32) -> Vec<&HostProcess> {
    let mut children: BTreeMap<i32, Vec<&HostProcess>> = BTreeMap::new();
    for process in table {
        children.entry(process.parent_pid).or_default().push(process);
    }
    let mut chosen = BTreeSet::new();
    let mut pending = vec![pid];
    while let Some(parent) = pending.pop() {
        for child in children.get(&parent).into_iter().flatten() {
            if child.pid != pid && chosen.insert(child.pid) {
                pending.push(child.pid);
            }
        }
    }
    chosen.extend(
        table
            .iter()
            .filter(|process| process.pid != pid && process.responsible_pid == Some(pid))
            .map(|process| process.pid),
    );
    let ide = table.iter().filter(|process| process.pid == pid);
    let others = table.iter().filter(|process| chosen.contains(&process.pid));
    let mut others: Vec<&HostProcess> = others.collect();
    others.sort_by_key(|process| process.pid);
    ide.chain(others).collect()
}

/// Reads the text of [`MEMORY_FILE`]. A sample without the process of the IDE is an error.
pub(crate) fn parse(text: &str) -> anyhow::Result<Sample> {
    let sample: Sample = serde_json::from_str(text).context("not a memory sample")?;
    if !sample.processes.iter().any(|process| process.pid == sample.pid) {
        bail!("the sample has no process of the IDE {}", sample.pid);
    }
    Ok(sample)
}

impl Sample {
    /// The group of `process`, see [`Group`]. A JCEF descendant is found through the parents in the sample.
    pub(crate) fn group(&self, process: &SampledProcess) -> Group {
        if process.pid == self.pid {
            return Group::Ide;
        }
        if process.name.starts_with(WEBKIT_PREFIX) {
            return Group::WebKit;
        }
        let by_pid: BTreeMap<i32, &SampledProcess> = self.processes.iter().map(|process| (process.pid, process)).collect();
        let mut current = Some(process);
        let mut seen = BTreeSet::new();
        while let Some(at) = current.filter(|at| at.pid != self.pid && seen.insert(at.pid)) {
            if at.name.starts_with(JCEF_PREFIX) {
                return Group::Jcef;
            }
            current = by_pid.get(&at.parent_pid).copied();
        }
        Group::Other
    }

    /// The responsible process of the IDE, when it is not the IDE itself. Then no WebKit process of the IDE names it,
    /// so the sample lacks them.
    pub(crate) fn foreign_responsible(&self) -> Option<i32> {
        let ide = self.processes.iter().find(|process| process.pid == self.pid)?;
        ide.responsible_pid.filter(|&responsible| responsible != self.pid)
    }

    /// The metrics of the sample: [`MEMORY_METRICS`] in MB and [`PROCESS_METRICS`]. A group without a process has 0.
    /// An IDE that is not its own responsible process gives no WebKit metric, see [`Self::foreign_responsible`].
    pub(crate) fn metrics(&self) -> BTreeMap<String, f64> {
        let mut footprint: BTreeMap<Group, u64> = BTreeMap::new();
        let mut count: BTreeMap<Group, u32> = BTreeMap::new();
        let mut resident = 0;
        let mut ide_resident = 0;
        for process in &self.processes {
            let group = self.group(process);
            *footprint.entry(group).or_default() += process.footprint_bytes;
            *count.entry(group).or_default() += 1;
            resident += process.resident_bytes;
            if group == Group::Ide {
                ide_resident += process.resident_bytes;
            }
        }
        let bytes = |group| footprint.get(&group).copied().unwrap_or_default();
        let processes = |group| f64::from(count.get(&group).copied().unwrap_or_default());
        let mut metrics = BTreeMap::from([
            (MEMORY_IDE.to_owned(), megabytes(bytes(Group::Ide))),
            (MEMORY_JCEF.to_owned(), megabytes(bytes(Group::Jcef))),
            (MEMORY_WEBKIT.to_owned(), megabytes(bytes(Group::WebKit))),
            (MEMORY_OTHER.to_owned(), megabytes(bytes(Group::Other))),
            (MEMORY_ALL.to_owned(), megabytes(footprint.values().sum())),
            (MEMORY_IDE_RESIDENT.to_owned(), megabytes(ide_resident)),
            (MEMORY_ALL_RESIDENT.to_owned(), megabytes(resident)),
            (PROCESSES_JCEF.to_owned(), processes(Group::Jcef)),
            (PROCESSES_WEBKIT.to_owned(), processes(Group::WebKit)),
            (PROCESSES_OTHER.to_owned(), processes(Group::Other)),
        ]);
        if self.foreign_responsible().is_some() {
            metrics.remove(MEMORY_WEBKIT);
            metrics.remove(PROCESSES_WEBKIT);
        }
        metrics
    }
}

/// MB of 2^20 bytes, to a tenth.
fn megabytes(bytes: u64) -> f64 {
    #[expect(clippy::cast_precision_loss, reason = "a footprint stays far below 2^53 bytes")]
    let bytes = bytes as f64;
    tenth(bytes / f64::from(1_u32 << 20))
}
