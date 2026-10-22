use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::supervisor::{Phase, is_run_id};

fn context_document() -> Value {
    json!({
        "schemaVersion": 2,
        "ideRoot": "/data/ide",
        "launchKey": "a1b2c3",
        "fresh": true,
        "project": {"archive": "/runfiles/deps/simpleJavaProject.zip", "archiveRoot": "BookmarksTestProject"},
        "disabledPluginIds": ["com.intellij.copyright"]
    })
}

fn launch_document() -> Value {
    json!({
        "schemaVersion": 2,
        "ideRoot": "/data/ide",
        "launchKey": "a1b2c3",
        "distHome": "/runfiles/_main/dist",
        "ideConfig": "/runfiles/_main/dist.config",
        "javaHome": "/data/runtime/jbr",
        "flagsFile": "/runfiles/_main/ide.flags",
        "properties": {"air.ui.test.http.port": "17000", "user.home": "/data/ide/a1b2c3/home"},
        "projectDir": "/data/ide/a1b2c3/project/BookmarksTestProject",
        "launchName": "launch-1",
        "productDigest": "0123"
    })
}

/// The context document of `value`, or the refusal of its check.
fn context(value: &Value) -> Result<IdeContext, IdeDocumentError> {
    let document: IdeContext = serde_json::from_value(value.clone()).unwrap_or_else(|error| panic!("{value} is no context: {error}"));
    validate_ide_context(&document).map(|()| document)
}

/// The launch document of `value`, or the refusal of its check.
fn launch(value: &Value) -> Result<IdeLaunch, IdeDocumentError> {
    let document: IdeLaunch = serde_json::from_value(value.clone()).unwrap_or_else(|error| panic!("{value} is no launch: {error}"));
    validate_ide_launch(&document).map(|()| document)
}

fn context_refusal(value: &Value) -> String {
    match context(value) {
        Ok(document) => panic!("the document was accepted: {document:?}"),
        Err(error) => error.message,
    }
}

fn launch_refusal(value: &Value) -> String {
    match launch(value) {
        Ok(document) => panic!("the document was accepted: {document:?}"),
        Err(error) => error.message,
    }
}

// The run id of a launch is a supervisor run id, and the launch name comes back out of it.
#[test]
fn an_ide_run_id_is_a_supervisor_run_id_and_names_its_launch() {
    let run_id = ide_run_id("launch-7").unwrap();
    assert_eq!(run_id, "run-ide-launch-7");
    assert!(is_run_id(&run_id));
    assert_eq!(launch_name_of(&run_id), Some("launch-7"));
    assert_eq!(launch_name_of("run-ui-daemon-1"), None);
    assert_eq!(launch_name_of("run-ide-"), None);
    assert_eq!(ide_run_id("a.b"), None, "a dot is no run id character");
    assert_eq!(ide_run_id(""), None);
}

#[test]
fn a_launch_key_is_one_plain_path_component() {
    for key in ["a1b2", "air-ui.ide_3", "X"] {
        assert!(is_launch_key(key), "{key}");
    }
    for key in ["", ".", "..", ".hidden", "a/b", "a b", "ä"] {
        assert!(!is_launch_key(key), "{key}");
    }
    assert_eq!(context_dir("/data/ide/", "k"), "/data/ide/k");
}

#[test]
fn both_documents_round_trip_at_version_2() {
    assert_eq!(SCHEMA_VERSION, 2);
    let read = context(&context_document()).unwrap();
    assert_eq!(read.project.archive_root, "BookmarksTestProject");
    assert_eq!(read.project.relocate_to, None);
    assert_eq!(serde_json::to_value(&read).unwrap(), context_document());

    let read = launch(&launch_document()).unwrap();
    assert_eq!(read.properties["air.ui.test.http.port"], "17000");
    assert_eq!(serde_json::to_value(&read).unwrap(), launch_document());
}

// The fields with a default may be absent.
#[test]
fn the_defaulted_fields_may_be_absent() {
    let mut value = context_document();
    value.as_object_mut().unwrap().remove("disabledPluginIds");
    assert_eq!(context(&value).unwrap().disabled_plugin_ids, Vec::<String>::new());

    let mut value = launch_document();
    let object = value.as_object_mut().unwrap();
    for field in ["properties", "productDigest"] {
        object.remove(field);
    }
    let document = launch(&value).unwrap();
    assert_eq!(document.properties, BTreeMap::new());
    assert_eq!(document.product_digest, "");
}

#[test]
fn a_context_document_the_agent_cannot_act_on_is_refused() {
    let cases: [(&str, Value, &str); 5] = [
        ("schemaVersion", json!(1), "schema version 1"),
        ("ideRoot", json!("/ide"), "ideRoot"),
        ("ideRoot", json!("/data/../ide"), "ideRoot"),
        ("launchKey", json!(".."), "launchKey"),
        ("disabledPluginIds", json!([" "]), "plugin id"),
    ];
    for (field, value, expected) in cases {
        let mut document = context_document();
        document[field] = value;
        let message = context_refusal(&document);
        assert!(message.contains(expected), "{field}: {message}");
    }
    for (field, value) in [
        ("archiveRoot", json!("../outside")),
        ("archiveRoot", json!("/absolute")),
        ("relocateTo", json!("/live")),
        ("archive", json!("project.zip")),
    ] {
        let mut document = context_document();
        document["project"][field] = value;
        context(&document).unwrap_err();
    }
    let mut document = context_document();
    document["project"]["relocateTo"] = json!("/tmp/air-live/project");
    assert_eq!(
        context(&document).unwrap().project.relocate_to.as_deref(),
        Some("/tmp/air-live/project")
    );
}

#[test]
fn a_launch_document_the_agent_cannot_act_on_is_refused() {
    let cases: [(&str, Value, &str); 10] = [
        ("schemaVersion", json!(1), "schema version 1"),
        ("ideRoot", json!("/ide"), "ideRoot"),
        ("launchKey", json!(".."), "launchKey"),
        ("launchName", json!("a.b"), "launchName"),
        ("distHome", json!("dist"), "distHome"),
        ("flagsFile", json!("ide.flags"), "flagsFile"),
        ("projectDir", json!("project"), "projectDir"),
        ("properties", json!({"idea.config.path": "/elsewhere"}), "idea.config.path"),
        ("properties", json!({"": "x"}), "property key"),
        ("properties", json!({"k": "a\nb"}), "line break"),
    ];
    for (field, value, expected) in cases {
        let mut document = launch_document();
        document[field] = value;
        let message = launch_refusal(&document);
        assert!(message.contains(expected), "{field}: {message}");
    }
}

#[test]
fn the_launch_answer_holds_the_run_as_status_answers_it() {
    let launched = IdeLaunched {
        log_dir: "/data/ide/k/log/launch-1".to_owned(),
        arg_file: "/data/ide/k/ide-jvm.args".to_owned(),
        run: RunState::new("run-ide-launch-1", Phase::Running),
    };
    let written = serde_json::to_value(&launched).unwrap();
    assert_eq!(written["run"], serde_json::to_value(&launched.run).unwrap());
    assert_eq!(
        (written["logDir"].as_str(), written["arg_file"].as_str()),
        (Some("/data/ide/k/log/launch-1"), None)
    );
    assert_eq!(serde_json::from_value::<IdeLaunched>(written).unwrap(), launched);
}

#[test]
fn a_launch_record_round_trips_and_checks_its_version() {
    let record = LaunchRecord {
        schema_version: SCHEMA_VERSION,
        launch_key: "k".to_owned(),
        launch_name: "launch-1".to_owned(),
        product_digest: "0123".to_owned(),
        java_home: "/jbr".to_owned(),
        arg_file: "/data/ide/k/ide-jvm.args".to_owned(),
        prepared_at: "2026-10-08T10:00:00Z".to_owned(),
    };
    let raw = serde_json::to_vec(&record).unwrap();
    assert_eq!(decode_launch_record(&raw).unwrap(), record);
    let text = String::from_utf8(raw).unwrap();
    assert!(text.contains(r#""argFile""#) && text.contains(r#""productDigest""#), "{text}");
    let older = text.replace(r#""schemaVersion":2"#, r#""schemaVersion":1"#);
    decode_launch_record(older.as_bytes()).unwrap_err();
}

// Every list of the collection answer is an array, also when it is empty.
#[test]
fn an_empty_collection_writes_five_arrays() {
    assert_eq!(
        serde_json::to_string(&IdeGcResult::default()).unwrap(),
        r#"{"stopped":[],"killed":[],"abandoned":[],"kept":[],"removed":[]}"#
    );
}

// An answer of an older agent names no killed and no abandoned run, and it still decodes.
#[test]
fn an_older_collection_answer_decodes_without_killed_and_abandoned() {
    let older: IdeGcResult = serde_json::from_str(r#"{"stopped":[],"kept":[],"removed":[]}"#).unwrap();
    assert_eq!(older, IdeGcResult::default());
}
