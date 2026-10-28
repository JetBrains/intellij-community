//! The calls through a fake `bd`: a shell script that records its arguments and answers the JSON of a file per
//! subcommand, with the exit code of another file.

use std::fs;

use pretty_assertions::assert_eq;

use super::*;
use crate::testkit::calm;
use crate::{Config, OsProcesses};

/// A directory with a fake `bd`. `<sub>.json` is the stdout of `bd -C <checkout> host <sub> …`, and `<sub>.exit` its
/// exit code, 0 without the file. `args` holds the arguments of each call, one per line.
struct FakeBd {
    dir: tempfile::TempDir,
    program: String,
}

impl FakeBd {
    fn new() -> Self {
        let dir = tempfile::tempdir().expect("a directory");
        let program = dir.path().join("bd");
        let base = dir.path().display();
        fs::write(
            &program,
            format!(
                "#!/bin/sh\nprintf '%s\\n' \"$@\" >> '{base}/args'\ncat '{base}/'\"$4\".json\nexit \"$(cat '{base}/'\"$4\".exit 2>/dev/null || echo 0)\"\n"
            ),
        )
        .expect("the fake bd");
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt as _;
            fs::set_permissions(&program, fs::Permissions::from_mode(0o755)).expect("an executable");
        }
        let program = program.to_string_lossy().into_owned();
        Self { dir, program }
    }

    fn answer(&self, sub: &str, json: &str, exit: i32) {
        fs::write(self.dir.path().join(format!("{sub}.json")), json).expect("the answer");
        fs::write(self.dir.path().join(format!("{sub}.exit")), exit.to_string()).expect("the exit");
    }

    fn args(&self) -> Vec<String> {
        fs::read_to_string(self.dir.path().join("args"))
            .unwrap_or_default()
            .lines()
            .map(str::to_owned)
            .collect()
    }
}

fn with_client<T>(bd: &FakeBd, call: impl FnOnce(&Client<'_>) -> T) -> T {
    let config = Config::new(bd.dir.path().join("config"));
    let client = Client::new(config, &OsProcesses, &|_| {}, &calm).with_bd(&bd.program);
    call(&client)
}

#[cfg(unix)]
#[test]
fn a_snapshot_names_its_scan_and_reads_the_answer_of_bd() {
    let bd = FakeBd::new();
    bd.answer(
        "snapshot",
        r#"{"schema":2,"commit":"c","head":"h","changed":[" M a.txt"],"repository":"idea","git_dir":"/c/.git","patch_dir":"/c/.git/yonder","base":"b","candidates":["h","h1"],"blob":{"path":".yonder.toml","id":"i","text":"outputs = []\n"},"operation_id":"o"}"#,
        0,
    );
    let checkout = Path::new("/c");
    let snapshot = with_client(&bd, |client| client.snapshot(checkout, Scan::WorkingTree, NEAR)).expect("the snapshot");
    assert_eq!(
        snapshot,
        Snapshot {
            commit: "c".to_owned(),
            head: "h".to_owned(),
            changed: vec![" M a.txt".to_owned()],
            repository: Some("idea".to_owned()),
            patch_dir: PathBuf::from("/c/.git/yonder"),
            base: Some("b".to_owned()),
            candidates: vec!["h".to_owned(), "h1".to_owned()],
            blob: Some(Blob {
                text: "outputs = []\n".to_owned()
            }),
        }
    );
    with_client(&bd, |client| {
        client.snapshot(checkout, Scan::Head, FARTHEST).expect("HEAD");
        client.snapshot(checkout, Scan::Status, 1).expect("the status");
    });
    assert_eq!(
        bd.args().join(" "),
        [
            "-C /c host snapshot --working-tree --blob .yonder.toml --farthest 16",
            "-C /c host snapshot --no-status --blob .yonder.toml --farthest 65536",
            "-C /c host snapshot --farthest 1",
        ]
        .join(" ")
    );
}

/// `changed`, `repository`, `base` and `blob` are optional: `--no-status` has no `changed`, and a snapshot without
/// `--blob` has no `blob`.
#[test]
fn a_snapshot_without_its_optional_fields_parses() {
    let snapshot: Snapshot = serde_json::from_str(
        r#"{"schema":2,"commit":"c","head":"c","git_dir":"/c/.git","patch_dir":"/c/.git/yonder","candidates":["c"],"operation_id":"o"}"#,
    )
    .expect("a snapshot");
    assert_eq!(
        snapshot,
        Snapshot {
            commit: "c".to_owned(),
            head: "c".to_owned(),
            patch_dir: PathBuf::from("/c/.git/yonder"),
            candidates: vec!["c".to_owned()],
            ..Snapshot::default()
        }
    );
}

#[cfg(unix)]
#[test]
fn a_pack_names_each_commit_that_the_host_has() {
    let bd = FakeBd::new();
    bd.answer("pack", r#"{"schema":2,"path":"/c/p.pack","bytes":12,"commits":3}"#, 0);
    let pack = with_client(&bd, |client| {
        client.build_pack(Path::new("/c"), "c", &["h1".to_owned(), "m0".to_owned()], &|| false)
    })
    .expect("the pack");
    assert_eq!(
        pack,
        Pack {
            path: PathBuf::from("/c/p.pack"),
            bytes: 12,
            commits: 3
        }
    );
    assert_eq!(bd.args(), ["-C", "/c", "host", "pack", "c", "--have", "h1", "--have", "m0"]);
}

/// The error object of bd names its code. A failed apply is `apply_failed` with the words of git.
#[cfg(unix)]
#[test]
fn an_error_of_bd_refuses_with_its_code() {
    let bd = FakeBd::new();
    bd.answer(
        "snapshot",
        r#"{"schema":2,"error":{"code":"git_error","message":"fatal: bad object HEAD\n","commits":[],"retryable":false}}"#,
        5,
    );
    bd.answer(
        "apply",
        r#"{"schema":2,"error":{"code":"git_error","message":"error: patch failed: a.txt:1","commits":[],"retryable":false}}"#,
        5,
    );
    let (snapshot, applied) = with_client(&bd, |client| {
        (
            client.snapshot(Path::new("/c"), Scan::Head, NEAR),
            client.apply_patch(Path::new("/c"), Path::new("/c/.git/yonder/1-1.patch")),
        )
    });
    let refusal = snapshot.expect_err("refused");
    assert_eq!(
        (refusal.code.as_ref(), refusal.message.as_str()),
        (code::BD_FAILED, "bd host snapshot refused with git_error: fatal: bad object HEAD")
    );
    let refusal = applied.expect_err("refused");
    assert_eq!(
        (refusal.code.as_ref(), refusal.message.as_str()),
        (
            code::APPLY_FAILED,
            "bd host apply refused with git_error: error: patch failed: a.txt:1"
        )
    );
    assert_eq!(
        bd.args()[bd.args().len() - 5..],
        ["-C", "/c", "host", "apply", "/c/.git/yonder/1-1.patch"]
    );

    bd.answer("apply", r#"{"schema":2,"patch":"/c/p","files":3}"#, 0);
    let files = with_client(&bd, |client| client.apply_patch(Path::new("/c"), Path::new("/c/p"))).expect("applied");
    assert_eq!(files, 3);
}

/// A bd that is missing, or one that does not know `bd host`, refuses with `bd_failed` and says what yonder needs.
#[test]
fn a_missing_or_old_bd_refuses_with_bd_failed() {
    let dir = tempfile::tempdir().expect("a directory");
    let missing = dir.path().join("no-bd").to_string_lossy().into_owned();
    let config = Config::new(dir.path().join("config"));
    let client = Client::new(config, &OsProcesses, &|_| {}, &calm).with_bd(&missing);
    let refusal = client.snapshot(Path::new("/c"), Scan::Head, NEAR).expect_err("no bd");
    assert_eq!(refusal.code, code::BD_FAILED);
    assert!(refusal.message.contains("must be on the PATH"), "{}", refusal.message);

    #[cfg(unix)]
    {
        let bd = FakeBd::new();
        bd.answer("snapshot", "error: unrecognized subcommand 'host'", 2);
        let refusal = with_client(&bd, |client| client.snapshot(Path::new("/c"), Scan::Head, NEAR)).expect_err("old bd");
        assert_eq!(refusal.code, code::BD_FAILED);
        assert!(
            refusal.message.starts_with("bd host snapshot failed: exit 2"),
            "{}",
            refusal.message
        );
    }
}
