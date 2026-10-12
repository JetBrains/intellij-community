use pretty_assertions::assert_eq;

use super::*;

fn digest(char: char) -> String {
    char.to_string().repeat(64)
}

// An absent `stableSources` is not an empty classpath: reading it as one stages nothing and reports success.
#[test]
fn an_absent_source_list_is_not_an_empty_one() {
    let digest = digest('a');
    let absent = decode_runtime_manifest(format!(r#"{{"schemaVersion":1,"runtimeDigest":"{digest}","jbrArchive":"/j.tgz"}}"#).as_bytes());
    assert!(matches!(absent, Err(StageError::InvalidRuntimeManifest)), "{absent:?}");
    assert_eq!(absent.unwrap_err().to_string(), "invalid runtime stage manifest");

    let null = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":1,"runtimeDigest":"{digest}","stableSources":null,"jbrArchive":"/j.tgz"}}"#).as_bytes(),
    );
    assert!(matches!(null, Err(StageError::InvalidRuntimeManifest)), "{null:?}");

    let empty = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":1,"runtimeDigest":"{digest}","stableSources":[],"jbrArchive":"/j.tgz"}}"#).as_bytes(),
    )
    .expect("an explicitly empty stableSources was refused");
    assert!(empty.stable_sources.is_empty());
}

#[test]
fn manifest_schema_is_refused() {
    let digest = digest('a');
    let other_schema = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":99,"runtimeDigest":"{digest}","stableSources":[],"jbrArchive":"/j.tgz"}}"#).as_bytes(),
    );
    assert!(matches!(other_schema, Err(StageError::InvalidRuntimeManifest)), "{other_schema:?}");
    assert_eq!(other_schema.unwrap_err().to_string(), "invalid runtime stage manifest");
    assert!(matches!(decode_runtime_manifest(b"{not json"), Err(StageError::Json(_))));

    let archiveless = decode_runtime_manifest(
        format!(r#"{{"schemaVersion":1,"runtimeDigest":"{digest}","stableSources":[],"jbrArchive":""}}"#).as_bytes(),
    );
    assert!(matches!(archiveless, Err(StageError::InvalidRuntimeManifest)), "{archiveless:?}");
}

#[test]
fn unknown_manifest_fields_are_accepted() {
    let digest = digest('a');
    decode_runtime_manifest(
        format!(
            r#"{{"schemaVersion":1,"runtimeDigest":"{digest}","stableSources":[],"jbrArchive":"/j.tgz","somethingTheGuestPredates":true}}"#
        )
        .as_bytes(),
    )
    .expect("an unknown manifest field was refused");
}

// Every digest on this wire is text in the type system, so no caller can widen one.
#[test]
fn digest_fields_are_strings() {
    let encoded = serde_json::to_string(&ArgFileRequest {
        destination: "/a.args".to_owned(),
        prefix: Vec::new(),
        main_class: "M".to_owned(),
        sha256: digest('b'),
    })
    .unwrap();
    assert_eq!(
        encoded,
        format!(
            r#"{{"destination":"/a.args","prefix":[],"mainClass":"M","sha256":"{}"}}"#,
            digest('b')
        )
    );
    assert!(is_sha256_hex(&digest('b')));
    for refused in [String::new(), "b".repeat(63), digest('B'), format!("0x{}", "b".repeat(62))] {
        assert!(!is_sha256_hex(&refused), "{refused:?} was accepted as a sha256");
    }
}

#[test]
fn a_launch_prep_is_refused_unless_every_part_is_there() {
    let valid = serde_json::json!({
        "schemaVersion": 1,
        "runtimeDigest": digest('a'),
        "stableCount": 3,
        "directories": [],
        "removeFiles": ["/g/state.json"],
        "argFile": {"destination": "/g/daemon.args", "prefix": ["-Xmx4g"], "mainClass": "a.Main", "sha256": digest('c')},
    });
    let request = decode_launch_prep(valid.to_string().as_bytes()).expect("a valid launch prep was refused");
    assert_eq!(request.arg_file.prefix, ["-Xmx4g"]);

    let broken = |edit: &dyn Fn(&mut serde_json::Value)| {
        let mut document = valid.clone();
        edit(&mut document);
        decode_launch_prep(document.to_string().as_bytes())
    };
    for (what, edit) in [
        (
            "no directories",
            &(|document: &mut serde_json::Value| {
                document.as_object_mut().unwrap().remove("directories");
            }) as &dyn Fn(&mut serde_json::Value),
        ),
        ("a zero stable count", &|document| {
            document["stableCount"] = 0.into();
        }),
        ("a digest that is not one", &|document| {
            document["runtimeDigest"] = "abc".into();
        }),
        ("no main class", &|document| {
            document["argFile"]["mainClass"] = "".into();
        }),
        ("an argfile digest that is not one", &|document| {
            document["argFile"]["sha256"] = digest('C').into();
        }),
        ("no prefix", &|document| {
            document["argFile"].as_object_mut().unwrap().remove("prefix");
        }),
    ] {
        let refused = broken(edit);
        assert!(matches!(refused, Err(StageError::InvalidLaunchPrep)), "{what}: {refused:?}");
    }
    assert_eq!(StageError::InvalidLaunchPrep.to_string(), "invalid launch preparation request");
}

// --- the `@argfile` ---------------------------------------------------------------------------------------

// The quoting rule, token by token: both halves render these bytes, so a disagreement about a single backslash
// is a start refused on every launch.
#[test]
fn every_token_shape_is_quoted_the_same_way_by_both_halves() {
    for (what, token, want) in [
        // Quoted even though nothing in it needs quoting, because the alternative is a rule deciding which do.
        ("a flag that needs nothing", "-Xmx4g", r#""-Xmx4g""#),
        (
            "a property with a value",
            "-Dair.ui.daemon.port=27100",
            r#""-Dair.ui.daemon.port=27100""#,
        ),
        ("a joined classpath", "/g/a.jar:/g/b.jar", r#""/g/a.jar:/g/b.jar""#),
        (
            "a path with a space",
            "/g/Application Support/tmp",
            r#""/g/Application Support/tmp""#,
        ),
        ("an embedded quote", r#"say "hi""#, r#""say \"hi\"""#),
        ("an embedded backslash", r"a\b", r#""a\\b""#),
        ("a backslash before a quote", r#"a\"b"#, r#""a\\\"b""#),
        ("the empty token", "", r#""""#),
        ("a token ending in backslash", r"C:\dist\", r#""C:\\dist\\""#),
    ] {
        assert_eq!(quote_for_arg_file(token), want, "{what}");
    }
}

// The backslash is doubled before the quote is escaped. Reversed, `a\"b` renders as `"a\\\\"b"`: the quote
// that was meant to be escaped ends the token, and the JVM reads the rest of the file from the wrong place.
#[test]
fn the_backslash_is_doubled_before_the_quote_is_escaped() {
    let token = r#"a\"b"#;
    assert_eq!(quote_for_arg_file(token), r#""a\\\"b""#);
    let reversed = format!("\"{}\"", token.replace('"', "\\\"").replace('\\', "\\\\"));
    assert_ne!(
        reversed,
        quote_for_arg_file(token),
        "the two orders agree, so this token no longer tells them apart"
    );
}

// One quoted token per line and no trailing newline: the bytes the digest is taken over.
#[test]
fn the_file_is_one_token_per_line_with_no_trailing_newline() {
    assert_eq!(
        arg_file_text(&["-Xmx4g", "-cp", "/g/a.jar:/g/b.jar", "com.example.Main"]),
        "\"-Xmx4g\"\n\"-cp\"\n\"/g/a.jar:/g/b.jar\"\n\"com.example.Main\""
    );
    assert_eq!(arg_file_text::<&str>(&[]), "");
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
