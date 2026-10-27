//! The durations of the steps of a run, for a person. The agent measures its own steps, such as `slot` and `command`,
//! and the client adds `pack`, `upload`, `fetch` and `total`.

use std::time::Duration;

use yonder_link::proto::Phase;

/// The steps of a run that a list shows: each step of 0.1 s or more.
const SHORT_FROM_MS: u64 = 100;

/// The line of the steps of a run on the stderr: `yonder: phases: pack 3.1s, upload 2.0s, … total 10m13s`.
pub fn phases_line(phases: &[Phase]) -> String {
    format!("yonder: phases: {}", join(phases.iter()))
}

/// The steps of a run in a short form for a list: `slot 9m02s, checkout 41.0s, command 25.1s`, without the steps under
/// 0.1 s. Empty when no step is that long.
pub fn phases_short(phases: &[Phase]) -> String {
    join(phases.iter().filter(|phase| phase.ms >= SHORT_FROM_MS))
}

/// A duration for a person, rounded: `3.1s` under a minute, `9m02s` under an hour, then `1h05m09s`.
pub fn duration_text(ms: u64) -> String {
    let tenths = (ms + 50) / 100;
    if tenths < 600 {
        return format!("{}.{}s", tenths / 10, tenths % 10);
    }
    let seconds = (ms + 500) / 1000;
    match seconds {
        0..3600 => format!("{}m{:02}s", seconds / 60, seconds % 60),
        _ => format!("{}h{:02}m{:02}s", seconds / 3600, seconds / 60 % 60, seconds % 60),
    }
}

/// One step and its duration.
pub(crate) fn phase(name: &str, took: Duration) -> Phase {
    Phase {
        name: name.to_owned(),
        ms: u64::try_from(took.as_millis()).unwrap_or(u64::MAX),
    }
}

fn join<'a>(phases: impl Iterator<Item = &'a Phase>) -> String {
    phases
        .map(|phase| format!("{} {}", phase.name, duration_text(phase.ms)))
        .collect::<Vec<_>>()
        .join(", ")
}

#[cfg(test)]
mod tests;
