use std::ffi::CStr;

use super::*;

/// The version that `build.rs` downloads, from its `VERSION`.
fn cargo_version() -> &'static str {
    let script = include_str!("../build.rs");
    let line = script
        .lines()
        .find(|line| line.starts_with("const VERSION: &str = "))
        .expect("build.rs names its VERSION");
    line.split('"').nth(1).expect("the VERSION is a string")
}

/// Cargo and Bazel build one release: the version, the digest and the mirror file of `build.rs` are those of `avl_aom`
/// in `avl.MODULE.bazel`. The library that this test links is that version, whichever of the two built it.
#[test]
fn the_cargo_build_takes_the_libaom_of_bazel() {
    let script = include_str!("../build.rs");
    let module = include_str!("../../../avl.MODULE.bazel");
    let quoted = |name: &str| {
        let line = script
            .lines()
            .find(|line| line.starts_with(&format!("const {name}: &str = ")))
            .expect(name);
        line.split('"').nth(1).expect(name).to_owned()
    };
    for name in ["SHA256", "URL"] {
        let value = quoted(name);
        assert!(
            module.contains(&format!("\"{value}\"")),
            "avl.MODULE.bazel pins another {name} than build.rs: {value}"
        );
    }
    // SAFETY: the call takes no argument.
    let linked = unsafe { aom_codec_version_str() };
    // SAFETY: libaom answers a static NUL-terminated string.
    let linked = unsafe { CStr::from_ptr(linked) };
    assert_eq!(linked.to_str().unwrap(), format!("v{}", cargo_version()));
}
