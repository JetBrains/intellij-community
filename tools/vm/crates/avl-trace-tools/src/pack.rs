//! `air-trace pack`: zips a trace root so it can leave the machine that recorded it.
//!
//! Media entries are STORED rather than deflated, so the server can answer a byte range straight out of the
//! archive: a stored entry's bytes sit at one offset in the zip, so a `Range` over a video is a `Range` over the
//! file. The guest agent's `trace-pack-ready` verb (`avl_wire::verb::AgentVerb::TracePackReady`) runs this same code
//! inside a worker VM, before the controller pulls the zip.
//!
//! The zip is deterministic: the same tree packs to the same bytes, whenever and wherever it is packed. Entries are
//! in byte order of their paths, and every entry carries one fixed time, one mode and one host system, because the
//! file times of a trace root say when a daemon iteration ran rather than anything about the evidence, and a zip
//! that differed by them could not be compared, deduplicated or cached by content.

use std::collections::BTreeSet;
use std::fs::{self, File};
use std::io::{self, BufWriter, Write};
use std::path::{Component, Path, PathBuf};

use avl_trace::bundle::{MANIFEST_FILE, PARTIAL_SUFFIX, SPANS_FILE};
use avl_trace::{Error, refuse};
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, DateTime, System, ZipWriter};

/// The entries kept uncompressed: media that is compressed already, which deflate would only make slower to
/// reach, and whose byte ranges the server serves in place. Everything else is deflated: the JSON, the JSON Lines
/// and the log slice shrink severalfold.
const STORED_EXTENSIONS: &[&str] = &["webp", "png", "jpg", "jpeg", "mp4", "webm", "zip", "gz"];

/// How one entry is written, by its extension.
fn method_of(name: &str) -> CompressionMethod {
    let extension = name
        .rsplit_once('/')
        .map_or(name, |(_, base)| base)
        .rsplit_once('.')
        .map(|(_, extension)| extension);
    match extension {
        Some(extension) if STORED_EXTENSIONS.iter().any(|stored| extension.eq_ignore_ascii_case(stored)) => CompressionMethod::Stored,
        _ => CompressionMethod::Deflated,
    }
}

/// The options of every entry: one time, the earliest the zip format can spell so no reader mistakes it for when
/// anything happened, one mode, and one host system, so the packing machine leaves no trace in the bytes. Deflate
/// runs at the default level, named rather than left implicit: a zip is deterministic only for one compressor, and
/// that is the one Cargo.lock pins at this level.
fn entry_options(method: CompressionMethod, size: u64) -> SimpleFileOptions {
    let options = SimpleFileOptions::default()
        .compression_method(method)
        .last_modified_time(DateTime::default())
        .unix_permissions(0o644)
        .system(System::Unix)
        .large_file(size >= u64::from(u32::MAX));
    match method {
        CompressionMethod::Deflated => options.compression_level(Some(6)),
        _ => options,
    }
}

/// Decides which bundles a pack keeps: given a bundle's directory relative to the source and whether it is
/// finished, whether to pack it.
pub type Select<'a> = &'a dyn Fn(&str, bool) -> bool;

/// Which bundles a pack keeps.
#[derive(Default)]
pub struct PackOptions<'a> {
    /// When set, keeps only the bundles it answers true for, and the files under them. It is given a bundle's
    /// directory relative to the source and whether the bundle is finished, which is whether it holds its
    /// [MANIFEST_FILE]. A file under no bundle is left out. When it keeps no bundle, the pack writes no zip, and the
    /// report has no destination.
    pub select: Option<Select<'a>>,
}

/// What a pack did. The guest verb answers it inside its envelope, and the CLI prints it with [write_report].
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct Report {
    pub source: PathBuf,
    /// The zip written, or `None` when the selection kept no bundle. It is `""` on the wire, which is how the host
    /// has always told "nothing new packed".
    #[serde(serialize_with = "empty_when_none", deserialize_with = "none_when_empty")]
    pub destination: Option<PathBuf>,
    /// How many files the zip holds.
    pub entries: usize,
    /// How many of the tree's directories hold a [SPANS_FILE].
    pub bundles: usize,
    /// Those directories, relative to the source, in byte order.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub packed: Vec<String>,
    pub bytes: u64,
    /// What the tree held and the zip does not: a symbolic link to a directory, a socket.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub skipped: Vec<Skipped>,
}

#[expect(clippy::ref_option, reason = "serde's serialize_with passes the field as &Option<PathBuf>")]
fn empty_when_none<S: Serializer>(path: &Option<PathBuf>, serializer: S) -> Result<S::Ok, S::Error> {
    path.as_deref().unwrap_or(Path::new("")).serialize(serializer)
}

fn none_when_empty<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Option<PathBuf>, D::Error> {
    let path = PathBuf::deserialize(deserializer)?;
    Ok((!path.as_os_str().is_empty()).then_some(path))
}

/// One path left out of the zip, and why.
#[derive(Serialize, Deserialize, Clone, PartialEq, Eq, Debug)]
pub struct Skipped {
    pub path: String,
    pub reason: String,
}

/// One file the zip will hold: its name in the zip, and where its bytes are read from.
#[derive(Clone, Debug)]
struct Entry {
    name: String,
    source: PathBuf,
}

/// Zips the tree under source into destination, atomically: the zip is written beside its destination under a
/// temporary name and renamed onto it, so a reader never opens half an archive and a failed pack leaves whatever
/// was there before.
///
/// Nothing cancels a pack: a signal kills the process, which may leave a `.<zip>.tmp-*` beside the destination.
pub fn pack(source: &Path, destination: &Path, options: &PackOptions<'_>) -> Result<Report, Error> {
    let source = absolute(source)?;
    let destination = absolute(destination)?;
    match fs::metadata(&source) {
        Ok(info) if info.is_dir() => {}
        Ok(_) => refuse!("the trace directory {} is not a directory", source.display()),
        Err(error) => refuse!("the trace directory {} cannot be read: {error}", source.display()),
    }
    // Refused rather than skipped: the zip would otherwise race its own temporary file into the walk, and a
    // destination inside the tree is always a typo for one beside it.
    if destination.starts_with(&source) {
        refuse!(
            "the zip {} would be written inside the tree it packs, {}",
            destination.display(),
            source.display()
        );
    }

    let Collected {
        mut entries,
        mut bundles,
        skipped,
    } = collect(&source)?;
    let mut report = Report {
        source,
        skipped,
        ..Report::default()
    };
    if let Some(select) = options.select {
        (entries, bundles) = select_bundles(entries, bundles, select);
        if bundles.is_empty() {
            return Ok(report);
        }
    }
    report.bundles = bundles.len();

    report.packed = bundles;

    let parent = destination.parent().unwrap_or(Path::new("/"));
    let base = destination.file_name().unwrap_or_default().to_string_lossy();
    let io_error = |error: io::Error| Error::new(format!("cannot write {}: {error}", destination.display()));
    fs::create_dir_all(parent).map_err(io_error)?;
    let mut temporary = tempfile::Builder::new()
        .prefix(&format!(".{base}.tmp-"))
        .tempfile_in(parent)
        .map_err(io_error)?;
    let mut writer = ZipWriter::new(BufWriter::with_capacity(1 << 20, temporary.as_file_mut()));
    for item in &entries {
        if write_entry(&mut writer, item).map_err(|error| Error::new(format!("{}: {error}", item.name)))? {
            report.entries += 1;
        } else {
            report.skipped.push(Skipped {
                path: item.name.clone(),
                reason: "removed while it was packed".to_owned(),
            });
        }
    }
    let buffered = writer.finish().map_err(|error| io_error(error.into()))?;
    buffered.into_inner().map_err(|error| io_error(error.into_error()))?;
    let file = temporary.as_file();
    file.sync_all().map_err(io_error)?;
    report.bytes = file.metadata().map_err(io_error)?.len();
    let mut temporary = temporary.into_temp_path();
    fs::rename(&temporary, &destination).map_err(io_error)?;
    temporary.disable_cleanup(true);
    report.destination = Some(destination);
    Ok(report)
}

/// The path made absolute and lexically clean, as the zip's report and the inside-the-tree check need it.
fn absolute(path: &Path) -> Result<PathBuf, Error> {
    let absolute = std::path::absolute(path).map_err(|error| Error::new(format!("cannot resolve {}: {error}", path.display())))?;
    let mut clean = PathBuf::new();
    for component in absolute.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                clean.pop();
            }
            other => clean.push(other),
        }
    }
    Ok(clean)
}

struct Collected {
    entries: Vec<Entry>,
    bundles: Vec<String>,
    skipped: Vec<Skipped>,
}

/// Lists every file under the tree in byte order of its zip name, and the bundles among the directories.
///
/// A symbolic link to a file is followed, because that is how Bazel lays out runfiles and sandbox inputs: the zip
/// holds the bytes, never the link. A link to a directory is skipped and reported, since following one can loop
/// and a trace root never needs to. So is a recorder's temporary file, which is there only when a recorder is still
/// finishing a bundle beside the pack, as it is after its lane was killed outright.
fn collect(source: &Path) -> Result<Collected, Error> {
    let mut collected = Collected {
        entries: Vec::new(),
        bundles: Vec::new(),
        skipped: Vec::new(),
    };
    for item in walkdir::WalkDir::new(source).follow_links(false) {
        let item = item.map_err(|error| Error::new(format!("cannot walk {}: {error}", source.display())))?;
        if item.file_type().is_dir() {
            continue;
        }
        let relative = item.path().strip_prefix(source).unwrap_or(item.path());
        let Some(name) = slash_name(relative) else {
            collected.skipped.push(Skipped {
                path: relative.to_string_lossy().into_owned(),
                reason: "a name that is not UTF-8".to_owned(),
            });
            continue;
        };
        let skip = |reason: String| Skipped {
            path: name.clone(),
            reason,
        };
        if name.ends_with(PARTIAL_SUFFIX) {
            collected
                .skipped
                .push(skip("a temporary file the recorder was still writing".to_owned()));
            continue;
        }
        if item.path_is_symlink() {
            match fs::metadata(item.path()) {
                Err(error) => {
                    collected
                        .skipped
                        .push(skip(format!("a symbolic link that does not resolve: {error}")));
                    continue;
                }
                Ok(target) if !target.is_file() => {
                    collected
                        .skipped
                        .push(skip("a symbolic link to something other than a file".to_owned()));
                    continue;
                }
                Ok(_) => {}
            }
        } else if !item.file_type().is_file() {
            collected.skipped.push(skip("not a regular file".to_owned()));
            continue;
        }
        if item.file_name() == SPANS_FILE {
            collected
                .bundles
                .push(name.rsplit_once('/').map_or(".", |(bundle, _)| bundle).to_owned());
        }
        collected.entries.push(Entry {
            name,
            source: item.into_path(),
        });
    }
    collected.entries.sort_by(|left, right| left.name.cmp(&right.name));
    collected.bundles.sort();
    Ok(collected)
}

/// A relative path as a zip name: its segments joined with `/`.
fn slash_name(relative: &Path) -> Option<String> {
    let segments: Option<Vec<&str>> = relative.components().map(|component| component.as_os_str().to_str()).collect();
    Some(segments?.join("/"))
}

/// Keeps the bundles that select keeps, and the entries under them.
fn select_bundles(entries: Vec<Entry>, bundles: Vec<String>, select: &dyn Fn(&str, bool) -> bool) -> (Vec<Entry>, Vec<String>) {
    let names: BTreeSet<&str> = entries.iter().map(|item| item.name.as_str()).collect();
    let manifest_of = |bundle: &str| match bundle {
        "." => MANIFEST_FILE.to_owned(),
        _ => format!("{bundle}/{MANIFEST_FILE}"),
    };
    let kept: Vec<String> = bundles
        .into_iter()
        .filter(|bundle| select(bundle, names.contains(manifest_of(bundle).as_str())))
        .collect();
    let under = |name: &str| {
        kept.iter()
            .any(|bundle| bundle == "." || name.strip_prefix(bundle.as_str()).is_some_and(|rest| rest.starts_with('/')))
    };
    let selected = entries.into_iter().filter(|item| under(&item.name)).collect();
    (selected, kept)
}

/// Adds one file, and answers false, writing nothing, for a file that is gone by the time it is opened: a recorder
/// still finishing a bundle renames its files into place, and a file the walk listed may have been renamed since.
/// Failing the whole pack over it would cost the controller every other bundle of the iteration.
///
/// The writer is seekable, so a stored entry gets its checksum and size patched into its local header rather than
/// a trailing data descriptor: its bytes are then exactly at the header's end, which is what a ranged read relies
/// on, and any reader can take them without the central directory. A file only appended to while it is read, like
/// a video still being encoded, packs what the read saw, under the checksum of those bytes.
fn write_entry<W: Write + io::Seek>(writer: &mut ZipWriter<W>, item: &Entry) -> io::Result<bool> {
    // Opened before the entry starts, so a file that is gone leaves no empty entry behind.
    let mut file = match File::open(&item.source) {
        Ok(file) => file,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(false),
        Err(error) => return Err(error),
    };
    let size = file.metadata()?.len();
    writer.start_file(item.name.as_str(), entry_options(method_of(&item.name), size))?;
    io::copy(&mut file, writer)?;
    Ok(true)
}

// --- the command line's output -------------------------------------------------------------------------------

/// Prints what a pack did the way `air-trace pack` does: every skipped path on stderr, and one summary line
/// on stdout.
pub fn write_report(report: &Report, stdout: &mut dyn Write, stderr: &mut dyn Write) -> io::Result<()> {
    for skipped in &report.skipped {
        writeln!(stderr, "air-trace pack: skipped {}: {}", skipped.path, skipped.reason)?;
    }
    match &report.destination {
        Some(destination) => writeln!(
            stdout,
            "packed {} files from {} bundles into {} ({} bytes)",
            report.entries,
            report.bundles,
            destination.display(),
            report.bytes
        ),
        None => writeln!(stdout, "packed nothing: no bundle of {} was selected", report.source.display()),
    }
}

#[cfg(test)]
mod tests;
