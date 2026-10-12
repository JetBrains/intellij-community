//! The pinned Docker CLI and its floor. The pin is `AIR_DOCKER_CLI_VERSION` in `docker.MODULE.bazel`, a test data
//! file of this crate, which is read, not transcribed. The resolution of the pin goes through the fake Bazel of
//! `avl-host-testkit`, as the pinned Tart's does.

use std::path::{Path, PathBuf};

use avl_base::config::{docker_buildx_label, docker_cli_label, label_target};
use avl_host_sys::guest::BazelHost;
use avl_host_sys::lock::LockManager;
use avl_host_testkit::{PinnedBazel, path_runner, quiet};
use pretty_assertions::assert_eq;

use super::*;
use crate::worker::pin::{PinnedTools, floor, module_pin};
use crate::worker::testing::Fixture;

fn docker_module() -> String {
    let path = avl_testkit::repo_path("tools/vm/docker.MODULE.bazel");
    std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("cannot read {}: {error}; it is test data of this crate", path.display()))
}

/// Keeps the CLI a developer gets by default at or above the one the backend's `--format` spellings need.
#[test]
fn the_pinned_docker_cli_meets_the_floor() {
    let pinned = module_pin("docker.MODULE.bazel", "AIR_DOCKER_CLI_VERSION");
    assert!(
        pinned >= floor(MINIMUM_DOCKER_CLI_VERSION),
        "the pinned Docker CLI {pinned:?} is older than the floor {MINIMUM_DOCKER_CLI_VERSION}"
    );
}

/// Keeps the `docker-buildx` plugin a developer gets by default at or above the one the image build needs.
#[test]
fn the_pinned_buildx_meets_the_floor() {
    let pinned = module_pin("docker.MODULE.bazel", "AIR_DOCKER_BUILDX_VERSION");
    assert!(
        pinned >= floor(MINIMUM_BUILDX_VERSION),
        "the pinned docker-buildx {pinned:?} is older than the floor {MINIMUM_BUILDX_VERSION}"
    );
}

/// Every label the controller asks Bazel for names a repository of `docker.MODULE.bazel`: the CLI with a `docker`
/// target, and the plugin as a downloaded file named `docker-buildx`.
#[test]
fn the_docker_labels_name_the_pinned_repositories() {
    let content = docker_module();
    for arch in [GuestArch::Arm64, GuestArch::X86_64] {
        for label in [docker_cli_label(arch), docker_buildx_label(arch)] {
            let repository = label_target(&label);
            assert!(
                content.contains(&format!("name = \"{repository}\"")),
                "docker.MODULE.bazel declares no {repository} for {label}"
            );
        }
    }
    assert!(content.contains("srcs = [\"docker/docker\"]"));
    assert!(content.contains("downloaded_file_path = \"docker-buildx\""));
}

/// No `DOCKER_BIN`, so the gate asks Bazel for the CLI of the host's architecture, keeps the real path, and asks
/// nothing more on the next command.
#[tokio::test]
async fn the_gate_resolves_the_pinned_docker_cli_once() {
    let fixture = Fixture::docker_builder().pinned_docker().build();
    let backend = fixture.manager.machine().docker().unwrap();
    assert_eq!(backend.program(), "", "nothing is resolved before the gate");
    for _ in 0..2 {
        backend.require_available(&Ctx::background(), "").await.unwrap();
    }
    let real = real_path(&fixture.fake.directory().join("docker")).unwrap();
    assert_eq!(PathBuf::from(backend.program()), real);
    // Two gates, and one query that resolves the CLI and its plugin together.
    let bazel = fixture.bazel.as_ref().unwrap();
    let labels = bazel.labels();
    assert_eq!(labels.len(), 1, "{labels:?}");
    assert_eq!(bazel.asked(), ["cquery", "info"]);
    let arch = fixture.settings.guest_arch;
    for label in [docker_cli_label(arch), docker_buildx_label(arch)] {
        assert!(labels[0].contains(&label), "{labels:?}");
    }
    // The pinned CLI gets the pinned plugin on an external engine too.
    let config = fixture.settings.docker_config_dir();
    let written: serde_json::Value = serde_json::from_slice(&std::fs::read(config.join("config.json")).unwrap()).unwrap();
    let directory = PathBuf::from(written["cliPluginsExtraDirs"][0].as_str().unwrap());
    assert!(directory.join("docker-buildx").is_file(), "{written}");
    let environment = fixture.manager.runner().environment();
    assert!(environment.contains(&("DOCKER_CONFIG".to_owned(), config.to_string_lossy().into_owned())));
    assert!(
        environment.contains(&("DOCKER_HOST".to_owned(), "unix:///nonexistent/docker.sock".to_owned())),
        "the engine the environment names stays"
    );
    assert!(fixture.fake.saw_call_containing("version --format {{.Server.Os}}/{{.Server.Arch}}"));
}

/// A CLI that `DOCKER_BIN` names is the operator's: no plugin is resolved, and no `DOCKER_CONFIG` replaces theirs.
#[tokio::test]
async fn a_named_cli_keeps_its_own_configuration() {
    let fixture = Fixture::docker();
    let backend = fixture.manager.machine().docker().unwrap();
    backend.require_available(&Ctx::background(), "").await.unwrap();
    assert!(!fixture.settings.docker_config_dir().exists());
    assert!(
        !fixture
            .manager
            .runner()
            .environment()
            .iter()
            .any(|(name, _)| name == "DOCKER_CONFIG")
    );
}

/// A guest line resolves the pinned CLI itself, so a verb that never passed the gate still spawns the executable.
#[tokio::test]
async fn the_guest_line_resolves_the_pinned_cli_without_the_gate() {
    let fixture = Fixture::docker_builder().pinned_docker().build();
    let backend = fixture.manager.machine().docker().unwrap();
    let line = backend
        .guest_argv(&Ctx::background(), "air-docker-1", &[String::from("/usr/bin/true")], false)
        .await
        .unwrap();
    let real = real_path(&fixture.fake.directory().join("docker")).unwrap();
    assert_eq!(PathBuf::from(&line[0]), real);
    assert_eq!(line[1..], ["exec", "air-docker-1", "/usr/bin/true"]);
}

/// With no `DOCKER_BIN` and no Bazel, the gate refuses before it spawns anything. A pin that Bazel names and the disk
/// does not hold is refused with the label and the command that fetches it.
#[tokio::test]
async fn a_pinned_cli_that_cannot_be_resolved_is_docker_missing() {
    let fixture = Fixture::docker_builder().pinned_docker().build();
    let label = docker_cli_label(fixture.settings.guest_arch);

    let locks = Arc::new(LockManager::new(path_runner()));
    let pinned = |bazel: Option<Arc<dyn BazelHost>>| Arc::new(PinnedTools::new(Arc::clone(&fixture.settings), path_runner(), bazel));
    let without_bazel = Docker::new(
        Arc::clone(&fixture.settings),
        path_runner(),
        quiet(),
        Arc::clone(&locks),
        pinned(None),
        Arc::default(),
    );
    let refusal = without_bazel.require_available(&Ctx::background(), "").await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("docker_missing", Exit::UNAVAILABLE));
    assert!(refusal.message.contains(&label), "{}", refusal.message);

    // The query resolves the CLI and its plugin together, so both are pinned, and both dangle.
    let dangling = PinnedBazel::default()
        .with_docker(fixture.settings.guest_arch, Path::new("/nonexistent/docker"))
        .with_buildx(fixture.settings.guest_arch, Path::new("/nonexistent/docker-buildx"));
    let backend = Docker::new(
        Arc::clone(&fixture.settings),
        path_runner(),
        quiet(),
        locks,
        pinned(Some(Arc::new(dangling))),
        Arc::default(),
    );
    let refusal = backend.require_available(&Ctx::background(), "").await.unwrap_err();
    assert_eq!(refusal.code, "docker_missing");
    assert!(
        refusal.message.contains(&format!("`./bazel.cmd cquery {label}`")),
        "{}",
        refusal.message
    );
    assert!(fixture.fake.calls().is_empty(), "{:?}", fixture.fake.calls());
}
