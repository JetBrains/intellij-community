//! Helpers the tests of this crate share.

use std::path::Path;

use avl_base::{Backend, Config, Environment, GuestOs, Selection};

/// Settings for one backend and guest, with the host paths resolved to `repo` and `repo/bazel-user-root`.
///
/// Every test names its whole environment, so a default under test is a default and not something the shell
/// running the suite happened to export.
pub(crate) fn settings(backend: Backend, guest_os: GuestOs, repo: &Path) -> Config {
    settings_in(backend, guest_os, repo, &[])
}

/// [`settings`] with more variables.
pub(crate) fn settings_in(backend: Backend, guest_os: GuestOs, repo: &Path, extra: &[(&str, &str)]) -> Config {
    let loaded = unresolved(backend, guest_os, extra);
    loaded
        .set_host_paths(repo, repo.join("bazel-user-root"))
        .unwrap_or_else(|refusal| panic!("the host paths were refused: {refusal:?}"));
    loaded
}

/// Settings whose host paths are not resolved yet. It installs the fixture areas, as every helper here does, because the
/// help text and the lane resolution read the Air area.
pub(crate) fn unresolved(backend: Backend, guest_os: GuestOs, extra: &[(&str, &str)]) -> Config {
    avl_affected::bridge::install_fixture();
    let mut environment = Environment::from_pairs([("HOME", "/Users/air")]);
    for (name, value) in extra {
        environment.set(*name, *value);
    }
    Config::load(
        avl_base::HostFacts::without_memory(),
        Selection { backend, guest_os },
        &environment,
        Path::new("/repo/community/tools/vm"),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"))
}

/// The backend of a pool for one guest: Docker for a Linux guest, and Tart for a macOS guest.
pub(crate) const fn pool_backend(guest_os: GuestOs) -> Backend {
    match guest_os {
        GuestOs::Linux => Backend::Docker,
        GuestOs::Macos => Backend::Tart,
    }
}

/// The settings of the pool for one guest over the fixed `/repo`, for the cases that read no checkout.
pub(crate) fn pool(guest_os: GuestOs) -> Config {
    settings(pool_backend(guest_os), guest_os, Path::new("/repo"))
}

/// Docker settings over the fixed `/repo`: the Linux guest, for the cases that read no checkout.
pub(crate) fn docker() -> Config {
    settings(Backend::Docker, GuestOs::Linux, Path::new("/repo"))
}

/// Tart settings over the fixed `/repo`, for the cases that read no checkout.
pub(crate) fn tart(guest_os: GuestOs) -> Config {
    settings(Backend::Tart, guest_os, Path::new("/repo"))
}
