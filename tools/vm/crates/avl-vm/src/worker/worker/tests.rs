//! The worker lifecycle's suite. Hermetic: no VM, no network, no Bazel.
//!
//! The one thing here that spawns something long-lived is a start, and every test that starts a worker reaps it: a
//! detached `tart run` that outlived the suite would hold a VM slot on the developer's host until a reboot.

use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::sync::{Arc, Mutex};

use avl_base::format::words;
use avl_base::{Backend, Exit, GuestOs, Refusal, SCHEMA_VERSION};
use avl_host_sys::interrupt::Interrupts;
use avl_host_sys::{Ctx, Runner, SpawnOptions};
use avl_host_testkit::{answer_guest, load_settings, said};
use avl_testkit::tartfake::Answer;
use futures::FutureExt;
use pretty_assertions::assert_eq;

use super::tart::SuspendedState;
use super::*;
use crate::worker::tart::ProcessIdentity;
use crate::worker::testing::{CONSOLE_LOGIN, Fixture, find_step, manager_over};
use avl_base::RefusalExt;

fn ctx() -> Ctx {
    Ctx::background()
}

fn lease_of(worker: &str, token: &str) -> Lease {
    Lease {
        schema_version: SCHEMA_VERSION,
        backend: Backend::Tart,
        guest_os: GuestOs::Macos,
        worker: worker.to_owned(),
        token: token.to_owned(),
        holder: "a holder".to_owned(),
        acquired_at: "2026-10-01T12:00:00Z".to_owned(),
    }
}

// Two leases are one when the worker and the token agree, whoever holds them; the held set forgets a lease by that
// comparison too.
#[test]
fn a_lease_is_its_worker_and_its_token() {
    let lease = lease_of("air-macos-1", "token-1");
    assert!(lease.same_lease(&Lease {
        holder: "another holder".to_owned(),
        ..lease.clone()
    }));
    assert!(!lease.same_lease(&lease_of("air-macos-1", "token-2")));
    assert!(!lease.same_lease(&lease_of("air-macos-2", "token-1")));

    let held = Arc::new(HeldLeases::default());
    let guard = held.hold(std::slice::from_ref(&lease));
    assert!(held.contains(&lease));
    assert!(!held.contains(&lease_of("air-macos-1", "token-2")));
    drop(guard);
    assert!(!held.contains(&lease));
}

// A probe inside a wait gets what is left of the wait, so a guest that stops answering cannot carry the wait past
// its budget; outside the last half minute it gets its own bound.
#[test]
fn a_probe_inside_a_wait_gets_what_is_left_of_it() {
    assert_eq!(probe_timeout(Duration::from_mins(10)), GUEST_PROBE_TIMEOUT);
    assert_eq!(probe_timeout(Duration::from_secs(5)), Duration::from_secs(5));
    assert_eq!(probe_timeout(Duration::ZERO), Duration::ZERO);
}

fn tart(fixture: &Fixture) -> &Tart {
    fixture.manager.tart().expect("a Tart pool")
}

// --- the lease document ----------------------------------------------------------------------------------------

/// An absent lease is a free worker; anything present that cannot be read is a refusal. Conflating the two is how a
/// leased worker would be handed to a second holder.
#[test]
fn an_absent_lease_is_free_and_an_unreadable_one_refuses() {
    let fixture = Fixture::macos();
    let path = fixture.settings.lease_path(fixture.worker(0));
    assert_eq!(read_lease(&path).unwrap(), None);

    for (name, content) in [
        ("not JSON at all", "{"),
        ("an empty file", ""),
        (
            "a wrong schema",
            r#"{"schemaVersion":99,"backend":"tart","guestOs":"macos","worker":"w","token":"t","holder":"h","acquiredAt":"a"}"#,
        ),
        (
            "a missing token",
            r#"{"schemaVersion":1,"backend":"tart","guestOs":"macos","worker":"w","holder":"h","acquiredAt":"a"}"#,
        ),
        (
            "an unknown backend",
            r#"{"schemaVersion":1,"backend":"vmware","guestOs":"linux","worker":"w","token":"t","holder":"h","acquiredAt":"a"}"#,
        ),
        (
            "no backend",
            r#"{"schemaVersion":1,"guestOs":"linux","worker":"w","token":"t","holder":"h","acquiredAt":"a"}"#,
        ),
        (
            "no guest OS",
            r#"{"schemaVersion":1,"backend":"tart","worker":"w","token":"t","holder":"h","acquiredAt":"a"}"#,
        ),
    ] {
        std::fs::write(&path, content).unwrap();
        let refusal = read_lease(&path).unwrap_err();
        assert_eq!(refusal.code, "corrupt_lease", "{name}");
    }
}

/// A lease reads as it was written, both axes included.
#[test]
fn a_lease_reads_back_as_it_was_written() {
    let fixture = Fixture::macos();
    let path = fixture.settings.lease_path(fixture.worker(0));
    let written = r#"{"schemaVersion":1,"backend":"tart","guestOs":"macos","worker":"air-macos-1","token":"t","holder":"h","acquiredAt":"2026-08-23T00:00:00.000Z"}"#;
    std::fs::write(&path, format!("{written}\n")).unwrap();
    let lease = read_lease(&path).unwrap().unwrap();
    assert_eq!((lease.backend, lease.guest_os), (Backend::Tart, GuestOs::Macos));
    assert_eq!(serde_json::to_string(&lease).unwrap(), written);
}

// --- the run process's pid receipt -----------------------------------------------------------------------------

/// The receipt round-trips at mode 0600, and every field of the identity triple is required: a receipt that disagrees
/// with the process it names answers "absent" rather than a partial value.
#[tokio::test]
async fn the_process_receipt_round_trips_and_is_refused_field_by_field() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let identity = fixture.run_as_fake_process(worker).await;
    assert!(!identity.process_start.is_empty() && !identity.process_command.is_empty());
    let path = fixture.settings.pid_path(worker);
    assert_eq!(std::fs::metadata(&path).unwrap().permissions().mode() & 0o7777, 0o600);
    assert_eq!(tart(&fixture).read_process_identity(worker), Some(identity.clone()));
    // The document's keys, in their order.
    let written = std::fs::read_to_string(&path).unwrap();
    assert!(
        written.starts_with(&format!(
            "{{\"schemaVersion\":1,\"worker\":\"{worker}\",\"pid\":{},\"processStart\":",
            identity.pid
        )),
        "{written}"
    );

    let damaged = |edit: fn(&mut ProcessIdentity)| {
        let mut broken = ProcessIdentity {
            schema_version: SCHEMA_VERSION,
            worker: worker.to_owned(),
            pid: 1,
            process_start: "s".to_owned(),
            process_command: "c".to_owned(),
        };
        edit(&mut broken);
        broken
    };
    for (name, broken) in [
        (
            "a schema this build does not speak",
            damaged(|identity| identity.schema_version = 99),
        ),
        (
            "another worker's receipt",
            damaged(|identity| identity.worker = "somebody-else".to_owned()),
        ),
        ("no pid", damaged(|identity| identity.pid = 0)),
        ("no start time", damaged(|identity| identity.process_start.clear())),
        ("no command line", damaged(|identity| identity.process_command.clear())),
    ] {
        std::fs::write(&path, serde_json::to_vec(&broken).unwrap()).unwrap();
        assert_eq!(tart(&fixture).read_process_identity(worker), None, "{name}");
    }
}

/// A process nothing can identify is refused rather than recorded: a receipt naming a pid whose start time and command
/// line could not be read would be a worker nothing could ever recognise again.
#[tokio::test]
async fn an_unidentifiable_process_is_refused_rather_than_recorded() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let refusal = fixture.manager.write_process_identity(&ctx(), worker, -1).await.unwrap_err();
    assert_eq!(refusal.code, "process_identity_unavailable");
    assert!(!fixture.settings.pid_path(worker).exists());
}

// --- the run argv --------------------------------------------------------------------------------------------

/// `--suspendable` is the device set that `tart suspend` needs. `AIR_VM_SUSPENDABLE` turns it off.
#[test]
fn the_run_argv_declares_suspendable_only_when_it_is_on() {
    for (setting, want) in [("0", false), ("1", true)] {
        let fixture = Fixture::builder(GuestOs::Macos).env("AIR_VM_SUSPENDABLE", setting).build();
        let worker = fixture.worker(0);
        let argv = fixture.manager.tart_run_argv(worker).unwrap();
        let joined = argv.join(" ");
        assert_eq!(joined.contains("--suspendable"), want, "AIR_VM_SUSPENDABLE={setting}: {joined}");
        assert_eq!(argv.last().map(String::as_str), Some(worker), "{joined}");
        // The share grammar is the backend's, and it must reach the argv by name rather than by tag.
        for share in [&fixture.settings.repo_share_name, &fixture.settings.bazel_share_name] {
            assert!(joined.contains(&format!("--dir={share}:")), "{joined}");
        }
    }
}

/// The network mode reaches `tart run` as at most one flag, and the three modes are alternatives. `nat` declares
/// nothing at all, which is what makes it the mode that needs nothing of the host.
#[test]
fn the_run_argv_declares_the_chosen_network_mode() {
    for (mode, overrides, want) in [
        ("nat", vec![], None),
        ("softnet", vec![("AIR_VM_NETWORK", "softnet")], Some("--net-softnet")),
        (
            "bridged",
            vec![("AIR_VM_NETWORK", "bridged"), ("AIR_VM_BRIDGED_INTERFACE", "en0")],
            Some("--net-bridged=en0"),
        ),
    ] {
        let mut builder = Fixture::builder(GuestOs::Macos);
        for (name, value) in overrides {
            builder = builder.env(name, value);
        }
        let fixture = builder.build();
        let argv = fixture.manager.tart_run_argv(fixture.worker(0)).unwrap();
        let declared: Vec<&str> = argv
            .iter()
            .map(String::as_str)
            .filter(|argument| argument.starts_with("--net-"))
            .collect();
        assert_eq!(declared, want.into_iter().collect::<Vec<_>>(), "{mode}");
    }
}

/// A share declared at the empty path is the failure that is invisible on the host: the VM boots and every guest probe
/// afterwards blames the guest. The refusal is the hypervisor's, and this makes sure the run argv cannot route around
/// it.
#[test]
fn the_run_argv_refuses_unresolved_host_paths() {
    let fixture = Fixture::builder(GuestOs::Macos).unresolved_host_paths().build();
    let refusal = fixture.manager.tart_run_argv(fixture.worker(0)).unwrap_err();
    assert_eq!(refusal.code, "host_paths_unresolved");
}

// --- the suspended-state record ----------------------------------------------------------------------------------

/// The record is refused field by field for the same reason the pid receipt is: the question it answers is "may I
/// resume against my current declaration", and a damaged record must answer no rather than nearly.
#[test]
fn a_suspended_state_record_is_refused_field_by_field() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let path = fixture.settings.suspended_state_path(worker);

    // A complete record.
    std::fs::write(
        &path,
        format!(r#"{{"schemaVersion":1,"worker":"{worker}","runArgv":["tart","run"]}}"#),
    )
    .unwrap();
    assert_eq!(
        fixture.manager.read_suspended_state(worker),
        Some(SuspendedState {
            schema_version: 1,
            worker: worker.to_owned(),
            run_argv: words(["tart", "run"]),
        })
    );

    for (name, content) in [
        (
            "a wrong schema",
            format!(r#"{{"schemaVersion":99,"worker":"{worker}","runArgv":[]}}"#),
        ),
        (
            "another worker",
            r#"{"schemaVersion":1,"worker":"somebody-else","runArgv":[]}"#.to_owned(),
        ),
        ("no argv at all", format!(r#"{{"schemaVersion":1,"worker":"{worker}"}}"#)),
        (
            "a null argv",
            format!(r#"{{"schemaVersion":1,"worker":"{worker}","runArgv":null}}"#),
        ),
        ("not an object", "{".to_owned()),
    ] {
        std::fs::write(&path, content).unwrap();
        assert_eq!(fixture.manager.read_suspended_state(worker), None, "{name}");
    }
}

// --- the stop guard ------------------------------------------------------------------------------------------

/// `pool stop` passes no lease, so a leased worker is refused - and the refusal deliberately names no holder, because
/// `status` and `lease show` withhold it from an unauthorized caller too.
#[tokio::test]
async fn stop_refuses_a_worker_leased_by_another_holder() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let lease = fixture.write_lease(worker, "another-holders-token");

    let refusal = fixture.manager.stop_without_lifecycle_lock(&ctx(), worker, None).await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_leased", Exit::TEMP_FAIL));
    assert!(!refusal.message.contains(&lease.holder), "{}", refusal.message);

    // A receipt for some other lease is not authorization either.
    let other = Lease {
        token: "not-the-same".to_owned(),
        ..lease
    };
    let refusal = fixture
        .manager
        .stop_without_lifecycle_lock(&ctx(), worker, Some(&other))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "worker_leased");
}

/// The holder's own lease *does* authorize a stop, which is what a checkout switch needs: the readiness gate restarts
/// the worker it is holding when the share set no longer describes the host.
#[tokio::test]
async fn stop_accepts_the_holders_own_lease() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let lease = fixture.write_lease(worker, "the-holders-own-token");
    // No run process was ever recorded, so there is nothing to stop - which is the answer, not an error.
    assert_eq!(
        fixture
            .manager
            .stop_without_lifecycle_lock(&ctx(), worker, Some(&lease))
            .await
            .unwrap(),
        StopState::AlreadyStopped
    );
}

// --- pool commands ---------------------------------------------------------------------------------------------

/// `pool init` is pool-wide, so one leased worker refuses the whole operation rather than initializing the rest.
#[tokio::test]
async fn pool_init_refuses_a_leased_worker() {
    let fixture = Fixture::macos();
    fixture.write_lease(fixture.worker(0), "held");
    let refusal = fixture.manager.pool(&ctx(), PoolCommand::Init { golden: None }).await.unwrap_err();
    assert_eq!(refusal.code, "worker_leased");
    assert!(!fixture.fake.saw_call_containing("clone"));
}

/// A named golden is only accepted if it is a name a guest command line can carry unquoted, and the refusal arrives
/// before any hypervisor call.
#[tokio::test]
async fn pool_init_refuses_an_unsafe_golden_name() {
    let fixture = Fixture::macos();
    let refusal = fixture
        .manager
        .pool_init_without_lifecycle_lock(&ctx(), Some("golden; rm -rf /"))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "unsafe_name");
    assert_eq!(fixture.fake.calls(), Vec::<String>::new());
}

/// `pool gc` removes Tart worker clones and `pool recycle` deletes one to clone it again; a Parallels worker is a VM
/// the operator made, and neither is this controller's business. Both refusals keep their code because a caller
/// branches on it.
#[tokio::test]
async fn pool_gc_and_recycle_are_refused_on_parallels() {
    let root = tempfile::tempdir().unwrap();
    let environment: Vec<(String, String)> = [
        ("HOME", root.path().to_string_lossy().into_owned()),
        ("PATH", std::env::var("PATH").unwrap_or_default()),
        ("AIR_VM_RUNTIME_ROOT", root.path().join("runtime").to_string_lossy().into_owned()),
    ]
    .into_iter()
    .map(|(name, value)| (name.to_owned(), value))
    .collect();
    let settings = load_settings(Backend::Parallels, GuestOs::Macos, &environment, root.path());
    let manager = manager_over(&settings, Runner::new(Vec::new(), Interrupts::detached()), None, builds_nothing());
    // No fake hypervisor and no runtime directories, deliberately: the backend check is the first line of both, so
    // a refusal that needed either would be a refusal arriving too late. `all` is the one target that would otherwise
    // expand to a pool-wide delete.
    for command in [PoolCommand::Gc, PoolCommand::Recycle(PoolTarget::All)] {
        let refusal = manager.pool(&ctx(), command.clone()).await.unwrap_err();
        assert_eq!(refusal.code, crate::worker::hypervisor::UNSUPPORTED_CODE, "{command:?}");
    }
    assert!(!root.path().join("runtime").exists());
}

// --- the readiness gates ---------------------------------------------------------------------------------------

/// A stopped Tart worker is refused rather than started: `lease release` handles that case itself, and every other
/// caller of this gate wants a running worker or a refusal naming the repair.
#[tokio::test]
async fn require_release_ready_refuses_a_stopped_tart_worker() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let refusal = fixture.manager.require_release_ready(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "worker_stopped");
    assert!(refusal.message.contains(&format!("pool start {worker}")), "{}", refusal.message);
}

/// A running worker whose guest does not answer is a different refusal, and the code is what a caller uses to tell
/// "the machine is off" from "the machine is up and its agent is not".
#[tokio::test]
async fn require_release_ready_refuses_a_guest_that_does_not_answer() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    fixture.run_as_fake_process(worker).await;
    fixture.guest.fail_everything();
    let refusal = fixture.manager.require_release_ready(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "guest_agent_unavailable");
}

/// The release gate resolves the host paths, and the caller is why: `lease release` installs the guest agent next,
/// and an install that has to resolve the host binary asks Bazel about it under the repository path, which the
/// settings refuse to answer before the paths are resolved. The gap stayed invisible until a receipt started
/// recording the checkout that wrote it - and then every release on the host refused `host_paths_unresolved`.
#[tokio::test]
async fn require_release_ready_resolves_the_host_paths() {
    let checkout = tempfile::tempdir().unwrap();
    let repo = avl_host_sys::fs::real_path(checkout.path()).unwrap();
    let git_directory = tempfile::tempdir().unwrap();
    // A fake `git`, so resolving the host paths does not depend on the checkout this suite runs from.
    let git = avl_testkit::fake_executable(
        git_directory.path(),
        "git",
        &format!(
            "case \"$*\" in\n*--show-toplevel*) echo '{}';;\n*--is-inside-work-tree*) echo true;;\nesac\n",
            repo.display()
        ),
    )
    .unwrap();
    let fixture = Fixture::builder(GuestOs::Macos)
        .unresolved_host_paths()
        .env("AIR_VM_HOST_REPO", "")
        .env("AIR_VM_HOST_GIT", &git.to_string_lossy())
        .build();
    fixture.guest.answer(answer_guest(vec![("/usr/bin/who", said(CONSOLE_LOGIN))]));
    let worker = fixture.worker(0);
    fixture.run_as_fake_process(worker).await;
    fixture.settings.host_repo().unwrap_err();

    fixture.manager.require_release_ready(&ctx(), worker).await.unwrap();
    assert_eq!(fixture.settings.host_repo().unwrap(), repo);
}

// --- the guest channel ---------------------------------------------------------------------------------------

/// A tty is asked for exactly when there is stdin to carry. Without it the one caller that writes a file into the
/// guest produces an empty file and exits 0, which is a failure nothing downstream can see.
#[tokio::test]
async fn the_guest_channel_asks_for_a_tty_only_when_there_is_stdin() {
    let fixture = Fixture::macos();
    // The hypervisor-backed channel, not the suite's: the argv shape is the subject here.
    let manager = manager_over(&fixture.settings, fixture.runner(), None, builds_nothing());
    let channel = manager.channel(fixture.worker(0));

    channel
        .exec(&ctx(), &words(["/usr/bin/true"]), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap();
    assert!(!fixture.fake.saw_call_containing("exec -i"));
    let with_stdin = SpawnOptions {
        stdin: Some(b"content".to_vec()),
        ..SpawnOptions::within(Duration::from_mins(1))
    };
    channel.exec(&ctx(), &words(["/usr/bin/tee", "/tmp/x"]), &with_stdin).await.unwrap();
    assert!(
        fixture
            .fake
            .saw_call_containing(&format!("exec -i {} /usr/bin/tee", fixture.worker(0))),
        "{:?}",
        fixture.fake.calls()
    );
}

/// A loopback listener that answers `reply` to its first connection and then closes it.
async fn one_reply(reply: &'static [u8]) -> (u16, tokio::task::JoinHandle<()>) {
    use tokio::io::AsyncWriteExt as _;
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = listener.local_addr().unwrap().port();
    let served = tokio::spawn(async move {
        let (mut connection, _) = listener.accept().await.unwrap();
        connection.write_all(reply).await.unwrap();
    });
    (port, served)
}

async fn read_all(channel: &dyn Channel, port: u16) -> Vec<u8> {
    use tokio::io::AsyncReadExt as _;
    let mut stream = channel.connect(&ctx(), port).await.unwrap();
    let mut received = Vec::new();
    tokio::time::timeout(Duration::from_secs(20), stream.read_to_end(&mut received))
        .await
        .expect("the relay did not end")
        .unwrap();
    received
}

/// A connect is the agent's `relay` verb over the exec channel, with stdin carried: on Tart that is `-i`, and the
/// agent is the installed one, run without the worker prefix. The fake `tart` bridges the relay to a loopback
/// listener, so the bytes cross the production stream too.
#[tokio::test]
async fn a_tart_connect_is_the_installed_agent_relay_with_stdin_carried() {
    let fixture = Fixture::macos();
    let manager = manager_over(&fixture.settings, fixture.runner(), None, builds_nothing());
    let worker = fixture.worker(0);
    let channel = manager.channel(worker);
    let (port, served) = one_reply(b"HTTP/1.1 200 OK\r\n\r\n").await;
    assert_eq!(read_all(channel.as_ref(), port).await, b"HTTP/1.1 200 OK\r\n\r\n");
    served.await.unwrap();
    let port = port.to_string();
    assert_eq!(
        fixture.fake.argvs().last().map(Vec::as_slice),
        Some(words(["exec", "-i", worker, &fixture.settings.vm_agent, AgentVerb::Relay.as_str(), &port]).as_slice())
    );
}

/// On Parallels the relay is one shell string, like every guest command there, and no tty flag is passed.
#[tokio::test]
async fn a_parallels_connect_is_the_installed_agent_relay_in_one_shell_string() {
    let pool = avl_host_testkit::HostPool::builder(Backend::Parallels, GuestOs::Macos, crate::worker::tart::MINIMUM_VERSION)
        .with_parallels()
        .build();
    let manager = manager_over(&pool.settings, pool.runner(), None, builds_nothing());
    let worker = pool.settings.workers[0].clone();
    let channel = manager.channel(&worker);
    let (port, served) = one_reply(b"from the macOS guest").await;
    assert_eq!(read_all(channel.as_ref(), port).await, b"from the macOS guest");
    served.await.unwrap();
    let line = format!("'{}' '{}' '{port}'", pool.settings.vm_agent, AgentVerb::Relay.as_str());
    assert_eq!(
        pool.fake.argvs().last().map(Vec::as_slice),
        Some(words(["exec", &worker, &line]).as_slice())
    );
}

/// A loopback port where nothing listens, and the sockets that hold it. While they live, the port refuses a connect,
/// and no other socket of the host receives it from a `bind` to port 0 or as the source port of a `connect`.
///
/// The holder is the client end of a loopback connection, and it binds its port before it connects. A dropped
/// listener frees its port for any process of the host. A bound socket that does not listen is not a refusal on
/// macOS, because the kernel drops the SYN.
async fn closed_port() -> (u16, impl Sized) {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let holder = tokio::net::TcpSocket::new_v4().unwrap();
    holder.bind((std::net::Ipv4Addr::LOCALHOST, 0).into()).unwrap();
    let holder = holder.connect(listener.local_addr().unwrap()).await.unwrap();
    let (accepted, _) = listener.accept().await.unwrap();
    (holder.local_addr().unwrap().port(), (holder, accepted))
}

/// A connect to a port where nothing listens is a refused connection at the first read, with the reason, as the
/// HTTP client expects of a socket.
#[tokio::test]
async fn a_connect_to_a_closed_port_is_refused_at_the_first_read() {
    use tokio::io::AsyncReadExt as _;
    let fixture = Fixture::macos();
    let manager = manager_over(&fixture.settings, fixture.runner(), None, builds_nothing());
    let channel = manager.channel(fixture.worker(0));
    let (port, _held) = closed_port().await;
    let mut stream = channel.connect(&ctx(), port).await.unwrap();
    let error = stream.read(&mut [0; 16]).await.unwrap_err();
    assert_eq!(error.kind(), std::io::ErrorKind::ConnectionRefused, "{error}");
    assert!(error.to_string().contains("relay"), "{error}");
}

/// A peer that is not running has no channel, because a stopped worker cannot share an SSH identity with anything -
/// which is the whole question the guest module asks this for.
#[tokio::test]
async fn a_peer_that_is_not_running_has_no_channel() {
    let fixture = Fixture::macos();
    let peer = fixture.settings.workers.last().unwrap().clone();
    assert!(fixture.manager.peer(&ctx(), &peer).await.unwrap().is_none());
    fixture.run_as_fake_process(&peer).await;
    let channel = fixture.manager.peer(&ctx(), &peer).await.unwrap();
    assert_eq!(channel.map(|channel| channel.worker().to_owned()), Some(peer));
}

// --- starting --------------------------------------------------------------------------------------------------

/// A run process that dies while the worker is starting is reported with the tail of what it said: `tart run` reports
/// an unusable share, a missing image and the host's VM limit only on its own stderr.
///
/// The guest of a run process that dies never answers, so the start can only end at the exit it observes. A guest that
/// answered would end the start as `Started` whenever the fake `tart run` exits later than the first probe.
#[tokio::test]
async fn a_start_whose_run_process_dies_is_reported_with_its_log_tail() {
    let fixture = Fixture::macos();
    fixture.seal_macos_golden();
    fixture.fake.answer(Answer::RunOutput, "tart: the golden image is not there\n");
    fixture.fake.answer(Answer::RunExit, "1");
    fixture.guest.fail_everything();
    let refusal = fixture
        .manager
        .start_without_lifecycle_lock(&ctx(), fixture.worker(0))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "tart_exited", "{refusal:?}");
    assert!(refusal.message.contains("the golden image is not there"), "{}", refusal.message);
}

/// A macOS console with no session is polled rather than failed. The guest answers `tart exec` well before its window
/// server has a session, so `console_login_required` means "poll again".
#[tokio::test]
async fn a_console_with_no_session_is_polled_rather_than_failing_the_boot() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    // `who` with no console line is a booted guest nobody has logged into. Every other guest command answers with
    // silence, so nothing beyond the login gate can be what decides this.
    fixture
        .guest
        .answer(answer_guest(vec![("/usr/bin/who", said("admin    ttys000\n"))]));
    let ready = fixture.manager.finish_tart_start(&ctx(), tart(&fixture), worker).await.unwrap();
    assert!(!ready, "no Aqua session yet is not a finished boot either");
    // Nothing past the gate ran: the storage grow and the parity check both drive the guest.
    for argv in fixture.guest.lines() {
        assert!(argv.contains("/usr/bin/who"), "{argv}");
    }
}

/// A boot builds the outputs it installs into a guest **before the slot is cloned**. `pool start` and `pool recycle`
/// run no lane build, so they installed whatever the output base already held - and a cold clone answered a boot verb
/// with `exited with 64: Usage:`. The order is the assertion.
#[tokio::test]
async fn a_boot_builds_the_guest_outputs_before_it_clones_the_slot() {
    let calls_when_built: Arc<Mutex<Option<Vec<String>>>> = Arc::default();
    let recorded = Arc::clone(&calls_when_built);
    // The build hook reads the fake's call log, which lives beside the fake; the fixture is not built yet, so the
    // hook finds the log through a slot filled in below.
    let log: Arc<Mutex<Option<avl_testkit::tartfake::Fake>>> = Arc::default();
    let log_for_hook = Arc::clone(&log);
    let fixture = Fixture::builder(GuestOs::Macos)
        .build_boot(Arc::new(move |_ctx| {
            let calls = log_for_hook.lock().unwrap().as_ref().map(avl_testkit::tartfake::Fake::calls);
            *recorded.lock().unwrap() = calls;
            async { Ok(()) }.boxed()
        }))
        .build();
    *log.lock().unwrap() = Some(fixture.fake.clone());
    fixture.make_macos_bootable();
    fixture.fake.answer(Answer::RunSleeps, "yes");
    let _reaper = fixture.kill_run_processes_on_drop();

    fixture
        .manager
        .start_without_lifecycle_lock(&ctx(), fixture.worker(0))
        .await
        .unwrap();
    let before_build = calls_when_built
        .lock()
        .unwrap()
        .clone()
        .expect("a boot installed the guest agent without building it");
    // The version gate asks `tart --version` first, which is a probe rather than a change to a slot. What must not
    // have happened yet is a clone.
    assert!(!before_build.iter().any(|call| call.contains("clone")), "{before_build:?}");
    assert!(fixture.fake.saw_call_containing("clone"));
}

/// A boot build that failed stops the boot there, and nothing is cloned: a worker materialized against bytes this
/// checkout could not produce is a worker whose every verb is a guess.
#[tokio::test]
async fn a_boot_that_cannot_build_its_guest_outputs_clones_nothing() {
    let fixture = Fixture::builder(GuestOs::Macos)
        .build_boot(Arc::new(|_ctx| {
            async { Err(Refusal::new("host_build_failed", Exit::FAILURE, "bazel build exited with 1")) }.boxed()
        }))
        .build();
    let refusal = fixture
        .manager
        .start_without_lifecycle_lock(&ctx(), fixture.worker(0))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "host_build_failed");
    assert!(!fixture.fake.saw_call_containing("clone"));
}

/// The whole start sequence over a live run process: the slot is cloned, the process is spawned detached and
/// identified, the guest is provisioned, and the worker is reported as started rather than as already running.
#[tokio::test]
async fn a_start_spawns_identifies_and_provisions() {
    let fixture = Fixture::macos();
    fixture.make_macos_bootable();
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::RunSleeps, "yes");
    let _reaper = fixture.kill_run_processes_on_drop();

    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::Started
    );
    let identity = tart(&fixture)
        .read_process_identity(worker)
        .expect("a started worker leaves an identifiable run process behind");
    assert!(identity.process_command.contains("run"), "{}", identity.process_command);
    // Its own session, so the terminal's Ctrl-C and the controller's exit leave it running.
    assert_eq!(
        nix::unistd::getsid(Some(nix::unistd::Pid::from_raw(identity.pid))).ok(),
        Some(nix::unistd::Pid::from_raw(identity.pid))
    );
    let calls = fixture.fake.calls();
    let run = calls
        .iter()
        .find(|call| call.starts_with("run "))
        .expect("the run process was spawned");
    assert!(run.contains("--no-graphics") && run.ends_with(worker), "{run}");

    // Every step is asserted, and **the order**, not the set: the console login, the TCC check, the worker storage,
    // the parity script, and then the SSH host key of the readiness.
    let argvs = fixture.guest.lines();
    let mut at = 0;
    for step in [
        "/usr/bin/who",
        "/usr/bin/sqlite3",
        "air-init-worker-storage",
        "/bin/sh ",
        "air-ensure-ssh-host-keys",
    ] {
        at = find_step(&argvs, step, at);
    }

    // A second start over the same live process is `already-running`, and spawns nothing. Tart lists the VM the first
    // start cloned; a run process whose VM is not listed is an orphan the start ends first.
    fixture.fake.answer(Answer::ListQuiet, format!("{worker}\n"));
    let before = fixture.fake.calls().len();
    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::AlreadyRunning
    );
    assert!(
        !fixture.fake.calls()[before..].iter().any(|call| call.starts_with("run ")),
        "a live worker must not be spawned a second time"
    );
}

/// A boot whose guest never answered says so by name, and does not say `boot_timeout`: `tart exec` *is* the guest
/// agent, so this is the only place a missing `tart-guest-agent` can be named.
#[tokio::test]
async fn a_boot_whose_guest_never_answered_names_the_guest_agent() {
    let fixture = Fixture::builder(GuestOs::Macos).env("AIR_VM_BOOT_TIMEOUT", "1").build();
    fixture.seal_macos_golden();
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::RunSleeps, "yes");
    let _reaper = fixture.kill_run_processes_on_drop();
    // What an image with no guest agent looks like from the host: the run process is alive and `tart exec` never
    // lands anywhere.
    fixture.guest.fail_everything();

    let refusal = fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "guest_agent_never_answered", "{refusal:?}");
    // The requirement, the service that supplies it, and the knob that raises the budget: sixty seconds of silence
    // does not *prove* an image has no agent.
    for fragment in [worker, "tart-guest-agent", "tart exec", "AIR_VM_BOOT_TIMEOUT"] {
        assert!(refusal.message.contains(fragment), "{fragment}: {refusal}");
    }
}

/// `boot_timeout` keeps its name for the boot that ran out of budget *after* its guest had spoken, and the two refusals
/// are one function's two arms.
#[test]
fn the_two_boot_timeouts_are_the_one_discriminator() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    let stalled = fixture.manager.boot_timed_out(worker, true);
    assert_eq!(stalled.code, "boot_timeout");
    assert!(!stalled.message.contains("tart-guest-agent"), "{stalled}");
    let silent = fixture.manager.boot_timed_out(worker, false);
    assert_eq!(silent.code, "guest_agent_never_answered");
}

// --- pool recycle ----------------------------------------------------------------------------------------------

/// A recycle is a stop that does not give the worker back, so it refuses another session's worker exactly as `pool
/// stop` does - and names the way past it. Nothing is deleted for a worker that was refused.
#[tokio::test]
async fn recycle_refuses_a_leased_worker_and_deletes_nothing() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0).to_owned();
    fixture.fake.answer(Answer::ListQuiet, format!("{worker}\n"));
    let lease = fixture.write_lease(&worker, "another-holders-token");

    let refusal = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "worker_leased", "{refusal:?}");
    assert!(refusal.message.contains("lease release"), "{refusal}");
    assert!(refusal.message.contains(&format!("pool recycle {worker}")), "{refusal}");
    assert!(!refusal.message.contains(&lease.holder), "{refusal}");
    assert!(!fixture.fake.saw_call_containing("delete"));
    // The lease itself is untouched: a refused recycle leaves the slot exactly as it was found.
    assert_eq!(read_lease(&fixture.settings.lease_path(&worker)).unwrap(), Some(lease));
}

/// A pool whose boot build empties the quiet listing of the fake `tart`. The fake `tart delete` keeps a listed VM
/// listed, so without this a recycle finds the deleted clone again and refuses its missing provenance.
fn unlisting_pool(workers: usize) -> Fixture {
    let log: Arc<Mutex<Option<avl_testkit::tartfake::Fake>>> = Arc::default();
    let log_for_hook = Arc::clone(&log);
    let fixture = Fixture::builder(GuestOs::Macos)
        .env("AIR_VM_MAX_WORKERS", &workers.to_string())
        .build_boot(Arc::new(move |_ctx| {
            if let Some(fake) = log_for_hook.lock().unwrap().as_ref() {
                fake.answer(Answer::ListQuiet, "");
            }
            async { Ok(()) }.boxed()
        }))
        .build();
    *log.lock().unwrap() = Some(fixture.fake.clone());
    fixture.make_macos_bootable();
    fixture
}

/// The whole repair over a materialized slot: the VM is deleted, every host record that described its guest goes with
/// it, the worker is booted and provisioned again - and the lifecycle lock file this operation holds survives,
/// because a `flock` binds to an inode and unlinking it would let a contender lock a fresh file at the same path.
#[tokio::test]
async fn recycle_deletes_the_guests_records_and_keeps_the_lifecycle_lock() {
    let fixture = unlisting_pool(2);
    let worker = fixture.worker(0).to_owned();
    fixture.fake.answer(Answer::ListQuiet, format!("{worker}\n"));
    fixture.fake.answer(Answer::RunSleeps, "yes");
    let _reaper = fixture.kill_run_processes_on_drop();

    // Everything a used worker leaves behind, including a directory. The boot at the end of the recycle writes the
    // parity receipt again, so for it what is shown is that the deleted guest's copy is not the one left behind. A
    // macOS boot installs no agent: a lease or a run installs it.
    let directory = fixture.settings.worker_dir(&worker);
    let stale = ["daemon.json", "suspended.json", "guest-agent.json"];
    let rewritten = ["guest-init.json"];
    for name in stale.iter().chain(&rewritten) {
        std::fs::write(directory.join(name), "{}").unwrap();
    }
    std::fs::create_dir_all(directory.join("reports/run-1")).unwrap();

    let outcome = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())))
        .await
        .unwrap();
    let calls = fixture.fake.calls();
    let deleted = calls.iter().position(|call| *call == format!("delete {worker}"));
    let spawned = calls.iter().position(|call| call.starts_with("run "));
    assert!(
        matches!((deleted, spawned), (Some(deleted), Some(spawned)) if spawned > deleted),
        "the clone must be deleted and then booted again: {calls:#?}"
    );
    for name in stale.iter().chain(&["reports"]) {
        assert!(
            !directory.join(name).exists(),
            "{name} described the deleted guest and must not outlive it"
        );
    }
    for name in rewritten {
        let content =
            std::fs::read_to_string(directory.join(name)).unwrap_or_else(|error| panic!("a provisioned worker must carry {name}: {error}"));
        assert!(
            content != "{}" && content.contains(&format!("\"worker\":\"{worker}\"")),
            "{name} must be the new guest's, not the deleted one's: {content}"
        );
    }
    assert!(fixture.manager.lifecycle_lock_path(&worker).exists());
    // The worker is running again, not merely deleted: the pid receipt is the new run process's.
    assert!(tart(&fixture).read_process_identity(&worker).is_some());
    assert_eq!(outcome.data["recycled"], serde_json::json!([worker]));
    assert_eq!(outcome.data["refused"], serde_json::json!({}));
    assert_eq!(outcome.data["action"], "recycle");
}

/// A stop that fails ends the recycle before anything is destroyed: a worker whose run process cannot be brought down
/// still has a VM and its records when the refusal arrives.
#[tokio::test]
async fn recycle_that_cannot_stop_the_worker_deletes_nothing() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0).to_owned();
    fixture.fake.answer(Answer::ListQuiet, format!("{worker}\n"));
    // A live run process of the fake table, and a `tart` that refuses every verb but the listings.
    fixture.run_as_fake_process(&worker).await;
    fixture.fake.answer(Answer::Exit, "1");

    let refusal = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed", "{refusal:?}");
    assert!(!fixture.fake.saw_call_containing("delete"));
    assert!(fixture.settings.pid_path(&worker).exists());
}

/// A clone that fails leaves the slot with no VM and no records rather than half of either, and the refusal is the
/// hypervisor's own - which is what carries what `tart` said.
#[tokio::test]
async fn recycle_leaves_no_half_state_when_the_clone_fails() {
    let fixture = Fixture::macos();
    fixture.seal_macos_golden();
    let worker = fixture.worker(0).to_owned();
    // The slot has no VM, so the delete is a no-op and the clone is the first verb that can fail.
    fixture.fake.answer(Answer::Exit, "1");
    let refusal = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed", "{refusal:?}");
    assert!(fixture.fake.saw_call_containing(&format!("clone {}", fixture.settings.golden_vm)));
    assert!(!fixture.fake.calls().iter().any(|call| call.starts_with("run ")));
    assert!(!fixture.settings.pid_path(&worker).exists());
}

/// `recycle all` repairs what it can and then refuses, naming every worker it left alone and why: its job is a usable
/// pool. It exits temporary here because the one thing in its way - a lease - goes away on its own; a worker that is
/// actually broken makes it a failure.
#[tokio::test]
async fn recycle_all_recycles_what_it_can_and_names_the_rest() {
    let fixture = unlisting_pool(3);
    let [leased, materialized, virgin] = [0, 1, 2].map(|index| fixture.worker(index).to_owned());
    fixture.fake.answer(Answer::ListQuiet, format!("{materialized}\n"));
    fixture.fake.answer(Answer::RunSleeps, "yes");
    let _reaper = fixture.kill_run_processes_on_drop();
    fixture.write_lease(&leased, "another-holders-token");

    let refusal = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::All))
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("recycle_incomplete", Exit::TEMP_FAIL),
        "{refusal:?}"
    );
    let details = refusal.details().expect("the per-worker outcome");
    assert_eq!(details["recycled"], serde_json::json!([materialized, virgin]));
    let refused = details["refused"].as_object().unwrap();
    assert_eq!(refused.len(), 1, "{details}");
    assert_eq!(refused[&leased]["code"], "worker_leased");
    assert!(
        refusal.message.contains(&format!("not recycled: {leased} (worker_leased)")),
        "{}",
        refusal.message
    );
    // The leased worker keeps its VM; the materialized one loses it; the never-cloned one gets one.
    assert!(!fixture.fake.saw_call_containing(&format!("delete {leased}")));
    assert!(fixture.fake.saw_call_containing(&format!("delete {materialized}")));
    assert!(
        fixture
            .fake
            .saw_call_containing(&format!("clone {} {virgin}", fixture.settings.golden_vm))
    );

    // A refusal that does not go away on its own makes the aggregate a failure.
    let broken = Fixture::builder(GuestOs::Macos).build();
    broken.fake.answer(Answer::Exit, "1");
    let refusal = broken
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::All))
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("recycle_incomplete", Exit::FAILURE));
}

/// A name this pool does not contain is refused before anything reaches for that worker's directory: the lifecycle
/// lock lives in it, and opening a lock path under a name nobody owns is how a recycle would operate on another
/// pool's VM.
#[tokio::test]
async fn recycle_refuses_a_worker_this_pool_does_not_contain() {
    let fixture = Fixture::macos();
    let refusal = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker("air-docker-1".to_owned())))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "unknown_worker", "{refusal:?}");
    assert_eq!(fixture.fake.calls(), ["--version"]);
    assert!(!Path::new(&fixture.settings.worker_dir("air-docker-1")).exists());
}

// --- a run process whose VM is gone ---------------------------------------------------------------------------

/// `tart stop` finds a VM by name, so it cannot reach a `tart run` whose VM directory is gone: the stop signals the
/// recorded process itself, and removes its record. Seen live on 2026-09-27, a peer checkout's run on unlinked disks.
#[tokio::test]
async fn stop_ends_a_run_process_whose_vm_tart_no_longer_lists() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    // The quiet listing stays unseeded: Tart lists no VM named `worker`.
    let stale = fixture.run_stale_tart_process(worker).await;
    let before = fixture.fake.calls().len();
    assert_eq!(
        fixture.manager.stop_without_lifecycle_lock(&ctx(), worker, None).await.unwrap(),
        StopState::Stopped
    );
    assert!(!tart(&fixture).process_alive(&ctx(), Some(&stale.identity)).await.unwrap());
    assert!(!fixture.settings.pid_path(worker).exists());
    let calls = &fixture.fake.calls()[before..];
    assert!(!calls.iter().any(|call| call.starts_with("stop ")), "{calls:?}");
}

/// A start over such a process ends it before the clone and boots a new one, rather than cloning a VM nobody boots
/// and waiting out the boot budget.
#[tokio::test]
async fn a_lazy_start_ends_a_stale_run_process_before_cloning() {
    let fixture = Fixture::macos();
    fixture.make_macos_bootable();
    let worker = fixture.worker(0);
    let _reaper = fixture.kill_run_processes_on_drop();
    let stale = fixture.run_stale_tart_process(worker).await;
    let before = fixture.fake.calls().len();
    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::Started
    );
    assert!(!tart(&fixture).process_alive(&ctx(), Some(&stale.identity)).await.unwrap());
    let calls = &fixture.fake.calls()[before..];
    let cloned = calls.iter().position(|call| call.starts_with("clone "));
    // The last `run`: the stale one records itself whenever its process gets to it.
    let spawned = calls.iter().rposition(|call| call.starts_with("run "));
    assert!(
        matches!((cloned, spawned), (Some(cloned), Some(spawned)) if spawned > cloned),
        "{calls:#?}"
    );
    let identity = tart(&fixture).read_process_identity(worker).unwrap();
    assert_ne!(identity.pid, stale.identity.pid);
}

/// The repair after a missing-VM release: `pool recycle` ends the stale process, deletes nothing Tart does not list,
/// clones the slot again and boots it.
#[tokio::test]
async fn recycle_after_a_missing_vm_release_stops_the_stale_run_process_and_clones_again() {
    let fixture = Fixture::macos();
    fixture.make_macos_bootable();
    let worker = fixture.worker(0).to_owned();
    let _reaper = fixture.kill_run_processes_on_drop();
    let stale = fixture.run_stale_tart_process(&worker).await;
    fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())))
        .await
        .unwrap();
    assert!(!tart(&fixture).process_alive(&ctx(), Some(&stale.identity)).await.unwrap());
    let calls = fixture.fake.calls();
    assert!(!calls.iter().any(|call| call.starts_with("stop ")), "{calls:#?}");
    assert!(!calls.iter().any(|call| call.starts_with("delete ")), "{calls:#?}");
    let cloned = calls.iter().position(|call| call.starts_with("clone "));
    // The last `run`: the stale one records itself whenever its process gets to it.
    let spawned = calls.iter().rposition(|call| call.starts_with("run "));
    assert!(
        matches!((cloned, spawned), (Some(cloned), Some(spawned)) if spawned > cloned),
        "{calls:#?}"
    );
    let identity = tart(&fixture).read_process_identity(&worker).unwrap();
    assert_ne!(identity.pid, stale.identity.pid);
}

// --- an interrupt --------------------------------------------------------------------------------------------

/// A manager over the fixture's pool whose children register with `interrupts`, the way `vm` main wires one.
///
/// No stop grace runs out in a test. A process of the fake table never exits, so only the interrupt ends the wait of
/// a stop for it, and a busy host cannot make the wait time out before the interrupt lands.
fn interruptible(fixture: &Fixture, interrupts: &Interrupts) -> Manager {
    let mut manager = manager_over(
        &fixture.settings,
        fixture.runner_with(interrupts),
        Some(fixture.guest.factory()),
        builds_nothing(),
    );
    manager.timings.stop_grace = Duration::from_hours(1);
    manager
}

/// Delivers the operator's Ctrl-C once `ready` holds.
fn interrupt_when(interrupts: &Interrupts, ready: impl Fn() -> bool + Send + 'static) -> tokio::task::JoinHandle<()> {
    let interrupts = interrupts.clone();
    tokio::spawn(async move {
        while !ready() {
            tokio::time::sleep(Duration::from_millis(5)).await;
        }
        interrupts.deliver(avl_host_sys::interrupt::Signal::Interrupt);
    })
}

/// Ctrl-C during a stop keeps the run process record. The run process lives in its own session, so the interrupt
/// does not reach it; a stop that removed the record after an interrupted wait would leave a running VM that the
/// next stop answers as already stopped and the next start boots a second time.
#[tokio::test]
async fn an_interrupted_stop_keeps_the_run_process_record() {
    // With the suspendable device set off, `pool stop` runs `tart stop` and not `tart suspend`.
    let fixture = Fixture::builder(GuestOs::Macos).env("AIR_VM_SUSPENDABLE", "0").build();
    let worker = fixture.worker(0);
    // A process of the fake table stands in for the run process, and `tart stop` does not end it: the wait is still
    // polling when the interrupt lands, or the interrupt reaches `tart stop` itself.
    let identity = fixture.run_as_fake_process(worker).await;
    let interrupts = Interrupts::detached();
    let manager = interruptible(&fixture, &interrupts);
    let fake = fixture.fake.clone();
    let interrupter = interrupt_when(&interrupts, move || fake.saw_call_containing("stop "));

    let ctx = interrupts.context();
    let refusal = manager.stop_without_lifecycle_lock(&ctx, worker, None).await.unwrap_err();
    interrupter.await.unwrap();
    assert_eq!(refusal.code, "worker_interrupted", "{refusal:?}");
    assert!(refusal.message.contains("SIGINT"), "{refusal}");
    let tart = manager.tart().expect("a Tart pool");
    assert_eq!(tart.read_process_identity(worker), Some(identity.clone()));
    // What the stop used to decide from: after the interrupt, a live process still reads as alive.
    assert!(tart.process_alive(&ctx, Some(&identity)).await.unwrap());
}

/// Ctrl-C while an incompatible suspended state is being spent keeps both records. The resumed run process is still
/// up, so removing the pid receipt would strand it, and the `tart stop` that would have ended it is not attempted.
#[tokio::test]
async fn an_interrupted_spend_of_a_suspended_state_keeps_its_records() {
    let fixture = Fixture::macos();
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::RunSleeps, "yes");
    fixture.fake.answer(
        Answer::ListJson,
        format!(r#"[{{"Name":"{worker}","Disk":200,"State":"suspended"}}]"#),
    );
    // A guest that never answers keeps the spend polling until the interrupt.
    fixture.guest.fail_everything();
    let _reaper = fixture.kill_run_processes_on_drop();
    let argv = fixture.manager.tart_run_argv(worker).unwrap();
    let mut recorded = argv.clone();
    recorded.insert(2, "--suspendable-with-another-share".to_owned());
    let record = SuspendedState {
        schema_version: SCHEMA_VERSION,
        worker: worker.to_owned(),
        run_argv: recorded,
    };
    std::fs::write(fixture.settings.suspended_state_path(worker), serde_json::to_vec(&record).unwrap()).unwrap();
    let interrupts = Interrupts::detached();
    let manager = interruptible(&fixture, &interrupts);
    let pid_path = fixture.settings.pid_path(worker);
    let interrupter = interrupt_when(&interrupts, move || pid_path.exists());

    let refusal = manager
        .discard_incompatible_suspended_state(&interrupts.context(), manager.tart().expect("a Tart pool"), worker, &argv)
        .await
        .unwrap_err();
    interrupter.await.unwrap();
    assert_eq!(refusal.code, "worker_interrupted", "{refusal:?}");
    let identity = tart(&fixture)
        .read_process_identity(worker)
        .expect("the resumed run process is still recorded");
    assert!(
        tart(&fixture).process_alive(&ctx(), Some(&identity)).await.unwrap(),
        "the resumed run process is still running"
    );
    assert_eq!(fixture.manager.read_suspended_state(worker), Some(record));
    assert!(!fixture.fake.saw_call_containing("stop "), "{:?}", fixture.fake.calls());
}

/// Ctrl-C during the boot poll is the interrupt, not `tart_exited`: the run process is still running, and the old
/// answer sent the operator to a Tart log that says nothing went wrong.
#[tokio::test]
async fn an_interrupted_boot_is_not_reported_as_tart_exiting() {
    let fixture = Fixture::macos();
    fixture.seal_macos_golden();
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::RunSleeps, "yes");
    fixture.guest.fail_everything();
    let _reaper = fixture.kill_run_processes_on_drop();
    let interrupts = Interrupts::detached();
    let manager = interruptible(&fixture, &interrupts);
    let pid_path = fixture.settings.pid_path(worker);
    let interrupter = interrupt_when(&interrupts, move || pid_path.exists());

    let refusal = manager
        .start_without_lifecycle_lock(&interrupts.context(), worker)
        .await
        .unwrap_err();
    interrupter.await.unwrap();
    assert_eq!(refusal.code, "worker_interrupted", "{refusal:?}");
    assert!(tart(&fixture).read_process_identity(worker).is_some());
}

/// The share refresh remounts on Tart. The Docker twin, which only settles, is in the Docker lifecycle suite.
#[tokio::test]
async fn the_share_refresh_remounts_on_tart() {
    let tart = Fixture::macos();
    tart.manager.refresh_shares(&ctx(), tart.worker(0)).await.unwrap();
    assert!(
        tart.guest.lines().iter().any(|line| line.contains("remount-shares.sh")),
        "{:#?}",
        tart.guest.lines()
    );
}
