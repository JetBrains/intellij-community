//! The reader of `cpu.collapsed`, the collapsed stacks that async-profiler writes with the `threads` option.
//!
//! A line is `[<thread> tid=<id>];<frame>;...;<frame> <samples>`. The reader keeps the stacks of the EDT, whose
//! thread name starts with `AWT-EventQueue`. It charges the samples of a stack to its deepest Java frame, a frame with
//! a `/` and no space, such as `java/lang/ClassLoader.defineClass1`. A native leaf such as `__psynch_mutexwait` names no EDT work. A
//! stack without a Java frame keeps its leaf.

use std::collections::BTreeMap;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};

const EDT_THREAD_PREFIX: &str = "[AWT-EventQueue";

/// The samples of one frame.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct FrameSamples {
    pub(crate) frame: String,
    pub(crate) samples: u64,
}

/// The EDT samples of one profile.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct EdtProfile {
    /// The samples of every EDT stack.
    pub(crate) samples: u64,
    /// The samples of each deepest Java frame of an EDT stack.
    pub(crate) self_samples: BTreeMap<String, u64>,
    /// The samples of every stack, of every thread.
    pub(crate) all_samples: u64,
}

impl EdtProfile {
    /// Adds the samples of another profile.
    pub(crate) fn merge(&mut self, other: &Self) {
        self.samples += other.samples;
        self.all_samples += other.all_samples;
        for (frame, samples) in &other.self_samples {
            *self.self_samples.entry(frame.clone()).or_default() += samples;
        }
    }

    /// The `limit` frames with the most samples, the most first, then by name.
    pub(crate) fn top(&self, limit: usize) -> Vec<FrameSamples> {
        let mut frames: Vec<FrameSamples> = self
            .self_samples
            .iter()
            .map(|(frame, samples)| FrameSamples {
                frame: frame.clone(),
                samples: *samples,
            })
            .collect();
        frames.sort_by(|left, right| right.samples.cmp(&left.samples).then_with(|| left.frame.cmp(&right.frame)));
        frames.truncate(limit);
        frames
    }
}

/// Parses a profile. A line without a sample count is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<EdtProfile> {
    let mut profile = EdtProfile::default();
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        let (stack, samples) = parse_line(line).with_context(|| format!("line {}", index + 1))?;
        profile.all_samples += samples;
        let mut frames = stack.split(';');
        if !frames.next().is_some_and(|thread| thread.starts_with(EDT_THREAD_PREFIX)) {
            continue;
        }
        profile.samples += samples;
        let frames: Vec<&str> = frames.collect();
        let owner = frames.iter().rev().find(|frame| is_java_frame(frame)).or_else(|| frames.last());
        if let Some(owner) = owner {
            *profile.self_samples.entry((*owner).to_owned()).or_default() += samples;
        }
    }
    Ok(profile)
}

/// A Java frame has a `/` and no space. A stub such as `I2C/C2I adapters(0xbb)` has a space.
fn is_java_frame(frame: &str) -> bool {
    frame.contains('/') && !frame.contains(' ')
}

fn parse_line(line: &str) -> anyhow::Result<(&str, u64)> {
    let Some((stack, count)) = line.rsplit_once(' ') else {
        bail!("no sample count at the end");
    };
    let samples = count
        .parse::<u64>()
        .with_context(|| format!("the sample count `{count}` is not a number"))?;
    Ok((stack, samples))
}

#[cfg(test)]
mod tests;
