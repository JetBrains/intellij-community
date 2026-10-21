use std::cell::RefCell;
use std::fs;
use std::time::Duration;

use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::*;
use crate::lane::program::tests::FakeServices;

/// A host side over [`FakeServices`] whose restart performs `while_down` on `context`.
struct Side<'a> {
    services: &'a FakeServices,
    context: &'a Path,
    restarts: RefCell<u32>,
}

impl HostSide for Side<'_> {
    fn services(&self) -> &dyn ServiceControl {
        self.services
    }

    fn restart_ide(&mut self, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String> {
        *self.restarts.borrow_mut() += 1;
        while_down(self.context)
    }

    fn recycle_through_failed_reset(&mut self) -> Result<(), String> {
        Err("this host side holds no IDE to recycle".to_owned())
    }

    fn reload_air(&mut self) -> Result<(), String> {
        Err("this host side holds no IDE to reload".to_owned())
    }
}

fn perform_id(id: HostId, args: Value, values: &mut Values, side: &mut Side<'_>) -> Result<(), String> {
    let args: BTreeMap<String, Value> = serde_json::from_value(args).unwrap();
    perform(id, "label", &args, values, side)
}

// The registry ids arm and clear the faults of the fake registry: a held package answer for 120 s, and a 503 for the
// next package. The fault assertion holds only for the package fault.
#[test]
fn the_registry_ids_arm_hold_release_and_check_the_package_fault() {
    let services = FakeServices::default();
    let context = TempDir::new().unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let mut values = Values::default();
    perform_id(HostId::Action(HostAction::ConsentHoldDownload), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(
        *services.fault.lock().unwrap(),
        ("slow-package-response".to_owned(), None, Some(120_000))
    );
    let refused = perform_id(
        HostId::Assertion(HostAssertion::ConsentDownloadFaultArmed),
        json!({}),
        &mut values,
        &mut side,
    );
    assert_eq!(
        refused,
        Err("expected a package HTTP fault, found slow-package-response".to_owned())
    );
    perform_id(
        HostId::Action(HostAction::RegistryFailNextDownload),
        json!({}),
        &mut values,
        &mut side,
    )
    .unwrap();
    assert_eq!(*services.fault.lock().unwrap(), ("package-http-error".to_owned(), Some(503), None));
    perform_id(
        HostId::Assertion(HostAssertion::ConsentDownloadFaultArmed),
        json!({}),
        &mut values,
        &mut side,
    )
    .unwrap();
    perform_id(HostId::Action(HostAction::ReleaseDownload), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(services.fault.lock().unwrap().0, "none");
}

// The mark answers the count so far as `consentRegistryBaseline`, and the count reads the package requests after it
// only. Zero is read at once, and a wrong count names the request log.
#[test]
fn the_request_count_reads_the_package_requests_after_the_mark() {
    let services = FakeServices::default();
    services.paths.lock().unwrap().push("/packages/old.zip".to_owned());
    let context = TempDir::new().unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let mut values = Values::default();
    perform_id(HostId::Action(HostAction::RegistryMarkRequests), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(values.get(REGISTRY_BASELINE), Some(&json!(1)));
    services
        .paths
        .lock()
        .unwrap()
        .extend(["/registry.json".to_owned(), "/packages/new.zip".to_owned()]);
    perform_id(
        HostId::Assertion(HostAssertion::RegistryRequestCount),
        json!({"requests": 1}),
        &mut values,
        &mut side,
    )
    .unwrap();
    let refused = await_request_count(&services, 1, 0, Duration::ZERO).unwrap_err();
    assert!(
        refused.starts_with("expected 0 package request(s) after registry event 1, found 1\n"),
        "{refused}"
    );
    let missing = perform_id(
        HostId::Assertion(HostAssertion::RegistryRequestCount),
        json!({"requests": 0}),
        &mut Values::default(),
        &mut side,
    );
    assert_eq!(
        missing,
        Err("the instruction 'label' takes the value 'consentRegistryBaseline', and no earlier instruction answered it".to_owned())
    );
}

// The restart performs the operations of `whileDown` on the context while the IDE is down, answers each read in
// `whileDownReads`, and empties `whileDown`, so a later restart performs nothing twice.
#[test]
fn a_restart_performs_while_down_and_answers_the_reads() {
    let services = FakeServices::default();
    let context = TempDir::new().unwrap();
    let events = context.path().join("home/.junie/sessions/s/events.jsonl");
    fs::create_dir_all(events.parent().unwrap()).unwrap();
    fs::write(&events, "first\n").unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let relative = "home/.junie/sessions/s/events.jsonl";
    let mut values = Values::new([(
        WHILE_DOWN.to_owned(),
        json!([{"type": "append", "path": relative, "text": "second\n"}, {"type": "read", "path": relative}]),
    )]);
    perform_id(HostId::Action(HostAction::RestartIde), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(*side.restarts.borrow(), 1);
    assert_eq!(values.get(WHILE_DOWN_READS), Some(&json!({relative: "first\nsecond\n"})));
    assert_eq!(values.get(WHILE_DOWN), Some(&json!([])));
    perform_id(HostId::Action(HostAction::RestartIde), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(fs::read_to_string(&events).unwrap(), "first\nsecond\n");
}

// A file operation reaches files of the context only: no absolute path, no `..`, and no file that does not exist.
#[test]
fn a_file_operation_outside_the_context_or_on_a_missing_file_is_refused() {
    let context = TempDir::new().unwrap();
    let refused = |operations: Value| {
        let operations: Vec<ContextFileOp> = serde_json::from_value(operations).unwrap();
        apply_context_file_ops(context.path(), &operations).unwrap_err()
    };
    assert!(refused(json!([{"type": "read", "path": "../outside.xml"}])).contains("is not a file of the IDE context"));
    assert!(refused(json!([{"type": "read", "path": "/etc/hosts"}])).contains("is not a file of the IDE context"));
    assert!(refused(json!([{"type": "append", "path": "home/missing.jsonl", "text": "x"}])).contains("finds no file"));
}

// The authority ends at the first '/', '?' or '#'. A callback URL with a query and no path, which the IDE gets from
// the emulator, keeps its query in the target, and a fragment is no part of the target.
#[test]
fn a_url_with_a_query_and_no_path_splits_after_its_port() {
    let split = |url: &str| split_http_url(url).map(|(authority, target)| (authority.to_owned(), target));
    assert_eq!(
        split("http://localhost:62345?code=air-lane-authorization-code&state=s-1"),
        Ok((
            "localhost:62345".to_owned(),
            "/?code=air-lane-authorization-code&state=s-1".to_owned()
        ))
    );
    assert_eq!(
        split("http://127.0.0.1:1/oauth2/auth?x=1#fragment"),
        Ok(("127.0.0.1:1".to_owned(), "/oauth2/auth?x=1".to_owned()))
    );
    assert_eq!(split("http://127.0.0.1:1"), Ok(("127.0.0.1:1".to_owned(), "/".to_owned())));
    assert_eq!(split("http://#x"), Err("the URL http://#x names no address".to_owned()));
    assert_eq!(split("https://h:1/"), Err("the URL https://h:1/ is not http://".to_owned()));
}

/// Answers one request on `listener` with `answer`, and gives the request line.
fn serve_once(listener: std::net::TcpListener, answer: String) -> std::thread::JoinHandle<String> {
    std::thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut head = Vec::new();
        let mut byte = [0_u8; 1];
        while !head.ends_with(b"\r\n\r\n") && stream.read(&mut byte).unwrap() == 1 {
            head.push(byte[0]);
        }
        stream.write_all(answer.as_bytes()).unwrap();
        String::from_utf8_lossy(&head).lines().next().unwrap_or_default().to_owned()
    })
}

// `account-approve-sign-in` follows the redirect of the emulator to the callback of the IDE. The callback URL has a
// query and no path, and the callback gets that query.
#[test]
fn the_sign_in_approval_follows_the_redirect_to_a_callback_with_a_query_and_no_path() {
    let emulator = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let callback = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let base_url = format!("http://{}", emulator.local_addr().unwrap());
    let callback_url = format!("http://localhost:{}?code=c-1&state=s-1", callback.local_addr().unwrap().port());
    let emulated = serve_once(
        emulator,
        format!("HTTP/1.1 302 Found\r\nLocation: {callback_url}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"),
    );
    let called = serve_once(
        callback,
        "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".to_owned(),
    );
    let services = FakeServices::default();
    *services.cloud_base_url.lock().unwrap() = base_url.clone();
    let context = TempDir::new().unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let mut values = Values::default();
    values.set(SIGN_IN_URL, json!(format!("{base_url}/oauth2/auth?client_id=ide")));
    perform_id(HostId::Action(HostAction::AccountApproveSignIn), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(emulated.join().unwrap(), "GET /oauth2/auth?client_id=ide HTTP/1.1");
    assert_eq!(called.join().unwrap(), "GET /?code=c-1&state=s-1 HTTP/1.1");
}

// A sign-in URL of another server is refused before any request, because the IDE must open the emulated JetBrains
// Account.
#[test]
fn a_sign_in_url_of_another_server_is_refused() {
    assert_eq!(
        approve_sign_in("http://127.0.0.1:9/oauth2/auth", "http://127.0.0.1:8"),
        Err("the IDE opened http://127.0.0.1:9/oauth2/auth, which is not the emulated JetBrains Account at http://127.0.0.1:8".to_owned())
    );
}
