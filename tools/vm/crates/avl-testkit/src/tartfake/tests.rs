//! The fake's suite. The `docker` tests run on every host, over the script on Unix and over the program of
//! [`crate::fakebin`] on Windows. The `tart` and `prlctl` tests are the script's, in `script.rs`.

use super::*;
use pretty_assertions::assert_eq;
use std::process::{Command, Output};

/// Runs the fake with `args` and answers its output.
fn run(fake: &Fake, args: &[&str]) -> Output {
    Command::new(fake.executable())
        .args(args)
        .output()
        .unwrap_or_else(|e| panic!("spawn {}: {e}", fake.executable().display()))
}

fn stdout(output: &Output) -> String {
    String::from_utf8_lossy(&output.stdout).into_owned()
}

fn code(output: &Output) -> i32 {
    output
        .status
        .code()
        .unwrap_or_else(|| panic!("the fake died: {:?} {}", output.status, String::from_utf8_lossy(&output.stderr)))
}

#[cfg(unix)]
mod script;

/// The fake `docker`: beside the fake `tart` on Unix, as a pool installs it, and alone on Windows, which has no fake
/// `tart`.
fn docker() -> Fake {
    if cfg!(unix) {
        Fake::install("2.33.0\n").install_beside(Binary::Docker)
    } else {
        Fake::install_binary(Binary::Docker, "Docker version 29.0.0\n")
    }
}
/// A loopback server that reads a request of `request_len` bytes, then writes `reply` and closes. It reads first,
/// as an HTTP server does, because the relay ends at the end of the socket and drops what stdin still holds.
fn one_reply(request_len: usize, reply: &'static str) -> (u16, std::thread::JoinHandle<String>) {
    use std::io::{Read, Write};
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("a loopback port");
    let port = listener.local_addr().expect("the bound port").port();
    let served = std::thread::spawn(move || {
        let (mut connection, _) = listener.accept().expect("the relay connects");
        let mut received = vec![0; request_len];
        connection.read_exact(&mut received).expect("the request arrives");
        connection.write_all(reply.as_bytes()).expect("the reply is written");
        String::from_utf8(received).expect("the request is text")
    });
    (port, served)
}

/// Runs the fake with `args` and `stdin`, and answers its output.
fn run_with_stdin(fake: &Fake, args: &[&str], stdin: &str) -> Output {
    use std::io::Write;
    use std::process::Stdio;
    let mut child = Command::new(fake.executable())
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap_or_else(|e| panic!("spawn {}: {e}", fake.executable().display()));
    child
        .stdin
        .take()
        .expect("a piped stdin")
        .write_all(stdin.as_bytes())
        .expect("the request is written");
    child.wait_with_output().expect("the fake ends")
}

// --- the fake docker -----------------------------------------------------------------------------------------

/// `docker version` answers the engine the backend accepts unless a suite seeds another one, and the exit code is
/// the shared version code.
#[test]
fn docker_version_answers_linux_arm64_unless_seeded() {
    let fake = docker();
    let output = run(&fake, &["version", "--format", "{{.Server.Os}}/{{.Server.Arch}}"]);
    assert_eq!((stdout(&output), code(&output)), ("linux/arm64\n".to_owned(), 0));
    fake.answer(Answer::DockerVersion, "linux/amd64\n");
    assert_eq!(stdout(&run(&fake, &["version"])), "linux/amd64\n");
    fake.answer(Answer::VersionExit, "1");
    assert_eq!(code(&run(&fake, &["version"])), 1);
}

/// The container verbs are a state machine, because the operations under test are sequences: a `create` has to
/// make the next `inspect` find a container, and a `stop` has to make it read as not running.
#[test]
fn docker_container_verbs_move_the_one_container_through_its_states() {
    let fake = docker();
    let status = [
        "inspect",
        "--type",
        "container",
        "--format",
        "{{.State.Status}}/{{.State.ExitCode}}",
        "w",
    ];
    let running = ["inspect", "--type", "container", "--format", "{{.State.Running}}", "w"];
    assert_eq!(code(&run(&fake, &status)), 1, "no container yet");

    assert_eq!(code(&run(&fake, &["create", "--name", "w", "image:tag"])), 0);
    assert_eq!(stdout(&run(&fake, &status)), "created/0\n");
    assert_eq!(stdout(&run(&fake, &running)), "false\n");
    // An inspect that does not name the container type is refused, so the suite catches one.
    let untyped = ["inspect", "--format", "{{.State.Status}}/{{.State.ExitCode}}", "w"];
    assert_eq!(code(&run(&fake, &untyped)), 2);
    run(&fake, &["start", "w"]);
    assert_eq!(stdout(&run(&fake, &status)), "running/0\n");
    assert_eq!(stdout(&run(&fake, &running)), "true\n");
    run(&fake, &["stop", "--time", "10", "w"]);
    assert_eq!(stdout(&run(&fake, &status)), "exited/0\n");

    // A verb the suite made fail changes nothing.
    fake.answer(Answer::Exit, "1");
    assert_eq!(code(&run(&fake, &["rm", "--force", "w"])), 1);
    assert_eq!(stdout(&run(&fake, &status)), "exited/0\n");
    fake.forget(Answer::Exit);
    run(&fake, &["rm", "--force", "w"]);
    assert_eq!(code(&run(&fake, &status)), 1);
}

/// `create` prints a new id each time and `inspect` answers it, so a test can tell a container made again from the
/// one before. `rm` without `--force` refuses a running container, as the engine does.
#[test]
fn docker_create_names_a_new_id_and_rm_without_force_spares_a_running_container() {
    let fake = docker();
    let status = [
        "inspect",
        "--type",
        "container",
        "--format",
        "{{.State.Status}}/{{.State.ExitCode}}",
        "w",
    ];
    let id = ["inspect", "--type", "container", "--format", "{{.Id}}", "w"];
    let first = run(&fake, &["create", "--name", "w", "image:tag"]);
    assert_eq!(stdout(&first), "fake-container-1\n");
    assert_eq!(stdout(&run(&fake, &id)), "fake-container-1\n");

    run(&fake, &["start", "w"]);
    let refused = run(&fake, &["rm", "w"]);
    assert_eq!(code(&refused), 1);
    assert_eq!(stdout(&run(&fake, &status)), "running/0\n");

    run(&fake, &["stop", "--time", "10", "w"]);
    assert_eq!(code(&run(&fake, &["rm", "w"])), 0);
    assert_eq!(code(&run(&fake, &status)), 1);
    let second = run(&fake, &["create", "--name", "w", "image:tag"]);
    assert_eq!(stdout(&second), "fake-container-2\n");
    assert_eq!(stdout(&run(&fake, &id)), "fake-container-2\n");
}

#[test]
fn docker_build_makes_the_image_that_image_inspect_finds() {
    let fake = docker();
    assert_eq!(code(&run(&fake, &["image", "inspect", "air-ui-worker:x"])), 1);
    assert_eq!(code(&run(&fake, &["build", "--tag", "air-ui-worker:x", "/ctx"])), 0);
    assert_eq!(code(&run(&fake, &["image", "inspect", "air-ui-worker:x"])), 0);

    assert_eq!(code(&run(&fake, &["volume", "rm", "air-w-data"])), 0);
    assert!(fake.saw_call_containing("volume rm air-w-data"));
}

/// The registry verbs: an unseeded `pull` fails, a seeded one makes the pulled reference present and inspectable
/// with its revision label, `tag` makes the local name present, `image rm` forgets the pulled reference, and the
/// `buildx` publish exits from its own code.
#[test]
fn docker_pull_tag_and_rm_move_the_pulled_reference() {
    let fake = docker();
    let remote = "registry.example/air/air-ui-worker:abc";
    let revision = [
        "image",
        "inspect",
        "--format",
        "{{index .Config.Labels \"org.opencontainers.image.revision\"}}",
        remote,
    ];
    assert_eq!(code(&run(&fake, &["pull", remote])), 1);
    assert_eq!(code(&run(&fake, &["image", "inspect", remote])), 1);

    fake.answer(Answer::PullExit, "0");
    assert_eq!(code(&run(&fake, &["pull", remote])), 0);
    assert_eq!(code(&run(&fake, &["image", "inspect", remote])), 0);
    // The local tag is still absent: only the pulled reference is there.
    assert_eq!(code(&run(&fake, &["image", "inspect", "air-ui-worker:abc"])), 1);
    let unlabelled = run(&fake, &revision);
    assert_eq!((stdout(&unlabelled), code(&unlabelled)), (String::new(), 0));
    fake.answer(Answer::ImageRevision, "abc\n");
    assert_eq!(stdout(&run(&fake, &revision)), "abc\n");

    assert_eq!(code(&run(&fake, &["tag", remote, "air-ui-worker:abc"])), 0);
    assert_eq!(code(&run(&fake, &["image", "inspect", "air-ui-worker:abc"])), 0);

    assert_eq!(code(&run(&fake, &["image", "rm", remote])), 0);
    assert_eq!(code(&run(&fake, &["image", "inspect", remote])), 1);
    assert_eq!(code(&run(&fake, &["image", "inspect", "air-ui-worker:abc"])), 0);

    let publish = [
        "buildx",
        "build",
        "--platform",
        "linux/arm64,linux/amd64",
        "--push",
        "--tag",
        remote,
        "/ctx",
    ];
    assert_eq!(code(&run(&fake, &publish)), 0);
    fake.answer(Answer::PushExit, "1");
    assert_eq!(code(&run(&fake, &publish)), 1);
}

// `docker exec` takes an argv, so it shares the `tart` exec arms: the relay and the shared exec answer.
#[test]
fn docker_exec_relays_like_tart_exec() {
    let fake = docker();
    fake.answer(Answer::ExecStdout, "shared");
    let (port, served) = one_reply("to the container".len(), "from the container");
    let port_text = port.to_string();
    let output = run_with_stdin(
        &fake,
        &["exec", "-i", "w", "/x/vm-guest-agent", "relay", &port_text],
        "to the container",
    );
    assert_eq!((stdout(&output), code(&output)), ("from the container".to_owned(), 0));
    assert_eq!(served.join().expect("the listener ends"), "to the container");
    assert_eq!(stdout(&run(&fake, &["exec", "w", "/usr/bin/true"])), "shared");
    // `tart ip` is not a docker verb.
    assert_eq!(stdout(&run(&fake, &["ip", "w"])), "");
}
