//! The Docker backend's suite, over the fake `docker` of the shared hypervisor fake. Hermetic: no engine, no image,
//! no network.
//!
//! What these assert on is mostly argv, as on the other backends: a wrong `-v` or a `-t` still exits 0, and the
//! guest is where it would show.

use std::path::PathBuf;

use avl_base::{GuestArch, GuestOs, SCHEMA_VERSION};
use avl_host_sys::Ctx;
use avl_host_sys::share::SHARE_MODE;
use avl_testkit::tartfake::Answer;
use pretty_assertions::assert_eq;

use super::*;
use crate::worker::hypervisor::is_unsupported;
use crate::worker::testing::Fixture;

fn ctx() -> Ctx {
    Ctx::background()
}

fn docker(fixture: &Fixture) -> &Docker {
    fixture.manager.machine().docker().expect("a Docker pool")
}

fn strings(words: &[&str]) -> Vec<String> {
    words.iter().map(|word| (*word).to_owned()).collect()
}

// --- the engine gate -----------------------------------------------------------------------------------------

/// An engine of the guest's architecture is accepted under the OCI spelling and under the `uname -m` one.
#[tokio::test]
async fn an_engine_of_the_guests_architecture_is_accepted_under_either_spelling() {
    for (arch, spellings) in [
        (GuestArch::Arm64, ["linux/arm64", "linux/aarch64"]),
        (GuestArch::X86_64, ["linux/amd64", "linux/x86_64"]),
    ] {
        let fixture = Fixture::docker_builder().guest_arch(arch).build();
        for spelling in spellings {
            fixture.fake.answer(Answer::DockerVersion, format!("{spelling}\n"));
            docker(&fixture)
                .require_available(&ctx(), "")
                .await
                .unwrap_or_else(|refusal| panic!("{arch} refused {spelling}: {refusal:?}"));
        }
        assert!(fixture.fake.saw_call_containing("version --format {{.Server.Os}}/{{.Server.Arch}}"));
    }
}

/// Another architecture is a refusal a caller can branch on, and the prose names what the worker runs: the host's
/// own architecture, which an emulating engine does not.
#[tokio::test]
async fn an_engine_of_another_architecture_is_unsupported() {
    for (arch, engine, wanted) in [
        (GuestArch::Arm64, "linux/amd64", "linux/arm64"),
        (GuestArch::X86_64, "linux/arm64", "linux/amd64"),
    ] {
        let fixture = Fixture::docker_builder().guest_arch(arch).build();
        fixture.fake.answer(Answer::DockerVersion, format!("{engine}\n"));
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        assert!(is_unsupported(&refusal), "{refusal:?}");
        assert!(refusal.message.contains(wanted), "{}", refusal.message);
        assert!(refusal.message.contains(engine), "{}", refusal.message);
    }
}

/// The publish builds one half per guest architecture, so a pull on either kind of host finds its half.
#[test]
fn every_guest_architecture_is_published() {
    let published: Vec<String> = [GuestArch::Arm64, GuestArch::X86_64]
        .into_iter()
        .map(|arch| format!("linux/{}", arch.oci_arch()))
        .collect();
    assert_eq!(PUBLISHED_PLATFORMS.to_vec(), published);
    for arch in [GuestArch::Arm64, GuestArch::X86_64] {
        assert!(runs_natively(arch, &format!("linux/{}", arch.oci_arch())));
        assert!(runs_natively(arch, &format!("linux/{}", arch.as_str())));
    }
    assert!(!runs_natively(GuestArch::Arm64, "darwin/arm64"));
    assert!(!runs_natively(GuestArch::X86_64, "linux/386"));
}

/// An engine that does not answer and a CLI that is not there are both `docker_missing` at 69, which is what a
/// caller retries against: an engine that is not started yet is the common case.
#[tokio::test]
async fn a_silent_engine_and_a_missing_cli_are_docker_missing() {
    let fixture = Fixture::docker();
    fixture.fake.answer(Answer::VersionExit, "1");
    let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("docker_missing", Exit::UNAVAILABLE));

    let missing = Fixture::docker_builder().env("DOCKER_BIN", "/nonexistent/docker").build();
    let refusal = docker(&missing).require_available(&ctx(), "").await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("docker_missing", Exit::UNAVAILABLE));
    assert!(refusal.message.contains("DOCKER_BIN"), "{}", refusal.message);
}

// --- the plugin configuration of the pinned CLI -----------------------------------------------------------------

/// The plugin directory is merged into the configuration, so the `auths` of a `docker login` for a publish survive.
#[test]
fn the_plugin_directory_is_merged_and_keeps_every_other_key() {
    let directory = Path::new("/bazel/external/+http_file+air_docker_buildx_darwin_arm64/file");
    let (fresh, replaced) = with_cli_plugin_directory(None, directory);
    assert!(!replaced);
    assert_eq!(fresh, serde_json::json!({ "cliPluginsExtraDirs": [directory] }));

    let logged_in = br#"{"auths":{"registry.example":{"auth":"c2VjcmV0"}},"cliPluginsExtraDirs":["/old"]}"#;
    let (merged, replaced) = with_cli_plugin_directory(Some(logged_in), directory);
    assert!(!replaced);
    assert_eq!(
        merged,
        serde_json::json!({
            "auths": { "registry.example": { "auth": "c2VjcmV0" } },
            "cliPluginsExtraDirs": [directory],
        })
    );

    for damaged in [&b"not json"[..], b"[1, 2]", b"\"text\""] {
        let (value, replaced) = with_cli_plugin_directory(Some(damaged), directory);
        assert!(replaced, "{}", String::from_utf8_lossy(damaged));
        assert_eq!(value, serde_json::json!({ "cliPluginsExtraDirs": [directory] }));
    }
}

// --- the guest argv ------------------------------------------------------------------------------------------

/// `-i` exactly when there is stdin to carry, and never `-t`: a tty would rewrite the relay's bytes.
#[tokio::test]
async fn the_guest_argv_asks_for_stdin_only_when_there_is_stdin() {
    let fixture = Fixture::docker();
    let program = docker(&fixture).program();
    let argv = strings(&["/usr/bin/true"]);
    assert_eq!(
        docker(&fixture).guest_argv(&ctx(), "air-docker-1", &argv, false).await.unwrap(),
        [program.as_str(), "exec", "air-docker-1", "/usr/bin/true"]
    );
    assert_eq!(
        docker(&fixture).guest_argv(&ctx(), "air-docker-1", &argv, true).await.unwrap(),
        [program.as_str(), "exec", "-i", "air-docker-1", "/usr/bin/true"]
    );
}

// --- the container state -------------------------------------------------------------------------------------

#[tokio::test]
async fn the_state_follows_the_container_through_its_life() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Absent);
    assert!(!backend.running(&ctx(), worker).await.unwrap());
    backend.create(&ctx(), worker).await.unwrap();
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Created);
    backend.start(&ctx(), worker).await.unwrap();
    assert!(backend.running(&ctx(), worker).await.unwrap());
    backend.stop(&ctx(), worker).await.unwrap();
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Exited(0));
    backend.remove(&ctx(), worker).await.unwrap();
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Absent);
    assert!(fixture.fake.saw_call_containing(&format!("stop --time 10 {worker}")));
    assert!(fixture.fake.saw_call_containing(&format!("rm --force {worker}")));
}

#[test]
fn an_engine_state_word_is_kept_when_nothing_acts_on_it() {
    for (answer, state) in [
        ("running/0\n", ContainerState::Running),
        ("created/0", ContainerState::Created),
        ("exited/137", ContainerState::Exited(137)),
        ("paused/0", ContainerState::Other("paused".to_owned())),
    ] {
        assert_eq!(ContainerState::parse(answer), Some(state), "{answer}");
    }
    assert_eq!(ContainerState::Other("paused".to_owned()).as_str(), "paused");
    assert_eq!(ContainerState::Exited(1).as_str(), "exited");
}

/// An answer that is not `<status>/<exit code>` is no state, whatever part of it is there.
#[test]
fn an_answer_without_a_status_and_a_code_is_no_state() {
    for answer in ["", "\n", "running", "running/", "/0", "exited/x", "Running/0", "run ning/0"] {
        assert_eq!(ContainerState::parse(answer), None, "{answer:?}");
    }
}

/// An `inspect` that exits 0 and prints nothing is a refusal that names the command and what was printed, and never a
/// state. `running` and the id agree.
#[tokio::test]
async fn an_inspect_that_prints_no_state_is_refused() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::ContainerState, "");
    for refusal in [
        backend.state(&ctx(), worker).await.unwrap_err(),
        backend.running(&ctx(), worker).await.unwrap_err(),
    ] {
        assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
        assert_eq!(refusal.exit, Exit::TEMP_FAIL);
        assert!(
            refusal.message.contains(&format!(
                "inspect --type container --format {{{{.State.Status}}}}/{{{{.State.ExitCode}}}} {worker}` exited with 0 and \
                 printed \"\""
            )),
            "{}",
            refusal.message
        );
    }
    let refusal = backend.container_id(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
}

/// A state the fake cannot print is refused with the reason the fake gave on stderr.
#[cfg(unix)]
#[tokio::test]
async fn a_state_the_fake_cannot_print_is_refused_with_its_reason() {
    use std::os::unix::fs::PermissionsExt;
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::ContainerState, "running/0\n");
    let state = fixture.fake.directory().join(Answer::ContainerState.file_name());
    std::fs::set_permissions(&state, std::fs::Permissions::from_mode(0o000)).unwrap();
    let refusal = backend.state(&ctx(), worker).await.unwrap_err();
    std::fs::set_permissions(&state, std::fs::Permissions::from_mode(0o644)).unwrap();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
    assert!(
        refusal.message.contains("fake docker: cannot print the state, cat exited with 1"),
        "{}",
        refusal.message
    );
}

/// An `inspect` that a signal ended, or that printed nothing, is no answer for the state, `running` and the id. Exit
/// 137 is not "no such container".
#[tokio::test]
async fn an_inspect_without_an_answer_is_refused_and_not_absent() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::ContainerState, "running/0\n");
    fixture.fake.answer(Answer::ContainerId, "fake-container-1\n");
    for (answer, code) in [(Answer::KilledVerb, 137), (Answer::SilentVerb, 0)] {
        fixture.fake.answer(answer, "inspect");
        for refusal in [
            backend.state(&ctx(), worker).await.unwrap_err(),
            backend.running(&ctx(), worker).await.unwrap_err(),
            backend.container_id(&ctx(), worker).await.unwrap_err(),
        ] {
            assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
            assert!(refusal.message.contains(&format!("exited with {code}")), "{}", refusal.message);
        }
        fixture.fake.forget(answer);
    }
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Running);
}

/// `docker image inspect` answers by its exit: 0 is present, 1 is absent. A CLI that a signal ended said neither.
#[tokio::test]
async fn an_image_inspect_without_an_answer_is_refused_and_not_absent() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let tag = backend.image_tag();
    assert!(!backend.image_present(&ctx(), &tag).await.unwrap());
    fixture.fake.answer(Answer::SilentVerb, "image");
    assert!(
        backend.image_present(&ctx(), &tag).await.unwrap(),
        "exit 0 is the answer of image inspect"
    );
    fixture.fake.forget(Answer::SilentVerb);
    fixture.fake.answer(Answer::KilledVerb, "image");
    let refusal = backend.image_present(&ctx(), &tag).await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
    assert!(refusal.message.contains("exited with 137"), "{}", refusal.message);
}

/// A `docker version` that printed nothing names no other architecture, and one that a signal ended is no missing
/// CLI: both are no answer.
#[tokio::test]
async fn a_version_without_an_answer_is_refused_and_not_unsupported() {
    let fixture = Fixture::docker();
    for (answer, code) in [(Answer::KilledVerb, 137), (Answer::SilentVerb, 0)] {
        fixture.fake.answer(answer, "version");
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
        assert!(refusal.message.contains(&format!("exited with {code}")), "{}", refusal.message);
        fixture.fake.forget(answer);
    }
    fixture.fake.answer(Answer::DockerVersion, "linux\n");
    let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "an answer without an architecture: {refusal:?}");
}

// --- the shares and the create argv --------------------------------------------------------------------------

/// The shares land at the guest paths the parity script probes, read-only, one `--mount` pair each.
#[test]
fn the_shares_are_read_only_bind_mounts_at_the_parity_paths() {
    let fixture = Fixture::docker();
    let settings = &fixture.settings;
    let declared = shares(settings).unwrap();
    let arguments = Docker::share_arguments(&declared, settings).unwrap();
    let root = fixture.root().to_string_lossy().into_owned();
    assert_eq!(
        arguments,
        [
            "--mount".to_owned(),
            format!(
                "type=bind,source={root},target=/mnt/AirVmShares/{},readonly",
                settings.repo_share_name
            ),
            "--mount".to_owned(),
            format!(
                "type=bind,source={root},target=/mnt/AirVmShares/{},readonly",
                settings.bazel_share_name
            ),
        ]
    );
}

/// A Windows host path starts with a drive and a `:`, which `--mount` carries as it is.
#[test]
fn a_windows_share_path_is_bound_as_it_is() {
    let fixture = Fixture::docker();
    let share = SharedFolder {
        name: "repo".to_owned(),
        path: PathBuf::from(r"C:\Users\air\idea"),
        mode: SHARE_MODE,
    };
    assert_eq!(
        Docker::share_arguments(&[share], &fixture.settings).unwrap(),
        [
            "--mount",
            r"type=bind,source=C:\Users\air\idea,target=/mnt/AirVmShares/repo,readonly"
        ]
    );
}

/// The `--mount` grammar is one CSV record, so a `,`, a `"` or a `=` in a host path could change the record, and an
/// empty path is a share declared before the host paths were resolved.
#[test]
fn a_share_path_the_grammar_cannot_carry_is_refused() {
    let fixture = Fixture::docker();
    for path in ["", "/Users/air/a,b", r#"/Users/air/a"b"#, "/Users/air/a=b"] {
        let share = SharedFolder {
            name: "repo".to_owned(),
            path: PathBuf::from(path),
            mode: SHARE_MODE,
        };
        let refusal = Docker::share_arguments(&[share], &fixture.settings).unwrap_err();
        assert_eq!(refusal.code, "unsafe_share_path", "{path:?}");
    }
}

#[test]
fn the_create_argv_declares_the_shares_the_volume_the_display_and_the_image() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let settings = &fixture.settings;
    let argv = backend.create_argv("air-docker-1").unwrap();
    let program = backend.program();
    let mut expected = strings(&[
        &program,
        "create",
        "--name",
        "air-docker-1",
        "--hostname",
        "air-docker-1",
        "--init",
        "--shm-size",
        "2g",
        "--ulimit",
        "nofile=65536:65536",
    ]);
    expected.extend(Docker::share_arguments(&shares(settings).unwrap(), settings).unwrap());
    expected.extend(strings(&[
        "--mount",
        "type=volume,source=air-air-docker-1-data,target=/home/admin/WorkerData",
        "-e",
        "AIR_VM_DISPLAY=:88",
    ]));
    expected.push(backend.image_tag());
    assert_eq!(argv, expected);
    // One container shares the engine VM, so no memory limit is declared.
    assert!(!argv.iter().any(|word| word.starts_with("--memory")), "{argv:?}");

    let screen = Fixture::docker_builder().env("AIR_VM_SCREEN", "2560x1440x24").build();
    let argv = docker(&screen).create_argv("air-docker-1").unwrap();
    let at = argv
        .iter()
        .position(|word| word == "AIR_VM_SCREEN=2560x1440x24")
        .expect("the screen is passed to the entrypoint");
    assert_eq!(argv[at - 1], "-e");
}

/// The record is what a create declared, and any damage reads as "no record": the next start then makes the
/// container again rather than trusting one it cannot vouch for.
#[tokio::test]
async fn a_create_records_its_argv_and_a_damaged_record_is_none() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    assert!(!backend.create_record_is_current(worker).unwrap());
    backend.create(&ctx(), worker).await.unwrap();
    let record = backend.read_create_record(worker).expect("a record");
    assert_eq!(record.argv, backend.create_argv(worker).unwrap());
    assert_eq!(record.container_id, "fake-container-1");
    assert!(backend.create_record_is_current(worker).unwrap());
    assert!(backend.container_is_current(&ctx(), worker).await.unwrap());
    let path = fixture.settings.docker_create_record_path(worker);
    assert!(
        std::fs::read_to_string(&path)
            .unwrap()
            .starts_with(&format!(r#"{{"schemaVersion":1,"worker":"{worker}","argv":["#)),
    );

    for damaged in [
        "{".to_owned(),
        r#"{"schemaVersion":2,"worker":"air-docker-1","argv":["docker"]}"#.to_owned(),
        r#"{"schemaVersion":1,"worker":"air-docker-2","argv":["docker"]}"#.to_owned(),
        r#"{"schemaVersion":1,"worker":"air-docker-1","argv":[]}"#.to_owned(),
    ] {
        std::fs::write(&path, &damaged).unwrap();
        assert_eq!(backend.read_create_record(worker), None, "{damaged}");
    }
    // A record of other arguments is read, and is not current.
    std::fs::write(
        &path,
        r#"{"schemaVersion":1,"worker":"air-docker-1","argv":["docker","create","old"]}"#,
    )
    .unwrap();
    assert!(backend.read_create_record(worker).is_some());
    assert!(!backend.create_record_is_current(worker).unwrap());

    // Removing the container removes its record; the volume stays.
    backend.remove(&ctx(), worker).await.unwrap();
    assert!(!path.exists());
    assert!(!fixture.fake.saw_call_containing("volume rm"));
}

// --- the image -----------------------------------------------------------------------------------------------

/// The tag is a digest of every input, so a change to any of them builds a new image, and it is stable: the same
/// inputs never build twice. A reordered package list is the same image.
#[test]
fn the_image_tag_is_a_stable_digest_of_what_the_image_is_built_from() {
    let packages = ["xvfb", "fluxbox"];
    let tag = image_tag_for("air-ui-worker", "FROM x", "#!/bin/sh", "ubuntu@sha256:1", &packages);
    let (repository, digest) = tag.split_once(':').unwrap();
    assert_eq!(repository, "air-ui-worker");
    assert_eq!(digest.len(), 12);
    assert!(digest.bytes().all(|byte| byte.is_ascii_hexdigit()), "{digest}");
    assert_eq!(
        tag,
        image_tag_for("air-ui-worker", "FROM x", "#!/bin/sh", "ubuntu@sha256:1", &["fluxbox", "xvfb"])
    );
    for other in [
        image_tag_for("air-ui-worker", "FROM y", "#!/bin/sh", "ubuntu@sha256:1", &packages),
        image_tag_for("air-ui-worker", "FROM x", "#!/bin/bash", "ubuntu@sha256:1", &packages),
        image_tag_for("air-ui-worker", "FROM x", "#!/bin/sh", "ubuntu@sha256:2", &packages),
        image_tag_for("air-ui-worker", "FROM x", "#!/bin/sh", "ubuntu@sha256:1", &["xvfb"]),
        // Text moved from one input into the next is another digest.
        image_tag_for("air-ui-worker", "FROM x#!", "/bin/sh", "ubuntu@sha256:1", &packages),
    ] {
        assert_ne!(other, tag);
    }
}

/// The image installs the one package list of a Linux worker. The build carries every entry of it, and the
/// embedded Dockerfile reads both build arguments, so neither half can drop the list without a failing test.
#[test]
fn the_build_carries_every_guest_package_and_the_dockerfile_reads_it() {
    let fixture = Fixture::docker();
    let argv = docker(&fixture).build_argv("air-ui-worker:x", Path::new("/ctx"));
    let packages = argv
        .iter()
        .find_map(|word| word.strip_prefix(&format!("{PACKAGES_BUILD_ARG}=")))
        .expect("the package list is a build argument");
    let listed: Vec<&str> = packages.split(' ').collect();
    assert_eq!(listed, GUEST_PACKAGES);
    assert!(
        argv.contains(&format!("{BASE_BUILD_ARG}={}", fixture.settings.docker_base_image)),
        "{argv:?}"
    );
    assert_eq!(argv.last().map(String::as_str), Some("/ctx"));
    for name in [PACKAGES_BUILD_ARG, BASE_BUILD_ARG, REVISION_BUILD_ARG] {
        assert!(DOCKERFILE.contains(&format!("ARG {name}")), "the Dockerfile does not read {name}");
    }
    // The revision label is what a pulled image is checked against, so the build must write the tag digest into it.
    assert!(argv.contains(&format!("{REVISION_BUILD_ARG}=x")), "{argv:?}");
    assert!(
        DOCKERFILE.contains(&format!("LABEL {REVISION_LABEL}=${{{REVISION_BUILD_ARG}}}")),
        "{DOCKERFILE}"
    );
    // The build and not the host gives the entrypoint its mode, so a context written on Windows builds the same image.
    assert!(DOCKERFILE.contains("COPY --chmod=0755 air-display "), "{DOCKERFILE}");
    // The fluxbox overlay keeps the style from running `fbsetbg`, so the image needs no wallpaper setter.
    assert!(
        DOCKERFILE.contains("background: unset") && !DOCKERFILE.contains(" feh "),
        "{DOCKERFILE}"
    );
    assert!(ENTRYPOINT.starts_with("#!"), "the entrypoint is not a script");
}

/// The image carries the Node of the pinned major, so `NODE_MAJOR` of `versions.env` and the image cannot drift apart.
/// One `NODE_VERSION` names the archive and the URL path, so the two cannot name different releases. The archive is
/// checked by its sha256 and goes into `/usr/local`, where [`Config::vm_node`] finds it. The layer comes before the
/// revision, so a change of the tag alone keeps it in the build cache.
#[test]
fn the_dockerfile_installs_the_pinned_node_major() {
    let major = avl_base::config::pins::guest_node_major();
    let version = DOCKERFILE
        .lines()
        .find_map(|line| line.strip_prefix("ARG NODE_VERSION="))
        .expect("the Dockerfile pins NODE_VERSION");
    assert!(
        version.starts_with(&format!("{major}.")),
        "NODE_VERSION={version} is not Node {major}"
    );
    assert_eq!(version.split('.').count(), 3, "{version}");
    // The shell references are built from their names, because a literal `{NAME}` reads as a format argument.
    let shell = |name: &str| format!("${{{name}}}");
    let archive = format!(r#"archive="node-v{}-linux-{}.tar.gz""#, shell("NODE_VERSION"), shell("node_arch"));
    let url = format!(r#""https://nodejs.org/dist/v{}/{}""#, shell("NODE_VERSION"), shell("archive"));
    assert!(
        DOCKERFILE.contains(&archive),
        "the archive name is not built from NODE_VERSION: {DOCKERFILE}"
    );
    assert!(
        DOCKERFILE.contains(&url),
        "the URL path is not built from NODE_VERSION: {DOCKERFILE}"
    );
    // The version is read once: no other line names a release.
    assert!(
        !DOCKERFILE.contains(&format!("v{version}")),
        "a line spells the release by hand: {DOCKERFILE}"
    );
    assert!(DOCKERFILE.contains("ARG TARGETARCH"), "{DOCKERFILE}");
    assert!(DOCKERFILE.contains("sha256sum --check"), "{DOCKERFILE}");
    assert!(DOCKERFILE.contains("--directory /opt/node --strip-components=1"), "{DOCKERFILE}");
    assert!(DOCKERFILE.contains("COPY --from=node /opt/node/ /usr/local/"), "{DOCKERFILE}");
    let node_layer = DOCKERFILE.find("ARG NODE_VERSION=").unwrap();
    let revision = DOCKERFILE.find(&format!("ARG {REVISION_BUILD_ARG}")).unwrap();
    assert!(node_layer < revision, "the Node layer comes after the revision");
    assert_eq!(Fixture::docker().settings.vm_node, "/usr/local/bin/node");
}

// The package list is the one place these names live, so what each group is for is pinned: without the first the
// guest has no display and no window manager, and without the second the IDE starts and logs one SEVERE line about
// Skiko while the lane keeps running.
#[test]
fn the_guest_packages_cover_the_display_and_the_ides_own_native_dependencies() {
    let present: std::collections::HashSet<&str> = GUEST_PACKAGES.iter().copied().collect();
    assert_eq!(present.len(), GUEST_PACKAGES.len(), "a package is listed twice");
    for needed in ["xvfb", "fluxbox", "x11-utils"] {
        assert!(present.contains(needed), "the display group is missing {needed}");
    }
    // No Node and no npm: Ubuntu's `nodejs` is too old for the agent CLIs. The Dockerfile installs a pinned Node.
    for refused in ["nodejs", "npm"] {
        assert!(!present.contains(refused), "{refused} is in the install set");
    }
    for needed in [
        "libegl1",
        "libgtk-3-0t64",
        "libxdamage1",
        "libxfixes3",
        "libasound2t64",
        "libatk1.0-0t64",
        "libatk-bridge2.0-0t64",
        "libatspi2.0-0t64",
        "libnss3",
    ] {
        assert!(present.contains(needed), "the IDE's native dependencies are missing {needed}");
    }
    // The recorder encodes the video itself.
    assert!(
        !present.contains("ffmpeg"),
        "the guest installs ffmpeg, which the trace recorder does not need"
    );
}

// Positional contract with the guest agent's `validate-guest`: the display, then the runtime root whose shared
// objects it sweeps. Not the IDE root, where nothing is staged and the sweep would find nothing on every boot.
#[test]
fn the_validate_argv_passes_what_the_guest_is_checked_against() {
    let fixture = Fixture::docker();
    let argv = validate_argv(&fixture.settings);
    assert_eq!(
        argv,
        [fixture.settings.guest_display.clone(), fixture.settings.guest_runtime_root()]
    );
    for word in &argv {
        assert!(!GUEST_PACKAGES.contains(&word.as_str()), "the package list leaked in: {argv:?}");
        assert!(!word.contains("node"), "the self-check is told about a Node: {argv:?}");
    }
}

/// The first `ensure_image` tries the registry, and when the pull fails it builds from a fresh context and keeps
/// both logs; the next one finds the image and neither pulls nor builds.
#[tokio::test]
async fn an_image_is_built_once_from_a_fresh_context() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let tag = backend.ensure_image(&ctx()).await.unwrap();
    assert_eq!(tag, backend.image_tag());
    let remote = backend.remote_reference(&tag).unwrap();
    assert_eq!(
        remote,
        format!(
            "registry.jetbrains.team/p/ij/containers-public/air-ui-worker:{}",
            tag.rsplit(':').next().unwrap()
        )
    );
    assert!(fixture.fake.saw_call_containing(&format!("pull {remote}")));
    assert!(fixture.settings.docker_pull_log_path().exists());
    assert_eq!(
        backend.read_image_record().unwrap(),
        ImageRecord {
            schema_version: SCHEMA_VERSION,
            tag: tag.clone(),
            source: ImageSource::Built,
        }
    );
    let builds = || fixture.fake.calls().into_iter().filter(|call| call.starts_with("build ")).count();
    assert_eq!(builds(), 1);
    let digest = tag.rsplit(':').next().unwrap();
    let context = fixture.settings.runtime_root.join("docker-context").join(digest);
    assert_eq!(std::fs::read_to_string(context.join("Dockerfile")).unwrap(), DOCKERFILE);
    assert_eq!(std::fs::read_to_string(context.join("air-display")).unwrap(), ENTRYPOINT);
    assert!(fixture.settings.docker_build_log_path().exists());

    let pulls = fixture.fake.calls().into_iter().filter(|call| call.starts_with("pull ")).count();
    assert_eq!(backend.ensure_image(&ctx()).await.unwrap(), tag);
    assert_eq!(builds(), 1, "a present image is built again");
    assert_eq!(
        fixture.fake.calls().into_iter().filter(|call| call.starts_with("pull ")).count(),
        pulls,
        "a present image is pulled again"
    );
    // Nothing is published unless the operator asks.
    assert!(!fixture.fake.saw_call_containing("buildx "));
}

/// A registry that holds the tag serves the worker: the pulled reference is checked against the tag digest through
/// its revision label, tagged with the local name, and recorded as pulled. Nothing is built.
#[tokio::test]
async fn a_registry_image_that_says_the_digest_is_pulled_and_not_built() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let tag = backend.image_tag();
    let digest = tag.rsplit(':').next().unwrap().to_owned();
    fixture.fake.answer(Answer::PullExit, "0");
    fixture.fake.answer(Answer::ImageRevision, format!("{digest}\n"));
    assert_eq!(backend.ensure_image(&ctx()).await.unwrap(), tag);
    let remote = backend.remote_reference(&tag).unwrap();
    let calls = fixture.fake.calls();
    assert!(calls.contains(&format!("pull {remote}")), "{calls:#?}");
    assert!(
        calls.contains(&format!(
            "image inspect --format {{{{index .Config.Labels \"{REVISION_LABEL}\"}}}} {remote}"
        )),
        "{calls:#?}"
    );
    assert!(calls.contains(&format!("tag {remote} {tag}")), "{calls:#?}");
    assert!(!calls.iter().any(|call| call.starts_with("build ")), "{calls:#?}");
    assert!(backend.image_present(&ctx(), &tag).await.unwrap());
    assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Pulled);
    assert!(!fixture.settings.docker_build_log_path().exists());
}

/// A pulled image whose revision label is not the tag digest is not trusted: it is removed from the engine and the
/// image is built, so a registry mistake costs a build and never a worker on other bytes.
#[tokio::test]
async fn a_pulled_image_whose_label_is_not_the_digest_is_removed_and_the_image_is_built() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let tag = backend.image_tag();
    fixture.fake.answer(Answer::PullExit, "0");
    fixture.fake.answer(Answer::ImageRevision, "deadbeefcafe\n");
    assert_eq!(backend.ensure_image(&ctx()).await.unwrap(), tag);
    let remote = backend.remote_reference(&tag).unwrap();
    let calls = fixture.fake.calls();
    assert!(calls.contains(&format!("image rm {remote}")), "{calls:#?}");
    assert!(!calls.contains(&format!("tag {remote} {tag}")), "{calls:#?}");
    assert!(calls.iter().any(|call| call.starts_with("build ")), "{calls:#?}");
    assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Built);
    // The mismatched reference is gone from the engine.
    assert!(!fixture.fake.saw_call_containing("nonexistent"));
    assert!(
        !backend.image_present(&ctx(), &remote).await.unwrap(),
        "the mismatched image stayed on the engine"
    );
}

/// `AIR_VM_DOCKER_PUSH` makes the controller build, even over a present tag, never pull, and publish the tag for
/// both platforms under the registry reference from the same context and build arguments. A publish that fails is
/// the operator's refusal, with the push log named.
#[tokio::test]
async fn a_build_pushes_when_asked() {
    let fixture = Fixture::docker_builder().env("AIR_VM_DOCKER_PUSH", "1").build();
    fixture.fake.answer(Answer::ImagePresent, "");
    fixture.fake.answer(Answer::PullExit, "0");
    let backend = docker(&fixture);
    let tag = backend.ensure_image(&ctx()).await.unwrap();
    let remote = backend.remote_reference(&tag).unwrap();
    let argvs = fixture.fake.argvs();
    let position = |verb: &str| {
        argvs
            .iter()
            .position(|argv| argv.first().is_some_and(|word| word == verb))
            .unwrap_or_else(|| panic!("no {verb:?} call in {argvs:#?}"))
    };
    assert!(!argvs.iter().any(|argv| argv.first().is_some_and(|word| word == "pull")));
    assert!(position("build") < position("buildx"));
    let build = &argvs[position("build")];
    let publish = &argvs[position("buildx")];
    let context = build.last().unwrap();
    let expected: Vec<String> = [
        "buildx",
        "build",
        "--platform",
        "linux/arm64,linux/amd64",
        "--provenance=false",
        "--push",
        "--tag",
        &remote,
    ]
    .map(str::to_owned)
    .into_iter()
    .chain(build[3..build.len() - 1].iter().cloned())
    .chain(std::iter::once(context.clone()))
    .collect();
    assert_eq!(publish, &expected);
    assert!(
        publish
            .iter()
            .any(|word| word == &format!("{REVISION_BUILD_ARG}={}", tag.rsplit(':').next().unwrap()))
    );
    assert!(fixture.settings.docker_push_log_path().exists());

    // A failed publish names its log. The answers of the first case are forgotten. The image it built stays, and a
    // publish builds again, as the first case shows with an image present.
    fixture.fake.forget_calls();
    fixture.fake.forget(Answer::ImagePresent);
    fixture.fake.forget(Answer::PullExit);
    fixture.fake.answer(Answer::PushExit, "1");
    let refusal = docker(&fixture).ensure_image(&ctx()).await.unwrap_err();
    assert_eq!(refusal.code, "docker_push_failed");
    assert!(
        refusal
            .message
            .contains(&fixture.settings.docker_push_log_path().display().to_string()),
        "{}",
        refusal.message
    );
}

/// `AIR_VM_DOCKER_REGISTRY=off` is a controller that builds and never talks to a registry.
#[tokio::test]
async fn a_registry_of_off_pulls_nothing() {
    let fixture = Fixture::docker_builder().env("AIR_VM_DOCKER_REGISTRY", "off").build();
    let backend = docker(&fixture);
    assert_eq!(backend.remote_reference("air-ui-worker:x"), None);
    backend.ensure_image(&ctx()).await.unwrap();
    let calls = fixture.fake.calls();
    assert!(!calls.iter().any(|call| call.starts_with("pull ")), "{calls:#?}");
    assert!(calls.iter().any(|call| call.starts_with("build ")));
    assert!(!fixture.settings.docker_pull_log_path().exists());
}

/// A damaged image record, or one of another schema, vouches for nothing.
#[test]
fn a_damaged_image_record_is_none() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let path = fixture.settings.docker_image_record_path();
    assert_eq!(backend.read_image_record(), None);
    for damaged in ["{", r#"{"schemaVersion":2,"tag":"a:b","source":"pulled"}"#] {
        std::fs::write(&path, damaged).unwrap();
        assert_eq!(backend.read_image_record(), None, "{damaged}");
    }
    std::fs::write(&path, r#"{"schemaVersion":1,"tag":"a:b","source":"pulled"}"#).unwrap();
    assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Pulled);
}

/// `image_present` answers from `docker image inspect`, before and after the build, and builds nothing itself.
#[tokio::test]
async fn image_present_answers_from_image_inspect() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let tag = backend.image_tag();
    assert!(!backend.image_present(&ctx(), &tag).await.unwrap());
    backend.ensure_image(&ctx()).await.unwrap();
    assert!(backend.image_present(&ctx(), &tag).await.unwrap());
    let calls = fixture.fake.calls();
    assert!(
        calls.iter().any(|call| call == &format!("image inspect {tag}")),
        "no image inspect in {calls:?}"
    );
    assert_eq!(calls.iter().filter(|call| call.starts_with("build ")).count(), 1);
}

#[tokio::test]
async fn a_failed_build_names_its_log() {
    let fixture = Fixture::docker();
    fixture.fake.answer(Answer::Exit, "1");
    let refusal = docker(&fixture).ensure_image(&ctx()).await.unwrap_err();
    assert_eq!(refusal.code, "docker_build_failed");
    assert!(
        refusal
            .message
            .contains(&fixture.settings.docker_build_log_path().display().to_string()),
        "{}",
        refusal.message
    );
}

// --- which container a record is about ----------------------------------------------------------------------

/// `DOCKER_BIN` names the CLI, not the container, so a respelling of it keeps the record current.
#[tokio::test]
async fn a_respelled_docker_bin_keeps_the_record_current() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    backend.create(&ctx(), worker).await.unwrap();
    let path = fixture.settings.docker_create_record_path(worker);
    let mut record = backend.read_create_record(worker).unwrap();
    record.argv[0] = "/usr/local/bin/docker".to_owned();
    std::fs::write(&path, serde_json::to_string(&record).unwrap()).unwrap();
    assert!(backend.create_record_is_current(worker).unwrap());
    assert!(backend.container_is_current(&ctx(), worker).await.unwrap());
}

/// A container of the same name with another id was not made by this record's create: made by hand, or made again
/// by another controller. The record cannot vouch for it, and a record with no id vouches for nothing.
#[tokio::test]
async fn a_container_of_another_id_is_not_current() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    backend.create(&ctx(), worker).await.unwrap();
    fixture.fake.answer(Answer::ContainerId, "hand-made\n");
    assert!(backend.create_record_is_current(worker).unwrap());
    assert!(!backend.container_is_current(&ctx(), worker).await.unwrap());
    assert!(
        fixture
            .fake
            .saw_call_containing(&format!("inspect --type container --format {{{{.Id}}}} {worker}"))
    );

    fixture.fake.answer(Answer::ContainerId, "fake-container-1\n");
    assert!(backend.container_is_current(&ctx(), worker).await.unwrap());
    let path = fixture.settings.docker_create_record_path(worker);
    let mut record = backend.read_create_record(worker).unwrap();
    record.container_id = String::new();
    std::fs::write(&path, serde_json::to_string(&record).unwrap()).unwrap();
    assert!(!backend.container_is_current(&ctx(), worker).await.unwrap());
    // A record written before the id existed parses, and is not current.
    let old = serde_json::json!({
        "schemaVersion": 1, "worker": worker, "argv": backend.create_argv(worker).unwrap(),
    });
    std::fs::write(&path, old.to_string()).unwrap();
    assert_eq!(backend.read_create_record(worker).unwrap().container_id, "");
    assert!(!backend.container_is_current(&ctx(), worker).await.unwrap());
}

/// A paused container reports `.State.Running=true`, and answers no `docker exec`. It is not running here.
#[tokio::test]
async fn a_paused_container_is_not_running() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    fixture.fake.answer(Answer::ContainerState, "paused/0\n");
    assert!(!backend.running(&ctx(), fixture.worker(0)).await.unwrap());
    assert_eq!(
        backend.state(&ctx(), fixture.worker(0)).await.unwrap(),
        ContainerState::Other("paused".to_owned())
    );
    fixture.fake.answer(Answer::ContainerState, "running/0\n");
    assert!(backend.running(&ctx(), fixture.worker(0)).await.unwrap());
}

/// `remove_stopped` leaves a running container to the engine's refusal, and removes a stopped one with its record.
#[tokio::test]
async fn remove_stopped_spares_a_running_container() {
    let fixture = Fixture::docker();
    let backend = docker(&fixture);
    let worker = fixture.worker(0);
    backend.create(&ctx(), worker).await.unwrap();
    backend.start(&ctx(), worker).await.unwrap();
    let refusal = backend.remove_stopped(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Running);
    assert!(fixture.settings.docker_create_record_path(worker).exists());

    backend.stop(&ctx(), worker).await.unwrap();
    backend.remove_stopped(&ctx(), worker).await.unwrap();
    assert_eq!(backend.state(&ctx(), worker).await.unwrap(), ContainerState::Absent);
    assert!(!fixture.settings.docker_create_record_path(worker).exists());
    assert!(!fixture.fake.saw_call_containing("rm --force"));
}

#[test]
fn a_docker_pool_is_a_linux_pool() {
    let fixture = Fixture::docker();
    assert_eq!(fixture.settings.guest_os, GuestOs::Linux);
    assert_eq!(Docker::volume_name("air-docker-1"), "air-air-docker-1-data");
}
