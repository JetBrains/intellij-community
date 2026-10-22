use pretty_assertions::assert_eq;

use super::{Arm, ArmKind, Gate, check_plugin_id, parse_arm};
use crate::bench::record::{EDITOR_HIGHLIGHTED, EMPTY_STATE_BUILT, EMPTY_STATE_SPAN, OPEN_HIGHLIGHTED, WELCOME_BECAME_VISIBLE};

#[test]
fn the_main_metric_of_a_welcome_arm_is_the_welcome_event() {
    assert_eq!(Arm::MODAL.main_metric(), WELCOME_BECAME_VISIBLE);
    assert_eq!(Arm::NON_MODAL.main_metric(), WELCOME_BECAME_VISIBLE);
    assert_eq!(Arm::OPEN_PROJECT.main_metric(), OPEN_HIGHLIGHTED);
    assert_eq!(Arm::PROJECT.main_metric(), EDITOR_HIGHLIGHTED);
    assert_eq!(Arm::EMPTY_EDITOR.main_metric(), EMPTY_STATE_BUILT);
}

#[test]
fn only_the_project_arms_skip_the_welcome_screen() {
    assert_eq!(Arm::MODAL.gate(), Gate::Welcome { modal: true });
    assert_eq!(Arm::NON_MODAL.gate(), Gate::Welcome { modal: false });
    assert_eq!(Arm::OPEN_PROJECT.gate(), Gate::Welcome { modal: false });
    assert_eq!(Arm::PROJECT.gate(), Gate::Highlighted);
    let Gate::Span { name, missing } = Arm::EMPTY_EDITOR.gate() else {
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
        [Arm::MODAL, Arm::NON_MODAL, Arm::OPEN_PROJECT, Arm::PROJECT, Arm::EMPTY_EDITOR]
    );
    assert_eq!(
        (Arm::EMPTY_EDITOR.key(), Arm::EMPTY_EDITOR.label()),
        ("emptyEditor".to_owned(), "empty-editor".to_owned())
    );
    assert_eq!(serde_json::to_string(&Arm::EMPTY_EDITOR).expect("a key"), "\"emptyEditor\"");
    for arm in Arm::ALL {
        assert_eq!(parse_arm(&arm.label()), Ok(arm));
    }
}

#[test]
fn a_label_names_one_arm() {
    for arm in Arm::ALL {
        assert_eq!(Arm::from_label(&arm.label()), Some(arm.clone()));
        assert_eq!(Arm::from_key(&arm.key()), Some(arm));
    }
    assert_eq!(Arm::from_label("projects"), None);
    assert_eq!(Arm::from_label("nonModal"), None, "a key is not a label");
}

/// A twin keeps the gate and the metrics of its kind. Its key and its label name the plugin, and both read back. The
/// twin sorts right after the plain arm of its kind.
#[test]
fn a_twin_is_the_arm_of_its_kind_without_one_plugin() {
    let twin = Arm::PROJECT.without("com.intellij.air");
    assert_eq!(twin.kind, ArmKind::Project);
    assert_eq!(twin.gate(), Gate::Highlighted);
    assert_eq!(twin.main_metric(), EDITOR_HIGHLIGHTED);
    assert_eq!(twin.key(), "project-without-com.intellij.air");
    let empty = Arm::EMPTY_EDITOR.without("Lombook Plugin");
    assert_eq!(
        (empty.key(), empty.label()),
        (
            "emptyEditor-without-Lombook Plugin".to_owned(),
            "empty-editor-without-Lombook Plugin".to_owned()
        )
    );
    assert_eq!(Arm::from_label("empty-editor-without-Lombook Plugin"), Some(empty.clone()));
    assert_eq!(Arm::from_key("emptyEditor-without-Lombook Plugin"), Some(empty.clone()));
    let json = serde_json::to_string(&empty).expect("a key");
    assert_eq!(serde_json::from_str::<Arm>(&json).expect("an arm"), empty);
    let refusal = serde_json::from_str::<Arm>("\"projects\"").expect_err("a refusal");
    assert!(refusal.to_string().starts_with("`projects` is not an arm key"), "{refusal}");

    let mut arms = vec![Arm::EMPTY_EDITOR.without("a"), Arm::EMPTY_EDITOR, twin.clone(), Arm::PROJECT];
    arms.sort();
    assert_eq!(arms, [Arm::PROJECT, twin, Arm::EMPTY_EDITOR, Arm::EMPTY_EDITOR.without("a")]);
}

#[test]
fn a_twin_refuses_a_plugin_id_that_it_cannot_name() {
    assert_eq!(check_plugin_id("com.intellij.air"), Ok(()));
    for id in ["", " a", "a,b", "a/b", "a\\b", "a:b"] {
        assert!(check_plugin_id(id).is_err(), "{id:?}");
    }
    assert_eq!(Arm::from_label("project-without-a:b"), None);
    assert_eq!(Arm::from_label("projects-without-a"), None);
    let refusal = parse_arm("project-with-air").expect_err("a refusal");
    assert!(refusal.ends_with("got: project-with-air"), "{refusal}");
}
