use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::supervisor::Envelope;

// The receipt is the data of the agent's success envelope, and the controller decodes the same type.
#[test]
fn a_receipt_names_the_size_and_the_digest_of_its_bytes() {
    let receipt = FileReceipt::of(b"abc");
    assert_eq!(
        receipt,
        FileReceipt {
            bytes: 3,
            sha256: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".to_owned(),
        }
    );
    let envelope = serde_json::to_value(Envelope::success("read-file", receipt.clone())).unwrap();
    assert_eq!(
        envelope,
        json!({"schemaVersion": 1, "ok": true, "command": "read-file",
            "data": {"bytes": 3, "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"}})
    );
    let decoded: Envelope<FileReceipt> = serde_json::from_value(envelope).unwrap();
    assert_eq!(decoded.into_result().unwrap(), receipt);
    // A field this side does not know is a receipt of another wire.
    serde_json::from_value::<FileReceipt>(json!({"bytes": 3, "sha256": "x", "more": 1})).unwrap_err();
}
