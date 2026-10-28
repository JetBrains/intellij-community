use pretty_assertions::assert_eq;

use super::*;

/// The last JSON line of `bd lease host lend`: a slot with its lease and its last clean, or a place in the queue
#[test]
fn a_lend_answers_a_slot_or_a_place_in_the_queue() {
    let lent = r#"{"schema":2,"state":"lent","slot":{"n":2,"path":"/p/2/idea-1","lease_id":"208e","cleaned_at":17,"generation":3,"last_used_by_affinity_ms":5}}"#;
    assert_eq!(
        parse_lend(lent),
        Some(Lend::Lent(Lent {
            n: 2,
            slot: PathBuf::from("/p/2/idea-1"),
            lease_id: "208e".to_owned(),
            generation: 3,
            last_used_by_holder_ms: Some(5),
        }))
    );
    let queued = r#"{"schema":2,"state":"queued","ahead":1,"slots":2,"lent":[{"n":1,"run":"r1"},{"n":2,"run":"r2"}],"quarantined":[{"n":3,"processes":[{"pid":9,"command":"sh","argv":"sh -c x"}]}]}"#;
    assert_eq!(
        parse_lend(queued),
        Some(Lend::Queued(Queued {
            ahead: 1,
            slots: 2,
            lent: vec![(1, "r1".to_owned()), (2, "r2".to_owned())],
            quarantined: vec![(3, vec!["9 (sh -c x)".to_owned()])],
        }))
    );
    assert_eq!(parse_lend(r#"{"state":"lent"}"#), None, "a lend without a slot");
    assert_eq!(parse_lend("not json"), None);
}

/// A give-back names its slot, or none when the run left the queue; a reclaim and a fill answer their slots
#[test]
fn a_give_back_a_reclaim_and_a_fill_answer_their_slots() {
    let gave_back = r#"{"schema":2,"n":1,"left_queue":false,"stopped":[{"pid":3,"command":"sleep","argv":"sleep 9"}],"quarantined":[],"discarded":0,"processes_checked":true,"maintenance_started":true,"deleted_ref":"refs/branch-deck/host/run/1-2"}"#;
    assert_eq!(
        parse_give_back(gave_back),
        Some(GaveBack {
            n: Some(1),
            stopped: 1,
            quarantined: Vec::new(),
            deleted_ref: Some("refs/branch-deck/host/run/1-2".to_owned()),
            maintenance_started: true,
        })
    );
    assert_eq!(
        parse_give_back(r#"{"schema":2,"left_queue":true,"discarded":0,"processes_checked":false}"#),
        Some(GaveBack::default())
    );
    assert_eq!(
        parse_reclaim(r#"{"schema":2,"reclaimed":[{"n":1,"action":"gave_back"},{"n":2,"action":"cleaned","error":"no"}]}"#),
        ["slot 1: gave_back", "slot 2: cleaned failed: no"]
    );
    assert_eq!(
        parse_fill("bd lease host fill: slot 1: cloning\n{\"schema\":2,\"filled\":[{\"n\":1,\"path\":\"/p\",\"reused\":true}]}"),
        [1]
    );
}

/// The fingerprints of a slot survive a run, and a new generation of its files or another lease id starts them empty
#[test]
fn the_fingerprints_of_a_slot_are_kept_until_its_next_clean() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let mut prepared = Prepared::load(dir.path(), "lease-1", 10);
    assert!(prepared.steps.is_empty());
    prepared.steps.insert("00aa".to_owned(), "f1".to_owned());
    prepared.save().expect("saved");
    assert_eq!(Prepared::load(dir.path(), "lease-1", 10).steps, prepared.steps);
    assert!(
        Prepared::load(dir.path(), "lease-1", 11).steps.is_empty(),
        "a give-back discarded changes, or a clean ran"
    );
    assert!(Prepared::load(dir.path(), "lease-2", 10).steps.is_empty(), "another slot");
}

/// The last JSON line of `bd lease host checkout`: how Branch Deck moved the slot, with git's reason for a forced one
#[test]
fn a_checkout_answers_how_the_slot_moved() {
    assert_eq!(
        parse_checkout(r#"{"schema":2,"n":1,"path":"/p","commit":"c","moved":"kept"}"#),
        Some(Checkout::Kept)
    );
    assert_eq!(
        parse_checkout(r#"{"schema":2,"n":1,"path":"/p","commit":"c","moved":"switched"}"#),
        Some(Checkout::Switched)
    );
    assert_eq!(
        parse_checkout(
            r#"{"schema":2,"n":1,"path":"/p","commit":"c","moved":"forced","reason":"error: Untracked working tree file 'b' would be overwritten by merge."}"#
        ),
        Some(Checkout::Forced(
            "git refused the diff: error: Untracked working tree file 'b' would be overwritten by merge.".to_owned()
        ))
    );
    assert_eq!(parse_checkout(r#"{"schema":2,"moved":"moved"}"#), None);
    assert_eq!(parse_checkout("not json"), None);
}
