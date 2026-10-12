use std::os::unix::fs::{MetadataExt, PermissionsExt};
use std::process::Command;
use std::sync::Barrier;

use pretty_assertions::assert_eq;

use super::*;

/// A fake that prints the answer of the directory it was started from.
const ANSWERING: &str = "#!/bin/sh\ncat \"$(dirname \"$0\")/answer.txt\"\n";

// Two fixtures of one fake get two names of one file, and each run reads the directory of the name it was started by.
#[test]
fn the_fixtures_of_a_process_share_one_file_and_keep_their_own_files() {
    let shared = script(ANSWERING).unwrap();
    let (first, second) = (new_directory(), new_directory());
    for (directory, answer) in [(&first, "first"), (&second, "second")] {
        place(&shared, &directory.join("tool")).unwrap();
        fs::write(directory.join("answer.txt"), answer).unwrap();
    }
    let inode = |directory: &Path| fs::metadata(directory.join("tool")).unwrap().ino();
    assert_eq!(inode(&first), inode(&second));
    let run = |directory: &Path| String::from_utf8(Command::new(directory.join("tool")).output().unwrap().stdout).unwrap();
    assert_eq!(run(&first), "first");
    assert_eq!(run(&second), "second");
    assert_eq!(script(ANSWERING).unwrap(), shared, "one content is written once");
    assert_ne!(script("#!/bin/sh\nexit 0\n").unwrap(), shared, "another content is another file");
    for directory in [first, second] {
        fs::remove_dir_all(&directory).unwrap();
    }
}

// A later process finds the file of an earlier one by its name, and uses it again without a write.
#[test]
fn a_later_process_uses_the_published_file_again() {
    let directory = new_directory();
    let first = publish(&directory, "script", "", ANSWERING.as_bytes()).unwrap();
    let metadata = fs::metadata(&first).unwrap();
    assert_eq!(metadata.permissions().mode() & 0o777, 0o555);
    let again = publish(&directory, "script", "", ANSWERING.as_bytes()).unwrap();
    assert_eq!(again, first);
    assert_eq!(fs::metadata(&again).unwrap().ino(), metadata.ino(), "the file is not written again");
    assert_eq!(entries(&directory), [file_name(&first)], "no staging file stays");
    fs::remove_dir_all(&directory).unwrap();
}

// The name is the hash of the content, so a file of that name with another content is a refusal and not a fake.
#[test]
fn a_file_with_another_content_under_the_name_is_refused() {
    let directory = new_directory();
    let path = publish(&directory, "script", "", ANSWERING.as_bytes()).unwrap();
    fs::remove_file(&path).unwrap();
    fs::write(&path, "#!/bin/sh\nexit 0\n").unwrap();
    let error = publish(&directory, "script", "", ANSWERING.as_bytes()).unwrap_err();
    assert_eq!(
        error.to_string(),
        format!("{} holds another content than its name states; remove the file", path.display())
    );
    fs::remove_dir_all(&directory).unwrap();
}

// The first writers of many processes at once all answer one complete file, and no staging file stays.
#[test]
fn concurrent_first_writers_publish_one_complete_file() {
    const WRITERS: usize = 8;
    let directory = new_directory();
    let content = format!("#!/bin/sh\n{}\n", "echo concurrent\n".repeat(4096));
    let barrier = Barrier::new(WRITERS);
    let paths: Vec<PathBuf> = std::thread::scope(|scope| {
        let writers: Vec<_> = (0..WRITERS)
            .map(|_| {
                scope.spawn(|| {
                    barrier.wait();
                    publish(&directory, "script", "", content.as_bytes()).unwrap()
                })
            })
            .collect();
        writers.into_iter().map(|writer| writer.join().unwrap()).collect()
    });
    assert!(paths.iter().all(|path| *path == paths[0]), "{paths:?}");
    assert_eq!(fs::read_to_string(&paths[0]).unwrap(), content);
    assert_eq!(entries(&directory), [file_name(&paths[0])]);
    fs::remove_dir_all(&directory).unwrap();
}

fn entries(directory: &Path) -> Vec<String> {
    let mut names: Vec<String> = fs::read_dir(directory)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .collect();
    names.sort();
    names
}

fn file_name(path: &Path) -> String {
    path.file_name().unwrap().to_str().unwrap().to_owned()
}
