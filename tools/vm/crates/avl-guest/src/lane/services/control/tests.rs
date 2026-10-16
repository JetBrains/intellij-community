use std::io;
use std::net::TcpStream;
use std::process::Command;

use pretty_assertions::assert_eq;

use crate::lane::services::feedback;
use crate::lane::services::testing::{self, TOKEN};

use super::TOKEN_HEADER;

#[test]
fn a_control_request_without_the_token_or_with_another_one_is_refused() {
    let service = feedback::start(0, TOKEN).unwrap();
    let address = service.address();
    for headers in [vec![], vec![(TOKEN_HEADER, "another-token")]] {
        let answer = testing::call(address, "GET", "/_lane/feedback/submissions", &headers, b"");
        assert_eq!(answer.status, 401);
        assert_eq!(answer.header("Content-Type"), Some("application/json"));
        assert_eq!(answer.json()["error"]["code"], "token_refused");
    }
    // The token rule comes before the route, so an unknown route without the token reveals nothing.
    assert_eq!(testing::get(address, "/_lane/unknown").status, 401);
}

#[test]
fn a_control_route_of_another_service_or_an_unknown_route_is_404_and_a_wrong_method_405() {
    let service = feedback::start(0, TOKEN).unwrap();
    let address = service.address();
    let answer = testing::control(address, "GET", "/_lane/registry/mark", "");
    assert_eq!(
        (answer.status, answer.json()["error"]["code"].clone()),
        (404, "unknown_route".into())
    );
    assert_eq!(
        answer.json()["error"]["message"],
        "the lane service has no control route GET /_lane/registry/mark"
    );
    assert_eq!(testing::control(address, "GET", "/_lane/feedback/unknown", "").status, 404);
    let answer = testing::control(address, "PUT", "/_lane/feedback/submissions", "");
    assert_eq!(
        (answer.status, answer.json()["error"]["code"].clone()),
        (405, "method_not_allowed".into())
    );
}

#[test]
fn a_control_body_that_is_no_valid_json_is_400() {
    let service = feedback::start(0, TOKEN).unwrap();
    let answer = testing::control(service.address(), "POST", "/_lane/feedback/next", "{not json");
    assert_eq!((answer.status, answer.json()["error"]["code"].clone()), (400, "bad_request".into()));
    let answer = testing::control(service.address(), "POST", "/_lane/feedback/next", r#"{"waitMs":1,"other":2}"#);
    assert_eq!(answer.status, 400);
}

/// The variable that makes [`a_malformed_request_is_400_and_a_stopped_service_frees_its_port`] run its body, in a child
/// process of this test binary that the test starts itself.
const ISOLATED: &str = "AVL_GUEST_PORT_TEST_ISOLATED";

// A stopped service frees its port: a connection is refused at once, and a new service binds the same port. The body
// runs in a child process of this binary with one test thread. On macOS a socket is marked close-on-exec only after it
// exists, so a child process that another test thread starts in between keeps the listener open after the drop. No
// test thread of the child starts a process. The port is below the ephemeral floor, so no bind of port 0 of another
// process takes it while the service is stopped.
#[test]
fn a_malformed_request_is_400_and_a_stopped_service_frees_its_port() {
    if std::env::var_os(ISOLATED).is_none() {
        let name = "lane::services::control::tests::a_malformed_request_is_400_and_a_stopped_service_frees_its_port";
        let mut child = Command::new(std::env::current_exe().unwrap());
        child.args(["--exact", name, "--test-threads=1", "--nocapture"]).env(ISOLATED, "1");
        for variable in ["TEST_TOTAL_SHARDS", "TEST_SHARD_INDEX", "TEST_SHARD_STATUS_FILE"] {
            child.env_remove(variable);
        }
        let output = child.output().unwrap();
        let text = format!(
            "{}{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(output.status.success() && text.contains("1 passed"), "{text}");
        return;
    }
    let service = feedback::start(avl_testkit::free_port_below_ephemeral_floor(), TOKEN).unwrap();
    let address = service.address();
    let answer = testing::exchange(address, b"NONSENSE\r\n\r\n");
    assert_eq!((answer.status, answer.json()["error"]["code"].clone()), (400, "bad_request".into()));
    drop(service);
    let refused = TcpStream::connect(address).expect_err("no listener answers after the drop");
    assert_eq!(refused.kind(), io::ErrorKind::ConnectionRefused, "{refused}");
    let again = feedback::start(address.port(), TOKEN).expect("the port is free again after the drop");
    assert_eq!(again.address(), address);
}

#[test]
fn a_port_in_use_is_refused_with_the_bind_code() {
    let service = feedback::start(0, TOKEN).unwrap();
    let refusal = feedback::start(service.address().port(), TOKEN).err().unwrap();
    assert_eq!(refusal.code, crate::lane::services::BIND_CODE);
    assert!(
        refusal
            .message
            .starts_with(&format!("the lane feedback cannot bind {}: ", service.address())),
        "{}",
        refusal.message
    );
}
