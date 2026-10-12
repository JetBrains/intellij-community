use std::path::{Path, PathBuf};

use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

const GUEST_JBR_PLATFORM: &str = "linux-x64";
const DESCRIPTOR_PATH: &str = "/out/bin/plugins/air/ui_daemon.runtime.json";

fn staged<const N: usize>(paths: [&str; N]) -> Vec<String> {
    paths.map(str::to_owned).to_vec()
}

fn file(logical_path: &str, owner: &str) -> Value {
    json!({
        "execPath": format!("bazel-out/k8-fastbuild/bin/{logical_path}"),
        "logicalPath": logical_path,
        "owner": owner,
    })
}

/// The descriptor every refusal below is one mutation away from. A value rather than a literal, so a case can
/// *delete* a field: a missing field and an empty one are different mistakes, and only the first is what an older
/// Bazel rule actually emits.
fn valid_descriptor() -> Value {
    json!({
        "schemaVersion": 5,
        "kind": "air-ui-daemon-runtime",
        "mainClass": "com.intellij.air.uiDaemon.AirUiDaemonMain",
        "staticJvmFlags": ["-Xmx4g", "-Dsun.io.useCanonCaches=false", "-Dair.ui.runfiles=${RUNFILES_ROOT}/_main"],
        "classpath": {
            "hot": [file("_main/plugins/air/ui-tests.jar", "//plugins/air/tests/integration/ui:ui_tests")],
            "stable": [file("_main/platform/util.jar", "//platform/util"), file("_main/platform/core.jar", "//platform/core")],
        },
        "devDist": {
            "config": file("_main/dist/ultimate.ide.config", "//build:dev_config"),
            "fingerprint": file("_main/dist/fingerprint.txt", "//build:dev_fingerprint"),
            "home": file("_main/dist/home", "//build:dev_home"),
        },
        "jbr": {
            "archive": file("_main/jbr/jbr-linux-x64.tar.gz", "//jbr:linux_x64"),
            "javaHomeSuffix": "",
            "manifest": file("_main/jbr/manifest.json", "//jbr:manifest"),
            "platform": "linux-x64",
            "preloadedOnly": false,
        },
        "data": [file("_main/plugins/air/testData/project.zip", "//plugins/air:test_data")],
    })
}

/// Sets the field at a JSON pointer. A pointer that names nothing panics: a silent no-op would leave the case
/// asserting a refusal against the *valid* document.
fn set(mut descriptor: Value, pointer: &str, value: Value) -> Value {
    *descriptor.pointer_mut(pointer).unwrap_or_else(|| panic!("no {pointer}")) = value;
    descriptor
}

fn drop_field(mut descriptor: Value, pointer: &str) -> Value {
    let (parent, name) = pointer.rsplit_once('/').unwrap();
    let removed = descriptor
        .pointer_mut(parent)
        .and_then(Value::as_object_mut)
        .and_then(|object| object.remove(name));
    assert!(removed.is_some(), "no {pointer}");
    descriptor
}

fn parse_bytes(content: &[u8]) -> Result<RuntimeDescriptor, DescriptorError> {
    parse_runtime_descriptor(content, Path::new(DESCRIPTOR_PATH), GUEST_JBR_PLATFORM)
}

fn parse(descriptor: &Value) -> RuntimeDescriptor {
    parse_bytes(&serde_json::to_vec(descriptor).unwrap()).unwrap_or_else(|refusal| panic!("the descriptor was refused: {refusal:?}"))
}

#[test]
fn a_well_formed_descriptor_parses_into_the_launch_contract() {
    let parsed = parse(&valid_descriptor());
    assert_eq!((parsed.schema_version, parsed.kind.as_str()), (5, DESCRIPTOR_KIND));
    assert_eq!(parsed.main_class, "com.intellij.air.uiDaemon.AirUiDaemonMain");
    assert_eq!((parsed.classpath.hot.len(), parsed.classpath.stable.len()), (1, 2));
    // The stable tier keeps its order: a classpath is ordered.
    assert_eq!(
        parsed.classpath.stable[0],
        RuntimeFile {
            exec_path: "bazel-out/k8-fastbuild/bin/_main/platform/util.jar".to_owned(),
            logical_path: "_main/platform/util.jar".to_owned(),
            owner: "//platform/util".to_owned(),
        }
    );
    assert_eq!(parsed.jbr.platform, GUEST_JBR_PLATFORM);
    assert!(!parsed.jbr.preloaded_only);
    // An empty Java-home suffix is legal - it is what a Linux JBR declares - so the check on it is a type check
    // and not an emptiness one.
    assert_eq!(parsed.jbr.java_home_suffix, "");
    assert_eq!(parsed.data.len(), 1);
    assert_eq!(parsed.dev_dist.home.logical_path, "_main/dist/home");
    assert_eq!(parsed.jbr.manifest.logical_path, "_main/jbr/manifest.json");
}

/// The refusal corpus: every way the Bazel-owned contract can be wrong, the code a caller branches on, and a
/// fragment of the message naming what is wrong.
fn corpus() -> Vec<(&'static str, Vec<u8>, &'static str, &'static [&'static str])> {
    let valid = valid_descriptor;
    let bytes = |descriptor: Value| serde_json::to_vec(&descriptor).unwrap();
    vec![
        (
            "a truncated write is not JSON at all",
            br#"{"schemaVersion":5,"#.to_vec(),
            code::NOT_JSON,
            &["not JSON"],
        ),
        // Valid JSON of the wrong shape has no schema version.
        (
            "a JSON array has no schema version",
            b"[]".to_vec(),
            code::SCHEMA_VERSION,
            &["schemaVersion is absent, expected 5"],
        ),
        (
            "an older rule emits no schema version",
            bytes(drop_field(valid(), "/schemaVersion")),
            code::SCHEMA_VERSION,
            &["schemaVersion is absent"],
        ),
        (
            "a schema version this controller predates",
            bytes(set(valid(), "/schemaVersion", json!(6))),
            code::SCHEMA_VERSION,
            &["schemaVersion is 6, expected 5"],
        ),
        (
            "the schema version arrives as text",
            bytes(set(valid(), "/schemaVersion", json!("5"))),
            code::SCHEMA_VERSION,
            &[r#"schemaVersion is "5""#],
        ),
        (
            "some other document at this label's output path",
            bytes(set(valid(), "/kind", json!("air-ui-daemon"))),
            code::KIND,
            &[r#"kind is "air-ui-daemon""#],
        ),
        (
            "a document that does not say what it is",
            bytes(drop_field(valid(), "/kind")),
            code::KIND,
            &["kind is absent"],
        ),
        (
            "no main class",
            bytes(drop_field(valid(), "/mainClass")),
            code::FIELD_INVALID,
            &["mainClass"],
        ),
        (
            "an empty main class, which would exec a JVM with no argv tail",
            bytes(set(valid(), "/mainClass", json!(""))),
            code::FIELD_INVALID,
            &["mainClass", "empty"],
        ),
        (
            "flags that are not a list",
            bytes(set(valid(), "/staticJvmFlags", json!({}))),
            code::FIELD_INVALID,
            &["staticJvmFlags"],
        ),
        (
            "a flag that is a number",
            bytes(set(valid(), "/staticJvmFlags", json!(["-Xmx4g", 1]))),
            code::FIELD_INVALID,
            &["staticJvmFlags[1]"],
        ),
        // A null element must not become an empty token in an argv.
        (
            "a flag that is null",
            bytes(set(valid(), "/staticJvmFlags", json!([null]))),
            code::FIELD_INVALID,
            &["staticJvmFlags[0]"],
        ),
        (
            "no classpath section",
            bytes(drop_field(valid(), "/classpath")),
            code::FIELD_INVALID,
            &["classpath"],
        ),
        (
            "a null devDist section",
            bytes(set(valid(), "/devDist", Value::Null)),
            code::FIELD_INVALID,
            &["devDist"],
        ),
        (
            "a jbr section that is a number",
            bytes(set(valid(), "/jbr", json!(0))),
            code::FIELD_INVALID,
            &["jbr"],
        ),
        // The same rule one level up: a section is an object, never three files taken positionally.
        (
            "a devDist section that is an array of its files",
            bytes({
                let descriptor = valid();
                let files = json!([
                    descriptor["devDist"]["config"],
                    descriptor["devDist"]["fingerprint"],
                    descriptor["devDist"]["home"],
                ]);
                set(descriptor, "/devDist", files)
            }),
            code::FIELD_INVALID,
            &["devDist"],
        ),
        (
            "a hot tier that is not there",
            bytes(drop_field(valid(), "/classpath/hot")),
            code::FIELD_INVALID,
            &["classpath", "hot"],
        ),
        // "No hot tier" must not read as "an empty hot tier", which is the other code.
        (
            "a hot tier that is null",
            bytes(set(valid(), "/classpath/hot", Value::Null)),
            code::FIELD_INVALID,
            &["classpath.hot"],
        ),
        (
            "a classpath entry that is a bare string",
            bytes(set(valid(), "/classpath/hot/0", json!("util.jar"))),
            code::FIELD_INVALID,
            &["classpath.hot[0]"],
        ),
        // A derived decoder would take three strings positionally as a file.
        (
            "a classpath entry that is an array",
            bytes(set(valid(), "/classpath/hot/0", json!(["a", "b", "c"]))),
            code::FIELD_INVALID,
            &["classpath.hot[0]"],
        ),
        (
            "a classpath entry with no exec path",
            bytes(set(valid(), "/classpath/hot/0/execPath", json!(""))),
            code::FIELD_INVALID,
            &["classpath.hot[0].execPath"],
        ),
        // The owner is only read to name a rule in a message, and it is still required: a refusal that cannot say
        // which rule produced the bad file is a refusal nobody can act on.
        (
            "a classpath entry with no owning label",
            bytes(drop_field(valid(), "/classpath/stable/1/owner")),
            code::FIELD_INVALID,
            &["classpath.stable[1]", "owner"],
        ),
        (
            "an absolute logical path",
            bytes(set(valid(), "/classpath/hot/0/logicalPath", json!("/etc/passwd"))),
            code::PATH_ESCAPES,
            &["classpath.hot[0].logicalPath escapes the runfiles root"],
        ),
        (
            "a logical path that climbs out of the tree",
            bytes(set(valid(), "/data/0/logicalPath", json!("_main/../../../etc/passwd"))),
            code::PATH_ESCAPES,
            &["data[0].logicalPath escapes the runfiles root"],
        ),
        (
            "an empty hot tier, which boots a JVM with no test code on it",
            bytes(set(valid(), "/classpath/hot", json!([]))),
            code::TIER_EMPTY,
            &["both classpath tiers must be non-empty"],
        ),
        (
            "an empty stable tier",
            bytes(set(valid(), "/classpath/stable", json!([]))),
            code::TIER_EMPTY,
            &["both classpath tiers must be non-empty"],
        ),
        (
            "no JBR platform",
            bytes(drop_field(valid(), "/jbr/platform")),
            code::FIELD_INVALID,
            &["jbr", "platform"],
        ),
        // The field whose absence would let a guest with no network try to download a runtime.
        (
            "no preload policy",
            bytes(drop_field(valid(), "/jbr/preloadedOnly")),
            code::FIELD_INVALID,
            &["jbr", "preloadedOnly"],
        ),
        (
            "a preload policy spelled as text",
            bytes(set(valid(), "/jbr/preloadedOnly", json!("false"))),
            code::FIELD_INVALID,
            &["jbr.preloadedOnly"],
        ),
        // Keeps a darwin distribution off a Linux worker, where its `Contents/Home` suffix exists inside the
        // tarball and staging therefore succeeds.
        (
            "a descriptor built for another guest",
            bytes(set(valid(), "/jbr/platform", json!("osx-aarch64"))),
            code::PLATFORM_MISMATCH,
            &["declares a osx-aarch64 JBR and distribution, but this worker runs a linux-x64 guest"],
        ),
        (
            "no data list at all",
            bytes(drop_field(valid(), "/data")),
            code::FIELD_INVALID,
            &["data"],
        ),
        (
            "no dev-dist config",
            bytes(drop_field(valid(), "/devDist/config")),
            code::FIELD_INVALID,
            &["devDist", "config"],
        ),
        (
            "no JBR archive",
            bytes(drop_field(valid(), "/jbr/archive")),
            code::FIELD_INVALID,
            &["jbr", "archive"],
        ),
        (
            "a JBR manifest with an empty logical path",
            bytes(set(valid(), "/jbr/manifest/logicalPath", json!(""))),
            code::FIELD_INVALID,
            &["jbr.manifest.logicalPath"],
        ),
        // Two *different* files claiming one staged name: the stage materializes whichever it reaches last.
        (
            "two files claiming one staged name",
            bytes(set(valid(), "/data/0/logicalPath", json!("_main/platform/util.jar"))),
            code::DUPLICATE_LOGICAL_PATH,
            &["duplicate logical path _main/platform/util.jar"],
        ),
    ]
}

#[test]
fn every_way_the_descriptor_can_be_wrong_is_its_own_refusal() {
    for (name, document, code, fragments) in corpus() {
        let refusal = match parse_bytes(&document) {
            Ok(parsed) => panic!("{name}: the descriptor was accepted: {parsed:?}"),
            Err(refusal) => refusal,
        };
        assert_eq!(refusal.code, code, "{name}: {}", refusal.message);
        for fragment in fragments {
            assert!(
                refusal.message.contains(fragment),
                "{name}: the message {:?} does not name {fragment:?}",
                refusal.message
            );
        }
        if code != code::PLATFORM_MISMATCH {
            assert!(
                refusal.message.contains(LABEL),
                "{name}: the message names no label to rebuild: {}",
                refusal.message
            );
        }
    }
}

/// The cases a stricter-looking validator would have refused, and which are legal.
#[test]
fn the_validator_refuses_nothing_it_should_accept() {
    // A float that is the same number in JSON.
    parse(&set(valid_descriptor(), "/schemaVersion", json!(5.0)));
    // An empty data list is a build that mounts nothing.
    assert!(parse(&set(valid_descriptor(), "/data", json!([]))).data.is_empty());
    // A jar whose name contains dots but climbs nowhere.
    parse(&set(
        valid_descriptor(),
        "/data/0/logicalPath",
        json!("_main/data/foo..bar/x..y.zip"),
    ));
    // A field a newer rule added is not this controller's business.
    let mut newer = valid_descriptor();
    newer["jbr"]["checksum"] = json!("sha256:0");
    parse(&newer);
}

#[test]
fn the_runfiles_root_is_derived_from_the_descriptor_path() {
    let root = runfiles_root(Path::new(DESCRIPTOR_PATH));
    assert_eq!(root, PathBuf::from(format!("{DESCRIPTOR_PATH}.runfiles")));
    let file = RuntimeFile {
        exec_path: "x".to_owned(),
        logical_path: "_main/platform/util.jar".to_owned(),
        owner: "//x".to_owned(),
    };
    assert_eq!(
        runtime_file_path(&root, &file),
        root.join("_main").join("platform").join("util.jar")
    );
}

// --- the daemon @-file ----------------------------------------------------------------------------------------

#[test]
fn the_daemon_arg_file_is_the_exact_bytes_the_jvm_reads() {
    let descriptor = parse(&valid_descriptor());
    // A tmpdir with a space in it, because the controller's own runtime root on macOS lives under
    // `Library/Application Support` and an unquoted @-file token would split there.
    let content = daemon_launch_arg_file(
        &descriptor,
        Path::new("/out/rf"),
        &staged(["/g/util.jar", "/g/core.jar"]),
        &LaunchOptions {
            test_tmp_dir: "/g/tmp dir".to_owned(),
            extra_flags: vec!["-Dair.ui.machine=air-linux-1".to_owned()],
        },
    )
    .unwrap();
    let want = [
        r#""-Djava.io.tmpdir=/g/tmp dir""#,
        r#""-Xmx4g""#,
        r#""-Dsun.io.useCanonCaches=false""#,
        r#""-Dair.ui.runfiles=/out/rf/_main""#,
        r#""-Dair.ui.machine=air-linux-1""#,
        r#""-cp""#,
        r#""/g/util.jar:/g/core.jar""#,
        r#""com.intellij.air.uiDaemon.AirUiDaemonMain""#,
    ]
    .join("\n");
    assert_eq!(content, want);
}

#[test]
fn arg_file_tokens_are_escaped_rather_than_trusted() {
    let descriptor = parse(&valid_descriptor());
    let content = daemon_launch_arg_file(
        &descriptor,
        Path::new("/out/rf"),
        &staged(["/g/a.jar", "/g/b.jar"]),
        &LaunchOptions {
            test_tmp_dir: r#"/g/a\b"c"#.to_owned(),
            extra_flags: Vec::new(),
        },
    )
    .unwrap();
    assert_eq!(content.split('\n').next().unwrap(), r#""-Djava.io.tmpdir=/g/a\\b\"c""#);
}

#[test]
fn a_staged_classpath_of_the_wrong_length_is_refused() {
    let descriptor = parse(&valid_descriptor());
    let refusal = daemon_launch_arg_file(
        &descriptor,
        Path::new("/out/rf"),
        &staged(["/g/util.jar"]),
        &LaunchOptions::default(),
    )
    .unwrap_err();
    assert_eq!(refusal.code, code::DAEMON_STAGE_INVALID);
    assert!(refusal.message.contains("has 1 entries, expected 2"), "{}", refusal.message);
}

/// The only way to reach the guard, since the substitution that precedes it removes every occurrence of the
/// token. Still worth keeping: the root is derived from a path a caller supplied.
#[test]
fn a_runfiles_root_that_names_the_token_is_refused_rather_than_execd() {
    let descriptor = parse(&valid_descriptor());
    let refusal = daemon_launch_arg_file(
        &descriptor,
        Path::new("/out/${RUNFILES_ROOT}"),
        &staged(["a", "b"]),
        &LaunchOptions::default(),
    )
    .unwrap_err();
    assert_eq!(refusal.code, code::FLAG_UNRESOLVED);
    assert!(refusal.message.contains("unresolved RUNFILES_ROOT"), "{}", refusal.message);
}
