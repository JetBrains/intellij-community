use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

const VALUE: &str = "central-login-export-0123456789abcdef";

fn secret_file(directory: &Path, name: &str, content: &str) -> RunSecretFile {
    let path = directory.join(name);
    std::fs::write(&path, content).unwrap();
    RunSecretFile {
        name: name.to_owned(),
        source: SecretSource::File(path),
    }
}

fn from_stdin(name: &str) -> RunSecretFile {
    RunSecretFile {
        name: name.to_owned(),
        source: SecretSource::Stdin,
    }
}

/// A pipe holding `bytes`.
fn piped(bytes: &[u8]) -> ScriptedStdin {
    let stdin = ScriptedStdin::default();
    stdin.feed(bytes);
    stdin
}

/// A stdin nobody reads: the run names no `@-`.
fn no_stdin() -> ScriptedStdin {
    ScriptedStdin::default()
}

// --- the grammar -------------------------------------------------------------------------------------------------

#[test]
fn only_name_at_stdin_and_name_at_file_are_accepted() {
    assert_eq!(
        parse_test_env("AIR_LIVE_CENTRAL_LOGIN=@/tmp/login").unwrap(),
        RunSecretFile {
            name: "AIR_LIVE_CENTRAL_LOGIN".to_owned(),
            source: SecretSource::File(PathBuf::from("/tmp/login")),
        }
    );
    // A path may hold `=` and `@` after the first `@`.
    assert_eq!(
        parse_test_env("_X1=@a=@b").unwrap().source,
        SecretSource::File(PathBuf::from("a=@b"))
    );
    // `@-` is stdin, and a host file named `-` is `@./-`.
    assert_eq!(
        parse_test_env("AIR_LIVE_CENTRAL_LOGIN=@-").unwrap(),
        from_stdin("AIR_LIVE_CENTRAL_LOGIN")
    );
    assert_eq!(parse_test_env("X=@./-").unwrap().source, SecretSource::File(PathBuf::from("./-")));
}

// A value on the command line is a value in the shell history and in `ps`, and the daemon's environment cannot take it.
#[test]
fn a_value_and_a_bare_name_are_refused_with_the_reason() {
    for (given, wanted) in [
        ("AIR_LIVE_CENTRAL_LOGIN", "a run hands the guest a file, not a variable"),
        (
            "AIR_LIVE_CENTRAL_LOGIN=secret-value",
            "a run hands the guest a file, not a variable",
        ),
        ("NAME=@", "names no file"),
        ("=@/tmp/x", "must be a variable name"),
        ("1NAME=@/tmp/x", "must be a variable name"),
        ("NA-ME=@/tmp/x", "must be a variable name"),
        ("../x=@/tmp/x", "must be a variable name"),
    ] {
        let refused = parse_test_env(given).unwrap_err();
        assert_eq!((refused.code.as_ref(), refused.exit), ("usage", Exit::USAGE), "{given}");
        assert!(refused.message.contains(wanted), "{given}: {}", refused.message);
    }
    // A refusal never repeats what was given: a mistaken value, a pasted token alone, or a token with `=` padding.
    for given in ["NAME=secret-value-0123", "secret-token-0123", "c2VjcmV0LXRva2Vu=="] {
        let refused = parse_test_env(given).unwrap_err();
        assert!(
            !refused.message.contains("secret-") && !refused.message.contains("c2Vj"),
            "{}",
            refused.message
        );
    }
}

// The command line takes `--test-env` on `run` alone, and refuses a value there with usage, exit 2.
#[test]
fn the_run_command_line_takes_the_option_and_refuses_a_value() {
    use clap::Parser as _;

    #[derive(clap::Parser)]
    struct Line {
        #[command(flatten)]
        run: crate::daemon::RunCommandArgs,
    }
    let parsed = Line::try_parse_from(["run", "--lane", "ui", "--test-env", "A=@/a", "--test-env", "B=@/b"]).unwrap();
    assert_eq!(
        parsed.run.test_env.iter().map(|file| file.name.as_str()).collect::<Vec<_>>(),
        ["A", "B"]
    );
    assert_eq!(parsed.run.run.lane.as_deref(), Some("ui"));
    // The selection a journal names leaves the secrets out.
    assert_eq!(parsed.run.run.argv(), ["--lane", "ui"]);
    let refused = Line::try_parse_from(["run", "--lane", "ui", "--test-env", "A=secret-value-0123"])
        .err()
        .unwrap();
    assert_eq!(refused.kind(), clap::error::ErrorKind::ValueValidation);
    // The command line answers the refusal itself, which clap would otherwise prefix with the quoted value.
    let refusal = std::error::Error::source(&refused)
        .and_then(|source| source.downcast_ref::<Refusal>())
        .expect("the parser refuses with a refusal of its own");
    assert_eq!(refusal.exit, Exit::USAGE);
    assert!(!refusal.message.contains("secret-value"), "{}", refusal.message);
}

// --- the read ----------------------------------------------------------------------------------------------------

#[test]
fn each_file_is_read_once_and_debug_names_no_value() {
    let directory = tempfile::tempdir().unwrap();
    let secrets = RunSecrets::read(
        &[
            secret_file(directory.path(), "FIRST", &format!("{VALUE}\n")),
            secret_file(directory.path(), "SECOND", "second-secret-value-0123456789"),
        ],
        &no_stdin(),
    )
    .unwrap();
    assert_eq!(secrets.names(), ["FIRST", "SECOND"]);
    let debug = format!("{secrets:?}");
    assert_eq!(debug, r#"RunSecrets { names: ["FIRST", "SECOND"] }"#);
    // Read into memory: the run does not depend on the host file staying.
    std::fs::remove_file(directory.path().join("FIRST")).unwrap();
    assert_eq!(secrets.found_in(VALUE.as_bytes()), Some("FIRST"));
}

#[test]
fn a_file_that_cannot_be_used_is_refused_before_any_lease() {
    let directory = tempfile::tempdir().unwrap();
    let missing = RunSecretFile {
        name: "MISSING".to_owned(),
        source: SecretSource::File(directory.path().join("nowhere")),
    };
    let not_regular = RunSecretFile {
        name: "DIRECTORY".to_owned(),
        source: SecretSource::File(directory.path().to_path_buf()),
    };
    let empty = secret_file(directory.path(), "EMPTY", "");
    let short = secret_file(directory.path(), "SHORT", "  short-value \n");
    for (file, wanted) in [
        (&missing, "cannot read it"),
        (&not_regular, "it is not a regular file"),
        (&empty, "fewer than 16 bytes"),
        (&short, "fewer than 16 bytes"),
    ] {
        let refusal = RunSecrets::read(std::slice::from_ref(file), &no_stdin()).unwrap_err();
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("run_secret_unreadable", Exit::NO_INPUT),
            "{}",
            file.name
        );
        assert!(refusal.message.contains(wanted), "{}", refusal.message);
        assert!(refusal.message.contains(&file.name), "{}", refusal.message);
        assert!(!refusal.message.contains("short-value"), "{}", refusal.message);
    }
    let large = secret_file(
        directory.path(),
        "LARGE",
        &"x".repeat(usize::try_from(MAX_SECRET_BYTES).unwrap() + 1),
    );
    let refusal = RunSecrets::read(&[large], &no_stdin()).unwrap_err();
    assert!(refusal.message.contains("larger than"), "{}", refusal.message);
}

// A FIFO would make the read wait for a writer that may never come.
#[cfg(unix)]
#[test]
fn a_fifo_is_refused_without_waiting_for_a_writer() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("fifo");
    nix::unistd::mkfifo(&path, nix::sys::stat::Mode::S_IRWXU).unwrap();
    let file = RunSecretFile {
        name: "FIFO".to_owned(),
        source: SecretSource::File(path),
    };
    let refusal = RunSecrets::read(&[file], &no_stdin()).unwrap_err();
    assert!(refusal.message.contains("not a regular file"), "{}", refusal.message);
}

#[test]
fn a_name_given_twice_is_a_usage_error() {
    let directory = tempfile::tempdir().unwrap();
    let file = secret_file(directory.path(), "TWICE", VALUE);
    let refusal = RunSecrets::read(&[file.clone(), file], &no_stdin()).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("run_secret_duplicate", Exit::USAGE));
    assert!(refusal.message.contains("TWICE"), "{}", refusal.message);
}

// `@-` reads the whole pipe once, beside a file, so `central login export | vm.cmd run ...` leaves no host file.
#[test]
fn stdin_is_read_once_beside_a_file() {
    let directory = tempfile::tempdir().unwrap();
    let export = format!("{VALUE}\nsecond-line-of-the-export-0123\n");
    let stdin = piped(export.as_bytes());
    let secrets = RunSecrets::read(
        &[
            from_stdin("AIR_LIVE_CENTRAL_LOGIN"),
            secret_file(directory.path(), "OTHER", "other-secret-value-0123456789"),
        ],
        &stdin,
    )
    .unwrap();
    assert_eq!(stdin.reads(), 1);
    assert_eq!(secrets.names(), ["AIR_LIVE_CENTRAL_LOGIN", "OTHER"]);
    assert_eq!(secrets.secrets[0].value, export.as_bytes());
    assert_eq!(
        secrets.found_in(b"... second-line-of-the-export-0123 ..."),
        Some("AIR_LIVE_CENTRAL_LOGIN")
    );
}

// Stdin has the bounds of a file: at least 16 bytes besides whitespace, at most 1 MiB, and the refusal names the
// variable and stdin, never what the pipe held.
#[test]
fn stdin_has_the_bounds_of_a_file() {
    let large = vec![b'x'; usize::try_from(MAX_SECRET_BYTES).unwrap() + 1];
    for (bytes, wanted) in [
        (b"".as_slice(), "fewer than 16 bytes"),
        (b" \n short-value \n".as_slice(), "fewer than 16 bytes"),
        (large.as_slice(), "larger than"),
    ] {
        let refusal = RunSecrets::read(&[from_stdin("PIPED")], &piped(bytes)).unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("run_secret_unreadable", Exit::NO_INPUT));
        assert!(refusal.message.contains(wanted), "{}", refusal.message);
        assert!(refusal.message.contains("--test-env PIPED=@-"), "{}", refusal.message);
        assert!(!refusal.message.contains("short-value"), "{}", refusal.message);
    }
}

// Stdin holds one secret: a second `@-` is a usage error before anything is read, files included.
#[test]
fn stdin_named_twice_is_refused_before_any_read() {
    let stdin = piped(VALUE.as_bytes());
    let missing = RunSecretFile {
        name: "MISSING".to_owned(),
        source: SecretSource::File(PathBuf::from("/nowhere/at/all")),
    };
    let refusal = RunSecrets::read(&[missing, from_stdin("FIRST"), from_stdin("SECOND")], &stdin).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    assert!(refusal.message.contains("@- once"), "{}", refusal.message);
    assert!(refusal.message.contains("FIRST and SECOND"), "{}", refusal.message);
    assert_eq!(stdin.reads(), 0);
}

// A terminal is no pipe: what a person types there is echoed and kept. The refusal names the pipe form, and nothing
// is read; a run that names no `@-` never asks.
#[test]
fn stdin_on_a_terminal_is_refused_with_the_pipe_form() {
    let stdin = piped(VALUE.as_bytes());
    stdin.set_terminal(true);
    let refusal = RunSecrets::read(&[from_stdin("AIR_LIVE_CENTRAL_LOGIN")], &stdin).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    assert!(
        refusal.message.contains("| vm.cmd run ... --test-env AIR_LIVE_CENTRAL_LOGIN=@-"),
        "{}",
        refusal.message
    );
    assert_eq!(stdin.reads(), 0);
    let directory = tempfile::tempdir().unwrap();
    let file = secret_file(directory.path(), "FROM_FILE", VALUE);
    assert_eq!(RunSecrets::read(&[file], &stdin).unwrap().names(), ["FROM_FILE"]);
}

// --- the scan ----------------------------------------------------------------------------------------------------

// A log line does not repeat the trailing newline of the file, a log may carry one line of a multi-line value, and a
// report or a journal carries the value JSON-escaped.
#[test]
fn the_scan_finds_the_value_trimmed_by_line_and_escaped() {
    let directory = tempfile::tempdir().unwrap();
    let multi = "first-line-of-the-export-0123\nsecond\"line\\of-the-export";
    let secrets = RunSecrets::read(
        &[
            secret_file(directory.path(), "ONE", &format!("  {VALUE}\n")),
            secret_file(directory.path(), "TWO", multi),
        ],
        &no_stdin(),
    )
    .unwrap();
    assert_eq!(secrets.found_in(format!("token={VALUE};").as_bytes()), Some("ONE"));
    assert_eq!(secrets.found_in(b"... first-line-of-the-export-0123 ..."), Some("TWO"));
    assert_eq!(secrets.found_in(br#"{"line":"second\"line\\of-the-export"}"#), Some("TWO"));
    // A fragment of a value below the minimum is not a hit: `second` alone is ordinary text.
    assert_eq!(secrets.found_in(b"second"), None);
    assert_eq!(secrets.found_in(b"nothing to see"), None);
}

/// A login export as a document: one JSON line whose fields a log may print one at a time.
const EXPORT: &str = r#"{"refreshToken":"rt.0aB9xQ7mK2pL5sW8vN3c","accountId":"acct-5f2e9a71","server":"https://central.example.org","expiresAtEpochSecond":1790000000}"#;

// A token of a line is found inside a longer artifact line, bare or as the log quoted it, and the escaped line is
// found inside a JSON string that a report or a journal wrapped around it.
#[test]
fn the_scan_finds_a_token_of_a_line_as_a_substring() {
    let secrets = RunSecrets::read(&[from_stdin("AIR_LIVE_CENTRAL_LOGIN")], &piped(EXPORT.as_bytes())).unwrap();
    for leaked in [
        "2026-10-03 12:00:01 INFO auth: Bearer rt.0aB9xQ7mK2pL5sW8vN3c accepted".to_owned(),
        r#"{"message":"refresh=rt.0aB9xQ7mK2pL5sW8vN3c;ok"}"#.to_owned(),
        // The whole line, escaped, in the middle of a longer JSON string.
        format!(
            r#"{{"stdout":"import: {} done"}}"#,
            serde_json::to_string(EXPORT).unwrap().trim_matches('"')
        ),
    ] {
        assert_eq!(secrets.found_in(leaked.as_bytes()), Some("AIR_LIVE_CENTRAL_LOGIN"), "{leaked}");
    }
}

// A token needs 16 bytes, a letter and a digit, so the key names, the host and a short id of the export are ordinary
// text that every IDE log may hold.
#[test]
fn the_scan_ignores_the_ordinary_parts_of_a_line() {
    let secrets = RunSecrets::read(&[from_stdin("AIR_LIVE_CENTRAL_LOGIN")], &piped(EXPORT.as_bytes())).unwrap();
    for ordinary in [
        "refreshToken expiresAtEpochSecond accountId",
        "connecting to https://central.example.org",
        "acct-5f2e9a71 logged in",
        "rt.0aB9xQ7mK2",
        "expires 1790000000",
    ] {
        assert_eq!(secrets.found_in(ordinary.as_bytes()), None, "{ordinary}");
    }
}

#[test]
fn a_hit_is_moved_aside_and_the_refusal_names_no_value() {
    let directory = tempfile::tempdir().unwrap();
    let secrets = RunSecrets::read(&[secret_file(directory.path(), "AIR_LIVE_CENTRAL_LOGIN", VALUE)], &no_stdin()).unwrap();
    let reports = directory.path().join("reports");
    std::fs::create_dir(&reports).unwrap();
    let leaked = reports.join("it-1.json");
    let clean = reports.join("it-2.json");
    std::fs::write(&leaked, format!("{{\"stdout\":\"{VALUE}\"}}")).unwrap();
    std::fs::write(&clean, "{}").unwrap();
    let unread = reports.join("never-fetched.txt");
    let hits = scan_artifacts(&secrets, &[leaked.clone(), clean.clone(), unread]);
    assert_eq!(
        hits,
        [SecretHit {
            name: "AIR_LIVE_CENTRAL_LOGIN".to_owned(),
            path: leaked.clone(),
        }]
    );
    let withheld = directory.path().join("withheld");
    let refusal = withhold(&hits, &withheld);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("secret_in_artifact", Exit::FAILURE));
    assert!(!leaked.exists(), "the leaked file stays where a publish rule would find it");
    assert!(clean.exists());
    let moved: Vec<_> = std::fs::read_dir(&withheld).unwrap().map(|entry| entry.unwrap().path()).collect();
    assert_eq!(moved.len(), 1);
    assert!(moved[0].to_string_lossy().ends_with("-it-1.json"), "{}", moved[0].display());
    assert!(refusal.message.contains("AIR_LIVE_CENTRAL_LOGIN"), "{}", refusal.message);
    assert!(refusal.message.contains("it-1.json"), "{}", refusal.message);
    let rendered = format!("{} {:?}", refusal.message, refusal.details());
    assert!(!rendered.contains(VALUE), "{rendered}");
}

#[test]
fn no_secret_scans_nothing() {
    let directory = tempfile::tempdir().unwrap();
    let file = directory.path().join("report.json");
    std::fs::write(&file, VALUE).unwrap();
    assert_eq!(scan_artifacts(&RunSecrets::default(), &[file]), []);
}
