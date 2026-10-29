use pretty_assertions::assert_eq;

use super::*;

fn step() -> Step {
    Step {
        name: "pnpm install".to_owned(),
        run: vec!["pnpm".to_owned(), "install".to_owned()],
        inputs: vec!["pnpm-lock.yaml".to_owned()],
        creates: vec!["node_modules".to_owned()],
    }
}

#[test]
fn a_step_runs_first_then_only_when_an_input_or_a_created_path_changed() {
    let slot = tempfile::tempdir().expect("a slot");
    let step = step();
    let mut prepared = BTreeMap::new();
    let first = fingerprint(&step, &[Some("a".repeat(40))]);
    assert_eq!(
        reason(&step, &first, &prepared, slot.path()).as_deref(),
        Some("node_modules is missing")
    );
    std::fs::create_dir(slot.path().join("node_modules")).expect("node_modules");
    assert_eq!(
        reason(&step, &first, &prepared, slot.path()).as_deref(),
        Some("the first run in this slot")
    );
    prepared.insert(key(&step), first.clone());
    assert_eq!(reason(&step, &first, &prepared, slot.path()), None);

    let changed = fingerprint(&step, &[Some("b".repeat(40))]);
    assert_ne!(changed, first);
    assert_eq!(
        reason(&step, &changed, &prepared, slot.path()).as_deref(),
        Some("pnpm-lock.yaml changed")
    );
    let missing = fingerprint(&step, &[None]);
    assert_ne!(missing, first);

    let other_command = Step {
        run: vec!["pnpm".to_owned(), "install".to_owned(), "--frozen-lockfile".to_owned()],
        ..step.clone()
    };
    assert_ne!(fingerprint(&other_command, &[Some("a".repeat(40))]), first);
    assert_eq!(key(&other_command), key(&step));
}

#[test]
fn the_fingerprint_does_not_join_two_words_into_one() {
    let one = Step {
        run: vec!["ab".to_owned()],
        inputs: vec![],
        ..step()
    };
    let two = Step {
        run: vec!["a".to_owned(), "b".to_owned()],
        ..one.clone()
    };
    assert_ne!(fingerprint(&one, &[]), fingerprint(&two, &[]));
}
