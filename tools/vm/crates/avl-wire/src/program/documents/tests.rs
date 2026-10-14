use std::fs;
use std::io::Write;
use std::path::Path;

use pretty_assertions::assert_eq;
use zip::CompressionMethod;
use zip::write::SimpleFileOptions;

use super::*;

/// The files of a resource root: two suite documents, one lane document, and the names that are not documents.
const ROOT: [(&str, &str); 6] = [
    ("flow-profiles/rename-session.json", r#"{"suite": "rename-session"}"#),
    ("flow-profiles/new-session.json", r#"{"suite": "new-session"}"#),
    ("flow-profiles/nested/deeper.json", "{}"),
    ("flow-profiles/README.txt", "not a document"),
    ("lanes/ui.json", r#"{"lane": "UI"}"#),
    ("authored-suites.json", "[]"),
];

fn write_tree(root: &Path, files: &[(&str, &str)]) {
    for (name, content) in files {
        let path = root.join(name);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, content).unwrap();
    }
}

/// A jar of `files` as Bazel writes one: deflated entries, with the directory entries before their files.
fn write_zip(path: &Path, files: &[(&str, &str)]) {
    let mut writer = zip::ZipWriter::new(fs::File::create(path).unwrap());
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    for directory in ["flow-profiles/", "lanes/"] {
        writer.add_directory(directory, options).unwrap();
    }
    for (name, content) in files {
        writer.start_file(*name, options).unwrap();
        writer.write_all(content.as_bytes()).unwrap();
    }
    writer.finish().unwrap();
}

fn expected(names: &[&str]) -> BTreeMap<String, Vec<u8>> {
    names
        .iter()
        .map(|name| {
            let (_, content) = ROOT.iter().find(|(path, _)| path.ends_with(&format!("/{name}"))).unwrap();
            ((*name).to_owned(), content.as_bytes().to_vec())
        })
        .collect()
}

#[test]
fn a_directory_and_a_zip_of_one_root_give_the_same_documents() {
    let temp = tempfile::tempdir().unwrap();
    let directory = temp.path().join("resources");
    write_tree(&directory, &ROOT);
    let jar = temp.path().join("flow-profiles.jar");
    write_zip(&jar, &ROOT);

    let suites = expected(&["new-session.json", "rename-session.json"]);
    assert_eq!(read_suite_documents(&directory).unwrap(), suites);
    assert_eq!(read_suite_documents(&jar).unwrap(), suites);
    let lanes = expected(&["ui.json"]);
    assert_eq!(read_lane_documents(&directory).unwrap(), lanes);
    assert_eq!(read_lane_documents(&jar).unwrap(), lanes);
}

#[cfg(unix)]
#[test]
fn a_document_that_is_a_link_is_read_through_it() {
    let temp = tempfile::tempdir().unwrap();
    let target = temp.path().join("ui.json");
    fs::write(&target, "{}").unwrap();
    let lanes = temp.path().join("resources/lanes");
    fs::create_dir_all(&lanes).unwrap();
    std::os::unix::fs::symlink(&target, lanes.join("ui.json")).unwrap();
    assert_eq!(
        read_lane_documents(&temp.path().join("resources")).unwrap(),
        BTreeMap::from([("ui.json".to_owned(), b"{}".to_vec())])
    );
}

#[test]
fn a_source_that_holds_no_document_is_refused_by_name() {
    let temp = tempfile::tempdir().unwrap();
    let directory = temp.path().join("resources");
    write_tree(&directory, &[("lanes/ui.json", "{}")]);
    let jar = temp.path().join("lanes-only.jar");
    write_zip(&jar, &[("lanes/ui.json", "{}")]);
    let empty = temp.path().join("empty");
    fs::create_dir_all(empty.join("flow-profiles")).unwrap();

    assert_eq!(
        read_suite_documents(&jar).unwrap_err(),
        format!("{} holds no flow-profiles/*.json document", jar.display())
    );
    assert_eq!(
        read_suite_documents(&empty).unwrap_err(),
        format!("{} holds no flow-profiles/*.json document", empty.display())
    );
    let missing = read_suite_documents(&directory).unwrap_err();
    assert!(
        missing.starts_with(&format!("cannot list {}: ", directory.join("flow-profiles").display())),
        "{missing}"
    );
}

#[test]
fn a_source_that_is_neither_a_directory_nor_a_zip_is_refused() {
    let temp = tempfile::tempdir().unwrap();
    let text = temp.path().join("documents.txt");
    fs::write(&text, "plain text").unwrap();
    let refused = read_suite_documents(&text).unwrap_err();
    assert!(
        refused.starts_with(&format!("{} is neither a directory nor a zip: ", text.display())),
        "{refused}"
    );
    let absent = temp.path().join("absent.jar");
    let refused = read_suite_documents(&absent).unwrap_err();
    assert!(refused.starts_with(&format!("cannot read {}: ", absent.display())), "{refused}");
}

#[test]
fn a_document_name_that_is_a_directory_is_refused() {
    let temp = tempfile::tempdir().unwrap();
    let directory = temp.path().join("resources");
    fs::create_dir_all(directory.join("lanes/ui.json")).unwrap();
    assert_eq!(
        read_lane_documents(&directory).unwrap_err(),
        format!("{} is not a file", directory.join("lanes/ui.json").display())
    );
}
