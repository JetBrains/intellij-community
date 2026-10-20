//! `air-trace teamcity-publish`: the trace bundles of a Bazel test run as TeamCity artifacts, one zip for each bundle,
//! with a link from each test to its trace.
//!
//! It runs as a build step after `bazel test`, because a Bazel test reports through its JUnit XML and a service
//! message of a test does not reach TeamCity. The step reads every `test.xml` under the testlogs, and the bundles of
//! the `test.outputs` beside it: the directory `air-traces/` and the zip `outputs.zip`, an inner zip included. Each
//! bundle becomes `<out>/<lane>/<runId>/<TestClass>/<scenario>.zip`, with the media stored. The step prints
//! `publishArtifacts` for it into `air-traces/<lane>/<runId>/<TestClass>`, and the `testMetadata` of its test: the
//! trace, the video, and the failure picture of a failed scenario. The test name is the one TeamCity took from the
//! XML, `<classname>.<name>`.
//!
//! The lane is the directory of the `test.xml`, the Bazel target's name, such as `ui_lane_test`, and a shard or run
//! directory below it keeps its target's name in front, such as `ui_lane_test/shard_1_of_2`.
//!
//! A testlogs tree without bundles prints nothing. A bundle that cannot be packed is a line on the standard error, and
//! the other bundles still publish, so a failure of this step never hides the test results.

use std::collections::{BTreeSet, HashSet};
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

use avl_report::teamcity::{Messages, PublishedTrace, TRACES_ARTIFACT_DIR};
use avl_trace::bundle::{ROOT_DIR_NAME, VIDEO_FILE, bundle_path};
use avl_trace_tools::discover::{Bundle, Location, Options, Root, RootKind, Scanner, Status};
use avl_trace_tools::pack::{PackOptions, pack, pack_zip_bundle};

use crate::{Exit, Refusal, report, usage};

/// `air-trace teamcity-publish`'s command line.
#[derive(clap::Args, Clone, Debug, PartialEq, Eq)]
pub(crate) struct PublishArgs {
    /// A Bazel testlogs directory to read; repeatable. Without one, every `bazel-out/*/testlogs` beside this program's
    /// own output, which is where `bazel run` finds it.
    #[arg(long = "testlogs", value_name = "DIR")]
    pub testlogs: Vec<PathBuf>,
    /// Where the bundle zips are written (default <workspace>/out/air-traces-publish).
    #[arg(long, value_name = "DIR")]
    pub out: Option<PathBuf>,
}

/// The name of a test's JUnit XML in the testlogs.
const TEST_XML: &str = "test.xml";
/// The directory of a test's undeclared outputs in the testlogs, beside its XML.
const TEST_OUTPUTS: &str = "test.outputs";
/// The zip of a test's undeclared outputs, in [TEST_OUTPUTS].
const OUTPUTS_ZIP: &str = "outputs.zip";

/// Runs `air-trace teamcity-publish`: the service messages on stdout, a summary line and each problem on stderr. It
/// answers 0 once the testlogs are read, whatever a bundle did.
pub(crate) fn run_publish(args: &PublishArgs, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let workspace = std::env::var_os("BUILD_WORKSPACE_DIRECTORY")
        .filter(|value| !value.is_empty())
        .map(PathBuf::from);
    let testlogs = if args.testlogs.is_empty() {
        own_testlogs()
    } else {
        args.testlogs.clone()
    };
    if testlogs.is_empty() {
        return report(
            stderr,
            "teamcity-publish",
            &usage("no testlogs: this program is not in a bazel-out tree; pass --testlogs DIR"),
        );
    }
    let out = match args.out.clone().map_or_else(
        || {
            let base = workspace.clone().map_or_else(std::env::current_dir, Ok)?;
            Ok(base.join("out").join("air-traces-publish"))
        },
        |out| std::path::absolute(&out),
    ) {
        Ok(out) => out,
        Err(error) => return report(stderr, "teamcity-publish", &usage(format!("--out: {error}"))),
    };
    match publish(&testlogs, &out, stdout, stderr) {
        Ok(()) => Exit::Ok.code(),
        Err(refused) => report(stderr, "teamcity-publish", &refused),
    }
}

/// Every `<bazel-out>/<configuration>/testlogs` of the `bazel-out` this program was built in.
fn own_testlogs() -> Vec<PathBuf> {
    let Ok(exe) = std::env::current_exe() else {
        return Vec::new();
    };
    let exe = fscopy::resolve_links(&exe).unwrap_or(exe);
    let Some(bazel_out) = exe
        .ancestors()
        .find(|ancestor| ancestor.file_name().is_some_and(|name| name == "bazel-out"))
    else {
        return Vec::new();
    };
    let Ok(configurations) = fs::read_dir(bazel_out) else {
        return Vec::new();
    };
    let mut found: Vec<PathBuf> = configurations
        .filter_map(Result::ok)
        .map(|entry| entry.path().join("testlogs"))
        .filter(|testlogs| testlogs.is_dir())
        .collect();
    found.sort();
    found
}

/// Publishes the bundles under each testlogs directory into out.
pub(crate) fn publish(testlogs: &[PathBuf], out: &Path, stdout: &mut dyn Write, stderr: &mut dyn Write) -> Result<(), Refusal> {
    let write_failed = |error: std::io::Error| Refusal::new("output_failed", Exit::Broken, error.to_string());
    let mut messages = Messages::default();
    // One scanner for the whole run: it keeps the extractions of inner zips, which the packs read, until the end.
    let scanner = Scanner::new(Options::default());
    let mut written = BTreeSet::new();
    let (mut xmls, mut problems, mut published) = (0, 0, 0);
    for root in testlogs {
        if !root.is_dir() {
            return Err(usage(format!("--testlogs {}: not a directory", root.display())));
        }
        for xml in test_xmls(root) {
            xmls += 1;
            let dir = xml.parent().unwrap_or(root);
            let bundles = bundles_beside(&scanner, dir);
            if bundles.is_empty() {
                continue;
            }
            // The cases of this XML only: a bundle beside it never names the test of another target.
            let cases: Vec<(String, String)> = match fs::read(&xml) {
                Ok(content) => bt_junit::parse_report(&String::from_utf8_lossy(&content))
                    .suites
                    .iter()
                    .flat_map(|suite| &suite.cases)
                    .map(|case| (case.class_name.clone(), case.name.clone()))
                    .collect(),
                Err(error) => {
                    problems += 1;
                    writeln!(stderr, "air-trace teamcity-publish: {}: {error}", xml.display()).map_err(write_failed)?;
                    Vec::new()
                }
            };
            let lane = lane_of(dir.strip_prefix(root).unwrap_or(dir));
            let mut seen = HashSet::new();
            for bundle in bundles {
                // One bundle in both `air-traces/` and `outputs.zip` of one test has two ids, so it is known by what its
                // manifest says: its run, its class, its scenario and its start. Another test's bundle is its own.
                let identity = (
                    bundle.run_id.clone(),
                    bundle.test_class.clone(),
                    bundle.scenario.clone(),
                    bundle.started_at.clone(),
                );
                if !seen.insert(identity) {
                    continue;
                }
                let placed = bundle_path(&bundle.run_id, &bundle.test_class, &bundle.scenario);
                let (class_dir, scenario) = placed.rsplit_once('/').unwrap_or(("", &placed));
                let target = format!("{TRACES_ARTIFACT_DIR}/{lane}/{class_dir}");
                let dir = lane
                    .split('/')
                    .chain(class_dir.split('/'))
                    .fold(out.to_path_buf(), |path, segment| path.join(segment));
                // Two names can sanitize to one file name; the later bundle takes the next free `<scenario>.<n>.zip`.
                let mut zip = dir.join(format!("{scenario}.zip"));
                let mut attempt = 1;
                while written.contains(&zip) {
                    attempt += 1;
                    zip = dir.join(format!("{scenario}.{attempt}.zip"));
                }
                if attempt > 1 {
                    writeln!(
                        stderr,
                        "air-trace teamcity-publish: {}: another bundle has the name {scenario}.zip, so this one is {}",
                        bundle.source.path,
                        zip.display()
                    )
                    .map_err(write_failed)?;
                }
                written.insert(zip.clone());
                let packed = match &bundle.location {
                    Location::Dir(dir) => pack(dir, &zip, &PackOptions::default()),
                    Location::Zip { path, prefix } => pack_zip_bundle(path, prefix, &zip),
                    Location::File => continue,
                };
                if let Err(error) = packed {
                    problems += 1;
                    writeln!(stderr, "air-trace teamcity-publish: {}: {error}", bundle.source.path).map_err(write_failed)?;
                    continue;
                }
                let failed = matches!(bundle.status, Status::Failed | Status::Aborted);
                let zip_text = zip.to_string_lossy();
                let lines = messages.published(&PublishedTrace {
                    zip: &zip_text,
                    target: &target,
                    test_class: &bundle.test_class,
                    scenario: &bundle.scenario,
                    video: bundle.has_video.then_some(VIDEO_FILE),
                    still: bundle.thumbnail.as_deref().filter(|_| failed),
                    cases: &cases,
                });
                for line in lines {
                    writeln!(stdout, "{line}").map_err(write_failed)?;
                }
                published += 1;
            }
        }
    }
    writeln!(
        stderr,
        "air-trace teamcity-publish: {published} bundle zip(s) from {xmls} test.xml file(s) into {}{}",
        out.display(),
        if problems > 0 {
            format!("; {problems} problem(s) above")
        } else {
            String::new()
        }
    )
    .map_err(write_failed)?;
    Ok(())
}

/// Every `test.xml` under a testlogs directory, in path order.
fn test_xmls(root: &Path) -> Vec<PathBuf> {
    walkdir::WalkDir::new(root)
        .sort_by_file_name()
        .into_iter()
        .filter_entry(|entry| entry.file_name() != TEST_OUTPUTS)
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file() && entry.file_name() == TEST_XML)
        .map(walkdir::DirEntry::into_path)
        .collect()
}

/// The bundles of the outputs beside a `test.xml`: under `test.outputs/air-traces/` and in `test.outputs/outputs.zip`.
fn bundles_beside(scanner: &Scanner, dir: &Path) -> Vec<Bundle> {
    let outputs = dir.join(TEST_OUTPUTS);
    let roots: Vec<Root> = [outputs.join(ROOT_DIR_NAME), outputs.join(OUTPUTS_ZIP)]
        .into_iter()
        .filter(|path| path.exists())
        .map(|path| Root::new(RootKind::Flag, path))
        .collect();
    if roots.is_empty() {
        return Vec::new();
    }
    let found = scanner.scan(&roots);
    let mut bundles: Vec<Bundle> = found.bundles.iter().map(|bundle| (**bundle).clone()).collect();
    bundles
        .sort_by(|left, right| (&left.run_id, &left.test_class, &left.scenario).cmp(&(&right.run_id, &right.test_class, &right.scenario)));
    bundles
}

/// The lane of a `test.xml` directory, relative to its testlogs: the Bazel target's name, and a shard or run
/// directory below it with the target's name in front.
fn lane_of(relative: &Path) -> String {
    let segments: Vec<String> = relative
        .components()
        .map(|component| component.as_os_str().to_string_lossy().into_owned())
        .collect();
    match segments.as_slice() {
        [] => "tests".to_owned(),
        [.., target, last] if last.starts_with("shard_") || last.starts_with("run_") => format!("{target}/{last}"),
        [.., last] => last.clone(),
    }
}

#[cfg(test)]
mod tests;
