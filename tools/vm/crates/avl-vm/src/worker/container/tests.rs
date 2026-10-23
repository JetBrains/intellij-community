//! The Apple `container` engine's suite. The parsers are pure and run on every host. The lifecycle tests run over the
//! fake `container` of the shared hypervisor fake, a shell script, so they are Unix only.
//!
//! The JSON answers below are the ones the CLI 1.5.0 printed on 2026-10-09, cut to the fields the controller reads.

use pretty_assertions::assert_eq;

use super::*;
use crate::worker::docker::{ContainerState, apple_container_id, apple_image_revision};

/// `system status --format json` of a server that runs, as the spike CLI printed it.
const RUNNING: &str = r#"{"client":{"appName":"container","build":"release","version":"1.5.0"},"host":{"architecture":"arm64","cpus":18},"paths":{"appRoot":"/Users/air/Library/Application Support/com.apple.container/","installRoot":"/opt/container/"},"resources":{"containersRunning":0},"server":{"appName":"container-apiserver","version":"1.5.0"},"status":"running"}"#;

#[test]
fn the_system_status_is_read_from_the_json_of_the_cli() {
    let status: SystemStatus = serde_json::from_str(RUNNING).unwrap();
    assert!(status.running());
    assert_eq!(status.paths.install_root, "/opt/container/");
    assert_eq!((status.client.version.as_str(), status.server.version.as_str()), ("1.5.0", "1.5.0"));
    assert_eq!(status.host.architecture, "arm64");
    let down: SystemStatus = serde_json::from_str(r#"{"status":"unregistered"}"#).unwrap();
    assert!(!down.running());
    assert_eq!(
        (major_version("1.5.0"), major_version("12.0"), major_version("")),
        (Some(1), Some(12), None)
    );
}

/// The install root of a CLI is the grandparent of its path, and the trailing `/` of the status does not matter.
#[test]
fn the_install_root_is_the_grandparent_of_the_cli() {
    let root = install_root_of(Path::new("/opt/container/bin/container"));
    assert_eq!(root, Path::new("/opt/container"));
    assert!(same_root(&root, "/opt/container/"));
    assert!(!same_root(&root, "/usr/local/"));
}

/// A loopback resolver, such as the one of a VPN client, and a scoped address are skipped. The first other nameserver
/// wins, and a host without one has none.
#[test]
fn the_nameserver_is_the_first_routable_one_of_the_host() {
    let vpn = "resolver #1\n  search domain[0] : localdomain\n  nameserver[0] : 127.0.2.2\n  nameserver[1] : 127.0.2.3\n";
    assert_eq!(first_routable_nameserver(vpn), None);
    let mixed = format!(
        "{vpn}\nresolver #2\n  nameserver[0] : fe80::1%en0\n  nameserver[1] : 0.0.0.0\n  nameserver[2] : 10.1.2.3\n\
         \nresolver #3\n  nameserver[0] : 192.168.1.1\n"
    );
    assert_eq!(first_routable_nameserver(&mixed), Some("10.1.2.3".parse().unwrap()));
    assert_eq!(
        first_routable_nameserver("  nameserver[0] : 2001:db8::53\n"),
        Some("2001:db8::53".parse().unwrap())
    );
}

/// `inspect` answers JSON: a created container and an exited one are both `stopped`, with no exit code. The id is the
/// name. An answer of another shape is no state.
#[test]
fn the_apple_inspect_is_read_from_its_json() {
    let answer = |state: &str| {
        format!(
            r#"[{{"configuration":{{"id":"air-docker-1","creationDate":"2026-10-09T07:29:06Z"}},"id":"air-docker-1","status":{{"networks":[],"state":"{state}"}}}}]"#
        )
    };
    assert_eq!(ContainerState::parse_apple(&answer("running")), Some(ContainerState::Running));
    assert_eq!(ContainerState::parse_apple(&answer("stopped")), Some(ContainerState::Stopped));
    assert_eq!(
        ContainerState::parse_apple(&answer("stopping")),
        Some(ContainerState::Other("stopping".to_owned()))
    );
    assert_eq!(ContainerState::parse_apple("[]"), Some(ContainerState::Absent));
    for broken in [
        "",
        "running/0",
        "[{}]",
        r#"[{"status":{"state":"Running"}}]"#,
        r#"{"status":"running"}"#,
    ] {
        assert_eq!(ContainerState::parse_apple(broken), None, "{broken:?}");
    }
    assert_eq!(ContainerState::Stopped.as_str(), "stopped");
    assert_eq!(apple_container_id(&answer("running")).as_deref(), Some("air-docker-1"));
    assert_eq!(apple_container_id("[]"), None);
}

/// The revision label sits in the image config of a variant. An image without it says the empty revision, as in the
/// Docker dialect.
#[test]
fn the_apple_image_revision_is_read_from_a_variant() {
    let image = r#"[{"configuration":{},"id":"x","variants":[{"platform":{"os":"linux"}},{"config":{"config":{"Labels":{"org.opencontainers.image.revision":"5b1e0c0ffee1"}}}}]}]"#;
    assert_eq!(apple_image_revision(image), "5b1e0c0ffee1");
    assert_eq!(apple_image_revision(r#"[{"variants":[]}]"#), "");
    assert_eq!(apple_image_revision("not json"), "");
}

/// The controller parses the JSON of one major version of the CLI, so the pin of `container.MODULE.bazel` stays at it.
/// The pin test data is read on Unix only, as the other pin tests do.
#[cfg(unix)]
#[test]
fn the_pinned_container_has_the_major_version_the_controller_speaks() {
    let pinned = crate::worker::pin::module_pin("container.MODULE.bazel", "AIR_CONTAINER_VERSION");
    assert_eq!(pinned[0], 1, "{pinned:?}");
}

// --- the lifecycle over the fake `container` -----------------------------------------------------------------------

#[cfg(unix)]
mod lifecycle {
    use avl_base::config::{CONTAINER_WORKER_MEMORY_MIB, MacosHost};
    use avl_testkit::tartfake::{Answer, MIRROR_HEADER, MIRROR_HEADER_MODE, MIRROR_UPLOAD};

    use pretty_assertions::assert_eq;

    use super::*;
    use crate::worker::docker::{Docker, ImageSource};
    use crate::worker::pin::PinnedTools;
    use crate::worker::testing::{Fixture, ctx, docker, position, start_again, start_first};
    use crate::worker::worker::{PoolCommand, PoolTarget};

    const START: &str = "system start --enable-kernel-install";

    /// The head of the download of the image archive, as the fake `curl` records it without its program.
    const DOWNLOAD: &str = "--fail --silent --show-error --location --output ";

    fn digest(tag: &str) -> &str {
        tag.rsplit(':').next().unwrap()
    }

    fn strings(words: &[&str]) -> Vec<String> {
        words.iter().map(|word| (*word).to_owned()).collect()
    }

    /// The mirror URL is `<mirror>/<repository>/<digest>-linux-<arch>.tar`. The download, the load, the save and the
    /// upload are the argv of the design, and the token is in no argv.
    #[test]
    fn the_mirror_commands_are_the_argv_of_the_design() {
        let fixture = Fixture::docker_builder()
            .container_engine()
            .env("AIR_VM_IMAGE_MIRROR_TOKEN", "secret-token")
            .build();
        let backend = docker(&fixture);
        let tag = "air-ui-worker:3d61c9831be6";
        let url =
            "https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/air-ui-worker/3d61c9831be6-linux-arm64.tar";
        assert_eq!(backend.mirror_url(tag).as_deref(), Some(url));
        let curl = fixture.settings.host_curl.as_str();
        let archive = Path::new("/runtime/docker-context/3d61c9831be6/3d61c9831be6-linux-arm64.tar");
        assert_eq!(
            backend.download_argv(url, archive),
            strings(&[
                curl,
                "--fail",
                "--silent",
                "--show-error",
                "--location",
                "--output",
                "/runtime/docker-context/3d61c9831be6/3d61c9831be6-linux-arm64.tar",
                url,
            ])
        );
        let program = backend.program();
        assert_eq!(
            backend.load_argv(archive)[1..],
            strings(&["image", "load", "-i", &archive.display().to_string()])
        );
        assert_eq!(
            backend.save_argv(tag, archive)[1..],
            strings(&[
                "image",
                "save",
                "--platform",
                "linux/arm64",
                "-o",
                &archive.display().to_string(),
                tag
            ])
        );
        assert_eq!(backend.load_argv(archive)[0], program);
        let header = Path::new("/runtime/docker-context/3d61c9831be6/.mirror-authorization-x.tmp");
        let upload = backend.upload_argv(archive, header, url);
        assert_eq!(
            upload,
            strings(&[
                curl,
                "--fail",
                "--silent",
                "--show-error",
                "--upload-file",
                &archive.display().to_string(),
                "--header",
                "@/runtime/docker-context/3d61c9831be6/.mirror-authorization-x.tmp",
                url,
            ])
        );
        assert!(!upload.iter().any(|word| word.contains("secret-token")), "{upload:?}");

        let off = Fixture::docker_builder()
            .container_engine()
            .env("AIR_VM_IMAGE_MIRROR", "off")
            .build();
        assert_eq!(docker(&off).mirror_url(tag), None);
    }

    /// A Docker pool on the Apple `container` engine whose engine is available.
    async fn available_container() -> Fixture {
        let fixture = Fixture::docker_container();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        fixture
    }

    /// An available `container` pool whose mirror holds an archive of the image tag of the pool. The image of the
    /// archive carries the revision label that `revision` gives for the tag. Answers the fixture and the tag.
    async fn mirror_with_archive(revision: impl FnOnce(&str) -> String) -> (Fixture, String) {
        let fixture = available_container().await;
        let tag = docker(&fixture).image_tag();
        fixture.fake.answer(Answer::MirrorArchive, format!("{tag}\n"));
        fixture.fake.answer(Answer::ImageRevision, revision(&tag));
        (fixture, tag)
    }

    /// An archive on the mirror serves the worker: it is downloaded, loaded under the tag, checked against the tag
    /// digest through its revision label, and recorded as pulled. Nothing is built, and the archive is removed.
    #[tokio::test]
    async fn an_archive_on_the_mirror_is_loaded_and_not_built() {
        let (fixture, tag) = mirror_with_archive(|tag| digest(tag).to_owned()).await;
        let backend = docker(&fixture);
        fixture.fake.forget_calls();
        assert_eq!(backend.ensure_image(&ctx()).await.unwrap(), tag);
        let calls = fixture.fake.calls();
        let url = backend.mirror_url(&tag).unwrap();
        let download = &calls[position(&calls, DOWNLOAD)];
        assert!(download.ends_with(&format!("-linux-arm64.tar {url}")), "{download}");
        assert!(position(&calls, DOWNLOAD) < position(&calls, "image load -i "));
        let load = position(&calls, "image load -i ");
        assert!(calls[load..].contains(&format!("image inspect {tag}")), "{calls:#?}");
        assert!(
            !calls
                .iter()
                .any(|call| call.starts_with("build ") || call.starts_with("image pull ") || call.starts_with("image tag ")),
            "{calls:#?}"
        );
        assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Pulled);
        let directory = fixture.settings.runtime_root.join("docker-context").join(digest(&tag));
        assert!(!directory.join(format!("{}-linux-arm64.tar", digest(&tag))).exists());
        assert!(!fixture.settings.docker_build_log_path().exists());
    }

    /// A loaded image whose revision label is not the tag digest is deleted from the engine and the image is built. An
    /// archive that holds no image of the tag is built too.
    #[tokio::test]
    async fn an_archive_of_other_bytes_is_not_trusted_and_the_image_is_built() {
        let (fixture, tag) = mirror_with_archive(|_| "deadbeefcafe".to_owned()).await;
        let backend = docker(&fixture);
        assert_eq!(backend.ensure_image(&ctx()).await.unwrap(), tag);
        let calls = fixture.fake.calls();
        assert!(position(&calls, &format!("image delete {tag}")) < position(&calls, "build "));
        assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Built);

        let fixture = available_container().await;
        let backend = docker(&fixture);
        fixture.fake.answer(Answer::MirrorArchive, "air-ui-worker:000000000000\n");
        assert_eq!(backend.ensure_image(&ctx()).await.unwrap(), tag);
        let calls = fixture.fake.calls();
        assert!(position(&calls, "image load -i ") < position(&calls, "build "));
        assert!(!calls.iter().any(|call| call.starts_with("image delete ")), "{calls:#?}");
        assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Built);
    }

    /// `AIR_VM_IMAGE_MIRROR=off` builds and never downloads.
    #[tokio::test]
    async fn a_mirror_of_off_downloads_nothing() {
        let fixture = Fixture::docker_builder()
            .container_engine()
            .env("AIR_VM_IMAGE_MIRROR", "off")
            .build();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        docker(&fixture).ensure_image(&ctx()).await.unwrap();
        let calls = fixture.fake.calls();
        assert!(!calls.iter().any(|call| call.starts_with("--fail")), "{calls:#?}");
        position(&calls, "build ");
        assert!(!fixture.settings.docker_pull_log_path().exists());
    }

    /// A publish without the mirror token is refused before the build, with its own code.
    #[tokio::test]
    async fn a_publish_without_a_token_is_refused_before_the_build() {
        let fixture = Fixture::docker_builder().container_engine().env("AIR_VM_DOCKER_PUSH", "1").build();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        fixture.fake.forget_calls();
        let refusal = docker(&fixture).ensure_image(&ctx()).await.unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("image_mirror_token_missing", Exit::USAGE));
        assert!(refusal.message.contains("AIR_VM_IMAGE_MIRROR_TOKEN"), "{}", refusal.message);
        assert!(fixture.fake.calls().is_empty(), "{:#?}", fixture.fake.calls());
    }

    /// The publish builds, saves the archive of the host platform into the build context, and uploads it to the mirror
    /// URL. The token travels in a private header file, never in an argv, and the archive and the header file are gone
    /// after the upload. A failed upload is `docker_push_failed` and names the push log.
    #[tokio::test]
    async fn a_publish_uploads_the_archive_of_the_host_platform() {
        let fixture = Fixture::docker_builder()
            .container_engine()
            .env("AIR_VM_DOCKER_PUSH", "1")
            .env("AIR_VM_IMAGE_MIRROR_TOKEN", "secret-token")
            .build();
        let backend = docker(&fixture);
        backend.require_available(&ctx(), "").await.unwrap();
        let tag = backend.ensure_image(&ctx()).await.unwrap();
        let calls = fixture.fake.calls();
        let context = fixture.settings.runtime_root.join("docker-context").join(digest(&tag));
        let archive = context.join(format!("{}-linux-arm64.tar", digest(&tag)));
        let save = format!("image save --platform linux/arm64 -o {} {tag}", archive.display());
        assert!(position(&calls, "build ") < position(&calls, &save));
        let upload_head = format!("--fail --silent --show-error --upload-file {} --header @", archive.display());
        assert!(position(&calls, &save) < position(&calls, &upload_head));
        let upload = &calls[position(&calls, &upload_head)];
        assert!(upload.ends_with(&format!(" {}", backend.mirror_url(&tag).unwrap())), "{upload}");
        assert!(!calls.iter().any(|call| call.contains("secret-token")), "{calls:#?}");
        assert!(
            !calls.iter().any(|call| call.starts_with("--fail --silent --show-error --location")),
            "{calls:#?}"
        );
        let beside = |name: &str| std::fs::read_to_string(fixture.fake.directory().join(name)).unwrap();
        assert_eq!(beside(MIRROR_UPLOAD), format!("{tag}\n"));
        assert_eq!(beside(MIRROR_HEADER), "Authorization: Bearer secret-token\n");
        assert_eq!(beside(MIRROR_HEADER_MODE).trim(), "-rw-------");
        let left: Vec<_> = std::fs::read_dir(&context)
            .unwrap()
            .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
            .filter(|name| name.ends_with(".tar") || name.contains("mirror-authorization"))
            .collect();
        assert!(left.is_empty(), "{left:?}");
        assert_eq!(backend.read_image_record().unwrap().source, ImageSource::Built);

        fixture.fake.answer(Answer::PushExit, "22");
        let refusal = docker(&fixture).ensure_image(&ctx()).await.unwrap_err();
        let log = fixture.settings.docker_push_log_path();
        assert_eq!(refusal.code, "docker_push_failed");
        assert!(refusal.message.contains(&log.display().to_string()), "{}", refusal.message);
        assert!(std::fs::read_to_string(&log).unwrap().contains("returned error: 403"));
        assert!(!archive.exists());
    }

    /// The gate starts a server that is down, once, and logs the start. The next gate finds it running. Nothing runs a
    /// Docker CLI, nothing names a Docker configuration, and nothing stops the server.
    #[tokio::test]
    async fn the_gate_starts_a_server_that_is_down_once() {
        let fixture = Fixture::docker_container();
        assert!(fixture.settings.runs_container_engine());
        for _ in 0..2 {
            docker(&fixture).require_available(&ctx(), "").await.unwrap();
        }
        let calls = fixture.fake.calls();
        assert_eq!(calls.iter().filter(|call| call.as_str() == START).count(), 1, "{calls:#?}");
        assert!(position(&calls, "system status --format json") < position(&calls, START));
        assert!(
            !calls
                .iter()
                .any(|call| call.starts_with("system stop") || call.starts_with("version") || call.contains("--app-root")),
            "{calls:#?}"
        );
        let log = std::fs::read_to_string(fixture.settings.container_system_log_path()).unwrap();
        assert!(log.contains("starting the server"), "{log}");
        let environment = fixture.manager.runner().environment();
        assert!(
            !environment.iter().any(|(name, _)| name == "DOCKER_CONFIG" || name == "DOCKER_HOST"),
            "{environment:?}"
        );
    }

    /// A Docker backend over the settings of `fixture` after `pin` changes a host fact. The settings keep the Apple
    /// `container` engine, which the engine rule never chooses on such a host.
    fn pinned_docker(fixture: &Fixture, pin: impl FnOnce(&mut Config)) -> Docker {
        let mut settings = (*fixture.settings).clone();
        pin(&mut settings);
        let settings = Arc::new(settings);
        let runner = fixture.manager.runner().clone();
        Docker::new(
            Arc::clone(&settings),
            runner.clone(),
            avl_host_testkit::quiet(),
            Arc::new(LockManager::new(runner.clone())),
            Arc::new(PinnedTools::new(settings, runner, None)),
        )
    }

    /// The engine rule keeps the host engine on macOS 15, so only settings that pin the engine reach this gate. It
    /// refuses `container_macos_too_old` before any `container` command runs. A `CONTAINER_BIN` that names no CLI gets
    /// the same refusal, so a Mac without the tool gets it too. A host that is not Apple silicon is refused as well.
    /// The remedy names the two ways out: a Docker engine in `DOCKER_BIN` or `DOCKER_HOST`, or a Mac of macOS 26 or
    /// newer on Apple silicon.
    #[tokio::test]
    async fn an_older_macos_is_refused_before_any_container_command() {
        let macos_15 = MacosHost {
            major: 15,
            apple_silicon: true,
        };
        for container_bin in [None, Some("/nonexistent/bin/container")] {
            let mut builder = Fixture::docker_builder().container_engine();
            if let Some(path) = container_bin {
                builder = builder.env("CONTAINER_BIN", path);
            }
            let fixture = builder.build();
            let backend = pinned_docker(&fixture, |settings| settings.macos = Some(macos_15));
            assert!(backend.apple().is_some(), "{container_bin:?}");
            let refusal = backend.require_available(&ctx(), "").await.unwrap_err();
            assert_eq!(
                (refusal.code.as_ref(), refusal.exit),
                ("container_macos_too_old", Exit::USAGE),
                "{container_bin:?}"
            );
            assert_eq!(
                refusal.message,
                "the Apple container engine needs macOS 26 or newer, and this host runs macOS 15; name a Docker engine \
                 with DOCKER_BIN or DOCKER_HOST, or run the lane on a Mac with macOS 26 or newer on Apple silicon",
                "{container_bin:?}"
            );
            assert_eq!(fixture.fake.calls(), Vec::<String>::new(), "{container_bin:?}");
        }
        let fixture = Fixture::docker_container();
        let refusal = pinned_docker(&fixture, |settings| settings.guest_arch = GuestArch::X86_64)
            .require_available(&ctx(), "")
            .await
            .unwrap_err();
        assert_eq!(refusal.code, "unsupported_backend_operation");
        assert!(
            refusal.message.ends_with(
                "the Apple container engine runs on Apple silicon only; name a Docker engine with DOCKER_BIN or \
                 DOCKER_HOST, or run the lane on a Mac with macOS 26 or newer on Apple silicon"
            ),
            "{}",
            refusal.message
        );
        assert_eq!(fixture.fake.calls(), Vec::<String>::new());
    }

    /// A server of another install with another major version is refused, and the controller does not touch it. One
    /// of the same major version is used.
    #[tokio::test]
    async fn a_foreign_server_of_another_major_version_is_refused() {
        let fixture = Fixture::docker_container();
        let foreign = |version: &str| {
            format!(
                r#"{{"status":"running","paths":{{"installRoot":"/usr/local/"}},"client":{{"version":"1.5.0"}},"server":{{"version":"{version}"}},"host":{{"architecture":"arm64"}}}}"#
            )
        };
        fixture.fake.answer(Answer::ContainerSystem, foreign("2.0.0"));
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("container_engine_foreign", Exit::UNAVAILABLE)
        );
        assert!(refusal.message.contains("/usr/local/"), "{}", refusal.message);
        assert!(!fixture.fake.saw_call_containing("system start"), "{:#?}", fixture.fake.calls());

        fixture.fake.answer(Answer::ContainerSystem, foreign("1.4.1"));
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
    }

    /// A start that fails is `engine_start_failed`, and it names the log that holds the output of `system start`.
    #[tokio::test]
    async fn a_failed_server_start_names_its_log() {
        let fixture = Fixture::docker_container();
        fixture.fake.answer(Answer::ContainerStartExit, "1");
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        let log = fixture.settings.container_system_log_path();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("engine_start_failed", Exit::UNAVAILABLE));
        assert!(refusal.message.contains(&log.display().to_string()), "{}", refusal.message);
        assert!(std::fs::read_to_string(&log).unwrap().contains("the start failed"));
    }

    /// The whole start in the Apple dialect: the download of the image archive, which the mirror does not hold, then a
    /// build that names the builder sizes and the nameserver, then the stop of the builder, then a create with the memory, the CPUs and the
    /// nameserver of a worker and without `--hostname`, then the start. A second start keeps the running container.
    #[tokio::test]
    async fn a_start_speaks_the_apple_dialect() {
        let fixture = Fixture::docker_container();
        let worker = start_first(&fixture).await;
        let calls = fixture.fake.calls();
        let cpus = fixture.settings.vm_cpu;
        assert!(position(&calls, DOWNLOAD) < position(&calls, "build "));
        assert!(!calls.iter().any(|call| call.starts_with("image pull ")), "{calls:#?}");
        let log = std::fs::read_to_string(fixture.settings.docker_pull_log_path()).unwrap();
        assert!(log.contains("returned error: 404"), "{log}");
        let build = &calls[position(&calls, "build ")];
        assert!(
            build.starts_with(&format!(
                "build --progress plain -c {cpus} -m 4g --dns 192.0.2.53 -t air-ui-worker:"
            )),
            "{build}"
        );
        let create = &calls[position(&calls, "create ")];
        assert!(
            create.starts_with(&format!(
                "create --name {worker} --init -m {CONTAINER_WORKER_MEMORY_MIB}M -c {cpus} --dns 192.0.2.53 --shm-size 2g \
                 --ulimit nofile=65536:65536 --mount type=bind,"
            )),
            "{create}"
        );
        assert!(!create.contains("--hostname"), "{create}");
        assert!(position(&calls, "build ") < position(&calls, "builder stop"));
        assert!(position(&calls, "builder stop") < position(&calls, "create "));
        assert!(position(&calls, "create ") < position(&calls, &format!("start {worker}")));
        let record = docker(&fixture).read_create_record(worker).unwrap();
        assert_eq!(record.container_id, worker);
        assert!(docker(&fixture).container_is_current(&ctx(), worker).await.unwrap());

        let after = start_again(&fixture, worker).await;
        assert!(
            !after
                .iter()
                .any(|call| call.starts_with("create ") || call.starts_with("start ") || call.starts_with("builder ")),
            "{after:#?}"
        );
    }

    /// A container that stops during the boot poll is `container_exited`. The engine keeps no exit code, so the
    /// refusal quotes the log of the container and its boot log, which holds the code.
    #[tokio::test]
    async fn a_container_that_stops_while_starting_quotes_both_logs() {
        let fixture = Fixture::docker_container();
        fixture.guest.answer(avl_host_testkit::answer_guest(vec![(
            "/usr/bin/true",
            avl_host_testkit::failed(1, "not running"),
        )]));
        fixture.fake.answer(Answer::StartedState, "exited/1\n");
        fixture
            .fake
            .answer(Answer::ContainerLog, "air-display: Xvfb did not start on :88\n");
        fixture.fake.answer(
            Answer::ContainerBootLog,
            "vminitd: id: air-docker-1, status: 1 managed process exit\n",
        );
        let worker = fixture.worker(0);
        let refusal = fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap_err();
        assert_eq!(refusal.code, "container_exited", "{refusal:?}");
        assert!(refusal.message.contains("Xvfb did not start"), "{}", refusal.message);
        assert!(refusal.message.contains("status: 1 managed process exit"), "{}", refusal.message);
        let calls = fixture.fake.calls();
        position(&calls, &format!("logs -n 40 {worker}"));
        position(&calls, &format!("logs --boot -n 40 {worker}"));
    }

    /// Starts the worker of a `container` pool and runs `command`. Answers the fixture and the engine calls of the
    /// command.
    async fn pool_after_a_start(command: PoolCommand) -> (Fixture, Vec<String>) {
        let fixture = Fixture::docker_container();
        start_first(&fixture).await;
        fixture.fake.forget_calls();
        fixture.run_pool(command).await.unwrap();
        let calls = fixture.fake.calls();
        (fixture, calls)
    }

    /// `pool stop` stops the container with `stop -t 10` and leaves the server and the builder alone.
    #[tokio::test]
    async fn a_pool_stop_stops_the_containers_only() {
        let (fixture, calls) = pool_after_a_start(PoolCommand::Stop(PoolTarget::All)).await;
        let worker = fixture.worker(0);
        position(&calls, &format!("stop -t 10 {worker}"));
        assert!(
            !calls
                .iter()
                .any(|call| call.starts_with("system stop") || call.starts_with("builder ")),
            "{calls:#?}"
        );
    }

    /// `pool gc` on a server that is down starts nothing, and it keeps every slot with the word `engine-stopped`.
    #[tokio::test]
    async fn a_pool_gc_on_a_stopped_server_starts_nothing() {
        let fixture = Fixture::docker_container();
        let outcome = fixture.run_pool(PoolCommand::Gc).await.unwrap();
        assert_eq!(outcome.data["kept"][fixture.worker(0)], "engine-stopped");
        assert_eq!(outcome.text, "removed=(none)");
        let calls = fixture.fake.calls();
        assert!(!calls.iter().any(|call| call.starts_with("system start")), "{calls:#?}");
    }

    /// `pool recycle all` stops and deletes the builder, deletes the container by force, finds no volume to delete,
    /// and never stops the server.
    #[tokio::test]
    async fn a_pool_recycle_of_all_deletes_the_builder_and_keeps_the_server() {
        let (fixture, calls) = pool_after_a_start(PoolCommand::Recycle(PoolTarget::All)).await;
        let worker = fixture.worker(0);
        assert!(position(&calls, "builder stop") < position(&calls, "builder delete"));
        assert!(position(&calls, "builder delete") < position(&calls, &format!("delete --force {worker}")));
        position(&calls, &format!("volume inspect air-{worker}-data"));
        assert!(
            !calls
                .iter()
                .any(|call| call.starts_with("system stop") || call.starts_with("volume delete")),
            "{calls:#?}"
        );
    }
}
