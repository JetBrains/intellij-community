//! Zips, read in place. A zip's central directory is read once per version of the file (its size and
//! modification time) and kept with the file open, so serving an entry costs one read.
//!
//! A zip can hold zips, as TeamCity's `bazel.logs.zip` holds Bazel's `outputs.zip`. An inner zip outside every
//! bundle of its zip is extracted once into the nested directory and read there as a zip of its own. Only one level
//! is opened: a zip inside an inner zip is a file like any other.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs::{self, File};
use std::io::{self, BufRead, BufReader, Cursor, Read, Seek};
use std::ops::Range;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, OnceLock, PoisonError};
use std::time::SystemTime;

use avl_trace::bundle::{LOGS_FILE, MANIFEST_FILE, SNAP_DIR, SPANS_FILE, VIDEO_FILE, decode_manifest};
use sha2::{Digest, Sha256};
use zip::{CompressionMethod, ZipArchive};

use super::{
    Bundle, Location, Source, SourceKind, Status, Summary, apply_inference, apply_manifest, bundle_id, infer_from_logs, trailing_segments,
};

/// Every zip the last scan saw, by its real path, and every inner zip of them, by the path of its extraction. A
/// version of a zip lives as long as a scan or a caller holds it: a changed zip is read again, and the old version's
/// file closes when its last holder lets it go.
#[derive(Default)]
pub struct ZipCache {
    archives: Mutex<HashMap<PathBuf, Arc<ZipIndex>>>,
    nested: NestedDir,
}

impl ZipCache {
    /// A cache that extracts inner zips into `nested_dir`, or into a private temporary directory when it is `None`.
    pub fn new(nested_dir: Option<PathBuf>) -> Self {
        Self {
            archives: Mutex::default(),
            nested: NestedDir {
                given: nested_dir,
                ..NestedDir::default()
            },
        }
    }

    /// The current version of a zip, read when it is new or changed. The inner zips of a new version are extracted
    /// and read with it, and this cache answers each of them by the path of its extraction. The read runs outside the
    /// lock, so an extraction does not hold up a request for another zip.
    pub fn get(&self, path: &Path) -> io::Result<Arc<ZipIndex>> {
        let info = fs::metadata(path)?;
        let modified = info.modified()?;
        if let Some(index) = self.archives.lock().unwrap_or_else(PoisonError::into_inner).get(path)
            && index.len == info.len()
            && index.modified == modified
        {
            return Ok(Arc::clone(index));
        }
        // An extraction is a zip of its own: it is never read as an outer zip, so no second level opens.
        let nested = (!self.nested.holds(path)).then_some(&self.nested);
        let index = Arc::new(ZipIndex::open(path, info.len(), modified, nested));
        let mut archives = self.archives.lock().unwrap_or_else(PoisonError::into_inner);
        for inner in &index.inner {
            archives.insert(inner.index.path.clone(), Arc::clone(&inner.index));
        }
        archives.insert(path.to_path_buf(), Arc::clone(&index));
        Ok(index)
    }

    /// Forgets every zip a scan did not see.
    pub(super) fn retain(&self, seen: &HashSet<PathBuf>) {
        self.archives
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .retain(|path, _| seen.contains(path));
    }

    /// Forgets every zip.
    pub fn clear(&self) {
        self.archives.lock().unwrap_or_else(PoisonError::into_inner).clear();
    }
}

/// One entry of a zip that holds a bundle.
#[derive(Clone, PartialEq, Eq, Debug)]
pub struct EntryMeta {
    /// Where the entry's bytes start in the zip: right after its local header.
    pub data_start: u64,
    pub compressed_size: u64,
    pub size: u64,
    /// Whether the bytes are stored as they are, so a range of the entry is the same range of the zip.
    pub stored: bool,
    pub crc32: u32,
}

/// One version of one zip: its bundles, its inner zips that hold any, and, for a zip that holds a bundle itself, its
/// entries and its open file. A zip without a bundle keeps only the fact, so the testlogs' every other `outputs.zip`
/// holds no descriptor.
pub struct ZipIndex {
    pub path: PathBuf,
    pub len: u64,
    pub modified: SystemTime,
    entries: BTreeMap<String, EntryMeta>,
    bundles: Vec<ZipBundle>,
    inner: Vec<InnerZip>,
    /// Why the zip could not be read: one Bazel is still writing, for instance. Remembered until the file changes.
    error: Option<String>,
    /// Why an inner zip could not be read, as `<entry>: <error>`.
    inner_errors: Vec<String>,
    open: Option<OpenZip>,
}

/// A zip inside a zip, extracted: its entry in the outer zip, and the index of its extraction.
pub struct InnerZip {
    pub entry: String,
    pub index: Arc<ZipIndex>,
}

struct OpenZip {
    archive: Mutex<ZipArchive<File>>,
    /// A second handle for positional reads of stored entries, so they neither wait for nor move the archive's.
    file: File,
}

impl ZipIndex {
    /// Reads one version of a zip. With `nested`, it also extracts and reads the inner zips.
    fn open(path: &Path, len: u64, modified: SystemTime, nested: Option<&NestedDir>) -> Self {
        let mut index = Self {
            path: path.to_path_buf(),
            len,
            modified,
            entries: BTreeMap::new(),
            bundles: Vec::new(),
            inner: Vec::new(),
            error: None,
            inner_errors: Vec::new(),
            open: None,
        };
        let opened = File::open(path).and_then(|file| ZipArchive::new(file).map_err(io::Error::from));
        let mut archive = match opened {
            Ok(archive) => archive,
            Err(error) => {
                index.error = Some(error.to_string());
                return index;
            }
        };
        index.bundles = find_bundles(&mut archive);
        if let Some(nested) = nested {
            for entry in inner_zip_names(&archive, &index.bundles) {
                match open_inner(&mut archive, path, &entry, nested) {
                    Ok(Some(inner)) => index.inner.push(InnerZip {
                        entry,
                        index: Arc::new(inner),
                    }),
                    Ok(None) => {}
                    Err(error) => index.inner_errors.push(format!("{entry}: {error}")),
                }
            }
        }
        if index.bundles.is_empty() {
            return index;
        }
        match (entry_metas(&mut archive), File::open(path)) {
            (Ok(entries), Ok(file)) => {
                index.entries = entries;
                index.open = Some(OpenZip {
                    archive: Mutex::new(archive),
                    file,
                });
            }
            (Err(error), _) | (_, Err(error)) => {
                index.bundles.clear();
                index.error = Some(error.to_string());
            }
        }
        index
    }

    /// Why the zip could not be read, when it could not.
    pub fn error(&self) -> Option<&str> {
        self.error.as_deref()
    }

    /// Why an inner zip could not be read, as `<entry>: <error>` for each.
    pub fn inner_errors(&self) -> &[String] {
        &self.inner_errors
    }

    /// The inner zips that hold a bundle.
    pub fn inner(&self) -> &[InnerZip] {
        &self.inner
    }

    /// The bundles in the zip, found at `container`, and those of its inner zips, found at `<container>!/<entry>`.
    pub fn bundles(&self, container: &str) -> Vec<Bundle> {
        let own = self.bundles.iter().map(|item| item.at(container, &self.path));
        let inner = self.inner.iter().flat_map(|inner| {
            let container = nested_container(container, &inner.entry);
            inner.index.bundles.iter().map(move |item| item.at(&container, &inner.index.path))
        });
        own.chain(inner).collect()
    }

    /// Every entry by name, for a zip that holds a bundle; empty for any other.
    pub const fn entries(&self) -> &BTreeMap<String, EntryMeta> {
        &self.entries
    }

    /// An entry's content, inflated and checked against its checksum, refused when it is larger than limit.
    pub fn read_entry(&self, name: &str, limit: u64) -> io::Result<Vec<u8>> {
        let open = self
            .open
            .as_ref()
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "the zip holds no bundle"))?;
        let mut archive = open.archive.lock().unwrap_or_else(PoisonError::into_inner);
        read_entry(&mut archive, name, limit)
    }

    /// A range of a stored entry's bytes, read in place.
    pub fn read_stored(&self, entry: &EntryMeta, range: Range<u64>) -> io::Result<Vec<u8>> {
        let open = self
            .open
            .as_ref()
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "the zip holds no bundle"))?;
        if !entry.stored || range.start > range.end || range.end > entry.size {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "not a range of a stored entry"));
        }
        let length = usize::try_from(range.end - range.start).map_err(|error| io::Error::new(io::ErrorKind::OutOfMemory, error))?;
        let mut buffer = vec![0; length];
        read_exact_at(&open.file, &mut buffer, entry.data_start + range.start)?;
        Ok(buffer)
    }
}

#[cfg(unix)]
fn read_exact_at(file: &File, buffer: &mut [u8], offset: u64) -> io::Result<()> {
    std::os::unix::fs::FileExt::read_exact_at(file, buffer, offset)
}

#[cfg(windows)]
fn read_exact_at(file: &File, mut buffer: &mut [u8], mut offset: u64) -> io::Result<()> {
    use std::os::windows::fs::FileExt;
    while !buffer.is_empty() {
        match file.seek_read(buffer, offset)? {
            0 => return Err(io::ErrorKind::UnexpectedEof.into()),
            read => {
                buffer = &mut buffer[read..];
                offset += read as u64;
            }
        }
    }
    Ok(())
}

fn entry_metas<R: Read + Seek>(archive: &mut ZipArchive<R>) -> io::Result<BTreeMap<String, EntryMeta>> {
    let mut entries = BTreeMap::new();
    for index in 0..archive.len() {
        let entry = archive.by_index_raw(index)?;
        let meta = EntryMeta {
            data_start: entry.data_start().unwrap_or_default(),
            compressed_size: entry.compressed_size(),
            size: entry.size(),
            stored: entry.compression() == CompressionMethod::Stored,
            crc32: entry.crc32(),
        };
        entries.insert(entry.name().to_owned(), meta);
    }
    Ok(entries)
}

/// The bundles in a zip already open, such as one read into memory, and those of its inner zips, which are read into
/// memory too. `container` names the zip in each bundle's source and id; an inner zip is `<container>!/<entry>`.
pub fn read_zip<R: Read + Seek>(archive: &mut ZipArchive<R>, container: &str) -> Vec<Bundle> {
    read_zip_reporting(archive, container).0
}

/// [read_zip], and why each inner zip that could not be read could not, as `<entry>: <error>`.
pub fn read_zip_reporting<R: Read + Seek>(archive: &mut ZipArchive<R>, container: &str) -> (Vec<Bundle>, Vec<String>) {
    let own = find_bundles(archive);
    let mut bundles: Vec<Bundle> = own.iter().map(|item| item.at(container, Path::new(container))).collect();
    let mut errors = Vec::new();
    for entry in inner_zip_names(archive, &own) {
        let read =
            read_entry(archive, &entry, MAX_INNER_BYTES).and_then(|content| ZipArchive::new(Cursor::new(content)).map_err(io::Error::from));
        let mut inner = match read {
            Ok(inner) => inner,
            Err(error) => {
                errors.push(format!("{entry}: {error}"));
                continue;
            }
        };
        let container = nested_container(container, &entry);
        bundles.extend(
            find_bundles(&mut inner)
                .iter()
                .map(|item| item.at(&container, Path::new(&container))),
        );
    }
    (bundles, errors)
}

/// Bounds an inner zip: one that [read_zip] reads into memory, and one that a cache extracts.
const MAX_INNER_BYTES: u64 = 1 << 30;

/// How a bundle of an inner zip names its container: the outer container, `!/` and the inner zip's entry, as Java and
/// TeamCity spell a path inside an archive.
fn nested_container(container: &str, entry: &str) -> String {
    format!("{container}!/{entry}")
}

/// The entries of a zip that may be zips of bundles: a clean name that ends in `.zip`, outside every bundle of the
/// zip. A zip inside a bundle is a file of that bundle.
fn inner_zip_names<R: Read + Seek>(archive: &ZipArchive<R>, bundles: &[ZipBundle]) -> Vec<String> {
    archive
        .file_names()
        .filter(|name| {
            name.len() > ".zip".len()
                && name
                    .get(name.len() - ".zip".len()..)
                    .is_some_and(|tail| tail.eq_ignore_ascii_case(".zip"))
        })
        .filter(|name| clean_bundle_path(name).is_some())
        .filter(|name| !bundles.iter().any(|bundle| name.starts_with(&bundle.prefix)))
        .map(str::to_owned)
        .collect()
}

/// Where inner zips are extracted: the directory the caller gave, or a private temporary one, made at the first
/// need and removed with the cache.
#[derive(Default)]
struct NestedDir {
    given: Option<PathBuf>,
    private: OnceLock<Result<tempfile::TempDir, String>>,
    /// Serializes the extractions, so a scan and a request never write or remove one extraction at once.
    extracting: Mutex<()>,
}

impl NestedDir {
    /// Whether a path is inside this directory, which is where the extractions are.
    fn holds(&self, path: &Path) -> bool {
        let private = self.private.get().and_then(|made| made.as_ref().ok()).map(tempfile::TempDir::path);
        self.given.as_deref().or(private).is_some_and(|dir| path.starts_with(dir))
    }

    fn dir(&self) -> io::Result<PathBuf> {
        if let Some(given) = &self.given {
            return Ok(given.clone());
        }
        match self.private.get_or_init(|| {
            tempfile::Builder::new()
                .prefix("air-trace-nested-")
                .tempdir()
                .map_err(|error| error.to_string())
        }) {
            Ok(dir) => Ok(dir.path().to_path_buf()),
            Err(error) => Err(io::Error::other(format!("no directory for inner zips: {error}"))),
        }
    }
}

/// Extracts one inner zip, unless an earlier extraction of the same entry is there, and reads it. Answers `None` for
/// an inner zip without a bundle. That and a failed extraction or read leave no extraction behind.
///
/// The extraction is `<nested>/n-<key>/inner.zip`. The key is the digest of the outer zip's path, the entry's name,
/// its CRC-32 and its size, so a rewritten outer zip that holds the same inner zip reuses the extraction. Each read
/// sets the directory's time, so a cleanup by age keeps what is read.
fn open_inner<R: Read + Seek>(archive: &mut ZipArchive<R>, outer: &Path, entry: &str, nested: &NestedDir) -> io::Result<Option<ZipIndex>> {
    let (crc32, size) = {
        let file = archive.by_name(entry).map_err(io::Error::from)?;
        (file.crc32(), file.size())
    };
    let key = Sha256::new()
        .chain_update(outer.to_string_lossy().as_bytes())
        .chain_update([0])
        .chain_update(entry)
        .chain_update([0])
        .chain_update(format!("{crc32:08x}-{size}"))
        .finalize();
    if size > MAX_INNER_BYTES {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("{size} bytes, more than {MAX_INNER_BYTES}"),
        ));
    }
    let dir = nested.dir()?.join(format!("n-{}", hex::encode(&key[..8])));
    // Held until the extraction is read, or removed: the reuse check of [extract] then runs under it too.
    let _serialized = nested.extracting.lock().unwrap_or_else(PoisonError::into_inner);
    let opened = extract(archive, entry, size, &dir).and_then(|target| {
        let info = fs::metadata(&target)?;
        let index = ZipIndex::open(&target, info.len(), info.modified()?, None);
        match &index.error {
            Some(error) => Err(io::Error::other(error.clone())),
            None => Ok(index),
        }
    });
    match opened {
        Ok(index) if !index.bundles.is_empty() => Ok(Some(index)),
        other => {
            let _ = fs::remove_dir_all(&dir);
            other.map(|_| None)
        }
    }
}

/// The extraction of an inner zip of `size` bytes in `dir`, written now unless one of that size is there. An entry
/// whose content is longer than its declared size is refused.
fn extract<R: Read + Seek>(archive: &mut ZipArchive<R>, entry: &str, size: u64, dir: &Path) -> io::Result<PathBuf> {
    let target = dir.join("inner.zip");
    if fs::metadata(&target).is_ok_and(|info| info.len() == size) {
        let _ = File::open(dir).and_then(|handle| handle.set_modified(SystemTime::now()));
        return Ok(target);
    }
    fs::create_dir_all(dir)?;
    let mut temporary = tempfile::Builder::new().prefix(".inner-").tempfile_in(dir)?;
    // The zip reader checks the CRC-32 at the entry's end, so a damaged entry fails here rather than caching.
    let reader = archive.by_name(entry).map_err(io::Error::from)?;
    let copied = io::copy(&mut reader.take(size + 1), temporary.as_file_mut())?;
    if copied != size {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!(
                "the entry declares {size} bytes and holds {}",
                if copied > size { "more" } else { "fewer" }
            ),
        ));
    }
    let mut temporary = temporary.into_temp_path();
    fs::rename(&temporary, &target)?;
    temporary.disable_cleanup(true);
    Ok(target)
}

/// One bundle inside a zip: its directory there, and what its files said.
struct ZipBundle {
    prefix: String,
    summary: Summary,
}

impl ZipBundle {
    /// The bundle as the zip at container holds it.
    fn at(&self, container: &str, path: &Path) -> Bundle {
        let mut summary = self.summary.clone();
        summary.id = bundle_id(SourceKind::Zip, container, &self.prefix);
        summary.source = Source {
            kind: SourceKind::Zip,
            path: container.to_owned(),
            entry: self.prefix.clone(),
        };
        Bundle {
            summary,
            location: Location::Zip {
                path: path.to_path_buf(),
                prefix: self.prefix.clone(),
            },
        }
    }
}

/// Every bundle in a zip. A bundle is found by its `spans.jsonl` wherever it sits, which is how one reader serves
/// the controller's iteration zip, Bazel's `outputs.zip` and a zipped bundle alike.
fn find_bundles<R: Read + Seek>(archive: &mut ZipArchive<R>) -> Vec<ZipBundle> {
    let names: Vec<String> = archive.file_names().map(str::to_owned).collect();
    let mut prefixes: Vec<String> = names
        .iter()
        .filter_map(|name| name.strip_suffix(SPANS_FILE))
        .filter(|prefix| prefix.is_empty() || prefix.ends_with('/'))
        .filter(|prefix| clean_prefix(prefix))
        .map(str::to_owned)
        .collect();
    prefixes.sort();
    prefixes
        .into_iter()
        .map(|prefix| {
            let summary = read_zip_summary(archive, &names, &prefix);
            ZipBundle { prefix, summary }
        })
        .collect()
}

/// Whether a bundle's directory inside a zip is one a request could name.
fn clean_prefix(prefix: &str) -> bool {
    prefix.is_empty() || prefix.strip_suffix('/').and_then(clean_bundle_path).is_some()
}

/// A path inside a bundle when it is one: relative, with `/` only, and without an empty, `.` or `..` segment.
/// Anything else never reaches the file system.
pub fn clean_bundle_path(file: &str) -> Option<&str> {
    let clean = !file.is_empty()
        && !file.contains(['\\', '\0'])
        && !file.starts_with('/')
        && file.split('/').all(|segment| !matches!(segment, "" | "." | ".."));
    clean.then_some(file)
}

/// Bounds a manifest read out of a zip; a real one is half a kilobyte.
const MAX_MANIFEST_BYTES: u64 = 1 << 20;

/// Reads one zip bundle's manifest, or infers what it can without one. A zip is finished by definition, so a bundle
/// in one without a manifest is truncated rather than running.
fn read_zip_summary<R: Read + Seek>(archive: &mut ZipArchive<R>, names: &[String], prefix: &str) -> Summary {
    let mut summary = Summary::default();
    let snap_prefix = format!("{prefix}{SNAP_DIR}/");
    summary.thumbnail = names
        .iter()
        .filter_map(|name| name.strip_prefix(&snap_prefix))
        .filter(|rest| !rest.contains('/') && rest.ends_with(".webp"))
        .max()
        .map(|rest| format!("{SNAP_DIR}/{rest}"));
    let video_present = archive
        .by_name(&format!("{prefix}{VIDEO_FILE}"))
        .is_ok_and(|video| video.size() > 0);
    let segments = trailing_segments(prefix);
    let manifest_name = format!("{prefix}{MANIFEST_FILE}");
    if archive.index_for_name(&manifest_name).is_some() {
        let manifest = read_entry(archive, &manifest_name, MAX_MANIFEST_BYTES)
            .map_err(|error| error.to_string())
            .and_then(|document| decode_manifest(&document).map_err(|error| error.to_string()));
        match manifest {
            Ok(manifest) => {
                apply_manifest(&mut summary, &manifest, video_present);
                return summary;
            }
            Err(error) => {
                summary.status = Status::Invalid;
                summary.error = Some(error);
            }
        }
    }
    let (first, last) = match archive.by_name(&format!("{prefix}{LOGS_FILE}")) {
        Ok(logs) => first_and_last_lines(logs),
        Err(_) => (None, None),
    };
    let status = summary.status;
    apply_inference(&mut summary, &infer_from_logs(first.as_deref(), last.as_deref()), &segments);
    summary.has_video = video_present;
    summary.status = if status == Status::Invalid {
        Status::Invalid
    } else {
        Status::Truncated
    };
    summary
}

fn read_entry<R: Read + Seek>(archive: &mut ZipArchive<R>, name: &str, limit: u64) -> io::Result<Vec<u8>> {
    let entry = archive.by_name(name)?;
    if entry.size() > limit {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("{name} is {} bytes, more than {limit}", entry.size()),
        ));
    }
    let mut content = Vec::new();
    entry.take(limit).read_to_end(&mut content)?;
    Ok(content)
}

/// Streams a JSON Lines entry for its first and last lines; a deflated entry cannot be read from its end.
fn first_and_last_lines(entry: impl Read) -> (Option<Vec<u8>>, Option<Vec<u8>>) {
    let mut first = None;
    let mut last = None;
    for line in BufReader::with_capacity(64 << 10, entry).split(b'\n') {
        let Ok(line) = line else {
            break;
        };
        if first.is_none() {
            first = Some(line.clone());
        }
        last = Some(line);
    }
    (first, last)
}
