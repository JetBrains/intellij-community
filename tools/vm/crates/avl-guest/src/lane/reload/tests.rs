use std::fs;
use std::path::Path;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

use avl_testkit::http::{FakeHttp, Request, Response};
use pretty_assertions::assert_eq;
use serde_json::{Value, json};
use tempfile::TempDir;

use super::*;
use crate::lane::testing::{context_plugin, token_refusal};

const RESIDENT: &str = REQUIRED_CONTEXT_PLUGINS[1];
const TOKEN: &str = "bridge-token";
const LAUNCH: &str = "air-ui-0123456789abcdef";

/// The context plugins of a run: the bridge with `bridge` as its jar, and the resident part with `resident`.
fn plugins(sources: &Path, bridge: &str, resident: &str) -> Vec<ContextPlugin> {
    vec![
        context_plugin(sources, &format!("{BRIDGE_DIRECTORY}/lib/bridge.jar"), bridge),
        context_plugin(sources, &format!("{BRIDGE_DIRECTORY}/lib/modules/frontend.jar"), "frontend"),
        context_plugin(sources, &format!("{RESIDENT}/lib/resident.jar"), resident),
    ]
}

fn bridge_digest(files: &[ContextPlugin]) -> String {
    plugin_directory_digests(files.iter().map(|file| (file.destination.as_str(), file.sha256.as_str())))
        .remove(BRIDGE_DIRECTORY)
        .unwrap()
}

// The decision reads the digest of each plugin directory: the same files are no change, a bridge edit alone is a
// reload, and an edit of the resident part, of both, or a context with no bridge is a relaunch that names the
// directories.
#[test]
fn only_a_change_of_the_bridge_directory_alone_is_a_reload() {
    let sources = TempDir::new().unwrap();
    let installed = plugins(sources.path(), "b1", "r1");
    let manifest = super::super::ide::manifest(&installed);
    assert_eq!(plugin_change(&manifest, &installed), PluginChange::Same);

    let bridge_edit = plugins(sources.path(), "b2", "r1");
    assert_eq!(
        plugin_change(&manifest, &bridge_edit),
        PluginChange::Bridge {
            installed: bridge_digest(&installed),
            wanted: bridge_digest(&bridge_edit),
        }
    );
    assert_eq!(
        plugin_change(&manifest, &plugins(sources.path(), "b1", "r2")),
        PluginChange::Others(vec![RESIDENT.to_owned()])
    );
    assert_eq!(
        plugin_change(&manifest, &plugins(sources.path(), "b2", "r2")),
        PluginChange::Others(vec![BRIDGE_DIRECTORY.to_owned(), RESIDENT.to_owned()])
    );
    let without_bridge: String = manifest
        .lines()
        .filter(|line| !line.contains(&format!(" {BRIDGE_DIRECTORY}/")))
        .map(|line| format!("{line}\n"))
        .collect();
    assert_eq!(
        plugin_change(&without_bridge, &bridge_edit),
        PluginChange::Others(vec![BRIDGE_DIRECTORY.to_owned()])
    );
    assert_eq!(
        plugin_change("", &installed),
        PluginChange::Others(vec![BRIDGE_DIRECTORY.to_owned(), RESIDENT.to_owned()])
    );
}

fn answer(unloaded: bool, old_loaders: i64, loaded: bool, digest: Option<&str>, reason: Option<&str>) -> ReloadAnswer {
    ReloadAnswer {
        unloaded,
        old_loaders,
        loaded,
        digest: digest.map(str::to_owned),
        reason: reason.map(str::to_owned),
    }
}

// Each answer of the route that is not a clean reload of the wanted copy names the reason of the relaunch.
#[test]
fn each_answer_that_is_not_a_clean_reload_names_its_relaunch() {
    let wanted = "a".repeat(64);
    let clean = answer(true, 0, true, Some(&wanted), None);
    assert_eq!(fallback_reason(&clean, false, &wanted), None);
    let cases = [
        (
            answer(false, -1, false, None, Some("the plugin is not loaded")),
            true,
            "the resident plugin refused the reload of the bridge plugin: the plugin is not loaded",
        ),
        (
            answer(false, -1, false, None, Some("the platform cannot unload it")),
            false,
            "the platform did not unload the old bridge plugin: the platform cannot unload it",
        ),
        (
            answer(true, 2, true, Some(&wanted), Some("2 old class loaders")),
            false,
            "the old bridge plugin left 2 class loader(s) reachable after the unload: 2 old class loaders",
        ),
        (
            answer(true, 0, false, None, Some("loadPlugin answered false")),
            false,
            "the platform did not load the new bridge plugin: loadPlugin answered false",
        ),
        (
            answer(true, 0, true, Some(&wanted), Some("a late failure")),
            false,
            "the reload of the bridge plugin answered: a late failure",
        ),
        (
            answer(true, 0, true, Some(&"b".repeat(64)), None),
            false,
            "the reload loaded the bridge plugin bbbbbbbbbbbb, not aaaaaaaaaaaa",
        ),
    ];
    for (answer, refused, expected) in cases {
        assert_eq!(fallback_reason(&answer, refused, &wanted).as_deref(), Some(expected), "{answer:?}");
    }
}

// The staged copy holds exactly the files of the bridge directory, below a directory of the context that is named by
// the digest. An earlier staged copy goes first.
#[test]
fn the_staged_copy_holds_the_bridge_files_outside_the_plugin_directory() {
    let sources = TempDir::new().unwrap();
    let context = TempDir::new().unwrap();
    let stale = context.path().join(STAGING_DIR).join("000000000000");
    fs::create_dir_all(&stale).unwrap();
    let files = plugins(sources.path(), "b2", "r1");
    let digest = bridge_digest(&files);
    let staged = stage(context.path(), &files, &digest).unwrap();
    assert_eq!(staged, context.path().join(STAGING_DIR).join(&digest[..12]));
    assert!(!stale.exists());
    assert_eq!(fs::read_to_string(staged.join("lib/bridge.jar")).unwrap(), "b2");
    assert_eq!(fs::read_to_string(staged.join("lib/modules/frontend.jar")).unwrap(), "frontend");
    assert_eq!(
        fs::read_dir(staged.join("lib")).unwrap().count(),
        2,
        "the resident part is not staged"
    );
    clear_staging(context.path()).unwrap();
    assert!(!context.path().join(STAGING_DIR).exists());
}

/// One lifecycle answer of the IDE of [`LAUNCH`] at `seq`, whose loaded bridge plugin is `bridge`.
fn snapshot(seq: u64, bridge: Option<&str>) -> String {
    json!({
        "seq": seq,
        "state": {
            "schemaVersion": 2,
            "phase": "READY",
            "launch": LAUNCH,
            "pid": 4321,
            "projects": ["plain-project"],
            "indexing": false,
            "backgroundTasks": 0,
            "bridgePlugin": bridge,
            "vocabularyDigest": null,
        }
    })
    .to_string()
}

/// A fake resident plugin: the reload route answers `status` and `answer`, and the lifecycle names `before` until
/// `switch_after` polls, then `after`. It keeps the body of each reload request.
struct FakeResident {
    server: FakeHttp,
    requests: Arc<Mutex<Vec<Value>>>,
}

impl FakeResident {
    fn start(status: u16, answer: Value, before: Option<String>, after: Option<String>, switch_after: usize) -> Self {
        let requests = Arc::new(Mutex::new(Vec::new()));
        let seen = Arc::clone(&requests);
        let polls = AtomicUsize::new(0);
        let server = FakeHttp::start(move |request: &Request| {
            if let Some(refusal) = token_refusal(request, TOKEN) {
                return refusal;
            }
            if request.path.starts_with("/api/air/ui-test-events") {
                let poll = polls.fetch_add(1, Ordering::SeqCst);
                let (seq, bridge) = if poll < switch_after { (1, &before) } else { (2, &after) };
                return Response::ok("application/json", snapshot(seq, bridge.as_deref()));
            }
            if request.method == "POST" && request.path == RELOAD_ROUTE {
                seen.lock().unwrap().push(serde_json::from_slice(&request.body).unwrap());
                if status == 500 {
                    return Response::error(500, "The Air UI-test reload failed: java.lang.IllegalStateException");
                }
                return Response::json(status, answer.to_string());
            }
            Response::error(404, "no such route")
        });
        Self { server, requests }
    }

    fn reload(&self, context: &Path, files: &[ContextPlugin]) -> (Result<(), String>, Vec<String>) {
        let client = Client::new(self.server.url(), TOKEN).unwrap();
        let mut notes = Vec::new();
        let result = reload(&client, LAUNCH, context, files, &mut |note| notes.push(note));
        (result, notes)
    }
}

/// The reload of the bridge jar `b2`: the sources of the context plugins, the context, the plugins and the digest of
/// the bridge plugin.
struct Reload {
    sources: TempDir,
    context: TempDir,
    files: Vec<ContextPlugin>,
    digest: String,
}

impl Reload {
    fn new() -> Self {
        let sources = TempDir::new().unwrap();
        let files = plugins(sources.path(), "b2", "r1");
        Self {
            context: TempDir::new().unwrap(),
            digest: bridge_digest(&files),
            files,
            sources,
        }
    }
}

fn clean_answer(digest: &str) -> Value {
    json!({ "unloaded": true, "oldLoaders": 0, "loaded": true, "digest": digest, "reason": null })
}

// A clean reload posts the bridge plugin id, the staged directory and its digest, and ends when the lifecycle names the
// new digest. Each step writes one timed line.
#[test]
fn a_clean_reload_waits_for_the_new_digest_in_the_lifecycle() {
    let Reload {
        sources,
        context,
        files,
        digest,
    } = Reload::new();
    let old = bridge_digest(&plugins(sources.path(), "b1", "r1"));
    let resident = FakeResident::start(200, clean_answer(&digest), Some(old), Some(digest.clone()), 2);
    let (result, notes) = resident.reload(context.path(), &files);
    result.unwrap();
    let staged = context.path().join(STAGING_DIR).join(&digest[..12]);
    assert_eq!(
        *resident.requests.lock().unwrap(),
        [json!({ "pluginId": BRIDGE_PLUGIN_ID, "stagedDir": staged.display().to_string(), "digest": digest })]
    );
    assert_eq!(notes.len(), 3, "{notes:?}");
    assert!(
        notes[0].starts_with(&format!("reload: staged the bridge plugin {}", &digest[..12])),
        "{notes:?}"
    );
    assert!(
        notes[1].contains("answered HTTP 200") && notes[1].contains("0 old class loader(s)"),
        "{notes:?}"
    );
    assert!(
        notes[2].starts_with(&format!("reload: the lifecycle names the bridge plugin {}", &digest[..12])),
        "{notes:?}"
    );
}

// A refused reload, a leaked class loader and a driver failure each end the reload with the reason of the relaunch,
// and none of them waits for the lifecycle.
#[test]
fn a_refusal_a_leak_and_a_driver_failure_are_each_a_relaunch() {
    let Reload {
        sources: _sources,
        context,
        files,
        digest,
    } = Reload::new();
    let refused = json!({ "unloaded": false, "oldLoaders": -1, "loaded": false, "digest": null, "reason": "a reload runs" });
    let leaked = json!({ "unloaded": true, "oldLoaders": 1, "loaded": true, "digest": digest, "reason": "1 old class loader" });
    for (status, answer, expected) in [
        (
            409,
            refused,
            "the resident plugin refused the reload of the bridge plugin: a reload runs",
        ),
        (
            200,
            leaked,
            "the old bridge plugin left 1 class loader(s) reachable after the unload: 1 old class loader",
        ),
        (
            500,
            Value::Null,
            "the reload request failed: POST /api/air/ui-test-resident/reload: the bridge answered HTTP 500",
        ),
    ] {
        let resident = FakeResident::start(status, answer, None, None, 0);
        let (result, _) = resident.reload(context.path(), &files);
        let reason = result.unwrap_err();
        assert!(reason.starts_with(expected), "{reason}");
        assert_eq!(resident.requests.lock().unwrap().len(), 1);
    }
}

// A load that the lifecycle never shows is a relaunch after the wait, and the reason names the digest that the state
// still names.
#[test]
fn a_digest_that_the_lifecycle_never_names_ends_the_wait() {
    let resident = FakeResident::start(200, Value::Null, Some("c".repeat(64)), Some("c".repeat(64)), 0);
    let reader = Reader::new(Client::new(resident.server.url(), TOKEN).unwrap(), Some(LAUNCH));
    let reason = await_bridge_plugin(&reader, &"a".repeat(64), Duration::from_millis(300)).unwrap_err();
    assert_eq!(
        reason,
        "the lifecycle state named no bridge plugin aaaaaaaaaaaa within 300 ms; the latest state names cccccccccccc"
    );
}

// The reload of Air posts the plugin id alone, with no staged directory, and waits until the lifecycle names the bridge
// plugin of before the reload again. Old class loaders are a note, because the next part counts them.
#[test]
fn a_reload_of_air_waits_for_the_same_bridge_plugin_and_counts_no_loader() {
    let digest = "d".repeat(64);
    let answer = json!({ "unloaded": true, "oldLoaders": 2, "loaded": true, "digest": null, "reason": null });
    let resident = FakeResident::start(200, answer, Some(digest.clone()), Some(digest.clone()), 1);
    let client = Client::new(resident.server.url(), TOKEN).unwrap();
    let mut notes = Vec::new();
    reload_air(&client, LAUNCH, Duration::from_secs(5), &mut |note| notes.push(note)).unwrap();
    assert_eq!(*resident.requests.lock().unwrap(), [json!({ "pluginId": AIR_PLUGIN_ID })]);
    assert_eq!(notes.len(), 2, "{notes:?}");
    assert!(
        notes[0].starts_with("reload-air: unloaded, 2 old class loader(s), loaded"),
        "{notes:?}"
    );
    assert!(
        notes[1].starts_with(&format!("reload-air: the lifecycle names the bridge plugin {}", &digest[..12])),
        "{notes:?}"
    );
}

// A refusal, an unload or a load that did not happen, and a lifecycle that names no bridge plugin each fail the reload
// of Air with the reason.
#[test]
fn a_reload_of_air_that_did_not_complete_fails_with_its_reason() {
    let digest = Some("d".repeat(64));
    for (status, answer, before, expected) in [
        (
            409,
            json!({ "unloaded": false, "oldLoaders": -1, "loaded": false, "digest": null, "reason": "a reload runs" }),
            digest.clone(),
            "the resident plugin refused the reload of Air: a reload runs",
        ),
        (
            200,
            json!({ "unloaded": false, "oldLoaders": -1, "loaded": false, "digest": null, "reason": "a dynamic plugin vetoed" }),
            digest.clone(),
            "the platform did not unload Air: a dynamic plugin vetoed",
        ),
        (
            200,
            json!({ "unloaded": true, "oldLoaders": 0, "loaded": false, "digest": null, "reason": "the load failed" }),
            digest,
            "the platform did not load Air again: the load failed",
        ),
        (
            200,
            json!({ "unloaded": true, "oldLoaders": 0, "loaded": true, "digest": null, "reason": null }),
            None,
            "the lifecycle names no bridge plugin before the reload of Air",
        ),
    ] {
        let resident = FakeResident::start(status, answer, before.clone(), before, 0);
        let client = Client::new(resident.server.url(), TOKEN).unwrap();
        let reason = reload_air(&client, LAUNCH, Duration::from_secs(5), &mut |_| {}).unwrap_err();
        assert_eq!(reason, expected);
    }
}
