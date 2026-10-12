use std::fs;

use avl_wire::pull::FileReceipt;
use avl_wire::supervisor::{Envelope, ReceivedEnvelope};
use pretty_assertions::assert_eq;

use crate::testing::run_agent;

/// Bytes that survive no text channel: every value 0..255, several chunks over.
fn corpus() -> Vec<u8> {
    (0..=u8::MAX).cycle().take(3 * 64 * 1024 + 17).collect()
}

// The file arrives on stdout byte for byte, and the receipt on stderr names its size and digest.
#[test]
fn read_file_writes_the_bytes_and_names_them_on_stderr() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("traces-001.zip");
    let content = corpus();
    fs::write(&path, &content).unwrap();
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let exit = crate::run(
        ["read-file".into(), path.as_os_str().to_owned()],
        &mut std::io::empty(),
        &mut stdout,
        &mut stderr,
    );
    assert_eq!(exit, 0, "{}", String::from_utf8_lossy(&stderr));
    assert!(stdout == content, "stdout is not the file");
    let envelope: Envelope<FileReceipt> = serde_json::from_slice(&stderr).unwrap();
    assert_eq!(envelope.command, "read-file");
    assert_eq!(envelope.into_result().unwrap(), FileReceipt::of(&content));
}

// A file that cannot be opened writes nothing on stdout, and the refusal names the file.
#[test]
fn read_file_refuses_a_missing_file_before_any_byte() {
    let directory = tempfile::tempdir().unwrap();
    let missing = directory.path().join("missing.xml");
    let answered = run_agent(&["read-file".as_ref(), missing.as_os_str()], b"");
    assert_eq!(answered.exit, 70, "{}", answered.stderr);
    assert_eq!(answered.stdout, "");
    let envelope = ReceivedEnvelope::read(answered.stderr.trim_end()).unwrap();
    let error = envelope.error.expect("a failure envelope");
    assert_eq!(error.code, "guest_read_file_failed");
    assert!(error.message.contains(&missing.display().to_string()), "{}", error.message);
}
