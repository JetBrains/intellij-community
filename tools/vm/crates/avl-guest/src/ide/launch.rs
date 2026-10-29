//! The launch: one launch of the lane IDE on a prepared context, from the launch document to a running supervisor run.
//!
//! The steps, in order: refuse a launch document that [`ide::validate_ide_launch`] refuses, a Linux guest whose agent
//! names no X display, and a context with a live IDE run. Kill the JCEF helpers that name the context. Create
//! `log/<launchName>` with `jvm`, `heap-dump` and `snapshots`. Compose the argument file through `dev-launch`, with the
//! data paths of the context last. Publish the argument file and the launch record. Start the run
//! `run-ide-<launchName>` in the run slot of the context: `<javaHome>/bin/java @<argument file> <projectDir>` in the
//! distribution home, with the closed environment of the context.

use std::ffi::OsString;
use std::fs;
use std::io::{self, Write};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use avl_wire::ide::{self, IdeLaunch, IdeLaunched, LOG_DIR, LaunchRecord, OWNED_JVM_OPTIONS, OWNED_PROPERTIES};
use avl_wire::supervisor::EnvironmentPolicy;
use dev_launch::{Distribution, DistributionFiles};
use serde::Deserialize;

use super::{CODE_LAUNCH_FAILED, ContextPaths, create_private_dirs, reap, require_free_slot};
use crate::clock::stamp;
use crate::fs::{is_directory, is_file};
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::supervisor::{self, Launcher, System};

#[cfg(test)]
mod tests;

/// The refusal of a launch on a Linux guest whose agent names no X display.
pub(crate) const CODE_DISPLAY_MISSING: &str = "ide_display_missing";

/// What the launch reads from the guest itself: the OS, and the display of the agent's own environment.
pub(crate) struct Guest {
    pub linux: bool,
    pub display: Option<OsString>,
}

impl Guest {
    pub(crate) fn current() -> Self {
        Self {
            linux: cfg!(target_os = "linux"),
            display: std::env::var_os("DISPLAY"),
        }
    }
}

/// A refusal of the launch, with [`CODE_LAUNCH_FAILED`].
fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(CODE_LAUNCH_FAILED, message)
}

/// The paths of one context for one launch, as guest path text.
struct LaunchPaths {
    context: ContextPaths,
    log: String,
}

impl LaunchPaths {
    fn new(document: &IdeLaunch) -> Self {
        let context = ContextPaths::new(&document.ide_root, &document.launch_key);
        Self {
            log: context.child(&format!("{LOG_DIR}/{}", document.launch_name)),
            context,
        }
    }
}

/// Launches the IDE of `document` on its prepared context and answers the state of the run. The document is checked
/// first, so every caller gets the checks of [`ide::validate_ide_launch`].
pub(crate) fn launch(system: &dyn System, launcher: &Launcher, guest: &Guest, document: &IdeLaunch) -> Result<IdeLaunched, AgentRefusal> {
    ide::validate_ide_launch(document).map_err(|error| refused(error.message))?;
    require_display(guest)?;
    let paths = LaunchPaths::new(document);
    let context = Path::new(&paths.context.dir);
    require_free_slot(system, context)?;
    for (directory, what) in [(&paths.context.config, "context"), (&document.project_dir, "project")] {
        if !is_directory(Path::new(directory)) {
            return Err(refused(format!(
                "the {what} directory {directory} does not exist; prepare the context first"
            )));
        }
    }
    reap::reap_jcef_helpers(context);
    for directory in ["jvm", "heap-dump", "snapshots"] {
        create_private_dirs(CODE_LAUNCH_FAILED, &Path::new(&paths.log).join(directory))?;
    }

    let arguments = compose_arguments(document, &paths)?;
    let text =
        dev_launch::argument_file_text(&arguments).map_err(|error| refused(format!("cannot write the IDE argument file: {error:#}")))?;
    publish_privately(Path::new(&paths.context.arg_file), text.as_bytes())
        .map_err(|error| refused(format!("cannot publish {}: {error}", paths.context.arg_file)))?;
    let record = LaunchRecord {
        schema_version: ide::SCHEMA_VERSION,
        launch_key: document.launch_key.clone(),
        launch_name: document.launch_name.clone(),
        product_digest: document.product_digest.clone(),
        java_home: document.java_home.clone(),
        arg_file: paths.context.arg_file.clone(),
        prepared_at: stamp(system.now()),
    };
    let mut encoded = serde_json::to_vec(&record).map_err(AgentRefusal::internal)?;
    encoded.push(b'\n');
    publish_privately(Path::new(&paths.context.launch_record), &encoded)
        .map_err(|error| refused(format!("cannot publish {}: {error}", paths.context.launch_record)))?;

    let Some(run_id) = ide::ide_run_id(&document.launch_name) else {
        return Err(AgentRefusal::internal("the checked launch name gives no run id"));
    };
    let argv = vec![
        format!("{}/bin/java", document.java_home.trim_end_matches('/')),
        format!("@{}", paths.context.arg_file),
        document.project_dir.clone(),
    ];
    let run = supervisor::launch_run(
        system,
        launcher,
        context,
        &run_id,
        Path::new(&document.dist_home),
        argv,
        None,
        EnvironmentPolicy::Context {
            context_dir: paths.context.dir.clone(),
        },
    )?;
    Ok(IdeLaunched {
        log_dir: paths.log,
        arg_file: paths.context.arg_file,
        run,
    })
}

/// Refuses a Linux guest where the agent's own environment names no X display. The guest has no Xvfb wrapper: an IDE
/// without a display fails only after its start.
fn require_display(guest: &Guest) -> Result<(), AgentRefusal> {
    if guest.linux && guest.display.as_ref().is_none_or(|display| display.is_empty()) {
        return Err(AgentRefusal::refused(
            CODE_DISPLAY_MISSING,
            "the IDE needs an X display on Linux, and the agent names no DISPLAY",
        ));
    }
    Ok(())
}

/// The arguments of `java` for the launch: the flags file, the properties of the document, the distribution, then
/// the data paths of the context, then the class path and the main class.
///
/// The data paths come after the properties of the distribution, so a value in `idea.properties` cannot move a data
/// directory out of the context.
fn compose_arguments(document: &IdeLaunch, paths: &LaunchPaths) -> Result<Vec<String>, AgentRefusal> {
    let home = document.dist_home.as_str();
    let bin = Path::new(home).join("bin");
    let product_info = product_info_file(Path::new(home));
    let vm_options = vm_options_destination(&product_info)?;

    let flags_file = Path::new(&document.flags_file);
    let flags = dev_launch::read_lines(flags_file).map_err(|error| refused(format!("{error:#}")))?;
    let mut command_line = Vec::new();
    for flag in flags.iter().map(|line| line.trim()).filter(|line| !line.is_empty()) {
        if let Some(owned) = owned_option(flag) {
            return Err(refused(format!(
                "the flags file {} states {owned}, which names a data path that the agent owns",
                flags_file.display()
            )));
        }
        command_line.push(flag.to_owned());
    }
    command_line.extend(document.properties.iter().map(|(key, value)| format!("-D{key}={value}")));

    let idea_properties = bin.join("idea.properties");
    let vm_options_file = vm_options.split('/').fold(PathBuf::from(home), |path, part| path.join(part));
    let core_classpath = Path::new(home).join("core-classpath.txt");
    let files = DistributionFiles {
        ide_config: Path::new(&document.ide_config),
        idea_properties: &idea_properties,
        vm_options: &vm_options_file,
        product_info: &product_info,
        core_classpath: &core_classpath,
    };
    let runtime_module_repository = is_file(&Path::new(home).join("modules").join("module-descriptors.dat"));
    let distribution = Distribution::read(home, &files, &vm_options, runtime_module_repository)
        .map_err(|error| refused(format!("cannot read the distribution {home}: {error:#}")))?;
    let mut arguments = dev_launch::java_arguments(command_line, distribution, None)
        .map_err(|error| refused(format!("cannot compose the IDE launch: {error:#}")))?;

    // `java_arguments` ends with `-cp <class path> <main class>`, and a JVM option after the main class is a program
    // argument.
    let Some(tail_start) = arguments.len().checked_sub(3).filter(|&start| arguments[start] == "-cp") else {
        return Err(AgentRefusal::internal(
            "the composed IDE launch does not end with the class path and the main class",
        ));
    };
    let tail = arguments.split_off(tail_start);
    arguments.extend(data_options(paths));
    arguments.extend(tail);
    Ok(arguments)
}

/// The place of `product-info.json` in a production distribution on this OS, relative to the home.
pub(crate) const PRODUCT_INFO_FILE: &str = if cfg!(target_os = "macos") {
    "Resources/product-info.json"
} else {
    "product-info.json"
};

/// The `product-info.json` of the distribution at `home`, as `DevLaunchProperties.kt` finds it.
///
/// The first candidate is [`PRODUCT_INFO_FILE`]. The second is `bin/product-info.json`, the place in an older dev
/// distribution. When neither file exists, the answer is the first candidate, so that the read error names it.
fn product_info_file(home: &Path) -> PathBuf {
    let production = home.join(PRODUCT_INFO_FILE);
    if is_file(&production) {
        return production;
    }
    let dev_build = home.join("bin").join("product-info.json");
    if is_file(&dev_build) { dev_build } else { production }
}

/// The slash path of the main vmoptions file relative to the home, as the launcher finds it.
///
/// It is `vmOptionsFilePath` of the first launch of `product_info`, relative to the directory of [`PRODUCT_INFO_FILE`].
/// The `bin/product-info.json` of an older dev distribution holds the text of the production place, so the same base
/// applies to it.
fn vm_options_destination(product_info: &Path) -> Result<String, AgentRefusal> {
    #[derive(Deserialize)]
    struct ProductInfo {
        launch: Vec<Launch>,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Launch {
        vm_options_file_path: String,
    }

    let data = fs::read(product_info).map_err(|error| refused(format!("cannot read {}: {error}", product_info.display())))?;
    let info: ProductInfo = serde_json::from_slice(&data).map_err(|error| {
        refused(format!(
            "{} states no launch with a vmOptionsFilePath: {error}",
            product_info.display()
        ))
    })?;
    let Some(launch) = info.launch.first() else {
        return Err(refused(format!("{} states no launch", product_info.display())));
    };
    let base = PRODUCT_INFO_FILE.rsplit_once('/').map_or("", |(directory, _)| directory);
    let Some(destination) = resolve_in_home(base, &launch.vm_options_file_path) else {
        return Err(refused(format!(
            "the vmOptionsFilePath '{}' of {} is not a file of the home",
            launch.vm_options_file_path,
            product_info.display()
        )));
    };
    Ok(destination)
}

/// The slash path `relative`, resolved against the slash directory `base` by its text. Both are relative to the home.
///
/// The answer is `None` for an absolute path, a backslash, a path that leaves the home and the home itself.
fn resolve_in_home(base: &str, relative: &str) -> Option<String> {
    if relative.starts_with('/') || relative.contains('\\') {
        return None;
    }
    let mut parts: Vec<&str> = base.split('/').filter(|part| !part.is_empty()).collect();
    for part in relative.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                parts.pop()?;
            }
            _ => parts.push(part),
        }
    }
    if parts.is_empty() { None } else { Some(parts.join("/")) }
}

/// The data path that a flag states, or `None`.
fn owned_option(flag: &str) -> Option<&str> {
    if let Some(property) = flag.strip_prefix("-D") {
        let key = property.split_once('=').map_or(property, |(key, _)| key);
        return OWNED_PROPERTIES.iter().copied().find(|owned| *owned == key);
    }
    OWNED_JVM_OPTIONS.iter().copied().find(|prefix| flag.starts_with(prefix))
}

/// The JVM options that put every file the IDE writes into the context: the data directories of `idea.properties`,
/// and the crash, heap dump, GC, trace and snapshot files in the log directory of the launch.
fn data_options(paths: &LaunchPaths) -> Vec<String> {
    let log = &paths.log;
    vec![
        format!("-Didea.config.path={}", paths.context.config),
        format!("-Didea.system.path={}", paths.context.system),
        format!("-Didea.plugins.path={}", paths.context.plugins),
        format!("-Didea.log.path={log}"),
        format!("-Dsnapshots.path={log}/snapshots"),
        format!("-Didea.diagnostic.opentelemetry.file={log}/opentelemetry.json"),
        format!("-XX:ErrorFile={log}/jvm/java_error_in_idea_%p.log"),
        format!("-XX:HeapDumpPath={log}/heap-dump/heap-dump.hprof"),
        format!("-Xlog:gc*:file={log}/gcLog.log"),
    ]
}

/// Writes `content` where a reader sees the whole file or no file, readable only by this account.
///
/// The publish is atomic, because the IDE that starts next reads the file. A half-written argument file is an IDE
/// that starts with part of its options. The file is synced before the rename, because a worker can hard-reset. A
/// failed publish leaves no temporary file.
fn publish_privately(path: &Path, content: &[u8]) -> io::Result<()> {
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
