//! Zips, read in place. A zip's central directory is read once per version of the file (its size and
//! modification time) and kept with the file open, so serving an entry costs one read.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs::File;
use std::io::{self, BufRead, BufReader, Read, Seek};
use std::ops::Range;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, PoisonError};
use std::time::SystemTime;

use avl_trace::bundle::{LOGS_FILE, MANIFEST_FILE, SNAP_DIR, SPANS_FILE, VIDEO_FILE, decode_manifest};
use zip::{CompressionMethod, ZipArchive};

use super::{
    Bundle, Location, Source, SourceKind, Status, Summary, apply_inference, apply_manifest, bundle_id, infer_from_logs, trailing_segments,
};

/// Every zip the last scan saw, by its real path. A version of a zip lives as long as a scan or a caller holds it:
/// a changed zip is read again, and the old version's file closes when its last holder lets it go.
#[derive(Default)]
pub struct ZipCache {
    archives: Mutex<HashMap<PathBuf, Arc<ZipIndex>>>,
}

impl ZipCache {
    /// The current version of a zip, read when it is new or changed.
    pub fn get(&self, path: &Path) -> io::Result<Arc<ZipIndex>> {
        let info = std::fs::metadata(path)?;
        let modified = info.modified()?;
        let mut archives = self.archives.lock().unwrap_or_else(PoisonError::into_inner);
        if let Some(index) = archives.get(path)
            && index.len == info.len()
            && index.modified == modified
        {
            return Ok(Arc::clone(index));
        }
        let index = Arc::new(ZipIndex::open(path, info.len(), modified));
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

/// One version of one zip: its bundles, and, for a zip that holds any, its entries and its open file. A zip
/// without a bundle keeps only the fact, so the testlogs' every other `outputs.zip` holds no descriptor.
pub struct ZipIndex {
    pub path: PathBuf,
    pub len: u64,
    pub modified: SystemTime,
    entries: BTreeMap<String, EntryMeta>,
    bundles: Vec<ZipBundle>,
    /// Why the zip could not be read: one Bazel is still writing, for instance. Remembered until the file changes.
    error: Option<String>,
    open: Option<OpenZip>,
}

struct OpenZip {
    archive: Mutex<ZipArchive<File>>,
    /// A second handle for positional reads of stored entries, so they neither wait for nor move the archive's.
    file: File,
}

impl ZipIndex {
    fn open(path: &Path, len: u64, modified: SystemTime) -> Self {
        let mut index = Self {
            path: path.to_path_buf(),
            len,
            modified,
            entries: BTreeMap::new(),
            bundles: Vec::new(),
            error: None,
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

    /// The bundles in the zip, found at `container`.
    pub fn bundles(&self, container: &str) -> Vec<Bundle> {
        self.bundles.iter().map(|item| item.at(container, &self.path)).collect()
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

/// The bundles in a zip already open, such as one read into memory. `container` names the zip in each bundle's
/// source and id.
pub fn read_zip<R: Read + Seek>(archive: &mut ZipArchive<R>, container: &str) -> Vec<Bundle> {
    find_bundles(archive)
        .iter()
        .map(|item| item.at(container, Path::new(container)))
        .collect()
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
