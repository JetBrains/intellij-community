use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::time::{Duration, SystemTime};

use avl_testkit::traces::{copy_tree, example_bundle, example_file};
use avl_trace::bundle::{MANIFEST_FILE, VIDEO_INDEX_FILE, bundle_file, decode_manifest};
use avl_trace_tools::artifacts::FAILED_DIR;
use avl_trace_tools::pack::{PackOptions, pack};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;

fn real(path: &Path) -> PathBuf {
    fscopy::resolve_links(path).unwrap()
}

fn testdata(name: &str) -> PathBuf {
    avl_testkit::testdata_path!(name)
}

/// The publish of `testlogs` into `out` with the links to [CI_SITE]: off TeamCity, with no start, into an output of its
/// own, and with the packed bundles kept.
fn default_job<'a>(testlogs: &'a [PathBuf], out: &'a Path) -> Publish<'a> {
    Publish {
        testlogs,
        out,
        site: CI_SITE,
        build: None,
        since: None,
        out_owned: true,
        remove_packed: false,
        affected: None,
    }
}

/// Publishes the testlogs `root` into `out` with the links to [CI_SITE], with the stdout and the stderr of the step.
fn run(root: &Path, out: &Path, build: Option<&Build>) -> (String, String) {
    run_job(&Publish {
        build,
        ..default_job(&[root.to_path_buf()], out)
    })
}

/// Runs one publish, with the stdout and the stderr of the step.
fn run_job(job: &Publish<'_>) -> (String, String) {
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    publish(job, &mut stdout, &mut stderr).unwrap();
    (String::from_utf8(stdout).unwrap(), String::from_utf8(stderr).unwrap())
}

/// Sets the time a file was last changed.
fn set_modified(path: &Path, time: SystemTime) {
    fs::File::options().write(true).open(path).unwrap().set_modified(time).unwrap();
}

/// The output with the temporary directories named `<out>` and `<testlogs>`, and `/` for a separator.
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
        revision: None,
        changed_files: None,
        checkout_dir: None,
    }
}

/// The start of the metadata of one published trace.
const TRACE_METADATA: &str = "name='trace' type='artifact'";

/// The entries of a zip in their order, one line each: `stored` or `deflated`, then the name.
fn listing_of(zip: &Path) -> String {
    let mut archive = zip::ZipArchive::new(fs::File::open(zip).unwrap()).unwrap();
    (0..archive.len())
        .map(|index| {
            let entry = archive.by_index(index).unwrap();
            let method = if entry.compression() == zip::CompressionMethod::Stored {
                "stored"
            } else {
                "deflated"
            };
            format!("{method} {}\n", entry.name())
        })
        .collect()
}

/// The bytes of the entry `name` of a zip.
fn entry_of(zip: &Path, name: &str) -> Vec<u8> {
    let mut archive = zip::ZipArchive::new(fs::File::open(zip).unwrap()).unwrap();
    let mut entry = archive.by_name(name).unwrap_or_else(|error| panic!("{name}: {error}"));
    let mut bytes = Vec::new();
    io::Read::read_to_end(&mut entry, &mut bytes).unwrap();
    bytes
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

/// Each bundle is one zip under its lane, run and class in the one archive. It is named by the case of its own XML, so
/// two lanes of one test class name two tests. A failed bundle links its video and its last picture, published as loose
/// files. Two scenarios of one sanitized name get two zips, and the second is reported. A lane without outputs prints
/// nothing. The archive holds runs.json first, then the zips. Off TeamCity, no test gets a link.
#[test]
fn the_bundles_of_the_testlogs_are_published_with_the_metadata_of_their_tests() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let (stdout, stderr) = run(root.path(), out.path(), None);
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
    let lines: Vec<&str> = stderr.lines().collect();
    assert_eq!(
        lines[0],
        "air-trace teamcity-publish: <testlogs>/plugins/air/tests/integration/ui/ui_lane_test/test.outputs/air-traces/copies/1: \
         another bundle has the name a_b.zip, so this one is a_b.2.zip",
        "{stderr}"
    );
    assert!(
        lines[1].starts_with("air-trace teamcity-publish: air-traces.zip holds 5 entries in "),
        "{stderr}"
    );
    assert_eq!(
        lines[2..],
        ["air-trace teamcity-publish: 4 bundle zip(s) from 3 test.xml file(s) into <out>"]
    );

    let archived = out.path().join(ARCHIVE);
    assert!(
        listing_of(&archived).starts_with("deflated runs.json\nstored "),
        "the listing comes first, then the zips"
    );
    let names: Vec<String> = listing_of(&archived)
        .lines()
        .map(|line| line.split_once(' ').unwrap().1.to_owned())
        .collect();
    for zip in names.iter().filter(|name| name.ends_with(".zip")) {
        let mut bundle = zip::ZipArchive::new(io::Cursor::new(entry_of(&archived, zip))).unwrap();
        assert!(bundle.by_name(MANIFEST_FILE).is_ok(), "{zip} holds the bundle at its top");
    }
    let failed = out
        .path()
        .join(FAILED_DIR)
        .join(format!("ui_lane_test/{class_dir}/rename-closed-project-session"));
    assert_eq!(fs::read(failed.join(VIDEO_FILE)).unwrap(), b"not really a video");
    let still = bundle_file(
        &root
            .path()
            .join("plugins/air/tests/integration/ui/ui_lane_test/test.outputs/air-traces"),
        &bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario),
    )
    .join(manifest_thumbnail());
    assert_eq!(fs::read(failed.join("failure.webp")).unwrap(), fs::read(still).unwrap());
    let loose: Vec<String> = files_of(&out.path().join(FAILED_DIR));
    assert_eq!(loose.len(), 2, "only the failed scenario has loose files: {loose:?}");

    let (again, _) = run(root.path(), out.path(), None);
    let second = tempfile::tempdir().unwrap();
    run(root.path(), second.path(), None);
    assert!(
        fs::read(&archived).unwrap() == fs::read(second.path().join(ARCHIVE)).unwrap(),
        "two publishes of one testlogs tree write one archive"
    );
    assert_eq!(placeholders(&again, &names_of(out.path())), stdout);
}

/// The placeholders of [placeholders] for an output.
fn names_of(out: &Path) -> Vec<(&Path, &'static str)> {
    vec![(out, "<out>")]
}

/// The picture of the last snapshot of the golden bundle, a path inside it.
fn manifest_thumbnail() -> String {
    let scanned = Scanner::new(Options::default()).scan(&[Root::new(RootKind::Flag, example_bundle())]);
    scanned.bundles[0].thumbnail.clone().unwrap()
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
    let (stdout, stderr) = run(root.path(), out.path(), None);
    assert_eq!(stdout.matches(TRACE_METADATA).count(), 1, "{stdout}");
    assert!(stderr.contains(": 1 bundle zip(s) from 1 test.xml"), "{stderr}");
}

/// A testlogs tree without bundles prints no message and writes nothing, not even the listing. A missing testlogs
/// directory is a warning, and the other directories still publish.
#[test]
fn testlogs_without_bundles_print_nothing() {
    let root = tempfile::tempdir().unwrap();
    write_xml(&root.path().join("plugins/other/plain_test"), &[("com.example.PlainTest", "works")]);
    let out = tempfile::tempdir().unwrap();
    let (stdout, _) = run(root.path(), out.path(), Some(&build()));
    assert_eq!(stdout, "");
    assert_eq!(fs::read_dir(out.path()).unwrap().count(), 0);

    let missing = root.path().join("missing");
    let bundles = testlogs();
    let testlogs = [missing.clone(), bundles.path().to_path_buf()];
    let (stdout, stderr) = run_job(&default_job(&testlogs, out.path()));
    let problem = format!("no testlogs: {}: not a directory", missing.display());
    assert!(
        stdout.starts_with(&format!(
            "##teamcity[message text='air-trace teamcity-publish: {problem}' status='WARNING']\n"
        )),
        "{stdout}"
    );
    assert!(stderr.starts_with(&format!("air-trace teamcity-publish: {problem}\n")), "{stderr}");
    assert_eq!(
        stdout.matches(TRACE_METADATA).count(),
        4,
        "the other testlogs still publish: {stdout}"
    );
    assert!(stderr.ends_with("; 1 problem(s) above\n"), "{stderr}");
}

/// `bazel run` starts the binary through a link into the `-opt` configuration of `bazel-out`. The TeamCity step and
/// both roots of the checkout lay it out so. The testlogs are those of the other configurations. A program outside
/// a `bazel-out` finds none.
#[cfg(unix)]
#[test]
fn the_testlogs_are_found_beside_the_binary_that_bazel_run_starts() {
    let base = tempfile::tempdir().unwrap();
    let bazel_out = base.path().join("execroot/_main/bazel-out");
    let real = bazel_out.join("local_linux-opt/bin/external/community+/tools/vm/crates/air-trace/air-trace");
    fs::create_dir_all(real.parent().unwrap()).unwrap();
    fs::write(&real, "").unwrap();
    let link = bazel_out.join("local_linux-fastbuild/bin/external/community+/tools/vm/air-trace");
    fs::create_dir_all(link.parent().unwrap()).unwrap();
    std::os::unix::fs::symlink(&real, &link).unwrap();
    let convenience = base.path().join("out/bazel-bin");
    fs::create_dir_all(convenience.parent().unwrap()).unwrap();
    std::os::unix::fs::symlink(bazel_out.join("local_linux-fastbuild/bin"), &convenience).unwrap();
    for configuration in ["local_linux-fastbuild", "jvm-fastbuild"] {
        fs::create_dir_all(bazel_out.join(configuration).join("testlogs")).unwrap();
    }
    let expected: Vec<PathBuf> = ["jvm-fastbuild", "local_linux-fastbuild"]
        .iter()
        .map(|configuration| real_path(&bazel_out).join(configuration).join("testlogs"))
        .collect();
    for exe in [&real, &link, &convenience.join("external/community+/tools/vm/air-trace")] {
        assert_eq!(testlogs_beside(exe), expected, "{}", exe.display());
    }
    let outside = base.path().join("bin/air-trace");
    fs::create_dir_all(outside.parent().unwrap()).unwrap();
    fs::write(&outside, "").unwrap();
    assert_eq!(testlogs_beside(&outside), Vec::<PathBuf>::new());
}

/// The real path of a directory, as the discovery resolves the binary: `/private/var` for `/var` on macOS.
#[cfg(unix)]
fn real_path(path: &Path) -> PathBuf {
    real(path)
}

/// On TeamCity, the listing names the build as the origin of each run. The archive holds the listing first, then the
/// zips, and no viewer page. Each test links to the CI trace page of its zip on the site, and the build log names the
/// CI page of the build.
#[test]
fn on_team_city_the_listing_names_the_build_and_each_test_links_its_trace() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let (stdout, _) = run(root.path(), out.path(), Some(&build()));
    let names = [(out.path(), "<out>"), (root.path(), "<testlogs>")];
    expect_test::expect_file![testdata("publish-on-team-city.teamcity.txt")].assert_eq(&placeholders(&stdout, &names));

    let archived = out.path().join(ARCHIVE);
    let listed = listing_of(&archived);
    expect_test::expect_file![testdata("publish.archive.txt")].assert_eq(&listed);
    let files: Vec<String> = listed.lines().map(|line| line.split_once(' ').unwrap().1.to_owned()).collect();
    // The path of the testlogs as JSON spells it, without its quotes.
    let json_path = serde_json::to_string(&root.path()).unwrap();
    let listing = String::from_utf8(entry_of(&archived, RUNS_FILE))
        .unwrap()
        .replace(&json_path[1..json_path.len() - 1], "<testlogs>");
    expect_test::expect_file![testdata("publish.runs.json")].assert_eq(&listing);
    for run in serde_json::from_str::<Value>(&listing).unwrap()["runs"].as_array().unwrap() {
        for scenario in run["scenarios"].as_array().unwrap() {
            let zip = scenario["zip"].as_str().unwrap();
            assert!(files.contains(&zip.to_owned()), "{zip} is a zip of the archive");
            assert!(
                !zip.starts_with('/')
                    && zip
                        .split('/')
                        .all(|segment| !segment.is_empty() && segment != ".." && segment != "."),
                "{zip} stays inside the directory of runs.json, as the portal requires"
            );
            assert_eq!(scenario["source"]["path"], zip);
            assert!(
                stdout.contains(&format!("value='{}']", ci_trace_url(CI_SITE, &build().id, zip))),
                "the test of {zip} links its trace: {stdout}"
            );
        }
    }
}

/// What the changed files of the build reach is the entry `affected.json` of the archive, after the listing. A build
/// without it has no such entry.
#[test]
fn the_archive_holds_what_the_changed_files_reach_after_the_listing() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let document = AffectedDocument {
        schema: 1,
        revision: "4444".to_owned(),
        commits: Vec::new(),
    };
    run_job(&Publish {
        build: Some(&build()),
        affected: Some(&document),
        ..default_job(&[root.path().to_path_buf()], out.path())
    });
    let archived = out.path().join(ARCHIVE);
    assert!(
        listing_of(&archived).starts_with("deflated runs.json\ndeflated affected.json\nstored "),
        "{}",
        listing_of(&archived)
    );
    let written: Value = serde_json::from_slice(&entry_of(&archived, AFFECTED_FILE)).unwrap();
    assert_eq!(written, serde_json::json!({"schema": 1, "revision": "4444", "commits": []}));

    let (_, _) = run(root.path(), out.path(), Some(&build()));
    assert!(!listing_of(&archived).contains(AFFECTED_FILE), "{}", listing_of(&archived));
}

/// The links open the site that the step names, such as a staging portal, and so does the page of the build.
#[test]
fn the_links_open_the_site_that_the_step_names() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let (stdout, _) = run_job(&Publish {
        site: "https://staging.example/air/",
        build: Some(&build()),
        ..default_job(&[root.path().to_path_buf()], out.path())
    });
    let links: Vec<&str> = stdout.lines().filter(|line| line.contains("type='link'")).collect();
    assert_eq!(links.len(), 4, "{stdout}");
    for link in links {
        assert!(
            link.contains("value='https://staging.example/air/ci/1100310296/trace?zip="),
            "{link}"
        );
    }
    assert!(
        stdout.contains("##teamcity[message text='The traces of this build: https://staging.example/air/ci/1100310296']\n"),
        "{stdout}"
    );
    assert!(!stdout.contains(CI_SITE), "{stdout}");
}

/// With a start, a test whose `test.xml` is older is the test of an earlier build. It is one line on stderr, and nothing
/// of it is published. The other tests publish.
#[test]
fn the_tests_of_an_earlier_build_are_skipped() {
    let root = testlogs();
    let stale = root.path().join("plugins/air/tests/integration/ui/ui_lane_test");
    set_modified(&stale.join(TEST_XML), SystemTime::now() - Duration::from_hours(2));
    let out = tempfile::tempdir().unwrap();
    let (stdout, stderr) = run_job(&Publish {
        since: Some(SystemTime::now() - Duration::from_hours(1)),
        ..default_job(&[root.path().to_path_buf()], out.path())
    });
    assert_eq!(
        stdout.matches(TRACE_METADATA).count(),
        1,
        "only the shard of ui-real publishes: {stdout}"
    );
    assert!(!stdout.contains("ui_lane_test/"), "{stdout}");
    let skipped = format!("air-trace teamcity-publish: skipped {}: its test.xml is from ", stale.display());
    assert!(
        stderr
            .lines()
            .any(|line| line.starts_with(&skipped) && line.ends_with(", so an earlier build ran it")),
        "{stderr}"
    );
    let warning = "##teamcity[message text='air-trace teamcity-publish: 1 test(s) skipped, because their test.xml is older than ";
    assert!(stdout.starts_with(warning), "a warning counts the skipped tests: {stdout}");

    let (stdout, _) = run_job(&Publish {
        since: Some(SystemTime::now() + Duration::from_hours(1)),
        ..default_job(&[root.path().to_path_buf()], out.path())
    });
    assert!(
        stdout.starts_with("##teamcity[message text='air-trace teamcity-publish: 3 test(s) skipped, because their test.xml")
            && stdout.contains("and no other test has a trace, so nothing is published; check AIR_TRACE_SINCE or --since"),
        "a warning says that nothing is left: {stdout}"
    );
    assert!(!stdout.contains("name='trace' type='artifact'"), "{stdout}");
}

/// Each run empties its output first, so the output holds the files of this run only. The step empties its own
/// output, and an output with its marker. It refuses another output that holds files, and then publishes nothing.
#[test]
fn the_output_holds_the_files_of_this_run_only() {
    let root = testlogs();
    let out = tempfile::tempdir().unwrap();
    let old = out.path().join("ui_lane_test/iter-0/OldTest/old.zip");
    fs::create_dir_all(old.parent().unwrap()).unwrap();
    fs::write(&old, "").unwrap();
    let testlogs = [root.path().to_path_buf()];
    let job = |out_owned| Publish {
        out_owned,
        ..default_job(&testlogs, out.path())
    };
    let (stdout, _) = run_job(&job(true));
    assert!(
        !old.exists() && !out.path().join("ui_lane_test/iter-0").exists(),
        "the zip of an earlier run is gone"
    );
    assert!(out.path().join(OUT_MARKER).is_file());
    assert_eq!(stdout.matches(TRACE_METADATA).count(), 4, "{stdout}");

    let (stdout, _) = run_job(&job(false));
    assert_eq!(
        stdout.matches(TRACE_METADATA).count(),
        4,
        "an output with the marker is emptied and published: {stdout}"
    );

    let foreign = tempfile::tempdir().unwrap();
    let kept = foreign.path().join("notes.txt");
    fs::write(&kept, "mine").unwrap();
    let (stdout, stderr) = run_job(&Publish {
        out: foreign.path(),
        ..job(false)
    });
    let refused = format!(
        "--out {} holds files that this step did not write; pass an empty directory; nothing is published",
        foreign.path().display()
    );
    assert!(stdout.contains(&refused) && !stdout.contains(TRACE_METADATA), "{stdout}");
    assert!(stderr.contains(&refused), "{stderr}");
    assert_eq!(fs::read_to_string(&kept).unwrap(), "mine");
    assert_eq!(fs::read_dir(foreign.path()).unwrap().count(), 1);
}

/// Under `bazel run`, a relative path of the command line is taken from the directory where the command ran. It is not
/// taken from the runfiles tree that the program starts in. An absolute path stays as it is.
#[test]
fn a_relative_path_is_taken_from_the_directory_where_bazel_run_ran() {
    let checkout = tempfile::tempdir().unwrap();
    let working = checkout.path().join("plugins/air");
    assert_eq!(
        given_path(Path::new("out/air-traces-publish"), Some(&working)).unwrap(),
        working.join("out/air-traces-publish")
    );
    let absolute = checkout.path().join("testlogs");
    assert_eq!(given_path(&absolute, Some(&working)).unwrap(), absolute);
    assert_eq!(
        given_path(Path::new("testlogs"), None).unwrap(),
        std::env::current_dir().unwrap().join("testlogs")
    );
}

/// The bytes of the files under a directory, links not followed.
/// The start of the build is `--since`, else `AIR_TRACE_SINCE`, which the first step of the build sets with the clock
/// of the agent. A value that does not read is a warning. On TeamCity without either, a warning says that every test
/// publishes, and off TeamCity no start is no problem.
#[test]
fn the_start_of_the_build_is_the_flag_or_the_variable_of_the_first_step() {
    let time = |text: &str| text.parse::<jiff::Timestamp>().unwrap();
    let set = |value: &'static str| move |name: &str| (name == SINCE_VARIABLE).then(|| value.to_owned());
    let unset = |_: &str| None;
    assert_eq!(
        since_of(Some(time("2026-10-21T08:00:00Z")), &set("2026-10-21T09:00:00Z"), true),
        Ok(Some((SystemTime::from(time("2026-10-21T08:00:00Z")), "--since")))
    );
    assert_eq!(
        since_of(None, &set(" 2026-10-21T09:00:00Z\n"), true),
        Ok(Some((SystemTime::from(time("2026-10-21T09:00:00Z")), SINCE_VARIABLE)))
    );
    let refused = since_of(None, &set("yesterday"), true).unwrap_err();
    assert!(
        refused.starts_with("AIR_TRACE_SINCE: yesterday is not a time such as 2026-10-21T08:00:00Z: ")
            && refused.ends_with("; so the tests of earlier builds publish too"),
        "{refused}"
    );
    assert_eq!(
        since_of(None, &set(" "), true),
        Err(
            "neither --since nor AIR_TRACE_SINCE names the start of the build, so the tests of earlier builds publish \
             too"
            .to_owned()
        )
    );
    assert_eq!(since_of(None, &unset, false), Ok(None));
}

fn bytes_of(dir: &Path) -> u64 {
    walkdir::WalkDir::new(dir)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .map(|entry| entry.metadata().unwrap().len())
        .sum()
}

/// Once the archive is published, the step removes the bundle directories that it holds from the testlogs. It also
/// removes a read-only one, and says the bytes it removes. The other outputs of the test stay: its `outputs.zip`, the
/// evidence of the lane, and the Allure results. Without the removal, the testlogs keep every bundle.
#[test]
fn the_packed_bundles_leave_the_testlogs_once_the_archive_is_published() {
    let root = testlogs();
    let outputs = root.path().join("plugins/air/tests/integration/ui/ui_lane_test").join(TEST_OUTPUTS);
    let traces = outputs.join(ROOT_DIR_NAME);
    let manifest = decode_manifest(&example_file(MANIFEST_FILE)).unwrap();
    let allure = traces.join(&manifest.run_id).join("allure-results/result.json");
    fs::create_dir_all(allure.parent().unwrap()).unwrap();
    fs::write(&allure, "{}").unwrap();
    let evidence = outputs.join("lane/idea.log");
    fs::create_dir_all(evidence.parent().unwrap()).unwrap();
    fs::write(&evidence, "log").unwrap();
    let bundles = [
        bundle_file(&traces, &bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario)),
        bundle_file(&traces, "copies/0"),
        bundle_file(&traces, "copies/1"),
    ];
    let bytes: u64 = bundles.iter().map(|bundle| bytes_of(bundle)).sum();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&bundles[1], fs::Permissions::from_mode(0o555)).unwrap();
    }
    let sharded = root
        .path()
        .join("plugins/air/tests/integration/ui-real/ui-real_lane_test/shard_1_of_2");
    let job = |remove_packed, out: &Path| {
        let testlogs = [root.path().to_path_buf()];
        run_job(&Publish {
            remove_packed,
            ..default_job(&testlogs, out)
        })
    };

    let kept = tempfile::tempdir().unwrap();
    let (_, stderr) = job(false, kept.path());
    assert!(bundles.iter().all(|bundle| bundle.join(MANIFEST_FILE).is_file()), "{stderr}");
    assert!(!stderr.contains("removed "), "{stderr}");

    let out = tempfile::tempdir().unwrap();
    let (stdout, stderr) = job(true, out.path());
    assert!(
        stderr.contains(&format!(
            "air-trace teamcity-publish: removed 3 packed bundle(s) with {bytes} bytes from the testlogs\n"
        )),
        "{stderr}"
    );
    assert!(!stdout.contains("status='WARNING']\n##teamcity[message"), "{stdout}");
    for bundle in &bundles {
        assert!(!bundle.exists(), "{} is removed", bundle.display());
    }
    assert!(!traces.join("copies").exists(), "an emptied directory goes too");
    assert_eq!(fs::read_to_string(&allure).unwrap(), "{}");
    assert_eq!(fs::read_to_string(&evidence).unwrap(), "log");
    assert!(sharded.join(TEST_OUTPUTS).join(OUTPUTS_ZIP).is_file(), "a zip of the outputs stays");
    let names: Vec<String> = listing_of(&out.path().join(ARCHIVE)).lines().map(str::to_owned).collect();
    assert_eq!(names.len(), 5, "the archive holds every bundle: {names:?}");
}

/// A directory that is a link, or that lies outside the traces of its test, stays, and the removal says why.
#[cfg(unix)]
#[test]
fn a_packed_directory_outside_the_traces_or_behind_a_link_stays() {
    let root = tempfile::tempdir().unwrap();
    let traces = root.path().join("ui_lane_test").join(TEST_OUTPUTS).join(ROOT_DIR_NAME);
    let outside = root.path().join("elsewhere/bundle");
    fs::create_dir_all(&outside).unwrap();
    fs::write(outside.join(MANIFEST_FILE), "{}").unwrap();
    fs::create_dir_all(&traces).unwrap();
    let link = traces.join("linked");
    std::os::unix::fs::symlink(&outside, &link).unwrap();
    let packed = |dir: &Path| PackedDir {
        dir: dir.to_path_buf(),
        traces: traces.clone(),
        root: root.path().to_path_buf(),
        left_out: Vec::new(),
    };
    let removal = remove_packed(&[packed(&outside), packed(&link)]);
    assert_eq!(removal.removed, 0);
    assert!(
        removal.problems[0].ends_with(&format!(
            "is not inside {} of the testlogs {}",
            real(&traces).display(),
            real(root.path()).display()
        )),
        "{:?}",
        removal.problems
    );
    assert!(
        removal.problems[1].ends_with("stays in the testlogs: it is not a directory"),
        "{:?}",
        removal.problems
    );
    assert!(outside.join(MANIFEST_FILE).is_file() && link.exists());
}

/// A bundle directory stays, with a warning, when its zip does not hold all of it. Examples are a link to a directory
/// and a link that does not resolve. That content has no other copy. A temporary file of a recorder keeps no bundle.
#[cfg(unix)]
#[test]
fn a_bundle_that_its_zip_does_not_hold_whole_stays_in_the_testlogs() {
    let root = testlogs();
    let traces = root
        .path()
        .join("plugins/air/tests/integration/ui/ui_lane_test")
        .join(TEST_OUTPUTS)
        .join(ROOT_DIR_NAME);
    let manifest = decode_manifest(&example_file(MANIFEST_FILE)).unwrap();
    let partial = bundle_file(&traces, &bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario))
        .join(format!("{}/0099.webp{PARTIAL_SUFFIX}", avl_trace::bundle::SNAP_DIR));
    fs::create_dir_all(partial.parent().unwrap()).unwrap();
    fs::write(&partial, "half").unwrap();
    let elsewhere = tempfile::tempdir().unwrap();
    let linked = bundle_file(&traces, "copies/0");
    std::os::unix::fs::symlink(elsewhere.path(), linked.join("tree")).unwrap();
    let broken = bundle_file(&traces, "copies/1");
    std::os::unix::fs::symlink(elsewhere.path().join("gone"), broken.join("gone")).unwrap();
    let out = tempfile::tempdir().unwrap();
    let testlogs = [root.path().to_path_buf()];
    let (stdout, stderr) = run_job(&Publish {
        remove_packed: true,
        ..default_job(&testlogs, out.path())
    });

    assert!(stderr.contains("removed 1 packed bundle(s) with "), "{stderr}");
    assert!(!partial.exists(), "a temporary file of a recorder keeps no bundle");
    assert!(
        stderr.contains(&format!(
            "{} stays in the testlogs: its zip does not hold tree (a symbolic link to something other than a file)",
            real(&linked).display()
        )),
        "{stderr}"
    );
    assert!(
        stderr.contains(&format!(
            "{} stays in the testlogs: its zip does not hold gone (a symbolic link that does not resolve",
            real(&broken).display()
        )),
        "{stderr}"
    );
    assert_eq!(stdout.matches("stays in the testlogs").count(), 2, "one warning each: {stdout}");
    assert!(linked.join(MANIFEST_FILE).is_file() && linked.join("tree").is_symlink());
    assert!(broken.join(MANIFEST_FILE).is_file() && broken.join("gone").is_symlink());
}
