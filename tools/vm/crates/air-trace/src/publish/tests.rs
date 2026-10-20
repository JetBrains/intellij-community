use std::fs;
use std::path::Path;

use avl_testkit::traces::{copy_tree, example_bundle, example_file};
use avl_trace::bundle::{MANIFEST_FILE, VIDEO_INDEX_FILE, bundle_file, decode_manifest};
use avl_trace_tools::pack::{PackOptions, pack};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;

fn real(path: &Path) -> PathBuf {
    fscopy::resolve_links(path).unwrap()
}

fn testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// A `test.xml` with one case for each `(class, name)`.
fn write_xml(dir: &Path, cases: &[(&str, &str)]) {
    let mut xml = String::from("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n");
    for (class, name) in cases {
        xml.push_str(&format!(
            "  <testsuite name=\"{class}\" tests=\"1\">\n    <testcase classname=\"{class}\" name=\"{name}\" time=\"1.0\"/>\n  </testsuite>\n"
        ));
    }
    xml.push_str("</testsuites>\n");
    fs::create_dir_all(dir).unwrap();
    fs::write(dir.join(TEST_XML), xml).unwrap();
}

/// The testlogs of three lanes, as Bazel leaves them:
///
/// - `ui_lane_test` has its outputs as a directory, as TeamCity keeps them: the golden bundle, failed, with a video.
///   Two more copies name the scenarios `a b` and `a_b`, which sanitize to one file name.
/// - `ui-real_lane_test/shard_1_of_2` has its outputs zipped into `outputs.zip`: the golden bundle as it is. Its XML
///   names the same test class and case in another package, as a second lane of one test would.
/// - `plain_test` has no outputs.
fn testlogs() -> tempfile::TempDir {
    let manifest = decode_manifest(&example_file(MANIFEST_FILE)).unwrap();
    let class = format!("com.intellij.air.integration.flow.generated.ui.{}", manifest.test_class);
    let root = tempfile::tempdir().unwrap();
    let placed = bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario);

    let lane = root.path().join("plugins/air/tests/integration/ui/ui_lane_test");
    write_xml(&lane, &[(&class, &manifest.scenario), (&class, "another scenario")]);
    let bundle = bundle_file(&lane.join(TEST_OUTPUTS).join(ROOT_DIR_NAME), &placed);
    copy_tree(&example_bundle(), &bundle);
    let mut failed: Value = serde_json::from_slice(&example_file(MANIFEST_FILE)).unwrap();
    failed["status"] = "failed".into();
    failed["video"] = serde_json::json!({ "codec": "h264", "file": VIDEO_FILE, "index": VIDEO_INDEX_FILE });
    fs::write(bundle.join(MANIFEST_FILE), serde_json::to_vec(&failed).unwrap()).unwrap();
    fs::write(bundle.join(VIDEO_FILE), b"not really a video").unwrap();
    for (index, scenario) in ["a b", "a_b"].into_iter().enumerate() {
        let copy = bundle_file(&lane.join(TEST_OUTPUTS).join(ROOT_DIR_NAME), &format!("copies/{index}"));
        copy_tree(&example_bundle(), &copy);
        let mut renamed: Value = serde_json::from_slice(&example_file(MANIFEST_FILE)).unwrap();
        renamed["scenario"] = scenario.into();
        fs::write(copy.join(MANIFEST_FILE), serde_json::to_vec(&renamed).unwrap()).unwrap();
    }

    let sharded = root
        .path()
        .join("plugins/air/tests/integration/ui-real/ui-real_lane_test/shard_1_of_2");
    let other_package = format!("com.intellij.air.integration.real.{}", manifest.test_class);
    write_xml(&sharded, &[(&other_package, &manifest.scenario)]);
    let outputs = tempfile::tempdir().unwrap();
    copy_tree(&example_bundle(), &bundle_file(&outputs.path().join(ROOT_DIR_NAME), &placed));
    pack(
        outputs.path(),
        &sharded.join(TEST_OUTPUTS).join(OUTPUTS_ZIP),
        &PackOptions::default(),
    )
    .unwrap();

    write_xml(&root.path().join("plugins/other/plain_test"), &[("com.example.PlainTest", "works")]);
    root
}

/// Each bundle is one zip under its lane, run and class, published once, and named by the case of its own XML, so two
/// lanes of one test class name two tests. A failed bundle links its video and its last picture. Two scenarios of one
/// sanitized name get two zips, and the second is reported. A lane without outputs prints nothing.
#[test]
fn the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    publish(&[root.path().to_path_buf()], out.path(), &mut stdout, &mut stderr).unwrap();
    let scanned = Scanner::new(Options::default()).scan(&[Root::new(RootKind::Flag, root.path())]);
    let statuses: BTreeSet<Status> = scanned.bundles.iter().map(|bundle| bundle.status).collect();
    assert_eq!(
        statuses,
        BTreeSet::from([Status::Passed, Status::Failed]),
        "the fixture manifests decode"
    );
    let stdout = String::from_utf8(stdout).unwrap().replace(&*out.path().to_string_lossy(), "<out>");
    let stderr = String::from_utf8(stderr)
        .unwrap()
        .replace(&*out.path().to_string_lossy(), "<out>")
        .replace(&*real(root.path()).to_string_lossy(), "<testlogs>")
        .replace('\\', "/");
    expect_test::expect_file![testdata("publish.teamcity.txt")].assert_eq(&stdout);
    let run = decode_manifest(&example_file(MANIFEST_FILE)).unwrap();
    let class_dir = format!("{}/{}", run.run_id, run.test_class);
    assert_eq!(
        stderr,
        format!(
            "air-trace teamcity-publish: <testlogs>/plugins/air/tests/integration/ui/ui_lane_test/test.outputs/air-traces/copies/1: \
             another bundle has the name a_b.zip, so this one is <out>/ui_lane_test/{class_dir}/a_b.2.zip\n\
             air-trace teamcity-publish: 4 bundle zip(s) from 3 test.xml file(s) into <out>\n"
        )
    );

    let zips: Vec<String> = walkdir::WalkDir::new(out.path())
        .sort_by_file_name()
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .map(|entry| entry.path().strip_prefix(out.path()).unwrap().to_string_lossy().replace('\\', "/"))
        .collect();
    assert_eq!(zips.len(), 4, "{zips:?}");
    for zip in &zips {
        let mut archive = zip::ZipArchive::new(fs::File::open(out.path().join(zip)).unwrap()).unwrap();
        assert!(archive.by_name(MANIFEST_FILE).is_ok(), "{zip} holds the bundle at its top");
    }
}

/// One bundle that a test left both as a directory and in `outputs.zip` is published once.
#[test]
fn a_bundle_in_both_the_directory_and_the_zip_is_published_once() {
    let manifest = decode_manifest(&example_file(MANIFEST_FILE)).unwrap();
    let placed = bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario);
    let root = tempfile::tempdir().unwrap();
    let lane = root.path().join("ui/ui_lane_test");
    write_xml(&lane, &[(&manifest.test_class, &manifest.scenario)]);
    let traces = lane.join(TEST_OUTPUTS).join(ROOT_DIR_NAME);
    copy_tree(&example_bundle(), &bundle_file(&traces, &placed));
    let outputs = tempfile::tempdir().unwrap();
    copy_tree(&example_bundle(), &bundle_file(&outputs.path().join(ROOT_DIR_NAME), &placed));
    pack(outputs.path(), &lane.join(TEST_OUTPUTS).join(OUTPUTS_ZIP), &PackOptions::default()).unwrap();
    let out = tempfile::tempdir().unwrap();
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    publish(&[root.path().to_path_buf()], out.path(), &mut stdout, &mut stderr).unwrap();
    let stdout = String::from_utf8(stdout).unwrap();
    assert_eq!(stdout.matches("##teamcity[publishArtifacts").count(), 1, "{stdout}");
    let stderr = String::from_utf8(stderr).unwrap();
    assert!(stderr.contains(": 1 bundle zip(s) from 1 test.xml"), "{stderr}");
}

/// A testlogs tree without bundles prints no message, and a missing directory is refused.
#[test]
fn testlogs_without_bundles_print_nothing() {
    let root = tempfile::tempdir().unwrap();
    write_xml(&root.path().join("plugins/other/plain_test"), &[("com.example.PlainTest", "works")]);
    let out = tempfile::tempdir().unwrap();
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    publish(&[root.path().to_path_buf()], out.path(), &mut stdout, &mut stderr).unwrap();
    assert_eq!(String::from_utf8(stdout).unwrap(), "");
    assert_eq!(fs::read_dir(out.path()).unwrap().count(), 0);

    let missing = root.path().join("missing");
    let refused = publish(&[missing], out.path(), &mut Vec::new(), &mut Vec::new()).unwrap_err();
    assert_eq!(refused.exit, Exit::Usage);
}

#[test]
fn a_lane_is_the_target_and_its_shard() {
    assert_eq!(lane_of(Path::new("plugins/air/tests/integration/ui/ui_lane_test")), "ui_lane_test");
    assert_eq!(lane_of(Path::new("a/ui_lane_test/shard_2_of_4")), "ui_lane_test/shard_2_of_4");
    assert_eq!(lane_of(Path::new("a/ui_lane_test/run_1_of_3")), "ui_lane_test/run_1_of_3");
    assert_eq!(lane_of(Path::new("")), "tests");
}
