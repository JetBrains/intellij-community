use pretty_assertions::assert_eq;

use super::*;

fn fingerprint(seed: &[u8]) -> Fingerprint {
    Fingerprint::of(seed)
}

#[test]
fn the_list_skips_comments_and_keeps_the_names() {
    let first = fingerprint(b"first");
    let second = fingerprint(b"second");
    let list = AllowList::parse(&format!("# clients\n\n{first} laptop of a person\n{second}\n")).expect("parsed");
    assert_eq!(list.name(&first), Some("laptop of a person"));
    assert_eq!(list.name(&second), Some(""));
    assert_eq!(list.name(&fingerprint(b"third")), None);
}

#[test]
fn a_bad_line_is_named_by_its_number() {
    assert_eq!(AllowList::parse("# ok\nnot-a-fingerprint name\n"), Err(2));
}

#[test]
fn a_missing_file_is_an_empty_list_and_an_added_client_round_trips() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let path = dir.path().join("clients");
    let mut list = AllowList::read(&path).expect("empty");
    assert_eq!(list, AllowList::default());
    let client = fingerprint(b"client");
    assert!(list.add(client, "studio"));
    assert!(!list.add(client, "studio"));
    fs::write(&path, list.text()).expect("written");
    assert_eq!(AllowList::read(&path).expect("read").name(&client), Some("studio"));
}
