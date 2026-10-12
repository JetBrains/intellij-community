use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;

fn state() -> HostState {
    HostState {
        run_id: "run-ui-daemon-1".to_owned(),
        port: 27_100,
        token: "token".to_owned(),
        worker: "avl-linux-1".to_owned(),
        daemon_boot_stamp: "boot-1".to_owned(),
        runtime_digest: "r".repeat(64),
        launch_digest: "l".repeat(64),
        last_product_digest: "p".repeat(64),
        last_mount_digest: "m".repeat(64),
    }
}

// The file is private two-space JSON in the field order, with a trailing newline, and reads back as written.
#[tokio::test]
async fn a_written_state_is_private_pretty_json_with_a_newline() {
    use std::os::unix::fs::PermissionsExt;
    let fixture = Fixture::new().await;
    state().write(&fixture.settings, &fixture.worker).unwrap();
    let path = HostState::path(&fixture.settings, &fixture.worker);
    let written = std::fs::read_to_string(&path).unwrap();
    let r = "r".repeat(64);
    let l = "l".repeat(64);
    let p = "p".repeat(64);
    let m = "m".repeat(64);
    assert_eq!(
        written,
        format!(
            "{{\n  \"runId\": \"run-ui-daemon-1\",\n  \"port\": 27100,\n  \"token\": \"token\",\n  \"worker\": \
             \"avl-linux-1\",\n  \"daemonBootStamp\": \"boot-1\",\n  \"runtimeDigest\": \"{r}\",\n  \
             \"launchDigest\": \"{l}\",\n  \"lastProductDigest\": \"{p}\",\n  \"lastMountDigest\": \"{m}\"\n}}\n"
        )
    );
    assert_eq!(std::fs::metadata(&path).unwrap().permissions().mode() & 0o777, 0o600);
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state()));
}

// An unknown field is ignored; a state missing any field, or unreadable, is no daemon at all.
#[tokio::test]
async fn a_state_reads_only_with_every_field() {
    let fixture = Fixture::new().await;
    let path = HostState::path(&fixture.settings, &fixture.worker);
    let mut document = serde_json::to_value(state()).unwrap();
    document
        .as_object_mut()
        .unwrap()
        .insert("somethingNewer".to_owned(), serde_json::json!(true));
    std::fs::write(&path, document.to_string()).unwrap();
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state()));

    for required in [
        "runId",
        "port",
        "token",
        "worker",
        "daemonBootStamp",
        "runtimeDigest",
        "launchDigest",
        "lastProductDigest",
        "lastMountDigest",
    ] {
        let mut document = serde_json::to_value(state()).unwrap();
        document.as_object_mut().unwrap().remove(required);
        std::fs::write(&path, document.to_string()).unwrap();
        assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None, "{required}");
    }
    std::fs::write(&path, "{").unwrap();
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
    HostState::remove(&fixture.settings, &fixture.worker).unwrap();
    HostState::remove(&fixture.settings, &fixture.worker).unwrap();
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
}
