use std::fs;
use std::path::Path;

use avl_testkit::traces::{copy_tree, example_bundle, example_file};
use avl_trace::bundle::{MANIFEST_FILE, VIDEO_INDEX_FILE, bundle_file, decode_manifest};
use avl_trace_tools::pack::{PackOptions, pack};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::viewer::VIEWER_PAGE;
use super::*;

fn real(path: &Path) -> PathBuf {
    fscopy::resolve_links(path).unwrap()
}

fn testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// Publishes the testlogs `root` into `out`, with the stdout and the stderr of the step.
fn run(root: &Path, out: &Path, viewer: Option<&Path>, build: Option<&Build>) -> (String, String) {
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    let viewer = viewer.map_or(ViewerSource::Missing, |dir| ViewerSource::Built(dir.to_path_buf()));
    let job = Publish {
        testlogs: &[root.to_path_buf()],
        out,
        viewer: &viewer,
        build,
    };
    publish(&job, &mut stdout, &mut stderr).unwrap();
    (String::from_utf8(stdout).unwrap(), String::from_utf8(stderr).unwrap())
}

/// The output with the temporary directories named `<out>`, `<testlogs>` and `<viewer>`, and `/` for a separator.
fn placeholders(text: &str, pairs: &[(&Path, &str)]) -> String {
    let mut text = text.to_owned();
    for (path, name) in pairs {
        text = text
            .replace(&*real(path).to_string_lossy(), name)
            .replace(&*path.to_string_lossy(), name);
    }
    text.replace('\\', "/")
}

/// A build of a TeamCity server.
fn build() -> Build {
    Build {
        server: "https://teamcity.example".to_owned(),
        build_type_id: "Air_UiTests_1".to_owned(),
        id: "1100310296".to_owned(),
        name: "Air Plugin UI Tests / 1".to_owned(),
    }
}

/// The files under a directory, relative and `/`-separated, in path order.
fn files_of(dir: &Path) -> Vec<String> {
    walkdir::WalkDir::new(dir)
        .sort_by_file_name()
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .map(|entry| entry.path().strip_prefix(dir).unwrap().to_string_lossy().replace('\\', "/"))
        .collect()
}

/// A built viewer: its page and one asset.
fn viewer() -> tempfile::TempDir {
    let viewer = tempfile::tempdir().unwrap();
    fs::write(viewer.path().join(VIEWER_PAGE), "<!doctype html>").unwrap();
    fs::create_dir_all(viewer.path().join("assets")).unwrap();
    fs::write(viewer.path().join("assets/index.js"), "export {}").unwrap();
    viewer
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
/// sanitized name get two zips, and the second is reported. A lane without outputs prints nothing. Off TeamCity and
/// without a viewer, runs.json is published with the zips, and no test gets a viewer link.
#[test]
fn the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let (stdout, stderr) = run(root.path(), out.path(), None, None);
    let scanned = Scanner::new(Options::default()).scan(&[Root::new(RootKind::Flag, root.path())]);
    let statuses: BTreeSet<Status> = scanned.bundles.iter().map(|bundle| bundle.status).collect();
    assert_eq!(
        statuses,
        BTreeSet::from([Status::Passed, Status::Failed]),
        "the fixture manifests decode"
    );
    let names = [(out.path(), "<out>"), (root.path(), "<testlogs>")];
    let (stdout, stderr) = (placeholders(&stdout, &names), placeholders(&stderr, &names));
    expect_test::expect_file![testdata("publish.teamcity.txt")].assert_eq(&stdout);
    let manifest = decode_manifest(&example_file(MANIFEST_FILE)).unwrap();
    let class_dir = format!("{}/{}", manifest.run_id, manifest.test_class);
    assert_eq!(
        stderr,
        format!(
            "air-trace teamcity-publish: no viewer: no --viewer DIR, and no checkout to build one in; so air-traces/ \
             holds the bundles and runs.json only\n\
             air-trace teamcity-publish: <testlogs>/plugins/air/tests/integration/ui/ui_lane_test/test.outputs/air-traces/copies/1: \
             another bundle has the name a_b.zip, so this one is <out>/ui_lane_test/{class_dir}/a_b.2.zip\n\
             air-trace teamcity-publish: 4 bundle zip(s) from 3 test.xml file(s) into <out>\n"
        )
    );

    let files = files_of(out.path());
    let zips: Vec<&String> = files.iter().filter(|file| file.ends_with(".zip")).collect();
    assert_eq!(zips.len(), 4, "{files:?}");
    assert_eq!(files.len(), 5, "the zips and {RUNS_FILE}: {files:?}");
    for zip in zips {
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
    let (stdout, stderr) = run(root.path(), out.path(), None, None);
    assert_eq!(stdout.matches(".zip => ").count(), 1, "{stdout}");
    assert!(stderr.contains(": 1 bundle zip(s) from 1 test.xml"), "{stderr}");
}

/// A testlogs tree without bundles prints no message and writes nothing, not even the viewer, and a missing directory
/// is refused.
#[test]
fn testlogs_without_bundles_print_nothing() {
    let root = tempfile::tempdir().unwrap();
    write_xml(&root.path().join("plugins/other/plain_test"), &[("com.example.PlainTest", "works")]);
    let out = tempfile::tempdir().unwrap();
    let viewer = viewer();
    let (stdout, _) = run(root.path(), out.path(), Some(viewer.path()), Some(&build()));
    assert_eq!(stdout, "");
    assert_eq!(fs::read_dir(out.path()).unwrap().count(), 0);

    let missing = [root.path().join("missing")];
    let job = Publish {
        testlogs: &missing,
        out: out.path(),
        viewer: &ViewerSource::Missing,
        build: None,
    };
    let refused = publish(&job, &mut Vec::new(), &mut Vec::new()).unwrap_err();
    assert_eq!(refused.exit, Exit::Usage);
}

/// On TeamCity with a viewer, the listing names the build as the origin of each run, the viewer is copied over the
/// one of an earlier run, and each test links to the viewer page of its trace.
#[test]
fn on_team_city_the_listing_names_the_build_and_each_test_links_its_trace() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let viewer = viewer();
    fs::create_dir_all(out.path().join("assets")).unwrap();
    fs::write(out.path().join("assets/stale.js"), "").unwrap();
    let (stdout, stderr) = run(root.path(), out.path(), Some(viewer.path()), Some(&build()));
    let names = [(out.path(), "<out>"), (root.path(), "<testlogs>")];
    expect_test::expect_file![testdata("publish-on-team-city.teamcity.txt")].assert_eq(&placeholders(&stdout, &names));
    assert!(!stderr.contains("no viewer"), "{stderr}");

    let files = files_of(out.path());
    assert!(!files.contains(&"assets/stale.js".to_owned()), "{files:?}");
    for file in [VIEWER_PAGE, "assets/index.js", RUNS_FILE] {
        assert!(files.contains(&file.to_owned()), "{file}: {files:?}");
    }
    // The path of the testlogs as JSON spells it, without its quotes.
    let json_path = serde_json::to_string(&root.path()).unwrap();
    let listing = String::from_utf8(fs::read(out.path().join(RUNS_FILE)).unwrap())
        .unwrap()
        .replace(&json_path[1..json_path.len() - 1], "<testlogs>");
    expect_test::expect_file![testdata("publish.runs.json")].assert_eq(&listing);
    for run in serde_json::from_str::<Value>(&listing).unwrap()["runs"].as_array().unwrap() {
        for scenario in run["scenarios"].as_array().unwrap() {
            let zip = scenario["zip"].as_str().unwrap();
            assert!(files.contains(&zip.to_owned()), "{zip} is a published zip");
            assert_eq!(scenario["source"]["path"], zip);
        }
    }
}

/// A viewer that is the output, or holds it, is refused before the copy removes or creates anything.
#[test]
fn a_viewer_that_overlaps_the_output_is_refused() {
    let viewer = viewer();
    for out in [viewer.path().to_path_buf(), viewer.path().join("assets/out")] {
        let refused = copy_viewer(viewer.path(), &out).unwrap_err();
        assert!(refused.ends_with(&format!("and the output {} overlap", out.display())), "{refused}");
    }
    assert_eq!(files_of(viewer.path()), ["assets/index.js", VIEWER_PAGE]);
    assert!(!viewer.path().join("assets/out").exists(), "the refused output is not created");
}

/// A viewer that is missing, or a directory without its page, is a line on stderr. The bundles and the listing still
/// publish, and no test gets a link to a page that is not there.
#[test]
fn without_a_viewer_the_bundles_and_the_listing_publish_alone() {
    let root = testlogs();
    for given in [false, true] {
        let out = tempfile::tempdir().unwrap();
        let empty = tempfile::tempdir().unwrap();
        let viewer = given.then_some(empty.path());
        let (stdout, stderr) = run(root.path(), out.path(), viewer, Some(&build()));
        let names = [(out.path(), "<out>"), (empty.path(), "<viewer>")];
        let (stdout, stderr) = (placeholders(&stdout, &names), placeholders(&stderr, &names));
        let reason = if given {
            "<viewer> holds no index.html"
        } else {
            "no --viewer DIR, and no checkout to build one in"
        };
        assert!(
            stderr.starts_with(&format!(
                "air-trace teamcity-publish: no viewer: {reason}; so air-traces/ holds the bundles and runs.json only\n"
            )),
            "{stderr}"
        );
        assert!(!stdout.contains("type='link'"), "{stdout}");
        assert!(
            stdout.contains("##teamcity[publishArtifacts '<out>/runs.json => air-traces']"),
            "{stdout}"
        );
        assert_eq!(stdout.matches(".zip => ").count(), 4, "{stdout}");
        assert_eq!(
            files_of(out.path())
                .iter()
                .filter(|file| !file.ends_with(".zip"))
                .collect::<Vec<_>>(),
            [RUNS_FILE]
        );
    }
}

#[test]
fn a_lane_is_the_target_and_its_shard() {
    assert_eq!(lane_of(Path::new("plugins/air/tests/integration/ui/ui_lane_test")), "ui_lane_test");
    assert_eq!(lane_of(Path::new("a/ui_lane_test/shard_2_of_4")), "ui_lane_test/shard_2_of_4");
    assert_eq!(lane_of(Path::new("a/ui_lane_test/run_1_of_3")), "ui_lane_test/run_1_of_3");
    assert_eq!(lane_of(Path::new("")), "tests");
}
