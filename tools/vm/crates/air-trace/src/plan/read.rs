//! Reading an input: deciding what it is, and handing it to the reader for that kind.
//!
//! The decision is made from the bytes wherever the bytes can make it, and from the name only where they cannot. A
//! dropped file has a name and no path, a piped one has neither, and a `test.xml` renamed `failure.xml` is still a
//! JUnit document; so a zip is its magic, a report is its `reportSchemaVersion`, and a suite document is its three
//! required fields. The name decides between a flow text and a source file, which share nothing else a reader
//! could test.

use std::fs::{self, File};
use std::io::{Cursor, Read};
use std::path::{Path, PathBuf};

use avl_trace::bundle::{LOGS_FILE, MANIFEST_FILE, SPANS_FILE};
use avl_trace_tools::discover::{self, Bundle, Root, RootKind, RootState, Snapshot, Source as BundleSource, SourceKind, Summary};
use avl_wire::report::{RunReport, decode_run_report};
use bt_junit::Outcome;
use serde_json::Value;

use bt_core::catalog::parse_suite_document;

use super::catalog::checked_lane;
use super::names::TestCase;
use super::{Kind, PlannedBundle, Planner, Unmapped, base_name, reason, unreadable};

/// One input as something to classify: a file on disk, a dropped file, or standard input.
pub(crate) struct Source {
    /// What the caller typed or dropped, for the reading and the unmapped entry.
    pub given: String,
    /// The base name, "" for standard input.
    pub name: String,
    /// The absolute path on disk, `None` for a dropped file.
    pub path: Option<PathBuf>,
    pub content: Vec<u8>,
}

/// Bounds one file read into memory to be classified. A zip is never read this way: it is opened in place, so an
/// iteration's archive of videos costs its central directory and nothing more.
const MAX_DOCUMENT_BYTES: u64 = 64 << 20;

/// Bounds the walk for bundles under a directory. A trace root holds a few hundred entries per run; a directory
/// past this is not a trace root, and a walk of the whole checkout would read as a hang.
const MAX_WALKED_ENTRIES: usize = 200_000;

const ZIP_MAGIC: &[u8] = b"PK\x03\x04";

impl Planner<'_> {
    /// Reads typed text: a path to a file, a name, or several of either separated by white space.
    pub(crate) fn read_text(&mut self, text: &str) -> anyhow::Result<()> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            return Ok(());
        }
        if let Some(absolute) = self.locate(trimmed) {
            return self.read_disk(&absolute, trimmed);
        }
        let tokens: Vec<&str> = trimmed
            .split([' ', '\t', '\n', '\r', ','])
            .filter(|token| !token.is_empty())
            .collect();
        if tokens.len() > 1 {
            for token in tokens {
                if !is_git_status_marker(token) {
                    self.read_text(token)?;
                }
            }
            return Ok(());
        }
        self.read_name(trimmed)
    }

    /// The file a typed path names: relative to the working directory first, because that is where the caller's
    /// shell completed it, and then relative to the checkout, because that is how documents name files.
    fn locate(&self, typed: &str) -> Option<PathBuf> {
        let typed_path = Path::new(typed);
        let mut candidates = vec![typed_path.to_path_buf()];
        if !typed_path.is_absolute() {
            candidates.push(self.repo_root.join(typed_path));
        }
        candidates
            .into_iter()
            .filter(|candidate| fs::metadata(candidate).is_ok())
            .find_map(|candidate| std::path::absolute(candidate).ok())
    }

    /// The checkout-relative spelling of a file on disk, or `None` for a file outside it.
    fn repo_relative(&self, absolute: &Path) -> Option<String> {
        let root = fscopy::resolve_links(&self.repo_root).unwrap_or_else(|_| self.repo_root.clone());
        let target = fscopy::resolve_links(absolute).unwrap_or_else(|_| absolute.to_path_buf());
        let relative = target.strip_prefix(&root).ok()?;
        let slashed = relative
            .components()
            .map(|component| component.as_os_str().to_string_lossy())
            .collect::<Vec<_>>()
            .join("/");
        Some(if slashed.is_empty() { ".".to_owned() } else { slashed })
    }

    /// Reads one file or directory on disk.
    pub(crate) fn read_disk(&mut self, absolute: &Path, given: &str) -> anyhow::Result<()> {
        let metadata = match fs::metadata(absolute) {
            Ok(metadata) => metadata,
            Err(error) => {
                self.unmapped(Unmapped::new(given, reason::UNREADABLE, error.to_string()));
                return Ok(());
            }
        };
        if metadata.is_dir() {
            self.read_directory(absolute, given);
            return Ok(());
        }
        if is_zip_file(absolute) {
            self.read_zip_file(absolute, given);
            return Ok(());
        }
        let mut content = Vec::new();
        let read = File::open(absolute).and_then(|file| file.take(MAX_DOCUMENT_BYTES + 1).read_to_end(&mut content));
        let problem = match read {
            Err(error) => Some(error.to_string()),
            Ok(_) if content.len() as u64 > MAX_DOCUMENT_BYTES => Some(format!("over {MAX_DOCUMENT_BYTES} bytes")),
            Ok(_) => None,
        };
        if let Some(problem) = problem {
            self.unmapped(Unmapped::new(given, reason::UNREADABLE, problem));
            return Ok(());
        }
        self.read_source(&Source {
            given: given.to_owned(),
            name: base_name(&absolute.to_string_lossy()),
            path: Some(absolute.to_path_buf()),
            content,
        })
    }

    /// Classifies bytes and hands them to their reader.
    pub(crate) fn read_source(&mut self, input: &Source) -> anyhow::Result<()> {
        let content = &input.content;
        let trimmed = content.trim_ascii();
        if content.starts_with(ZIP_MAGIC) {
            match zip::ZipArchive::new(Cursor::new(content.as_slice())) {
                Ok(mut archive) => {
                    let bundles = discover::read_zip(&mut archive, &input.given);
                    if !self.add_bundles(&input.given, bundles.iter()) {
                        self.unmapped(Unmapped::new(
                            &input.given,
                            reason::NO_BUNDLE,
                            format!("the archive holds no {SPANS_FILE}"),
                        ));
                    }
                }
                Err(error) => {
                    self.unmapped(Unmapped::new(&input.given, reason::UNREADABLE, error.to_string()));
                }
            }
            return Ok(());
        }
        if [MANIFEST_FILE, SPANS_FILE, LOGS_FILE].contains(&input.name.as_str()) {
            self.read_bundle_file(input);
            return Ok(());
        }
        if trimmed.starts_with(b"<") && contains(content, b"<testsuite") {
            return self.read_junit(input);
        }
        if trimmed.starts_with(b"{") && self.read_json(input)? {
            return Ok(());
        }
        if let Some(flow) = flow_of_text(&input.name, content) {
            return self.read_flow(&flow, &input.given);
        }
        if let Some(path) = &input.path {
            match self.repo_relative(path) {
                Some(relative) => {
                    self.read(&input.given, Kind::Path, relative.clone());
                    self.paths.push(relative);
                }
                None => self.unmapped(Unmapped::new(
                    &input.given,
                    reason::UNRECOGNIZED,
                    "outside the checkout, and not a bundle, flow text, profile, test.xml or report",
                )),
            }
            return Ok(());
        }
        if input.name.is_empty() || input.name == "." {
            self.unmapped(Unmapped::new(
                &input.given,
                reason::UNRECOGNIZED,
                "not a bundle, flow text, profile, test.xml or report, and there is no file name to look up",
            ));
            return Ok(());
        }
        self.read_file_name(&input.name, &input.given, Some(content))
    }

    // --- bundles -------------------------------------------------------------------------------------------

    /// Looks for bundles at one path on disk, a directory tree or a zip, with the reader the server lists bundles
    /// with. The scanner is made on the first call.
    fn scan(&mut self, path: &Path) -> (Snapshot, RootState) {
        let scanner = self.scanner.get_or_insert_with(|| {
            discover::Scanner::new(discover::Options {
                max_entries: Some(MAX_WALKED_ENTRIES),
                ..discover::Options::default()
            })
        });
        let found = scanner.scan(&[Root::new(RootKind::Flag, path)]);
        let state = found.roots[0].clone();
        (found, state)
    }

    /// Plans the bundles an input holds, and answers whether it held one.
    fn add_bundles<'b>(&mut self, given: &str, bundles: impl ExactSizeIterator<Item = &'b Bundle>) -> bool {
        let count = bundles.len();
        for bundle in bundles {
            self.add_bundle(PlannedBundle {
                summary: bundle.summary.clone(),
                missing: false,
            });
        }
        if count > 0 {
            self.read(given, Kind::Bundle, format!("{count} bundle(s)"));
        }
        count > 0
    }

    /// Opens an archive in place: an iteration's traces, Bazel's `outputs.zip`, or one bundle zipped.
    fn read_zip_file(&mut self, absolute: &Path, given: &str) {
        let (found, state) = self.scan(absolute);
        if self.add_bundles(given, found.bundles.iter().map(|bundle| &**bundle)) {
            return;
        }
        let problem = unreadable(&state);
        if !problem.is_empty() {
            self.unmapped(Unmapped::new(given, reason::UNREADABLE, problem));
            return;
        }
        self.unmapped(Unmapped::new(
            given,
            reason::NO_BUNDLE,
            format!("the archive holds no {SPANS_FILE}"),
        ));
    }

    /// Answers every bundle under a directory, which may be one bundle, a run, or a whole trace root. A directory
    /// with no bundle is a repository path like any other, and the join says what it reaches.
    fn read_directory(&mut self, absolute: &Path, given: &str) {
        let (found, state) = self.scan(absolute);
        if self.add_bundles(given, found.bundles.iter().map(|bundle| &**bundle)) {
            // What the walk could not read, and whether it stopped at its bound, qualify the bundles it did find.
            for problem in state.problems {
                self.note(problem);
            }
            return;
        }
        if let Some(relative) = self.repo_relative(absolute) {
            self.read(given, Kind::Path, relative.clone());
            self.paths.push(relative);
            return;
        }
        self.unmapped(Unmapped::new(
            given,
            reason::NO_BUNDLE,
            format!("the directory holds no {SPANS_FILE} and is outside the checkout"),
        ));
    }

    /// Reads one file of a bundle: its manifest, its spans or its log records.
    ///
    /// On disk the file's directory is the bundle, and is read as one. Dropped, the file is all there is, so the
    /// bundle is named from what the file itself says.
    fn read_bundle_file(&mut self, input: &Source) {
        if let Some(path) = &input.path {
            let directory = path.parent().unwrap_or(path);
            self.read_directory(directory, &input.given);
            return;
        }
        let summary = discover::read_file(&input.name, &input.content, &input.given);
        self.add_bundle(PlannedBundle { summary, missing: false });
        self.read(&input.given, Kind::Bundle, "one bundle file");
    }

    // --- flows and profiles --------------------------------------------------------------------------------

    /// Plans every scenario that tells or walks the flow, or implements it.
    pub(crate) fn read_flow(&mut self, flow: &str, given: &str) -> anyhow::Result<()> {
        let loaded = self.catalog()?;
        let count = self.add_matches(&loaded.scenarios_of_flow(flow));
        self.read(given, Kind::Flow, flow);
        if count == 0 {
            self.unmapped(Unmapped {
                path: given.to_owned(),
                reason: avl_affected::reason::FLOW_DECLARES_NO_SUITE.to_owned(),
                flows: vec![flow.to_owned()],
                module: None,
                detail: Some("no generated scenario tells, walks or implements this flow".to_owned()),
            });
        }
        Ok(())
    }

    /// Reads a JSON document: a suite document, or anything holding run reports. It answers false for a JSON
    /// document that is neither, which is then read as a file like any other.
    fn read_json(&mut self, input: &Source) -> anyhow::Result<bool> {
        if let Ok(fields) = serde_json::from_slice::<serde_json::Map<String, Value>>(&input.content)
            && ["testClassName", "profiles", "suite"].iter().all(|key| fields.contains_key(*key))
        {
            self.read_profile(input);
            return Ok(true);
        }
        let documents = json_documents(&input.content);
        if documents.is_empty() {
            return Ok(false);
        }
        let mut holder = ReportHolder::default();
        for document in &documents {
            holder.collect(document);
        }
        if holder.reports.is_empty() && holder.reproduce.is_empty() && holder.errors.is_empty() {
            return Ok(false);
        }
        self.read_reports(&input.given, &holder)?;
        Ok(true)
    }

    /// Plans every scenario of one suite document, the document's own copy rather than the committed one, so a
    /// profile regenerated but not yet committed is planned as it reads.
    fn read_profile(&mut self, input: &Source) {
        let parsed = parse_suite_document(&input.content)
            .map_err(|reason| format!("is not a suite document: it {reason}"))
            .and_then(|owner| checked_lane(&owner).map(|_| owner).map_err(|refused| refused.message));
        match parsed {
            Ok(owner) => {
                for scenario in &owner.profiles {
                    self.add_scenario(&owner, scenario);
                }
                self.read(&input.given, Kind::Profile, owner.suite.clone());
            }
            Err(error) => self.unmapped(Unmapped::new(&input.given, reason::UNREADABLE, error)),
        }
    }

    // --- test results --------------------------------------------------------------------------------------

    /// Plans what a JUnit document says failed, and what it holds when nothing did.
    ///
    /// Read through the reader every other consumer of these documents uses, so a truncated file is read the same
    /// way here as in a report: whatever cases it has, and a note about its integrity.
    fn read_junit(&mut self, input: &Source) -> anyhow::Result<()> {
        let parsed = bt_junit::parse_report(&String::from_utf8_lossy(&input.content));
        let mut failed = Vec::new();
        let mut all = Vec::new();
        for case in parsed.suites.iter().flat_map(|suite| &suite.cases) {
            let entry = || TestCase {
                class_name: case.class_name.clone(),
                name: case.name.clone(),
            };
            if case.outcome == Outcome::Failed {
                failed.push(entry());
            }
            all.push(entry());
        }
        if !parsed.integrity.is_complete() {
            let mut state = parsed.integrity.status.as_str().to_owned();
            if let Some(message) = &parsed.integrity.message {
                state.push_str(": ");
                state.push_str(message);
            }
            self.note(format!("{} is {state}; the cases it does hold were read", input.given));
        }
        let (chosen, detail) = if failed.is_empty() {
            let detail = format!("nothing failed; all {} case(s)", all.len());
            (all, detail)
        } else {
            let detail = format!("{} failed or errored case(s)", failed.len());
            (failed, detail)
        };
        self.read(&input.given, Kind::Junit, detail);
        self.plan_cases(&input.given, &chosen)
    }

    /// Plans what the reports say failed, and names their traces.
    ///
    /// The failures are the report's own list, which is the XML's when the XML was complete and the progress
    /// stream's otherwise - the report already decided which account to trust, so this does not decide again. The
    /// reproduce list adds each failed class that named no failure the planner could read. A green report plans
    /// every case it holds, so a passing run can be recorded again on purpose.
    fn read_reports(&mut self, given: &str, holder: &ReportHolder) -> anyhow::Result<()> {
        for refusal in &holder.errors {
            self.unmapped(Unmapped::new(given, reason::UNREADABLE, refusal.clone()));
        }
        let mut cases = Vec::new();
        let mut failed_classes = std::collections::HashSet::new();
        for document in &holder.reports {
            for failure in &document.failures {
                match failure.class_name.as_deref().filter(|name| !name.is_empty()) {
                    Some(class_name) => {
                        cases.push(TestCase {
                            class_name: class_name.to_owned(),
                            name: failure.test_name.clone(),
                        });
                        failed_classes.insert(class_name.to_owned());
                    }
                    None => self.note(format!(
                        "{given} names a failure of {:?} with no class, which no command can select",
                        failure.test_name
                    )),
                }
            }
            self.read_report_traces(document);
        }
        // A reproduce command names a class and no case, so it would plan the whole class. It is taken only for a
        // class no failure above already named, which is a report this reader could not decode.
        for command in &holder.reproduce {
            if let Some(class_name) = command.strip_prefix("run ")
                && !failed_classes.contains(class_name)
            {
                cases.push(TestCase {
                    class_name: class_name.to_owned(),
                    name: String::new(),
                });
            }
        }
        let detail = if cases.is_empty() {
            for document in &holder.reports {
                for reported in document.suites.iter().flat_map(|suite| &suite.cases) {
                    cases.push(TestCase {
                        class_name: reported.class_name.clone(),
                        name: reported.name.clone(),
                    });
                }
            }
            format!("{} report(s), nothing failed; all {} case(s)", holder.reports.len(), cases.len())
        } else {
            format!("{} report(s), {} failure(s)", holder.reports.len(), cases.len())
        };
        self.read(given, Kind::Report, detail);
        self.plan_cases(given, &cases)
    }

    /// Names the bundles a report's traces archives hold, or why there are none.
    fn read_report_traces(&mut self, document: &RunReport) {
        let iteration = &document.iteration_id;
        for traces in &document.trace_archives {
            let archive = &traces.path;
            let (found, state) = self.scan(Path::new(archive));
            let problem = unreadable(&state);
            if !found.bundles.is_empty() {
                for bundle in &found.bundles {
                    self.add_bundle(PlannedBundle {
                        summary: bundle.summary.clone(),
                        missing: false,
                    });
                }
            } else if !state.exists && state.error.is_none() {
                self.add_bundle(PlannedBundle {
                    summary: Summary {
                        id: discover::bundle_id(SourceKind::Zip, archive, ""),
                        run_id: iteration.clone(),
                        source: BundleSource {
                            kind: SourceKind::Zip,
                            path: archive.clone(),
                            entry: String::new(),
                        },
                        ..Summary::default()
                    },
                    missing: true,
                });
                self.note(format!(
                    "iteration {iteration}'s traces are at {archive}, which this machine does not hold"
                ));
            } else if !problem.is_empty() {
                self.note(format!(
                    "iteration {iteration}'s traces archive {archive} cannot be read: {problem}"
                ));
            } else {
                self.note(format!("iteration {iteration}'s traces archive {archive} holds no bundle"));
            }
        }
        if let Some(error) = &document.traces_error {
            self.note(format!("iteration {iteration}'s traces were not pulled: {error}"));
        }
    }
}

/// The two-column status `git status --short` prints before each path, which a pasted list carries along. No path,
/// class, flow or scenario this planner reads is spelled like one.
fn is_git_status_marker(token: &str) -> bool {
    (1..=2).contains(&token.len()) && token.bytes().all(|byte| b"MADRCU?!".contains(&byte))
}

/// Whether a file on disk starts with a zip's local-header magic.
fn is_zip_file(absolute: &Path) -> bool {
    let mut magic = [0; 4];
    File::open(absolute)
        .and_then(|mut file| file.read_exact(&mut magic))
        .is_ok_and(|()| magic == ZIP_MAGIC)
}

fn contains(haystack: &[u8], needle: &[u8]) -> bool {
    haystack.windows(needle.len()).any(|window| window == needle)
}

/// Whether a word is a flow id as the generator spells one: `flow-` and at least one of `[A-Za-z0-9._-]`.
fn is_flow_id(word: &str) -> bool {
    word.strip_prefix("flow-")
        .is_some_and(|rest| !rest.is_empty() && rest.bytes().all(|byte| byte.is_ascii_alphanumeric() || b"._-".contains(&byte)))
}

/// The flow of the generated flow text's own header line, `flow        flow-rename-session`, when a line is one.
fn flow_header(content: &[u8]) -> Option<String> {
    content.split(|byte| *byte == b'\n').find_map(|line| {
        let line = line.strip_suffix(b"\r").unwrap_or(line);
        let line = std::str::from_utf8(line).ok()?.trim_end_matches([' ', '\t']);
        let rest = line.strip_prefix("flow")?;
        let id = rest.trim_start_matches([' ', '\t']);
        (id.len() < rest.len() && is_flow_id(id)).then(|| id.to_owned())
    })
}

/// The flow a generator's file name for a flow's text names: `flow-<id>.txt`.
fn flow_of_file_name(name: &str) -> Option<&str> {
    name.strip_suffix(".txt").filter(|stem| is_flow_id(stem))
}

/// The flow a flow text describes, or `None`.
///
/// The header wins over the name, because a renamed or downloaded copy keeps its header. The generator's comment
/// line is required when only the header is there, so a source file that happens to start a line with `flow
/// flow-x` is not read as a flow text.
pub(crate) fn flow_of_text(name: &str, content: &[u8]) -> Option<String> {
    let header = flow_header(content);
    let from_name = flow_of_file_name(name);
    if let Some(header) = &header
        && (from_name.is_some() || contains(content, b"# Air user flow"))
    {
        return Some(header.clone());
    }
    match from_name {
        Some(flow) if header.is_none() && (content.trim_ascii().is_empty() || content.starts_with(b"#")) => Some(flow.to_owned()),
        _ => None,
    }
}

/// The JSON values in a text: the whole text when it is one, and otherwise each line that is one. The second
/// shape is `vm.cmd --stream`'s, where NDJSON progress precedes the envelope.
fn json_documents(content: &[u8]) -> Vec<Value> {
    if let Ok(whole) = serde_json::from_slice::<Value>(content) {
        return vec![whole];
    }
    content
        .split(|byte| *byte == b'\n')
        .map(<[u8]>::trim_ascii)
        .filter(|line| !line.is_empty())
        .filter_map(|line| serde_json::from_slice(line).ok())
        .collect()
}

/// Every run report and reproduce list found inside one JSON document.
#[derive(Default)]
struct ReportHolder {
    reports: Vec<RunReport>,
    reproduce: Vec<String>,
    errors: Vec<String>,
}

impl ReportHolder {
    /// Walks a JSON value for run reports and reproduce lists.
    ///
    /// A walk rather than a path, because the report is at a different place in each document that carries one:
    /// the whole of a persisted report, `data.report` of a green `run`, `error.details.report` of a red one, and
    /// once per attempt inside a `shard` or `flake` aggregate. Each report is decoded by the wire's decoder, so one
    /// that fails its schema is refused here exactly as it would be anywhere else.
    fn collect(&mut self, value: &Value) {
        match value {
            Value::Object(fields) => {
                if fields.contains_key("reportSchemaVersion") {
                    let encoded = serde_json::to_vec(value).unwrap_or_default();
                    match decode_run_report(&encoded) {
                        Ok(report) => self.reports.push(report),
                        Err(error) => self.errors.push(error.message),
                    }
                    return;
                }
                for (key, nested) in fields {
                    if key == "reproduce"
                        && let Value::Array(commands) = nested
                    {
                        self.reproduce.extend(commands.iter().filter_map(Value::as_str).map(str::to_owned));
                        continue;
                    }
                    self.collect(nested);
                }
            }
            Value::Array(items) => {
                for nested in items {
                    self.collect(nested);
                }
            }
            _ => {}
        }
    }
}
