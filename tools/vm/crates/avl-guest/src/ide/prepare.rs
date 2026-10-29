//! The preparation: the layout of one context of the lane IDE, from the context document to the paths that the caller
//! seeds before the launch.
//!
//! The steps, in order: refuse a context document that [`ide::validate_ide_context`] refuses, and a context with a live
//! IDE run. With `fresh`, delete what a preparation and a launch write: `config`, `system`, `plugins`, the project,
//! `home`, `bin`, the argument file and the launch record. The log directories, the run directories and `active.json`
//! of earlier runs stay. Create `config`, `system`, `plugins`, `project`, `home` and `bin`, each private. Write
//! `config/disabled_plugins.txt`. Unpack the project archive when the project is not there yet. Answer the paths.

use std::fs::{self, File};
use std::io;
use std::path::Path;

use avl_wire::ide::{self, BIN_DIR, DISABLED_PLUGINS_FILE, HOME_DIR, IdeContext, IdePrepared, PROJECT_DIR, ProjectSource};

use super::{CODE_PREPARE_FAILED, ContextPaths, create_private_dirs, require_free_slot};
use crate::fs::{StagingTree, is_directory};
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::supervisor::System;

#[cfg(test)]
mod tests;

/// A refusal of the preparation, with [`CODE_PREPARE_FAILED`].
fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(CODE_PREPARE_FAILED, message)
}

/// The paths of one context for its preparation, as guest path text.
struct PreparePaths {
    context: ContextPaths,
    project: String,
    home: String,
    bin: String,
}

impl PreparePaths {
    fn new(document: &IdeContext) -> Self {
        let context = ContextPaths::new(&document.ide_root, &document.launch_key);
        Self {
            project: context.child(PROJECT_DIR),
            home: context.child(HOME_DIR),
            bin: context.child(BIN_DIR),
            context,
        }
    }
}

/// Lays out the context of `document` and answers its paths. The document is checked first, so every caller gets the
/// checks of [`ide::validate_ide_context`].
pub(crate) fn prepare(system: &dyn System, document: &IdeContext) -> Result<IdePrepared, AgentRefusal> {
    ide::validate_ide_context(document).map_err(|error| refused(error.message))?;
    let paths = PreparePaths::new(document);
    require_free_slot(system, Path::new(&paths.context.dir))?;
    if document.fresh {
        remove_prepared(&paths, &document.project)?;
    }
    for directory in [
        &paths.context.config,
        &paths.context.system,
        &paths.context.plugins,
        &paths.project,
        &paths.home,
        &paths.bin,
    ] {
        create_private_dirs(CODE_PREPARE_FAILED, Path::new(directory))?;
    }
    write_disabled_plugins(&paths.context.config, &document.disabled_plugin_ids)?;
    let project_dir = unpack_project(&document.project, &paths.project)?;
    Ok(IdePrepared {
        context_dir: paths.context.dir,
        config_dir: paths.context.config,
        system_dir: paths.context.system,
        plugins_dir: paths.context.plugins,
        project_dir,
        home_dir: paths.home,
        bin_dir: paths.bin,
    })
}

/// Deletes what a preparation and a launch write: the data directories, the project with its relocated unpack root,
/// the home, the `bin` directory, the argument file and the launch record.
fn remove_prepared(paths: &PreparePaths, project: &ProjectSource) -> Result<(), AgentRefusal> {
    let relocated = project.relocate_to.iter();
    for directory in [
        &paths.context.config,
        &paths.context.system,
        &paths.context.plugins,
        &paths.project,
        &paths.home,
        &paths.bin,
    ]
    .into_iter()
    .chain(relocated)
    {
        remove_existing_tree(Path::new(directory))?;
    }
    for file in [&paths.context.arg_file, &paths.context.launch_record] {
        remove_existing_file(Path::new(file))?;
    }
    Ok(())
}

fn remove_existing_tree(path: &Path) -> Result<(), AgentRefusal> {
    deleted(path, fs::remove_dir_all(path))
}

fn remove_existing_file(path: &Path) -> Result<(), AgentRefusal> {
    deleted(path, fs::remove_file(path))
}

/// Answers the `result` of the deletion of `path`. A path that does not exist counts as deleted.
fn deleted(path: &Path, result: io::Result<()>) -> Result<(), AgentRefusal> {
    match result {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(refused(format!("cannot delete {}: {error}", path.display()))),
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
    fs::write(&path, text).map_err(|error| refused(format!("cannot write {}: {error}", path.display())))
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
        return Err(refused(format!("the unpack root {root} has no parent")));
    };
    create_private_dirs(CODE_PREPARE_FAILED, parent)?;
    let staging = StagingTree::new(parent, &name.to_string_lossy());
    create_private_dirs(CODE_PREPARE_FAILED, staging.path())?;
    let archive = Path::new(&project.archive);
    File::open(archive)
        .map_err(|error| error.to_string())
        .and_then(|file| zip::ZipArchive::new(file).map_err(|error| error.to_string()))
        .and_then(|mut zip| zip.extract(staging.path()).map_err(|error| error.to_string()))
        .map_err(|error| refused(format!("cannot unpack {}: {error}", archive.display())))?;
    remove_existing_tree(root_path)?;
    staging
        .publish(root_path)
        .map_err(|error| refused(format!("cannot move the project into {root}: {error}")))?;
    if !has_entries(Path::new(&project_dir)) {
        return Err(refused(format!(
            "the project archive {} holds no directory {archive_root:?} with files",
            archive.display()
        )));
    }
    Ok(project_dir)
}

/// Whether `path` is a directory with at least one entry. A project always has files.
fn has_entries(path: &Path) -> bool {
    is_directory(path) && fs::read_dir(path).is_ok_and(|mut entries| entries.next().is_some())
}
