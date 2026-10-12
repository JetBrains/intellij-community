use avl_base::GuestOs;
use avl_host_testkit::HostPool;
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
    let lima = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .with_lima_engine()
        .build();
    assert_eq!(
        PinnedTool::of_pool(&lima.settings),
        [PinnedTool::DockerCli, PinnedTool::DockerBuildx, PinnedTool::Limactl]
    );
}

// The first request resolves every pinned tool of the pool with one `cquery` and one `info`, in any order of the
// requests, and a later request asks Bazel nothing.
#[tokio::test]
async fn one_query_resolves_every_pinned_tool_of_a_pool() {
    let pool = HostPool::builder(Backend::Docker, GuestOs::Linux, MINIMUM_VERSION)
        .with_lima_engine()
        .build();
    let bazel = Arc::new(pool.pinned_bazel());
    let host: Arc<dyn BazelHost> = bazel.clone();
    let pinned = PinnedTools::new(Arc::clone(&pool.settings), pool.runner(), Some(host));
    let ctx = Ctx::background();
    for tool in [
        PinnedTool::Limactl,
        PinnedTool::DockerCli,
        PinnedTool::DockerBuildx,
        PinnedTool::Limactl,
    ] {
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
