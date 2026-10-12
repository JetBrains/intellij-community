//! The daemon's runtime: `stage` (a flat jar list plus an extracted JBR) and `stage-check` (whether a staged
//! generation can be handed back untouched without sending its classpath).

use std::collections::BTreeMap;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use avl_wire::stage::{self, RuntimeManifest, RuntimeResult, RuntimeReuse, is_sha256_hex};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::donor::{RuntimeDonor, newest_donor};
use super::{
    StagingTree, TAR_BINARY, copy_file_hashed, create_dirs, generation_root, generations_dir, is_file, remove_tree, require_runtime_root,
    sha256_file, write_json_file, write_private,
};
use crate::cli::StageCheckArgs;
use crate::clock::now_stamp;
use crate::reply::{AgentRefusal, AgentRefusalExt, refuse};
use avl_wire::verb::AgentVerb;

/// The recorded shape of one staged runtime generation, `runtime.json`.
///
/// `entries` is the sha256 of every staged jar, keyed by the name it was staged under. It is what makes a hardlink
/// from this generation safe, so its *presence* is what qualifies the tree as a donor: an empty map is a
/// legitimate donor, an absent one is a generation staged before the record existed. The reuse checks ignore it,
/// so a worker already holding such generations keeps reusing them and only stops linking from them; requiring it
/// there would restage 852 MiB once per worker in the fleet.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub(crate) struct RuntimeStageState {
    pub schema_version: u32,
    pub runtime_digest: String,
    pub stable_count: usize,
    pub entries: Option<BTreeMap<String, String>>,
    pub staged_at: String,
}

pub(crate) fn state_path(root: &Path) -> PathBuf {
    root.join("runtime.json")
}

pub(crate) fn read_state(root: &Path) -> Option<RuntimeStageState> {
    serde_json::from_slice(&fs::read(state_path(root)).ok()?).ok()
}

pub(crate) fn java_binary(root: &Path, suffix: &str) -> PathBuf {
    root.join("jbr").join(suffix).join("bin").join("java")
}

/// The name one stable classpath entry is staged under. The index is part of the name because classpath order is
/// contract data, which is exactly why the name says nothing about the bytes.
fn staged_lib_name(index: usize, source: &Path) -> String {
    let base = source
        .file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_default();
    format!("{index:04}-{base}")
}

pub(crate) fn classpath_file(root: &Path) -> PathBuf {
    root.join(stage::CLASSPATH_FILE)
}

/// The staged classpath one generation recorded, in classpath order ([`stage::parse_classpath_file`]).
pub(crate) fn read_classpath(root: &Path) -> std::io::Result<Vec<String>> {
    let text = fs::read_to_string(classpath_file(root))?;
    Ok(stage::parse_classpath_file(&text))
}

/// Whether every recorded entry is a staged file of this generation.
///
/// One predicate for two answers: the reuse probe reads a false here as "stage again", and `launch-prep` reads it
/// as a refusal, because a classpath pointing outside the generation is a tree nothing may launch from.
pub(crate) fn classpath_is_staged_under(root: &Path, classpath: &[String]) -> bool {
    let lib = root.join("lib");
    classpath.iter().all(|entry| {
        let entry = Path::new(entry);
        entry.parent() == Some(lib.as_path()) && is_file(entry)
    })
}

/// A generation this stage can hand back untouched, or `None`.
///
/// Stricter than "the directory exists": a stage killed between its copy and its rename leaves nothing here, but
/// one killed by a full disk *after* the rename would leave a tree that looks finished.
fn read_complete_generation(root: &Path, digest: &str, stable_count: usize, suffix: &str) -> Option<RuntimeResult> {
    let state = read_state(root)?;
    let classpath = read_classpath(root).ok()?;
    if state.schema_version != stage::SCHEMA_VERSION
        || state.runtime_digest != digest
        || state.stable_count != stable_count
        || classpath.len() != stable_count
        || !classpath_is_staged_under(root, &classpath)
        || !is_file(&java_binary(root, suffix))
    {
        return None;
    }
    Some(RuntimeResult {
        root: display(root),
        java_binary: display(&java_binary(root, suffix)),
        classpath,
        classpath_file: display(&classpath_file(root)),
        reused: true,
    })
}

pub(crate) fn display(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

/// Whether one generation can be handed back untouched, without a manifest: `stage-check`.
///
/// Every check a reuse makes is still made - local stats that cost nothing. What replaces the manifest is
/// `classpath_sha256`: the caller's memory of the list this generation was staged with, held against the bytes
/// of the `classpath.txt` the stage itself wrote. A caller whose memory disagrees gets `complete: false` and
/// stages again: a needless stage is minutes, and the alternative is a daemon launched with a classpath nobody
/// checked. The list crosses the exec channel in neither direction, which was 16.1 s of a 95.7 s warm restart.
pub(crate) fn check(args: &StageCheckArgs) -> Result<RuntimeReuse, AgentRefusal> {
    let runtime_root = require_runtime_root(AgentVerb::StageCheck, &args.runtime_root)?;
    let Ok(stable_count) = args.stable_count.parse::<usize>() else {
        refuse!(
            AgentVerb::StageCheck,
            "stable classpath count is not a count: {}",
            args.stable_count
        );
    };
    if !is_sha256_hex(&args.classpath_sha256) {
        refuse!(
            AgentVerb::StageCheck,
            "expected classpath digest is not a sha256: {}",
            args.classpath_sha256
        );
    }
    let root = generation_root(AgentVerb::StageCheck, &runtime_root, &args.digest)?;
    let incomplete = RuntimeReuse {
        complete: false,
        root: None,
        java_binary: None,
        classpath_file: None,
    };
    let Some(complete) = read_complete_generation(&root, &args.digest, stable_count, &args.java_home_suffix) else {
        return Ok(incomplete);
    };
    let recorded = fs::read(&complete.classpath_file).ok();
    if recorded.is_none_or(|bytes| hex::encode(Sha256::digest(&bytes)) != args.classpath_sha256) {
        return Ok(incomplete);
    }
    Ok(RuntimeReuse {
        complete: true,
        root: Some(complete.root),
        java_binary: Some(complete.java_binary),
        classpath_file: Some(complete.classpath_file),
    })
}

/// The newest retained generation whose staged jars this stage may link, or `None`.
///
/// The host's manifest lists paths only, so a source's identity is read here, from the source: one pass over each
/// jar the size filter admits, against the two passes a copy costs. The donor's own hashes were taken by
/// [`copy_file_hashed`] over the bytes it actually wrote, so a match means "these two files hold the same bytes".
pub(crate) fn find_donor(runtime_root: &Path, digest: &str) -> Option<RuntimeDonor> {
    newest_donor(runtime_root, digest, |root| {
        let state = read_state(root)?;
        if state.schema_version != stage::SCHEMA_VERSION {
            return None;
        }
        // A generation staged before the content record existed is not a donor.
        let entries = state.entries?;
        Some(RuntimeDonor::from_record(&root.join("lib"), &entries))
    })
}

/// Stages the runtime `manifest_path` describes: `stage`.
pub(crate) fn stage(runtime_root: &str, manifest_path: Option<&Path>, stdin: &mut dyn Read) -> Result<RuntimeResult, AgentRefusal> {
    let runtime_root = require_runtime_root(AgentVerb::Stage, runtime_root)?;
    let raw = read_manifest(manifest_path, stdin)?;
    let Ok(manifest) = stage::decode_runtime_manifest(&raw) else {
        refuse!(AgentVerb::Stage, "invalid runtime stage manifest");
    };
    stage_manifest(&runtime_root, &manifest)
}

/// The manifest of a stage: on standard input, which is how the controller sends it, or in the file an argument
/// names. A manifest in both places is refused, because the stager cannot know which one the caller meant, and so is
/// a stage with neither.
fn read_manifest(manifest_path: Option<&Path>, stdin: &mut dyn Read) -> Result<Vec<u8>, AgentRefusal> {
    let mut piped = Vec::new();
    stdin.read_to_end(&mut piped).map_err(|error| {
        AgentRefusal::for_verb(
            AgentVerb::Stage,
            format!("cannot read the runtime stage manifest on standard input: {error}"),
        )
    })?;
    match manifest_path {
        None if piped.is_empty() => refuse!(
            AgentVerb::Stage,
            "no runtime stage manifest: standard input is empty, and no MANIFEST argument names a file"
        ),
        None => Ok(piped),
        Some(path) if !piped.is_empty() => refuse!(
            AgentVerb::Stage,
            "a runtime stage manifest arrived on standard input and as the argument {}; pass it once",
            path.display()
        ),
        Some(path) => fs::read(path).map_err(|error| {
            AgentRefusal::for_verb(
                AgentVerb::Stage,
                format!("cannot read the runtime stage manifest {}: {error}", path.display()),
            )
        }),
    }
}

fn stage_manifest(runtime_root: &Path, manifest: &RuntimeManifest) -> Result<RuntimeResult, AgentRefusal> {
    let digest = manifest.runtime_digest.as_str();
    let final_root = generation_root(AgentVerb::Stage, runtime_root, digest)?;
    let stable_count = manifest.stable_sources.len();
    if let Some(complete) = read_complete_generation(&final_root, digest, stable_count, &manifest.java_home_suffix) {
        return Ok(complete);
    }

    create_dirs(AgentVerb::Stage, &generations_dir(runtime_root))?;
    let staging = StagingTree::new(&generations_dir(runtime_root), digest);
    let temporary_root = staging.path();
    for directory in ["lib", "jbr"] {
        create_dirs(AgentVerb::Stage, &temporary_root.join(directory))?;
    }

    // A jar a retained generation already staged is linked rather than read again.
    let donor = find_donor(runtime_root, digest);
    let mut entries = BTreeMap::new();
    let mut final_classpath = Vec::with_capacity(stable_count);
    for (index, source) in manifest.stable_sources.iter().enumerate() {
        let source = Path::new(source);
        // One stat, because the size it reports decides whether this jar is read at all.
        let size = match fs::metadata(source) {
            Ok(info) if source.is_absolute() && info.is_file() => info.len(),
            _ => refuse!(AgentVerb::Stage, "stable classpath source is not a file: {}", source.display()),
        };
        let name = staged_lib_name(index, source);
        let staged = temporary_root.join("lib").join(&name);
        // The size is a filter and never an answer: a source whose length no retained file has cannot hold the
        // same bytes as one, so it goes straight to the copy without being hashed first. That keeps a rebuild of
        // the whole tier at one read per jar rather than two.
        let mut content = None;
        let mut donor_file = None;
        if let Some(donor) = donor.as_ref().filter(|donor| donor.sizes.contains(&size)) {
            let hashed = sha256_file(source)
                .map_err(|error| AgentRefusal::for_verb(AgentVerb::Stage, format!("cannot read {}: {error}", source.display())))?;
            donor_file = donor.by_content.get(&hashed).filter(|path| is_file(path)).cloned();
            content = Some(hashed);
        }
        let content = match (donor_file, content) {
            (Some(donor_file), Some(content)) => {
                fs::hard_link(&donor_file, &staged).map_err(|error| {
                    AgentRefusal::for_verb(
                        AgentVerb::Stage,
                        format!("cannot link {} to {}: {error}", donor_file.display(), staged.display()),
                    )
                })?;
                content
            }
            _ => copy_file_hashed(source, &staged)
                .map_err(|error| AgentRefusal::for_verb(AgentVerb::Stage, format!("cannot copy {}: {error}", source.display())))?,
        };
        entries.insert(name.clone(), content);
        final_classpath.push(display(&final_root.join("lib").join(&name)));
    }

    let archive = Path::new(&manifest.jbr_archive);
    if !archive.is_absolute() || !is_file(archive) {
        refuse!(AgentVerb::Stage, "JBR archive is not a file: {}", manifest.jbr_archive);
    }
    // `tar`'s own output is dropped rather than quoted. A staging failure's message travels to the host inside
    // the envelope, and a subprocess's output there is guest output reaching an agent the host never meant to
    // show it. What names the fault is what this process composed: which archive, and how `tar` exited. The
    // archive is a host-built artifact named by its digest, so a corrupt one reproduces on demand.
    let extracted = Command::new(TAR_BINARY)
        .arg("xzf")
        .arg(archive)
        .arg("-C")
        .arg(temporary_root.join("jbr"))
        .arg("--strip-components=1")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
    match extracted {
        Ok(status) if status.success() => {}
        Ok(status) => refuse!(AgentVerb::Stage, "JBR extraction failed for {}: {status}", manifest.jbr_archive),
        Err(error) => refuse!(AgentVerb::Stage, "JBR extraction failed for {}: {error}", manifest.jbr_archive),
    }
    let java = java_binary(temporary_root, &manifest.java_home_suffix);
    if !java.exists() {
        refuse!(AgentVerb::Stage, "JBR contains no {}", java.display());
    }

    write_records(
        temporary_root,
        &final_classpath,
        &RuntimeStageState {
            schema_version: stage::SCHEMA_VERSION,
            runtime_digest: digest.to_owned(),
            stable_count,
            entries: Some(entries),
            staged_at: now_stamp(),
        },
    )?;
    remove_tree(&final_root);
    staging
        .publish(&final_root)
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::Stage, format!("cannot publish {}: {error}", final_root.display())))?;
    Ok(RuntimeResult {
        root: display(&final_root),
        java_binary: display(&java_binary(&final_root, &manifest.java_home_suffix)),
        classpath: final_classpath,
        classpath_file: display(&classpath_file(&final_root)),
        reused: false,
    })
}

/// Writes the two records of the generation staged under `root`: the classpath and the marker. A refusal names the
/// file that could not be written.
pub(crate) fn write_records(root: &Path, classpath: &[String], state: &RuntimeStageState) -> Result<(), AgentRefusal> {
    let classpath_path = classpath_file(root);
    write_private(&classpath_path, stage::classpath_file_text(classpath).as_bytes())
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::Stage, format!("cannot write {}: {error}", classpath_path.display())))?;
    let marker = state_path(root);
    write_json_file(&marker, state)
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::Stage, format!("cannot write {}: {error}", marker.display())))
}
