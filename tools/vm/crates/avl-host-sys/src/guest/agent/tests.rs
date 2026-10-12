use std::path::Path;

use avl_base::{GuestArch, GuestOs};
use pretty_assertions::assert_eq;

use super::*;
use crate::guest::testing::{FakeBazel, FakeChannel, ForbiddenBazel, Host, failed, has, prose, said};
use crate::proc::Captured;

fn receipt(host: &Host, source: &Path, content: &str) -> AgentReceipt {
    AgentReceipt {
        schema_version: 1,
        worker: "air-docker-1".to_owned(),
        guest_os: "linux".to_owned(),
        host_repo: host.repo.to_string_lossy().into_owned(),
        source: source.to_string_lossy().into_owned(),
        source_signature: host_file_signature(source),
        sha256: agent_digest(content.as_bytes()),
    }
}

// A transitioned binary's output *is* a link into another configuration directory, so an `lstat`-based signature
// describes the link and never the bytes. That check silently degraded to "always reinstall", and the bug reached
// hardware after passing its whole suite.
#[cfg(unix)]
#[test]
fn host_file_signature_follows_the_symlink() {
    let directory = tempfile::tempdir().unwrap();
    let target = directory.path().join("vm-guest-agent");
    let link = directory.path().join("link-to-agent");
    std::fs::write(&target, "agent bytes").unwrap();
    std::os::unix::fs::symlink(&target, &link).unwrap();
    assert_eq!(host_file_signature(&link), host_file_signature(&target));
    assert_eq!(host_file_signature(&target).split(':').count(), 4);
    // A link repointed at another file still invalidates a receipt, because the identity is the target's inode.
    let other = directory.path().join("other");
    std::fs::write(&other, "other bytes").unwrap();
    std::fs::remove_file(&link).unwrap();
    std::os::unix::fs::symlink(&other, &link).unwrap();
    assert_ne!(host_file_signature(&link), host_file_signature(&target));
}

// Two files never share a signature, because the identity is in it, and the size is the second field.
#[test]
fn host_file_signature_names_one_file() {
    let directory = tempfile::tempdir().unwrap();
    let first = directory.path().join("first");
    let second = directory.path().join("second");
    std::fs::write(&first, "agent bytes").unwrap();
    std::fs::write(&second, "agent bytes").unwrap();
    let signature = host_file_signature(&first);
    let fields: Vec<&str> = signature.split(':').collect();
    assert_eq!(fields.len(), 4, "{signature}");
    assert_eq!(fields[1], "11", "{signature}");
    assert_eq!(signature, host_file_signature(&first));
    assert_ne!(signature, host_file_signature(&second));
}

// Empty is deliberately never equal to a recorded signature, so a `bazel clean` invalidates a receipt rather than
// matching it.
#[test]
fn host_file_signature_is_empty_for_anything_that_is_not_a_file() {
    let directory = tempfile::tempdir().unwrap();
    assert_eq!(host_file_signature(&directory.path().join("missing")), "");
    assert_eq!(host_file_signature(directory.path()), "");
}

// One guest round-trip (~93 ms) replaces both halves of an install that used to cost 21.0 s of a warm daemon
// restart - 15.4 s of it pushing a binary the guest already had.
#[tokio::test]
async fn install_agent_skips_when_the_guest_already_runs_these_bytes() {
    let mut host = Host::new(GuestOs::Linux);
    let source = host.agent_source("agent bytes");
    host.settings.vm_agent_source = Some(source.clone());
    write_agent_receipt(&host.settings, "air-docker-1", &receipt(&host, &source, "agent bytes")).unwrap();
    let contract = format!(r#"{{"selfDigest":"{}"}}"#, agent_digest(b"agent bytes"));
    let channel = FakeChannel::spoke("air-docker-1", contract);
    let bazel = FakeBazel::default();
    host.guest(&channel).install_agent(&bazel).await.unwrap();
    // Exactly the one `contract` round-trip: no push, and - the expensive half - no resolution either, which is only
    // skippable because the comparison is against a host-side receipt rather than against the binary.
    let calls = channel.calls();
    assert_eq!(calls.len(), 1, "{:?}", channel.lines());
    assert!(has(&calls[0].argv, "contract"));
    assert_eq!(bazel.state().queries, 0);
}

#[tokio::test]
async fn install_agent_renames_onto_the_running_binary_and_records_what_it_pushed() {
    let mut host = Host::new(GuestOs::Linux);
    let source = host.agent_source("new agent bytes");
    host.settings.vm_agent_source = Some(source.clone());
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel).install_agent(&ForbiddenBazel).await.unwrap();
    let settings = &host.settings;
    let as_user = format!("/usr/bin/sudo -H -u {}", settings.vm_user);
    let incoming = format!("{}.incoming", settings.vm_agent);
    // Staged beside the destination and renamed onto it. `tee` opens with O_TRUNC, and the installed agent is being
    // *executed* whenever a daemon is up. No `contract` round-trip: with no receipt there is nothing to compare the
    // guest's answer against.
    assert_eq!(
        channel.lines(),
        [
            format!("{as_user} /usr/bin/tee {incoming}"),
            format!("{as_user} /bin/chmod 700 {incoming}"),
            format!("{as_user} /bin/mv -f {incoming} {}", settings.vm_agent),
        ]
    );
    assert_eq!(channel.calls()[0].options.stdin.as_deref(), Some(&b"new agent bytes"[..]));
    let installed = installed_agent_receipt(settings, "air-docker-1", &host.quiet).unwrap();
    assert_eq!(installed, receipt(&host, &source, "new agent bytes"));
}

// The receipt says which checkout installed the bytes, and a receipt from another one is not evidence about this
// controller's agent. Measured on air-linux-2 on 2026-08-24: two checkouts on one machine share the pool and
// therefore this file, the older checkout's receipt validated, and the newer controller drove the older agent until
// it asked for `launch-prep` and got that agent's usage exit.
#[test]
fn installed_agent_receipt_rejects_a_receipt_from_another_checkout() {
    let host = Host::new(GuestOs::Linux);
    let source = host.agent_source("agent bytes");
    let repo = host.repo.to_string_lossy().into_owned();
    let mut foreign = receipt(&host, &source, "agent bytes");
    foreign.host_repo = format!("{repo}-other");
    write_agent_receipt(&host.settings, "air-docker-1", &foreign).unwrap();
    let (reporter, said) = prose();
    assert_eq!(installed_agent_receipt(&host.settings, "air-docker-1", &reporter), None);
    // The rejection names both checkouts, because the whole difficulty of the original failure was that neither was
    // visible: the symptom was one exit 64 from a verb the other checkout's agent did not have.
    let text = said.text();
    for wanted in [format!("{repo}-other"), repo.clone(), "air-docker-1".to_owned()] {
        assert!(text.contains(&wanted), "no {wanted:?} in {text:?}");
    }
    // Every other field of that receipt is in order, so this is the only thing that rejected it.
    foreign.host_repo = repo;
    write_agent_receipt(&host.settings, "air-docker-1", &foreign).unwrap();
    let (reporter, said) = prose();
    assert!(installed_agent_receipt(&host.settings, "air-docker-1", &reporter).is_some());
    assert_eq!(said.text(), "");
}

// A source rewritten while it was being read would otherwise be recorded with the new file's identity and the old
// file's digest, and every later start would skip an install it needed. A source that vanishes between the read and
// the second signature gets no receipt - and a receipt left from before is discarded, not kept.
#[tokio::test]
async fn install_agent_writes_no_receipt_for_an_absent_source() {
    let mut host = Host::new(GuestOs::Linux);
    let source = host.agent_source("agent bytes");
    host.settings.vm_agent_source = Some(source.clone());
    let mut stale = receipt(&host, &source, "older bytes");
    stale.source_signature = "1:2:3:4".to_owned();
    write_agent_receipt(&host.settings, "air-docker-1", &stale).unwrap();
    let vanishing = source.clone();
    let channel = FakeChannel::answering("air-docker-1", move |argv| {
        if has(argv, "/usr/bin/tee") {
            std::fs::remove_file(&vanishing).unwrap();
        }
        Ok(Captured::default())
    });
    host.guest(&channel).install_agent(&ForbiddenBazel).await.unwrap();
    assert!(
        !agent_receipt_path(&host.settings, "air-docker-1").exists(),
        "a receipt survived an unstable source"
    );
}

#[tokio::test]
async fn install_agent_refuses_a_source_that_is_not_there() {
    let mut host = Host::new(GuestOs::Linux);
    host.settings.vm_agent_source = Some(host.dir().join("never-built"));
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).install_agent(&ForbiddenBazel).await.unwrap_err();
    assert_eq!(refusal.code, "guest_agent_missing");
    assert!(channel.calls().is_empty());
}

// The path is read out of the target's own `DefaultInfo`, and the *last* non-empty line is the answer, so anything
// Bazel prints ahead of it cannot be mistaken for one.
#[tokio::test]
async fn resolving_the_agent_takes_the_last_cquery_line() {
    let host = Host::new(GuestOs::Linux);
    let root = tempfile::tempdir().unwrap();
    let relative = "bazel-out/linux-arm64/vm-guest-agent";
    std::fs::create_dir_all(root.path().join("bazel-out/linux-arm64")).unwrap();
    std::fs::write(root.path().join(relative), "cross built").unwrap();
    let bazel = FakeBazel {
        stdout: format!("Loading: 0 packages loaded\n\n{relative}\n"),
        root: root.path().to_owned(),
        ..FakeBazel::default()
    };
    let channel = FakeChannel::new("air-docker-1");
    host.guest(&channel).install_agent(&bazel).await.unwrap();
    let query = bazel.state().last_query;
    assert_eq!(query[0], "cquery");
    assert!(has(&query, "--output=starlark"), "{query:?}");
    assert_eq!(query.last().unwrap(), "@community//tools/vm:vm-guest-agent-linux-arm64");
    let installed = installed_agent_receipt(&host.settings, "air-docker-1", &host.quiet).unwrap();
    assert_eq!(installed.source, root.path().join(relative).to_string_lossy());
    // Memoized: the answer cannot change under a running controller, because nothing here builds it. The second
    // install is skipped by its receipt only if the guest agrees, which this one does not, so it resolves again -
    // from the memo.
    host.guest(&channel).install_agent(&bazel).await.unwrap();
    let state = bazel.state();
    assert_eq!((state.queries, state.root_requests), (1, 1));
}

// Resolution never builds: a `bazel build` from inside a lease operation would spend minutes while holding the lease
// lock, so a missing binary is a refusal naming the one command that fixes it.
#[tokio::test]
async fn resolving_the_agent_refuses_with_the_build_to_run() {
    let host = Host::new(GuestOs::Linux);
    let root = tempfile::tempdir().unwrap();
    let bazel = FakeBazel {
        stdout: "bazel-out/linux-arm64/vm-guest-agent\n".to_owned(),
        root: root.path().to_owned(),
        ..FakeBazel::default()
    };
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).install_agent(&bazel).await.unwrap_err();
    assert_eq!(refusal.code, "guest_agent_missing");
    assert!(
        refusal
            .message
            .contains("./bazel.cmd build @community//tools/vm:vm-guest-agent-linux-arm64"),
        "{}",
        refusal.message
    );
}

#[tokio::test]
async fn resolving_the_agent_refuses_an_empty_cquery_answer() {
    let host = Host::new(GuestOs::Linux);
    let bazel = FakeBazel {
        stdout: "\n\n".to_owned(),
        root: host.dir().to_owned(),
        ..FakeBazel::default()
    };
    let channel = FakeChannel::new("air-docker-1");
    let refusal = host.guest(&channel).install_agent(&bazel).await.unwrap_err();
    assert_eq!(refusal.code, "guest_agent_unresolved");
}

// Every way of not knowing means "install it": an agent too old to report a digest, a reply that is not JSON, an
// exec that failed.
#[tokio::test]
async fn agent_self_digest_is_empty_for_every_way_of_not_knowing() {
    let host = Host::new(GuestOs::Linux);
    let cases: [(&str, Captured); 5] = [
        ("the exec failed", failed(127, "")),
        ("the reply is prose", said("command not found\n")),
        ("there is no digest", said(r#"{"schemaVersion":1,"phases":[]}"#)),
        ("the digest is short", said(r#"{"selfDigest":"abc"}"#)),
        // Never a number: a digest routed through a JSON number rounds, and a rounded digest compares equal to
        // bytes it does not describe.
        ("the digest is a number", said(r#"{"selfDigest":12345}"#)),
    ];
    for (name, answer) in cases {
        let channel = FakeChannel::answering("air-docker-1", move |_| Ok(answer.clone()));
        assert_eq!(host.guest(&channel).agent_self_digest().await, None, "{name}");
    }
    // The Rust agent's own `contract` reply is read too.
    let digest = agent_digest(b"x");
    let contract = serde_json::to_string(&avl_wire::supervisor::Contract::current(&digest)).unwrap();
    let channel = FakeChannel::spoke("air-docker-1", contract);
    assert_eq!(host.guest(&channel).agent_self_digest().await, Some(digest));
}

// Every way of not knowing still means "install it" - and the reason is visible. This is asked only when a receipt
// says this controller already installed an agent here, so a `contract` that fails is the agent this controller put
// there refusing to say what it is.
#[tokio::test]
async fn agent_self_digest_reports_why_it_could_not_ask() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(64, "Usage:\n  vm-guest-agent start --root DIR\n")));
    let (reporter, said) = prose();
    let guest = host.guest_reporting(&channel, &reporter);
    assert_eq!(guest.agent_self_digest().await, None);
    let text = said.text();
    // The agent and its exit code, not the sudo the call was re-targeted with.
    for fragment in ["contract", "air-docker-1", "exited with 64", "reinstalling"] {
        assert!(text.contains(fragment), "no {fragment:?} in {text}");
    }
    assert!(!text.contains("sudo"), "{text}");
    // Bounded by its own timeout, named for the guest.
    let options = channel.calls()[0].options.clone();
    assert_eq!(options.timeout_code, Some("guest_agent_timeout"));
}

#[test]
fn installed_agent_receipt_rejects_a_receipt_for_another_guest() {
    let host = Host::new(GuestOs::Linux);
    let source = host.agent_source("agent bytes");
    let mut other_guest = receipt(&host, &source, "agent bytes");
    other_guest.guest_os = "macos".to_owned();
    write_agent_receipt(&host.settings, "air-docker-1", &other_guest).unwrap();
    // The binary is per *guest* platform: a darwin build in a Linux worker would not run at all.
    assert_eq!(installed_agent_receipt(&host.settings, "air-docker-1", &host.quiet), None);
    // A `bazel clean` invalidates the receipt rather than matching it.
    let mut cleaned = receipt(&host, &source, "agent bytes");
    cleaned.source = host.dir().join("cleaned-away").to_string_lossy().into_owned();
    cleaned.source_signature = "1:2:3:4".to_owned();
    write_agent_receipt(&host.settings, "air-docker-1", &cleaned).unwrap();
    assert_eq!(installed_agent_receipt(&host.settings, "air-docker-1", &host.quiet), None);
}

// The labels are the root `avl_binary` targets, one file each: the lane builds exactly these, and the install reads
// exactly these.
#[test]
fn the_agent_labels_are_the_root_single_file_binaries() {
    for arch in [GuestArch::Arm64, GuestArch::X86_64] {
        assert_eq!(
            agent_label(GuestOs::Macos, arch),
            "@community//tools/vm:vm-guest-agent-darwin-arm64"
        );
    }
    assert_eq!(
        agent_label(GuestOs::Linux, GuestArch::Arm64),
        "@community//tools/vm:vm-guest-agent-linux-arm64"
    );
    assert_eq!(
        agent_label(GuestOs::Linux, GuestArch::X86_64),
        "@community//tools/vm:vm-guest-agent-linux-x86_64"
    );
}

// The receipt is one camelCase line, and it reads back as written.
#[test]
fn an_agent_receipt_is_one_camel_case_line() {
    let host = Host::new(GuestOs::Linux);
    let written = AgentReceipt {
        schema_version: 1,
        worker: "air-docker-1".to_owned(),
        guest_os: "linux".to_owned(),
        host_repo: "/repo".to_owned(),
        source: "/out/agent".to_owned(),
        source_signature: "1:2:3:4".to_owned(),
        sha256: "ab".repeat(32),
    };
    write_agent_receipt(&host.settings, "air-docker-1", &written).unwrap();
    let path = agent_receipt_path(&host.settings, "air-docker-1");
    assert_eq!(
        std::fs::read_to_string(&path).unwrap(),
        format!(
            r#"{{"schemaVersion":1,"worker":"air-docker-1","guestOs":"linux","hostRepo":"/repo","source":"/out/agent","sourceSignature":"1:2:3:4","sha256":"{}"}}"#,
            "ab".repeat(32)
        ) + "\n"
    );
    let read: AgentReceipt = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
    assert_eq!(read, written);
}
