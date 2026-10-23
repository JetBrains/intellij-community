use std::fs;
use std::io::Cursor;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use avl_testkit::traces::{copy_tree, example_bundle, example_file};
use avl_trace::bundle::{ROOT_DIR_NAME, SNAP_DIR, bundle_file, bundle_path};
use pretty_assertions::assert_eq;
use zip::ZipArchive;

use super::scan::{glob_pattern, glob_watch};
use super::*;
use crate::pack::{PackOptions, pack};
use crate::testdata::example_manifest;

fn real_path(path: &Path) -> PathBuf {
    fscopy::resolve_links(path).unwrap()
}

/// The golden bundle's directory inside a packed `outputs.zip`.
fn example_entry(manifest: &Manifest) -> String {
    format!(
        "{ROOT_DIR_NAME}/{}/",
        bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario)
    )
}

/// Packs the golden bundle under `air-traces/`, the way Bazel zips a test's undeclared outputs, into a new zip in
/// its own directory, and answers the zip.
fn packed_example(manifest: &Manifest, keep: &tempfile::TempDir) -> PathBuf {
    let outputs = tempfile::tempdir().unwrap();
    copy_tree(&example_bundle(), &bundle_file(outputs.path(), &example_entry(manifest)));
    let zip = keep.path().join("outputs.zip");
    pack(outputs.path(), &zip, &PackOptions::default()).unwrap();
    zip
}

/// Bundle ids are durable: reports, journals and printed URLs hold them. The vectors were computed outside this
/// code, as the SHA-256 of the three parts.
/// The runs glob spells the traces directory out; this keeps it the one the controller writes into.
#[test]
fn the_runs_glob_names_the_run_traces_directory() {
    assert_eq!(VM_RUNS_GLOB, format!("*/{VM_RUN_TRACES_DIR}/*/*.zip"));
}

#[test]
fn a_bundle_id_is_the_digest_of_its_place() {
    assert_eq!(
        bundle_id(SourceKind::Zip, "dropped.zip", "air-traces/iter-1/AirExampleUiTest/example/"),
        "1e22c7da3b3e50409fdc"
    );
    assert_eq!(bundle_id(SourceKind::Dir, "/traces/run/Class/scenario", ""), "ec373110dffe4ac48f35");
}

/// A directory root and a zip root each hold the golden bundle, and each is named by its manifest, not by where it
/// sits: the directory copy is under another run and scenario name.
#[test]
fn a_scan_names_a_directory_bundle_and_a_zipped_one_by_their_manifests() {
    let manifest = example_manifest();
    let dir_root = tempfile::tempdir().unwrap();
    copy_tree(
        &example_bundle(),
        &bundle_file(dir_root.path(), "elsewhere/NotTheClass/not-the-scenario"),
    );
    let zip_dir = tempfile::tempdir().unwrap();
    let zip = packed_example(&manifest, &zip_dir);

    let scanner = Scanner::new(Options::default());
    let found = scanner.scan(&[
        Root::new(RootKind::Flag, dir_root.path()),
        Root::new(RootKind::BazelTestlogs, zip_dir.path()),
    ]);
    let counts: Vec<(bool, usize)> = found.roots.iter().map(|root| (root.exists, root.bundles)).collect();
    assert_eq!(counts, [(true, 1), (true, 1)], "{:?}", found.roots);
    let mut kinds = Vec::new();
    for bundle in &found.bundles {
        kinds.push(bundle.source.kind);
        assert_eq!(
            (
                bundle.run_id.as_str(),
                bundle.test_class.as_str(),
                bundle.scenario.as_str(),
                bundle.status
            ),
            (
                manifest.run_id.as_str(),
                manifest.test_class.as_str(),
                manifest.scenario.as_str(),
                manifest.status.into()
            ),
            "the {} bundle does not read its manifest",
            bundle.source.kind
        );
        assert!(
            Arc::ptr_eq(&found.by_id[&bundle.id], bundle),
            "the {} bundle is not found by its id",
            bundle.source.kind
        );
    }
    kinds.sort();
    assert_eq!(kinds, [SourceKind::Dir, SourceKind::Zip]);
    let real_zip = real_path(&zip);
    let zipped = &found.by_id[&bundle_id(SourceKind::Zip, &real_zip.to_string_lossy(), &example_entry(&manifest))];
    assert_eq!(
        zipped.location,
        Location::Zip {
            path: real_zip.clone(),
            prefix: example_entry(&manifest)
        }
    );
    assert!(found.under_roots(&real_zip) && !found.under_roots(Path::new("/elsewhere")));

    // The zip is read in place: a stored entry is one range of the file, a deflated one is inflated.
    let index = scanner.zips().get(&real_zip).unwrap();
    let image = format!("{}snap/0001.webp", example_entry(&manifest));
    let meta = &index.entries()[&image];
    assert!(meta.stored);
    assert_eq!(index.read_stored(meta, 0..meta.size).unwrap(), example_file("snap/0001.webp"));
    let manifest_entry = format!("{}{MANIFEST_FILE}", example_entry(&manifest));
    assert_eq!(index.read_entry(&manifest_entry, 1 << 20).unwrap(), example_file(MANIFEST_FILE));

    // A second scan of unchanged roots answers the same bundles.
    let again = scanner.scan(&[
        Root::new(RootKind::Flag, dir_root.path()),
        Root::new(RootKind::BazelTestlogs, zip_dir.path()),
    ]);
    assert_eq!(
        again.bundles.iter().map(|bundle| &bundle.summary).collect::<Vec<_>>(),
        found.bundles.iter().map(|bundle| &bundle.summary).collect::<Vec<_>>()
    );
    scanner.close();
}

/// A zip already open and one bundle file read alone are named by the same readers a scan uses, so the planner
/// names a dropped bundle the way the server lists it.
#[test]
fn a_zip_in_memory_and_a_lone_bundle_file_are_read_like_a_scanned_bundle() {
    let manifest = example_manifest();
    let keep = tempfile::tempdir().unwrap();
    let content = fs::read(packed_example(&manifest, &keep)).unwrap();
    let mut archive = ZipArchive::new(Cursor::new(content)).unwrap();
    let entry = example_entry(&manifest);
    let bundles = read_zip(&mut archive, "dropped.zip");
    assert_eq!(bundles.len(), 1);
    assert_eq!(
        (bundles[0].scenario.as_str(), bundles[0].status),
        (manifest.scenario.as_str(), manifest.status.into())
    );
    assert_eq!(
        bundles[0].source,
        Source {
            kind: SourceKind::Zip,
            path: "dropped.zip".to_owned(),
            entry: entry.clone()
        }
    );
    assert_eq!(bundles[0].id, bundle_id(SourceKind::Zip, "dropped.zip", &entry));

    let alone = read_file(MANIFEST_FILE, &example_file(MANIFEST_FILE), "bundle.json");
    assert_eq!(
        (alone.scenario.as_str(), alone.status),
        (manifest.scenario.as_str(), manifest.status.into())
    );
    assert_eq!(
        alone.source,
        Source {
            kind: SourceKind::File,
            path: "bundle.json".to_owned(),
            entry: String::new()
        }
    );
    for name in [SPANS_FILE, LOGS_FILE] {
        let read = read_file(name, &example_file(name), name);
        assert_eq!(
            (read.run_id.as_str(), read.scenario.as_str(), read.status),
            (manifest.run_id.as_str(), manifest.scenario.as_str(), Status::Truncated),
            "{name} read alone"
        );
    }
    let spans = read_file(SPANS_FILE, &example_file(SPANS_FILE), "spans");
    assert_eq!(
        (spans.test_class.as_str(), spans.flow.as_ref()),
        (manifest.test_class.as_str(), manifest.flow.as_ref())
    );
    let logs = read_file(LOGS_FILE, &example_file(LOGS_FILE), "logs");
    assert!(
        logs.started_at.is_some() && logs.duration_ms > 0,
        "the log records give the start and the duration: {logs:?}"
    );
    let broken = read_file(MANIFEST_FILE, b"{}", "bundle.json");
    assert_eq!(broken.status, Status::Invalid);
    assert!(broken.error.is_some());
}

/// Wraps zips into one more zip, deflated, as TeamCity packs the testlogs into `bazel.logs.zip`: each `(entry, zip)`
/// is one inner zip. Answers the outer zip, in its own directory under keep.
fn zip_of_zips(inner: &[(&str, &Path)], keep: &tempfile::TempDir) -> PathBuf {
    let outer = keep.path().join("bazel.logs.zip");
    let mut writer = zip::ZipWriter::new(fs::File::create(&outer).unwrap());
    let options = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    for (entry, zip) in inner {
        writer.start_file(*entry, options).unwrap();
        std::io::copy(&mut fs::File::open(zip).unwrap(), &mut writer).unwrap();
    }
    writer.finish().unwrap();
    outer
}

/// Bazel's `outputs.zip` inside TeamCity's `bazel.logs.zip` is read as a zip of its own. Its bundle names the outer
/// zip and the inner entry, its files are served from the extraction, and a second scanner reuses that extraction.
/// An inner zip without a bundle leaves no extraction behind.
#[test]
fn a_zip_inside_a_zip_is_extracted_once_and_read_as_a_zip() {
    let manifest = example_manifest();
    let keep = tempfile::tempdir().unwrap();
    let outputs = packed_example(&manifest, &keep);
    let empty = keep.path().join("empty.zip");
    zip::ZipWriter::new(fs::File::create(&empty).unwrap()).finish().unwrap();
    let inner_entry = "abc/execroot/_main/bazel-out/k8-fastbuild/testlogs/ui/ui_lane_test/test.outputs/outputs.zip";
    let outer_dir = tempfile::tempdir().unwrap();
    let outer = zip_of_zips(
        &[(inner_entry, &outputs), ("abc/other/test.outputs/outputs.zip", &empty)],
        &outer_dir,
    );
    let nested = tempfile::tempdir().unwrap();
    let options = || Options {
        nested_dir: Some(nested.path().to_path_buf()),
        ..Options::default()
    };

    let scanner = Scanner::new(options());
    let found = scanner.scan(&[Root::new(RootKind::Flag, &outer)]);
    assert_eq!(found.bundles.len(), 1, "{:?}", found.roots);
    let bundle = &found.bundles[0];
    let container = format!("{}!/{inner_entry}", real_path(&outer).to_string_lossy());
    let entry = example_entry(&manifest);
    assert_eq!(
        bundle.source,
        Source {
            kind: SourceKind::Zip,
            path: container.clone(),
            entry: entry.clone()
        }
    );
    assert_eq!(bundle.id, bundle_id(SourceKind::Zip, &container, &entry));
    assert_eq!(bundle.scenario, manifest.scenario);
    let Location::Zip { path: extracted, prefix } = &bundle.location else {
        panic!("a nested bundle is a zip bundle: {:?}", bundle.location);
    };
    assert!(extracted.starts_with(nested.path()), "{}", extracted.display());
    assert_eq!(prefix, &entry);
    let index = scanner.zips().get(extracted).unwrap();
    let image = &index.entries()[&format!("{entry}snap/0001.webp")];
    assert_eq!(index.read_stored(image, 0..image.size).unwrap(), example_file("snap/0001.webp"));
    let extractions = || fs::read_dir(nested.path()).unwrap().count();
    assert_eq!(extractions(), 1, "the inner zip without a bundle is removed");

    let written = fs::metadata(extracted).unwrap().modified().unwrap();
    let again = Scanner::new(options()).scan(&[Root::new(RootKind::Flag, &outer)]);
    assert_eq!(again.bundles[0].summary, bundle.summary);
    assert_eq!(
        fs::metadata(extracted).unwrap().modified().unwrap(),
        written,
        "the extraction is reused"
    );
    assert_eq!(extractions(), 1);

    let private = Scanner::new(Options::default()).scan(&[Root::new(RootKind::Flag, &outer)]);
    assert_eq!(private.bundles[0].id, bundle.id, "a private nested directory finds the same bundle");

    let mut archive = ZipArchive::new(Cursor::new(fs::read(&outer).unwrap())).unwrap();
    let in_memory = read_zip(&mut archive, "bazel.logs.zip");
    assert_eq!(in_memory.len(), 1);
    assert_eq!(in_memory[0].source.path, format!("bazel.logs.zip!/{inner_entry}"));
    scanner.close();
}

/// A root's origin, such as the TeamCity build of a downloaded `bazel.logs.zip`, is on the root and on each of its
/// bundles; the bundle's id does not change with it.
#[test]
fn the_origin_of_a_root_is_on_each_of_its_bundles() {
    let manifest = example_manifest();
    let keep = tempfile::tempdir().unwrap();
    let zip = packed_example(&manifest, &keep);
    let origin = Origin {
        name: "Air Plugin UI Tests / 1".to_owned(),
        id: "1099482304".to_owned(),
        url: None,
    };
    let scanner = Scanner::new(Options::default());
    let plain = scanner.scan(&[Root::new(RootKind::Flag, &zip)]);
    let found = scanner.scan(&[Root {
        origin: Some(origin.clone()),
        ..Root::new(RootKind::TeamCity, &zip)
    }]);
    assert_eq!(found.roots[0].origin.as_ref(), Some(&origin));
    assert_eq!(found.bundles[0].origin.as_ref(), Some(&origin));
    assert_eq!(
        (plain.bundles[0].origin.as_ref(), &plain.bundles[0].id),
        (None, &found.bundles[0].id)
    );
}

/// A zip inside a bundle is one of its files, not a zip to open.
#[test]
fn a_zip_inside_a_bundle_is_not_opened() {
    let manifest = example_manifest();
    let keep = tempfile::tempdir().unwrap();
    let inner = packed_example(&manifest, &keep);
    let root = tempfile::tempdir().unwrap();
    let bundle = bundle_file(root.path(), "run/AirExampleUiTest/example");
    copy_tree(&example_bundle(), &bundle);
    fs::copy(&inner, bundle.join("attachment.zip")).unwrap();
    let packed = tempfile::tempdir().unwrap();
    let zip = packed.path().join("traces.zip");
    pack(root.path(), &zip, &PackOptions::default()).unwrap();
    let nested = tempfile::tempdir().unwrap();
    let found = Scanner::new(Options {
        nested_dir: Some(nested.path().to_path_buf()),
        ..Options::default()
    })
    .scan(&[Root::new(RootKind::Flag, &zip)]);
    assert_eq!(found.bundles.len(), 1);
    assert_eq!(fs::read_dir(nested.path()).unwrap().count(), 0);
}

/// Requests that open one outer zip at once share one extraction, and each reads it. An extraction is read as a zip
/// of its own, so a zip inside it is a file, even when the cache opens the extraction again.
#[test]
fn concurrent_reads_share_an_extraction_that_opens_no_second_level() {
    let manifest = example_manifest();
    let keep = tempfile::tempdir().unwrap();
    let deeper = packed_example(&manifest, &keep);
    // The inner zip holds a bundle and, beside it, one more zip with a bundle: two levels down from the outer zip.
    let tree = tempfile::tempdir().unwrap();
    copy_tree(&example_bundle(), &bundle_file(tree.path(), &example_entry(&manifest)));
    fs::create_dir_all(tree.path().join("extra")).unwrap();
    fs::copy(&deeper, tree.path().join("extra").join("deeper.zip")).unwrap();
    let inner_dir = tempfile::tempdir().unwrap();
    let inner = inner_dir.path().join("outputs.zip");
    pack(tree.path(), &inner, &PackOptions::default()).unwrap();
    let outer_dir = tempfile::tempdir().unwrap();
    let outer = zip_of_zips(&[("a/outputs.zip", &inner)], &outer_dir);
    let outer = real_path(&outer);
    let nested = tempfile::tempdir().unwrap();
    let scanner = Scanner::new(Options {
        nested_dir: Some(nested.path().to_path_buf()),
        ..Options::default()
    });

    let counts: Vec<usize> = std::thread::scope(|scope| {
        let readers: Vec<_> = (0..8)
            .map(|_| scope.spawn(|| scanner.zips().get(&outer).unwrap().bundles("outer").len()))
            .collect();
        readers.into_iter().map(|reader| reader.join().unwrap()).collect()
    });
    assert_eq!(counts, [1; 8], "only the bundle one level deep is found");
    let index = scanner.zips().get(&outer).unwrap();
    let extraction = index.inner()[0].index.path.clone();
    scanner.zips().clear();
    let reopened = scanner.zips().get(&extraction).unwrap();
    assert!(reopened.inner().is_empty(), "an extraction opens no inner zip");
    assert_eq!(reopened.bundles("x").len(), 1);
    let image = &reopened.entries()[&format!("{}snap/0001.webp", example_entry(&manifest))];
    assert_eq!(reopened.read_stored(image, 0..image.size).unwrap(), example_file("snap/0001.webp"));
}

/// An inner `.zip` that is no zip is a problem of its root, and leaves no extraction behind. The reader of a zip in
/// memory reports it too, so the planner can say why it found no bundle.
#[test]
fn an_inner_zip_that_does_not_read_is_reported_and_leaves_nothing() {
    let keep = tempfile::tempdir().unwrap();
    let bogus = keep.path().join("bogus.zip");
    fs::write(&bogus, b"not a zip").unwrap();
    let outer_dir = tempfile::tempdir().unwrap();
    let outer = zip_of_zips(&[("a/test.outputs/outputs.zip", &bogus)], &outer_dir);
    let nested = tempfile::tempdir().unwrap();
    let found = Scanner::new(Options {
        nested_dir: Some(nested.path().to_path_buf()),
        ..Options::default()
    })
    .scan(&[Root::new(RootKind::Flag, &outer)]);
    assert!(found.bundles.is_empty());
    let problems = &found.roots[0].problems;
    assert_eq!(problems.len(), 1, "{problems:?}");
    assert!(
        problems[0].starts_with(&format!("{}!/a/test.outputs/outputs.zip: ", real_path(&outer).display())),
        "{problems:?}"
    );
    assert_eq!(fs::read_dir(nested.path()).unwrap().count(), 0, "the failed extraction is removed");

    let mut archive = ZipArchive::new(Cursor::new(fs::read(&outer).unwrap())).unwrap();
    let (bundles, errors) = read_zip_reporting(&mut archive, "bazel.logs.zip");
    assert!(bundles.is_empty());
    assert_eq!(errors.len(), 1, "{errors:?}");
    assert!(errors[0].starts_with("a/test.outputs/outputs.zip: "), "{errors:?}");
}

/// A bundle without its manifest is named by its logs and its path: running while its files still change,
/// truncated once they stopped, and always truncated inside a zip.
#[test]
fn a_bundle_without_a_manifest_is_named_by_its_logs_and_its_path() {
    let root = tempfile::tempdir().unwrap();
    let bundle = bundle_file(root.path(), "run-7/AirExampleUiTest/example");
    copy_tree(&example_bundle(), &bundle);
    fs::remove_file(bundle.join(MANIFEST_FILE)).unwrap();
    let manifest = example_manifest();

    let running = Scanner::new(Options::default()).scan(&[Root::new(RootKind::Flag, root.path())]);
    let summary = &running.bundles[0].summary;
    assert_eq!(summary.status, Status::Running);
    assert_eq!(
        (summary.run_id.as_str(), summary.test_class.as_str()),
        (manifest.run_id.as_str(), "AirExampleUiTest")
    );
    assert_eq!(summary.scenario, manifest.scenario, "the root span's start names the scenario");
    assert_eq!(summary.thumbnail.as_deref(), Some("snap/0012.webp"));
    assert!(running.watch.contains(&real_path(&bundle)), "a running bundle is watched");

    let later = Options {
        now: Box::new(|| std::time::SystemTime::now() + std::time::Duration::from_secs(3600)),
        ..Options::default()
    };
    let stale = Scanner::new(later).scan(&[Root::new(RootKind::Flag, root.path())]);
    assert_eq!(stale.bundles[0].status, Status::Truncated);
}

/// The thumbnail is the last picture of `snap/` by name in either format, in a directory and in a zip. A lossy bundle
/// holds AVIF stills, which a tree file of the same ordinal does not hide.
#[test]
fn the_thumbnail_of_a_lossy_bundle_is_its_last_avif_still() {
    let manifest = example_manifest();
    let outputs = tempfile::tempdir().unwrap();
    let bundle = bundle_file(outputs.path(), &example_entry(&manifest));
    copy_tree(&example_bundle(), &bundle);
    let snap = bundle.join(SNAP_DIR);
    for entry in fs::read_dir(&snap).unwrap() {
        let path = entry.unwrap().path();
        if path.extension().is_some_and(|extension| extension == "webp") {
            fs::rename(&path, path.with_extension("avif")).unwrap();
        }
    }
    let keep = tempfile::tempdir().unwrap();
    let zip = keep.path().join("outputs.zip");
    pack(outputs.path(), &zip, &PackOptions::default()).unwrap();

    let found = Scanner::new(Options::default()).scan(&[
        Root::new(RootKind::Flag, outputs.path()),
        Root::new(RootKind::BazelTestlogs, keep.path()),
    ]);
    let thumbnails: Vec<Option<&str>> = found.bundles.iter().map(|bundle| bundle.summary.thumbnail.as_deref()).collect();
    assert_eq!(thumbnails, [Some("snap/0012.avif"); 2]);
}

/// A walk never enters `.git` or `node_modules`, and one with a bound stops at it, keeps what it found, and says so.
/// The bound is for a directory named without knowing whether it is a trace root, such as the checkout.
#[test]
fn a_walk_skips_git_and_node_modules_and_stops_at_its_bound() {
    let root = tempfile::tempdir().unwrap();
    for skipped in [".git", "node_modules"] {
        copy_tree(
            &example_bundle(),
            &bundle_file(root.path(), &format!("{skipped}/run/AirSkippedTest/skipped")),
        );
    }
    for index in 0..5 {
        fs::create_dir_all(root.path().join("a").join(index.to_string())).unwrap();
    }
    copy_tree(&example_bundle(), &bundle_file(root.path(), "z/run/AirLastTest/last"));

    let unbounded = Scanner::new(Options::default()).scan(&[Root::new(RootKind::Flag, root.path())]);
    assert_eq!(
        (unbounded.bundles.len(), unbounded.roots[0].problems.len()),
        (1, 0),
        "{:?}",
        unbounded.roots[0].problems
    );

    let bounded = Scanner::new(Options {
        max_entries: Some(4),
        ..Options::default()
    })
    .scan(&[Root::new(RootKind::Flag, root.path())]);
    assert!(bounded.bundles.is_empty());
    assert_eq!(bounded.roots[0].problems.len(), 1);
    assert!(
        bounded.roots[0].problems[0].contains("holds more than 4 entries"),
        "{:?}",
        bounded.roots[0].problems
    );
}

/// The VM reports glob is watched where a new match can appear as a new entry, and not in a run's report
/// directory, which holds every file of the report and which kqueue would hold a descriptor open for each of.
#[test]
fn the_vm_reports_glob_is_not_watched_inside_report_directories() {
    let workers = tempfile::tempdir().unwrap();
    let workers = real_path(workers.path());
    let run = workers.join("air-linux-1").join("reports").join("run-1");
    fs::create_dir_all(run.join("traces")).unwrap();
    assert_eq!(
        glob_watch(&workers, VM_REPORTS_GLOB),
        [workers.clone(), workers.join("air-linux-1").join("reports"), run.join("traces")]
    );
}

/// The directory of a glob is escaped, and its root is not: the `?` of a Windows verbatim prefix is a prefix, not a
/// pattern.
#[test]
fn a_glob_escapes_its_directory_and_keeps_the_root() {
    assert_eq!(
        glob_pattern(Path::new("/runs/a[1]"), "*/traces/*.zip"),
        "/runs/a[[]1[]]/*/traces/*.zip"
    );
    #[cfg(windows)]
    {
        assert_eq!(
            glob_pattern(Path::new(r"\\?\C:\runs\a[1]"), "*/traces/*.zip"),
            r"\\?\C:\runs\a[[]1[]]/*/traces/*.zip"
        );
        assert_eq!(glob_pattern(Path::new(r"C:\runs"), "*.zip"), r"C:\runs/*.zip");
    }
}

/// The controller's zips are found by the globs under its runtime root, and a runtime root that could not be
/// resolved says so on both of its roots.
#[test]
fn the_default_roots_find_the_controllers_zips() {
    let manifest = example_manifest();
    let runtime = tempfile::tempdir().unwrap();
    let traces = bundle_file(runtime.path(), "workers/air-linux-1/reports/run-1/traces");
    fs::create_dir_all(&traces).unwrap();
    let keep = tempfile::tempdir().unwrap();
    fs::copy(packed_example(&manifest, &keep), traces.join("iter-1.zip")).unwrap();

    let roots = default_roots(None, Ok(runtime.path()));
    assert_eq!(
        roots.iter().map(|root| root.kind).collect::<Vec<_>>(),
        [RootKind::VmReports, RootKind::VmRuns]
    );
    let found = Scanner::new(Options::default()).scan(&roots);
    assert_eq!(found.bundles.len(), 1, "{:?}", found.roots);
    assert_eq!(found.bundles[0].source.kind, SourceKind::Zip);
    assert!(!found.roots[1].exists, "the runs directory does not exist yet");

    let unresolved = default_roots(Some(Path::new("/repo")), Err("AIR_VM_RUNTIME_ROOT is not absolute"));
    assert_eq!(
        unresolved.iter().map(|root| root.kind).collect::<Vec<_>>(),
        [RootKind::BazelTestlogs, RootKind::VmReports, RootKind::VmRuns]
    );
    let states = Scanner::new(Options::default()).scan(&unresolved).roots;
    assert_eq!(states[1].error.as_deref(), Some("AIR_VM_RUNTIME_ROOT is not absolute"));
    assert_eq!(states[2].error.as_deref(), Some("AIR_VM_RUNTIME_ROOT is not absolute"));
}

#[test]
fn a_bundle_path_is_clean_or_nothing() {
    for clean in ["spans.jsonl", "snap/0001.webp", "a.b/c"] {
        assert_eq!(clean_bundle_path(clean), Some(clean));
    }
    for refused in ["", "/etc/passwd", "../x", "a/../b", "a//b", "./a", "a/", "a\\b", "a\0b"] {
        assert_eq!(clean_bundle_path(refused), None, "{refused:?}");
    }
}
