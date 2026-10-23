//! The bindings of the AV1 encoder of libaom, which `avif.rs` of the recorder calls.
//!
//! `bindings.rs` holds the calls, the constants and the types of the encoder only. `community/tools/vm/aom/update.mjs`
//! writes it with bindgen from the headers of the release that `avl.MODULE.bazel` pins. Cargo builds and links that
//! release through `build.rs`. Bazel links `@avl_aom//:aom` and runs no build script.
//!
//! The bindings are those of an LP64 target, so the crate is empty on Windows, where the recorder links no libaom.

#[cfg(unix)]
#[expect(
    non_camel_case_types,
    clippy::allow_attributes_without_reason,
    clippy::pub_underscore_fields,
    reason = "bindgen writes the names and the forms of libaom"
)]
mod bindings;

#[cfg(unix)]
pub use bindings::*;

#[cfg(test)]
#[cfg(unix)]
mod tests;
