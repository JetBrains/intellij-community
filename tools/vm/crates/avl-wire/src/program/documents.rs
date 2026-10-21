//! The documents of the flow-profiles resource root, read from a directory or from a zip: the suite documents under
//! [`SUITE_DOCUMENT_DIRECTORY`], the lane documents under [`LANE_DOCUMENT_DIRECTORY`], and the flow vocabulary file
//! [`FLOW_VOCABULARY_FILE`] at the root.
//!
//! A runner on the host reads the root as a directory. The lane daemon in a guest reads the resource jar that the
//! controller pushes by digest. Both get the same names and the same bytes, because one reader serves both sources.

use std::collections::BTreeMap;
use std::fs;
use std::io::Read;
use std::path::Path;

use sha2::{Digest, Sha256};

#[cfg(test)]
mod tests;

/// The directory of the suite documents below the resource root, one `<suite>.json` per generated suite.
pub const SUITE_DOCUMENT_DIRECTORY: &str = "flow-profiles";

/// The directory of the lane documents below the resource root, one `<lane>.json` per lane.
pub const LANE_DOCUMENT_DIRECTORY: &str = "lanes";

/// The flow vocabulary file at the resource root. The generator writes the same bytes into the bridge module, so its
/// digest is the digest of the vocabulary that the bridge serves.
pub const FLOW_VOCABULARY_FILE: &str = "flow-vocabulary.json";

/// The suite documents of `source` by file name, such as `rename-session.json`, as their bytes.
///
/// `source` is the resource root: a directory that holds [`SUITE_DOCUMENT_DIRECTORY`], or a zip whose entries start
/// with it, such as the flow-profiles resource jar. Only the `.json` files directly in that directory count. A source
/// that holds no suite document is refused.
pub fn read_suite_documents(source: &Path) -> Result<BTreeMap<String, Vec<u8>>, String> {
    read_documents(source, SUITE_DOCUMENT_DIRECTORY)
}

/// The lane documents of `source` by file name, such as `ui.json`, as their bytes. The rules of
/// [`read_suite_documents`] apply, with [`LANE_DOCUMENT_DIRECTORY`] in place of the suite directory.
pub fn read_lane_documents(source: &Path) -> Result<BTreeMap<String, Vec<u8>>, String> {
    read_documents(source, LANE_DOCUMENT_DIRECTORY)
}

/// The digest of the flow vocabulary of `source`: the lowercase hex SHA-256 of the bytes of [`FLOW_VOCABULARY_FILE`] at
/// the resource root. `source` is a directory or a zip, as for [`read_suite_documents`]. A source without the file is
/// refused by its name.
pub fn read_vocabulary_digest(source: &Path) -> Result<String, String> {
    let content =
        read_root_file(source, FLOW_VOCABULARY_FILE)?.ok_or_else(|| format!("{} holds no {FLOW_VOCABULARY_FILE}", source.display()))?;
    Ok(hex::encode(Sha256::digest(content)))
}

fn read_documents(source: &Path, directory: &str) -> Result<BTreeMap<String, Vec<u8>>, String> {
    let documents = if is_directory(source)? {
        from_directory(source, directory)?
    } else {
        from_zip(source, directory)?
    };
    if documents.is_empty() {
        return Err(format!("{} holds no {directory}/*.json document", source.display()));
    }
    Ok(documents)
}

/// The bytes of the file `name` at the root of `source`, or `None` when the root holds no such file.
fn read_root_file(source: &Path, name: &str) -> Result<Option<Vec<u8>>, String> {
    if is_directory(source)? {
        let path = source.join(name);
        // Through the link: a runfiles tree holds each file as a link to its file.
        return match fs::metadata(&path) {
            Ok(metadata) if metadata.is_file() => fs::read(&path)
                .map(Some)
                .map_err(|error| format!("cannot read {}: {error}", path.display())),
            Ok(_) => Err(format!("{} is not a file", path.display())),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(error) => Err(format!("cannot read {}: {error}", path.display())),
        };
    }
    let shown = source.display();
    let mut archive = open_zip(source)?;
    let mut entry = match archive.by_name(name) {
        Ok(entry) => entry,
        Err(zip::result::ZipError::FileNotFound) => return Ok(None),
        Err(error) => return Err(format!("cannot read {name} of {shown}: {error}")),
    };
    let mut content = Vec::new();
    entry
        .read_to_end(&mut content)
        .map_err(|error| format!("cannot read {name} of {shown}: {error}"))?;
    Ok(Some(content))
}

fn is_directory(source: &Path) -> Result<bool, String> {
    fs::metadata(source)
        .map(|metadata| metadata.is_dir())
        .map_err(|error| format!("cannot read {}: {error}", source.display()))
}

fn open_zip(source: &Path) -> Result<zip::ZipArchive<fs::File>, String> {
    let shown = source.display();
    let file = fs::File::open(source).map_err(|error| format!("cannot open {shown}: {error}"))?;
    zip::ZipArchive::new(file).map_err(|error| format!("{shown} is neither a directory nor a zip: {error}"))
}

/// The name of a document: a `.json` file name with no further path.
fn is_document_name(name: &str) -> bool {
    name.len() > ".json".len() && name.ends_with(".json") && !name.contains(['/', '\\'])
}

fn from_directory(source: &Path, directory: &str) -> Result<BTreeMap<String, Vec<u8>>, String> {
    let root = source.join(directory);
    let entries = fs::read_dir(&root).map_err(|error| format!("cannot list {}: {error}", root.display()))?;
    let mut documents = BTreeMap::new();
    for entry in entries {
        let entry = entry.map_err(|error| format!("cannot list {}: {error}", root.display()))?;
        let path = entry.path();
        let Some(name) = entry.file_name().to_str().filter(|name| is_document_name(name)).map(str::to_owned) else {
            continue;
        };
        // Through the link: a runfiles tree holds each document as a link to its file.
        let is_file = fs::metadata(&path)
            .map_err(|error| format!("cannot read {}: {error}", path.display()))?
            .is_file();
        if !is_file {
            return Err(format!("{} is not a file", path.display()));
        }
        let content = fs::read(&path).map_err(|error| format!("cannot read {}: {error}", path.display()))?;
        documents.insert(name, content);
    }
    Ok(documents)
}

fn from_zip(source: &Path, directory: &str) -> Result<BTreeMap<String, Vec<u8>>, String> {
    let shown = source.display();
    let mut archive = open_zip(source)?;
    let prefix = format!("{directory}/");
    let mut documents = BTreeMap::new();
    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .map_err(|error| format!("cannot read entry {index} of {shown}: {error}"))?;
        let Some(name) = entry
            .name()
            .strip_prefix(&prefix)
            .filter(|name| is_document_name(name))
            .map(str::to_owned)
        else {
            continue;
        };
        let mut content = Vec::new();
        entry
            .read_to_end(&mut content)
            .map_err(|error| format!("cannot read {prefix}{name} of {shown}: {error}"))?;
        if documents.insert(name.clone(), content).is_some() {
            return Err(format!("{shown} holds {prefix}{name} twice"));
        }
    }
    Ok(documents)
}
