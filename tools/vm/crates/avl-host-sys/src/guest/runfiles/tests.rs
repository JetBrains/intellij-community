use avl_base::GuestOs;
use avl_wire::runfiles::{RunfilesTreeRequest, RunfilesTreeResult};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::guest::testing::{FakeChannel, Host, prose};
use crate::runfiles::manifest_paths;

/// A host whose descriptor has a MANIFEST under its checkout, and the runfiles of it.
fn manifest_host() -> (Host, HostRunfiles) {
    let host = Host::new(GuestOs::Linux);
    let descriptor = host.repo.join("ui_daemon.runtime.json");
    let target = host.repo.join("lib").join("a.jar");
    std::fs::write(&manifest_paths(&descriptor)[0], format!("_main/lib/a.jar {}\n", target.display())).unwrap();
    let runfiles = HostRunfiles::of(&descriptor).unwrap();
    (host, runfiles)
}

fn reply(root: &str, reused: bool) -> String {
    json!({
        "schemaVersion": 1,
        "ok": true,
        "command": "runfiles-tree",
        "data": RunfilesTreeResult {
            root: root.to_owned(),
            digest: "d".to_owned(),
            entries: 1,
            reused,
            removed: Vec::new(),
        },
    })
    .to_string()
}

/// The verb gets the MANIFEST by its guest path, the table of the shares, and the destination, on its stdin, as the
/// worker user.
#[tokio::test]
async fn a_manifest_is_sent_to_the_verb_and_its_root_is_checked() {
    let (host, runfiles) = manifest_host();
    let settings = &host.settings;
    let expected = runfiles.guest_root(settings).unwrap();
    let channel = FakeChannel::spoke("air-docker-1", reply(&expected, false));
    host.guest(&channel).ensure_runfiles_tree(&runfiles, &expected).await.unwrap();

    let calls = channel.calls();
    assert_eq!(calls.len(), 1, "{:?}", channel.lines());
    assert_eq!(
        calls[0].line(),
        format!("/usr/bin/sudo -H -u {} {} runfiles-tree", settings.vm_user, settings.vm_agent)
    );
    let request: RunfilesTreeRequest = serde_json::from_slice(calls[0].options.stdin.as_deref().unwrap()).unwrap();
    let paths = GuestPaths::of(settings).unwrap();
    assert_eq!(
        request,
        RunfilesTreeRequest {
            schema_version: SCHEMA_VERSION,
            manifest: paths
                .to_guest(&manifest_paths(&host.repo.join("ui_daemon.runtime.json"))[0])
                .unwrap(),
            path_map: paths.map().clone(),
            destination: guest_runfiles_destination(settings),
        }
    );
}

/// The trees the verb removed are noted, as the collection of old runtimes is.
#[tokio::test]
async fn the_trees_the_verb_removed_are_noted() {
    let (host, runfiles) = manifest_host();
    let expected = runfiles.guest_root(&host.settings).unwrap();
    let answer = json!({
        "schemaVersion": 1,
        "ok": true,
        "command": "runfiles-tree",
        "data": {
            "root": expected,
            "digest": "d",
            "entries": 1,
            "reused": false,
            "removed": ["/data/runfiles/old-1", "/data/runfiles/old-2"],
        },
    });
    let channel = FakeChannel::spoke("air-docker-1", answer.to_string());
    let (reporter, output) = prose();
    host.guest_reporting(&channel, &reporter)
        .ensure_runfiles_tree(&runfiles, &expected)
        .await
        .unwrap();
    assert!(output.text().contains("removed 2 old runfiles trees"), "{}", output.text());
}

/// A guest that built its tree anywhere but the root the host named is refused: the launch digest and the JVM
/// flags already name that root.
#[tokio::test]
async fn a_tree_at_another_root_is_refused() {
    let (host, runfiles) = manifest_host();
    let expected = runfiles.guest_root(&host.settings).unwrap();
    let channel = FakeChannel::spoke("air-docker-1", reply("/data/runfiles/other", true));
    let refusal = host.guest(&channel).ensure_runfiles_tree(&runfiles, &expected).await.unwrap_err();
    assert_eq!(refusal.code, "guest_runfiles_mismatch");

    let garbled = FakeChannel::spoke("air-docker-1", "not json");
    let refusal = host.guest(&garbled).ensure_runfiles_tree(&runfiles, &expected).await.unwrap_err();
    assert_eq!(refusal.code, "guest_runfiles_protocol");
}

/// A tree Bazel built needs no guest call: the guest opens it through its share.
#[tokio::test]
async fn a_tree_needs_no_guest_call() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::new("air-docker-1");
    let tree = HostRunfiles::Tree(host.repo.join("ui_daemon.runtime.json.runfiles"));
    host.guest(&channel).ensure_runfiles_tree(&tree, "/unused").await.unwrap();
    assert!(channel.calls().is_empty());
}
