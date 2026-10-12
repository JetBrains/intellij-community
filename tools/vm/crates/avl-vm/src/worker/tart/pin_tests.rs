//! The pinned Tart and the floor. The pin is `AIR_TART_VERSION` in `tart.MODULE.bazel`, and the floor has two copies:
//! [`MINIMUM_VERSION`] and `TART_MIN_VERSION` in `provision/versions.env`. Both files are test data of this crate and
//! are read, not transcribed.

use std::path::PathBuf;
use std::sync::Arc;

use avl_base::config::TART_LABEL;
use avl_base::{Backend, GuestOs};
use avl_host_sys::Ctx;
use avl_host_sys::guest::BazelHost;
use avl_host_testkit::{HostPool, PinnedBazel, path_runner, quiet};
use pretty_assertions::assert_eq;
use regex::Regex;

use super::*;
use crate::worker::pin::PinnedTools;

/// A Tart backend over `settings` whose pinned tools resolve through `bazel`.
fn backend_over(settings: &Arc<Config>, bazel: Option<Arc<dyn BazelHost>>) -> Tart {
    let pinned = Arc::new(PinnedTools::new(Arc::clone(settings), path_runner(), bazel));
    Tart::new(Arc::clone(settings), path_runner(), quiet(), pinned)
}

fn repository_file(relative: &str) -> String {
    let path = avl_testkit::repo_path(relative);
    std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("cannot read {}: {error}; it is test data of this crate", path.display()))
}

/// Keeps the Tart a developer gets by default inside the controller's own gate. A pin below the floor would refuse
/// every Tart command with `unsupported_tart`.
#[test]
fn the_pinned_tart_meets_the_floor() {
    let content = repository_file("tools/vm/tart.MODULE.bazel");
    let pin = Regex::new(r#"(?m)^AIR_TART_VERSION = "([^"]+)"$"#)
        .unwrap()
        .captures(&content)
        .expect("tart.MODULE.bazel declares no AIR_TART_VERSION; the pin has moved");
    let pinned = parse_version(&pin[1]).expect("AIR_TART_VERSION is not a version");
    assert!(
        pinned >= parse_version(MINIMUM_VERSION).unwrap(),
        "the pinned Tart {} is older than the floor {MINIMUM_VERSION}",
        &pin[1]
    );
}

/// Keeps the two copies of the floor equal: `check-host.sh` gates the image pipeline on `TART_MIN_VERSION`, and this
/// controller gates on [`MINIMUM_VERSION`].
#[test]
fn the_image_pipeline_floor_is_the_controller_floor() {
    let content = repository_file("tools/vm/provision/versions.env");
    let floor = Regex::new(r"(?m)^TART_MIN_VERSION=(\S+)$")
        .unwrap()
        .captures(&content)
        .expect("versions.env declares no TART_MIN_VERSION; the pipeline's floor has moved");
    assert_eq!(&floor[1], MINIMUM_VERSION, "the Tart floor has drifted");
}

/// A Tart backend over a pool with no `TART_BIN`, and a Bazel that resolves the pinned Tart to the pool's fake, or no
/// Bazel at all.
fn unpinned_backend(with_bazel: bool) -> (Tart, Arc<PinnedBazel>, HostPool) {
    let pool = HostPool::builder(Backend::Tart, GuestOs::Macos, MINIMUM_VERSION)
        .without_tart_bin()
        .build();
    let bazel = Arc::new(PinnedBazel::tart(pool.fake.executable()));
    let host: Option<Arc<dyn BazelHost>> = with_bazel.then(|| bazel.clone() as Arc<dyn BazelHost>);
    let backend = backend_over(&pool.settings, host);
    (backend, bazel, pool)
}

/// The default path: no `TART_BIN`, so the gate asks Bazel for [`TART_LABEL`], keeps the real path, and asks nothing
/// more on the next command.
#[tokio::test]
async fn the_gate_resolves_the_pinned_tart_once() {
    let (backend, bazel, fixture) = unpinned_backend(true);
    assert_eq!(backend.program(), "", "nothing is resolved before the gate");

    for _ in 0..2 {
        backend.require_available(&Ctx::background(), "").await.unwrap();
    }
    let real = real_path(fixture.fake.executable()).unwrap();
    assert_eq!(PathBuf::from(backend.program()), real);
    assert_eq!(bazel.asked(), ["cquery", "info"], "two gates, one resolution");
    let labels = bazel.labels();
    assert!(labels.iter().any(|label| label.contains(TART_LABEL)), "{labels:?}");
}

/// `TART_BIN` stays an override that costs no Bazel call.
#[tokio::test]
async fn a_named_tart_asks_bazel_nothing() {
    let fixture = HostPool::builder(Backend::Tart, GuestOs::Macos, MINIMUM_VERSION).build();
    let bazel = Arc::new(PinnedBazel::tart(fixture.fake.executable()));
    let backend = backend_over(&fixture.settings, Some(bazel.clone()));
    backend.require_available(&Ctx::background(), "").await.unwrap();
    assert!(bazel.asked().is_empty());
    assert_eq!(PathBuf::from(backend.program()), fixture.fake.executable());
}

/// A guest line resolves the pinned Tart itself, so a verb that never passed the gate (`daemon stop`, `daemon log`)
/// still spawns the real executable. The spawn goes through the fake, which records only what it was started as.
#[tokio::test]
async fn the_guest_line_resolves_the_pinned_tart_without_the_gate() {
    let (backend, bazel, fixture) = unpinned_backend(true);
    let ctx = Ctx::background();
    let real = real_path(fixture.fake.executable()).unwrap();
    for _ in 0..2 {
        let line = backend
            .guest_argv(&ctx, "air-macos-1", &[String::from("/usr/bin/true")], false)
            .await
            .unwrap();
        assert_eq!(PathBuf::from(&line[0]), real);
        let captured = path_runner()
            .capture(&ctx, &line, &SpawnOptions::within(Duration::from_mins(1)))
            .await
            .unwrap();
        assert_eq!(captured.exit_code, 0);
    }
    assert_eq!(bazel.asked(), ["cquery", "info"], "two guest lines, one resolution");
    assert!(
        fixture.fake.saw_call_containing("exec air-macos-1 /usr/bin/true"),
        "{:?}",
        fixture.fake.calls()
    );
}

/// With neither `TART_BIN` nor Bazel, a guest line refuses `tart_missing` rather than spawning an argv with no
/// executable.
#[tokio::test]
async fn a_guest_line_with_no_resolvable_tart_is_tart_missing() {
    let (backend, _bazel, fixture) = unpinned_backend(false);
    let refusal = backend
        .guest_argv(&Ctx::background(), "air-macos-1", &[String::from("/usr/bin/true")], false)
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("tart_missing", Exit::UNAVAILABLE));
    assert!(fixture.fake.calls().is_empty(), "{:?}", fixture.fake.calls());
}
