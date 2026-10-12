//! What the golden manifest decodes to, as the tests of the packer and the discovery read it. The files are
//! [`avl_testkit::traces`]'.

use avl_testkit::traces;
use avl_trace::bundle::{MANIFEST_FILE, Manifest, decode_manifest};

pub(crate) fn example_manifest() -> Manifest {
    decode_manifest(&traces::example_file(MANIFEST_FILE)).expect("the golden manifest decodes")
}
