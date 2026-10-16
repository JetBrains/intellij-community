use std::path::PathBuf;

use avl_base::GuestOs;
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::{fixture_backend, settings};

// An empty share path is the one failure that is invisible on the host: `tart run` accepts it, the VM boots, and
// the guest gets blamed for a share pointing nowhere. So the share set refuses to exist before the host paths are
// resolved.
#[test]
fn shares_refuse_to_be_built_before_the_host_paths_are_resolved() {
    let resolved = settings(fixture_backend(GuestOs::Linux), GuestOs::Linux);
    let refusal = shares(&resolved).unwrap_err();
    assert_eq!(refusal.code, "host_paths_unresolved");

    resolved.set_host_paths("/Users/air/idea", "/Users/air/bazel").unwrap();
    // One share, the Bazel output root: no share holds the checkout.
    let [bazel] = shares(&resolved).unwrap();
    assert_eq!(
        bazel,
        SharedFolder {
            name: resolved.bazel_share_name,
            path: PathBuf::from("/Users/air/bazel"),
            mode: SHARE_MODE,
        }
    );
    // Read-only is not a default a caller may override: the parity layout's whole premise is that the guest reads
    // outputs the host built.
    assert_eq!(SHARE_MODE, "ro");
}
