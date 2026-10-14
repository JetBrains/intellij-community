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

#[test]
fn a_malformed_request_is_400_and_a_stopped_service_frees_its_port() {
    let service = feedback::start(0, TOKEN).unwrap();
    let address = service.address();
    let answer = testing::exchange(address, b"NONSENSE\r\n\r\n");
    assert_eq!((answer.status, answer.json()["error"]["code"].clone()), (400, "bad_request".into()));
    drop(service);
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
