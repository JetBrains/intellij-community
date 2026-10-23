use std::path::PathBuf;

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn launchd_loads_restarts_or_reloads() {
    assert_eq!(LaunchdStep::of(false, true), LaunchdStep::Bootstrap);
    assert_eq!(LaunchdStep::of(false, false), LaunchdStep::Bootstrap);
    assert_eq!(LaunchdStep::of(true, false), LaunchdStep::Kickstart);
    assert_eq!(LaunchdStep::of(true, true), LaunchdStep::Reload);
}

#[test]
fn the_agent_path_puts_the_tool_directories_first_once() {
    assert_eq!(
        agent_path(Path::new("/Users/me"), "/usr/bin:/Users/me/.local/bin:/opt/x::/bin"),
        "/Users/me/.local/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/opt/x:/bin:/usr/sbin:/sbin"
    );
}

#[test]
fn the_plist_runs_serve_on_the_port_and_escapes_its_strings() {
    let plist = plist_text(
        &PathBuf::from("/Users/a&b/.local/bin/yonderd"),
        7742,
        Path::new("/Users/a&b/Library/Logs/yonderd.log"),
        "/usr/bin",
    );
    assert!(plist.contains("<string>com.jetbrains.yonderd</string>"), "{plist}");
    assert!(
        plist.contains("<string>/Users/a&amp;b/.local/bin/yonderd</string>\n    <string>serve</string>\n    <string>--port</string>\n    <string>7742</string>"),
        "{plist}"
    );
    assert!(plist.contains("<key>KeepAlive</key>\n  <true/>"), "{plist}");
    assert!(plist.contains("<key>RunAtLoad</key>\n  <true/>"), "{plist}");
    assert!(!plist.contains("a&b"), "{plist}");
}

#[test]
fn a_setup_line_has_the_json_shape_of_the_spec() {
    let line = SetupLine {
        fingerprint: "ab".to_owned(),
        port: 7741,
        launchd: "restarted",
    };
    assert_eq!(
        serde_json::to_string(&line).expect("json"),
        r#"{"fingerprint":"ab","port":7741,"launchd":"restarted"}"#
    );
}

#[test]
fn the_listen_wait_ends_when_the_port_takes_connections_and_refuses_after_its_time() {
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("a port");
    let port = listener.local_addr().expect("the address").port();
    assert_eq!(wait_for_listen(port, Duration::from_secs(5)), Ok(()));
    drop(listener);
    // On macOS a child of another test thread can get the listening socket before its close-on-exec flag is set, and
    // the socket then lives as long as that child. The check starts once the port refuses.
    let closed = Instant::now();
    while TcpStream::connect(("127.0.0.1", port)).is_ok() {
        assert!(closed.elapsed() < Duration::from_secs(60), "the port still takes connections");
        std::thread::sleep(Duration::from_millis(50));
    }
    let started = Instant::now();
    let refused = wait_for_listen(port, Duration::from_millis(300)).expect_err("nothing listens");
    assert!(refused.contains("nothing listens on 127.0.0.1"), "{refused}");
    assert!(started.elapsed() < Duration::from_secs(3), "{:?}", started.elapsed());
}
