use std::sync::Arc;

use avl_base::GuestOs;
use pretty_assertions::assert_eq;

use super::*;
use crate::guest::testing::{FakeChannel, Host, Peers, failed};

fn peers(channel: &Arc<FakeChannel>) -> Peers {
    Peers(vec![("air-docker-2".to_owned(), channel.clone() as Arc<dyn Channel>)])
}

#[tokio::test]
async fn ensure_unique_ssh_host_keys_reads_peers_rather_than_regenerating() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", "regenerated\nSHA256:AAAA\n");
    let peer = Arc::new(FakeChannel::spoke("air-docker-2", "SHA256:BBBB\n"));
    let fingerprint = host.guest(&channel).ensure_unique_ssh_host_keys(&peers(&peer)).await.unwrap();
    // The last line, because the guest script prints what it did before it prints the answer.
    assert_eq!(fingerprint, "SHA256:AAAA");
    let marker = &host.settings.vm_ssh_host_key_fingerprint;
    assert_eq!(
        channel.lines(),
        [format!("/usr/bin/sudo -H /usr/local/sbin/air-ensure-ssh-host-keys {marker}")]
    );
    // Read, never regenerated: a peer with a live run must not have its host identity changed underneath it.
    assert_eq!(peer.lines(), [format!("/usr/bin/sudo /bin/cat {marker}")]);
}

#[tokio::test]
async fn ensure_unique_ssh_host_keys_refuses_a_shared_identity() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", "SHA256:SAME\n");
    let peer = Arc::new(FakeChannel::spoke("air-docker-2", "SHA256:SAME\n"));
    let refusal = host.guest(&channel).ensure_unique_ssh_host_keys(&peers(&peer)).await.unwrap_err();
    assert_eq!(refusal.code, "ssh_host_key_collision");
    assert!(refusal.message.contains("air-docker-2"), "{}", refusal.message);
}

// A running peer with no readiness marker is a refusal and not a skip: nothing has established that the two workers
// differ.
#[tokio::test]
async fn ensure_unique_ssh_host_keys_refuses_a_peer_with_no_marker() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", "SHA256:AAAA\n");
    let peer = Arc::new(FakeChannel::answering("air-docker-2", |_| Ok(failed(1, ""))));
    let refusal = host.guest(&channel).ensure_unique_ssh_host_keys(&peers(&peer)).await.unwrap_err();
    assert_eq!(refusal.code, "ssh_host_key_peer_not_ready");
}

#[tokio::test]
async fn ensure_unique_ssh_host_keys_skips_a_stopped_peer() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", "SHA256:AAAA\n");
    host.guest(&channel).ensure_unique_ssh_host_keys(&Peers::default()).await.unwrap();
}

// Two empty answers would compare equal and be reported as a shared identity that does not exist.
#[test]
fn parse_ssh_host_key_fingerprint_refuses_anything() {
    for refused in ["", "\n", "not a fingerprint", "SHA256:", "MD5:aa:bb", "SHA256:AA\nsaid more"] {
        assert_eq!(
            parse_ssh_host_key_fingerprint("air-docker-1", refused).unwrap_err().code,
            "ssh_host_key_invalid",
            "{refused:?}"
        );
    }
    assert_eq!(
        parse_ssh_host_key_fingerprint("air-docker-1", "made keys\r\nSHA256:ab+/=\r\n").unwrap(),
        "SHA256:ab+/="
    );
}
