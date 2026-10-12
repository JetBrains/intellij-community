//! `launch-prep`: everything between a staged generation and a supervisor start, in one call.
//!
//! Three round-trips used to do this: a `mkdir`, an `rm` and a `tee` of an @-file the controller had rendered,
//! 5.4 s and 10.6 s of a warm restart between them. The `tee` was the expensive one, and its cost was one token: a
//! `-cp` holding ~1000 absolute paths, pushed to a guest that had staged every one of those files itself and
//! written their paths to `classpath.txt` minutes earlier.
//!
//! So the classpath does not cross. The flags, the properties, the main class and the sha256 of the finished
//! bytes do; this side joins its own recorded list into the `-cp` token and answers what it published. The
//! controller refuses a reply whose digest is not the one it computed, so composing here is not deciding here.

use std::fs;
use std::io::{self, Read, Write};
use std::os::unix::fs::{DirBuilderExt, PermissionsExt};
use std::path::Path;

use avl_wire::stage::{self, LaunchPrep, LaunchPrepResult, arg_file_text};
use sha2::{Digest, Sha256};

use super::runtime::{classpath_file, classpath_is_staged_under, read_classpath};
use super::{generation_root, require_runtime_root};
use crate::reply::{AgentRefusal, AgentRefusalExt, refuse};
use avl_wire::verb::AgentVerb;

/// Reads the request from standard input.
///
/// Stdin rather than a path or an argv, and both halves are deliberate. Not a path, because writing the document
/// would put back a round-trip. Not an argv, because the prefix carries every `-D` property of the launch - a
/// bridge token among them - and an argv is readable in the guest's process table by every account on the
/// machine, while stdin is readable by nothing but this process.
pub(crate) fn read_request(stdin: &mut dyn Read) -> Result<LaunchPrep, AgentRefusal> {
    let mut raw = Vec::new();
    stdin.read_to_end(&mut raw).map_err(|error| {
        AgentRefusal::for_verb(
            AgentVerb::LaunchPrep,
            format!("cannot read the launch preparation request: {error}"),
        )
    })?;
    stage::decode_launch_prep(&raw).map_err(|error| AgentRefusal::for_verb(AgentVerb::LaunchPrep, error.to_string()))
}

pub(crate) fn prepare(runtime_root: &str, request: &LaunchPrep) -> Result<LaunchPrepResult, AgentRefusal> {
    let runtime_root = require_runtime_root(AgentVerb::LaunchPrep, runtime_root)?;
    for directory in &request.directories {
        if !Path::new(directory).is_absolute() {
            refuse!(AgentVerb::LaunchPrep, "launch-prep directory is not absolute: {directory}");
        }
        fs::DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(directory)
            .map_err(|error| AgentRefusal::for_verb(AgentVerb::LaunchPrep, format!("cannot create {directory}: {error}")))?;
    }
    // Absence tolerated: these name the previous daemon's leftovers, and on a fresh worker there never were any.
    for path in &request.remove_files {
        if !Path::new(path).is_absolute() {
            refuse!(AgentVerb::LaunchPrep, "launch-prep path is not absolute: {path}");
        }
        match fs::remove_file(path) {
            Ok(()) => {}
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => refuse!(AgentVerb::LaunchPrep, "cannot remove {path}: {error}"),
        }
    }

    let digest = &request.runtime_digest;
    let generation = generation_root(AgentVerb::LaunchPrep, &runtime_root, digest)?;
    let classpath = read_classpath(&generation).map_err(|error| {
        AgentRefusal::for_verb(
            AgentVerb::LaunchPrep,
            format!(
                "cannot read the staged classpath {}: {error}",
                classpath_file(&generation).display()
            ),
        )
    })?;
    // The count is what stands between "the generation this controller staged" and "whatever is in that
    // directory now": a collected and restaged or truncated generation is a refusal, not a short classpath.
    if classpath.len() != request.stable_count as usize {
        refuse!(
            AgentVerb::LaunchPrep,
            "staged classpath of {digest} has {} entries, expected {}",
            classpath.len(),
            request.stable_count
        );
    }
    if !classpath_is_staged_under(&generation, &classpath) {
        refuse!(
            AgentVerb::LaunchPrep,
            "staged classpath of {digest} names files outside its own generation"
        );
    }

    let arg_file = &request.arg_file;
    let mut tokens = arg_file.prefix.clone();
    tokens.push("-cp".to_owned());
    tokens.push(classpath.join(":"));
    tokens.push(arg_file.main_class.clone());
    let content = arg_file_text(&tokens);
    publish_privately(Path::new(&arg_file.destination), content.as_bytes())
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::LaunchPrep, format!("cannot publish {}: {error}", arg_file.destination)))?;
    Ok(LaunchPrepResult {
        arg_file: arg_file.destination.clone(),
        sha256: hex::encode(Sha256::digest(content.as_bytes())),
        bytes: content.len() as u64,
        entries: u32::try_from(classpath.len()).unwrap_or(u32::MAX),
    })
}

/// Writes `content` where a reader sees the whole file or no file at all, readable only by this account.
///
/// Atomic, because a JVM the controller starts immediately afterwards reads it, and an @-file read half-written
/// is a JVM that starts with an incomplete classpath rather than one that fails to start. Fsynced before the
/// rename, because both workers this runs on hard-reset. 0600 is contract: the prefix carries every `-D` property
/// of the launch, including a bridge token. A failed publish leaves no temporary behind.
pub(crate) fn publish_privately(path: &Path, content: &[u8]) -> io::Result<()> {
    let parent = path.parent().unwrap_or(Path::new("."));
    let name = path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default();
    let mut temporary = tempfile::Builder::new()
        .prefix(&format!(".{name}."))
        .permissions(fs::Permissions::from_mode(0o600))
        .tempfile_in(parent)?;
    temporary.write_all(content)?;
    temporary.as_file().sync_all()?;
    let mut temporary = temporary.into_temp_path();
    fs::rename(&temporary, path)?;
    temporary.disable_cleanup(true);
    Ok(())
}
