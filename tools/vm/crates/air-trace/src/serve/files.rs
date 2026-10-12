//! Serving bytes: a zip entry in place or through the inflate cache, a file with `Range`, and the answers every
//! route shares.

use std::fs::{self, File};
use std::io::{self, Read};
use std::ops::RangeInclusive;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use avl_base::sync::lock;
use avl_trace_tools::discover::{EntryMeta, ZipIndex};
use axum::body::Body;
use axum::response::{IntoResponse, Response};
use bytes::Bytes;
use futures::stream;
use http::{HeaderMap, HeaderValue, Method, Request, StatusCode, header};
use serde::Serialize;
use sha2::{Digest, Sha256};
use tower::ServiceExt;
use tower_http::services::ServeFile;

use super::state::Server;

/// A JSON answer, never cached.
pub(crate) fn json_response(status: StatusCode, value: &impl Serialize) -> Response {
    let mut body = match serde_json::to_vec(value) {
        Ok(body) => body,
        Err(error) => return text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
    };
    body.push(b'\n');
    (
        status,
        [
            (header::CONTENT_TYPE, "application/json; charset=utf-8"),
            (header::CACHE_CONTROL, "no-store"),
        ],
        body,
    )
        .into_response()
}

/// A refusal as one line of text.
pub(crate) fn text_error(status: StatusCode, message: &str) -> Response {
    (
        status,
        [
            (header::CONTENT_TYPE, "text/plain; charset=utf-8"),
            (header::X_CONTENT_TYPE_OPTIONS, "nosniff"),
        ],
        format!("{message}\n"),
    )
        .into_response()
}

/// A bundle file's type, by extension. The ones a platform table may lack are named.
pub(crate) fn content_type(file: &str) -> String {
    let extension = Path::new(file)
        .extension()
        .map(|extension| extension.to_string_lossy().to_ascii_lowercase())
        .unwrap_or_default();
    match extension.as_str() {
        "jsonl" => "application/jsonl; charset=utf-8".to_owned(),
        "json" => "application/json; charset=utf-8".to_owned(),
        "log" => "text/plain; charset=utf-8".to_owned(),
        "webp" => "image/webp".to_owned(),
        "mp4" => "video/mp4".to_owned(),
        _ => mime_guess::from_ext(&extension)
            .first()
            .map_or_else(|| "application/octet-stream".to_owned(), |mime| mime.to_string()),
    }
}

/// Whether an `If-None-Match` names this tag.
pub(crate) fn matches_etag(headers: &HeaderMap, etag: &str) -> bool {
    headers
        .get_all(header::IF_NONE_MATCH)
        .iter()
        .filter_map(|value| value.to_str().ok())
        .flat_map(|value| value.split(','))
        .map(str::trim)
        .any(|candidate| candidate == "*" || candidate == etag || candidate.strip_prefix("W/") == Some(etag))
}

fn unix_nanos(time: SystemTime) -> u128 {
    time.duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos()
}

/// What a request's `Range` asks of a body of `len` bytes.
enum Wanted {
    Whole,
    Part(RangeInclusive<u64>),
    Unsatisfiable,
}

fn wanted(headers: &HeaderMap, len: u64) -> Wanted {
    let Some(value) = headers.get(header::RANGE).and_then(|value| value.to_str().ok()) else {
        return Wanted::Whole;
    };
    match http_range_header::parse_range_header(value).and_then(|parsed| parsed.validate(len)) {
        Ok(ranges) if ranges.len() == 1 => Wanted::Part(ranges[0].clone()),
        // Several ranges are answered whole; zip.js and the video element ask for one at a time.
        Ok(_) => Wanted::Whole,
        Err(_) => Wanted::Unsatisfiable,
    }
}

/// The chunk a streamed body reads at a time.
const CHUNK: u64 = 256 << 10;

/// A body produced by blocking reads, so a large entry is neither held in memory nor read on an async worker.
/// `produce` hands each chunk to its sink, which answers false once the client went away; an error ends the body.
fn blocking_body(produce: impl FnOnce(&mut dyn FnMut(Vec<u8>) -> bool) -> io::Result<()> + Send + 'static) -> Body {
    let (sender, receiver) = tokio::sync::mpsc::channel::<io::Result<Bytes>>(4);
    tokio::task::spawn_blocking(move || {
        let mut sink = |chunk: Vec<u8>| sender.blocking_send(Ok(Bytes::from(chunk))).is_ok();
        if let Err(error) = produce(&mut sink) {
            // A client that went away already dropped the receiver, and has no use for the error.
            let _ = sender.blocking_send(Err(error));
        }
    });
    Body::from_stream(stream::unfold(receiver, |mut receiver| async move {
        receiver.recv().await.map(|item| (item, receiver))
    }))
}

// --- zip entries ---------------------------------------------------------------------------------------------

impl Server {
    /// Serves one file of a zip bundle. A stored entry is a section of the zip file, so `Range` is answered in
    /// place; a deflated one is streamed whole, or inflated once into the cache when a range of it is asked for.
    pub(crate) async fn serve_zip_entry(self: &Arc<Self>, request: Request<Body>, zip: PathBuf, name: String, file: &str) -> Response {
        let index = match self.blocking(move |server| server.scanner.zips().get(&zip)).await {
            Ok(index) => index,
            Err(error) => return text_error(StatusCode::NOT_FOUND, &error.to_string()),
        };
        let Some(entry) = index.entries().get(&name).cloned() else {
            return text_error(StatusCode::NOT_FOUND, "404 page not found");
        };
        let etag = format!("\"z-{:08x}-{}\"", entry.crc32, entry.size);
        let headers = request.headers();
        if entry.stored {
            return ranged(headers, request.method(), entry.size, &etag, &content_type(file), move |range| {
                let (index, entry) = (Arc::clone(&index), entry.clone());
                blocking_body(move |sink| {
                    let mut position = *range.start();
                    let end = range.end() + 1;
                    while position < end {
                        let next = (position + CHUNK).min(end);
                        if !sink(index.read_stored(&entry, position..next)?) {
                            break;
                        }
                        position = next;
                    }
                    Ok(())
                })
            });
        }
        if headers.contains_key(header::RANGE) {
            let cached = {
                let (index, name, entry) = (Arc::clone(&index), name.clone(), entry.clone());
                self.blocking(move |server| server.inflated(&index, &name, &entry)).await
            };
            return match cached {
                Ok(cached) => {
                    let mut response = serve_file(request, &cached, &content_type(file), false).await;
                    set(response.headers_mut(), header::ETAG, &etag);
                    response
                }
                Err(error) => text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
            };
        }
        let mut response = if matches_etag(headers, &etag) {
            StatusCode::NOT_MODIFIED.into_response()
        } else if request.method() == Method::HEAD {
            let mut response = StatusCode::OK.into_response();
            set(response.headers_mut(), header::CONTENT_LENGTH, &entry.size.to_string());
            response
        } else {
            let mut response = Response::new(blocking_body(move |sink| {
                with_entry(&index, &name, |reader| {
                    loop {
                        let mut chunk = vec![0; usize::try_from(CHUNK).unwrap_or(usize::MAX)];
                        let read = reader.read(&mut chunk)?;
                        if read == 0 {
                            return Ok(());
                        }
                        chunk.truncate(read);
                        if !sink(chunk) {
                            return Ok(());
                        }
                    }
                })
            }));
            set(response.headers_mut(), header::CONTENT_LENGTH, &entry.size.to_string());
            response
        };
        let headers = response.headers_mut();
        set(headers, header::CONTENT_TYPE, &content_type(file));
        set(headers, header::CACHE_CONTROL, "no-cache");
        set(headers, header::ETAG, &etag);
        set(headers, header::ACCEPT_RANGES, "bytes");
        response
    }

    /// A deflated entry as a file in the inflate cache, inflated the first time. Only a ranged request pays for
    /// this: a video scrubbed backwards asks for the same bytes many times, and inflating from the start for each
    /// range would read the entry quadratically. A whole-entry request streams instead.
    ///
    /// The cache is keyed by the zip's version as well as its path, so a zip Bazel rewrote never answers a stale
    /// entry; the server prunes the versions nobody read for a week when it starts.
    pub(crate) fn inflated(&self, index: &ZipIndex, name: &str, entry: &EntryMeta) -> io::Result<PathBuf> {
        let zip_key = Sha256::new()
            .chain_update(index.path.to_string_lossy().as_bytes())
            .chain_update([0])
            .chain_update(index.len.to_string())
            .chain_update([0])
            .chain_update(unix_nanos(index.modified).to_string())
            .finalize();
        let entry_key = Sha256::digest(name.as_bytes());
        let dir = self.settings.cache_dir.join(hex::encode(&zip_key[..8]));
        // The entry's own name is never a path here: a zip may name anything, and the cache is written to.
        let extension = Path::new(name)
            .extension()
            .map(|extension| format!(".{}", extension.to_string_lossy()))
            .unwrap_or_default();
        let target = dir.join(format!("{}{extension}", hex::encode(&entry_key[..8])));

        let _serialized = lock(&self.inflate);
        if fs::metadata(&target).is_ok_and(|info| info.len() == entry.size) {
            // Read now, so the pruning keeps it.
            let _ = set_modified(&dir, SystemTime::now());
            return Ok(target);
        }
        fs::create_dir_all(&dir)?;
        let mut temporary = tempfile::Builder::new().prefix(".inflate-").tempfile_in(&dir)?;
        // The zip reader checks the entry's CRC-32 at its end, so a damaged entry fails here rather than caching.
        with_entry(index, name, |reader| io::copy(reader, temporary.as_file_mut()).map(drop))?;
        avl_base::fs::move_into_place(temporary, &target)?;
        Ok(target)
    }
}

/// Reads an entry of a zip, inflating, from a fresh handle on the version of the zip the index describes.
fn with_entry<T>(index: &ZipIndex, name: &str, read: impl FnOnce(&mut dyn Read) -> io::Result<T>) -> io::Result<T> {
    let file = File::open(&index.path)?;
    let info = file.metadata()?;
    if info.len() != index.len || info.modified()? != index.modified {
        return Err(io::Error::other(format!("{} changed while it was read", index.path.display())));
    }
    let mut archive = zip::ZipArchive::new(file).map_err(io::Error::from)?;
    let mut entry = archive.by_name(name).map_err(io::Error::from)?;
    read(&mut entry)
}

// --- files ---------------------------------------------------------------------------------------------------

/// Serves a file of the disk with `Range`, as tower-http does. `keep_modified` keeps the file's time as the
/// validator; without it the caller's tag is the only one, because a file that grows twice within one second
/// would otherwise answer 304 to a request that should see the second write.
pub(crate) async fn serve_file(mut request: Request<Body>, path: &Path, content_type: &str, keep_modified: bool) -> Response {
    if !keep_modified {
        let headers = request.headers_mut();
        headers.remove(header::IF_MODIFIED_SINCE);
        headers.remove(header::IF_UNMODIFIED_SINCE);
        headers.remove(header::IF_RANGE);
    }
    let mime = content_type
        .parse::<mime_guess::mime::Mime>()
        .unwrap_or(mime_guess::mime::APPLICATION_OCTET_STREAM);
    match ServeFile::new_with_mime(path, &mime).oneshot(request).await {
        Ok(response) => {
            let mut response = response.map(Body::new);
            if !keep_modified {
                response.headers_mut().remove(header::LAST_MODIFIED);
            }
            response
        }
        Err(error) => text_error(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
    }
}

/// Answers a body of `len` bytes with `Range`, `If-None-Match` and `HEAD`, reading only the bytes asked for.
fn ranged(
    headers: &HeaderMap,
    method: &Method,
    len: u64,
    etag: &str,
    content_type: &str,
    body: impl FnOnce(RangeInclusive<u64>) -> Body,
) -> Response {
    let mut response = if matches_etag(headers, etag) {
        StatusCode::NOT_MODIFIED.into_response()
    } else {
        let (status, range) = match wanted(headers, len) {
            Wanted::Unsatisfiable => {
                let mut response = text_error(StatusCode::RANGE_NOT_SATISFIABLE, "invalid range");
                set(response.headers_mut(), header::CONTENT_RANGE, &format!("bytes */{len}"));
                return response;
            }
            Wanted::Whole if len == 0 => (StatusCode::OK, None),
            Wanted::Whole => (StatusCode::OK, Some(0..=len - 1)),
            Wanted::Part(range) => (StatusCode::PARTIAL_CONTENT, Some(range)),
        };
        let length = range.as_ref().map_or(0, |range| range.end() - range.start() + 1);
        let body = match &range {
            Some(range) if method != Method::HEAD => body(range.clone()),
            _ => Body::empty(),
        };
        let mut response = Response::new(body);
        *response.status_mut() = status;
        let headers = response.headers_mut();
        set(headers, header::CONTENT_LENGTH, &length.to_string());
        if let (StatusCode::PARTIAL_CONTENT, Some(range)) = (status, &range) {
            set(
                headers,
                header::CONTENT_RANGE,
                &format!("bytes {}-{}/{len}", range.start(), range.end()),
            );
        }
        response
    };
    let headers = response.headers_mut();
    set(headers, header::CONTENT_TYPE, content_type);
    set(headers, header::CACHE_CONTROL, "no-cache");
    set(headers, header::ETAG, etag);
    set(headers, header::ACCEPT_RANGES, "bytes");
    response
}

/// The tag of a file on disk: its size and its modification time to the nanosecond.
pub(crate) fn file_etag(prefix: char, info: &fs::Metadata) -> String {
    let modified = info.modified().map(unix_nanos).unwrap_or_default();
    format!("\"{prefix}-{}-{modified}\"", info.len())
}

/// Sets a file's or a directory's modification time.
pub(crate) fn set_modified(path: &Path, time: SystemTime) -> io::Result<()> {
    fs::OpenOptions::new().read(true).open(path)?.set_modified(time)
}

pub(crate) fn set(headers: &mut HeaderMap, name: header::HeaderName, value: &str) {
    if let Ok(value) = HeaderValue::from_str(value) {
        headers.insert(name, value);
    }
}
