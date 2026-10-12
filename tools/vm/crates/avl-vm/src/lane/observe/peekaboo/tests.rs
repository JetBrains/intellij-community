use avl_base::Exit;
use avl_base::format::words;
use avl_host_sys::Ctx;
use avl_host_testkit::{outcome_of, refusal};
use avl_testkit::tartfake::Answer;
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use clap::Parser;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::{ArtifactError, ArtifactOutcome, EXIT_ARTIFACT_PUBLICATION_FAILED, PeekabooArgs, command_peekaboo, verdict};
use crate::lane::observe::testing::{Fixture, parallels_fixture};
use crate::terminal::Output;
use avl_base::RefusalExt;

/// `peekaboo` as `vm` flattens it.
#[derive(Debug, Parser)]
struct Peekaboo {
    #[command(flatten)]
    args: PeekabooArgs,
}

fn parse(words: &[&str]) -> Result<PeekabooArgs, clap::Error> {
    Peekaboo::try_parse_from(std::iter::once("peekaboo").chain(words.iter().copied())).map(|peekaboo| peekaboo.args)
}

fn failed_artifact(code: &str, message: &str) -> Vec<ArtifactOutcome> {
    vec![ArtifactOutcome {
        source: "/g/a.png".to_owned(),
        destination: "/h/a.png".to_owned(),
        bytes: None,
        error: Some(ArtifactError {
            code: code.to_owned(),
            message: message.to_owned(),
        }),
    }]
}

// --- the option grammar ----------------------------------------------------------------------------------------

#[test]
fn the_controller_options_are_split_from_the_peekaboo_command() {
    let args = parse(&[
        "--interactive",
        "--cwd",
        "/guest/dir",
        "--publish",
        "/guest/shot.png",
        "art/shot.png",
        "--",
        "image",
        "--mode",
        "screen",
    ])
    .expect("the invocation parses");
    assert_eq!(
        args,
        PeekabooArgs {
            interactive: true,
            cwd: Some("/guest/dir".to_owned()),
            publish: words(["/guest/shot.png", "art/shot.png"]),
            // A Peekaboo flag after the separator is Peekaboo's, never the controller's.
            command: words(["image", "--mode", "screen"]),
        }
    );
    assert_eq!(args.publish_requests().collect::<Vec<_>>(), [("/guest/shot.png", "art/shot.png")]);
    // Two publish pairs stay two pairs.
    let args = parse(&["--publish", "/g/a", "a", "--publish", "/g/b", "b", "--", "image"]).expect("the invocation parses");
    assert_eq!(args.publish_requests().collect::<Vec<_>>(), [("/g/a", "a"), ("/g/b", "b")]);
}

#[test]
fn what_the_option_grammar_cannot_mean_is_refused() {
    for (name, words) in [
        ("no separator", &["image"][..]),
        ("unknown controller option", &["--frames", "--", "image"]),
        ("relative cwd", &["--cwd", "relative/dir", "--", "image"]),
        ("publish missing its artifact path", &["--publish", "/guest/a.png", "--", "image"]),
        ("empty publish source", &["--publish", "", "a.png", "--", "image"]),
        ("empty command", &["--interactive", "--"]),
    ] {
        assert!(parse(words).is_err(), "{name} was accepted");
    }
    // The one combination the grammar cannot see: a terminal session inside a JSON envelope.
    let interactive = parse(&["--interactive", "--", "image"]).expect("the invocation parses");
    let refusal = interactive
        .validate(Output::Json)
        .expect_err("--interactive was accepted in JSON mode");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    interactive.validate(Output::Text).unwrap();
}

// --- the verdict ordering --------------------------------------------------------------------------------------

/// The command's own failure outranks the artifact failure: an exit code is the primary fact and the artifacts are
/// the evidence salvaged around it, so a caller must never see `peekaboo_artifact_failed` for a run whose command
/// already failed.
#[test]
fn the_command_failure_is_reported_before_the_artifact_failure() {
    let refusal = verdict("macOS".to_owned(), 7, None, failed_artifact("pull_failed", "gone")).expect_err("a failed command was accepted");
    assert_eq!((refusal.code.as_ref(), refusal.exit.code()), ("peekaboo_command_failed", 7));
    // The artifact outcomes still travel, as details on the command's own failure.
    assert_eq!(
        refusal.details(),
        Some(json!({
            "exitCode": 7,
            "artifacts": [{
                "source": "/g/a.png",
                "destination": "/h/a.png",
                "error": { "code": "pull_failed", "message": "gone" },
            }],
        }))
    );
}

#[test]
fn an_artifact_failure_alone_exits_seventy_four() {
    let refusal = verdict(
        "macOS".to_owned(),
        0,
        None,
        failed_artifact("pull_destination_exists", "already there"),
    )
    .expect_err("a failed artifact was accepted");
    assert_eq!(
        (refusal.code.as_ref(), i32::from(refusal.exit)),
        ("peekaboo_artifact_failed", EXIT_ARTIFACT_PUBLICATION_FAILED)
    );
    assert!(refusal.message.contains("already there"), "{}", refusal.message);
}

// --- the command -----------------------------------------------------------------------------------------------

/// Every pool but Parallels, Docker included: Peekaboo's physical input needs the TCC grants only the Parallels image
/// carries.
#[tokio::test]
async fn peekaboo_is_refused_off_the_parallels_backend() {
    for fixture in [Fixture::tart_macos(), Fixture::docker()] {
        let args = parse(&["--", "image"]).expect("the invocation parses");
        let refusal = refusal(
            command_peekaboo(
                &Ctx::background(),
                &fixture.manager,
                args,
                Some(std::path::Path::new("unused")),
                Output::Json,
            )
            .await,
        );
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("unsupported_backend_operation", Exit::USAGE),
            "{:?}",
            fixture.settings.backend
        );
        assert!(refusal.message.contains("Parallels"), "{}", refusal.message);
    }
}

/// The artifacts are published even when the Peekaboo command failed - a screenshot taken before the failure is
/// exactly the evidence the caller needs - and the failure reported is then the command's own.
#[tokio::test]
async fn artifacts_are_published_even_when_the_command_failed() {
    let (fixture, receipt) = parallels_fixture();
    fixture.fake.answer(Answer::ExecStdout, STANDARD.encode("screenshot bytes") + "\n");
    // The fake's one `exec` arm serves the Peekaboo passthrough and the artifact pull alike, so exit 3 fails both.
    // That is fine for what this pins: that publication was *attempted* after the command failed, and that the
    // verdict is the command's failure, not the artifact's.
    fixture.fake.answer(Answer::ExecExit, "3");
    let args = parse(&["--publish", "/guest/shot.png", "art/shot.png", "--", "image", "--mode", "screen"]).expect("the invocation parses");
    let refusal = refusal(command_peekaboo(&Ctx::background(), &fixture.manager, args, Some(&receipt), Output::Json).await);
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit.code()),
        ("peekaboo_command_failed", 3),
        "{}",
        refusal.message
    );
    let details = refusal.details().expect("the refusal carries details");
    let artifacts = details["artifacts"].as_array().expect("artifact outcomes");
    assert_eq!(artifacts.len(), 1, "{artifacts:?}");
    assert_eq!(artifacts[0]["source"], json!("/guest/shot.png"));
    assert_eq!(artifacts[0]["error"]["code"], json!("pull_failed"));
    assert_eq!(details["progress"]["stdoutTruncated"], json!(false));
    let calls = fixture.fake.calls();
    let peekaboo = calls.iter().position(|call| call.contains("peekaboo")).expect("Peekaboo ran");
    assert!(
        calls[peekaboo..].iter().any(|call| call.contains("base64")),
        "the artifact pull did not run after the failed command: {calls:?}"
    );
}

/// The happy path of the same sequence: the command succeeds, the artifact lands, its bytes are counted.
#[tokio::test]
async fn the_requested_artifacts_are_published() {
    let (fixture, receipt) = parallels_fixture();
    let payload = "screenshot bytes";
    fixture.fake.answer(Answer::ExecStdout, STANDARD.encode(payload) + "\n");
    let args = parse(&["--publish", "/guest/shot.png", "art/shot.png", "--", "image"]).expect("the invocation parses");
    let outcome = outcome_of(command_peekaboo(&Ctx::background(), &fixture.manager, args, Some(&receipt), Output::Json).await);
    assert_eq!(outcome.data["exitCode"], json!(0));
    let artifacts = outcome.data["artifacts"].as_array().expect("artifact outcomes");
    assert_eq!(artifacts.len(), 1, "{artifacts:?}");
    assert_eq!(artifacts[0]["bytes"], json!(payload.len()));
    assert!(artifacts[0].get("error").is_none(), "{:?}", artifacts[0]);
    let destination = artifacts[0]["destination"].as_str().expect("a destination");
    assert_eq!(std::fs::read_to_string(destination).expect("the artifact was published"), payload);
    // Inside the worker's own artifact directory, under the parallels- key that keeps a Parallels worker's artifacts
    // apart from a Tart slot of the same name.
    assert!(destination.ends_with("artifacts/parallels-macOS/art/shot.png"), "{destination}");
    // Peekaboo ran as the console user, in one shell string.
    assert!(
        fixture
            .fake
            .saw_call_containing("exec macOS --current-user exec '/opt/homebrew/bin/peekaboo' 'image'"),
        "{:?}",
        fixture.fake.calls()
    );
}
