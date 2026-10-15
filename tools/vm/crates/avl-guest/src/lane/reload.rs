//! The reload of the bridge plugin in a running lane IDE, which a bridge edit costs instead of a relaunch.
//!
//! The owner of the lane IDE decides a reload when only the bridge plugin directory of the context plugins changed
//! ([`plugin_change`]). The reload then runs these steps:
//!
//! 1. The files of the new bridge directory go to `<context>/plugin-staging/<the first 12 hex digits of its digest>`.
//!    That directory is outside the plugin directory, so a crash never leaves two copies for the next start.
//! 2. The resident plugin gets `POST /api/air/ui-test-resident/reload {pluginId, stagedDir, digest}`. It unloads the
//!    old copy, counts its old class loaders, renames the staged directory over the old one, rewrites the context
//!    plugin manifest, and loads the new copy. It answers `{unloaded, oldLoaders, loaded, digest, reason}`.
//! 3. The lifecycle long poll waits until the state names the new digest as `bridgePlugin`.
//!
//! The caller then waits for readiness, which pings the new copy. Each step that fails gives the reason of a relaunch.
//! The relaunch starts the new copy: after a rename, the directory and the manifest are already in place, and
//! otherwise the install of the relaunch writes them.
//!
//! The host action `reload-air` reloads Air in place, see [`reload_air`]. The bridge plugin depends on Air, so it
//! reloads from the same files with the same digest.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, Instant};

use avl_bridge::Client;
use avl_bridge::lifecycle::Reader;
use avl_wire::daemon::plugin_directory_digests;
use serde::Deserialize;
use serde_json::json;

use super::ide::{ContextPlugin, REQUIRED_CONTEXT_PLUGINS, remove_tree};

#[cfg(test)]
mod tests;

/// The plugin directory of the bridge plugin, the one context plugin that reloads.
pub(crate) const BRIDGE_DIRECTORY: &str = REQUIRED_CONTEXT_PLUGINS[0];

/// The id of the bridge plugin, `AIR_UI_TEST_BRIDGE_PLUGIN_ID` of the resident plugin.
pub(crate) const BRIDGE_PLUGIN_ID: &str = "com.intellij.air.integrationTests.bridge.plugin";

/// The reload route of the resident plugin, `AIR_UI_TEST_RESIDENT_RELOAD_PREFIX` with its leading slash.
pub(crate) const RELOAD_ROUTE: &str = "/api/air/ui-test-resident/reload";

/// The directory of the context that holds a staged copy, beside the plugin directory and never inside it.
pub(crate) const STAGING_DIR: &str = "plugin-staging";

/// How long the lifecycle state may take to name the new digest after the answer of the reload.
pub(crate) const DIGEST_WAIT: Duration = Duration::from_secs(30);

/// How long the reload route may take to answer. The driver waits up to 60 s for each step on the event thread to start,
/// counts the old class loaders for up to 10 s, and then loads the new copy.
const RELOAD_WAIT: Duration = Duration::from_mins(2);

/// The id of Air, which `reload-air` reloads in place.
pub(crate) const AIR_PLUGIN_ID: &str = "com.intellij.air";

/// The status of a reload that the driver refused before the unload. Its answer is a document too.
const REFUSED_STATUS: u16 = 409;

/// The pause before the next lifecycle poll after a failed poll.
const RETRY_INTERVAL: Duration = Duration::from_millis(250);

/// How the context plugins that a context holds differ from the wanted ones.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum PluginChange {
    /// Each plugin directory has the wanted digest.
    Same,
    /// Only the bridge directory changed: the digest of the installed copy and the digest of the wanted copy.
    Bridge { installed: String, wanted: String },
    /// A directory other than the bridge changed, or the context holds no bridge: the names of the directories that
    /// differ, in name order.
    Others(Vec<String>),
}

/// The change from the context plugin manifest `installed` to the files `wanted`, by the digest of each directory.
pub(crate) fn plugin_change(installed: &str, wanted: &[ContextPlugin]) -> PluginChange {
    let installed = plugin_directory_digests(installed.lines().filter_map(|line| {
        let (sha256, destination) = line.split_once(' ')?;
        Some((destination, sha256))
    }));
    let wanted = plugin_directory_digests(wanted.iter().map(|file| (file.destination.as_str(), file.sha256.as_str())));
    let mut changed: Vec<String> = installed
        .keys()
        .chain(wanted.keys())
        .filter(|directory| installed.get(*directory) != wanted.get(*directory))
        .cloned()
        .collect();
    changed.sort();
    changed.dedup();
    match changed.as_slice() {
        [] => PluginChange::Same,
        [only] if only == BRIDGE_DIRECTORY => match (installed.get(only), wanted.get(only)) {
            (Some(installed), Some(wanted)) => PluginChange::Bridge {
                installed: installed.clone(),
                wanted: wanted.clone(),
            },
            _ => PluginChange::Others(changed),
        },
        _ => PluginChange::Others(changed),
    }
}

/// The first 12 hex digits of a digest, as a note and a staged directory name it.
pub(crate) fn short(digest: &str) -> &str {
    digest.get(..12).unwrap_or(digest)
}

/// The answer of the reload route, `AirPluginReloadAnswer` of the resident plugin.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct ReloadAnswer {
    pub unloaded: bool,
    /// The class loaders of the old copy that stayed reachable after the unload, or -1 when no census ran.
    pub old_loaders: i64,
    pub loaded: bool,
    pub digest: Option<String>,
    /// Null only for a clean reload.
    pub reason: Option<String>,
}

/// Why the answer `answer` of the reload of the copy `wanted` is a relaunch, or `None` for a clean reload. `refused`
/// is a reload that the driver refused before the unload.
pub(crate) fn fallback_reason(answer: &ReloadAnswer, refused: bool, wanted: &str) -> Option<String> {
    let because = answer.reason.as_deref().map_or_else(String::new, |reason| format!(": {reason}"));
    if refused {
        return Some(format!("the resident plugin refused the reload of the bridge plugin{because}"));
    }
    if !answer.unloaded {
        return Some(format!("the platform did not unload the old bridge plugin{because}"));
    }
    if answer.old_loaders > 0 {
        return Some(format!(
            "the old bridge plugin left {} class loader(s) reachable after the unload{because}",
            answer.old_loaders
        ));
    }
    if !answer.loaded {
        return Some(format!("the platform did not load the new bridge plugin{because}"));
    }
    if let Some(reason) = &answer.reason {
        return Some(format!("the reload of the bridge plugin answered: {reason}"));
    }
    match answer.digest.as_deref() {
        Some(digest) if digest == wanted => None,
        other => Some(format!(
            "the reload loaded the bridge plugin {}, not {}",
            other.map_or("<none>", short),
            short(wanted)
        )),
    }
}

/// Reloads Air in place in the IDE behind `client`, the IDE of `launch`, within `within`, and waits until the lifecycle
/// names the bridge plugin of before the reload again.
///
/// The route gets no staged directory and no digest, so the resident plugin unloads Air and loads it again from the same
/// files. The bridge plugin depends on Air, so it reloads too, with the same digest. The resident plugin outlives the
/// reload, so it answers after the load. A refusal, an unload that did not happen and a load that did not happen fail
/// the reload. Old class loaders that stayed reachable fail nothing: the next part of the scenario counts them. `note`
/// gets one line for the answer and one for the wait. The caller then pings the new copy.
pub(crate) fn reload_air(client: &Client, launch: &str, within: Duration, note: &mut dyn FnMut(String)) -> Result<(), String> {
    let reader = Reader::new(client.clone(), Some(launch));
    let digest = reader
        .poll(None, Duration::ZERO)
        .map_err(|error| format!("the lifecycle did not answer before the reload of Air: {error}"))?
        .state
        .bridge_plugin
        .ok_or_else(|| "the lifecycle names no bridge plugin before the reload of Air".to_owned())?;
    let requested = Instant::now();
    let (status, body) = client
        .post_document(
            RELOAD_ROUTE,
            &json!({ "pluginId": AIR_PLUGIN_ID }).to_string(),
            &[REFUSED_STATUS],
            requested + within,
        )
        .map_err(|error| format!("the reload request of Air failed: {error}"))?;
    let answer: ReloadAnswer = serde_json::from_slice(&body).map_err(|error| {
        format!(
            "the reload route answered {}, not a reload answer: {error}",
            String::from_utf8_lossy(&body)
        )
    })?;
    let because = answer.reason.as_deref().map_or_else(String::new, |reason| format!(": {reason}"));
    if status == REFUSED_STATUS {
        return Err(format!("the resident plugin refused the reload of Air{because}"));
    }
    if !answer.unloaded {
        return Err(format!("the platform did not unload Air{because}"));
    }
    if !answer.loaded {
        return Err(format!("the platform did not load Air again{because}"));
    }
    note(format!(
        "reload-air: unloaded, {} old class loader(s), loaded, in {} ms",
        answer.old_loaders,
        requested.elapsed().as_millis()
    ));
    let polled = Instant::now();
    await_bridge_plugin(&reader, &digest, DIGEST_WAIT)?;
    note(format!(
        "reload-air: the lifecycle names the bridge plugin {} after {} ms",
        short(&digest),
        polled.elapsed().as_millis()
    ));
    Ok(())
}

/// Reloads the bridge plugin of the IDE behind `client` from `files`, the context plugin files of the run, and waits
/// until the lifecycle of the IDE of `launch` names the new digest. `context` is the context directory of the IDE.
/// `note` gets one timed line for each step. The error is the reason of a relaunch.
pub(crate) fn reload(
    client: &Client,
    launch: &str,
    context: &Path,
    files: &[ContextPlugin],
    note: &mut dyn FnMut(String),
) -> Result<(), String> {
    let wanted = plugin_directory_digests(files.iter().map(|file| (file.destination.as_str(), file.sha256.as_str())))
        .remove(BRIDGE_DIRECTORY)
        .ok_or_else(|| format!("the context plugins of the run hold no {BRIDGE_DIRECTORY}"))?;
    let started = Instant::now();
    let staged = stage(context, files, &wanted).map_err(|error| format!("cannot stage the bridge plugin: {error}"))?;
    note(format!(
        "reload: staged the bridge plugin {} in {} ms at {}",
        short(&wanted),
        started.elapsed().as_millis(),
        staged.display()
    ));
    let requested = Instant::now();
    let request = json!({ "pluginId": BRIDGE_PLUGIN_ID, "stagedDir": staged.display().to_string(), "digest": wanted });
    let (status, body) = client
        .post_document(RELOAD_ROUTE, &request.to_string(), &[REFUSED_STATUS], requested + RELOAD_WAIT)
        .map_err(|error| format!("the reload request failed: {error}"))?;
    let answer: ReloadAnswer = serde_json::from_slice(&body).map_err(|error| {
        format!(
            "the reload route answered {}, not a reload answer: {error}",
            String::from_utf8_lossy(&body)
        )
    })?;
    note(format!(
        "reload: the IDE answered HTTP {status} in {} ms: unloaded {}, {} old class loader(s), loaded {}",
        requested.elapsed().as_millis(),
        answer.unloaded,
        answer.old_loaders,
        answer.loaded
    ));
    if let Some(reason) = fallback_reason(&answer, status == REFUSED_STATUS, &wanted) {
        return Err(reason);
    }
    let polled = Instant::now();
    await_bridge_plugin(&Reader::new(client.clone(), Some(launch)), &wanted, DIGEST_WAIT)?;
    note(format!(
        "reload: the lifecycle names the bridge plugin {} after {} ms",
        short(&wanted),
        polled.elapsed().as_millis()
    ));
    Ok(())
}

/// Writes the files of the bridge directory among `files` to `<context>/plugin-staging/<short digest>` and answers that
/// directory. An earlier staged copy goes first, so the directory holds exactly the files of the run.
pub(crate) fn stage(context: &Path, files: &[ContextPlugin], digest: &str) -> io::Result<PathBuf> {
    let root = context.join(STAGING_DIR);
    remove_tree(&root)?;
    let staged = root.join(short(digest));
    fs::create_dir_all(&staged)?;
    for file in files {
        let mut components = file.destination.split('/');
        if components.next() != Some(BRIDGE_DIRECTORY) {
            continue;
        }
        let target = components.fold(staged.clone(), |path, component| path.join(component));
        if let Some(parent) = target.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(&file.source, &target)?;
    }
    Ok(staged)
}

/// Removes the staged copies of `context`, as after a reload that fell back to a relaunch.
pub(crate) fn clear_staging(context: &Path) -> io::Result<()> {
    remove_tree(&context.join(STAGING_DIR))
}

/// Waits up to `within` until the lifecycle state names `digest` as `bridgePlugin`. A failed poll that a later poll can
/// cure waits for the next one, because the bridge answers 503 while it unloads.
pub(crate) fn await_bridge_plugin(reader: &Reader, digest: &str, within: Duration) -> Result<(), String> {
    let deadline = Instant::now() + within;
    let mut after = None;
    let mut latest: Option<String> = None;
    loop {
        let left = deadline.saturating_duration_since(Instant::now());
        if left.is_zero() {
            return Err(format!(
                "the lifecycle state named no bridge plugin {} within {} ms; the latest state names {}",
                short(digest),
                within.as_millis(),
                latest.as_deref().map_or("none", short)
            ));
        }
        match reader.poll(after, left) {
            Ok(snapshot) if snapshot.state.bridge_plugin.as_deref() == Some(digest) => return Ok(()),
            Ok(snapshot) => {
                after = Some(snapshot.seq);
                latest = snapshot.state.bridge_plugin;
            }
            Err(error) if error.is_transient() => {
                after = None;
                thread::sleep(RETRY_INTERVAL.min(left));
            }
            Err(error) => return Err(format!("the lifecycle poll after the reload failed: {error}")),
        }
    }
}
