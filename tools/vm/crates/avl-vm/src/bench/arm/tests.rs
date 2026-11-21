use pretty_assertions::assert_eq;

use super::{Arm, ArmKind, Gate, Property, Twin, check_plugin_id, parse_arm};
use crate::bench::record::{
    CARGO_SYNC_LOG_LINE, CARGO_SYNC_LOG_MS, CARGO_SYNC_STARTED, EDITOR_HIGHLIGHTED, EMPTY_STATE_BUILT, EMPTY_STATE_SPAN, FIND_IN_FILES,
    FIND_IN_FILES_END, OPEN_HIGHLIGHTED, WELCOME_BECAME_VISIBLE,
};

#[test]
fn the_main_metric_of_a_welcome_arm_is_the_welcome_event() {
    assert_eq!(Arm::MODAL.main_metric(), WELCOME_BECAME_VISIBLE);
    assert_eq!(Arm::NON_MODAL.main_metric(), WELCOME_BECAME_VISIBLE);
    assert_eq!(Arm::OPEN_PROJECT.main_metric(), OPEN_HIGHLIGHTED);
    assert_eq!(Arm::PROJECT.main_metric(), EDITOR_HIGHLIGHTED);
    assert_eq!(Arm::EMPTY_EDITOR.main_metric(), EMPTY_STATE_BUILT);
    assert_eq!(Arm::CARGO_SYNC.main_metric(), CARGO_SYNC_STARTED);
    assert_eq!(Arm::FIND_IN_FILES.main_metric(), FIND_IN_FILES);
    assert_eq!(Arm::FIND_IN_FILES.main_anchor(), FIND_IN_FILES_END);
    assert_eq!(Arm::FIND_IN_FILES.gate(), Gate::FindInFiles);
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
    let Gate::LogLine {
        line, metric, log_metric, ..
    } = Arm::CARGO_SYNC.gate()
    else {
        panic!("the cargo-sync arm waits for a line of idea.log");
    };
    assert_eq!(
        (line, metric, log_metric),
        (CARGO_SYNC_LOG_LINE, CARGO_SYNC_STARTED, CARGO_SYNC_LOG_MS)
    );
}

/// The find-in-files arm is the last one of the digest, and the label of an arm is its value on the command line.
#[test]
fn the_find_in_files_arm_comes_last_and_each_arm_has_its_key_and_its_label() {
    assert_eq!(
        Arm::ALL,
        [
            Arm::MODAL,
            Arm::NON_MODAL,
            Arm::OPEN_PROJECT,
            Arm::PROJECT,
            Arm::EMPTY_EDITOR,
            Arm::CARGO_SYNC,
            Arm::FIND_IN_FILES
        ]
    );
    assert_eq!(
        (Arm::FIND_IN_FILES.key(), Arm::FIND_IN_FILES.label()),
        ("findInFiles".to_owned(), "find-in-files".to_owned())
    );
    assert_eq!(
        (Arm::CARGO_SYNC.key(), Arm::CARGO_SYNC.label()),
        ("cargoSync".to_owned(), "cargo-sync".to_owned())
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

/// A twin of a property keeps the gate and the metrics of its kind. Its key and its label name the property, and both
/// read back, also with a value that holds `-without-` or `=`. It sorts after the twin without a plugin.
#[test]
fn a_twin_is_the_arm_of_its_kind_with_one_property() {
    let property = Property::parse("air.apps.native.webview=true").expect("a property");
    assert_eq!(
        property,
        Property {
            name: "air.apps.native.webview".to_owned(),
            value: "true".to_owned()
        }
    );
    let twin = Arm::EMPTY_EDITOR.with(&property);
    assert_eq!(twin.twin, Some(Twin::With(property)));
    assert_eq!(twin.gate(), Arm::EMPTY_EDITOR.gate());
    assert_eq!(twin.main_metric(), EMPTY_STATE_BUILT);
    assert_eq!(twin.suppressed_plugin(), None);
    assert_eq!(
        (twin.key(), twin.label()),
        (
            "emptyEditor-with-air.apps.native.webview=true".to_owned(),
            "empty-editor-with-air.apps.native.webview=true".to_owned()
        )
    );
    assert_eq!(Arm::from_label(&twin.label()), Some(twin.clone()));
    assert_eq!(Arm::from_key(&twin.key()), Some(twin.clone()));
    let json = serde_json::to_string(&twin).expect("a key");
    assert_eq!(serde_json::from_str::<Arm>(&json).expect("an arm"), twin);

    let odd = Arm::PROJECT.with(&Property::parse("a.b=x-without-y=z").expect("a property"));
    assert_eq!(odd.label(), "project-with-a.b=x-without-y=z");
    assert_eq!(Arm::from_label(&odd.label()), Some(odd));
    let empty = Arm::PROJECT.with(&Property::parse("flag=").expect("an empty value"));
    assert_eq!(Arm::from_label("project-with-flag="), Some(empty));

    let without = Arm::EMPTY_EDITOR.without("com.intellij.air");
    let mut arms = vec![twin.clone(), Arm::EMPTY_EDITOR, without.clone()];
    arms.sort();
    assert_eq!(arms, [Arm::EMPTY_EDITOR, without, twin]);
}

#[test]
fn a_twin_refuses_a_property_that_it_cannot_name() {
    for text in ["", "flag", "=true", " a=b", "a=b ", "a/b=c", "a=b:c", "a=b\\c", "a\tb=c"] {
        assert!(Property::parse(text).is_err(), "{text:?}");
    }
    assert_eq!(
        Property::parse("flag"),
        Err("the property `flag` has no `=`; write <name>=<value>".to_owned())
    );
    assert_eq!(Arm::from_label("project-with-flag"), None);
    assert_eq!(Arm::from_label("project-with-a=b:c"), None);
    let refusal = parse_arm("project-with-a").expect_err("a refusal");
    assert!(refusal.contains("<arm>-with-<name>=<value>"), "{refusal}");
}
