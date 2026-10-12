//! The commands, and the plan as text.

use std::fmt::Write as _;

use avl_trace_tools::discover::Summary;
use avl_trace_tools::viewer::DEFAULT_SERVE_PORT;

use super::{Command, CommandKind, Kind, PlanResult, Planner};

/// The two wrappers, from the checkout root, as the `vm-ui-tests` skill spells them. A permission rule matches the
/// literal argv prefix, which is why the path is written out on every line rather than held in a variable.
const VM_WRAPPER: &str = "./community/tools/vm.cmd";
const BT_WRAPPER: &str = "./community/tools/bt.cmd";

/// What a host run costs beyond its time.
pub(crate) const HOST_WARNING: &str = "takes your screen: the lane launches a real IDE on this machine's display, \
                                       which holds the pointer and the focus until it finishes";

/// What a run of an explicit-only lane costs beyond its time, on either side.
pub(crate) const BILLED_WARNING: &str = "explicit-only: every scenario spends a billed turn on a real account, so no \
                                         --changed, suites, shard or flake runs it";

/// The run input of the live lane's guest run, and the host command whose output it is: the developer's own Central
/// session, piped into the run so no file on the host holds it (ADR 0200).
const LIVE_LOGIN_INPUT: &str = "AIR_LIVE_CENTRAL_LOGIN";
const LIVE_LOGIN_SOURCE: &str = "central login export";

/// Whether the lane runs only when a caller names it: the lane table gives it no catalog lane.
fn explicit_only(lane: &str) -> bool {
    avl_affected::ui_lane(lane).is_ok_and(|lane| lane.explicit_only)
}

/// The guest run's input of a lane that needs one, as the pipe that feeds it and the `--test-env` that names it.
///
/// Only the live lane has one. Without it the guest run of that lane fails, naming the input.
fn guest_input(lane: &str) -> Option<(&'static str, String)> {
    (lane == "ui-live").then(|| (LIVE_LOGIN_SOURCE, format!("--test-env {LIVE_LOGIN_INPUT}=@-")))
}

/// The host passthroughs of a lane, as `bt` takes them for one class: only `--lane` applies the lane's `extra`, so a
/// class line names each valueless `--test_env` itself.
fn host_passthroughs(lane: &str) -> Vec<String> {
    let Some(spec) = avl_affected::air_area().lanes().get(lane) else {
        return Vec::new();
    };
    spec.extra
        .iter()
        .filter_map(|flag| flag.strip_prefix("--test_env="))
        .filter(|name| !name.contains('='))
        .map(|name| format!("--test-env {name}"))
        .collect()
}

/// The `--backend` a lane's VM line names, for a lane whose guest is not the default one.
///
/// A GUI-chat suite may drive the physical pointer, so the skill keeps it on the Parallels guest the user chose. The
/// line states that choice rather than leaving it to the default Linux pool, and running the line is the user's
/// choice of guest.
fn guest_backend(lane: &str) -> Option<&'static str> {
    (lane == "gui-chat").then_some("parallels")
}

impl Planner<'_> {
    /// One VM run and one host run per lane, VM first.
    ///
    /// Per lane, because a lane is the unit a person picks and the one a guest is chosen for: the skill keeps a
    /// GUI-chat suite on the guest the user chose, and a run per lane lets them run one and not the other. Each line
    /// stands alone: `vm.cmd run` without a receipt warms the analysis, takes its own lease and releases it (ADR
    /// 0157), so the planner spells no lease script.
    pub(crate) fn commands(&self) -> Vec<Command> {
        let mut commands = Vec::new();
        for lane in &self.result.lanes {
            let classes: Vec<&String> = self
                .result
                .classes
                .iter()
                .filter(|class| self.class_lanes.get(*class) == Some(lane))
                .collect();
            let covered = self.changed_classes.get(lane);
            let mut lines = Vec::new();
            if !self.changed.is_empty() && covered.is_some_and(|covered| !covered.is_empty()) {
                lines.push(self.changed_run(lane));
            }
            for class in &classes {
                if !covered.is_some_and(|covered| covered.contains(*class)) {
                    lines.push(vm_run(lane, &[class.as_str()]));
                }
            }
            let guest = match guest_backend(lane) {
                Some(_) => "the macOS Parallels guest",
                None => "a Linux VM worker",
            };
            let explicit = explicit_only(lane);
            let kind = if explicit { ", explicit-only" } else { "" };
            let passthroughs = if explicit { host_passthroughs(lane) } else { Vec::new() };
            commands.push(Command {
                kind: CommandKind::Vm,
                title: format!("the {lane} lane on {guest}{kind} ({} class(es))", classes.len()),
                lines,
                warning: explicit.then(|| BILLED_WARNING.to_owned()),
            });
            commands.push(Command {
                kind: CommandKind::Host,
                title: format!("the {lane} lane on this machine{kind} ({} class(es))", classes.len()),
                lines: classes.iter().map(|class| host_run(class, &passthroughs)).collect(),
                warning: Some(if explicit {
                    format!("{HOST_WARNING}; {BILLED_WARNING}")
                } else {
                    HOST_WARNING.to_owned()
                }),
            });
        }
        commands
    }

    /// `run --changed` over the paths that reached a suite, one flag per path, which the controller reads the same as
    /// one flag before every path. `--lane` is added only when the paths reach more than one lane: `run` without it
    /// runs every reached lane, and the planner prints one line per lane, so that a GUI-chat line can name its own
    /// backend.
    fn changed_run(&self, lane: &str) -> String {
        let mut arguments: Vec<String> = Vec::new();
        for path in &self.changed {
            arguments.push("--changed".to_owned());
            arguments.push(shell_quote(path));
        }
        if self.changed_lanes > 1 {
            arguments.push("--lane".to_owned());
            arguments.push(lane.to_owned());
        }
        vm_run(lane, &arguments.iter().map(String::as_str).collect::<Vec<_>>())
    }
}

/// One self-leased `vm.cmd run`, with the lane's guest when it is not the default one, and the lane's guest input
/// piped in when it needs one.
pub(crate) fn vm_run(lane: &str, arguments: &[&str]) -> String {
    let mut parts = vec![VM_WRAPPER];
    if let Some(backend) = guest_backend(lane) {
        parts.extend(["--backend", backend]);
    }
    parts.push("run");
    parts.extend(arguments);
    match guest_input(lane) {
        Some((source, flag)) => format!("{source} | {} {flag}", parts.join(" ")),
        None => parts.join(" "),
    }
}

/// One `bt` run of a class on this machine, with the lane's passthroughs that one class has to name itself.
fn host_run(class: &str, passthroughs: &[String]) -> String {
    let mut parts = vec![format!("{BT_WRAPPER} {class}")];
    parts.extend(passthroughs.iter().cloned());
    parts.join(" ")
}

/// Quotes a word for a POSIX shell when it needs it, so a path with a space is still one argument.
fn shell_quote(word: &str) -> String {
    let plain = !word.is_empty()
        && word
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._/@%+=:,-".contains(&byte));
    if plain {
        word.to_owned()
    } else {
        avl_base::posix_shell_quote(word)
    }
}

/// The plan as text for a person: how each input was read, what it reaches, the commands, and what it could not
/// map.
pub(crate) fn render(result: &PlanResult) -> String {
    let mut text = String::new();
    // Writing to a String cannot fail.
    macro_rules! line {
        ($($argument:tt)*) => {
            let _ = writeln!(text, $($argument)*);
        };
    }
    for reading in &result.read {
        let mut line = format!("read {} as {}", reading.given, describe_kind(reading.kind));
        if !reading.detail.is_empty() {
            line.push_str(": ");
            line.push_str(&reading.detail);
        }
        line!("{line}");
    }
    if !result.scenarios.is_empty() {
        line!("\n{} scenario(s) in {}:", result.scenarios.len(), result.lanes.join(", "));
        for scenario in &result.scenarios {
            let mut line = format!("  {:<8} {}  {}", scenario.lane, scenario.test_class, scenario.scenario);
            if let Some(flow) = &scenario.flow {
                line.push_str(&format!("  ({flow})"));
            }
            line!("{line}");
        }
    }
    let authored: Vec<&str> = result
        .classes
        .iter()
        .filter(|class| !result.scenarios.iter().any(|scenario| &scenario.test_class == *class))
        .map(String::as_str)
        .collect();
    if !authored.is_empty() {
        line!("\nclasses with no generated scenario: {}", authored.join(", "));
    }
    if !result.bundles.is_empty() {
        line!("\n{} bundle(s):", result.bundles.len());
        for bundle in &result.bundles {
            line!("{}", bundle_line(&bundle.summary, bundle.status()));
        }
    }
    if !result.existing.is_empty() {
        line!(
            "\n{} bundle(s) of these scenarios on this machine, newest first:",
            result.existing.len()
        );
        for bundle in &result.existing {
            line!("{}", bundle_line(bundle, bundle.status.as_str()));
        }
    }
    for command in &result.commands {
        line!("\n{}: {}", command.kind.as_str(), command.title);
        if let Some(warning) = &command.warning {
            line!("  # {warning}");
        }
        for command_line in &command.lines {
            line!("  {command_line}");
        }
    }
    if !result.unmapped.is_empty() {
        line!("\nunmapped:");
        for entry in &result.unmapped {
            let mut line = format!("  {}: {}", entry.path, entry.reason);
            if !entry.flows.is_empty() {
                line.push_str(&format!(" ({})", entry.flows.join(" ")));
            }
            if let Some(module) = &entry.module {
                line.push_str(&format!(" ({module})"));
            }
            if let Some(detail) = &entry.detail {
                line.push_str("; ");
                line.push_str(&detail.replace('\n', "\n    "));
            }
            line!("{line}");
        }
    }
    if !result.notes.is_empty() {
        line!("\nnotes:");
        for note in &result.notes {
            line!("  {note}");
        }
    }
    if !result.scenarios.is_empty() || !result.classes.is_empty() {
        line!(
            "\nEach run's report names its traces as `traces: <zip>`; ./community/tools/trace.cmd serve \
             (http://127.0.0.1:{DEFAULT_SERVE_PORT}) lists them."
        );
    }
    text
}

/// One bundle as a line of the plan: its status, what it records, its run and start, and where it is.
fn bundle_line(bundle: &Summary, status: &str) -> String {
    let mut place = bundle.source.path.clone();
    if !bundle.source.entry.is_empty() {
        place.push_str(" :: ");
        place.push_str(&bundle.source.entry);
    }
    let identity = format!("{} {}", bundle.test_class, bundle.scenario);
    let identity = match identity.trim() {
        "" => "unnamed",
        trimmed => trimmed,
    };
    let mut line = format!("  {status:<10} {identity}");
    let run = format!("{} {}", bundle.run_id, bundle.started_at.as_deref().unwrap_or_default());
    if !run.trim().is_empty() {
        line.push_str(&format!("  ({})", run.trim()));
    }
    line.push_str("  ");
    line.push_str(&place);
    line
}

const fn describe_kind(kind: Kind) -> &'static str {
    match kind {
        Kind::Bundle => "a trace bundle",
        Kind::Flow => "a flow",
        Kind::Profile => "a flow profile",
        Kind::Junit => "a JUnit test.xml",
        Kind::Report => "a vm.cmd report",
        Kind::Path => "a source path",
        Kind::Name => "a name",
    }
}
