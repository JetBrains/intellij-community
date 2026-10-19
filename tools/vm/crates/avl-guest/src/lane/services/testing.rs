//! The client side of the lane service tests: one real `TcpStream` per request, and the raw answer it reads.

use std::io::{Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::path::{Path, PathBuf};
use std::time::Duration;

use serde_json::Value;

use super::control::TOKEN_HEADER;
use crate::lane::package::Packages;

/// The lane-service token of every test.
pub(crate) const TOKEN: &str = "lane-service-test-token";

/// The `java` of the distribution's JBR that the fixture packages name.
pub(crate) const FIXTURE_JAVA: &str = "/home/admin/air-lane/jbr/bin/java";

/// The fixture agent jar of `testdata/lane-services`.
pub(crate) fn fixture_jar() -> PathBuf {
    avl_testkit::crate_path!("testdata/lane-services/fake-agent.jar")
}

/// The packages of the fixture jar for [FIXTURE_JAVA].
pub(crate) fn fixture_packages() -> Packages {
    Packages::assemble(&fixture_jar(), Path::new(FIXTURE_JAVA)).expect("the fixture packages")
}

/// The absolute path of the golden `name` of `testdata/lane-services`, because `expect_file!` reads a relative path from
/// the workspace root.
pub(crate) fn golden(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/lane-services/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// One answer as the client read it, up to the end of the connection.
pub(crate) struct Answer {
    pub status: u16,
    pub head: String,
    /// The body bytes as they came, before a chunked decoding.
    pub raw_body: Vec<u8>,
}

impl Answer {
    /// A header's value, by its case-insensitive name.
    pub(crate) fn header(&self, name: &str) -> Option<&str> {
        self.head.lines().skip(1).find_map(|line| {
            let (key, value) = line.split_once(':')?;
            key.eq_ignore_ascii_case(name).then(|| value.trim())
        })
    }

    /// The body, decoded when the answer is chunked.
    pub(crate) fn body(&self) -> Vec<u8> {
        if self.header("Transfer-Encoding") != Some("chunked") {
            return self.raw_body.clone();
        }
        let mut body = Vec::new();
        let mut rest = self.raw_body.as_slice();
        loop {
            let end = rest.windows(2).position(|pair| pair == b"\r\n").expect("a chunk size line");
            let size = usize::from_str_radix(std::str::from_utf8(&rest[..end]).unwrap(), 16).unwrap();
            rest = &rest[end + 2..];
            if size == 0 {
                assert_eq!(rest, b"\r\n", "the last chunk ends the body");
                return body;
            }
            body.extend_from_slice(&rest[..size]);
            assert_eq!(&rest[size..size + 2], b"\r\n");
            rest = &rest[size + 2..];
        }
    }

    pub(crate) fn text(&self) -> String {
        String::from_utf8(self.body()).expect("a UTF-8 body")
    }

    pub(crate) fn json(&self) -> Value {
        serde_json::from_slice(&self.body()).unwrap_or_else(|error| panic!("the body is not JSON ({error}): {}", self.text()))
    }
}

/// Sends `request` as it is and reads the answer to the end of the connection.
pub(crate) fn exchange(address: SocketAddr, request: &[u8]) -> Answer {
    let mut stream = TcpStream::connect(address).expect("connect to the service");
    stream.set_read_timeout(Some(Duration::from_secs(30))).unwrap();
    stream.write_all(request).expect("write the request");
    let mut answer = Vec::new();
    stream.read_to_end(&mut answer).expect("read the answer");
    let end = answer
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .unwrap_or_else(|| panic!("no answer head in {:?}", String::from_utf8_lossy(&answer)));
    let head = String::from_utf8(answer[..end].to_vec()).expect("a UTF-8 head");
    let status = head.split(' ').nth(1).and_then(|status| status.parse().ok()).expect("a status");
    Answer {
        status,
        head,
        raw_body: answer[end + 4..].to_vec(),
    }
}

/// Sends `method target` with `headers` and `body`, the way the IDE's client does: one request with `Connection:
/// close`.
pub(crate) fn call(address: SocketAddr, method: &str, target: &str, headers: &[(&str, &str)], body: &[u8]) -> Answer {
    let mut request = format!("{method} {target} HTTP/1.1\r\nHost: {address}\r\nConnection: close\r\n");
    for (name, value) in headers {
        request.push_str(&format!("{name}: {value}\r\n"));
    }
    if !body.is_empty() {
        request.push_str(&format!("Content-Length: {}\r\n", body.len()));
    }
    request.push_str("\r\n");
    let mut bytes = request.into_bytes();
    bytes.extend_from_slice(body);
    exchange(address, &bytes)
}

/// A request of the IDE: `GET target`.
pub(crate) fn get(address: SocketAddr, target: &str) -> Answer {
    call(address, "GET", target, &[], b"")
}

/// A control request with the test token and the JSON text `body`.
pub(crate) fn control(address: SocketAddr, method: &str, target: &str, body: &str) -> Answer {
    let mut headers = vec![(TOKEN_HEADER, TOKEN)];
    if !body.is_empty() {
        headers.push(("Content-Type", "application/json"));
    }
    call(address, method, target, &headers, body.as_bytes())
}
