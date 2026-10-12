use avl_host_sys::{Captured, Ctx};
use avl_host_testkit::{answer_exit, answer_text, handler, refusal};
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::testing::PLENTIFUL_DF;

fn generation(digest: &str) -> String {
    format!("/vm/data/daemon-runtime/generations/{digest}")
}

fn write_receipt(fixture: &Fixture, digest: &str, mutate: impl FnOnce(&mut Value)) {
    let root = generation(digest);
    let mut receipt = json!({
        "schemaVersion": 1,
        "worker": fixture.worker,
        "runtimeDigest": digest,
        "root": root,
        "javaBinary": format!("{root}/jbr/bin/java"),
        "classpathFile": format!("{root}/classpath.txt"),
        "classpath": [format!("{root}/classpath/000.jar"), format!("{root}/classpath/001.jar")],
    });
    mutate(&mut receipt);
    std::fs::write(
        staged_runtime_receipt_path(&fixture.settings, &fixture.worker),
        format!("{receipt}\n"),
    )
    .unwrap();
}

async fn ensure(fixture: &Fixture, prep: &PreparedBuild) -> Result<GuestRuntime, Refusal> {
    let ctx = Ctx::background();
    let channel = fixture.channel();
    fixture
        .host
        .ensure_guest_runtime(&fixture.host.guest(&ctx, channel.as_ref()), prep)
        .await
}

const DIGEST: &str = "2f0784bd199fc61fa6224be41f8c6cd75321e6ac77acebc1d1354ac21a45e147";

// The receipt is `None` on any mismatch - never an error - because every way of not trusting it falls through to a
// full stage, which is what happened unconditionally before the receipt existed.
#[tokio::test]
async fn a_staged_receipt_is_none_on_any_mismatch() {
    let fixture = Fixture::new().await;
    write_receipt(&fixture, DIGEST, |_| {});
    let read = read_staged_runtime_receipt(&fixture.settings, &fixture.worker).expect("an intact receipt reads back");
    assert_eq!(read.classpath.len(), 2);

    type Mutation = fn(&mut Value);
    let mutations: [(&str, Mutation); 10] = [
        ("a wrong schema", |r| r["schemaVersion"] = json!(99)),
        ("another worker", |r| r["worker"] = json!("somebody-else")),
        ("a digest that is not hex", |r| {
            r["runtimeDigest"] = json!("not-a-digest");
        }),
        ("a short digest", |r| r["runtimeDigest"] = json!("abc123")),
        ("a missing root", |r| {
            r.as_object_mut().unwrap().remove("root");
        }),
        ("a missing java binary", |r| {
            r.as_object_mut().unwrap().remove("javaBinary");
        }),
        ("a missing classpath file", |r| {
            r.as_object_mut().unwrap().remove("classpathFile");
        }),
        ("a classpath that is null", |r| r["classpath"] = Value::Null),
        ("a non-string in classpath", |r| {
            r["classpath"] = json!(["/a", 7]);
        }),
        ("a missing classpath", |r| {
            r.as_object_mut().unwrap().remove("classpath");
        }),
    ];
    for (name, mutate) in mutations {
        write_receipt(&fixture, DIGEST, mutate);
        assert_eq!(read_staged_runtime_receipt(&fixture.settings, &fixture.worker), None, "{name}");
    }
    std::fs::write(staged_runtime_receipt_path(&fixture.settings, &fixture.worker), "{").unwrap();
    assert_eq!(
        read_staged_runtime_receipt(&fixture.settings, &fixture.worker),
        None,
        "unreadable JSON"
    );
}

// The stage-check verb travels with the generation, the count, the JBR suffix and the classpath-file digest, under
// the staging timeout code - so a wedged check reads as staging, not as a mystery.
#[tokio::test]
async fn stage_check_asks_with_the_classpath_digest_and_reuses_the_generation() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    write_receipt(&fixture, &prep.runtime_digest, |_| {});
    let root = generation(&prep.runtime_digest);
    fixture.on("df", answer_text(PLENTIFUL_DF));
    fixture.on(
        "stage-check",
        answer_text(
            json!({
                "complete": true,
                "root": root,
                "javaBinary": format!("{root}/jbr/bin/java"),
                "classpathFile": format!("{root}/classpath.txt"),
            })
            .to_string(),
        ),
    );
    let staged = ensure(&fixture, &prep).await.unwrap();
    assert!(staged.reused && staged.root == root && staged.classpath.len() == 2, "{staged:?}");

    let channel = fixture.channel();
    let checks = channel.calls_containing("stage-check");
    assert_eq!(checks.len(), 1, "{checks:?}");
    let expected = digest::sha256_text(&wire::classpath_file_text(&[
        format!("{root}/classpath/000.jar"),
        format!("{root}/classpath/001.jar"),
    ]));
    assert!(
        checks[0].ends_with(&format!(
            "stage-check /vm/data/daemon-runtime {} 2 a&b {expected}",
            prep.runtime_digest
        )),
        "{}",
        checks[0]
    );
    let options = channel.options_for_call_containing("stage-check").unwrap();
    assert_eq!(options.timeout, Duration::from_secs(120));
    assert_eq!(options.timeout_code, Some("guest_runtime_stage_timeout"));
    assert!(
        channel.calls_containing("vm-guest-agent stage ").is_empty(),
        "a reused generation is not staged again"
    );
}

// A guest that cannot answer the stage-check - an agent that does not know the verb, or fails it - is a note and a
// full stage, never a failure: a full stage is always correct.
#[tokio::test]
async fn a_guest_that_cannot_answer_stage_check_falls_through_to_a_full_stage() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    write_receipt(&fixture, &prep.runtime_digest, |_| {});
    fixture.install_happy_guest(&prep);
    fixture.on("stage-check", answer_exit(1));
    let staged = ensure(&fixture, &prep).await.unwrap();
    assert!(!staged.reused, "an unanswered check falls through to a full stage");

    let channel = fixture.channel();
    assert_eq!(channel.calls_containing("vm-guest-agent stage /vm").len(), 1);
    let options = channel.options_for_call_containing("vm-guest-agent stage /vm").unwrap();
    assert_eq!(options.timeout, Duration::from_mins(30));
    assert_eq!(options.timeout_code, Some("guest_runtime_stage_timeout"));
    // The stage's receipt is remembered for the next iteration.
    let remembered = read_staged_runtime_receipt(&fixture.settings, &fixture.worker).expect("a full stage leaves a receipt");
    assert_eq!(remembered.runtime_digest, prep.runtime_digest);
    assert_eq!(remembered.classpath.len(), 2);
    // And the manifest it staged from goes on the stage's stdin, with no file and no exec of its own, and it names
    // the host paths in classpath order.
    let manifest = options.stdin.expect("the manifest is on the stage's stdin");
    assert!(
        channel.calls_containing("runtime-stage.json").is_empty(),
        "the manifest was written to a guest file: {:?}",
        channel.lines()
    );
    let manifest: RuntimeManifest = serde_json::from_slice(&manifest).unwrap();
    assert!(
        manifest.stable_sources[0].ends_with("_main/stable/one.jar") && manifest.stable_sources[1].ends_with("_main/stable/two.jar"),
        "{manifest:?}"
    );
}

// What a failed agent verb reports, from the caller's side: the agent and its own exit, never the sudo in front of
// it. Both agents' usage shapes read as an agent older than this controller.
#[tokio::test]
async fn a_failed_agent_verb_names_the_agent_and_not_the_sudo() {
    let fixture = Fixture::new().await;
    fixture.on("df", answer_text(PLENTIFUL_DF));
    let prep = fixture.prepared().await;

    // An agent that does not know the verb prints a usage block.
    fixture.on(
        "stage",
        handler(|_, _| {
            Ok(Captured {
                exit_code: 64,
                stderr: "usage: vm-guest-agent stage <runtime-root> <manifest>\n".to_owned(),
                ..Captured::default()
            })
        }),
    );
    let failure = refusal(ensure(&fixture, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit.code()), ("guest_agent_failed", 64));
    for fragment in [
        "stage",
        fixture.worker.as_str(),
        "exited with 64",
        "usage: vm-guest-agent stage",
        "older than this controller",
    ] {
        assert!(failure.message.contains(fragment), "{fragment}: {}", failure.message);
    }
    assert!(!failure.message.contains("sudo"), "{}", failure.message);

    // The Rust agent answers an envelope with code `usage`.
    fixture.on(
        "stage",
        handler(|_, _| {
            Ok(Captured {
                exit_code: 64,
                stderr: json!({"schemaVersion": 1, "ok": false, "command": "stage",
                    "error": {"code": "usage", "message": "unknown argument --manifest"}})
                .to_string(),
                ..Captured::default()
            })
        }),
    );
    let failure = refusal(ensure(&fixture, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit.code()), ("usage", 64));
    assert!(
        failure.message.contains("unknown argument --manifest")
            && failure.message.contains("older than this controller")
            && !failure.message.contains("sudo"),
        "{}",
        failure.message
    );
}

// A reuse answer that says complete and carries no paths is a protocol failure, not a fall-through: the guest
// claimed the generation and refused to say where it is.
#[tokio::test]
async fn a_reuse_answer_without_its_paths_refuses() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    write_receipt(&fixture, &prep.runtime_digest, |_| {});
    fixture.on("df", answer_text(PLENTIFUL_DF));

    fixture.on("stage-check", answer_text(r#"{"complete":true}"#));
    let failure = refusal(ensure(&fixture, &prep).await);
    assert_eq!(failure.code, "guest_runtime_stage_failed");
    assert!(failure.message.contains("answered without its paths"), "{}", failure.message);

    fixture.on("stage-check", answer_text("not json"));
    let failure = refusal(ensure(&fixture, &prep).await);
    assert_eq!(failure.code, "guest_runtime_stage_failed");
    assert!(failure.message.contains("invalid JSON"), "{}", failure.message);

    fixture.install_happy_guest(&prep);
    fixture.on("stage-check", answer_text(r#"{"complete":false}"#));
    let staged = ensure(&fixture, &prep).await.unwrap();
    assert!(!staged.reused, "an honest not-complete falls through to the stage");
}

// A stage whose reply names paths outside its generation is refused: the JVM would be launched over bytes this
// build never named.
#[tokio::test]
async fn a_stage_outside_its_generation_refuses() {
    let fixture = Fixture::new().await;
    fixture.on("df", answer_text(PLENTIFUL_DF));
    let prep = fixture.prepared().await;
    fixture.on(
        "stage",
        answer_text(
            r#"{"root":"/somewhere/else","javaBinary":"/somewhere/else/java","classpath":["/somewhere/else/a.jar","/somewhere/else/b.jar"],"classpathFile":"/somewhere/else/classpath.txt","reused":false}"#,
        ),
    );
    let failure = refusal(ensure(&fixture, &prep).await);
    assert_eq!(failure.code, "guest_runtime_stage_failed");
    assert!(failure.message.contains("outside its generation"), "{}", failure.message);
}

// A sibling directory whose name merely begins with the generation's is outside it.
#[test]
fn a_path_beside_the_generation_is_outside_it() {
    let root = generation(DIGEST);
    let staged = GuestRuntime {
        root: root.clone(),
        java_binary: format!("{root}-evil/java"),
        classpath: vec![format!("{root}/a.jar")],
        classpath_file: format!("{root}/classpath.txt"),
        reused: false,
    };
    assert!(require_stage_within_generation(&staged, &root, 1).is_err());
    let honest = GuestRuntime {
        java_binary: format!("{root}/jbr/bin/java"),
        ..staged
    };
    require_stage_within_generation(&honest, &root, 1).unwrap();
    assert!(require_stage_within_generation(&honest, &root, 2).is_err());
}

// The receipt's mere existence is not reuse: a receipt for another generation asks no question at all.
#[tokio::test]
async fn a_receipt_for_another_generation_skips_the_check_entirely() {
    let fixture = Fixture::new().await;
    write_receipt(&fixture, &"ab".repeat(32), |_| {});
    let prep = fixture.prepared().await;
    fixture.install_happy_guest(&prep);
    ensure(&fixture, &prep).await.unwrap();
    assert!(
        fixture.channel().calls_containing("stage-check").is_empty(),
        "a mismatched receipt must not cost a guest round-trip"
    );
}
