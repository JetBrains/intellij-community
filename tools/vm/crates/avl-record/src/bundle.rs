//! The files of one bundle: the OTLP line writer, the manifest, the bundle's directory, and the atomic write every
//! file a reader may tail goes through.

use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use anyhow::{Context, bail};
use avl_trace::bundle::{
    LOGS_FILE, MANIFEST_FILE, MANIFEST_SCHEMA, Manifest, PARTIAL_SUFFIX, SNAP_DIR, SPANS_FILE, bundle_dir, encode_manifest,
};
use avl_trace::otlp::{
    InstrumentationScope, KeyValue, LogRecord, LogsData, Resource, ResourceLogs, ResourceSpans, SCOPE_NAME, ScopeLogs, ScopeSpans,
    SeverityNumber, Span, SpanKindCode, TracesData, UnixNano, attr, event, span_id,
};

use crate::{Clock, Diagnostics, lock, unix_ms};

/// Appends one bundle's spans and log records.
///
/// Each line is written with one write call on a file opened for appending, and nothing is buffered. A recorder
/// killed outright therefore leaves every line it had written, whole, and a viewer tailing the bundle while the
/// scenario runs reads the same lines the finished bundle will hold.
///
/// It is shared by the command loop and the threads that report on their own (the frame loop, the still encoder),
/// hence the lock. A write that fails is reported once on the diagnostics stream and the bundle is otherwise left
/// alone: evidence that cannot be written must not become a failure of the scenario it watches.
pub(crate) struct BundleWriter {
    dir: PathBuf,
    trace_id: String,
    resource: Resource,
    /// The IDE under test, the resource of every span [BundleWriter::ide_span] writes.
    ide_resource: Resource,
    scope: InstrumentationScope,
    clock: Clock,
    diagnostics: Diagnostics,
    files: Mutex<Files>,
}

struct Files {
    /// `None` once the bundle is closed.
    open: Option<(File, File)>,
    failed: bool,
}

impl BundleWriter {
    pub(crate) fn open(
        dir: &Path,
        trace_id: String,
        resource: Resource,
        ide_resource: Resource,
        clock: Clock,
        diagnostics: Diagnostics,
    ) -> io::Result<Self> {
        let append = |name: &str| {
            OpenOptions::new()
                .create(true)
                .write(true)
                .truncate(true)
                .open(dir.join(name))
                .and_then(|file| {
                    // Reopened for appending, since std does not take truncate and append together.
                    drop(file);
                    OpenOptions::new().append(true).open(dir.join(name))
                })
        };
        let spans = append(SPANS_FILE)?;
        let logs = append(LOGS_FILE)?;
        Ok(Self {
            dir: dir.to_owned(),
            trace_id,
            resource,
            ide_resource,
            scope: InstrumentationScope {
                name: SCOPE_NAME.to_owned(),
                version: MANIFEST_SCHEMA.to_owned(),
                ..InstrumentationScope::default()
            },
            clock,
            diagnostics,
            files: Mutex::new(Files {
                open: Some((spans, logs)),
                failed: false,
            }),
        })
    }

    /// Appends one log record, correlated with this bundle's trace and observed now. A record without a severity
    /// is INFO.
    pub(crate) fn record(&self, mut record: LogRecord) {
        record.trace_id = self.trace_id.clone();
        record.observed_time_unix_nano = nanos(unix_ms((self.clock)()));
        if record.severity_number == SeverityNumber::UNSPECIFIED {
            record.severity_number = SeverityNumber::INFO;
            record.severity_text = "INFO".to_owned();
        }
        let document = LogsData {
            resource_logs: vec![ResourceLogs {
                resource: self.resource.clone(),
                scope_logs: vec![ScopeLogs {
                    scope: self.scope.clone(),
                    log_records: vec![record],
                    ..ScopeLogs::default()
                }],
                ..ResourceLogs::default()
            }],
        };
        self.append(false, avl_trace::encode(&document));
    }

    /// Appends one ended span.
    pub(crate) fn span(&self, span: Span) {
        self.append_span(&self.resource, self.scope.clone(), span);
    }

    /// Appends one span of the IDE, under the IDE's resource and the span's own instrumentation scope.
    pub(crate) fn ide_span(&self, scope: &str, span: Span) {
        let scope = InstrumentationScope {
            name: scope.to_owned(),
            ..InstrumentationScope::default()
        };
        self.append_span(&self.ide_resource, scope, span);
    }

    fn append_span(&self, resource: &Resource, scope: InstrumentationScope, mut span: Span) {
        span.trace_id = self.trace_id.clone();
        span.kind = SpanKindCode::INTERNAL;
        let document = TracesData {
            resource_spans: vec![ResourceSpans {
                resource: resource.clone(),
                scope_spans: vec![ScopeSpans {
                    scope,
                    spans: vec![span],
                    ..ScopeSpans::default()
                }],
                ..ResourceSpans::default()
            }],
        };
        self.append(true, avl_trace::encode(&document));
    }

    /// Records evidence that could not be collected, on the span `id`.
    pub(crate) fn trace_error_on(&self, id: u32, source: &str, message: &str, at_ms: i64) {
        self.record(LogRecord {
            time_unix_nano: nanos(at_ms),
            severity_number: SeverityNumber::WARN,
            severity_text: "WARN".to_owned(),
            span_id: span_id(id),
            event_name: event::TRACE_ERROR.to_owned(),
            attributes: vec![
                KeyValue::string(attr::TRACE_ERROR_SOURCE, source),
                KeyValue::string(attr::TRACE_ERROR_MESSAGE, message),
            ],
            ..LogRecord::default()
        });
    }

    fn append(&self, spans: bool, line: Result<Vec<u8>, avl_trace::Error>) {
        let mut files = lock(&self.files);
        let result = match (line, files.open.as_mut()) {
            (Ok(mut line), Some((spans_file, logs_file))) => {
                line.push(b'\n');
                let file = if spans { spans_file } else { logs_file };
                file.write_all(&line).map_err(|error| error.to_string())
            }
            (Ok(_), None) => Err("the bundle is closed".to_owned()),
            (Err(error), _) => Err(error.to_string()),
        };
        if let Err(error) = result
            && !files.failed
        {
            files.failed = true;
            say!(
                self.diagnostics,
                "{}: writing the bundle failed, later lines may be missing: {error}",
                self.dir.display()
            );
        }
    }

    pub(crate) fn close(&self) {
        lock(&self.files).open = None;
    }
}

/// Writes `bundle.json`, last and atomically: a bundle with a manifest is complete.
pub(crate) fn write_manifest(dir: &Path, manifest: &Manifest) -> anyhow::Result<()> {
    let document = encode_manifest(manifest).context("the recorder built a manifest the contract refuses")?;
    write_file_atomically(&dir.join(MANIFEST_FILE), &document)?;
    Ok(())
}

/// Writes `content` under a temporary name and renames it into place, so a viewer tailing a bundle that is still
/// being written never reads half a file, and a pack skips the temporary name.
pub(crate) fn write_file_atomically(path: &Path, content: &[u8]) -> io::Result<()> {
    let mut temporary = path.as_os_str().to_owned();
    temporary.push(PARTIAL_SUFFIX);
    let temporary = PathBuf::from(temporary);
    fs::write(&temporary, content)?;
    fs::rename(&temporary, path).inspect_err(|_| {
        let _ = fs::remove_file(&temporary);
    })
}

/// Copies `from` to `to` under a [PARTIAL_SUFFIX] name and renames the copy into place, so a reader never finds half a
/// file at `to`. A failed rename removes the copy.
pub(crate) fn copy_into_place(from: &Path, to: &Path) -> io::Result<()> {
    let mut partial = to.as_os_str().to_owned();
    partial.push(PARTIAL_SUFFIX);
    fs::copy(from, &partial)?;
    fs::rename(&partial, to).inspect_err(|_| {
        let _ = fs::remove_file(&partial);
    })
}

/// Epoch milliseconds as OTLP holds them.
///
/// Every time the recorder writes is cut to the millisecond, its own receipt stamps included, because the lane stamps
/// calls in milliseconds. A span stamped in nanoseconds could start a fraction of a millisecond
/// after a call the lane made inside it, and the viewer's join of a record to its span would miss.
pub(crate) fn nanos(ms: i64) -> UnixNano {
    UnixNano::from_ms(ms)
}

/// The scenario's bundle directory, created with its snapshot directory.
///
/// A scenario runs once per run, so the directory is normally new. When it is not, the same scenario ran again in
/// the same run, a retry or a rerun of the class, and overwriting would destroy the attempt most worth reading, so
/// the new attempt gets `.2`, `.3` and so on.
pub(crate) fn unique_bundle_dir(root: &Path, run_id: &str, test_class: &str, scenario: &str) -> anyhow::Result<PathBuf> {
    let base = bundle_dir(root, run_id, test_class, scenario);
    if let Some(parent) = base.parent() {
        fs::create_dir_all(parent)?;
    }
    for attempt in 1..=100 {
        let dir = if attempt == 1 {
            base.clone()
        } else {
            let mut name = base.as_os_str().to_owned();
            name.push(format!(".{attempt}"));
            PathBuf::from(name)
        };
        match fs::create_dir(&dir) {
            Ok(()) => {
                fs::create_dir(dir.join(SNAP_DIR))?;
                return Ok(dir);
            }
            Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error.into()),
        }
    }
    bail!("{} already has 100 attempts", base.display())
}
