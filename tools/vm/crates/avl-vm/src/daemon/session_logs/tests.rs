use std::fs;
use std::path::Path;

use avl_base::Environment;
use avl_report::digest::sha256_file;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

// Each log of the variable is one zip of the push, named in the run request by its label and its digest. The zips
// leave the host once the request takes the names.
#[test]
fn each_named_log_is_one_zip_of_the_push_and_leaves_the_host_after_it() {
    let root = TempDir::new().unwrap();
    let thread = root.path().join("logs/thread-7");
    fs::create_dir_all(&thread).unwrap();
    fs::write(thread.join("events.jsonl"), "{}\n").unwrap();
    let runtime_root = root.path().join("runtime");
    fs::create_dir_all(&runtime_root).unwrap();
    let environment = Environment::from_pairs([(VARIABLE, thread.display().to_string())]);
    let packed = pack(&environment, &runtime_root).unwrap();
    assert_eq!(packed.files.len(), 1);
    let zip = Path::new(&packed.files[0].path).to_path_buf();
    assert!(zip.starts_with(&runtime_root), "{}", zip.display());
    let sha256 = sha256_file(&zip).unwrap();
    assert_eq!(packed.files[0].sha256, sha256);
    assert_eq!(
        packed.into_logs(),
        [SessionLog {
            label: "thread-7".to_owned(),
            sha256,
        }]
    );
    assert!(!zip.exists(), "the zip left the host");
}

// No variable, or one that names nothing, packs nothing. A variable that names no events file is refused.
#[test]
fn no_variable_packs_nothing_and_a_wrong_entry_is_refused() {
    let root = TempDir::new().unwrap();
    assert!(
        pack(&Environment::from_pairs::<&str, &str>([]), root.path())
            .unwrap()
            .files
            .is_empty()
    );
    assert!(
        pack(&Environment::from_pairs([(VARIABLE, " ")]), root.path())
            .unwrap()
            .files
            .is_empty()
    );
    let refusal = pack(&Environment::from_pairs([(VARIABLE, "/no/such/log.jsonl")]), root.path()).unwrap_err();
    assert_eq!(refusal.code, "session_log_invalid");
    assert!(refusal.message.contains("is not a file"), "{}", refusal.message);
}
