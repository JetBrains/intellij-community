use std::path::{Path, PathBuf};

use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

const GUEST_JBR_PLATFORM: &str = "linux-x64";
const DESCRIPTOR_PATH: &str = "/out/bin/plugins/air/ui_daemon.runtime.json";

fn file(logical_path: &str, owner: &str) -> Value {
    json!({
        "execPath": format!("bazel-out/k8-fastbuild/bin/{logical_path}"),
        "logicalPath": logical_path,
        "owner": owner,
    })
}

fn plugin(destination: &str) -> Value {
    json!({
        "destination": destination,
        "file": file(
            &format!("_main/plugins/air/tests/integration/bridge_plugin_dir/{destination}"),
            "//plugins/air/tests/integration:bridge_plugin_dir",
        ),
    })
}

/// The descriptor every refusal below is one mutation away from. A value rather than a literal, so a case can
/// *delete* a field: a missing field and an empty one are different mistakes, and only the first is what an older
/// Bazel rule actually emits.
fn valid_descriptor() -> Value {
    json!({
        "schemaVersion": 10,
        "kind": "air-ui-daemon-runtime",
        "staticJvmFlags": ["-Xmx4g", "-Dsun.io.useCanonCaches=false", "-Dair.ui.runfiles=${RUNFILES_ROOT}/_main"],
        "devDist": {
            "config": file("_main/dist/ultimate.ide.config", "//build:dev_config"),
            "fingerprint": file("_main/dist/fingerprint.txt", "//build:dev_fingerprint"),
            "home": file("_main/dist/home", "//build:dev_home"),
        },
        "jbr": {
            "platform": "linux-x64",
        },
        "ide": {
            "fakeAgentJar": file(FAKE_AGENT_JAR, "//plugins/air/integrationTests/test-support:acp_scenario_agent"),
            "flagsFile": file("_main/plugins/air/tests/integration/ui/ide_flags.jvm-flags.txt", "//plugins/air/tests/integration/ui:ide_flags"),
            // One jar holds both kinds of document.
            "laneDocuments": documents_jar(),
            "plugins": [
                plugin("air-integrationTests-bridge/lib/air-integrationTests-bridge-plugin.jar"),
                plugin("air-integrationTests-bridge/lib/modules/intellij.air.integrationTests.bridge.jar"),
            ],
            "projectArchive": file("_main/external/air_integration_test_deps/simpleJavaProject.zip", "@air_integration_test_deps//:simpleJavaProject.zip"),
            "projectRoot": "BookmarksTestProject",
            "suiteDocuments": documents_jar(),
        },
        "data": [file("_main/plugins/air/testData/project.zip", "//plugins/air:test_data")],
    })
}

const FAKE_AGENT_JAR: &str = "_main/plugins/air/integrationTests/test-support/acp_scenario_agent_deploy.jar";
const DOCUMENTS_JAR: &str = "_main/plugins/air/tests/integration/flow-profiles/flow-profiles_test_lib.jar";

/// The flow-profiles resource jar, which holds the suite and the lane documents.
fn documents_jar() -> Value {
    file(
        DOCUMENTS_JAR,
        "//plugins/air/tests/integration/flow-profiles:flow-profiles_test_lib",
    )
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
    assert_eq!((parsed.schema_version, parsed.kind.as_str()), (10, DESCRIPTOR_KIND));
    // The lane inputs in key order, each with its runfile.
    let inputs: Vec<(&str, &str)> = parsed
        .ide
        .lane_inputs()
        .iter()
        .map(|(field, input)| (*field, input.logical_path.as_str()))
        .collect();
    assert_eq!(
        inputs,
        [
            ("fakeAgentJar", FAKE_AGENT_JAR),
            ("laneDocuments", DOCUMENTS_JAR),
            ("suiteDocuments", DOCUMENTS_JAR),
        ]
    );
    // A data entry is the file with its three fields.
    assert_eq!(
        parsed.data,
        [RuntimeFile {
            exec_path: "bazel-out/k8-fastbuild/bin/_main/plugins/air/testData/project.zip".to_owned(),
            logical_path: "_main/plugins/air/testData/project.zip".to_owned(),
            owner: "//plugins/air:test_data".to_owned(),
        }]
    );
    assert_eq!(parsed.jbr.platform, GUEST_JBR_PLATFORM);
    assert_eq!(parsed.dev_dist.home.logical_path, "_main/dist/home");
    assert_eq!(parsed.ide.project_root, "BookmarksTestProject");
    assert_eq!(
        parsed.ide.flags_file.logical_path,
        "_main/plugins/air/tests/integration/ui/ide_flags.jvm-flags.txt"
    );
    // The plugin files keep their order and their destination below the plugin directory of the context.
    let destinations: Vec<&str> = parsed.ide.plugins.iter().map(|plugin| plugin.destination.as_str()).collect();
    assert_eq!(
        destinations,
        [
            "air-integrationTests-bridge/lib/air-integrationTests-bridge-plugin.jar",
            "air-integrationTests-bridge/lib/modules/intellij.air.integrationTests.bridge.jar",
        ]
    );
    assert_eq!(
        parsed.ide.plugins[0].file.owner,
        "//plugins/air/tests/integration:bridge_plugin_dir"
    );
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
            &["schemaVersion is absent, expected 10"],
        ),
        // The version before the JBR of the distribution: it names a JBR archive, which this controller stages no more.
        (
            "a descriptor of the version that names a JBR archive",
            bytes(set(valid(), "/schemaVersion", json!(7))),
            code::SCHEMA_VERSION,
            &["schemaVersion is 7, expected 10"],
        ),
        // The version before the lane inputs: its fake agent jar is data, so an edit of the agent moves the mount.
        (
            "a descriptor of the version without the lane inputs",
            bytes(set(valid(), "/schemaVersion", json!(8))),
            code::SCHEMA_VERSION,
            &["schemaVersion is 8, expected 10"],
        ),
        // The version before this one: it declares the classpath tiers, the main class and the JBR directory of the
        // retired JVM daemon.
        (
            "a descriptor of the version with the classpath tiers",
            bytes(set(valid(), "/schemaVersion", json!(9))),
            code::SCHEMA_VERSION,
            &["schemaVersion is 9, expected 10"],
        ),
        (
            "an older rule emits no schema version",
            bytes(drop_field(valid(), "/schemaVersion")),
            code::SCHEMA_VERSION,
            &["schemaVersion is absent"],
        ),
        (
            "a schema version this controller predates",
            bytes(set(valid(), "/schemaVersion", json!(11))),
            code::SCHEMA_VERSION,
            &["schemaVersion is 11, expected 10"],
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
            "a data list that is null",
            bytes(set(valid(), "/data", Value::Null)),
            code::FIELD_INVALID,
            &["data"],
        ),
        (
            "a data entry that is a bare string",
            bytes(set(valid(), "/data/0", json!("project.zip"))),
            code::FIELD_INVALID,
            &["data[0]"],
        ),
        // A derived decoder would take three strings positionally as a file.
        (
            "a data entry that is an array",
            bytes(set(valid(), "/data/0", json!(["a", "b", "c"]))),
            code::FIELD_INVALID,
            &["data[0]"],
        ),
        (
            "a data entry with no exec path",
            bytes(set(valid(), "/data/0/execPath", json!(""))),
            code::FIELD_INVALID,
            &["data[0].execPath"],
        ),
        // The owner is only read to name a rule in a message, and it is still required: a refusal that cannot say
        // which rule produced the bad file is a refusal nobody can act on.
        (
            "a data entry with no owning label",
            bytes(drop_field(valid(), "/data/0/owner")),
            code::FIELD_INVALID,
            &["data[0]", "owner"],
        ),
        (
            "an absolute logical path",
            bytes(set(valid(), "/devDist/home/logicalPath", json!("/etc/passwd"))),
            code::PATH_ESCAPES,
            &["devDist.home.logicalPath escapes the runfiles root"],
        ),
        (
            "a logical path that climbs out of the tree",
            bytes(set(valid(), "/data/0/logicalPath", json!("_main/../../../etc/passwd"))),
            code::PATH_ESCAPES,
            &["data[0].logicalPath escapes the runfiles root"],
        ),
        (
            "no JBR platform",
            bytes(drop_field(valid(), "/jbr/platform")),
            code::FIELD_INVALID,
            &["jbr", "platform"],
        ),
        // Keeps a darwin distribution off a Linux worker, where the lane IDE would fail only after its launch.
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
        ("no IDE section", bytes(drop_field(valid(), "/ide")), code::FIELD_INVALID, &["ide"]),
        (
            "an IDE section that is an array",
            bytes(set(valid(), "/ide", json!([]))),
            code::FIELD_INVALID,
            &["ide"],
        ),
        (
            "no IDE flags file",
            bytes(drop_field(valid(), "/ide/flagsFile")),
            code::FIELD_INVALID,
            &["ide", "flagsFile"],
        ),
        (
            "an IDE project archive with an empty exec path",
            bytes(set(valid(), "/ide/projectArchive/execPath", json!(""))),
            code::FIELD_INVALID,
            &["ide.projectArchive.execPath"],
        ),
        (
            "an empty IDE project root",
            bytes(set(valid(), "/ide/projectRoot", json!(""))),
            code::FIELD_INVALID,
            &["ide.projectRoot", "empty"],
        ),
        (
            "an IDE project root that climbs out of the archive",
            bytes(set(valid(), "/ide/projectRoot", json!("../outside"))),
            code::PROJECT_ROOT_INVALID,
            &["ide.projectRoot ../outside escapes the project archive"],
        ),
        (
            "an IDE flags file outside the runfiles tree",
            bytes(set(valid(), "/ide/flagsFile/logicalPath", json!("/etc/flags"))),
            code::PATH_ESCAPES,
            &["ide.flagsFile.logicalPath escapes the runfiles root"],
        ),
        (
            "no IDE plugins",
            bytes(drop_field(valid(), "/ide/plugins")),
            code::FIELD_INVALID,
            &["ide", "plugins"],
        ),
        (
            "an IDE plugin file that is an array",
            bytes(set(valid(), "/ide/plugins/0", json!(["a", "b"]))),
            code::FIELD_INVALID,
            &["ide.plugins[0]"],
        ),
        (
            "an IDE plugin file with an empty destination",
            bytes(set(valid(), "/ide/plugins/0/destination", json!(""))),
            code::FIELD_INVALID,
            &["ide.plugins[0].destination", "empty"],
        ),
        (
            "an IDE plugin file that climbs out of the plugin directory",
            bytes(set(
                valid(),
                "/ide/plugins/0/destination",
                json!("air-integrationTests-bridge/../../config/x.jar"),
            )),
            code::PLUGIN_DESTINATION_INVALID,
            &["ide.plugins[0].destination air-integrationTests-bridge/../../config/x.jar is not a file inside a plugin directory"],
        ),
        (
            "an IDE plugin file at the top of the plugin directory",
            bytes(set(valid(), "/ide/plugins/0/destination", json!("loose.jar"))),
            code::PLUGIN_DESTINATION_INVALID,
            &["ide.plugins[0].destination loose.jar"],
        ),
        (
            "an absolute IDE plugin destination",
            bytes(set(valid(), "/ide/plugins/0/destination", json!("/plugins/x/lib/x.jar"))),
            code::PLUGIN_DESTINATION_INVALID,
            &["ide.plugins[0].destination /plugins/x/lib/x.jar"],
        ),
        (
            "two IDE plugin files with one destination",
            bytes(set(
                valid(),
                "/ide/plugins/1/destination",
                json!("air-integrationTests-bridge/lib/air-integrationTests-bridge-plugin.jar"),
            )),
            code::PLUGIN_DESTINATION_INVALID,
            &["ide.plugins[1].destination air-integrationTests-bridge/lib/air-integrationTests-bridge-plugin.jar is claimed twice"],
        ),
        (
            "an IDE plugin file outside the runfiles tree",
            bytes(set(valid(), "/ide/plugins/1/file/logicalPath", json!("/etc/x.jar"))),
            code::PATH_ESCAPES,
            &["ide.plugins[1].file.logicalPath escapes the runfiles root"],
        ),
        // The plugin files travel by the push, so the rule must take them out of the data list.
        (
            "an IDE plugin file that is also declared data",
            bytes(set(
                valid(),
                "/data/0/logicalPath",
                json!(
                    "_main/plugins/air/tests/integration/bridge_plugin_dir/air-integrationTests-bridge/lib/air-integrationTests-bridge-plugin.jar"
                ),
            )),
            code::DUPLICATE_LOGICAL_PATH,
            &["duplicate logical path _main/plugins/air/tests/integration/bridge_plugin_dir/"],
        ),
        // The project archive is a runfile of every lane, so the rule must take it out of the data list.
        (
            "an IDE project archive that is also declared data",
            bytes(set(
                valid(),
                "/data/0/logicalPath",
                json!("_main/external/air_integration_test_deps/simpleJavaProject.zip"),
            )),
            code::DUPLICATE_LOGICAL_PATH,
            &["duplicate logical path _main/external/air_integration_test_deps/simpleJavaProject.zip"],
        ),
        // Two *different* files claiming one staged name: the stage materializes whichever it reaches last.
        (
            "two files claiming one staged name",
            bytes(set(valid(), "/data/0/logicalPath", json!("_main/dist/ultimate.ide.config"))),
            code::DUPLICATE_LOGICAL_PATH,
            &["duplicate logical path _main/dist/ultimate.ide.config"],
        ),
        (
            "no fake agent jar",
            bytes(drop_field(valid(), "/ide/fakeAgentJar")),
            code::FIELD_INVALID,
            &["missing field `fakeAgentJar`"],
        ),
        (
            "no jar of the suite documents",
            bytes(drop_field(valid(), "/ide/suiteDocuments")),
            code::FIELD_INVALID,
            &["missing field `suiteDocuments`"],
        ),
        (
            "a lane input outside the runfiles tree",
            bytes(set(valid(), "/ide/laneDocuments/logicalPath", json!("../lanes.jar"))),
            code::PATH_ESCAPES,
            &["ide.laneDocuments.logicalPath escapes the runfiles root"],
        ),
        // The point of a lane input: the rule must take the fake agent jar out of the data list, or an edit of the
        // agent still moves the mount identity.
        (
            "a fake agent jar that is also declared data",
            bytes(set(valid(), "/data/0/logicalPath", json!(FAKE_AGENT_JAR))),
            code::LANE_INPUT_STAGED,
            &["ide.fakeAgentJar _main/plugins/air/integrationTests/test-support/acp_scenario_agent_deploy.jar is also staged"],
        ),
        // The rule must take the jar of the documents out of the data list too, as it takes the fake agent jar.
        (
            "a jar of the documents that is also declared data",
            bytes(set(valid(), "/data/0/logicalPath", json!(DOCUMENTS_JAR))),
            code::LANE_INPUT_STAGED,
            &["ide.laneDocuments _main/plugins/air/tests/integration/flow-profiles/flow-profiles_test_lib.jar is also staged"],
        ),
        (
            "a jar of the documents that is the IDE flags file",
            bytes(set(
                valid(),
                "/ide/suiteDocuments/logicalPath",
                json!("_main/plugins/air/tests/integration/ui/ide_flags.jvm-flags.txt"),
            )),
            code::LANE_INPUT_STAGED,
            &["ide.suiteDocuments _main/plugins/air/tests/integration/ui/ide_flags.jvm-flags.txt is also staged"],
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
    parse(&set(valid_descriptor(), "/schemaVersion", json!(10.0)));
    // A jar of the lane documents apart from the jar of the suite documents.
    parse(&set(
        valid_descriptor(),
        "/ide/laneDocuments/logicalPath",
        json!("_main/plugins/air/tests/integration/flow-profiles/lanes.zip"),
    ));
    // A build with no context plugin installs nothing.
    assert_eq!(parse(&set(valid_descriptor(), "/ide/plugins", json!([]))).ide.plugins, []);
    // An empty data list is a build that mounts nothing.
    assert_eq!(parse(&set(valid_descriptor(), "/data", json!([]))).data, []);
    // A project root of more than one segment.
    parse(&set(valid_descriptor(), "/ide/projectRoot", json!("projects/BookmarksTestProject")));
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
fn the_ide_section_keys_are_the_ones_the_reader_reads() {
    let section = serde_json::to_value(&parse(&valid_descriptor()).ide).unwrap();
    let mut keys: Vec<&str> = section.as_object().unwrap().keys().map(String::as_str).collect();
    keys.sort_unstable();
    assert_eq!(keys, IDE_SECTION_FIELDS);
}

#[test]
fn the_jbr_section_keys_are_the_ones_the_reader_reads() {
    let section = serde_json::to_value(&parse(&valid_descriptor()).jbr).unwrap();
    let mut keys: Vec<&str> = section.as_object().unwrap().keys().map(String::as_str).collect();
    keys.sort_unstable();
    assert_eq!(keys, JBR_SECTION_FIELDS);
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
