//! The reader of `cpu.collapsed`, the collapsed stacks that async-profiler writes with the `threads` option.
//!
//! A line is `[<thread> tid=<id>];<frame>;...;<frame> <samples>`. The reader keeps the stacks of the EDT, whose
//! thread name starts with `AWT-EventQueue`. It charges the samples of a stack to its deepest Java frame, a frame with
//! a `/` and no space, such as `java/lang/ClassLoader.defineClass1`. A native leaf such as `__psynch_mutexwait` names no EDT work. A
//! stack without a Java frame keeps its leaf.
//!
//! The reader also reads the class-loading triggers of every thread, see [`Triggers`].

use std::collections::BTreeMap;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};

const EDT_THREAD_PREFIX: &str = "[AWT-EventQueue";

/// The prefix of the frame that defines a class.
const DEFINE_FRAME_PREFIX: &str = "java/lang/ClassLoader.defineClass";

/// The prefixes of the loader frames: the class loaders, the reflection and the method handles. No such frame is a
/// trigger.
const LOADER_FRAME_PREFIXES: [&str; 11] = [
    "java/lang/ClassLoader",
    "java/lang/Class.forName",
    "java/lang/Class.<init>",
    "jdk/internal/loader",
    "com/intellij/util/lang",
    "com/intellij/ide/plugins/cl",
    "java/security/SecureClassLoader",
    "java/lang/invoke/",
    "java/lang/reflect/",
    "jdk/internal/reflect/",
    "sun/reflect/",
];

/// The trigger of a define stack without a frame above the define frame.
pub(crate) const ROOT_TRIGGER: &str = "<root>";

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
    /// The class-loading triggers of every thread.
    pub(crate) triggers: Triggers,
}

/// The class-loading triggers of one profile, over all threads.
///
/// A define stack holds a frame that starts with `java/lang/ClassLoader.defineClass`. The reader charges its samples
/// to the trigger: the nearest Java frame above the first define frame, towards the root, that is not a loader frame.
/// The first define frame is the define frame nearest to the root. The loader frames are the frames of the class
/// loaders, of the reflection and of the method handles. A stack without such a Java frame charges the deepest frame
/// above the define frame that is not a loader frame, such as the native thread root, or [`ROOT_TRIGGER`].
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Triggers {
    /// The samples of every define stack.
    pub(crate) define_samples: u64,
    /// The samples of each trigger.
    pub(crate) by_trigger: BTreeMap<String, u64>,
}

impl EdtProfile {
    /// Adds the samples of another profile.
    pub(crate) fn merge(&mut self, other: &Self) {
        self.samples += other.samples;
        self.all_samples += other.all_samples;
        merge_samples(&mut self.self_samples, &other.self_samples);
        self.triggers.merge(&other.triggers);
    }

    /// The `limit` frames with the most samples, the most first, then by name.
    pub(crate) fn top(&self, limit: usize) -> Vec<FrameSamples> {
        top_frames(&self.self_samples, limit)
    }

    /// The part of the samples of every thread that define classes, from 0 to 1. A profile without samples has none.
    pub(crate) fn define_share(&self) -> Option<f64> {
        #[expect(clippy::cast_precision_loss, reason = "a share needs no exact sample count")]
        (self.all_samples > 0).then(|| self.triggers.define_samples as f64 / self.all_samples as f64)
    }
}

impl Triggers {
    /// Adds the samples of another profile.
    pub(crate) fn merge(&mut self, other: &Self) {
        self.define_samples += other.define_samples;
        merge_samples(&mut self.by_trigger, &other.by_trigger);
    }

    /// The `limit` triggers with the most samples, the most first, then by name.
    pub(crate) fn top(&self, limit: usize) -> Vec<FrameSamples> {
        top_frames(&self.by_trigger, limit)
    }

    /// Charges the samples of a stack when it is a define stack. `frames` holds the frames from the root, without
    /// the thread.
    fn add(&mut self, frames: &[&str], samples: u64) {
        let Some(define) = frames.iter().position(|frame| frame.starts_with(DEFINE_FRAME_PREFIX)) else {
            return;
        };
        self.define_samples += samples;
        let callers = || frames[..define].iter().rev().filter(|frame| !is_loader_frame(frame));
        let trigger = callers()
            .find(|frame| is_java_frame(frame))
            .or_else(|| callers().next())
            .map_or(ROOT_TRIGGER, |frame| *frame);
        *self.by_trigger.entry(trigger.to_owned()).or_default() += samples;
    }
}

/// Parses a profile. A line without a sample count is an error that names the line.
pub(crate) fn parse(text: &str) -> anyhow::Result<EdtProfile> {
    let mut profile = EdtProfile::default();
    for (index, line) in text.lines().enumerate().filter(|(_, line)| !line.trim().is_empty()) {
        let (stack, samples) = parse_line(line).with_context(|| format!("line {}", index + 1))?;
        profile.all_samples += samples;
        let mut frames = stack.split(';');
        let is_edt = frames.next().is_some_and(|thread| thread.starts_with(EDT_THREAD_PREFIX));
        let frames: Vec<&str> = frames.collect();
        profile.triggers.add(&frames, samples);
        if !is_edt {
            continue;
        }
        profile.samples += samples;
        let owner = frames.iter().rev().find(|frame| is_java_frame(frame)).or_else(|| frames.last());
        if let Some(owner) = owner {
            *profile.self_samples.entry((*owner).to_owned()).or_default() += samples;
        }
    }
    Ok(profile)
}

fn merge_samples(into: &mut BTreeMap<String, u64>, from: &BTreeMap<String, u64>) {
    for (frame, samples) in from {
        *into.entry(frame.clone()).or_default() += samples;
    }
}

/// The `limit` frames of a map with the most samples, the most first, then by name.
fn top_frames(samples: &BTreeMap<String, u64>, limit: usize) -> Vec<FrameSamples> {
    let mut frames: Vec<FrameSamples> = samples
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

/// A Java frame has a `/` and no space. A stub such as `I2C/C2I adapters(0xbb)` has a space.
fn is_java_frame(frame: &str) -> bool {
    frame.contains('/') && !frame.contains(' ')
}

fn is_loader_frame(frame: &str) -> bool {
    LOADER_FRAME_PREFIXES.iter().any(|prefix| frame.starts_with(prefix))
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
