//! The arms of a session: the start-up variants that a session compares, and the twin of an arm that suppresses one
//! plugin.

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
}

/// What a run of an arm must show to pass the gate.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Gate {
    /// The FUS event `welcome.screen.became.visible` with this `is_modal`. A non-modal screen also needs the welcome
    /// project in `idea.log`.
    Welcome { modal: bool },
    /// The `editor highlighting completed` event in the trace or in the report.
    Highlighted,
    /// The span `name` in the trace. The IDE writes a span when it ends. `missing` tells why a run can lack it.
    Span { name: &'static str, missing: &'static str },
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
        }
    }

    /// Every kind, in the order of the digest.
    pub(crate) const ALL: [Self; 5] = [Self::Modal, Self::NonModal, Self::OpenProject, Self::Project, Self::EmptyEditor];
}

/// One arm of a session: a start-up variant, and the plugin that its runs suppress. An arm with a suppressed plugin is
/// the twin of the arm of its kind. On the command line and in a run directory, an arm is its [`Arm::label`], such as
/// `project` or `project-without-com.intellij.air`. In `summary.json`, it is its [`Arm::key`].
#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(into = "String", try_from = "String")]
pub(crate) struct Arm {
    pub(crate) kind: ArmKind,
    /// The plugin id that `idea.suppressed.plugins.id` names in each run of the twin. A plain arm has none.
    pub(crate) suppressed: Option<String>,
}

/// The infix of the key and of the label of a twin, between the kind and the suppressed plugin.
const WITHOUT: &str = "-without-";

impl Arm {
    pub(crate) const MODAL: Self = Self::plain(ArmKind::Modal);
    pub(crate) const NON_MODAL: Self = Self::plain(ArmKind::NonModal);
    pub(crate) const OPEN_PROJECT: Self = Self::plain(ArmKind::OpenProject);
    pub(crate) const PROJECT: Self = Self::plain(ArmKind::Project);
    pub(crate) const EMPTY_EDITOR: Self = Self::plain(ArmKind::EmptyEditor);

    /// Every plain arm, in the order of the digest. A twin is not in it.
    pub(crate) const ALL: [Self; 5] = [Self::MODAL, Self::NON_MODAL, Self::OPEN_PROJECT, Self::PROJECT, Self::EMPTY_EDITOR];

    /// The arm of `kind` that suppresses no plugin.
    pub(crate) const fn plain(kind: ArmKind) -> Self {
        Self { kind, suppressed: None }
    }

    /// The twin of this arm: the same start, with the plugin `plugin` suppressed.
    pub(crate) fn without(&self, plugin: &str) -> Self {
        Self {
            kind: self.kind,
            suppressed: Some(plugin.to_owned()),
        }
    }

    /// The key in `summary.json` and in the `--json` envelope: the key of the kind, and for a twin
    /// `<key>-without-<plugin id>`.
    pub(crate) fn key(&self) -> String {
        self.spelled(self.kind.key())
    }

    /// The prefix of the run directories and the name in the digest: the label of the kind, and for a twin
    /// `<label>-without-<plugin id>`.
    pub(crate) fn label(&self) -> String {
        self.spelled(self.kind.label())
    }

    fn spelled(&self, kind: &str) -> String {
        match &self.suppressed {
            Some(plugin) => format!("{kind}{WITHOUT}{plugin}"),
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
        }
    }

    /// The anchor of the arm in milliseconds from the process start: [`Self::main_metric`] when it is one, else
    /// `welcomeBecameVisible`, which the open-project arm reaches before it opens its second project. The default window
    /// of `bench trace` without the frame ends at it, and `bench classes` counts at it by default.
    pub(crate) const fn main_anchor(&self) -> &'static str {
        match self.kind {
            ArmKind::Modal | ArmKind::NonModal | ArmKind::OpenProject => record::WELCOME_BECAME_VISIBLE,
            ArmKind::Project => record::EDITOR_HIGHLIGHTED,
            ArmKind::EmptyEditor => record::EMPTY_STATE_BUILT,
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
        }
    }

    /// The arm of a run directory label or of a value of `--arm`: the label of a kind, else
    /// `<label>-without-<plugin id>` with a plugin id that [`check_plugin_id`] accepts.
    pub(crate) fn from_label(label: &str) -> Option<Self> {
        Self::parse(label, ArmKind::label)
    }

    /// The arm of a key of `summary.json`. See [`Self::key`].
    pub(crate) fn from_key(key: &str) -> Option<Self> {
        Self::parse(key, ArmKind::key)
    }

    fn parse(text: &str, spelling: fn(ArmKind) -> &'static str) -> Option<Self> {
        let kind_of = |name: &str| ArmKind::ALL.into_iter().find(|kind| spelling(*kind) == name);
        match text.split_once(WITHOUT) {
            Some((kind, plugin)) => {
                let kind = kind_of(kind)?;
                check_plugin_id(plugin).ok()?;
                Some(Self::plain(kind).without(plugin))
            }
            None => kind_of(text).map(Self::plain),
        }
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
            "must be an arm: {}, or <arm>{WITHOUT}<plugin id> for a twin, got: {value}",
            labels.join(", ")
        )
    })
}

/// Refuses a plugin id that a twin cannot suppress or name: an empty id, an id with a space at its ends, and an id
/// with a `,`, which ends an id of `idea.suppressed.plugins.id`. A `/`, a `\` or a `:` is refused too, because the id
/// is part of a run directory name, and `-Xlog` ends a file name at a `:`.
pub(crate) fn check_plugin_id(plugin: &str) -> Result<(), String> {
    if plugin.is_empty() || plugin.trim() != plugin {
        return Err(format!("the plugin id `{plugin}` is empty or has a space at an end"));
    }
    if let Some(character) = plugin
        .chars()
        .find(|character| matches!(character, ',' | '/' | '\\' | ':') || character.is_control())
    {
        return Err(format!(
            "the plugin id `{plugin}` holds `{character}`, which a twin arm cannot name"
        ));
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
