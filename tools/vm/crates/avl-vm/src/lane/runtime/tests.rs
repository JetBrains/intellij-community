use std::ffi::OsString;

use avl_base::Reporter;
use avl_base::report::Mode;
use bt_core::{Platform, Runtime};

use super::*;

/// A resolution that reached for bazel would spawn it from inside a held lease, minutes into a step where every other
/// one spends seconds. `Runtime::spawn` cannot fail by contract, so the refusal arrives as the 127 its classifier
/// reads as infra.
#[test]
fn the_selector_runtime_cannot_spawn() {
    let (reporter, _, _) = Reporter::in_memory("test");
    let command: Vec<OsString> = ["/bin/sh", "bazel.cmd", "test"].map(OsString::from).into();

    let result = bt_runtime(Path::new("/repo"), &reporter).spawn(&command, None);

    assert_eq!(result.exit_code, 127);
    assert!(result.output.contains("must not spawn"), "{result:?}");
}

/// `bt`'s diagnostics become the reporter's notes, which carry the worker a line came from; its digest goes nowhere.
#[test]
fn the_selector_runtimes_diagnostics_reach_the_reporter() {
    let (reporter, stdout, stderr) = Reporter::in_memory("test");
    // The progress stream, so a note is a record on stderr.
    reporter.set_mode(Mode::Stream);
    let runtime = bt_runtime(Path::new("/repo"), &reporter);

    runtime.write("a digest nobody asked for");
    runtime.write_error("resolving the selector\n");

    assert!(stdout.is_empty(), "{}", stdout.text());
    let said = stderr.text();
    // Without the newline the runtime's callers leave to the sink.
    assert!(said.contains(r#""resolving the selector""#), "{said:?}");
    assert!(!said.contains("digest"), "{said:?}");
    assert_eq!(runtime.repo_root(), Path::new("/repo"));
    // The host's platform, which is what a selector's paths and command lines follow.
    assert_eq!(runtime.platform(), Platform::current());
}

/// The runtime reads the checkout a selector resolves against: files, lines split the way the BEP reader expects,
/// listings and existence, directories included.
#[test]
fn the_selector_runtime_reads_the_tree_it_resolves_against() {
    let (reporter, _, _) = Reporter::in_memory("test");
    let directory = tempfile::tempdir().expect("a checkout");
    let source = directory.path().join("Sample.kt");
    std::fs::write(&source, "package a.b\r\nclass Sample\n").expect("the source is written");
    let runtime = bt_runtime(directory.path(), &reporter);

    let text = runtime.read_text_file(&source).expect("the source is readable");
    assert!(text.contains("class Sample"), "{text:?}");
    // CRLF is split like LF, and the trailing newline yields a final empty line.
    assert_eq!(runtime.read_lines(&source).collect::<Vec<_>>(), ["package a.b", "class Sample", ""]);
    // No error and no lines for a file that is not there: bazel never wrote the BEP file when it died during option
    // parsing.
    let gone = directory.path().join("gone");
    assert_eq!(runtime.read_lines(&gone).count(), 0);

    let entries = runtime.read_dir(directory.path()).expect("the checkout lists");
    assert_eq!(
        entries.iter().map(|entry| (entry.name.as_str(), entry.is_dir)).collect::<Vec<_>>(),
        [("Sample.kt", false)]
    );
    // Answers for a directory as well as a file: every test source root bt probes is one.
    assert!(runtime.exists(directory.path()) && runtime.exists(&source) && !runtime.exists(&gone));
}

#[test]
fn the_selector_runtime_names_and_removes_its_own_temporaries() {
    let (reporter, _, _) = Reporter::in_memory("test");
    let directory = tempfile::tempdir().expect("a checkout");
    let runtime = bt_runtime(directory.path(), &reporter);
    let first = runtime.temp_file("bep");
    let second = runtime.temp_file("bep");
    // A name two processes can both choose is one they can share.
    assert_ne!(first, second);
    let name = first
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_default();
    assert!(name.starts_with("bep-"), "the temporary is named {name}");
    std::fs::write(&first, "{}").expect("the temporary is written");
    runtime.remove(&first).expect("the temporary is removed");
    // Content with an absent path.
    runtime.remove(&first).expect("removing an absent file was refused");
}
