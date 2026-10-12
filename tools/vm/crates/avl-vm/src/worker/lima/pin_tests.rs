//! The pinned Lima and its floor. The pin is `AIR_LIMA_VERSION` in `lima.MODULE.bazel`, and the floor has two
//! copies: [`MINIMUM_LIMA_VERSION`] and the `minimumLimaVersion` of the engine template. The Bazel file is test data
//! of this crate and is read, not transcribed.

use avl_base::GuestArch;
use avl_base::config::{label_target, limactl_label};
use pretty_assertions::assert_eq;
use regex::Regex;

use super::*;
use crate::worker::pin::{floor, module_pin};

/// Keeps the Lima a developer gets by default at or above the version the engine template asks for. A pin below
/// it would refuse every engine start.
#[test]
fn the_pinned_lima_meets_the_floor() {
    let pinned = module_pin("lima.MODULE.bazel", "AIR_LIMA_VERSION");
    assert!(
        pinned >= floor(MINIMUM_LIMA_VERSION),
        "the pinned Lima {pinned:?} is older than the floor {MINIMUM_LIMA_VERSION}"
    );
}

/// Keeps the two copies of the floor equal: Lima refuses a template whose `minimumLimaVersion` is above its own
/// version, and the pin test reads [`MINIMUM_LIMA_VERSION`].
#[test]
fn the_template_floor_is_the_controller_floor() {
    let floor = Regex::new(r"(?m)^minimumLimaVersion: (\S+)$")
        .unwrap()
        .captures(ENGINE_TEMPLATE)
        .expect("the engine template declares no minimumLimaVersion");
    assert_eq!(&floor[1], MINIMUM_LIMA_VERSION, "the Lima floor has drifted");
}

/// Every label the controller asks Bazel for names a repository of `lima.MODULE.bazel`, with a `limactl` target.
#[test]
fn the_limactl_labels_name_the_pinned_repositories() {
    let path = avl_testkit::repo_path("tools/vm/lima.MODULE.bazel");
    let content = std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("cannot read {}: {error}", path.display()));
    for arch in [GuestArch::Arm64, GuestArch::X86_64] {
        let label = limactl_label(arch);
        let repository = label_target(&label);
        assert!(
            content.contains(&format!("name = \"{repository}\"")),
            "lima.MODULE.bazel declares no {repository} for {label}"
        );
    }
    assert!(content.contains("name = \"limactl\""));
    assert!(content.contains("srcs = [\"bin/limactl\"]"));
}
