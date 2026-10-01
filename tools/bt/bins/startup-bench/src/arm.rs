//! The arms of a session: the start-up variants that a session compares.

use serde::{Deserialize, Serialize};

/// One start-up variant.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum Arm {
    /// The modal `WelcomeFrame`, forced with `idea.force.disable.non.modal.welcome.screen=true`.
    Modal,
    /// The non-modal welcome screen, the default of the product.
    NonModal,
    /// The non-modal welcome screen, then a project that the running IDE opens.
    OpenProject,
}

impl Arm {
    /// The key in `summary.json` and in the `--json` envelope.
    pub(crate) const fn key(self) -> &'static str {
        match self {
            Self::Modal => "modal",
            Self::NonModal => "nonModal",
            Self::OpenProject => "openProject",
        }
    }

    /// The prefix of the run directories and the name in the digest.
    pub(crate) const fn label(self) -> &'static str {
        match self {
            Self::Modal => "modal",
            Self::NonModal => "non-modal",
            Self::OpenProject => "open-project",
        }
    }

    /// The value of the `is_modal` field that the gate expects in `welcome.screen.became.visible`.
    pub(crate) const fn expects_modal(self) -> bool {
        matches!(self, Self::Modal)
    }

    /// Tells whether the gate also needs the welcome project in `idea.log`.
    pub(crate) const fn opens_welcome_project(self) -> bool {
        !self.expects_modal()
    }

    /// The arm of a run directory label.
    pub(crate) fn from_label(label: &str) -> Option<Self> {
        [Self::Modal, Self::NonModal, Self::OpenProject]
            .into_iter()
            .find(|arm| arm.label() == label)
    }
}
