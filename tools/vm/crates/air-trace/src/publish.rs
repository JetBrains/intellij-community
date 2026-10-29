//! `air-trace teamcity-publish`: the trace bundles of a Bazel test run as one TeamCity archive artifact,
//! `air-traces.zip`. It holds a listing and one zip for each bundle, and each test links to its trace on the hosted
//! site.
//!
//! It runs as a build step after `bazel test`. A Bazel test reports through its JUnit XML, and a service message of a
//! test does not reach TeamCity. The step reads every `test.xml` under the testlogs, and the bundles of the
//! `test.outputs` beside it. These are the directory `air-traces/` and the zip `outputs.zip`, an inner zip included.
//! Each bundle becomes the zip `<lane>/<runId>/<TestClass>/<scenario>.zip` of the archive, with the media stored. The
//! step prints the `testMetadata` of its test: the trace and the trace page of the site. For a failed scenario they
//! also name the video and the failure picture. The test name is the one TeamCity took from the XML, `<classname>.<name>`.
//!
//! TeamCity redirects a loose artifact to a signed address where a sibling file is refused. An entry of an archive
//! artifact is served by TeamCity itself, with byte ranges and beside its siblings. So the archive holds the listing
//! `runs.json` and the zips, and the step publishes it as one file. Only the video and the failure picture of a failed
//! scenario are loose files in `air-traces-failed/`, so the Tests tab shows them.
//!
//! The lane is the directory of the `test.xml`: the Bazel target's name, such as `ui_lane_test`. A shard or run
//! directory below it keeps its target's name in front, such as `ui_lane_test/shard_1_of_2`.
//!
//! The listing `runs.json` lists the published bundles as `GET /__air/runs` of `air-trace serve` lists bundles, with
//! the server's own types. Each scenario carries its `zip`, relative to the listing, and is a zip at that path in its
//! `source` and its id. On TeamCity, each run names the build as its `origin`, from the agent's property files
//! ([teamcity]). Each test links to the CI trace page of the site, `<site>/ci/<buildId>/trace?zip=<zip>`
//! ([ci_trace_url]), and the build log names the CI page of the build. The portal of the site reads
//! `air-traces.zip!/runs.json` and the zips of the build from TeamCity. The links need the build.
//!
//! A testlogs tree without bundles prints nothing and writes nothing. Each problem is a line on the standard error and
//! a warning `message` on the standard output. A problem is no testlogs, a missing testlogs directory, or an output
//! that does not read. A bundle that cannot be packed and build properties that do not read are problems too. The rest
//! still publishes, and the step answers 0, so it never turns a build red. Only a usage error of the command line
//! answers 2.
//!
//! On TeamCity the step publishes only the tests of this build ([teamcity::Build::start]). It also empties its output
//! first. So a test or a zip of an earlier build on the agent does not publish again.
//!
//! On TeamCity the archive also holds `affected.json` after `runs.json` ([affected]). For each commit of the build it
//! names the changed files, the suites that they reach and the files that reach no suite. A build without changed files
//! has no such entry. A build that does not name its changed files, a file that does not read and a refusal of the
//! join are problems. The rest still publishes.

use std::collections::HashSet;
use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use avl_report::teamcity::{Messages, PublishedTrace, TRACES_ARTIFACT_DIR, is_build_step, log_message, warning};
use avl_trace::bundle::{PARTIAL_SUFFIX, ROOT_DIR_NAME, VIDEO_FILE, bundle_path};
use avl_trace_tools::artifacts::{Stems, failed_target, failure_picture, testlogs_lane};
use avl_trace_tools::discover::{
    Bundle, Location, Options, Origin, Root, RootKind, RootState, Scanner, Source, SourceKind, Status, Summary, bundle_id,
};
use avl_trace_tools::pack::{PackOptions, Skipped, archive, pack, pack_zip_bundle};
use avl_trace_tools::viewer::{CI_SITE, ci_build_url, ci_trace_url};

use crate::serve::{Runs, Scenario, group_runs, listed};
use crate::{Exit, Refusal, report, usage};
use affected::{AFFECTED_FILE, AffectedDocument};
use teamcity::Build;

mod affected;
mod teamcity;

/// `air-trace teamcity-publish`'s command line.
#[derive(clap::Args, Clone, Debug, PartialEq, Eq)]
pub(crate) struct PublishArgs {
    /// A Bazel testlogs directory to read; repeatable. Without one, every `bazel-out/*/testlogs` beside this program's
    /// own output, which is where `bazel run` finds it.
    #[arg(long = "testlogs", value_name = "DIR")]
    pub testlogs: Vec<PathBuf>,
    /// Where air-traces.zip, its parts and the loose files are written (default <workspace>/out/air-traces-publish).
    #[arg(long, value_name = "DIR")]
    pub out: Option<PathBuf>,
    /// The hosted site whose CI pages the links of the tests open, such as a staging portal.
    #[arg(long, value_name = "URL", env = SITE_VARIABLE, default_value = CI_SITE)]
    pub site: String,
    /// Publish only the tests whose test.xml was written at this time or later, such as 2026-10-21T08:00:00Z. The
    /// default is the variable AIR_TRACE_SINCE, which the first step of a TeamCity build sets.
    #[arg(long, value_name = "TIME", value_parser = parse_since)]
    pub since: Option<jiff::Timestamp>,
    /// Once air-traces.zip is published, remove the bundle directories that it holds from the testlogs. On TeamCity
    /// the step does so always, so the artifact bazel.logs.zip does not hold them a second time.
    #[arg(long)]
    pub remove_packed: bool,
    /// The checkout that the changed files of the TeamCity build are joined on, for affected.json. The default is
    /// teamcity.build.checkoutDir of the build, then BUILD_WORKSPACE_DIRECTORY.
    #[arg(long, value_name = "DIR")]
    pub repo: Option<PathBuf>,
}

/// Reads `--since`: a time of RFC 3339, such as `2026-10-21T08:00:00Z`.
fn parse_since(value: &str) -> Result<jiff::Timestamp, String> {
    value
        .parse()
        .map_err(|error| format!("{value} is not a time such as 2026-10-21T08:00:00Z: {error}"))
}

/// The variable that names the site when `--site` does not.
const SITE_VARIABLE: &str = "AIR_TRACE_SITE";

/// The variable that names the start of the build when `--since` does not. The first step of a TeamCity build sets it
/// to the time of the agent, such as with `date -u +%Y-%m-%dT%H:%M:%SZ`. The test.xml files have the times of the
/// same clock.
const SINCE_VARIABLE: &str = "AIR_TRACE_SINCE";

/// The time from which a `test.xml` counts, and what names it: `--since`, else [SINCE_VARIABLE]. A value of the
/// variable that does not read is a warning, and so is a step on TeamCity with neither. Then every test publishes.
fn since_of(
    given: Option<jiff::Timestamp>,
    variable: &dyn Fn(&str) -> Option<String>,
    on_team_city: bool,
) -> Result<Option<(SystemTime, &'static str)>, String> {
    if let Some(since) = given {
        return Ok(Some((SystemTime::from(since), "--since")));
    }
    let Some(value) = variable(SINCE_VARIABLE).filter(|value| !value.trim().is_empty()) else {
        return if on_team_city {
            Err(format!(
                "neither --since nor {SINCE_VARIABLE} names the start of the build, so the tests of earlier builds \
                 publish too"
            ))
        } else {
            Ok(None)
        };
    };
    match parse_since(value.trim()) {
        Ok(since) => Ok(Some((SystemTime::from(since), SINCE_VARIABLE))),
        Err(reason) => Err(format!("{SINCE_VARIABLE}: {reason}; so the tests of earlier builds publish too")),
    }
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
/// The file in the output that says that this step wrote it, so a later run may empty it.
const OUT_MARKER: &str = ".air-traces-publish";
/// The one archive artifact of the traces, at the top of the artifacts and of the output.
pub(crate) const ARCHIVE: &str = "air-traces.zip";

/// What one publish reads and where it writes.
pub(crate) struct Publish<'a> {
    pub testlogs: &'a [PathBuf],
    pub out: &'a Path,
    /// The hosted site of the links, such as [CI_SITE].
    pub site: &'a str,
    /// The TeamCity build that the step runs in, or `None` off TeamCity.
    pub build: Option<&'a Build>,
    /// The time from which a `test.xml` counts, or `None` for all. An older one is a test of an earlier build.
    pub since: Option<SystemTime>,
    /// Whether the output is the default one, which the step owns even without its marker.
    pub out_owned: bool,
    /// Whether the step removes the bundle directories that the published archive holds from the testlogs.
    pub remove_packed: bool,
    /// What the changed files of the build reach, or `None` for no `affected.json`.
    pub affected: Option<&'a AffectedDocument>,
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
    let repo = match args.repo.as_deref().map(given).transpose() {
        Ok(repo) => repo,
        Err(error) => return report(stderr, "teamcity-publish", &usage(format!("--repo: {error}"))),
    };
    let variable = |name: &str| std::env::var(name).ok();
    let build = Build::of_step(&variable).unwrap_or_else(|reason| {
        let _ = warn(
            stdout,
            stderr,
            &format!("{reason}; so the runs name no build and the tests get no trace link"),
        );
        None
    });
    if let Some(fallback) = build.as_ref().and_then(|build| build.server_fallback(&variable)) {
        let _ = warn(stdout, stderr, &fallback);
    }
    let affected = build.as_ref().and_then(|build| {
        affected::of_build(build, repo.as_deref(), workspace.as_deref()).unwrap_or_else(|reason| {
            let _ = warn(stdout, stderr, &reason);
            None
        })
    });
    let on_team_city = is_build_step(variable);
    let since = match since_of(args.since, &variable, on_team_city) {
        Ok(Some((since, source))) => {
            let _ = say(stderr, &format!("a test.xml from {} on counts, as {source} says", instant(since)));
            Some(since)
        }
        Ok(None) => None,
        Err(warning) => {
            let _ = warn(stdout, stderr, &warning);
            None
        }
    };
    let job = Publish {
        testlogs: &testlogs,
        out: &out,
        site: &args.site,
        build: build.as_ref(),
        since,
        out_owned: args.out.is_none(),
        remove_packed: args.remove_packed || on_team_city,
        affected: affected.as_ref(),
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

/// Publishes the bundles under each testlogs directory into out, with runs.json beside them.
pub(crate) fn publish(job: &Publish<'_>, stdout: &mut dyn Write, stderr: &mut dyn Write) -> Result<(), Refusal> {
    let write_failed = |error: io::Error| Refusal::new("output_failed", Exit::Broken, error.to_string());
    let out = job.out;
    let origin = job.build.map(Build::origin);
    // One scanner for the whole run: it keeps the extractions of inner zips, which the packs read, until the end.
    let scanner = Scanner::new(Options::default());
    let mut roots = Vec::new();
    let mut tests = Vec::new();
    let mut xmls = 0;
    let mut skipped = 0;
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
            if let Some(since) = job.since
                && let Some(written) = fs::metadata(&xml)
                    .and_then(|info| info.modified())
                    .ok()
                    .filter(|written| *written < since)
            {
                say(
                    stderr,
                    &format!(
                        "skipped {}: its test.xml is from {}, before {}, so an earlier build ran it",
                        dir.display(),
                        instant(written),
                        instant(since)
                    ),
                )
                .map_err(write_failed)?;
                skipped += 1;
                continue;
            }
            let (bundles, unread) = bundles_beside(&scanner, dir);
            for problem in unread {
                warn(stdout, stderr, &problem).map_err(write_failed)?;
                state.problems.push(problem);
            }
            if !bundles.is_empty() {
                let lane = testlogs_lane(dir.strip_prefix(root).unwrap_or(dir));
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
    if let Some(since) = job.since.filter(|_| skipped > 0) {
        let warning = if tests.is_empty() {
            format!(
                "{skipped} test(s) skipped, because their test.xml is older than {}, and no other test has a trace, so \
                 nothing is published; check {SINCE_VARIABLE} or --since",
                instant(since)
            )
        } else {
            format!("{skipped} test(s) skipped, because their test.xml is older than {}", instant(since))
        };
        warn(stdout, stderr, &warning).map_err(write_failed)?;
    }
    if tests.is_empty() {
        let problems = roots
            .iter()
            .map(|root| root.problems.len() + usize::from(root.error.is_some()))
            .sum();
        return summarize(stderr, 0, xmls, out, problems).map_err(write_failed);
    }
    if let Err(reason) = empty_out(out, job.out_owned) {
        warn(stdout, stderr, &format!("{reason}; nothing is published")).map_err(write_failed)?;
        return summarize(stderr, 0, xmls, out, 1).map_err(write_failed);
    }

    // The archive is assembled in `<out>/air-traces/`, which holds its entries as they lie in it.
    let staging = out.join(TRACES_ARTIFACT_DIR);

    let mut messages = Messages::default();
    let mut stems = Stems::default();
    let mut scenarios = Vec::new();
    let mut zips = Vec::new();
    let mut packed_dirs = Vec::new();
    let mut problems = 0;
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
            // One bundle in both `air-traces/` and `outputs.zip` of one test has two ids. So it is known by what its
            // manifest says: its run, its class, its scenario and its start. Another test's bundle is its own.
            let identity = (&bundle.run_id, &bundle.test_class, &bundle.scenario, &bundle.started_at);
            if !seen.insert(identity) {
                continue;
            }
            let placed = bundle_path(&bundle.run_id, &bundle.test_class, &bundle.scenario);
            let (class_dir, scenario) = placed.rsplit_once('/').unwrap_or(("", &placed));
            let dir = lane
                .split('/')
                .chain(class_dir.split('/'))
                .fold(staging.clone(), |path, segment| path.join(segment));
            // Two names can sanitize to one file name; the later bundle takes the next free `<scenario>.<n>.zip`.
            let stem = stems.take(lane, class_dir, scenario);
            let zip = dir.join(format!("{stem}.zip"));
            if stem != scenario {
                say(
                    stderr,
                    &format!(
                        "{}: another bundle has the name {scenario}.zip, so this one is {stem}.zip",
                        bundle.source.path
                    ),
                )
                .map_err(write_failed)?;
            }
            let packed = match &bundle.location {
                Location::Dir(dir) => pack(dir, &zip, &PackOptions::default()),
                Location::Zip { path, prefix } => pack_zip_bundle(path, prefix, &zip),
                Location::File => continue,
            };
            let left_out = match packed {
                Ok(packed) => packed.skipped,
                Err(error) => {
                    let problem = format!("{}: {error}", bundle.source.path);
                    warn(stdout, stderr, &problem).map_err(write_failed)?;
                    state.problems.push(problem);
                    continue;
                }
            };
            let relative = format!("{lane}/{class_dir}/{stem}.zip");
            let mut lines = Vec::new();
            let failure = if matches!(bundle.status, Status::Failed | Status::Aborted) {
                let target = failed_target(lane, class_dir, &stem);
                let loose = Loose {
                    dir: out.join(&target),
                    target,
                };
                let video = bundle.has_video.then_some((VIDEO_FILE, VIDEO_FILE.to_owned()));
                let still = bundle.thumbnail.as_deref().map(|still| (still, failure_picture(still)));
                let mut published = |entry: Option<(&str, String)>| -> io::Result<Option<String>> {
                    let Some((entry, name)) = entry else {
                        return Ok(None);
                    };
                    match loose.extract(&zip, entry, &name) {
                        Ok(file) => {
                            lines.extend(messages.artifact(&file.to_string_lossy(), &loose.target));
                            Ok(Some(format!("{}/{name}", loose.target)))
                        }
                        Err(reason) => {
                            problems += 1;
                            warn(stdout, stderr, &reason).map(|()| None)
                        }
                    }
                };
                let video = published(video).map_err(write_failed)?;
                let still = published(still).map_err(write_failed)?;
                (video, still)
            } else {
                (None, None)
            };
            let trace = format!("{ARCHIVE}!/{relative}");
            let link = job.build.map(|build| ci_trace_url(job.site, &build.id, &relative));
            lines.extend(messages.published(&PublishedTrace {
                trace: &trace,
                test_class: &bundle.test_class,
                scenario: &bundle.scenario,
                video: failure.0.as_deref(),
                still: failure.1.as_deref(),
                link: link.as_deref(),
                cases: &cases,
            }));
            for line in lines {
                writeln!(stdout, "{line}").map_err(write_failed)?;
            }
            zips.push((relative.clone(), zip));
            if let Location::Dir(bundle_dir) = &bundle.location {
                packed_dirs.push(PackedDir {
                    dir: bundle_dir.clone(),
                    traces: test.xml.parent().unwrap_or(Path::new("")).join(TEST_OUTPUTS).join(ROOT_DIR_NAME),
                    root: job.testlogs[test.root].clone(),
                    // The zip has no copy of what the packer left out. Only a temporary file of a recorder may go.
                    left_out: left_out.into_iter().filter(|item| !item.path.ends_with(PARTIAL_SUFFIX)).collect(),
                });
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
    problems += roots
        .iter()
        .map(|root| root.problems.len() + usize::from(root.error.is_some()))
        .sum::<usize>();
    // The listing first, then what the changed files reach, then each bundle zip in the order it was published.
    let mut entries: Vec<(String, PathBuf)> = Vec::new();
    let written = [
        Some((RUNS_FILE, write_json(&listing, &staging, RUNS_FILE))),
        job.affected
            .map(|affected| (AFFECTED_FILE, write_json(affected, &staging, AFFECTED_FILE))),
    ];
    for (name, file) in written.into_iter().flatten() {
        match file {
            Ok(file) => entries.push((name.to_owned(), file)),
            Err(reason) => {
                problems += 1;
                warn(stdout, stderr, &reason).map_err(write_failed)?;
            }
        }
    }
    entries.extend(zips);
    let archived = out.join(ARCHIVE);
    match archive(&archived, &entries) {
        Ok(bytes) => {
            for line in messages.artifact_file(&archived.to_string_lossy()) {
                writeln!(stdout, "{line}").map_err(write_failed)?;
            }
            if let Some(build) = job.build {
                let page = ci_build_url(job.site, &build.id);
                writeln!(stdout, "{}", log_message(&format!("The traces of this build: {page}"))).map_err(write_failed)?;
            }
            say(stderr, &format!("{ARCHIVE} holds {} entries in {bytes} bytes", entries.len())).map_err(write_failed)?;
            if job.remove_packed {
                let removal = remove_packed(&packed_dirs);
                for problem in &removal.problems {
                    warn(stdout, stderr, problem).map_err(write_failed)?;
                }
                say(
                    stderr,
                    &format!(
                        "removed {} packed bundle(s) with {} bytes from the testlogs",
                        removal.removed, removal.bytes
                    ),
                )
                .map_err(write_failed)?;
            }
        }
        Err(error) => {
            problems += 1;
            warn(stdout, stderr, &format!("cannot write {}: {error}", archived.display())).map_err(write_failed)?;
        }
    }
    summarize(stderr, published, xmls, out, problems).map_err(write_failed)
}

/// A bundle directory of the testlogs that the archive holds.
struct PackedDir {
    /// The real directory of the bundle.
    dir: PathBuf,
    /// The `test.outputs/air-traces` directory of its test.
    traces: PathBuf,
    /// The testlogs directory of its test.
    root: PathBuf,
    /// What the zip of the bundle does not hold, other than a temporary file of a recorder.
    left_out: Vec<Skipped>,
}

/// What [remove_packed] did.
#[derive(Debug, Default, PartialEq, Eq)]
struct Removal {
    removed: usize,
    /// The bytes of the removed files. A file with another hard link frees no disk space, but it leaves the testlogs.
    bytes: u64,
    problems: Vec<String>,
}

/// Removes the bundle directories that the published archive holds from the testlogs. TeamCity packs the testlogs
/// into bazel.logs.zip at the end of the build, which would hold the bundles a second time. A directory is removed
/// only when it is a directory and no link. Its real path must be inside the `test.outputs/air-traces` of its test,
/// inside its testlogs. A directory stays when the zip does not hold all of it, such as a link to a directory. The
/// other outputs of the test stay, such as `outputs.zip` and the Allure results. A directory of a run or of a class
/// that the removal empties goes too. A directory that is not removed is a problem.
fn remove_packed(packed: &[PackedDir]) -> Removal {
    let mut removal = Removal::default();
    for item in packed {
        match remove_bundle_dir(item) {
            Ok(bytes) => {
                removal.removed += 1;
                removal.bytes += bytes;
            }
            Err(reason) => removal
                .problems
                .push(format!("{} stays in the testlogs: {reason}", item.dir.display())),
        }
    }
    removal
}

/// Removes one packed bundle directory, after the checks of [remove_packed]. Answers the bytes of its files.
fn remove_bundle_dir(item: &PackedDir) -> Result<u64, String> {
    if let Some(first) = item.left_out.first() {
        let more = match item.left_out.len() - 1 {
            0 => String::new(),
            more => format!(" and {more} more"),
        };
        return Err(format!("its zip does not hold {} ({}){more}", first.path, first.reason));
    }
    let kind = fs::symlink_metadata(&item.dir).map_err(|error| error.to_string())?.file_type();
    if !kind.is_dir() {
        return Err("it is not a directory".to_owned());
    }
    let real = |path: &Path| fscopy::real_path(path).map_err(|error| format!("{}: {error}", path.display()));
    let (dir, traces, root) = (real(&item.dir)?, real(&item.traces)?, real(&item.root)?);
    if dir == traces || !dir.starts_with(&traces) || !traces.starts_with(&root) {
        return Err(format!("it is not inside {} of the testlogs {}", traces.display(), root.display()));
    }
    // A Bazel output is read-only. A directory must be writable for its entries to go, from the traces directory down.
    let writable = |path: &Path| make_writable(path).map_err(|error| format!("{}: {error}", path.display()));
    for ancestor in dir.ancestors().skip(1).take_while(|ancestor| ancestor.starts_with(&traces)) {
        writable(ancestor)?;
    }
    let mut bytes = 0;
    for entry in walkdir::WalkDir::new(&dir) {
        let entry = entry.map_err(|error| error.to_string())?;
        let kind = entry.file_type();
        // A link is removed as a link; its target is neither read nor changed.
        if kind.is_file() {
            bytes += entry.metadata().map_err(|error| error.to_string())?.len();
        }
        if kind.is_dir() || kind.is_file() {
            writable(entry.path())?;
        }
    }
    fs::remove_dir_all(&dir).map_err(|error| format!("cannot remove it: {error}"))?;
    let mut parent = dir.parent();
    while let Some(empty) = parent.filter(|parent| *parent != traces && parent.starts_with(&traces)) {
        if fs::remove_dir(empty).is_err() {
            break;
        }
        parent = empty.parent();
    }
    Ok(bytes)
}

/// Gives the owner of `path` the right to write it, when the owner has none. Only a file or a directory, never a link.
fn make_writable(path: &Path) -> io::Result<()> {
    let permissions = fs::symlink_metadata(path)?.permissions();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = permissions.mode();
        if mode & 0o200 == 0 {
            fs::set_permissions(path, fs::Permissions::from_mode(mode | 0o200))?;
        }
    }
    #[cfg(not(unix))]
    if permissions.readonly() {
        let mut permissions = permissions;
        #[expect(
            clippy::permissions_set_readonly_false,
            reason = "Windows has only the read-only attribute, and the entry is about to be removed"
        )]
        permissions.set_readonly(false);
        fs::set_permissions(path, permissions)?;
    }
    Ok(())
}

/// Where the loose files of one failed scenario go: a directory of the output, and its artifact directory.
struct Loose {
    dir: PathBuf,
    target: String,
}

impl Loose {
    /// Copies the entry `entry` of the bundle zip `zip` into the directory as `name`. Answers the file, or why it
    /// could not be copied.
    fn extract(&self, zip: &Path, entry: &str, name: &str) -> Result<PathBuf, String> {
        let file = self.dir.join(name);
        let failed = |error: &dyn std::fmt::Display| format!("{}!/{entry}: {error}", zip.display());
        let mut archive = fs::File::open(zip)
            .map_err(|error| failed(&error))
            .and_then(|opened| zip::ZipArchive::new(opened).map_err(|error| failed(&error)))?;
        let mut source = archive.by_name(entry).map_err(|error| failed(&error))?;
        fs::create_dir_all(&self.dir).map_err(|error| failed(&error))?;
        let mut target = fs::File::create(&file).map_err(|error| failed(&error))?;
        io::copy(&mut source, &mut target).map_err(|error| failed(&error))?;
        Ok(file)
    }
}

/// Empties the output and marks it as this step's, so it holds the files of this run only. The step empties a directory
/// that it owns or that holds its marker. It refuses another directory that is not empty, because its files are not
/// the step's.
fn empty_out(out: &Path, owned: bool) -> Result<(), String> {
    let failed = |path: &Path, error: io::Error| format!("cannot empty {}: {error}", path.display());
    match fs::read_dir(out) {
        Ok(entries) => {
            let entries: Vec<PathBuf> = entries
                .map(|entry| entry.map(|entry| entry.path()))
                .collect::<io::Result<_>>()
                .map_err(|error| failed(out, error))?;
            if !entries.is_empty() && !owned && !out.join(OUT_MARKER).is_file() {
                return Err(format!(
                    "--out {} holds files that this step did not write; pass an empty directory",
                    out.display()
                ));
            }
            for entry in entries {
                let removed = if entry.is_dir() && !entry.is_symlink() {
                    fs::remove_dir_all(&entry)
                } else {
                    fs::remove_file(&entry)
                };
                removed.map_err(|error| failed(&entry, error))?;
            }
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(failed(out, error)),
    }
    fs::create_dir_all(out)
        .and_then(|()| fs::write(out.join(OUT_MARKER), b""))
        .map_err(|error| failed(out, error))
}

/// A time as RFC 3339 writes it, in UTC.
fn instant(time: SystemTime) -> String {
    jiff::Timestamp::try_from(time).map_or_else(|_| format!("{time:?}"), |time| time.to_string())
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

/// The listing's scenario of a bundle published as the zip `relative`. It says what the files of the bundle say, with
/// the zip as its source and its id, and the build as its origin.
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

/// Writes a document of the archive, such as the listing, as `<out>/<name>`. Answers the file, or why it could not be
/// written.
fn write_json(document: &impl serde::Serialize, out: &Path, name: &str) -> Result<PathBuf, String> {
    let file = out.join(name);
    let mut json = serde_json::to_vec_pretty(document).map_err(|error| format!("{}: {error}", file.display()))?;
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

#[cfg(test)]
mod tests;
