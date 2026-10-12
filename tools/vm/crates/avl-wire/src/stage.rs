//! The staging wire: what the host asks the guest to materialize, and what the guest answers.
//!
//! The host composes a manifest on its own disk, hands the guest a path to it, and reads one JSON reply.
//! Nothing else crosses - deliberately, because the thing being described is a thousand absolute paths, and
//! moving the list rather than a digest of it cost 16.1 s of a 95.7 s warm restart for a generation the guest
//! already had.

use std::fmt;

use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use serde_json::error::Category;

#[cfg(test)]
mod tests;

/// What a manifest declares and what a staged generation's own marker records. One number for both, because a
/// manifest change that the marker does not notice is a generation reused under rules it was not staged by.
pub const SCHEMA_VERSION: u32 = 1;

/// Whether `text` has the shape of every digest on this wire: sha256, lowercase hex, as text.
pub fn is_sha256_hex(text: &str) -> bool {
    text.len() == 64 && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
}

// --- the daemon's runtime ---------------------------------------------------------------------------------

/// A flat jar list plus an extracted JBR.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeManifest {
    pub schema_version: u32,
    /// The identity of the generation, as text. Never a number.
    #[serde(default)]
    pub runtime_digest: String,
    /// Absolute host paths, in classpath order. Order is contract data: the staged names carry the index.
    ///
    /// Required: an absent list and an explicitly empty one are different requests, and only the second is a
    /// legal "stage nothing". Reading the absent case as empty would stage an empty classpath and report
    /// success.
    pub stable_sources: Vec<String>,
    #[serde(default)]
    pub jbr_archive: String,
    #[serde(default)]
    pub java_home_suffix: String,
}

/// Where the generation ended up, and whether anything had to be done to get it there.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeResult {
    pub root: String,
    pub java_binary: String,
    pub classpath: Vec<String>,
    pub classpath_file: String,
    pub reused: bool,
}

/// The answer to the cheap question: is the generation the caller remembers still whole?
///
/// Deliberately without the classpath: the caller sends a digest of the list it remembers and gets back the
/// three paths it cannot derive, so the list crosses in neither direction.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeReuse {
    pub complete: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub root: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub java_binary: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub classpath_file: Option<String>,
}

// --- preparing the guest for one daemon -------------------------------------------------------------------

/// Everything the guest has to do between a staged generation and a supervisor start: the directories the run
/// writes into, the previous daemon's state file, and the JVM `@argfile`.
///
/// One document rather than three calls: each was a `tart exec`, and on a warm restart two of them were 5.4 s
/// and 10.6 s of the phase table. `stable_count` and `runtime_digest` make the guest's own `classpath.txt`
/// usable as input: the guest refuses a generation that does not hold exactly this many entries, so one
/// collected or half-written between the stage and the launch is a refusal rather than a daemon started with
/// the wrong jars.
///
/// `directories` and `remove_files` are required: an empty list is a request to create nothing, while a
/// request that never mentioned the field is from a controller this guest does not understand.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchPrep {
    pub schema_version: u32,
    /// The generation whose `classpath.txt` the `-cp` token is joined from.
    pub runtime_digest: String,
    pub stable_count: u32,
    /// Absolute guest paths to create, and absolute guest paths to delete if they exist.
    pub directories: Vec<String>,
    pub remove_files: Vec<String>,
    pub arg_file: ArgFileRequest,
}

/// The half of the `@argfile` the controller decides, plus the digest of the whole.
///
/// Every flag, every `-D` property, the main class and the destination are the controller's: `prefix` is those
/// tokens in order, and the guest appends `-cp <joined>` and `main_class` - it composes, it does not choose.
/// `sha256` is over the finished bytes ([`arg_file_text`] of the whole token list), computed by the controller
/// from the classpath the stage reported to it, so the only file a daemon can launch from is one the
/// controller already knows byte for byte.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ArgFileRequest {
    pub destination: String,
    pub prefix: Vec<String>,
    pub main_class: String,
    pub sha256: String,
}

/// What the guest published, in terms the controller can hold against what it asked for.
///
/// `bytes` is redundant with `sha256` and reported anyway: when the digests disagree, the length separates "a
/// truncated write" from "a classpath this controller did not expect", for a reader with no access to the
/// guest's disk.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchPrepResult {
    pub arg_file: String,
    pub sha256: String,
    pub bytes: u64,
    pub entries: u32,
}

// --- garbage collection ----------------------------------------------------------------------------------

/// What a collection removed. Always an array on the wire: an empty list says "nothing to remove", and a null
/// would say "no answer".
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct GcResult {
    pub removed: Vec<String>,
}

/// A request the guest cannot act on. The refusal texts are the guest's CLI output and a host test reads
/// them, so they are contract rather than diagnostics.
#[derive(Debug)]
pub enum StageError {
    InvalidRuntimeManifest,
    InvalidLaunchPrep,
    /// Not JSON at all.
    Json(serde_json::Error),
}

impl fmt::Display for StageError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidRuntimeManifest => f.write_str("invalid runtime stage manifest"),
            Self::InvalidLaunchPrep => f.write_str("invalid launch preparation request"),
            Self::Json(error) => error.fmt(f),
        }
    }
}

impl std::error::Error for StageError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::Json(error) => Some(error),
            _ => None,
        }
    }
}

/// Decodes a request: a document that is not JSON is a JSON error, and a JSON document of the wrong shape
/// (a required list absent or `null`, a field of the wrong kind) is the request's own refusal.
fn decode<T: DeserializeOwned>(raw: &[u8], invalid: StageError) -> Result<T, StageError> {
    serde_json::from_slice(raw).map_err(|error| match error.classify() {
        Category::Data => invalid,
        Category::Io | Category::Syntax | Category::Eof => StageError::Json(error),
    })
}

/// Reads a runtime manifest and refuses one the guest cannot act on.
///
/// Unknown fields are accepted, as everywhere on this controller's wires: adding one is how a manifest grows
/// compatibly, and the host adds them ahead of the guest that reads them.
pub fn decode_runtime_manifest(raw: &[u8]) -> Result<RuntimeManifest, StageError> {
    let manifest: RuntimeManifest = decode(raw, StageError::InvalidRuntimeManifest)?;
    if manifest.schema_version != SCHEMA_VERSION || manifest.jbr_archive.is_empty() {
        return Err(StageError::InvalidRuntimeManifest);
    }
    Ok(manifest)
}

/// Reads a launch-prep request and refuses one the guest cannot act on.
pub fn decode_launch_prep(raw: &[u8]) -> Result<LaunchPrep, StageError> {
    let request: LaunchPrep = decode(raw, StageError::InvalidLaunchPrep)?;
    let arg_file = &request.arg_file;
    if request.schema_version != SCHEMA_VERSION
        || !is_sha256_hex(&request.runtime_digest)
        || request.stable_count == 0
        || arg_file.destination.is_empty()
        || arg_file.main_class.is_empty()
        || !is_sha256_hex(&arg_file.sha256)
    {
        return Err(StageError::InvalidLaunchPrep);
    }
    Ok(request)
}

// --- a generation on the guest's disk ---------------------------------------------------------------------
//
// A staged generation lives at `<runtimeRoot>/generations/<runtimeDigest>`, and its classpath file lists the staged
// jars one per line. The guest writes both. The controller names the generation it asked for and hashes the
// classpath file it remembers, so a second spelling of either is a reuse probe that never matches.

/// The directory under a runtime root that holds one directory per staged generation.
pub const GENERATIONS_DIR: &str = "generations";

/// The file inside a generation that lists its staged classpath.
pub const CLASSPATH_FILE: &str = "classpath.txt";

/// Where the generation of one runtime digest is staged, as a guest path under `runtime_root`.
pub fn generation_dir(runtime_root: &str, runtime_digest: &str) -> String {
    format!("{}/{GENERATIONS_DIR}/{runtime_digest}", runtime_root.trim_end_matches('/'))
}

/// The exact bytes of a classpath file: the entries joined by newline, and one trailing newline.
///
/// The stage-check digest is taken over exactly these bytes, so the guest's file and the controller's memory of it
/// must be written by this one function.
pub fn classpath_file_text<S: AsRef<str>>(classpath: &[S]) -> String {
    let mut text = classpath.iter().map(AsRef::as_ref).collect::<Vec<_>>().join("\n");
    text.push('\n');
    text
}

/// The classpath a classpath file lists, in classpath order.
///
/// Blank lines are dropped, which makes the trailing newline harmless. The reuse probe and `launch-prep` both count
/// the entries against what the controller remembers, so they read the file through this one function.
pub fn parse_classpath_file(text: &str) -> Vec<String> {
    text.split('\n').filter(|line| !line.is_empty()).map(str::to_owned).collect()
}

// --- the `@argfile` ---------------------------------------------------------------------------------------
//
// The byte format of a JVM `@argfile`, on the wire because both halves write one: the guest joins its own
// staged list into the `-cp` token and renders the file, while the controller renders the same tokens locally
// to know what digest to expect. A disagreement about how a backslash is escaped is a refused start on every
// launch, so the rule exists once, here.

/// Spells one token the way the JVM's `@argfile` reader expects.
///
/// Everything is quoted, including tokens that need no quoting, because the alternative is a rule deciding
/// which ones do - and a staged path containing a space is ordinary on macOS. The backslash is doubled before
/// the quote is escaped; the other order escapes the escapes.
pub fn quote_for_arg_file(token: &str) -> String {
    let escaped = token.replace('\\', "\\\\").replace('"', "\\\"");
    format!("\"{escaped}\"")
}

/// The exact bytes a JVM reads from `@<path>`: one quoted token per line, and no trailing newline.
///
/// The absence of that newline is contract rather than taste: the bytes are what [`ArgFileRequest::sha256`]
/// is taken over, so a newline added on one side is a launch the other side refuses.
pub fn arg_file_text<S: AsRef<str>>(tokens: &[S]) -> String {
    tokens
        .iter()
        .map(|token| quote_for_arg_file(token.as_ref()))
        .collect::<Vec<_>>()
        .join("\n")
}
