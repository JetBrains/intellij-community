//! The reader of the collapsed stacks that async-profiler writes with the `threads` option: [`GATE_PROFILE`], which
//! the run writes when it passes its gate, else [`WHOLE_PROFILE`], which the JVM writes at its exit.
//!
//! A line is `[<thread> tid=<id>];<frame>;...;<frame> <samples>`. The reader keeps the stacks of the EDT, whose
//! thread name starts with `AWT-EventQueue`. It charges the samples of a stack to its deepest Java frame, a frame with
//! a `/` and no space, such as `java/lang/ClassLoader.defineClass1`. A native leaf such as `__psynch_mutexwait` names no EDT work. A
//! stack without a Java frame keeps its leaf.
//!
//! The reader also reads the class-loading triggers of every thread, see [`Triggers`]. It leaves out every stack of
//! the profiler's own symbol parsing, see [`is_profiler_hook`], and counts it apart in [`ExcludedSamples`].

use std::collections::BTreeMap;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};

const EDT_THREAD_PREFIX: &str = "[AWT-EventQueue";

/// The profile of the start-up to the gate of the run, which `jcmd` makes async-profiler write when the gate passes.
pub(crate) const GATE_PROFILE: &str = "cpu-gate.collapsed";

/// The profile of the whole run, with the hold and the quit, which async-profiler writes at the exit of the JVM when
/// it still runs.
pub(crate) const WHOLE_PROFILE: &str = "cpu.collapsed";

/// The name of the frame of async-profiler that runs on each `dlopen` of the JVM, before the parameters of a C++
/// frame. It parses the symbols of the new library, about half a second on the EDT of a profiled start, which no run
/// without the profiler spends. Real profiles name it `Profiler::dlopen_hook`.
const PROFILER_HOOK_FRAME: &str = "dlopen_hook";

/// The samples of the stacks of the profiler's own symbol parsing, which a profile leaves out.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ExcludedSamples {
    /// The samples of every thread.
    pub(crate) samples: u64,
    /// The samples of the EDT among them.
    pub(crate) edt_samples: u64,
}

impl ExcludedSamples {
    const fn merge(&mut self, other: Self) {
        self.samples += other.samples;
        self.edt_samples += other.edt_samples;
    }
}

/// The event that async-profiler samples.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize, clap::ValueEnum)]
#[serde(rename_all = "camelCase")]
pub(crate) enum ProfileEvent {
    /// The CPU time of each thread.
    #[default]
    Cpu,
    /// The wall clock, which samples a thread that waits too.
    Wall,
}

impl ProfileEvent {
    /// The value of the `event` option of async-profiler, and the word of the digest.
    pub(crate) const fn option(self) -> &'static str {
        match self {
            Self::Cpu => "cpu",
            Self::Wall => "wall",
        }
    }
}

/// The span of a run that its profile covers.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum ProfileScope {
    /// The start-up to the gate: [`GATE_PROFILE`].
    Gate,
    /// The whole run: [`WHOLE_PROFILE`].
    WholeRun,
}

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
    /// The samples of the profiler's own symbol parsing, which no other count holds.
    pub(crate) excluded: ExcludedSamples,
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
        self.excluded.merge(other.excluded);
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
        let mut frames = stack.split(';');
        let is_edt = frames.next().is_some_and(|thread| thread.starts_with(EDT_THREAD_PREFIX));
        let frames: Vec<&str> = frames.collect();
        if frames.iter().any(|frame| is_profiler_hook(frame)) {
            profile.excluded.samples += samples;
            if is_edt {
                profile.excluded.edt_samples += samples;
            }
            continue;
        }
        profile.all_samples += samples;
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

/// Tells whether a frame is the dlopen hook of the profiler, see [`PROFILER_HOOK_FRAME`], with or without a class and
/// the parameters: `Profiler::dlopen_hook` or `Profiler::dlopen_hook(char const*, int)`.
fn is_profiler_hook(frame: &str) -> bool {
    frame
        .split('(')
        .next()
        .and_then(|name| name.rsplit("::").next())
        .is_some_and(|name| name == PROFILER_HOOK_FRAME)
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
