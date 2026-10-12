use std::fs;
use std::os::unix::fs::{MetadataExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::Command;

use avl_wire::stage::{ArgFileRequest, LaunchPrep, RuntimeManifest, RuntimeResult};
use avl_wire::verb::AgentVerb;
use pretty_assertions::assert_eq;
use sha2::{Digest, Sha256};

use super::runtime::{RuntimeStageState, find_donor, state_path};
use super::*;
use crate::cli::StageCheckArgs;
use crate::testing::{RunningProcess, finished_pid, run_agent};

fn digest_of(letter: char) -> String {
    letter.to_string().repeat(64)
}

/// Whether the host has the `tar` the stager runs; a test that needs it skips with a reason otherwise.
fn tar_available() -> bool {
    let present = Path::new(TAR_BINARY).is_file();
    if !present {
        eprintln!("skipped: {TAR_BINARY} is not on this host");
    }
    present
}

// --- the refusal a staging verb answers --------------------------------------------------------------------------

fn write_runtime_manifest(directory: &Path, manifest: &RuntimeManifest) -> PathBuf {
    let path = directory.join(format!("manifest-{}.json", &manifest.runtime_digest[..4]));
    fs::write(&path, serde_json::to_vec(manifest).unwrap()).unwrap();
    path
}

fn runtime_manifest(digest: &str, sources: Vec<String>, archive: &str) -> RuntimeManifest {
    RuntimeManifest {
        schema_version: avl_wire::stage::SCHEMA_VERSION,
        runtime_digest: digest.to_owned(),
        stable_sources: sources,
        jbr_archive: archive.to_owned(),
        java_home_suffix: JAVA_HOME_SUFFIX.to_owned(),
    }
}

/// A staging refusal carries what this process composed and not what a subprocess printed: the message travels to
/// the host inside the envelope, so a `tar` stream quoted into it would travel too. `tar`'s wording differs per
/// platform, so the test runs the same failing extraction itself and asserts its output is absent.
#[test]
fn a_staging_refusal_does_not_quote_a_subprocesses_output() {
    if !tar_available() {
        return;
    }
    let root = tempfile::tempdir().unwrap();
    let archive = root.path().join("jbr-archive.bin");
    fs::write(&archive, "this is not a gzipped tar archive").unwrap();
    let archive_text = archive.to_string_lossy().into_owned();
    let manifest = write_runtime_manifest(root.path(), &runtime_manifest(&digest_of('a'), vec![], &archive_text));

    let refusal = runtime::stage(&root.path().to_string_lossy(), Some(&manifest), &mut io::empty())
        .unwrap_err()
        .message;
    let prefix = format!("JBR extraction failed for {archive_text}: ");
    assert!(refusal.starts_with(&prefix), "the refusal does not name the archive: {refusal}");
    assert!(!refusal.contains('\n'), "a staging refusal must stay one line: {refusal:?}");

    let elsewhere = root.path().join("elsewhere");
    fs::create_dir_all(&elsewhere).unwrap();
    let printed = Command::new(TAR_BINARY)
        .arg("xzf")
        .arg(&archive)
        .arg("-C")
        .arg(&elsewhere)
        .arg("--strip-components=1")
        .output()
        .unwrap();
    assert!(!printed.status.success(), "the fixture archive extracted; the test proves nothing");
    let quoted = String::from_utf8_lossy(&printed.stderr).trim().to_owned();
    assert!(!quoted.is_empty(), "tar printed nothing; the test proves nothing");
    assert!(!refusal.contains(&quoted), "tar's own output travelled in the refusal: {refusal}");

    // And it is absent from the document that actually crosses to the host.
    let answered = run_agent(&[Path::new("stage"), root.path(), &manifest], b"");
    assert_eq!(answered.exit, 70);
    assert!(
        !answered.stderr.contains(&quoted),
        "tar's own output reached the envelope: {}",
        answered.stderr
    );
}

/// A staging failure answers a structured refusal, which is what lets its message reach the host: the failure
/// envelope `{code: guest_stage_failed, message}` on stderr, exit 70, and nothing on stdout.
#[test]
fn a_staging_failure_answers_a_structured_refusal_its_message_can_travel_in() {
    let root = tempfile::tempdir().unwrap();
    let absent = root.path().join("absent.jar").to_string_lossy().into_owned();
    let manifest = write_runtime_manifest(root.path(), &runtime_manifest(&digest_of('a'), vec![absent.clone()], "/j.tgz"));

    let answered = run_agent(&[Path::new("stage"), root.path(), &manifest], b"");
    assert_eq!(answered.exit, 70);
    assert!(answered.stdout.is_empty(), "{}", answered.stdout);
    let envelope = answered.failure();
    assert_eq!(envelope["command"], "stage");
    // The verb is the one distinction a caller can act on, so the code carries it.
    assert_eq!(envelope["error"]["code"], "guest_stage_failed");
    assert_eq!(
        envelope["error"]["message"],
        format!("stable classpath source is not a file: {absent}"),
        "the staging message did not travel whole"
    );

    // An unreadable and an invalid manifest are the same structured refusal, each naming what is wrong.
    let absent_manifest = root.path().join("absent.json");
    let unreadable = run_agent(&[Path::new("stage"), root.path(), &absent_manifest], b"");
    assert_eq!(unreadable.exit, 70);
    assert_eq!(unreadable.code(), "guest_stage_failed");
    let message = unreadable.failure()["error"]["message"].as_str().unwrap().to_owned();
    let prefix = format!("cannot read the runtime stage manifest {}: ", absent_manifest.display());
    assert!(message.starts_with(&prefix), "{message}");
    let invalid_path = root.path().join("bad-runtime.json");
    fs::write(&invalid_path, "{not json").unwrap();
    let invalid = run_agent(&[Path::new("stage"), root.path(), &invalid_path], b"");
    assert_eq!(invalid.failure()["error"]["message"], "invalid runtime stage manifest");

    // The controller sends the manifest on standard input. A manifest there and as the argument is refused, because
    // the stager cannot know which one was meant, and so is a stage with neither.
    let piped = serde_json::to_vec(&runtime_manifest(&digest_of('a'), vec![absent.clone()], "/j.tgz")).unwrap();
    let from_stdin = run_agent(&[Path::new("stage"), root.path()], &piped);
    assert_eq!(
        from_stdin.failure()["error"]["message"],
        format!("stable classpath source is not a file: {absent}"),
        "the manifest on standard input was not the one staged"
    );
    let both = run_agent(&[Path::new("stage"), root.path(), &manifest], &piped);
    assert_eq!((both.exit, both.code()), (70, "guest_stage_failed".to_owned()));
    assert_eq!(
        both.failure()["error"]["message"],
        format!(
            "a runtime stage manifest arrived on standard input and as the argument {}; pass it once",
            manifest.display()
        )
    );
    let neither = run_agent(&[Path::new("stage"), root.path()], b"");
    assert_eq!(neither.code(), "guest_stage_failed");
    assert_eq!(
        neither.failure()["error"]["message"],
        "no runtime stage manifest: standard input is empty, and no MANIFEST argument names a file"
    );

    for (verb, code) in [
        (AgentVerb::Gc, "guest_gc_failed"),
        (AgentVerb::LaunchPrep, "guest_launch_prep_failed"),
        (AgentVerb::Stage, "guest_stage_failed"),
        (AgentVerb::StageCheck, "guest_stage_check_failed"),
    ] {
        assert_eq!(crate::reply::verb_refusal_code(verb), code);
    }
}

// --- garbage collection ----------------------------------------------------------------------------------------

/// A `gc` against a root with no generations is a success answering an empty removal set, not a failure.
#[test]
fn collect_generations_on_an_empty_root_succeeds() {
    let root = tempfile::tempdir().unwrap();
    let answered = run_agent(&[Path::new("gc"), root.path()], b"");
    assert_eq!(answered.exit, 0, "{}", answered.stderr);
    assert_eq!(answered.document(), serde_json::json!({"removed": []}));
}

/// The two newest generations stay whatever was asked for, the requested ones stay whatever their age, and nothing
/// else survives. Two old requested generations do not push out the newest.
#[test]
fn gc_keeps_the_two_newest_and_every_requested_generation() {
    let root = tempfile::tempdir().unwrap();
    let generations = generations_dir(root.path());
    // Oldest first, with distinct mtimes.
    for (age, letter) in ['a', 'b', 'c', 'd', 'e'].into_iter().enumerate() {
        let generation = generations.join(digest_of(letter));
        fs::create_dir_all(&generation).unwrap();
        let modified = std::time::SystemTime::UNIX_EPOCH + std::time::Duration::from_secs(1_000_000 + age as u64 * 60);
        File::open(&generation).unwrap().set_modified(modified).unwrap();
    }
    fs::create_dir_all(generations.join(".stage-partial")).unwrap();

    // `a` and `b` are the two oldest and were asked for, `f` is not present, and `e` and `d` are the two newest.
    let result = gc::collect(&root.path().to_string_lossy(), &[digest_of('a'), digest_of('b'), digest_of('f')]).unwrap();
    assert_eq!(result.removed, [digest_of('c')]);
    for letter in ['a', 'b', 'd', 'e'] {
        assert!(generations.join(digest_of(letter)).is_dir(), "gc removed {letter}");
    }
    assert!(
        generations.join(".stage-partial").is_dir(),
        "gc removed a directory whose name records no pid"
    );

    // With nothing asked for, only the two newest stay.
    let second = gc::collect(&root.path().to_string_lossy(), &[]).unwrap();
    assert_eq!(second.removed, [digest_of('b'), digest_of('a')]);
    assert!(generations.join(digest_of('d')).is_dir() && generations.join(digest_of('e')).is_dir());
}

/// A killed stage leaves its staging tree, and its pid is the only owner record. `gc` removes the tree of a pid
/// that no longer runs and keeps the tree of a running one, which can be a stage in progress.
#[test]
fn gc_removes_the_staging_tree_of_a_stage_that_no_longer_runs() {
    let root = tempfile::tempdir().unwrap();
    let generations = generations_dir(root.path());
    let kept = generations.join(digest_of('a'));
    fs::create_dir_all(&kept).unwrap();
    let running = RunningProcess::start();
    let abandoned = format!(".stage-{}-{}", digest_of('b'), finished_pid());
    let in_progress = format!(".stage-{}-{}", digest_of('c'), running.pid());
    for name in [&abandoned, &in_progress] {
        fs::create_dir_all(generations.join(name).join("lib")).unwrap();
        fs::write(generations.join(name).join("lib/0000-util.jar"), "staged bytes").unwrap();
    }

    let answered = run_agent(&[Path::new("gc"), root.path()], b"");
    assert_eq!(answered.exit, 0, "{}", answered.stderr);
    assert_eq!(answered.document(), serde_json::json!({"removed": [abandoned]}));
    assert!(!generations.join(&abandoned).exists(), "the tree of a finished stage survived");
    assert!(generations.join(&in_progress).is_dir(), "gc removed the tree of a running stage");
    assert!(kept.is_dir());
}

// --- a failed stage --------------------------------------------------------------------------------------------

/// The staging tree can hold 852 MiB, so every refusal after its creation removes it, and the next stage of the
/// same digest by another pid does not find a stranger's tree.
#[test]
fn a_failed_stage_removes_its_staging_tree() {
    let root = tempfile::tempdir().unwrap();
    let runtime_root = root.path().join("daemon-runtime");
    let jar = root.path().join("util.jar");
    fs::write(&jar, "util bytes").unwrap();
    let jar = jar.to_string_lossy().into_owned();
    let absent = root.path().join("absent.jar").to_string_lossy().into_owned();
    let not_an_archive = root.path().join("not-an-archive.tgz");
    fs::write(&not_an_archive, "this is not a gzipped tar archive").unwrap();
    let not_an_archive = not_an_archive.to_string_lossy().into_owned();
    let mut cases = vec![
        ("a stable source is not a file", vec![jar.clone(), absent], "/j.tgz".to_owned()),
        (
            "the JBR archive is not a file",
            vec![jar.clone()],
            root.path().join("absent.tgz").to_string_lossy().into_owned(),
        ),
    ];
    if tar_available() {
        cases.push(("the JBR extraction failed", vec![jar], not_an_archive));
    }
    for (index, (name, sources, archive)) in cases.into_iter().enumerate() {
        let digest = digest_of(['a', 'b', 'c'][index]);
        let manifest = write_runtime_manifest(root.path(), &runtime_manifest(&digest, sources, &archive));
        runtime::stage(&runtime_root.to_string_lossy(), Some(&manifest), &mut io::empty()).expect_err(name);
        let left: Vec<_> = fs::read_dir(generations_dir(&runtime_root))
            .unwrap()
            .map(|entry| entry.unwrap().file_name())
            .collect();
        assert!(left.is_empty(), "{name}: the failed stage left {left:?}");
    }
}

/// A refusal names the file that failed, so the host reads more than "Is a directory (os error 21)".
#[test]
fn a_failed_record_write_names_the_file() {
    let root = tempfile::tempdir().unwrap();
    let state = RuntimeStageState::default();
    let classpath = root.path().join(avl_wire::stage::CLASSPATH_FILE);
    fs::create_dir_all(&classpath).unwrap();
    let refusal = runtime::write_records(root.path(), &[], &state).unwrap_err().message;
    assert!(
        refusal.starts_with(&format!("cannot write {}: ", classpath.display())),
        "the refusal does not name the classpath file: {refusal}"
    );

    fs::remove_dir(&classpath).unwrap();
    let marker = state_path(root.path());
    fs::create_dir_all(&marker).unwrap();
    let refusal = runtime::write_records(root.path(), &[], &state).unwrap_err().message;
    assert!(
        refusal.starts_with(&format!("cannot write {}: ", marker.display())),
        "the refusal does not name the marker: {refusal}"
    );
}

// --- preparing one daemon launch -------------------------------------------------------------------------------

/// Writes one generation's staged jars and the `classpath.txt` recording them the way `stage` writes it, and
/// answers the runtime root and that recorded classpath.
fn seed_staged_generation(root: &Path, names: &[&str]) -> (PathBuf, Vec<String>) {
    let runtime_root = root.join("daemon-runtime");
    let generation = generations_dir(&runtime_root).join(digest_of('b'));
    fs::create_dir_all(generation.join("lib")).unwrap();
    let classpath: Vec<String> = names
        .iter()
        .map(|name| {
            let jar = generation.join("lib").join(name);
            fs::write(&jar, format!("jar {name}")).unwrap();
            jar.to_string_lossy().into_owned()
        })
        .collect();
    fs::write(generation.join("classpath.txt"), classpath.join("\n") + "\n").unwrap();
    (runtime_root, classpath)
}

fn launch_request(destination: &Path, entries: u32) -> LaunchPrep {
    LaunchPrep {
        schema_version: avl_wire::stage::SCHEMA_VERSION,
        runtime_digest: digest_of('b'),
        stable_count: entries,
        directories: vec![],
        remove_files: vec![],
        arg_file: ArgFileRequest {
            destination: destination.to_string_lossy().into_owned(),
            prefix: vec![
                "-Djava.io.tmpdir=/g/tmp dir".into(),
                "-Xmx4g".into(),
                r#"-Dair.ui.probe=a\b"c"#.into(),
            ],
            main_class: "com.example.Main".into(),
            sha256: digest_of('c'),
        },
    }
}

fn prepare(runtime_root: &Path, request: &LaunchPrep) -> Result<avl_wire::stage::LaunchPrepResult, AgentRefusal> {
    launch_prep::prepare(&runtime_root.to_string_lossy(), request)
}

/// The whole point of the verb: the ~1000 classpath paths never cross the exec channel, so the file the JVM reads
/// is assembled here from the list this guest recorded when it staged the generation - the controller's prefix,
/// then `-cp`, the joined list, and the main class. The request arrives on stdin.
#[test]
fn launch_prep_assembles_the_argfile_from_its_own_classpath() {
    let root = tempfile::tempdir().unwrap();
    let (runtime_root, classpath) = seed_staged_generation(root.path(), &["0000-util.jar", "0001-core.jar"]);
    let destination = root.path().join("daemon-jvm.args");
    let request = launch_request(&destination, 2);

    let answered = run_agent(&[Path::new("launch-prep"), &runtime_root], &serde_json::to_vec(&request).unwrap());
    assert_eq!(answered.exit, 0, "{}", answered.stderr);
    let mut tokens = request.arg_file.prefix;
    tokens.extend(["-cp".to_owned(), classpath.join(":"), "com.example.Main".to_owned()]);
    let want = avl_wire::stage::arg_file_text(&tokens);
    assert_eq!(fs::read_to_string(&destination).unwrap(), want);
    let reply = answered.document();
    assert_eq!(reply["argFile"], destination.to_string_lossy().as_ref());
    assert_eq!(reply["entries"], 2);
    assert_eq!(reply["bytes"], want.len());
    assert_eq!(
        reply["sha256"],
        hex::encode(Sha256::digest(want.as_bytes())),
        "the digest is not over the bytes"
    );
}

/// The count is what stands between "the generation this controller staged" and "whatever is in that directory
/// now". A generation restaged by another controller, or one whose `classpath.txt` lost a line, is refused rather
/// than launched with a short classpath, which boots and then reads as a test failure.
#[test]
fn launch_prep_refuses_a_generation_whose_classpath_count_disagrees() {
    let root = tempfile::tempdir().unwrap();
    let (runtime_root, _) = seed_staged_generation(root.path(), &["0000-util.jar", "0001-core.jar"]);
    let destination = root.path().join("daemon-jvm.args");

    let refusal = prepare(&runtime_root, &launch_request(&destination, 3)).unwrap_err();
    assert!(
        refusal.message.contains("has 2 entries, expected 3"),
        "the refusal must name both counts: {refusal}"
    );
    assert!(
        !destination.exists(),
        "nothing may be published over a generation that failed its count check"
    );

    // A generation that was never staged is the same refusal rather than an empty classpath.
    let mut absent = launch_request(&destination, 2);
    absent.runtime_digest = digest_of('d');
    assert!(
        prepare(&runtime_root, &absent).is_err(),
        "a generation that was never staged was accepted"
    );
}

/// The two round-trips this verb absorbed: the directories a run writes into, and the previous daemon's state file,
/// whose absence is the ordinary case on a worker's first start.
#[test]
fn launch_prep_creates_its_directories_and_removes_the_stale_state() {
    let root = tempfile::tempdir().unwrap();
    let (runtime_root, _) = seed_staged_generation(root.path(), &["0000-util.jar"]);
    let state_dir = root.path().join("daemon");
    let outputs = root.path().join("tmp/run-1/outputs");
    let stale = state_dir.join("daemon.json");
    fs::create_dir_all(&state_dir).unwrap();
    fs::write(&stale, r#"{"port":1}"#).unwrap();

    let mut request = launch_request(&state_dir.join("daemon-jvm.args"), 1);
    request.directories = vec![state_dir.to_string_lossy().into(), outputs.to_string_lossy().into()];
    request.remove_files = vec![
        stale.to_string_lossy().into(),
        state_dir.join("never-existed.json").to_string_lossy().into(),
    ];
    prepare(&runtime_root, &request).unwrap();

    let info = fs::metadata(&outputs).unwrap();
    assert!(info.is_dir());
    assert_eq!(info.permissions().mode() & 0o777, 0o700);
    assert!(!stale.exists(), "the previous daemon's state file survived");
}

/// Published with a rename, so the JVM started next reads a whole @-file or none. An in-place rewrite would keep
/// the destination's 0644, and a publish that left its temporary behind would leave two files instead of one.
#[test]
fn launch_prep_publishes_the_argfile_atomically() {
    let root = tempfile::tempdir().unwrap();
    let (runtime_root, _) = seed_staged_generation(root.path(), &["0000-util.jar"]);
    let published = root.path().join("publish");
    fs::create_dir_all(&published).unwrap();
    let destination = published.join("daemon-jvm.args");
    fs::write(&destination, "the previous launch\n").unwrap();
    fs::set_permissions(&destination, fs::Permissions::from_mode(0o644)).unwrap();
    let inode = fs::metadata(&destination).unwrap().ino();

    prepare(&runtime_root, &launch_request(&destination, 1)).unwrap();
    assert!(!fs::read_to_string(&destination).unwrap().contains("previous launch"));
    let info = fs::metadata(&destination).unwrap();
    assert_eq!(
        info.permissions().mode() & 0o777,
        0o600,
        "the prefix carries the launch's properties"
    );
    assert_ne!(
        info.ino(),
        inode,
        "the previous @-file was written over in place rather than replaced"
    );
    assert_eq!(fs::read_dir(&published).unwrap().count(), 1, "the publish left debris");

    // A destination nothing can be renamed onto fails, and still leaves no temporary behind.
    let blocked = published.join("occupied");
    fs::create_dir_all(blocked.join("child")).unwrap();
    prepare(&runtime_root, &launch_request(&blocked, 1)).unwrap_err();
    assert_eq!(fs::read_dir(&published).unwrap().count(), 2, "a failed publish left debris");
}

// --- linking one generation from the last ----------------------------------------------------------------------

const JAVA_HOME_SUFFIX: &str = "jdk";

/// Builds the one tar.gz `stage` refuses to stage without, so a test drives the whole verb.
fn seed_jbr_archive(directory: &Path) -> String {
    let payload = directory.join("jbr-payload");
    let bin = payload.join("top").join(JAVA_HOME_SUFFIX).join("bin");
    fs::create_dir_all(&bin).unwrap();
    fs::write(bin.join("java"), "#!/bin/sh\n").unwrap();
    let archive = directory.join("jbr.tgz");
    let built = Command::new(TAR_BINARY)
        .arg("czf")
        .arg(&archive)
        .arg("-C")
        .arg(&payload)
        .arg("top")
        .output()
        .unwrap();
    assert!(built.status.success(), "cannot build the fixture JBR archive: {built:?}");
    archive.to_string_lossy().into_owned()
}

fn stage_sources(runtime_root: &Path, work: &Path, digest: &str, archive: &str, sources: &[&Path]) -> RuntimeResult {
    let sources = sources.iter().map(|source| source.to_string_lossy().into_owned()).collect();
    let manifest = write_runtime_manifest(work, &runtime_manifest(digest, sources, archive));
    runtime::stage(&runtime_root.to_string_lossy(), Some(&manifest), &mut io::empty())
        .unwrap_or_else(|refusal| panic!("staging {} was refused: {refusal}", &digest[..12]))
}

fn same_file(left: &str, right: &str) -> bool {
    let (left, right) = (fs::metadata(left).unwrap(), fs::metadata(right).unwrap());
    (left.dev(), left.ino()) == (right.dev(), right.ino())
}

/// The cold path: a jar the retained generation already holds is linked, and a jar whose bytes moved is copied.
/// The changed jar keeps *the same length*, because the size is only a filter: this is the case where nothing
/// but the content hash tells the two files apart.
#[test]
fn an_unchanged_jar_is_linked_and_a_changed_jar_of_the_same_length_is_copied() {
    if !tar_available() {
        return;
    }
    let root = tempfile::tempdir().unwrap();
    let runtime_root = root.path().join("daemon-runtime");
    let archive = seed_jbr_archive(root.path());
    let share = root.path().join("share");
    fs::create_dir_all(&share).unwrap();
    let (stable, moving) = (share.join("util.jar"), share.join("core.jar"));
    fs::write(&stable, "the platform, unchanged").unwrap();
    fs::write(&moving, "the core, first build ").unwrap();

    let first = stage_sources(&runtime_root, root.path(), &digest_of('a'), &archive, &[&stable, &moving]);
    assert!(!first.reused && first.classpath.len() == 2, "{first:?}");

    let rewritten = "the core, second build";
    assert_eq!(rewritten.len(), "the core, first build ".len());
    fs::write(&moving, rewritten).unwrap();
    let second = stage_sources(&runtime_root, root.path(), &digest_of('b'), &archive, &[&stable, &moving]);
    assert!(!second.reused && second.root != first.root, "{second:?}");

    assert!(
        same_file(&first.classpath[0], &second.classpath[0]),
        "an unchanged jar was copied again"
    );
    assert!(
        !same_file(&first.classpath[1], &second.classpath[1]),
        "a changed jar was linked to old bytes"
    );
    assert_eq!(fs::read_to_string(&second.classpath[1]).unwrap(), rewritten);

    // The record the next generation links against covers every staged jar, and names its bytes.
    let state = runtime::read_state(Path::new(&second.root)).unwrap();
    let entries = state.entries.unwrap();
    assert_eq!(entries.len(), 2);
    for (name, recorded) in entries {
        let want = sha256_file(&Path::new(&second.root).join("lib").join(&name)).unwrap();
        assert_eq!(recorded, want, "{name}");
    }
}

/// A donor is chosen by content and never by the staged name, which carries the classpath index: reordering the
/// stable tier renames every jar without changing a byte of it.
#[test]
fn a_reordered_classpath_links_by_content_rather_than_by_staged_name() {
    if !tar_available() {
        return;
    }
    let root = tempfile::tempdir().unwrap();
    let runtime_root = root.path().join("daemon-runtime");
    let archive = seed_jbr_archive(root.path());
    let share = root.path().join("share");
    fs::create_dir_all(&share).unwrap();
    let (util, core) = (share.join("util.jar"), share.join("core.jar"));
    fs::write(&util, "util bytes").unwrap();
    fs::write(&core, "core bytes").unwrap();

    let before = stage_sources(&runtime_root, root.path(), &digest_of('a'), &archive, &[&util, &core]);
    let after = stage_sources(&runtime_root, root.path(), &digest_of('b'), &archive, &[&core, &util]);

    assert!(after.classpath[0].ends_with("/0000-core.jar"), "{}", after.classpath[0]);
    assert!(
        same_file(&before.classpath[1], &after.classpath[0]),
        "the reordered jar was copied again"
    );
    assert!(
        same_file(&before.classpath[0], &after.classpath[1]),
        "the reordered jar was copied again"
    );
    for (index, source) in [&core, &util].into_iter().enumerate() {
        assert_eq!(fs::read(&after.classpath[index]).unwrap(), fs::read(source).unwrap());
    }
}

/// A generation staged before the content record existed is not a donor, so an upgrade copies once and links from
/// then on - and it is still reused, so a worker holding two such generations does not restage 852 MiB.
#[test]
fn a_generation_without_the_content_record_is_not_a_donor_and_is_still_reused() {
    if !tar_available() {
        return;
    }
    let root = tempfile::tempdir().unwrap();
    let runtime_root = root.path().join("daemon-runtime");
    let archive = seed_jbr_archive(root.path());
    let jar = root.path().join("util.jar");
    fs::write(&jar, "util bytes").unwrap();
    let first = stage_sources(&runtime_root, root.path(), &digest_of('a'), &archive, &[&jar]);

    // The marker an older stager wrote: everything the reuse checks read, and no `entries`.
    write_json_file(
        &state_path(Path::new(&first.root)),
        &RuntimeStageState {
            schema_version: avl_wire::stage::SCHEMA_VERSION,
            runtime_digest: digest_of('a'),
            stable_count: 1,
            entries: None,
            staged_at: crate::clock::now_stamp(),
        },
    )
    .unwrap();
    // The key is present and null.
    let marker = fs::read_to_string(state_path(Path::new(&first.root))).unwrap();
    assert!(marker.contains(r#""entries":null"#), "{marker}");
    assert!(
        find_donor(&runtime_root, &digest_of('b')).is_none(),
        "a generation without a record is a donor"
    );
    let second = stage_sources(&runtime_root, root.path(), &digest_of('b'), &archive, &[&jar]);
    assert!(
        !same_file(&first.classpath[0], &second.classpath[0]),
        "a jar was linked from unrecorded bytes"
    );

    let again = stage_sources(&runtime_root, root.path(), &digest_of('a'), &archive, &[&jar]);
    assert!(
        again.reused && again.root == first.root,
        "a generation with no content record was restaged: {again:?}"
    );
}

/// `stage-check` answers `complete` only for a generation whose recorded classpath is the one the caller remembers,
/// and never sends the list back.
#[test]
fn stage_check_reuses_only_the_classpath_the_caller_remembers() {
    if !tar_available() {
        return;
    }
    let root = tempfile::tempdir().unwrap();
    let runtime_root = root.path().join("daemon-runtime");
    let archive = seed_jbr_archive(root.path());
    let jar = root.path().join("util.jar");
    fs::write(&jar, "util bytes").unwrap();
    let staged = stage_sources(&runtime_root, root.path(), &digest_of('a'), &archive, &[&jar]);
    let remembered = hex::encode(Sha256::digest(fs::read(&staged.classpath_file).unwrap()));
    let check = |count: &str, sha: &str| {
        runtime::check(&StageCheckArgs {
            runtime_root: runtime_root.to_string_lossy().into_owned(),
            digest: digest_of('a'),
            stable_count: count.to_owned(),
            java_home_suffix: JAVA_HOME_SUFFIX.to_owned(),
            classpath_sha256: sha.to_owned(),
        })
    };

    let reuse = check("1", &remembered).unwrap();
    assert!(reuse.complete);
    assert_eq!(reuse.root.as_deref(), Some(staged.root.as_str()));
    assert_eq!(reuse.java_binary.as_deref(), Some(staged.java_binary.as_str()));
    assert!(
        !check("1", &digest_of('e')).unwrap().complete,
        "a different remembered classpath was reused"
    );
    assert!(!check("2", &remembered).unwrap().complete, "a different count was reused");
    assert!(check("many", &remembered).unwrap_err().message.contains("not a count"));
    assert!(check("1", "short").unwrap_err().message.contains("not a sha256"));
}
