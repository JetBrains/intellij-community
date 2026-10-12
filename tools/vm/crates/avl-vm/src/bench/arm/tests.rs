use clap::ValueEnum;
use pretty_assertions::assert_eq;

use super::{Arm, Gate};
use crate::bench::record::{EDITOR_HIGHLIGHTED, EMPTY_STATE_BUILT, EMPTY_STATE_SPAN, OPEN_HIGHLIGHTED, WELCOME_BECAME_VISIBLE};

#[test]
fn the_main_metric_of_a_welcome_arm_is_the_welcome_event() {
    assert_eq!(Arm::Modal.main_metric(), WELCOME_BECAME_VISIBLE);
    assert_eq!(Arm::NonModal.main_metric(), WELCOME_BECAME_VISIBLE);
    assert_eq!(Arm::OpenProject.main_metric(), OPEN_HIGHLIGHTED);
    assert_eq!(Arm::Project.main_metric(), EDITOR_HIGHLIGHTED);
    assert_eq!(Arm::EmptyEditor.main_metric(), EMPTY_STATE_BUILT);
}

#[test]
fn only_the_project_arms_skip_the_welcome_screen() {
    assert_eq!(Arm::Modal.gate(), Gate::Welcome { modal: true });
    assert_eq!(Arm::NonModal.gate(), Gate::Welcome { modal: false });
    assert_eq!(Arm::OpenProject.gate(), Gate::Welcome { modal: false });
    assert_eq!(Arm::Project.gate(), Gate::Highlighted);
    let Gate::Span { name, missing } = Arm::EmptyEditor.gate() else {
        panic!("the empty-editor arm waits for a span");
    };
    assert_eq!(name, EMPTY_STATE_SPAN);
    assert!(missing.contains("air.inline.empty.state.prompt"), "{missing}");
}

/// The empty-editor arm is the last one of the digest, and its label is its value on the command line.
#[test]
fn the_empty_editor_arm_comes_last_with_its_key_and_its_label() {
    assert_eq!(
        Arm::ALL,
        [Arm::Modal, Arm::NonModal, Arm::OpenProject, Arm::Project, Arm::EmptyEditor]
    );
    assert_eq!((Arm::EmptyEditor.key(), Arm::EmptyEditor.label()), ("emptyEditor", "empty-editor"));
    assert_eq!(serde_json::to_string(&Arm::EmptyEditor).expect("a key"), "\"emptyEditor\"");
    for arm in Arm::ALL {
        let value = arm.to_possible_value().expect("a value");
        assert_eq!(value.get_name(), arm.label());
    }
}

#[test]
fn a_label_names_one_arm() {
    for arm in Arm::ALL {
        assert_eq!(Arm::from_label(arm.label()), Some(arm));
    }
    assert_eq!(Arm::from_label("projects"), None);
}
