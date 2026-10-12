//! The load of the host at the start of a run: the load averages and the CPU count, so a noisy session shows in
//! each run record and in the digest. A session refuses to start on a busy host.
//!
//! macOS answers `/usr/sbin/sysctl -n vm.loadavg`, Linux has `/proc/loadavg`, and Windows has no load average.

use std::time::Duration;

use avl_base::{Exit, Refusal};
use avl_host_sys::{Ctx, Runner, SpawnOptions};
use serde::{Deserialize, Serialize};

/// Bounds the `sysctl` read. It answers at once, so a read that hangs is a host to measure without its load.
const SYSCTL_TIMEOUT: Duration = Duration::from_secs(10);

/// The load averages of the host over 1, 5 and 15 minutes, and its logical CPUs.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub(crate) struct HostLoad {
    pub(crate) one: f64,
    pub(crate) five: f64,
    pub(crate) fifteen: f64,
    /// 0 when the count is unknown.
    pub(crate) cpus: u32,
}

impl HostLoad {
    /// Tells whether the 1-minute load was above the CPU count: more runnable threads than CPUs. A session refuses a
    /// saturated host unless `--max-load` sets another limit. A load with an unknown CPU count is not saturated.
    pub(crate) fn saturated(&self) -> bool {
        limit(None, self.cpus).is_some_and(|limit| self.one > limit)
    }
}

/// The limit of the 1-minute load for a session: `max_load`, else the CPU count `cpus`. An unknown CPU count, 0, gives
/// no limit.
fn limit(max_load: Option<f64>, cpus: u32) -> Option<f64> {
    max_load.or_else(|| (cpus > 0).then(|| f64::from(cpus)))
}

/// Refuses a session on a busy host and gives the limit that it checked. The limit is `max_load`, else the CPU count,
/// so without `--max-load` a saturated host is busy. A load above the limit is the refusal `host_busy`. A host without
/// a load average, or without `max_load` and a CPU count, passes unchecked, and the answer is `None`.
pub(crate) fn check_max(load: Option<&HostLoad>, max_load: Option<f64>) -> Result<Option<f64>, Refusal> {
    let Some(load) = load else {
        return Ok(None);
    };
    match limit(max_load, load.cpus) {
        Some(limit) if load.one > limit => {
            let cpus = if load.cpus > 0 {
                format!(" on {} CPUs", load.cpus)
            } else {
                String::new()
            };
            let reason = match max_load {
                Some(max_load) => format!("above --max-load {max_load}; wait for the host to settle"),
                None => "above the CPU count; wait for the host to settle, or set another limit with --max-load".to_owned(),
            };
            Err(Refusal::new(
                "host_busy",
                Exit::TEMP_FAIL,
                format!("the 1-minute load is {:.1}{cpus}, {reason}", load.one),
            ))
        }
        limit => Ok(limit),
    }
}

/// The three averages of `sysctl -n vm.loadavg`, such as `{ 1.52 1.61 1.70 }`, or of `/proc/loadavg`, such as
/// `0.52 0.61 0.70 1/123 4567`.
pub(crate) fn parse_averages(text: &str) -> Option<(f64, f64, f64)> {
    let mut numbers = text
        .split_whitespace()
        .filter(|word| *word != "{" && *word != "}")
        .map(str::parse::<f64>);
    let one = numbers.next()?.ok()?;
    let five = numbers.next()?.ok()?;
    let fifteen = numbers.next()?.ok()?;
    Some((one, five, fifteen))
}

/// The load of this host now, or `None` where the host has no load average or the read failed. A missing load is
/// no failure of the run.
pub(crate) async fn read(ctx: &Ctx, runner: &Runner) -> Option<HostLoad> {
    let text = if cfg!(target_os = "macos") {
        let argv = ["/usr/sbin/sysctl", "-n", "vm.loadavg"].map(str::to_owned);
        runner.checked(ctx, &argv, &SpawnOptions::within(SYSCTL_TIMEOUT)).await.ok()?.stdout
    } else if cfg!(target_os = "linux") {
        std::fs::read_to_string("/proc/loadavg").ok()?
    } else {
        return None;
    };
    let (one, five, fifteen) = parse_averages(&text)?;
    Some(HostLoad {
        one,
        five,
        fifteen,
        cpus: cpu_count(),
    })
}

/// The logical CPUs that this process can use, or 0 when the count is unknown.
fn cpu_count() -> u32 {
    std::thread::available_parallelism().map_or(0, |count| u32::try_from(count.get()).unwrap_or(u32::MAX))
}

#[cfg(test)]
mod tests;
