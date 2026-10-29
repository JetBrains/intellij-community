use avl_base::GuestOs;
use avl_host_testkit::{HostPool, PinnedBazel};
use pretty_assertions::assert_eq;

use super::*;
use crate::worker::tart::MINIMUM_VERSION;

// A tool that `TART_BIN` or `DOCKER_BIN` names is the operator's, so only the others are pinned tools of the pool.
#[test]
fn a_pool_runs_from_its_pins_only_what_no_variable_names() {
    let tart = HostPool::builder(Backend::Tart, GuestOs::Macos, MINIMUM_VERSION)
        .without_tart_bin()
        .build();
    assert_eq!(PinnedTool::of_pool(&tart.settings), [PinnedTool::Tart]);
    let named = HostPool::builder(Backend::Tart, GuestOs::Macos, MINIMUM_VERSION).build();
    assert_eq!(PinnedTool::of_pool(&named.settings), []);
    let docker = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION).build();
    assert_eq!(PinnedTool::of_pool(&docker.settings), []);
    let cli = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .without_docker_bin()
        .build();
    assert_eq!(
        PinnedTool::of_pool(&cli.settings),
        [PinnedTool::DockerCli, PinnedTool::DockerBuildx]
    );
    // The Apple container engine runs no Docker CLI, and `CONTAINER_BIN` names the fake, so nothing is pinned.
    let container = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .with_container_engine()
        .build();
    assert_eq!(PinnedTool::of_pool(&container.settings), []);
    let pinned = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .with_container_engine()
        .env("CONTAINER_BIN", "")
        .build();
    assert_eq!(PinnedTool::of_pool(&pinned.settings), [PinnedTool::Container]);
    assert_eq!(
        PinnedTool::Container.label(&pinned.settings),
        "@community//tools/vm:air_container_darwin_arm64"
    );
}

// The first request resolves every pinned tool of the pool with one `cquery` and one `info`, in any order of the
// requests, and a later request asks Bazel nothing.
#[tokio::test]
async fn one_query_resolves_every_pinned_tool_of_a_pool() {
    let pool = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .without_docker_bin()
        .build();
    let bazel = Arc::new(pool.pinned_bazel());
    let host: Arc<dyn BazelHost> = bazel.clone();
    let pinned = PinnedTools::new(Arc::clone(&pool.settings), pool.runner(), Some(host));
    let ctx = Ctx::background();
    for tool in [PinnedTool::DockerBuildx, PinnedTool::DockerCli, PinnedTool::DockerBuildx] {
        let path = pinned
            .path(&ctx, tool)
            .await
            .unwrap()
            .expect("a pool with a Bazel resolves its tools");
        assert!(path.exists(), "{tool}: {}", path.display());
    }
    assert_eq!(bazel.asked(), ["cquery", "info"]);
    let labels = bazel.labels();
    assert_eq!(labels.len(), 1, "{labels:?}");
    for tool in PinnedTool::of_pool(&pool.settings) {
        assert!(labels[0].contains(&tool.label(&pool.settings)), "{labels:?}");
    }
}

// With no Bazel every request answers `None`, and each backend refuses with its own code.
#[tokio::test]
async fn a_pool_without_bazel_resolves_nothing() {
    let pool = HostPool::builder(Backend::Tart, GuestOs::Macos, MINIMUM_VERSION)
        .without_tart_bin()
        .build();
    let pinned = PinnedTools::new(Arc::clone(&pool.settings), pool.runner(), None);
    assert_eq!(pinned.path(&Ctx::background(), PinnedTool::Tart).await.unwrap(), None);
}

/// Writes the `.MODULE.bazel` file of the pinned Docker CLI into the checkout of `pool`, so the record can key on it.
fn write_docker_pin(pool: &HostPool, docker: &str) {
    let directory = pool.root().join("community/tools/vm");
    std::fs::create_dir_all(&directory).unwrap();
    std::fs::write(directory.join("docker.MODULE.bazel"), docker).unwrap();
}

/// The resolution of every tool of `pool` by a fresh resolver over `bazel`, as the next process makes it, and what that
/// Bazel was asked. The caller keeps `bazel`, because its files are the files that a record names.
async fn resolve_as_a_new_process(pool: &HostPool, bazel: &Arc<PinnedBazel>) -> (Vec<PathBuf>, Vec<String>) {
    let before = bazel.asked().len();
    let host: Arc<dyn BazelHost> = bazel.clone();
    let pinned = PinnedTools::new(Arc::clone(&pool.settings), pool.runner(), Some(host));
    let mut paths = Vec::new();
    for tool in PinnedTool::of_pool(&pool.settings) {
        paths.push(pinned.path(&Ctx::background(), tool).await.unwrap().unwrap());
    }
    (paths, bazel.asked()[before..].to_vec())
}

/// A Docker pool without `DOCKER_BIN` whose Docker pin is written, and the Bazel that resolves the pinned tools.
fn pinned_docker_pool() -> (HostPool, Arc<PinnedBazel>) {
    let pool = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .without_docker_bin()
        .build();
    write_docker_pin(&pool, "docker pin\n");
    let bazel = Arc::new(pool.pinned_bazel());
    (pool, bazel)
}

// A process records what it resolved, so the next one asks Bazel nothing. On 2026-10-10 each `cquery` of a pinned tool
// started with a deletion of about 510,000 Skyframe nodes, and the next lane build analyzed the whole graph again.
#[tokio::test]
async fn a_recorded_path_answers_the_next_process_without_a_query() {
    let (pool, bazel) = pinned_docker_pool();

    let (first, asked) = resolve_as_a_new_process(&pool, &bazel).await;
    assert_eq!(asked, ["cquery", "info"]);
    let (second, asked) = resolve_as_a_new_process(&pool, &bazel).await;
    assert_eq!(second, first);
    assert_eq!(asked, Vec::<String>::new(), "the record did not answer");
}

// A bump of a pin changes the digest of its `.MODULE.bazel` file, so the tool is resolved again.
#[tokio::test]
async fn a_changed_pin_resolves_again() {
    let (pool, bazel) = pinned_docker_pool();
    resolve_as_a_new_process(&pool, &bazel).await;

    write_docker_pin(&pool, "docker pin, bumped\n");
    let (_, asked) = resolve_as_a_new_process(&pool, &bazel).await;
    assert_eq!(asked, ["cquery", "info"]);
}

// A recorded file that is gone, such as one of a removed output base, is no answer.
#[tokio::test]
async fn a_recorded_file_that_is_gone_resolves_again() {
    let (pool, bazel) = pinned_docker_pool();
    resolve_as_a_new_process(&pool, &bazel).await;
    let record = pool.settings.runtime_root.join(PINNED_TOOLS_RECORD);
    let text = std::fs::read_to_string(&record).unwrap();
    let mut tools: Vec<serde_json::Value> = serde_json::from_str(&text).unwrap();
    tools[0]["path"] = serde_json::json!(pool.root().join("gone").to_string_lossy());
    std::fs::write(&record, serde_json::to_vec(&tools).unwrap()).unwrap();

    let (_, asked) = resolve_as_a_new_process(&pool, &bazel).await;
    assert_eq!(asked, ["cquery", "info"]);
}

// A checkout without the `.MODULE.bazel` files has no key, so it queries every time and records nothing.
#[tokio::test]
async fn a_checkout_without_the_pins_records_nothing() {
    let pool = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .without_docker_bin()
        .build();
    resolve_as_a_new_process(&pool, &Arc::new(pool.pinned_bazel())).await;
    assert!(!pool.settings.runtime_root.join(PINNED_TOOLS_RECORD).exists());
}
