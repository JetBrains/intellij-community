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
