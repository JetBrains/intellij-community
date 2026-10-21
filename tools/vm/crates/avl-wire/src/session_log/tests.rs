use std::fs;
use std::io::Write;
use std::path::Path;

use pretty_assertions::assert_eq;
use tempfile::TempDir;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

use super::*;

fn write(path: &Path, content: &str) {
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, content).unwrap();
}

// The label is the thread directory, or the file name without its extension, URL-decoded, with each character outside
// [A-Za-z0-9._-] as '_'.
#[test]
fn a_label_is_the_thread_directory_or_the_file_name_url_decoded_and_safe() {
    assert_eq!(label_of(Path::new("/logs/thread-7/events.jsonl")), "thread-7");
    assert_eq!(label_of(Path::new("/logs/My%20Log+1.v2.jsonl")), "My_Log_1.v2");
    assert_eq!(label_of(Path::new("/logs/%D0%A1%D0%B5%D1%81%D1%81%D0%B8%D1%8F.jsonl")), "______");
    assert_eq!(label_of(Path::new("/logs/100%25-done%zz.jsonl")), "100_-done_zz");
}

// The variable names files and thread directories, separated by the path separator. A blank entry counts nothing, an
// entry with no events file is refused, and so are two logs of one label.
#[test]
fn the_variable_names_files_and_thread_directories() {
    let root = TempDir::new().unwrap();
    write(&root.path().join("thread-a/events.jsonl"), "{}\n");
    write(&root.path().join("b.jsonl"), "{}\n");
    let value = format!(
        "{}{SEPARATOR} {SEPARATOR}{}",
        root.path().join("thread-a").display(),
        root.path().join("b.jsonl").display()
    );
    assert_eq!(
        named_logs(&value).unwrap(),
        [
            NamedLog {
                label: "thread-a".to_owned(),
                events: root.path().join("thread-a/events.jsonl"),
            },
            NamedLog {
                label: "b".to_owned(),
                events: root.path().join("b.jsonl"),
            },
        ]
    );
    assert_eq!(named_logs("").unwrap(), []);
    let missing = root.path().join("missing");
    assert!(named_logs(&missing.display().to_string()).unwrap_err().contains("is not a file"));
    write(&root.path().join("other/b/events.jsonl"), "{}\n");
    let twice = format!(
        "{}{SEPARATOR}{}",
        root.path().join("b.jsonl").display(),
        root.path().join("other/b").display()
    );
    assert_eq!(
        named_logs(&twice).unwrap_err(),
        format!("{VARIABLE} names two logs of the label 'b'")
    );
}

// A log packs its events file and the files of its sibling directories as stored entries, in name order, and unpacks
// to the same tree. A directory beside it of another name stays behind, and one log packs to one set of bytes.
#[test]
fn a_log_packs_its_events_and_siblings_and_unpacks_to_the_same_tree() {
    let root = TempDir::new().unwrap();
    let thread = root.path().join("thread");
    write(&thread.join("events.jsonl"), "{\"a\":1}\n");
    write(&thread.join("attachments/b.png"), "png");
    write(&thread.join("outputs/sub/c.txt"), "out");
    write(&thread.join("private/d.txt"), "no");
    let log = &named_logs(&thread.display().to_string()).unwrap()[0];
    let archive = root.path().join("thread.zip");
    pack(log, &archive).unwrap();
    let mut zip = ZipArchive::new(fs::File::open(&archive).unwrap()).unwrap();
    let mut names = Vec::new();
    for index in 0..zip.len() {
        let entry = zip.by_index(index).unwrap();
        assert_eq!(entry.compression(), CompressionMethod::Stored, "{}", entry.name());
        names.push(entry.name().to_owned());
    }
    assert_eq!(names, ["events.jsonl", "attachments/b.png", "outputs/sub/c.txt"]);
    let again = root.path().join("again.zip");
    pack(log, &again).unwrap();
    assert_eq!(fs::read(&again).unwrap(), fs::read(&archive).unwrap());

    let context = root.path().join("context/thread");
    let events = unpack(&archive, &context).unwrap();
    assert_eq!(events, context.join("events.jsonl"));
    assert_eq!(fs::read_to_string(&events).unwrap(), "{\"a\":1}\n");
    assert_eq!(fs::read_to_string(context.join("attachments/b.png")).unwrap(), "png");
    assert_eq!(fs::read_to_string(context.join("outputs/sub/c.txt")).unwrap(), "out");
    assert!(!context.join("private").exists());
}

// An entry that leaves the directory is refused, and so is a zip with no events file.
#[test]
fn an_unpack_refuses_an_entry_outside_and_a_zip_without_events() {
    let root = TempDir::new().unwrap();
    let zip_of = |name: &str, entry: &str| {
        let path = root.path().join(name);
        let mut writer = ZipWriter::new(fs::File::create(&path).unwrap());
        writer.start_file(entry, SimpleFileOptions::default()).unwrap();
        writer.write_all(b"x").unwrap();
        writer.finish().unwrap();
        path
    };
    let outside = zip_of("outside.zip", "../escape.txt");
    assert!(
        unpack(&outside, &root.path().join("a"))
            .unwrap_err()
            .contains("which leaves its directory")
    );
    assert!(!root.path().join("escape.txt").exists());
    let no_events = zip_of("no-events.zip", "outputs/x.txt");
    assert!(
        unpack(&no_events, &root.path().join("b"))
            .unwrap_err()
            .ends_with("holds no events.jsonl")
    );
}
