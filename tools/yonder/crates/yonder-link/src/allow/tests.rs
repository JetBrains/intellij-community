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
    assert!(list.add(client, "studio").expect("a name"));
    assert!(!list.add(client, "studio").expect("a name"));
    fs::write(&path, list.text()).expect("written");
    assert_eq!(AllowList::read(&path).expect("read").name(&client), Some("studio"));
}

#[test]
fn a_name_with_a_control_character_is_refused() {
    let mut list = AllowList::default();
    let client = fingerprint(b"client");
    let other = fingerprint(b"other");
    for name in [format!("laptop\n{other} evil"), "tab\tname".to_owned(), "bell\u{7}".to_owned()] {
        let error = list.add(client, &name).expect_err("refused");
        assert_eq!(error.kind(), io::ErrorKind::InvalidInput);
        assert_eq!(error.to_string(), format!("the client name {name:?} has a control character"));
    }
    assert_eq!(list, AllowList::default());
    assert!(list.add(client, "laptop of a person").expect("a name with spaces"));
}
