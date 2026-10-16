use std::fs;
use std::io::{Cursor, Read};
use std::path::Path;
use std::time::{Duration, SystemTime};

use avl_testkit::traces::{copy_tree, example_bundle};
use avl_trace::bundle::{IDEA_LOG_FILE, LOGS_FILE, SNAP_DIR, bundle_file, bundle_path, snap_image_path, snap_tree_path};
use pretty_assertions::assert_eq;
use zip::ZipArchive;
use zip::read::HasZipMetadata;

use super::*;
use crate::testdata::example_manifest;

/// Copies the golden bundle into a fresh trace root at the layout a lane writes,
/// `<root>/<runId>/<TestClass>/<scenario>/`, and answers the root and the bundle's path under it.
fn example_root() -> (tempfile::TempDir, String) {
    let manifest = example_manifest();
    let root = tempfile::tempdir().unwrap();
    let bundle = bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario);
    copy_tree(&example_bundle(), &bundle_file(root.path(), &bundle));
    (root, bundle)
}

fn pack_or_fail(source: &Path, destination: &Path, options: &PackOptions<'_>) -> Report {
    pack(source, destination, options).unwrap_or_else(|error| panic!("pack {}: {error}", source.display()))
}

/// The whole point of fixing times and order: two packs of one tree are one zip, even when the files were touched
/// in between, so a zip can be compared, cached and deduplicated by its bytes.
#[test]
fn pack_is_deterministic() {
    let (root, _) = example_root();
    let out = tempfile::tempdir().unwrap();
    let first = out.path().join("first.zip");
    let second = out.path().join("second.zip");
    let report = pack_or_fail(root.path(), &first, &PackOptions::default());

    let earlier = SystemTime::now() - Duration::from_secs(3600);
    for entry in walkdir::WalkDir::new(root.path()) {
        let entry = entry.unwrap();
        if entry.file_type().is_file() {
            File::options()
                .write(true)
                .open(entry.path())
                .unwrap()
                .set_modified(earlier)
                .unwrap();
        }
    }
    pack_or_fail(root.path(), &second, &PackOptions::default());

    let first_bytes = fs::read(&first).unwrap();
    assert!(first_bytes == fs::read(&second).unwrap(), "two packs of one tree differ");
    assert_eq!((report.bundles, report.bytes), (1, first_bytes.len() as u64));
    assert_eq!(report.destination.as_deref(), Some(first.as_path()));
}

/// Media is stored and text is deflated, entries are in byte order, and a stored entry's bytes sit right after its
/// local header with nothing trailing, which is what the server's ranged reads rely on.
#[test]
fn pack_stores_media_and_deflates_text() {
    let (root, bundle) = example_root();
    let destination = tempfile::tempdir().unwrap();
    let destination = destination.path().join("traces.zip");
    let report = pack_or_fail(root.path(), &destination, &PackOptions::default());

    let raw = fs::read(&destination).unwrap();
    let mut archive = ZipArchive::new(Cursor::new(raw.clone())).unwrap();
    assert_eq!(archive.len(), report.entries);
    let mut names = Vec::new();
    for index in 0..archive.len() {
        let mut file = archive.by_index(index).unwrap();
        let name = file.name().to_owned();
        let stored = [".webp", ".mp4", ".png"].iter().any(|extension| name.ends_with(extension));
        let want = if stored {
            CompressionMethod::Stored
        } else {
            CompressionMethod::Deflated
        };
        assert_eq!(file.compression(), want, "{name}");
        assert_eq!(file.last_modified(), Some(DateTime::default()), "{name} carries another time");
        assert_eq!(file.unix_mode().map(|mode| mode & 0o777), Some(0o644), "{name}");
        assert_eq!(file.get_metadata().system, System::Unix, "{name} names the packing host's system");
        let original = fs::read(bundle_file(root.path(), &name)).unwrap();
        // Reading through the zip reader checks the checksum as well as the bytes.
        let mut content = Vec::new();
        file.read_to_end(&mut content).unwrap();
        assert!(content == original, "{name} does not read back as the file it packed");
        if stored {
            let offset = usize::try_from(file.data_start().unwrap()).unwrap();
            assert!(
                raw[offset..offset + original.len()] == original[..],
                "{name}'s bytes are not at its data offset"
            );
            // The general-purpose flags sit six bytes into the local header, and bit 3 is the trailing descriptor.
            let header = usize::try_from(file.header_start()).unwrap();
            let flags = u16::from_le_bytes([raw[header + 6], raw[header + 7]]);
            assert_eq!(flags & 0x8, 0, "{name} is stored with a trailing data descriptor");
            let crc = u32::from_le_bytes(raw[header + 14..header + 18].try_into().unwrap());
            assert_eq!(crc, file.crc32(), "{name}'s local header does not carry its checksum");
        }
        names.push(name);
    }
    assert!(names.is_sorted(), "entries are not in byte order: {names:?}");
    for want in [
        MANIFEST_FILE,
        SPANS_FILE,
        LOGS_FILE,
        IDEA_LOG_FILE,
        &snap_image_path(1),
        &snap_tree_path(17),
    ] {
        assert!(names.contains(&format!("{bundle}/{want}")), "the zip has no {bundle}/{want}");
    }
}

/// A failed pack leaves the previous zip in place and no temporary file beside it.
#[test]
fn pack_is_atomic() {
    let out = tempfile::tempdir().unwrap();
    let destination = out.path().join("traces.zip");
    fs::write(&destination, "previous").unwrap();
    pack(&out.path().join("missing"), &destination, &PackOptions::default()).unwrap_err();
    assert_eq!(fs::read_to_string(&destination).unwrap(), "previous");
    assert_eq!(
        fs::read_dir(out.path()).unwrap().count(),
        1,
        "a failed pack left a file beside the zip"
    );
}

#[test]
fn pack_refuses_a_destination_inside_the_tree() {
    let (root, _) = example_root();
    let error = pack(root.path(), &root.path().join("traces.zip"), &PackOptions::default()).unwrap_err();
    assert!(error.message().contains("inside the tree"), "{error}");
    let sneaky = root.path().join("elsewhere").join("..").join("traces.zip");
    assert!(
        pack(root.path(), &sneaky, &PackOptions::default()).is_err(),
        "a destination spelled with .. escaped the check"
    );
}

/// A link to a file packs the file's bytes, which is how Bazel's runfiles look; a link to a directory is skipped and
/// reported rather than followed into a possible loop.
#[cfg(unix)]
#[test]
fn pack_follows_file_links_and_skips_directory_links() {
    use std::os::unix::fs::symlink;
    let root = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    let target = outside.path().join("spans.jsonl");
    fs::write(&target, "{}\n").unwrap();
    let bundle = root.path().join("run").join("Class").join("scenario");
    fs::create_dir_all(&bundle).unwrap();
    symlink(&target, bundle.join("spans.jsonl")).unwrap();
    symlink(outside.path(), root.path().join("loop")).unwrap();
    let destination = tempfile::tempdir().unwrap();
    let destination = destination.path().join("traces.zip");
    let report = pack_or_fail(root.path(), &destination, &PackOptions::default());
    assert_eq!((report.entries, report.bundles), (1, 1));
    assert_eq!(
        report.skipped.iter().map(|skipped| skipped.path.as_str()).collect::<Vec<_>>(),
        ["loop"]
    );
    let mut archive = ZipArchive::new(File::open(&destination).unwrap()).unwrap();
    let mut content = String::new();
    archive
        .by_name("run/Class/scenario/spans.jsonl")
        .unwrap()
        .read_to_string(&mut content)
        .unwrap();
    assert_eq!(content, "{}\n");
}

/// A recorder still finishing a bundle beside the pack, after its lane was killed outright, leaves temporary files
/// and renames them into place while the pack runs. Neither may cost the controller the iteration's zip: a
/// temporary file is left out, and so is a file that is gone by the time it is read.
#[test]
fn a_recorder_still_finishing_a_bundle_does_not_fail_the_pack() {
    let (root, bundle) = example_root();
    let partial = bundle_file(root.path(), &bundle)
        .join(SNAP_DIR)
        .join(format!("0018.webp{PARTIAL_SUFFIX}"));
    fs::write(&partial, "half a picture").unwrap();
    let destination = tempfile::tempdir().unwrap();
    let report = pack_or_fail(root.path(), &destination.path().join("traces.zip"), &PackOptions::default());
    let partial_name = format!("{bundle}/{SNAP_DIR}/0018.webp{PARTIAL_SUFFIX}");
    assert!(
        report.skipped.iter().any(|skipped| skipped.path == partial_name),
        "{:?}",
        report.skipped
    );

    let gone = tempfile::tempdir().unwrap();
    let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
    for name in ["gone.json", "gone.webp"] {
        let written = write_entry(
            &mut writer,
            &Entry {
                name: name.to_owned(),
                source: gone.path().join(name),
            },
        )
        .unwrap();
        assert!(!written, "a file that is gone was written");
    }
    let archive = ZipArchive::new(writer.finish().unwrap()).unwrap();
    assert_eq!(archive.len(), 0, "the files that were gone left entries");
}

/// A selection keeps its bundles and the files under them, and a selection of nothing writes no zip.
#[test]
fn a_selection_packs_only_the_bundles_it_keeps() {
    let (root, bundle) = example_root();
    let unfinished = "run/AirOtherTest/running";
    fs::create_dir_all(bundle_file(root.path(), unfinished)).unwrap();
    fs::write(bundle_file(root.path(), unfinished).join(SPANS_FILE), "").unwrap();
    fs::write(root.path().join("stray.txt"), "under no bundle").unwrap();
    let destination = tempfile::tempdir().unwrap();
    let destination = destination.path().join("traces.zip");

    let finished_only = |_: &str, finished: bool| finished;
    let report = pack_or_fail(
        root.path(),
        &destination,
        &PackOptions {
            select: Some(&finished_only),
        },
    );
    assert_eq!(report.packed, std::slice::from_ref(&bundle));
    let archive = ZipArchive::new(File::open(&destination).unwrap()).unwrap();
    assert!(archive.file_names().all(|name| name.starts_with(&format!("{bundle}/"))));

    let nothing = |_: &str, _: bool| false;
    fs::remove_file(&destination).unwrap();
    let report = pack_or_fail(root.path(), &destination, &PackOptions { select: Some(&nothing) });
    assert_eq!((report.destination.as_deref(), report.bundles), (None, 0));
    assert!(!destination.exists(), "a selection of nothing wrote a zip");
}

/// The guest answers the report inside its envelope, and the host decodes the whole report and reads its
/// `destination`; an empty destination is how "nothing new packed" has always been spelled.
#[test]
fn the_report_keeps_its_wire_shape() {
    let report = Report {
        source: "/src".into(),
        destination: None,
        bundles: 0,
        ..Report::default()
    };
    assert_eq!(
        serde_json::to_string(&report).unwrap(),
        r#"{"source":"/src","destination":"","entries":0,"bundles":0,"bytes":0}"#
    );
    let decoded: Report =
        serde_json::from_str(r#"{"source":"/src","destination":"/d.zip","entries":2,"bundles":1,"packed":["a/b/c"],"bytes":9}"#).unwrap();
    assert_eq!(decoded.destination.as_deref(), Some(Path::new("/d.zip")));
    assert_eq!(decoded.packed, vec!["a/b/c".to_owned()]);
}

/// Writes an Allure result for the bundle `bundle` of the run `run` under the root, with one attachment file, and
/// answers the names of the two files in the pack.
fn write_allure_result(root: &Path, run: &str, bundle: &str, uuid: &str) -> [String; 2] {
    use avl_trace::bundle::{
        ALLURE_BUNDLE_LABEL, AllureAttachment, AllureResult, AllureStage, AllureStatus, AllureStatusDetails, allure_attachment_file,
        allure_result_file, encode_allure_result,
    };
    use avl_trace::protocol::Label;
    let source = allure_attachment_file(uuid, "webp");
    let result = AllureResult {
        uuid: uuid.to_owned(),
        history_id: "history".to_owned(),
        full_name: format!("{bundle}.full"),
        name: bundle.to_owned(),
        status: AllureStatus::Passed,
        status_details: AllureStatusDetails::default(),
        stage: AllureStage::Finished,
        start: 1,
        stop: 2,
        labels: vec![Label {
            name: ALLURE_BUNDLE_LABEL.to_owned(),
            value: bundle.to_owned(),
        }],
        steps: Vec::new(),
        attachments: vec![AllureAttachment {
            name: "Video".to_owned(),
            source: source.clone(),
            mime: "video/mp4".to_owned(),
        }],
    };
    let dir = format!("{run}/{ALLURE_RESULTS_DIR}");
    fs::create_dir_all(bundle_file(root, &dir)).unwrap();
    let names = [format!("{dir}/{}", allure_result_file(uuid)), format!("{dir}/{source}")];
    fs::write(bundle_file(root, &names[0]), encode_allure_result(&result).unwrap()).unwrap();
    fs::write(bundle_file(root, &names[1]), "picture").unwrap();
    names
}

/// The Allure results leave with their run. A whole pack keeps every result, and a selection keeps the results of
/// the bundles it keeps, with the files those results name.
#[test]
fn a_pack_keeps_the_allure_results_of_its_bundles() {
    let (root, bundle) = example_root();
    let (run, under_run) = bundle.split_once('/').unwrap();
    let kept = write_allure_result(root.path(), run, under_run, "11111111-1111-8111-8111-111111111111");
    let unfinished = format!("{run}/AirOtherTest/running");
    fs::create_dir_all(bundle_file(root.path(), &unfinished)).unwrap();
    fs::write(bundle_file(root.path(), &unfinished).join(SPANS_FILE), "").unwrap();
    let left = write_allure_result(root.path(), run, "AirOtherTest/running", "22222222-2222-8222-8222-222222222222");
    let broken = format!("{run}/{ALLURE_RESULTS_DIR}/broken{ALLURE_RESULT_SUFFIX}");
    fs::write(bundle_file(root.path(), &broken), "{}").unwrap();
    let out = tempfile::tempdir().unwrap();
    let names = |zip: &Path| -> Vec<String> {
        ZipArchive::new(File::open(zip).unwrap())
            .unwrap()
            .file_names()
            .map(str::to_owned)
            .collect()
    };

    let whole = out.path().join("whole.zip");
    pack_or_fail(root.path(), &whole, &PackOptions::default());
    let packed = names(&whole);
    for name in kept.iter().chain(&left).chain([&broken]) {
        assert!(packed.contains(name), "the whole pack has no {name}");
    }

    let selected = out.path().join("selected.zip");
    let finished_only = |_: &str, finished: bool| finished;
    let report = pack_or_fail(
        root.path(),
        &selected,
        &PackOptions {
            select: Some(&finished_only),
        },
    );
    let packed = names(&selected);
    let results: Vec<&String> = packed.iter().filter(|name| !name.starts_with(&format!("{bundle}/"))).collect();
    let mut want: Vec<&String> = kept.iter().collect();
    want.sort();
    assert_eq!(results, want);
    let skipped: Vec<&str> = report.skipped.iter().map(|skipped| skipped.path.as_str()).collect();
    assert_eq!(skipped, [broken.as_str()]);
}

/// The walk can pass a run's results directory before the recorder writes a result there, and then reach the
/// result's finished bundle. The results directory is listed after the walk, so the pack still holds the result.
/// A file that a kept result names is packed even when no listing held it.
#[test]
fn a_result_written_during_the_walk_leaves_with_its_bundle() {
    let (root, bundle) = example_root();
    let (run, under_run) = bundle.split_once('/').unwrap();
    assert!(
        bundle_file(root.path(), &bundle).join(MANIFEST_FILE).is_file(),
        "the example bundle is not finished"
    );
    let finished_only = |_: &str, finished: bool| finished;
    for results_dir_before_walk in [false, true] {
        let results = bundle_file(root.path(), &format!("{run}/{ALLURE_RESULTS_DIR}"));
        if results.exists() {
            fs::remove_dir_all(&results).unwrap();
        }
        if results_dir_before_walk {
            fs::create_dir_all(&results).unwrap();
        }
        let mut written = None;
        let Collected { mut entries, bundles, .. } = collect(root.path(), &mut || {
            written = Some(write_allure_result(
                root.path(),
                run,
                under_run,
                "33333333-3333-8333-8333-333333333333",
            ));
        })
        .unwrap();
        let [result, attachment] = written.unwrap();
        let listed: Vec<&str> = entries.iter().map(|item| item.name.as_str()).collect();
        assert!(listed.contains(&result.as_str()), "the result is not listed: {listed:?}");

        entries.retain(|item| item.name != attachment);
        let mut skipped = Vec::new();
        let (selected, kept) = select_bundles(entries, bundles, &finished_only, &mut skipped);
        assert_eq!(kept, std::slice::from_ref(&bundle));
        let names: Vec<&str> = selected.iter().map(|item| item.name.as_str()).collect();
        for name in [&result, &attachment] {
            assert!(names.contains(&name.as_str()), "the selection has no {name}: {names:?}");
        }
        let attached = selected.iter().find(|item| item.name == attachment).unwrap();
        assert_eq!(fs::read_to_string(&attached.source).unwrap(), "picture");
        assert_eq!(skipped, []);
    }
}
