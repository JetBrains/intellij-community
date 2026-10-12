//! The Lima engine's suite. The template tests are pure and run on every host. The lifecycle tests run over the fake
//! `limactl` of the shared hypervisor fake, a shell script, so they are Unix only.

use pretty_assertions::assert_eq;

use super::*;

fn inputs() -> EngineInputs {
    EngineInputs {
        cpus: 8,
        memory_mib: 6_144,
        disk_gb: 80,
        home: "/Users/air".to_owned(),
        image_arm64: ("https://images.example/arm64.img".to_owned(), "a".repeat(64)),
        image_x86_64: ("https://images.example/amd64.img".to_owned(), "b".repeat(64)),
    }
}

// --- the template ----------------------------------------------------------------------------------------------

/// Every marker is replaced, a string arrives quoted, and Lima's own placeholders stay for Lima.
#[test]
fn the_template_renders_every_marker_and_keeps_limas_placeholders() {
    let rendered = render_template(&inputs());
    assert!(!rendered.contains("@@"), "{rendered}");
    for line in [
        "vmType: vz",
        "mountType: virtiofs",
        "cpus: 8",
        "memory: \"6144MiB\"",
        "disk: \"80GiB\"",
        "- location: \"https://images.example/arm64.img\"",
        "  arch: aarch64",
        &format!("  digest: \"sha256:{}\"", "a".repeat(64)),
        "- location: \"https://images.example/amd64.img\"",
        &format!("  digest: \"sha256:{}\"", "b".repeat(64)),
        "- location: \"/Users/air\"",
        "  mountPoint: \"/Users/air\"",
        "  writable: false",
        "  system: false",
        "  user: false",
        "  expression: .Socket.SocketUser=\"{{.User}}\"",
        "- guestSocket: /var/run/docker.sock",
        "  hostSocket: \"{{.Dir}}/sock/docker.sock\"",
    ] {
        assert!(
            rendered.lines().any(|candidate| candidate == line),
            "no line {line:?} in\n{rendered}"
        );
    }
    // The guest builds nothing, so it installs the engine alone.
    assert!(
        rendered
            .lines()
            .any(|line| line.trim() == "apt-get install -y --no-install-recommends docker.io"),
        "{rendered}"
    );
    // Each key sits directly under its parent mapping.
    for block in ["param:\n  internal_netplanOptional: \"true\"\n", "ssh:\n  overVsock: true\n"] {
        assert!(rendered.contains(block), "no block {block:?} in\n{rendered}");
    }
    // One mount: a checkout switch changes nothing in the template.
    assert_eq!(rendered.matches("mountPoint:").count(), 1, "{rendered}");
}

/// The substitution runs once over the template: a value that holds a marker spelling stays as it is.
#[test]
fn a_value_is_never_read_as_a_marker() {
    let mut odd = inputs();
    odd.home = "/Users/@@CPUS@@".to_owned();
    let rendered = render_template(&odd);
    assert!(rendered.contains("- location: \"/Users/@@CPUS@@\""), "{rendered}");
}

/// A path is a JSON string, so a quote or a colon in it stays inside the scalar.
#[test]
fn a_share_path_cannot_change_the_document() {
    let mut odd = inputs();
    odd.home = "/Users/air/my \"idea\": checkout".to_owned();
    let rendered = render_template(&odd);
    assert!(rendered.contains(r#"- location: "/Users/air/my \"idea\": checkout""#), "{rendered}");
}

/// The digest is stable for the same inputs and moves with each of them, so a change of a share, a size or an
/// image makes the engine again.
#[test]
fn the_template_digest_follows_every_input() {
    let base = template_digest(&render_template(&inputs()));
    assert_eq!(base, template_digest(&render_template(&inputs())));
    assert_eq!(base.len(), 64);
    let changes: [fn(&mut EngineInputs); 6] = [
        |inputs| inputs.cpus = 4,
        |inputs| inputs.memory_mib = 8_192,
        |inputs| inputs.disk_gb = 100,
        |inputs| inputs.home = "/Users/other".to_owned(),
        |inputs| inputs.image_arm64.1 = "c".repeat(64),
        |inputs| inputs.image_x86_64.0 = "https://images.example/other.img".to_owned(),
    ];
    for (index, change) in changes.iter().enumerate() {
        let mut changed = inputs();
        change(&mut changed);
        assert_ne!(template_digest(&render_template(&changed)), base, "change {index}");
    }
}

#[test]
fn the_engine_state_keeps_limas_word() {
    assert_eq!(EngineState::parse("Running\n"), EngineState::Running);
    assert_eq!(EngineState::parse("Stopped"), EngineState::Stopped);
    assert_eq!(EngineState::parse("Broken\n"), EngineState::Other("Broken".to_owned()));
    assert_eq!(EngineState::parse("\n"), EngineState::Absent);
    assert_eq!(EngineState::Absent.as_str(), "Absent");
    assert_eq!(EngineState::Other("Broken".to_owned()).as_str(), "Broken");
}

// --- the lifecycle over the fake `limactl` -----------------------------------------------------------------------

#[cfg(unix)]
mod lifecycle {
    use avl_base::config::{docker_buildx_label, docker_cli_label};
    use avl_testkit::tartfake::{Answer, LIMA_TEMPLATE_COPY};
    use std::path::PathBuf;

    use pretty_assertions::assert_eq;

    use super::*;
    use crate::worker::docker::Docker;
    use crate::worker::testing::Fixture;
    use crate::worker::worker::{PoolCommand, PoolTarget};

    fn ctx() -> Ctx {
        Ctx::background()
    }

    fn docker(fixture: &Fixture) -> &Docker {
        fixture.manager.machine().docker().expect("a Docker pool")
    }

    fn engine(fixture: &Fixture) -> &Lima {
        docker(fixture).engine().expect("a pool on the Lima engine")
    }

    fn position(calls: &[String], prefix: &str) -> usize {
        calls
            .iter()
            .position(|call| call.starts_with(prefix))
            .unwrap_or_else(|| panic!("no {prefix:?} call in {calls:#?}"))
    }

    /// The create of the engine: a first start, whose budget is at least 900 s.
    const CREATE: &str = "start --tty=false --timeout=900s --name air-docker-engine ";

    /// The gate creates the engine from the rendered template, records its digest, and only then asks `docker
    /// version`, over a runner that carries the engine's `DOCKER_HOST`.
    #[tokio::test]
    async fn the_gate_starts_the_engine_before_it_asks_docker() {
        let fixture = Fixture::docker_lima();
        assert_eq!(engine(&fixture).state(&ctx()).await.unwrap(), EngineState::Absent);
        assert!(
            fixture.fake.calls().is_empty(),
            "an engine never started is Absent without a limactl call: {:?}",
            fixture.fake.calls()
        );
        assert!(fixture.bazel.as_ref().unwrap().asked().is_empty());

        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        let calls = fixture.fake.calls();
        assert!(position(&calls, CREATE) < position(&calls, "version --format "));
        let settings = &fixture.settings;
        assert!(settings.lima_socket_path().exists());
        let rendered = std::fs::read_to_string(settings.lima_template_path()).unwrap();
        assert_eq!(
            std::fs::read_to_string(fixture.fake.directory().join(LIMA_TEMPLATE_COPY)).unwrap(),
            rendered,
            "limactl start reads the rendered template"
        );
        assert_eq!(rendered, render_template(&EngineInputs::of(settings).unwrap()));
        let record: EngineRecord = serde_json::from_slice(&std::fs::read(settings.lima_engine_record_path()).unwrap()).unwrap();
        assert_eq!(record.template_digest, template_digest(&rendered));
        assert_eq!(record.instance, "air-docker-engine");
        let log = std::fs::read_to_string(settings.lima_engine_log_path()).unwrap();
        assert!(log.contains("starting air-docker-engine"), "{log}");

        let environment = fixture.manager.runner().environment();
        let host = format!("unix://{}", settings.lima_socket_path().display());
        assert!(
            environment.contains(&("DOCKER_HOST".to_owned(), host)),
            "the manager's runner must reach the Lima engine"
        );
        let config = settings.docker_config_dir();
        assert!(
            environment.contains(&("DOCKER_CONFIG".to_owned(), config.to_string_lossy().into_owned())),
            "the manager's runner must name the pinned CLI's configuration"
        );
        // The configuration names the directory of the pinned plugin, where the file is `docker-buildx`.
        let written: serde_json::Value = serde_json::from_slice(&std::fs::read(config.join("config.json")).unwrap()).unwrap();
        let directories = written["cliPluginsExtraDirs"].as_array().unwrap();
        assert_eq!(directories.len(), 1, "{written}");
        let directory = PathBuf::from(directories[0].as_str().unwrap());
        assert!(directory.join("docker-buildx").is_file(), "{written}");
    }

    /// A running engine of the current template is kept: the next gate lists it and starts nothing.
    #[tokio::test]
    async fn a_running_engine_of_the_current_template_is_kept() {
        let fixture = Fixture::docker_lima();
        for _ in 0..2 {
            docker(&fixture).require_available(&ctx(), "").await.unwrap();
        }
        let calls = fixture.fake.calls();
        assert_eq!(
            calls.iter().filter(|call| call.starts_with("start --tty=false")).count(),
            1,
            "{calls:#?}"
        );
        assert!(!calls.iter().any(|call| call.starts_with("delete ")), "{calls:#?}");
        // One query resolves the CLI, its plugin and limactl together, however many gates ran.
        let labels = fixture.bazel.as_ref().unwrap().labels();
        assert_eq!(labels.len(), 1, "{labels:#?}");
        let arch = fixture.settings.guest_arch;
        for label in [docker_cli_label(arch), docker_buildx_label(arch), limactl_label(arch)] {
            assert!(labels[0].contains(&label), "{labels:#?}");
        }
    }

    /// A stopped engine is started by name, from the configuration Lima keeps.
    #[tokio::test]
    async fn a_stopped_engine_is_started_by_name() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        assert_eq!(engine(&fixture).stop(&ctx()).await.unwrap(), EngineState::Stopped);
        assert!(!fixture.settings.lima_socket_path().exists());
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        assert!(
            fixture
                .fake
                .calls()
                .contains(&"start --tty=false --timeout=180s air-docker-engine".to_owned()),
            "{:#?}",
            fixture.fake.calls()
        );
        assert!(fixture.settings.lima_socket_path().exists());
    }

    fn declare_another_template(fixture: &Fixture) {
        let path = fixture.settings.lima_engine_record_path();
        let mut record: EngineRecord = serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
        record.template_digest = "0".repeat(64);
        std::fs::write(&path, serde_json::to_vec(&record).unwrap()).unwrap();
    }

    /// An engine of another template digest is deleted and made again, and its record then says the current digest.
    #[tokio::test]
    async fn a_changed_template_makes_the_engine_again() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        let before = fixture.fake.calls().len();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(
            position(&after, "delete --force air-docker-engine") < position(&after, CREATE),
            "{after:#?}"
        );
        let record: EngineRecord = serde_json::from_slice(&std::fs::read(fixture.settings.lima_engine_record_path()).unwrap()).unwrap();
        let rendered = std::fs::read_to_string(fixture.settings.lima_template_path()).unwrap();
        assert_eq!(record.template_digest, template_digest(&rendered));
    }

    /// The delete would take a leased container with it, so a changed template under a lease is refused and the
    /// engine stays.
    #[tokio::test]
    async fn a_changed_template_under_a_lease_is_worker_leased() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        fixture.write_lease(fixture.worker(0), "token");
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_leased", Exit::TEMP_FAIL));
        assert!(!fixture.fake.saw_call_containing("delete "), "{:#?}", fixture.fake.calls());
    }

    /// A start that fails is `engine_start_failed`, and it names the log that holds the output of `limactl start`.
    #[tokio::test]
    async fn a_failed_start_is_engine_start_failed_and_names_the_log() {
        let fixture = Fixture::docker_lima();
        fixture.fake.answer(Answer::LimaStartExit, "1");
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        let log = fixture.settings.lima_engine_log_path();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("engine_start_failed", Exit::UNAVAILABLE));
        assert!(refusal.message.contains(&log.display().to_string()), "{}", refusal.message);
        assert!(
            std::fs::read_to_string(&log).unwrap().contains("the start failed"),
            "the log keeps the output of the failed start"
        );
        assert!(
            refusal.message.contains("the budget was 900 s from the first-start budget")
                && refusal.message.contains("downloads the Ubuntu image"),
            "a first start names its budget: {}",
            refusal.message
        );
        assert!(
            !fixture.fake.saw_call_containing("version --format"),
            "no docker command runs against an engine that did not start"
        );
        assert!(!fixture.settings.lima_engine_record_path().exists());
    }

    /// A first start gets at least 900 s, because it downloads the image; a larger `AIR_VM_BOOT_TIMEOUT` wins. The start
    /// of an engine that exists gets `AIR_VM_BOOT_TIMEOUT`.
    #[tokio::test]
    async fn the_first_start_has_the_larger_budget() {
        let fixture = Fixture::docker_builder().env("AIR_VM_BOOT_TIMEOUT", "1200").lima_engine().build();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        engine(&fixture).stop(&ctx()).await.unwrap();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        let calls = fixture.fake.calls();
        position(&calls, "start --tty=false --timeout=1200s --name air-docker-engine ");
        position(&calls, "start --tty=false --timeout=1200s air-docker-engine");
        assert_eq!(FIRST_START_BUDGET.as_secs(), 900);
    }

    /// The lease holder's own gate makes the engine again after a template change: its own worker does not count as
    /// another session's container.
    #[tokio::test]
    async fn the_lease_holders_own_gate_recreates_the_engine() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        let lease = fixture.write_lease(fixture.worker(0), "token");
        let before = fixture.fake.calls().len();
        fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(
            position(&after, "delete --force air-docker-engine") < position(&after, CREATE),
            "{after:#?}"
        );
    }

    /// The two bodies of one `shard` hold one lease each, and the manager records both as this invocation's. After a
    /// template change they make the engine again once, and neither refuses the other's lease.
    #[tokio::test]
    async fn sibling_leases_of_one_invocation_recreate_the_engine_once() {
        let fixture = Fixture::docker_builder().env("AIR_VM_MAX_WORKERS", "2").lima_engine().build();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        let first = fixture.write_lease(fixture.worker(0), "token-1");
        let second = fixture.write_lease(fixture.worker(1), "token-2");
        let held = fixture.manager.hold_leases(&[first.clone(), second.clone()]);
        let before = fixture.fake.calls().len();
        let (one, two) = (ctx(), ctx());
        let (left, right) = tokio::join!(
            docker(&fixture).require_available_for(&one, Some(&first)),
            docker(&fixture).require_available_for(&two, Some(&second))
        );
        left.unwrap();
        right.unwrap();
        let after = fixture.fake.calls()[before..].to_vec();
        let count = |prefix: &str| after.iter().filter(|call| call.starts_with(prefix)).count();
        assert_eq!(count("delete --force air-docker-engine"), 1, "{after:#?}");
        assert_eq!(count(CREATE), 1, "{after:#?}");

        // Once the invocation lets its leases go, they are leases like any other.
        drop(held);
        let refusal = engine(&fixture).require_no_lease("make the Docker engine again", None).unwrap_err();
        assert_eq!(refusal.code, "worker_leased");
    }

    /// A lease that this invocation does not hold is another session's, and it still stops the recreate. The refusal
    /// names what it checked: the lease, and not whether the container runs.
    #[tokio::test]
    async fn a_lease_of_another_process_still_refuses_the_recreate() {
        let fixture = Fixture::docker_builder().env("AIR_VM_MAX_WORKERS", "2").lima_engine().build();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        let own = fixture.write_lease(fixture.worker(0), "token-1");
        fixture.write_lease(fixture.worker(1), "foreign");
        let _held = fixture.manager.hold_leases(std::slice::from_ref(&own));
        let before = fixture.fake.calls().len();
        let refusal = docker(&fixture).require_available_for(&ctx(), Some(&own)).await.unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_leased", Exit::TEMP_FAIL));
        assert!(
            refusal.message.contains(fixture.worker(1))
                && refusal.message.contains("holds a lease of another process")
                && !refusal.message.contains("runs its container"),
            "{}",
            refusal.message
        );
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(!after.iter().any(|call| call.starts_with("delete ")), "{after:#?}");
    }

    /// Two bodies of one leased run reach the gate at once. The engine lock lets one start the engine, and the other
    /// finds it running.
    #[tokio::test]
    async fn two_concurrent_gates_start_the_engine_once() {
        let fixture = Fixture::docker_lima();
        let (one, two) = (ctx(), ctx());
        let (first, second) = tokio::join!(
            engine(&fixture).ensure_running(&one, None),
            engine(&fixture).ensure_running(&two, None)
        );
        first.unwrap();
        second.unwrap();
        let calls = fixture.fake.calls();
        assert_eq!(
            calls.iter().filter(|call| call.starts_with("start --tty=false")).count(),
            1,
            "{calls:#?}"
        );
    }

    /// `pool stop` never makes the engine again: a template change is the next start's business.
    #[tokio::test]
    async fn pool_stop_of_an_engine_of_another_template_neither_deletes_nor_starts() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        let before = fixture.fake.calls().len();
        let outcome = fixture.manager.pool(&ctx(), PoolCommand::Stop(PoolTarget::All)).await.unwrap();
        assert_eq!(outcome.data["engine"], "Stopped");
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(
            !after
                .iter()
                .any(|call| call.starts_with("delete ") || call.starts_with("start --tty=false")),
            "{after:#?}"
        );
    }

    /// `pool gc` on a stopped engine asks no `docker` command and starts nothing: every slot is kept.
    #[tokio::test]
    async fn pool_gc_on_a_stopped_engine_starts_nothing() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        engine(&fixture).stop(&ctx()).await.unwrap();
        let before = fixture.fake.calls().len();
        let outcome = fixture.manager.pool(&ctx(), PoolCommand::Gc).await.unwrap();
        assert_eq!(outcome.data["kept"][fixture.worker(0)], "engine-stopped");
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(after.iter().all(|call| call.starts_with("list ")), "{after:#?}");
    }

    /// The engine mounts only the home, so a repository outside it is refused before the engine is touched.
    #[tokio::test]
    async fn a_repository_outside_the_home_is_share_outside_home() {
        let fixture = Fixture::docker_builder().env("HOME", "/nonexistent-home").lima_engine().build();
        let refusal = engine(&fixture).ensure_running(&ctx(), None).await.unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("share_outside_home", Exit::USAGE));
        assert!(
            refusal.message.contains(&fixture.root().display().to_string()) && refusal.message.contains("/nonexistent-home"),
            "{}",
            refusal.message
        );
        assert!(fixture.fake.calls().is_empty(), "{:?}", fixture.fake.calls());
    }

    /// A socket file of an earlier start is removed before `limactl start`, so the wait cannot pass on it.
    #[tokio::test]
    async fn a_stale_socket_is_removed_before_the_start() {
        let fixture = Fixture::docker_lima();
        let socket = fixture.settings.lima_socket_path();
        std::fs::create_dir_all(socket.parent().unwrap()).unwrap();
        std::fs::write(&socket, "").unwrap();
        fixture.fake.answer(Answer::LimaStartExit, "1");
        engine(&fixture).ensure_running(&ctx(), None).await.unwrap_err();
        assert!(!socket.exists(), "the stale socket survived the start");
    }

    /// A lease release never starts or makes the engine again: a template change waits for the next start, and the
    /// lease under release is not refused against itself.
    #[tokio::test]
    async fn a_lease_release_after_a_template_change_neither_deletes_nor_starts() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        declare_another_template(&fixture);
        let worker = fixture.worker(0).to_owned();
        fixture.write_lease(&worker, "token");
        fixture.fake.answer(Answer::ContainerState, "running/0\n");
        let before = fixture.fake.calls().len();
        fixture.manager.require_release_ready(&ctx(), &worker).await.unwrap();
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(
            !after
                .iter()
                .any(|call| call.starts_with("delete ") || call.starts_with("start --tty=false")),
            "{after:#?}"
        );

        // A stopped engine runs no container: the release is refused at once, and the engine stays stopped.
        engine(&fixture).stop(&ctx()).await.unwrap();
        let before = fixture.fake.calls().len();
        let refusal = fixture.manager.require_release_ready(&ctx(), &worker).await.unwrap_err();
        assert_eq!(refusal.code, "worker_stopped");
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(after.iter().all(|call| call.starts_with("list ")), "{after:#?}");
    }

    /// A broken engine of the current template is refused with its word, and the refusal names the repair.
    #[tokio::test]
    async fn a_broken_engine_is_engine_unusable() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        fixture.fake.answer(Answer::LimaState, "Broken\n");
        let refusal = docker(&fixture).require_available(&ctx(), "").await.unwrap_err();
        assert_eq!(refusal.code, "engine_unusable");
        assert!(
            refusal.message.contains("Broken") && refusal.message.contains("pool recycle all"),
            "{}",
            refusal.message
        );
    }

    /// `pool stop` stops the engine after the containers when nothing of the pool needs it, and reports its state.
    #[tokio::test]
    async fn pool_stop_stops_an_unleased_engine() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        let outcome = fixture.manager.pool(&ctx(), PoolCommand::Stop(PoolTarget::All)).await.unwrap();
        assert_eq!(outcome.data["engine"], "Stopped");
        assert!(outcome.text.lines().any(|line| line == "engine=Stopped"), "{}", outcome.text);
        let calls = fixture.fake.calls();
        assert!(calls.contains(&"stop air-docker-engine".to_owned()), "{calls:#?}");
        assert_eq!(engine(&fixture).state(&ctx()).await.unwrap(), EngineState::Stopped);

        // A stopped engine holds no running container: the next stop boots nothing and asks docker nothing.
        let before = fixture.fake.calls().len();
        let outcome = fixture.manager.pool(&ctx(), PoolCommand::Stop(PoolTarget::All)).await.unwrap();
        assert_eq!(outcome.data["engine"], "Stopped");
        assert_eq!(outcome.data["workers"][fixture.worker(0)], "already-stopped");
        let after = fixture.fake.calls()[before..].to_vec();
        assert!(
            after.iter().all(|call| call.starts_with("list ")),
            "only limactl list may run: {after:#?}"
        );
    }

    /// A leased worker keeps the engine running: its container runs the other session's tests.
    #[tokio::test]
    async fn pool_stop_keeps_the_engine_of_a_leased_worker() {
        let fixture = Fixture::docker_builder().env("AIR_VM_MAX_WORKERS", "2").lima_engine().build();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        fixture.write_lease(fixture.worker(1), "token");
        let outcome = fixture
            .manager
            .pool(&ctx(), PoolCommand::Stop(PoolTarget::Worker(fixture.worker(0).to_owned())))
            .await
            .unwrap();
        assert_eq!(outcome.data["engine"], "Running");
        assert!(
            !fixture.fake.calls().contains(&"stop air-docker-engine".to_owned()),
            "{:#?}",
            fixture.fake.calls()
        );
    }

    /// `pool recycle all` deletes the engine before the slots, and the recycle of each slot makes it again.
    #[tokio::test]
    async fn pool_recycle_all_makes_the_engine_again() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        let before = fixture.fake.calls().len();
        fixture.manager.pool(&ctx(), PoolCommand::Recycle(PoolTarget::All)).await.unwrap();
        let after = fixture.fake.calls()[before..].to_vec();
        let worker = fixture.worker(0);
        assert!(
            position(&after, "delete --force air-docker-engine") < position(&after, CREATE),
            "{after:#?}"
        );
        assert!(position(&after, CREATE) < position(&after, &format!("start {worker}")));
    }

    /// The recycle of one slot leaves the engine: only `all` makes the whole pool again.
    #[tokio::test]
    async fn pool_recycle_of_one_worker_keeps_the_engine() {
        let fixture = Fixture::docker_lima();
        docker(&fixture).require_available(&ctx(), "").await.unwrap();
        fixture
            .manager
            .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(fixture.worker(0).to_owned())))
            .await
            .unwrap();
        assert!(
            !fixture.fake.saw_call_containing("delete --force air-docker-engine"),
            "{:#?}",
            fixture.fake.calls()
        );
    }
}
