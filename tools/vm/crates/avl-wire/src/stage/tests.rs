use pretty_assertions::assert_eq;

use super::*;

fn digest(char: char) -> String {
    char.to_string().repeat(64)
}

// An absent `stableSources` is not an empty classpath: reading it as one stages nothing and reports success.
#[test]
fn an_absent_source_list_is_not_an_empty_one() {
    let digest = digest('a');
    let absent = decode_runtime_manifest(format!(r#"{{"schemaVersion":2,"runtimeDigest":"{digest}","jbrSource":"/dist/jbr"}}"#).as_bytes());
    assert!(matches!(absent, Err(StageError::InvalidRuntimeManifest)), "{absent:?}");
    assert_eq!(absent.unwrap_err().to_string(), "invalid runtime stage manifest");

    let null = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":2,"runtimeDigest":"{digest}","stableSources":null,"jbrSource":"/dist/jbr"}}"#).as_bytes(),
    );
    assert!(matches!(null, Err(StageError::InvalidRuntimeManifest)), "{null:?}");

    let empty = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":2,"runtimeDigest":"{digest}","stableSources":[],"jbrSource":"/dist/jbr"}}"#).as_bytes(),
    )
    .expect("an explicitly empty stableSources was refused");
    assert!(empty.stable_sources.is_empty());
}

#[test]
fn manifest_schema_is_refused() {
    let digest = digest('a');
    let other_schema = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":99,"runtimeDigest":"{digest}","stableSources":[],"jbrSource":"/dist/jbr"}}"#).as_bytes(),
    );
    assert!(matches!(other_schema, Err(StageError::InvalidRuntimeManifest)), "{other_schema:?}");
    assert_eq!(other_schema.unwrap_err().to_string(), "invalid runtime stage manifest");
    assert!(matches!(decode_runtime_manifest(b"{not json"), Err(StageError::Json(_))));

    let sourceless = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":2,"runtimeDigest":"{digest}","stableSources":[],"jbrSource":""}}"#).as_bytes(),
    );
    assert!(matches!(sourceless, Err(StageError::InvalidRuntimeManifest)), "{sourceless:?}");

    // A controller of version 1 named a JBR archive. This guest copies a directory, so it refuses that manifest.
    let archive = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":1,"runtimeDigest":"{digest}","stableSources":[],"jbrArchive":"/j.tgz"}}"#).as_bytes(),
    );
    assert!(matches!(archive, Err(StageError::InvalidRuntimeManifest)), "{archive:?}");
}

#[test]
fn unknown_manifest_fields_are_accepted() {
    let digest = digest('a');
    decode_runtime_manifest(
        format!(
            r#"{{"schemaVersion":2,"runtimeDigest":"{digest}","stableSources":[],"jbrSource":"/dist/jbr","somethingTheGuestPredates":true}}"#
        )
        .as_bytes(),
    )
    .expect("an unknown manifest field was refused");
}

// Every digest on this wire is text in the type system, so no caller can widen one.
#[test]
fn digest_fields_are_strings() {
    let encoded = serde_json::to_string(&RuntimeManifest {
        schema_version: SCHEMA_VERSION,
        runtime_digest: digest('b'),
        stable_sources: Vec::new(),
        jbr_source: "/g/jbr".to_owned(),
        java_home_suffix: String::new(),
    })
    .unwrap();
    assert_eq!(
        encoded,
        format!(
            r#"{{"schemaVersion":2,"runtimeDigest":"{}","stableSources":[],"jbrSource":"/g/jbr","javaHomeSuffix":""}}"#,
            digest('b')
        )
    );
    assert!(is_sha256_hex(&digest('b')));
    for refused in [String::new(), "b".repeat(63), digest('B'), format!("0x{}", "b".repeat(62))] {
        assert!(!is_sha256_hex(&refused), "{refused:?} was accepted as a sha256");
    }
}

// The classpath file's content is the stager's exact bytes: entries joined by newline, one trailing newline. The
// digest the stage-check carries is taken over exactly this.
#[test]
fn the_classpath_file_is_the_stagers_exact_bytes() {
    assert_eq!(classpath_file_text(&["/a", "/b"]), "/a\n/b\n");
    assert_eq!(classpath_file_text::<&str>(&[]), "\n", "an empty classpath is still one newline");
    assert_eq!(parse_classpath_file("/a\n/b\n"), ["/a", "/b"]);
    assert!(parse_classpath_file("\n").is_empty());
}

#[test]
fn a_generation_is_named_by_its_digest_under_the_runtime_root() {
    assert_eq!(
        generation_dir("/vm/data/daemon-runtime/", "abc"),
        "/vm/data/daemon-runtime/generations/abc"
    );
    assert_eq!(generation_dir("/r", "abc"), "/r/generations/abc");
}

proptest::proptest! {
    // Whatever the controller remembers is what the guest reads back, entry for entry.
    #[test]
    fn a_classpath_file_reads_back_what_was_written(
        classpath in proptest::collection::vec("[^\n]{1,40}", 0..20),
    ) {
        proptest::prop_assert_eq!(parse_classpath_file(&classpath_file_text(&classpath)), classpath);
    }
}
