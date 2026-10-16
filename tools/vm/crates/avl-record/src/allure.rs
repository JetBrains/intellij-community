//! The Allure result of one finished bundle, in the run's `allure-results` directory.
//!
//! The result is read back from the bundle's own files, so it holds what the bundle holds and nothing the bundle
//! lost. The lane spans become the steps. Each step names the Before and After pictures of its snapshots and the
//! files the lane attached to its span. The result names the video, the log slice and the bundle.

use std::collections::HashMap;
use std::fs;
use std::io;
use std::path::Path;

use anyhow::Context;
use avl_trace::bundle::{
    ALLURE_BUNDLE_LABEL, AllureAttachment, AllureResult, AllureStage, AllureStatusDetails, AllureStep, BundleStatus, IDEA_LOG_FILE,
    LOGS_FILE, Manifest, SPANS_FILE, VIDEO_FILE, Video, allure_attachment_file, allure_history_id, allure_result_file, bundle_file,
    derived_uuid, encode_allure_result,
};
use avl_trace::otlp::{SERVICE_NAME, attr, decode_logs_line, decode_traces_line, event, lookup_int, lookup_str, span_id};
use avl_trace::protocol::{Label, Status};

use crate::bundle::{copy_into_place, write_file_atomically};

// The titles of the attachments the recorder adds.
pub(crate) const BEFORE: &str = "Before";
pub(crate) const AFTER: &str = "After";
pub(crate) const VIDEO: &str = "Video";
pub(crate) const BUNDLE: &str = "Trace bundle";

const MIME_WEBP: &str = "image/webp";
const MIME_MP4: &str = "video/mp4";
const MIME_TEXT: &str = "text/plain";

/// What the result needs beyond the bundle's files.
pub(crate) struct ResultInput<'a> {
    pub(crate) manifest: &'a Manifest,
    /// The bundle's path under the run's directory, such as `AirExampleUiTest/example.2`.
    pub(crate) bundle: &'a str,
    /// The scenario command's labels.
    pub(crate) labels: &'a [Label],
    /// The scenario command's suite.
    pub(crate) suite: Option<&'a str>,
    pub(crate) host: &'a str,
    /// The `done` command's message and trace.
    pub(crate) details: AllureStatusDetails,
}

/// Writes the Allure result of the bundle `dir` into `results`, and answers what it had to leave out: a picture or
/// a lane file that the bundle names and does not hold.
///
/// The attachments go first, and the result last under a partial name that is renamed into place. So a
/// reader of the directory never finds a result that names a file not there yet.
pub(crate) fn write_result(dir: &Path, results: &Path, input: &ResultInput<'_>) -> anyhow::Result<Vec<String>> {
    let tree = Tree::read(dir)?;
    let manifest = input.manifest;
    let start = manifest
        .started_at
        .parse::<jiff::Timestamp>()
        .map(jiff::Timestamp::as_millisecond)
        .with_context(|| format!("the manifest's start {:?}", manifest.started_at))?;
    let stop = start.saturating_add(i64::try_from(manifest.duration_ms).unwrap_or(i64::MAX));
    let uuid = derived_uuid(&[&manifest.run_id, input.bundle, &manifest.started_at]);
    fs::create_dir_all(results).with_context(|| format!("cannot create {}", results.display()))?;
    let mut linker = Linker {
        dir,
        results,
        uuid: &uuid,
        linked: HashMap::new(),
        count: 0,
        problems: Vec::new(),
        link: |from, to| fs::hard_link(from, to),
    };

    let mut attachments = Vec::new();
    attachments.push(linker.text(BUNDLE, &format!("{}\n", input.bundle))?);
    if manifest.video == Video::H264 {
        attachments.extend(linker.optional(VIDEO, MIME_MP4, VIDEO_FILE));
    }
    attachments.extend(linker.optional(IDEA_LOG_FILE, MIME_TEXT, IDEA_LOG_FILE));
    let root = span_id(0);
    attachments.extend(tree.attached(&root, &mut linker));
    let steps = tree.steps(&root, &mut linker);

    let full_name = format!("{}.{}", manifest.test_class, manifest.scenario);
    let mut details = input.details.clone();
    if manifest.status == BundleStatus::Truncated && details.is_empty() {
        let running = manifest.running_span.as_deref().unwrap_or_default();
        let title = tree.spans.get(running).map_or(running, |span| span.name.as_str());
        details.message = Some(format!("the lane stopped before done, in the span {title:?}"));
    }
    let result = AllureResult {
        uuid: uuid.clone(),
        history_id: allure_history_id(&full_name),
        full_name,
        name: manifest.scenario.clone(),
        status: manifest.status.into(),
        status_details: details,
        stage: AllureStage::Finished,
        start,
        stop,
        labels: labels(input),
        steps,
        attachments,
    };
    let document = encode_allure_result(&result).context("the recorder built an Allure result the contract refuses")?;
    let path = results.join(allure_result_file(&uuid));
    write_file_atomically(&path, &document).with_context(|| format!("cannot write {}", path.display()))?;
    Ok(linker.problems)
}

/// The lane's labels, then the ones the recorder adds when the lane sent none of that name: `suite`, `testClass`,
/// `package`, `host`, `lane` and `flow`. The [ALLURE_BUNDLE_LABEL] is always the recorder's own.
fn labels(input: &ResultInput<'_>) -> Vec<Label> {
    let manifest = input.manifest;
    let mut labels: Vec<Label> = input
        .labels
        .iter()
        .filter(|label| label.name != ALLURE_BUNDLE_LABEL)
        .cloned()
        .collect();
    let (package, simple) = manifest
        .test_class
        .rsplit_once('.')
        .map_or((None, manifest.test_class.as_str()), |(package, simple)| (Some(package), simple));
    let added = [
        ("suite", Some(input.suite.filter(|suite| !suite.is_empty()).unwrap_or(simple))),
        ("testClass", Some(manifest.test_class.as_str())),
        ("package", package),
        ("host", Some(input.host).filter(|host| !host.is_empty())),
        ("lane", Some(manifest.lane.as_str())),
        ("flow", manifest.flow.as_deref()),
    ];
    for (name, value) in added {
        if let Some(value) = value
            && !labels.iter().any(|label| label.name == name)
        {
            labels.push(Label {
                name: name.to_owned(),
                value: value.to_owned(),
            });
        }
    }
    labels.push(Label {
        name: ALLURE_BUNDLE_LABEL.to_owned(),
        value: input.bundle.to_owned(),
    });
    labels
}

// --- the bundle as the result reads it -----------------------------------------------------------------------

struct LaneSpan {
    name: String,
    status: Status,
    message: String,
    start: i64,
    stop: i64,
}

/// A picture of a snapshot.
struct Picture {
    ordinal: i64,
    span: String,
    image: String,
}

/// A file the lane attached.
struct Attached {
    name: String,
    mime: String,
    file: String,
}

/// The lane spans of a bundle by their OTLP id, with the pictures and the attachments of its records.
struct Tree {
    spans: HashMap<String, LaneSpan>,
    /// The spans under each span, in the order they started.
    children: HashMap<String, Vec<String>>,
    /// Every snapshot that has a picture, in the order the snapshots were taken.
    pictures: Vec<Picture>,
    /// The pictures of each span itself, as indexes into `pictures`.
    pictures_of: HashMap<String, Vec<usize>>,
    attached: HashMap<String, Vec<Attached>>,
}

impl Tree {
    fn read(dir: &Path) -> anyhow::Result<Self> {
        let mut tree = Self {
            spans: HashMap::new(),
            children: HashMap::new(),
            pictures: Vec::new(),
            pictures_of: HashMap::new(),
            attached: HashMap::new(),
        };
        let mut parents = Vec::new();
        for line in read_lines(&dir.join(SPANS_FILE))? {
            let data = decode_traces_line(line.as_bytes())?;
            for resource in data.resource_spans {
                // The IDE's spans are the IDE's work, not the scenario's steps.
                if lookup_str(&resource.resource.attributes, attr::SERVICE_NAME) != Some(SERVICE_NAME) {
                    continue;
                }
                for span in resource.scope_spans.into_iter().flat_map(|scope| scope.spans) {
                    let status = lookup_str(&span.attributes, attr::SPAN_STATUS)
                        .and_then(Status::from_word)
                        .unwrap_or(Status::Aborted);
                    let start = span.start_time_unix_nano.ms();
                    parents.push((span.parent_span_id, start, span.span_id.clone()));
                    tree.spans.insert(
                        span.span_id,
                        LaneSpan {
                            name: span.name,
                            status,
                            message: span.status.map(|status| status.message).unwrap_or_default(),
                            start,
                            stop: span.end_time_unix_nano.ms(),
                        },
                    );
                }
            }
        }
        parents.sort();
        for (parent, _, id) in parents {
            if !parent.is_empty() {
                tree.children.entry(parent).or_default().push(id);
            }
        }
        for line in read_lines(&dir.join(LOGS_FILE))? {
            let data = decode_logs_line(line.as_bytes())?;
            let records = data
                .resource_logs
                .into_iter()
                .flat_map(|resource| resource.scope_logs)
                .flat_map(|scope| scope.log_records);
            for record in records {
                let text = |key: &str| lookup_str(&record.attributes, key).unwrap_or_default().to_owned();
                match record.event_name.as_str() {
                    event::SNAPSHOT => {
                        let image = text(attr::SNAPSHOT_IMAGE);
                        if !image.is_empty() {
                            tree.pictures.push(Picture {
                                ordinal: lookup_int(&record.attributes, attr::SNAPSHOT_ORDINAL).unwrap_or_default(),
                                span: record.span_id.clone(),
                                image,
                            });
                        }
                    }
                    event::ATTACHMENT => {
                        let attached = Attached {
                            name: text(attr::ATTACHMENT_NAME),
                            mime: text(attr::ATTACHMENT_MIME),
                            file: text(attr::ATTACHMENT_FILE),
                        };
                        tree.attached.entry(record.span_id.clone()).or_default().push(attached);
                    }
                    _ => {}
                }
            }
        }
        tree.pictures.sort_by_key(|picture| picture.ordinal);
        for (index, picture) in tree.pictures.iter().enumerate() {
            tree.pictures_of.entry(picture.span.clone()).or_default().push(index);
        }
        Ok(tree)
    }

    /// The first and the last picture of the span and the spans inside it, as indexes into `pictures`.
    fn picture_range(&self, id: &str) -> Option<(usize, usize)> {
        let own = self.pictures_of.get(id).into_iter().flatten().map(|index| (*index, *index));
        let inner = self
            .children
            .get(id)
            .into_iter()
            .flatten()
            .filter_map(|child| self.picture_range(child));
        own.chain(inner)
            .reduce(|(first, last), (start, end)| (first.min(start), last.max(end)))
    }

    /// The Before and After pictures of a span. Before is the first picture taken in the span. After is the first
    /// picture taken after the span's last one, or that last one when the scenario took no later picture. A step's
    /// After is so the next step's Before, as the lane's boundary snapshots define them.
    fn before_and_after(&self, id: &str) -> (Option<&Picture>, Option<&Picture>) {
        let Some((first, last)) = self.picture_range(id) else {
            return (None, None);
        };
        let after = self.pictures.get(last + 1).or_else(|| (last > first).then(|| &self.pictures[last]));
        (self.pictures.get(first), after)
    }

    /// The steps of the spans under `id`, in the order they started.
    fn steps(&self, id: &str, linker: &mut Linker<'_>) -> Vec<AllureStep> {
        let mut steps = Vec::new();
        for child in self.children.get(id).into_iter().flatten() {
            let Some(span) = self.spans.get(child) else {
                continue;
            };
            let (before, after) = self.before_and_after(child);
            let mut attachments = Vec::new();
            for (title, picture) in [(BEFORE, before), (AFTER, after)] {
                if let Some(picture) = picture {
                    attachments.extend(linker.required(title, MIME_WEBP, &picture.image));
                }
            }
            attachments.extend(self.attached(child, linker));
            let status_details = AllureStatusDetails {
                message: (!span.message.is_empty()).then(|| span.message.clone()),
                trace: None,
            };
            steps.push(AllureStep {
                name: span.name.clone(),
                status: span.status.into(),
                status_details,
                stage: AllureStage::Finished,
                start: span.start,
                stop: span.stop,
                steps: self.steps(child, linker),
                attachments,
            });
        }
        steps
    }

    /// The files the lane attached to the span `id`.
    fn attached(&self, id: &str, linker: &mut Linker<'_>) -> Vec<AllureAttachment> {
        self.attached
            .get(id)
            .into_iter()
            .flatten()
            .filter_map(|attached| linker.required(&attached.name, &attached.mime, &attached.file))
            .collect()
    }
}

fn read_lines(path: &Path) -> anyhow::Result<Vec<String>> {
    let content = fs::read_to_string(path).with_context(|| format!("cannot read {}", path.display()))?;
    Ok(content.lines().filter(|line| !line.is_empty()).map(str::to_owned).collect())
}

// --- the attachment files ------------------------------------------------------------------------------------

/// Puts a file at a new path without a copy of its bytes, as [fs::hard_link] does.
type LinkFile = fn(&Path, &Path) -> io::Result<()>;

/// Puts the bundle's files into the results directory, each once, as `<uuid>-attachment.<ext>`.
struct Linker<'a> {
    dir: &'a Path,
    results: &'a Path,
    /// The result's uuid, from which each attachment's uuid derives.
    uuid: &'a str,
    /// The results file of each bundle file already put there, so a picture that two steps name is one file.
    linked: HashMap<String, String>,
    count: u32,
    problems: Vec<String>,
    /// How a bundle file gets into the results directory before [copy_into_place] copies it.
    link: LinkFile,
}

impl Linker<'_> {
    fn next_source(&mut self, extension: &str) -> String {
        self.count += 1;
        let uuid = derived_uuid(&[self.uuid, &self.count.to_string()]);
        allure_attachment_file(&uuid, extension)
    }

    /// An attachment of a bundle file that the bundle names, or `None` and a problem when the file is not there.
    fn required(&mut self, name: &str, mime: &str, bundle_path: &str) -> Option<AllureAttachment> {
        let attachment = self.link(name, mime, bundle_path);
        if let Err(error) = &attachment {
            self.problems.push(format!("{bundle_path}: not in the Allure result: {error}"));
        }
        attachment.ok()
    }

    /// An attachment of a bundle file that may be absent, such as the log slice.
    fn optional(&mut self, name: &str, mime: &str, bundle_path: &str) -> Option<AllureAttachment> {
        if !bundle_file(self.dir, bundle_path).is_file() {
            return None;
        }
        self.required(name, mime, bundle_path)
    }

    fn link(&mut self, name: &str, mime: &str, bundle_path: &str) -> io::Result<AllureAttachment> {
        let attachment = |source: String| AllureAttachment {
            name: name.to_owned(),
            source,
            mime: mime.to_owned(),
        };
        if let Some(source) = self.linked.get(bundle_path) {
            return Ok(attachment(source.clone()));
        }
        let from = bundle_file(self.dir, bundle_path);
        if !fs::metadata(&from)?.is_file() {
            return Err(io::Error::other("it is not a regular file"));
        }
        let source = self.next_source(extension_of(bundle_path));
        link_or_copy(&from, &self.results.join(&source), self.link)?;
        self.linked.insert(bundle_path.to_owned(), source.clone());
        Ok(attachment(source))
    }

    /// A text attachment that the recorder writes itself.
    fn text(&mut self, name: &str, content: &str) -> anyhow::Result<AllureAttachment> {
        let source = self.next_source("txt");
        let path = self.results.join(&source);
        write_file_atomically(&path, content.as_bytes()).with_context(|| format!("cannot write {}", path.display()))?;
        Ok(AllureAttachment {
            name: name.to_owned(),
            source,
            mime: MIME_TEXT.to_owned(),
        })
    }
}

/// The extension of a bundle path's last segment, or `bin` when it has none.
fn extension_of(bundle_path: &str) -> &str {
    let base = bundle_path.rsplit_once('/').map_or(bundle_path, |(_, base)| base);
    base.rsplit_once('.').map_or("bin", |(_, extension)| extension)
}

/// Puts `from` at `to` with `link`, a hard link that costs no space. A link fails across two file systems, and then
/// [copy_into_place] copies the file.
fn link_or_copy(from: &Path, to: &Path, link: LinkFile) -> io::Result<()> {
    if link(from, to).is_ok() {
        return Ok(());
    }
    copy_into_place(from, to)
}

#[cfg(test)]
mod tests;
