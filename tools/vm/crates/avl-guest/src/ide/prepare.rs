//! `ide-prepare`: the layout of one context of the lane IDE, from the context document on standard input to the
//! paths that the caller seeds before `ide-launch`.
//!
//! The steps, in order: refuse a context with a live IDE run. With `fresh`, delete what a preparation and a launch
//! write: `config`, `system`, `plugins`, the project, `home`, `bin`, the argument file and the launch record. The log
//! directories, the run directories and `active.json` of earlier runs stay. Create `config`, `system`, `plugins`,
//! `project`, `home` and `bin`, each private. Write `config/disabled_plugins.txt`. Unpack the project archive when
//! the project is not there yet. Answer the paths.

use std::fs::{self, File};
use std::io::{self, Read};
use std::path::Path;

use avl_wire::ide::{
    self, ARG_FILE, BIN_DIR, CONFIG_DIR, DISABLED_PLUGINS_FILE, HOME_DIR, IdeContext, IdePrepared, LAUNCH_RECORD_FILE, PLUGINS_DIR,
    PROJECT_DIR, ProjectSource, SYSTEM_DIR,
};
use avl_wire::verb::AgentVerb;

use super::{create_private_dirs, require_free_slot};
use crate::reply::{AgentRefusal, AgentRefusalExt, refuse};
use crate::stage::{StagingTree, is_directory};
use crate::supervisor::System;

#[cfg(test)]
mod tests;

const VERB: AgentVerb = AgentVerb::IdePrepare;

/// Reads the context document from standard input, as `ide-launch` reads its document.
pub(crate) fn read_document(stdin: &mut dyn Read) -> Result<IdeContext, AgentRefusal> {
    let mut raw = Vec::new();
    stdin
        .read_to_end(&mut raw)
        .map_err(|error| AgentRefusal::for_verb(VERB, format!("cannot read the IDE context document: {error}")))?;
    ide::decode_ide_context(&raw).map_err(|error| AgentRefusal::for_verb(VERB, error.message))
}

/// The paths of one context, as guest path text.
struct ContextPaths {
    context: String,
    config: String,
    system: String,
    plugins: String,
    project: String,
    home: String,
    bin: String,
    arg_file: String,
    launch_record: String,
}

impl ContextPaths {
    fn new(document: &IdeContext) -> Self {
        let context = ide::context_dir(&document.ide_root, &document.launch_key);
        let child = |name: &str| format!("{context}/{name}");
        Self {
            config: child(CONFIG_DIR),
            system: child(SYSTEM_DIR),
            plugins: child(PLUGINS_DIR),
            project: child(PROJECT_DIR),
            home: child(HOME_DIR),
            bin: child(BIN_DIR),
            arg_file: child(ARG_FILE),
            launch_record: child(LAUNCH_RECORD_FILE),
            context,
        }
    }
}

/// Lays out the context of `document` and answers its paths.
pub(crate) fn prepare(system: &dyn System, document: &IdeContext) -> Result<IdePrepared, AgentRefusal> {
    let paths = ContextPaths::new(document);
    require_free_slot(system, Path::new(&paths.context))?;
    if document.fresh {
        remove_prepared(&paths, &document.project)?;
    }
    for directory in [
        &paths.config,
        &paths.system,
        &paths.plugins,
        &paths.project,
        &paths.home,
        &paths.bin,
    ] {
        create_private_dirs(VERB, Path::new(directory))?;
    }
    write_disabled_plugins(&paths.config, &document.disabled_plugin_ids)?;
    let project_dir = unpack_project(&document.project, &paths.project)?;
    Ok(IdePrepared {
        context_dir: paths.context,
        config_dir: paths.config,
        system_dir: paths.system,
        plugins_dir: paths.plugins,
        project_dir,
        home_dir: paths.home,
        bin_dir: paths.bin,
    })
}

/// Deletes what a preparation and a launch write: the data directories, the project with its relocated unpack root,
/// the home, the `bin` directory, the argument file and the launch record.
fn remove_prepared(paths: &ContextPaths, project: &ProjectSource) -> Result<(), AgentRefusal> {
    let relocated = project.relocate_to.iter();
    for directory in [
        &paths.config,
        &paths.system,
        &paths.plugins,
        &paths.project,
        &paths.home,
        &paths.bin,
    ]
    .into_iter()
    .chain(relocated)
    {
        remove_existing_tree(Path::new(directory))?;
    }
    for file in [&paths.arg_file, &paths.launch_record] {
        remove_existing_file(Path::new(file))?;
    }
    Ok(())
}

fn remove_existing_tree(path: &Path) -> Result<(), AgentRefusal> {
    match fs::remove_dir_all(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => refuse!(VERB, "cannot delete {}: {error}", path.display()),
    }
}

fn remove_existing_file(path: &Path) -> Result<(), AgentRefusal> {
    match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => refuse!(VERB, "cannot delete {}: {error}", path.display()),
    }
}

/// Writes the plugin ids that the IDE does not load, one per line, or removes the file of an earlier launch when the
/// list is empty.
fn write_disabled_plugins(config: &str, ids: &[String]) -> Result<(), AgentRefusal> {
    let path = Path::new(config).join(DISABLED_PLUGINS_FILE);
    if ids.is_empty() {
        return remove_existing_file(&path);
    }
    let mut text = ids.join("\n");
    text.push('\n');
    fs::write(&path, text).map_err(|error| AgentRefusal::for_verb(VERB, format!("cannot write {}: {error}", path.display())))
}

/// Unpacks the project archive when the project is not there yet, and answers the project directory.
///
/// The project stays across a relaunch on the same context, as it stays across an IDE restart: a scenario that
/// restarts the IDE reads what the earlier IDE wrote. A fresh preparation deletes the unpack root first, so it
/// unpacks again. The archive goes into a staging tree that is renamed onto the unpack root, so a killed unpack
/// leaves no half project that a later call takes for a whole one.
fn unpack_project(project: &ProjectSource, default_root: &str) -> Result<String, AgentRefusal> {
    let root = project.relocate_to.as_deref().unwrap_or(default_root);
    let archive_root = project.archive_root.trim_end_matches('/');
    let project_dir = if archive_root.is_empty() {
        root.to_owned()
    } else {
        format!("{root}/{archive_root}")
    };
    if has_entries(Path::new(&project_dir)) {
        return Ok(project_dir);
    }
    let root_path = Path::new(root);
    let (Some(parent), Some(name)) = (root_path.parent(), root_path.file_name()) else {
        refuse!(VERB, "the unpack root {root} has no parent");
    };
    create_private_dirs(VERB, parent)?;
    let staging = StagingTree::new(parent, &name.to_string_lossy());
    create_private_dirs(VERB, staging.path())?;
    let archive = Path::new(&project.archive);
    File::open(archive)
        .map_err(|error| error.to_string())
        .and_then(|file| zip::ZipArchive::new(file).map_err(|error| error.to_string()))
        .and_then(|mut zip| zip.extract(staging.path()).map_err(|error| error.to_string()))
        .map_err(|error| AgentRefusal::for_verb(VERB, format!("cannot unpack {}: {error}", archive.display())))?;
    remove_existing_tree(root_path)?;
    staging
        .publish(root_path)
        .map_err(|error| AgentRefusal::for_verb(VERB, format!("cannot move the project into {root}: {error}")))?;
    if !has_entries(Path::new(&project_dir)) {
        refuse!(
            VERB,
            "the project archive {} holds no directory {archive_root:?} with files",
            archive.display()
        );
    }
    Ok(project_dir)
}

/// Whether `path` is a directory with at least one entry. A project always has files.
fn has_entries(path: &Path) -> bool {
    is_directory(path) && fs::read_dir(path).is_ok_and(|mut entries| entries.next().is_some())
}
