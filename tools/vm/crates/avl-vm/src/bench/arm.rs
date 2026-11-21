//! The arms of a session: the start-up variants that a session compares, and the twin of an arm that suppresses one
//! plugin or sets one system property.

use std::collections::BTreeMap;
use std::fmt;

use serde::{Deserialize, Serialize};

use super::record;

/// The start-up variant of an arm.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub(crate) enum ArmKind {
    /// The modal `WelcomeFrame`, forced with `idea.force.disable.non.modal.welcome.screen=true`.
    Modal,
    /// The non-modal welcome screen, the default of the product.
    NonModal,
    /// The non-modal welcome screen, then a project that the running IDE opens.
    OpenProject,
    /// A project that the command line of the IDE names, with no welcome screen.
    Project,
    /// A project that the command line of the IDE names and that restores no editor, so the empty editor area builds
    /// the Air composer.
    EmptyEditor,
    /// A Rust project that the command line of the IDE names, to the start of its first Cargo sync, as the perf test
    /// of RustRover measures it.
    CargoSync,
    /// A project that the command line of the IDE names, to the end of the Find in Files searches that the
    /// `%findInFiles` command of the performance plugin runs.
    FindInFiles,
}

/// What a run of an arm must show to pass the gate.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Gate {
    /// The FUS event `welcome.screen.became.visible` with this `is_modal`. A non-modal screen also needs the welcome
    /// project in `idea.log`.
    Welcome { modal: bool },
    /// The `editor highlighting completed` event in the trace or in the report.
    Highlighted,
    /// A span `findInFiles#search: <query>` in the trace for each query of the run, see
    /// [`record::FIND_SEARCH_PREFIX`]. The run directory holds the queries in [`record::FIND_SCRIPT_FILE`].
    FindInFiles,
    /// The span `name` in the trace. The IDE writes a span when it ends. `missing` tells why a run can lack it.
    Span { name: &'static str, missing: &'static str },
    /// The first line of `idea.log` that holds `line`. The record keeps its time from the process start as `metric`,
    /// and its time on the clock of the log as `log_metric`.
    LogLine {
        line: &'static str,
        metric: &'static str,
        log_metric: &'static str,
        missing: &'static str,
    },
}

impl ArmKind {
    /// The key in `summary.json` and in the `--json` envelope.
    pub(crate) const fn key(self) -> &'static str {
        match self {
            Self::Modal => "modal",
            Self::NonModal => "nonModal",
            Self::OpenProject => "openProject",
            Self::Project => "project",
            Self::EmptyEditor => "emptyEditor",
            Self::CargoSync => "cargoSync",
            Self::FindInFiles => "findInFiles",
        }
    }

    /// The prefix of the run directories and the name in the digest.
    pub(crate) const fn label(self) -> &'static str {
        match self {
            Self::Modal => "modal",
            Self::NonModal => "non-modal",
            Self::OpenProject => "open-project",
            Self::Project => "project",
            Self::EmptyEditor => "empty-editor",
            Self::CargoSync => "cargo-sync",
            Self::FindInFiles => "find-in-files",
        }
    }

    /// Every kind, in the order of the digest.
    pub(crate) const ALL: [Self; 7] = [
        Self::Modal,
        Self::NonModal,
        Self::OpenProject,
        Self::Project,
        Self::EmptyEditor,
        Self::CargoSync,
        Self::FindInFiles,
    ];
}

/// One arm of a session: a start-up variant, and the change of its twin. An arm with a twin change is the twin of the
/// arm of its kind. On the command line and in a run directory, an arm is its [`Arm::label`], such as `project`,
/// `project-without-com.intellij.air` or `project-with-air.apps.native.webview=true`. In `summary.json`, it is its
/// [`Arm::key`].
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(into = "String", try_from = "String")]
pub(crate) struct Arm {
    pub(crate) kind: ArmKind,
    /// What each run of the twin changes. A plain arm has none.
    pub(crate) twin: Option<Twin>,
}

/// What each run of a twin changes against the arm of its kind.
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub(crate) enum Twin {
    /// The plugin id that `idea.suppressed.plugins.id` names, so the IDE loads no plugin of that id.
    Without(String),
    /// The system property that the run passes as `-D<name>=<value>`.
    With(Property),
}

/// A system property of a twin: a name that [`check_twin_text`] accepts and has no `=`, and a value that it accepts or
/// that is empty. Its text is `<name>=<value>`.
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub(crate) struct Property {
    pub(crate) name: String,
    pub(crate) value: String,
}

impl Property {
    /// Parses `<name>=<value>`. The name is the text before the first `=`.
    pub(crate) fn parse(text: &str) -> Result<Self, String> {
        let Some((name, value)) = text.split_once('=') else {
            return Err(format!("the property `{text}` has no `=`; write <name>=<value>"));
        };
        check_twin_text("the property name", name)?;
        if !value.is_empty() {
            check_twin_text("the property value", value)?;
        }
        Ok(Self {
            name: name.to_owned(),
            value: value.to_owned(),
        })
    }
}

impl Property {
    /// Parses `<name>=<value>` of a property of every run of a session. The name is a name that [`check_twin_text`]
    /// accepts, and the value can hold any character but a control character, such as the `/` of a path: no label
    /// names it.
    pub(crate) fn parse_session(text: &str) -> Result<Self, String> {
        let Some((name, value)) = text.split_once('=') else {
            return Err(format!("the property `{text}` has no `=`; write <name>=<value>"));
        };
        check_twin_text("the property name", name)?;
        if value.chars().any(char::is_control) {
            return Err(format!("the value of the property `{name}` holds a control character"));
        }
        Ok(Self {
            name: name.to_owned(),
            value: value.to_owned(),
        })
    }
}

impl fmt::Display for Property {
    /// `<name>=<value>`.
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}={}", self.name, self.value)
    }
}

/// The infix of the key and of the label of a twin, between the kind and the suppressed plugin.
const WITHOUT: &str = "-without-";
/// The infix of the key and of the label of a twin, between the kind and the property.
const WITH: &str = "-with-";

impl Arm {
    pub(crate) const MODAL: Self = Self::plain(ArmKind::Modal);
    pub(crate) const NON_MODAL: Self = Self::plain(ArmKind::NonModal);
    pub(crate) const OPEN_PROJECT: Self = Self::plain(ArmKind::OpenProject);
    pub(crate) const PROJECT: Self = Self::plain(ArmKind::Project);
    pub(crate) const EMPTY_EDITOR: Self = Self::plain(ArmKind::EmptyEditor);
    pub(crate) const CARGO_SYNC: Self = Self::plain(ArmKind::CargoSync);
    pub(crate) const FIND_IN_FILES: Self = Self::plain(ArmKind::FindInFiles);

    /// Every plain arm, in the order of the digest. A twin is not in it.
    pub(crate) const ALL: [Self; 7] = [
        Self::MODAL,
        Self::NON_MODAL,
        Self::OPEN_PROJECT,
        Self::PROJECT,
        Self::EMPTY_EDITOR,
        Self::CARGO_SYNC,
        Self::FIND_IN_FILES,
    ];

    /// The arm of `kind` without a twin change.
    pub(crate) const fn plain(kind: ArmKind) -> Self {
        Self { kind, twin: None }
    }

    /// The twin of this arm with the change `twin`.
    pub(crate) const fn twin(&self, twin: Twin) -> Self {
        Self {
            kind: self.kind,
            twin: Some(twin),
        }
    }

    /// The twin of this arm: the same start, with the plugin `plugin` suppressed.
    pub(crate) fn without(&self, plugin: &str) -> Self {
        self.twin(Twin::Without(plugin.to_owned()))
    }

    /// The twin of this arm: the same start, with the system property `property`.
    pub(crate) fn with(&self, property: &Property) -> Self {
        self.twin(Twin::With(property.clone()))
    }

    /// The plugin that each run of the twin suppresses. Another arm has none.
    pub(crate) fn suppressed_plugin(&self) -> Option<&str> {
        match &self.twin {
            Some(Twin::Without(plugin)) => Some(plugin),
            Some(Twin::With(_)) | None => None,
        }
    }

    /// The key in `summary.json` and in the `--json` envelope: the key of the kind, and for a twin
    /// `<key>-without-<plugin id>` or `<key>-with-<name>=<value>`.
    pub(crate) fn key(&self) -> String {
        self.spelled(self.kind.key())
    }

    /// The prefix of the run directories and the name in the digest: the label of the kind, and for a twin
    /// `<label>-without-<plugin id>` or `<label>-with-<name>=<value>`.
    pub(crate) fn label(&self) -> String {
        self.spelled(self.kind.label())
    }

    fn spelled(&self, kind: &str) -> String {
        match &self.twin {
            Some(Twin::Without(plugin)) => format!("{kind}{WITHOUT}{plugin}"),
            Some(Twin::With(property)) => format!("{kind}{WITH}{property}"),
            None => kind.to_owned(),
        }
    }

    /// The metric that tells the time of the arm: the run line reports it, and it picks the median run of the arm.
    pub(crate) const fn main_metric(&self) -> &'static str {
        match self.kind {
            ArmKind::Modal | ArmKind::NonModal => record::WELCOME_BECAME_VISIBLE,
            ArmKind::OpenProject => record::OPEN_HIGHLIGHTED,
            ArmKind::Project => record::EDITOR_HIGHLIGHTED,
            ArmKind::EmptyEditor => record::EMPTY_STATE_BUILT,
            ArmKind::CargoSync => record::CARGO_SYNC_STARTED,
            ArmKind::FindInFiles => record::FIND_IN_FILES,
        }
    }

    /// The anchor of the arm in milliseconds from the process start: [`Self::main_metric`] when it is one, else
    /// `welcomeBecameVisible`, which the open-project arm reaches before it opens its second project. The main metric of
    /// the find-in-files arm is a sum of durations, so its anchor is the end of its last search. The default window of
    /// `bench trace` without the frame ends at it, and `bench classes` counts at it by default.
    pub(crate) const fn main_anchor(&self) -> &'static str {
        match self.kind {
            ArmKind::Modal | ArmKind::NonModal | ArmKind::OpenProject => record::WELCOME_BECAME_VISIBLE,
            ArmKind::Project => record::EDITOR_HIGHLIGHTED,
            ArmKind::EmptyEditor => record::EMPTY_STATE_BUILT,
            ArmKind::CargoSync => record::CARGO_SYNC_STARTED,
            ArmKind::FindInFiles => record::FIND_IN_FILES_END,
        }
    }

    /// The gate of a run of the arm. The launch waits for it, and the record checks it.
    pub(crate) const fn gate(&self) -> Gate {
        match self.kind {
            ArmKind::Modal => Gate::Welcome { modal: true },
            ArmKind::NonModal | ArmKind::OpenProject => Gate::Welcome { modal: false },
            ArmKind::Project => Gate::Highlighted,
            ArmKind::EmptyEditor => Gate::Span {
                name: record::EMPTY_STATE_SPAN,
                missing: "the project restored an editor, or the registry key air.inline.empty.state.prompt is off",
            },
            ArmKind::CargoSync => Gate::LogLine {
                line: record::CARGO_SYNC_LOG_LINE,
                metric: record::CARGO_SYNC_STARTED,
                log_metric: record::CARGO_SYNC_LOG_MS,
                missing: "the IDE has no Rust plugin, or the project has no Cargo.toml",
            },
            ArmKind::FindInFiles => Gate::FindInFiles,
        }
    }

    /// The arm of a run directory label or of a value of `--arm`: the label of a kind, else
    /// `<label>-without-<plugin id>` with a plugin id that [`check_plugin_id`] accepts, else
    /// `<label>-with-<name>=<value>` with a property that [`Property::parse`] accepts.
    pub(crate) fn from_label(label: &str) -> Option<Self> {
        Self::parse(label, ArmKind::label)
    }

    /// The arm of a key of `summary.json`. See [`Self::key`].
    pub(crate) fn from_key(key: &str) -> Option<Self> {
        Self::parse(key, ArmKind::key)
    }

    /// The text of a kind, then nothing or a twin change. No spelling of a kind starts with the spelling of another
    /// kind and a `-`, so one kind at most matches.
    fn parse(text: &str, spelling: fn(ArmKind) -> &'static str) -> Option<Self> {
        ArmKind::ALL.into_iter().find_map(|kind| {
            let rest = text.strip_prefix(spelling(kind))?;
            if rest.is_empty() {
                return Some(Self::plain(kind));
            }
            if let Some(plugin) = rest.strip_prefix(WITHOUT) {
                check_plugin_id(plugin).ok()?;
                return Some(Self::plain(kind).without(plugin));
            }
            let property = Property::parse(rest.strip_prefix(WITH)?).ok()?;
            Some(Self::plain(kind).with(&property))
        })
    }
}

impl fmt::Display for Arm {
    /// The label, see [`Arm::label`].
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.label())
    }
}

impl From<Arm> for String {
    fn from(arm: Arm) -> Self {
        arm.key()
    }
}

impl TryFrom<String> for Arm {
    type Error = String;

    fn try_from(key: String) -> Result<Self, Self::Error> {
        Self::from_key(&key).ok_or_else(|| format!("`{key}` is not an arm key"))
    }
}

/// Parses a value of `--arm`: a label of [`Arm::from_label`].
pub(crate) fn parse_arm(value: &str) -> Result<Arm, String> {
    Arm::from_label(value).ok_or_else(|| {
        let labels: Vec<&str> = ArmKind::ALL.into_iter().map(ArmKind::label).collect();
        format!(
            "must be an arm: {}, or <arm>{WITHOUT}<plugin id> or <arm>{WITH}<name>=<value> for a twin, got: {value}",
            labels.join(", ")
        )
    })
}

/// Refuses a plugin id that a twin cannot suppress or name: an id that [`check_twin_text`] refuses, and an id with a
/// `,`, which ends an id of `idea.suppressed.plugins.id`.
pub(crate) fn check_plugin_id(plugin: &str) -> Result<(), String> {
    check_twin_text("the plugin id", plugin)?;
    if plugin.contains(',') {
        return Err(format!("the plugin id `{plugin}` holds `,`, which a twin arm cannot name"));
    }
    Ok(())
}

/// Refuses a text that the label of a twin cannot hold: an empty text, and a text with a space at its ends. A `/`, a
/// `\`, a `:` or a control character is refused too, because the label is part of a run directory name, and `-Xlog`
/// ends a file name at a `:`. `what` names the text in the refusal.
fn check_twin_text(what: &str, text: &str) -> Result<(), String> {
    if text.is_empty() || text.trim() != text {
        return Err(format!("{what} `{text}` is empty or has a space at an end"));
    }
    if let Some(character) = text
        .chars()
        .find(|character| matches!(character, '/' | '\\' | ':') || character.is_control())
    {
        return Err(format!("{what} `{text}` holds `{character}`, which a twin arm cannot name"));
    }
    Ok(())
}

/// The entries of a map by [`Arm::key`] in the order of the arms: [`Arm::ALL`], each plain arm followed by its twins. A
/// key that names no arm is left out.
pub(crate) fn in_arm_order<T>(by_key: &BTreeMap<String, T>) -> Vec<(Arm, &T)> {
    let mut arms: Vec<(Arm, &T)> = by_key
        .iter()
        .filter_map(|(key, value)| Arm::from_key(key).map(|arm| (arm, value)))
        .collect();
    arms.sort_by(|left, right| left.0.cmp(&right.0));
    arms
}

#[cfg(test)]
mod tests;
