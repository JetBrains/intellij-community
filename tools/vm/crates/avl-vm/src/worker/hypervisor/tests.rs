use avl_base::report::Mode;
use avl_base::{Backend, Reporter};
use pretty_assertions::assert_eq;

use super::*;

// The code is what callers branch on, so it must be the code that reaches the envelope - not `internal_error` at
// exit 1 - and the prose must survive with it.
#[test]
fn an_unsupported_refusal_survives_the_envelope() {
    let (reporter, stdout, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(Mode::Json);
    let refusal = unsupported(format!(
        "pool gc removes Tart worker clones; {} owns its own VM",
        Backend::Parallels
    ));
    assert_eq!(reporter.refuse("pool", &refusal), Exit::USAGE);
    let envelope: serde_json::Value =
        serde_json::from_str(&stderr.text()).unwrap_or_else(|failure| panic!("the envelope is not JSON: {failure} ({})", stderr.text()));
    assert_eq!(envelope["ok"], false);
    assert_eq!(envelope["error"]["code"], UNSUPPORTED_CODE);
    let message = envelope["error"]["message"].as_str().unwrap();
    assert!(message.contains("owns its own VM"), "{message}");
    assert!(stdout.is_empty(), "a refusal wrote to stdout: {}", stdout.text());
}

#[test]
fn unsupported_refusals_are_recognised_by_code() {
    assert!(is_unsupported(&unsupported("no")));
    assert!(is_unsupported(&Refusal::new(UNSUPPORTED_CODE, Exit::USAGE, "built elsewhere")));
    assert!(!is_unsupported(&Refusal::new("boot_timeout", Exit::FAILURE, "no")));
    assert_eq!(unsupported("no").exit, Exit::USAGE);
}

// The three questions reach the Docker backend: the gate asks the engine, the guest argv is `docker exec`, and a
// container that does not exist is not running.
#[tokio::test]
async fn the_machine_questions_reach_the_docker_backend() {
    let fixture = crate::worker::testing::Fixture::docker();
    let ctx = Ctx::background();
    let machine = fixture.manager.machine();
    assert!(matches!(machine, Machine::Docker(_)));
    machine.require_available(&ctx, "air-docker-1").await.unwrap();
    let argv = machine
        .guest_argv(&ctx, "air-docker-1", &["/usr/bin/true".to_owned()], true)
        .await
        .unwrap();
    assert_eq!(argv[1..], ["exec", "-i", "air-docker-1", "/usr/bin/true"]);
    assert!(!machine.running(&ctx, "air-docker-1").await.unwrap());
    fixture.fake.answer(avl_testkit::tartfake::Answer::ContainerState, "running/0\n");
    assert!(machine.running(&ctx, "air-docker-1").await.unwrap());
}

// The questions reach the container-linux backend: the gate asks the script's `list`, the guest argv is the script's
// `exec`, the shares are bind mounts, and liveness is the container line of this checkout. A `list` that fails is
// a runtime that does not answer, for the gate and for the liveness question alike.
#[cfg(unix)]
#[tokio::test]
async fn the_machine_questions_reach_the_container_linux_backend() {
    use avl_host_sys::guest::ShareMount;
    use avl_testkit::tartfake::Answer;
    let fixture = crate::worker::testing::Fixture::container_linux();
    let ctx = Ctx::background();
    let machine = fixture.manager.machine();
    assert!(matches!(machine, Machine::ContainerLinux(_)));
    machine.require_available(&ctx, "container-linux-1").await.unwrap();
    let argv = machine
        .guest_argv(&ctx, "container-linux-1", &["/usr/bin/true".to_owned()], true)
        .await
        .unwrap();
    let exec = argv
        .iter()
        .position(|word| word == "exec")
        .unwrap_or_else(|| panic!("no exec in {argv:?}"));
    assert_eq!(argv[exec..], ["exec", "/usr/bin/true"]);
    // The word before the verb is the script, whichever file beside the configured one the host runs.
    let script = std::path::Path::new(&argv[exec - 1]);
    assert_eq!(script.parent(), fixture.settings.container_linux_script.parent(), "{argv:?}");
    assert_eq!(script.file_stem().and_then(|stem| stem.to_str()), Some("container"), "{argv:?}");
    assert_eq!(machine.share_mount(), ShareMount::Bind);
    assert!(!machine.running(&ctx, "container-linux-1").await.unwrap());
    fixture.fake.answer(Answer::ContainerLinuxState, "running\n");
    assert!(machine.running(&ctx, "container-linux-1").await.unwrap());

    fixture.fake.answer(Answer::ContainerLinuxFailedVerb, "list");
    for refusal in [
        machine.require_available(&ctx, "container-linux-1").await.unwrap_err(),
        machine.running(&ctx, "container-linux-1").await.unwrap_err(),
    ] {
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("container_linux_unavailable", Exit::UNAVAILABLE),
            "{refusal:?}"
        );
        assert!(refusal.message.contains("list failed"), "{}", refusal.message);
    }
}

// A checkout without the testing-ui skill is refused by name before anything runs, and the refusal names the path
// the script would have.
#[cfg(unix)]
#[tokio::test]
async fn a_missing_container_linux_script_is_refused_by_name() {
    let fixture = crate::worker::testing::Fixture::container_linux();
    let script = fixture.settings.container_linux_script.clone();
    for file in [script.clone(), script.with_extension("sh")] {
        std::fs::remove_file(&file).expect("the fake script is removed");
    }
    let refusal = fixture
        .manager
        .machine()
        .require_available(&Ctx::background(), "container-linux-1")
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("container_linux_script_missing", Exit::UNAVAILABLE),
        "{refusal:?}"
    );
    assert!(
        refusal.message.contains(&script.to_string_lossy().into_owned()),
        "{}",
        refusal.message
    );
    assert!(fixture.fake.calls().is_empty(), "{:?}", fixture.fake.calls());
}
