//! The prepare steps of `.yonder.toml`. A step runs before the command when its fingerprint differs from the one of its
//! last success in the host slot, or when a path that it creates is missing. The fingerprint covers the command of the
//! step, the paths of its inputs and their object ids at the commit of the run, so a step runs again when one of its
//! inputs changed. The fingerprints are kept per slot (`slots::Prepared`), by its lease id and the generation of its
//! files, so a new slot, or a slot whose files a give-back or a clean removed, runs each step once: the agent cannot
//! know what was done to it before.

use std::collections::BTreeMap;
use std::path::Path;

use crate::config::Step;

/// Why a step runs, or `None` when it is current.
pub(crate) fn reason(step: &Step, fingerprint: &str, prepared: &BTreeMap<String, String>, slot: &Path) -> Option<String> {
    if let Some(missing) = step.creates.iter().find(|path| !slot.join(path).exists()) {
        return Some(format!("{missing} is missing"));
    }
    match prepared.get(&key(step)) {
        None => Some("the first run in this slot".to_owned()),
        Some(recorded) if recorded != fingerprint => Some(if step.inputs.is_empty() {
            "its command changed".to_owned()
        } else {
            format!("{} changed", step.inputs.join(", "))
        }),
        Some(_) => None,
    }
}

/// The key of a step among the fingerprints of a slot: a hash of its name.
pub(crate) fn key(step: &Step) -> String {
    format!("{:016x}", fnv(step.name.as_bytes(), FNV_OFFSET))
}

/// The fingerprint of a step: its command, and each input path with its object id at the commit, `-` for a path that
/// the commit lacks.
pub(crate) fn fingerprint(step: &Step, ids: &[Option<String>]) -> String {
    let mut hash = FNV_OFFSET;
    for word in &step.run {
        hash = fnv(word.as_bytes(), hash);
        hash = fnv(&[0], hash);
    }
    for (path, id) in step.inputs.iter().zip(ids) {
        hash = fnv(&[1], hash);
        hash = fnv(path.as_bytes(), hash);
        hash = fnv(&[0], hash);
        hash = fnv(id.as_deref().unwrap_or("-").as_bytes(), hash);
    }
    format!("{hash:016x}")
}

const FNV_OFFSET: u64 = 0xcbf2_9ce4_8422_2325;

/// FNV-1a over `bytes`, from `hash`. Stable across builds and platforms.
fn fnv(bytes: &[u8], hash: u64) -> u64 {
    bytes
        .iter()
        .fold(hash, |hash, byte| (hash ^ u64::from(*byte)).wrapping_mul(0x0100_0000_01b3))
}

#[cfg(test)]
mod tests;
