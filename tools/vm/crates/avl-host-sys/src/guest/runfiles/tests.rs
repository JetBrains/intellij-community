use avl_base::GuestOs;
use avl_wire::runfiles::{RunfilesTreeRequest, RunfilesTreeResult, SCHEMA_VERSION, StagedRunfile, split_request_stdin};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::guest::testing::{FakeChannel, Host, prose};
use crate::paths::GuestPaths;
use crate::proc::Captured;

/// A tree of one share line and one staged runfile, whose host file lies in the checkout of `host`.
fn tree(host: &Host) -> GuestRunfilesTree {
    let staged_file = host.repo.join("pnpm-lock.yaml");
    std::fs::write(&staged_file, "lock: 1\n").unwrap();
    let request = RunfilesTreeRequest {
        schema_version: SCHEMA_VERSION,
        manifest_text: "_main/lib/a.jar /bazel/out/a.jar\n".to_owned(),
        path_map: GuestPaths::of(&host.settings).unwrap().map().clone(),
        destination: "/data/runfiles".to_owned(),
        staged: vec![StagedRunfile {
            path: "_main/pnpm-lock.yaml".to_owned(),
            sha256: "ab".to_owned(),
            size: 8,
        }],
        with_bytes: false,
        copy_package_stores: false,
    };
    GuestRunfilesTree {
        root: "/data/runfiles/expected".to_owned(),
        request,
        staged_files: vec![staged_file],
    }
}

fn reply(root: &str, reused: bool) -> Captured {
    Captured {
        stdout: json!({
            "schemaVersion": 1,
            "ok": true,
            "command": "runfiles-tree",
            "data": RunfilesTreeResult {
                root: root.to_owned(),
                digest: "d".to_owned(),
                entries: 2,
                reused,
                removed: Vec::new(),
            },
        })
        .to_string(),
        ..Captured::default()
    }
}

fn bytes_missing() -> Captured {
    Captured {
        exit_code: 70,
        stderr: json!({"schemaVersion": 1, "ok": false, "command": "runfiles-tree",
            "error": {"code": STAGED_BYTES_MISSING_CODE, "message": "no tree"}})
        .to_string(),
        ..Captured::default()
    }
}

/// A channel that answers its calls with `answers`, one after the other.
fn in_turn(worker: &str, answers: Vec<Captured>) -> FakeChannel {
    let answers = std::sync::Mutex::new(std::collections::VecDeque::from(answers));
    FakeChannel::answering(worker, move |_| {
        Ok(answers.lock().unwrap().pop_front().expect("an answer for each call"))
    })
}

/// The request of one call, and the bytes after its JSON line.
fn sent(call: &crate::guest::testing::Call) -> (RunfilesTreeRequest, Vec<u8>) {
    let stdin = call.options.stdin.as_deref().unwrap();
    let (line, bytes) = split_request_stdin(stdin);
    (serde_json::from_slice(line).unwrap(), bytes.to_vec())
}

/// A guest that holds the tree reuses it from the first request, which carries no bytes, as the worker user.
#[tokio::test]
async fn a_tree_the_guest_holds_costs_one_request_without_bytes() {
    let host = Host::new(GuestOs::Linux);
    let tree = tree(&host);
    let channel = in_turn("air-docker-1", vec![reply(&tree.root, true)]);
    host.guest(&channel).ensure_runfiles_tree(&tree).await.unwrap();

    let calls = channel.calls();
    assert_eq!(calls.len(), 1, "{:?}", channel.lines());
    let settings = &host.settings;
    assert_eq!(
        calls[0].line(),
        format!("/usr/bin/sudo -H -u {} {} runfiles-tree", settings.vm_user, settings.vm_agent)
    );
    let (request, bytes) = sent(&calls[0]);
    assert_eq!(request, tree.request);
    assert_eq!(bytes, b"");
}

/// A guest without the tree refuses the first request, and the second one carries the bytes of each staged runfile.
#[tokio::test]
async fn a_guest_without_the_tree_gets_the_bytes_in_a_second_request() {
    let host = Host::new(GuestOs::Linux);
    let tree = tree(&host);
    let channel = in_turn("air-docker-1", vec![bytes_missing(), reply(&tree.root, false)]);
    host.guest(&channel).ensure_runfiles_tree(&tree).await.unwrap();

    let calls = channel.calls();
    assert_eq!(calls.len(), 2, "{:?}", channel.lines());
    let (request, bytes) = sent(&calls[1]);
    assert!(request.with_bytes);
    assert_eq!(bytes, b"lock: 1\n");
}

/// The trees the verb removed are noted, as the collection of old runtimes is.
#[tokio::test]
async fn the_trees_the_verb_removed_are_noted() {
    let host = Host::new(GuestOs::Linux);
    let tree = tree(&host);
    let answer = json!({
        "schemaVersion": 1,
        "ok": true,
        "command": "runfiles-tree",
        "data": {
            "root": tree.root,
            "digest": "d",
            "entries": 1,
            "reused": false,
            "removed": ["/data/runfiles/old-1", "/data/runfiles/old-2"],
        },
    });
    let channel = FakeChannel::spoke("air-docker-1", answer.to_string());
    let (reporter, output) = prose();
    host.guest_reporting(&channel, &reporter).ensure_runfiles_tree(&tree).await.unwrap();
    assert!(output.text().contains("removed 2 old runfiles trees"), "{}", output.text());
}

/// A guest that built its tree anywhere but the root the host named is refused: the launch digest and the JVM
/// flags already name that root.
#[tokio::test]
async fn a_tree_at_another_root_is_refused() {
    let host = Host::new(GuestOs::Linux);
    let tree = tree(&host);
    let channel = in_turn("air-docker-1", vec![reply("/data/runfiles/other", true)]);
    let refusal = host.guest(&channel).ensure_runfiles_tree(&tree).await.unwrap_err();
    assert_eq!(refusal.code, "guest_runfiles_mismatch");

    let garbled = FakeChannel::spoke("air-docker-1", "not json");
    let refusal = host.guest(&garbled).ensure_runfiles_tree(&tree).await.unwrap_err();
    assert_eq!(refusal.code, "guest_runfiles_protocol");
}
