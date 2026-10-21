//! `air-trace teamcity-publish`: the trace bundles of a Bazel test run as TeamCity artifacts, one zip for each bundle,
//! with a listing and the trace viewer beside them, and a link from each test to its trace.
//!
//! It runs as a build step after `bazel test`, because a Bazel test reports through its JUnit XML and a service
//! message of a test does not reach TeamCity. The step reads every `test.xml` under the testlogs, and the bundles of
//! the `test.outputs` beside it: the directory `air-traces/` and the zip `outputs.zip`, an inner zip included. Each
//! bundle becomes `<out>/<lane>/<runId>/<TestClass>/<scenario>.zip`, with the media stored. The step prints
//! `publishArtifacts` for it into `air-traces/<lane>/<runId>/<TestClass>`, and the `testMetadata` of its test: the
//! trace, the viewer page of the trace, the video, and the failure picture of a failed scenario. The test name is the
//! one TeamCity took from the XML, `<classname>.<name>`.
//!
//! The lane is the directory of the `test.xml`, the Bazel target's name, such as `ui_lane_test`, and a shard or run
//! directory below it keeps its target's name in front, such as `ui_lane_test/shard_1_of_2`.
//!
//! Beside the zips, `<out>/runs.json` lists the published bundles as `GET /__air/runs` of `air-trace serve` lists
//! bundles, with the server's own types. Each scenario carries its `zip`, relative to the listing, and is a zip at that
//! path in its `source` and its id. On TeamCity, each run names the build as its `origin`, from the agent's property
//! files ([teamcity]). The viewer goes into `<out>` too: the one of `--viewer`, or one that the step builds in the
//! checkout ([viewer]). So `air-traces/index.html` opens the listing, and each test links to
//! `index.html#/trace?zip=<zip>`. The link needs both the viewer and the build.
//!
//! A testlogs tree without bundles prints nothing and writes nothing. Each problem is a line on the standard error and
//! a warning `message` on the standard output: no testlogs, a testlogs directory that is missing, an output that does
//! not read, a bundle that cannot be packed, a missing viewer, and build properties that do not read. The rest still
//! publishes, and the step answers 0, so it never turns a build red. Only a usage error of the command line answers 2.

use std::collections::{BTreeSet, HashSet};
use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use avl_report::teamcity::{Messages, PublishedTrace, TRACES_ARTIFACT_DIR, warning};
use avl_trace::bundle::{ROOT_DIR_NAME, VIDEO_FILE, bundle_path};
use avl_trace_tools::discover::{
    Bundle, Location, Options, Origin, Root, RootKind, RootState, Scanner, Source, SourceKind, Status, Summary, bundle_id,
};
use avl_trace_tools::pack::{PackOptions, pack, pack_zip_bundle};

use crate::serve::{Runs, Scenario, group_runs, listed};
use crate::{Exit, Refusal, report, usage};
use teamcity::Build;
use viewer::{VIEWER_BUILD_TIMEOUT, ViewerSource, copy_viewer, viewer_dir};

mod teamcity;
mod viewer;

/// `air-trace teamcity-publish`'s command line.
#[derive(clap::Args, Clone, Debug, PartialEq, Eq)]
pub(crate) struct PublishArgs {
    /// A Bazel testlogs directory to read; repeatable. Without one, every `bazel-out/*/testlogs` beside this program's
    /// own output, which is where `bazel run` finds it.
    #[arg(long = "testlogs", value_name = "DIR")]
    pub testlogs: Vec<PathBuf>,
    /// Where the bundle zips, runs.json and the viewer are written (default <workspace>/out/air-traces-publish).
    #[arg(long, value_name = "DIR")]
    pub out: Option<PathBuf>,
    /// The built trace viewer, a directory with index.html, which is published beside the bundles. Without it, the step
    /// builds the viewer in the checkout with `pnpm run build:viewer`, within 10 minutes. Without a viewer, the bundles
    /// and runs.json publish alone and the tests get no viewer link.
    #[arg(long, value_name = "DIR")]
    pub viewer: Option<PathBuf>,
}

/// The name of a test's JUnit XML in the testlogs.
const TEST_XML: &str = "test.xml";
/// The directory of a test's undeclared outputs in the testlogs, beside its XML.
const TEST_OUTPUTS: &str = "test.outputs";
/// The zip of a test's undeclared outputs, in [TEST_OUTPUTS].
const OUTPUTS_ZIP: &str = "outputs.zip";
/// The listing of the published bundles, at the top of the output.
const RUNS_FILE: &str = "runs.json";
/// The generation of the listing: the step writes one listing, as a server's first scan answers generation 1.
const LISTING_GENERATION: u64 = 1;

/// What one publish reads and where it writes.
pub(crate) struct Publish<'a> {
    pub testlogs: &'a [PathBuf],
    pub out: &'a Path,
    pub viewer: &'a ViewerSource,
    /// The TeamCity build that the step runs in, or `None` off TeamCity.
    pub build: Option<&'a Build>,
}

/// Runs `air-trace teamcity-publish`: the service messages on stdout, a summary line and each problem on stderr. It
/// answers 0 whatever it found, and 2 only for a usage error of the command line.
pub(crate) fn run_publish(args: &PublishArgs, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let directory = |name: &str| std::env::var_os(name).filter(|value| !value.is_empty()).map(PathBuf::from);
    let workspace = directory("BUILD_WORKSPACE_DIRECTORY");
    let working = directory("BUILD_WORKING_DIRECTORY");
    let given = |path: &Path| given_path(path, working.as_deref());
    let testlogs = if args.testlogs.is_empty() {
        own_testlogs()
    } else {
        match args.testlogs.iter().map(|dir| given(dir)).collect::<io::Result<Vec<_>>>() {
            Ok(testlogs) => testlogs,
            Err(error) => return report(stderr, "teamcity-publish", &usage(format!("--testlogs: {error}"))),
        }
    };
    if testlogs.is_empty() {
        let _ = warn(
            stdout,
            stderr,
            "no testlogs: no --testlogs DIR, and no bazel-out/*/testlogs beside this program; nothing to publish",
        );
        return Exit::Ok.code();
    }
    let out = match args.out.clone().map_or_else(
        || {
            let base = workspace.clone().map_or_else(std::env::current_dir, Ok)?;
            Ok(base.join("out").join("air-traces-publish"))
        },
        |out| given(&out),
    ) {
        Ok(out) => out,
        Err(error) => return report(stderr, "teamcity-publish", &usage(format!("--out: {error}"))),
    };
    let viewer_dir = match args.viewer.as_deref().map(given).transpose() {
        Ok(viewer) => viewer,
        Err(error) => return report(stderr, "teamcity-publish", &usage(format!("--viewer: {error}"))),
    };
    let variable = |name: &str| std::env::var(name).ok();
    let build = Build::of_step(&variable).unwrap_or_else(|reason| {
        let _ = warn(
            stdout,
            stderr,
            &format!("{reason}; so the runs name no build and the tests get no viewer link"),
        );
        None
    });
    if let Some(fallback) = build.as_ref().and_then(|build| build.server_fallback(&variable)) {
        let _ = warn(stdout, stderr, &fallback);
    }
    let viewer = match (viewer_dir, workspace) {
        (Some(dir), _) => ViewerSource::Built(dir),
        (None, Some(checkout)) => ViewerSource::Build {
            checkout,
            tools: None,
            timeout: VIEWER_BUILD_TIMEOUT,
        },
        (None, None) => ViewerSource::Missing,
    };
    let job = Publish {
        testlogs: &testlogs,
        out: &out,
        viewer: &viewer,
        build: build.as_ref(),
    };
    if let Err(refused) = publish(&job, stdout, stderr) {
        report(stderr, "teamcity-publish", &refused);
    }
    Exit::Ok.code()
}

/// A path of the command line, made absolute. `bazel run` starts the program in its runfiles tree, so a relative path is
/// taken from `working`, the directory of `BUILD_WORKING_DIRECTORY`, where the command ran.
fn given_path(path: &Path, working: Option<&Path>) -> io::Result<PathBuf> {
    match working {
        Some(working) if path.is_relative() => std::path::absolute(working.join(path)),
        _ => std::path::absolute(path),
    }
}

/// Every `<bazel-out>/<configuration>/testlogs` of the `bazel-out` this program was built in.
fn own_testlogs() -> Vec<PathBuf> {
    std::env::current_exe().map_or_else(|_| Vec::new(), |exe| testlogs_beside(&exe))
}

/// Every `<bazel-out>/<configuration>/testlogs` of the `bazel-out` that holds the real file of `exe`, in path order.
/// `bazel run` starts the binary through links into `bazel-out/<configuration>-opt/bin`, while the tests leave their
/// logs in the testlogs of another configuration.
fn testlogs_beside(exe: &Path) -> Vec<PathBuf> {
    let exe = fscopy::resolve_links(exe).unwrap_or_else(|_| exe.to_path_buf());
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

/// The bundles beside one `test.xml`.
struct Beside {
    /// The index of the testlogs directory in [Publish::testlogs].
    root: usize,
    xml: PathBuf,
    lane: String,
    bundles: Vec<Bundle>,
}

/// Publishes the bundles under each testlogs directory into out, with runs.json and the viewer beside them.
pub(crate) fn publish(job: &Publish<'_>, stdout: &mut dyn Write, stderr: &mut dyn Write) -> Result<(), Refusal> {
    let write_failed = |error: io::Error| Refusal::new("output_failed", Exit::Broken, error.to_string());
    let out = job.out;
    let origin = job.build.map(Build::origin);
    // One scanner for the whole run: it keeps the extractions of inner zips, which the packs read, until the end.
    let scanner = Scanner::new(Options::default());
    let mut roots = Vec::new();
    let mut tests = Vec::new();
    let mut xmls = 0;
    for (index, root) in job.testlogs.iter().enumerate() {
        let mut state = RootState {
            kind: RootKind::BazelTestlogs,
            path: root.clone(),
            origin: origin.clone(),
            exists: root.is_dir(),
            bundles: 0,
            error: None,
            problems: Vec::new(),
        };
        if !state.exists {
            let error = format!("{}: not a directory", root.display());
            warn(stdout, stderr, &format!("no testlogs: {error}")).map_err(write_failed)?;
            state.error = Some(error);
            roots.push(state);
            continue;
        }
        for xml in test_xmls(root) {
            xmls += 1;
            let dir = xml.parent().unwrap_or(root);
            let (bundles, unread) = bundles_beside(&scanner, dir);
            for problem in unread {
                warn(stdout, stderr, &problem).map_err(write_failed)?;
                state.problems.push(problem);
            }
            if !bundles.is_empty() {
                let lane = lane_of(dir.strip_prefix(root).unwrap_or(dir));
                tests.push(Beside {
                    root: index,
                    xml,
                    lane,
                    bundles,
                });
            }
        }
        roots.push(state);
    }
    if tests.is_empty() {
        let problems = roots
            .iter()
            .map(|root| root.problems.len() + usize::from(root.error.is_some()))
            .sum();
        return summarize(stderr, 0, xmls, out, problems).map_err(write_failed);
    }

    let copied = viewer_dir(job.viewer, stderr)
        .map_err(write_failed)?
        .and_then(|dir| copy_viewer(&dir, out));
    let page = match copied {
        Ok(entries) => Some(entries),
        Err(reason) => {
            warn(
                stdout,
                stderr,
                &format!("no viewer: {reason}; so air-traces/ holds the bundles and {RUNS_FILE} only"),
            )
            .map_err(write_failed)?;
            None
        }
    };

    let mut messages = Messages::default();
    let mut written = BTreeSet::new();
    let mut scenarios = Vec::new();
    for test in tests {
        let state = &mut roots[test.root];
        // The cases of this XML only: a bundle beside it never names the test of another target.
        let cases: Vec<(String, String)> = match fs::read(&test.xml) {
            Ok(content) => bt_junit::parse_report(&String::from_utf8_lossy(&content))
                .suites
                .iter()
                .flat_map(|suite| &suite.cases)
                .map(|case| (case.class_name.clone(), case.name.clone()))
                .collect(),
            Err(error) => {
                let problem = format!("{}: {error}", test.xml.display());
                warn(stdout, stderr, &problem).map_err(write_failed)?;
                state.problems.push(problem);
                Vec::new()
            }
        };
        let lane = &test.lane;
        let mut seen = HashSet::new();
        for bundle in &test.bundles {
            // One bundle in both `air-traces/` and `outputs.zip` of one test has two ids, so it is known by what its
            // manifest says: its run, its class, its scenario and its start. Another test's bundle is its own.
            let identity = (&bundle.run_id, &bundle.test_class, &bundle.scenario, &bundle.started_at);
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
            let mut file_name = format!("{scenario}.zip");
            let mut attempt = 1;
            while written.contains(&dir.join(&file_name)) {
                attempt += 1;
                file_name = format!("{scenario}.{attempt}.zip");
            }
            let zip = dir.join(&file_name);
            if attempt > 1 {
                say(
                    stderr,
                    &format!(
                        "{}: another bundle has the name {scenario}.zip, so this one is {}",
                        bundle.source.path,
                        zip.display()
                    ),
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
                let problem = format!("{}: {error}", bundle.source.path);
                warn(stdout, stderr, &problem).map_err(write_failed)?;
                state.problems.push(problem);
                continue;
            }
            let relative = format!("{lane}/{class_dir}/{file_name}");
            let link = job.build.filter(|_| page.is_some()).map(|build| build.viewer_link(&relative));
            let failed = matches!(bundle.status, Status::Failed | Status::Aborted);
            let zip_text = zip.to_string_lossy();
            let lines = messages.published(&PublishedTrace {
                zip: &zip_text,
                target: &target,
                test_class: &bundle.test_class,
                scenario: &bundle.scenario,
                video: bundle.has_video.then_some(VIDEO_FILE),
                still: bundle.thumbnail.as_deref().filter(|_| failed),
                link: link.as_deref(),
                cases: &cases,
            });
            for line in lines {
                writeln!(stdout, "{line}").map_err(write_failed)?;
            }
            scenarios.push(published_scenario(bundle, relative, origin.as_ref()));
            state.bundles += 1;
        }
    }

    let published = scenarios.len();
    let listing = Runs {
        generation: LISTING_GENERATION,
        controller_runs: &[],
        runs: group_runs(scenarios),
        roots: &roots,
    };
    let mut lines = Vec::new();
    let mut problems: usize = roots
        .iter()
        .map(|root| root.problems.len() + usize::from(root.error.is_some()))
        .sum();
    match write_listing(&listing, out) {
        Ok(file) => lines.extend(messages.artifact(&file.to_string_lossy(), TRACES_ARTIFACT_DIR)),
        Err(reason) => {
            problems += 1;
            warn(stdout, stderr, &reason).map_err(write_failed)?;
        }
    }
    for (name, is_dir) in page.iter().flatten() {
        let target = if *is_dir {
            format!("{TRACES_ARTIFACT_DIR}/{name}")
        } else {
            TRACES_ARTIFACT_DIR.to_owned()
        };
        lines.extend(messages.artifact(&out.join(name).to_string_lossy(), &target));
    }
    for line in lines {
        writeln!(stdout, "{line}").map_err(write_failed)?;
    }
    summarize(stderr, published, xmls, out, problems).map_err(write_failed)
}

/// One line of the step on stderr.
fn say(stderr: &mut dyn Write, line: &str) -> io::Result<()> {
    writeln!(stderr, "air-trace teamcity-publish: {line}")
}

/// One problem of the step: its line on stderr, and the same text as a warning of the build log on stdout.
fn warn(stdout: &mut dyn Write, stderr: &mut dyn Write, line: &str) -> io::Result<()> {
    say(stderr, line)?;
    writeln!(stdout, "{}", warning(&format!("air-trace teamcity-publish: {line}")))
}

/// The last line of the step on stderr: what it published, and how many problems it said above.
fn summarize(stderr: &mut dyn Write, published: usize, xmls: usize, out: &Path, problems: usize) -> io::Result<()> {
    let problems = if problems > 0 {
        format!("; {problems} problem(s) above")
    } else {
        String::new()
    };
    say(
        stderr,
        &format!(
            "{published} bundle zip(s) from {xmls} test.xml file(s) into {}{problems}",
            out.display()
        ),
    )
}

/// The listing's scenario of a bundle published as the zip `relative`: what its files say, the zip as its source and
/// its id, and the build as its origin.
fn published_scenario(bundle: &Bundle, relative: String, origin: Option<&Origin>) -> Scenario {
    let summary = Summary {
        id: bundle_id(SourceKind::Zip, &relative, ""),
        source: Source {
            kind: SourceKind::Zip,
            path: relative.clone(),
            entry: String::new(),
        },
        origin: origin.cloned(),
        ..bundle.summary.clone()
    };
    Scenario {
        zip: Some(relative),
        ..listed(&summary)
    }
}

/// Writes the listing as `<out>/runs.json`. Answers the file, or why it could not be written.
fn write_listing(listing: &Runs<'_>, out: &Path) -> Result<PathBuf, String> {
    let file = out.join(RUNS_FILE);
    let mut json = serde_json::to_vec_pretty(listing).map_err(|error| format!("{}: {error}", file.display()))?;
    json.push(b'\n');
    fs::create_dir_all(out)
        .and_then(|()| fs::write(&file, json))
        .map_err(|error| format!("cannot write {}: {error}", file.display()))?;
    Ok(file)
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

/// The bundles of the outputs beside a `test.xml`, under `test.outputs/air-traces/` and in `test.outputs/outputs.zip`,
/// and what of those outputs could not be read.
fn bundles_beside(scanner: &Scanner, dir: &Path) -> (Vec<Bundle>, Vec<String>) {
    let outputs = dir.join(TEST_OUTPUTS);
    let roots: Vec<Root> = [outputs.join(ROOT_DIR_NAME), outputs.join(OUTPUTS_ZIP)]
        .into_iter()
        .filter(|path| path.exists())
        .map(|path| Root::new(RootKind::Flag, path))
        .collect();
    if roots.is_empty() {
        return (Vec::new(), Vec::new());
    }
    let found = scanner.scan(&roots);
    let mut bundles: Vec<Bundle> = found.bundles.iter().map(|bundle| (**bundle).clone()).collect();
    bundles
        .sort_by(|left, right| (&left.run_id, &left.test_class, &left.scenario).cmp(&(&right.run_id, &right.test_class, &right.scenario)));
    let unread = found
        .roots
        .iter()
        .flat_map(|root| {
            let error = root.error.as_ref().map(|error| format!("{}: {error}", root.path.display()));
            error.into_iter().chain(root.problems.iter().cloned())
        })
        .collect();
    (bundles, unread)
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
