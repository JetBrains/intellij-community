//! The port answers in the test process. On Unix each answer is compared with the script's answer to the same call.

use std::io::Cursor;

use pretty_assertions::assert_eq;

use super::*;
use crate::tartfake::{Binary, Fake};

/// What one call printed and exited with.
#[derive(Debug, PartialEq, Eq)]
struct Output {
    stdout: String,
    stderr: String,
    code: i32,
}

/// Answers one call of the port, in the directory of `fake`, as the tool that `fake` is.
fn port(fake: &Fake, args: &[&str]) -> Output {
    let args: Vec<String> = args.iter().map(|arg| (*arg).to_owned()).collect();
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    let reply = serve(fake.executable(), &args, &mut stdout, &mut stderr).expect("the port answers");
    let Reply::Exit(code) = reply else {
        panic!("{args:?} relays: {reply:?}");
    };
    Output {
        stdout: String::from_utf8(stdout).expect("text"),
        stderr: String::from_utf8(stderr).expect("text"),
        code,
    }
}

/// The fake `docker` of a fresh directory. On Unix it is the script, and the port answers in the same directory.
fn docker() -> Fake {
    Fake::install_binary(Binary::Docker, "Docker version 29.0.0\n")
}

const STATUS: [&str; 6] = [
    "inspect",
    "--type",
    "container",
    "--format",
    "{{.State.Status}}/{{.State.ExitCode}}",
    "w",
];

#[test]
fn the_port_moves_the_one_container_through_its_states() {
    let fake = docker();
    assert_eq!(port(&fake, &STATUS).code, 1);
    assert_eq!(port(&fake, &STATUS).stderr, "Error: No such container: w\n");
    assert_eq!(port(&fake, &["create", "--name", "w", "image:tag"]).stdout, "fake-container-1\n");
    assert_eq!(port(&fake, &STATUS).stdout, "created/0\n");
    assert_eq!(port(&fake, &["start", "w"]).code, 0);
    assert_eq!(port(&fake, &STATUS).stdout, "running/0\n");
    assert_eq!(port(&fake, &["rm", "w"]).code, 1);
    port(&fake, &["stop", "--time", "10", "w"]);
    assert_eq!(port(&fake, &STATUS).stdout, "exited/0\n");
    assert_eq!(port(&fake, &["rm", "w"]).code, 0);
    assert_eq!(port(&fake, &STATUS).code, 1);
    // The port records into the call log the fake reads.
    assert_eq!(fake.argvs().len(), 11);
    assert_eq!(fake.argvs()[2], ["create", "--name", "w", "image:tag"]);
}

#[test]
fn the_port_holds_the_images_that_build_pull_and_tag_make() {
    let fake = docker();
    let inspect = ["image", "inspect", "air-ui-worker:x"];
    assert_eq!(port(&fake, &inspect).code, 1);
    port(&fake, &["build", "--tag", "air-ui-worker:x", "/ctx"]);
    assert_eq!(port(&fake, &inspect).code, 0);
    assert_eq!(port(&fake, &["pull", "remote:x"]).code, 1);
    fake.answer(Answer::PullExit, "0");
    assert_eq!(port(&fake, &["pull", "remote:x"]).code, 0);
    fake.answer(Answer::ImageRevision, "x\n");
    assert_eq!(
        port(&fake, &["image", "inspect", "--format", "{{.Config.Labels}}", "remote:x"]).stdout,
        "x\n"
    );
    port(&fake, &["image", "rm", "remote:x"]);
    assert_eq!(port(&fake, &["image", "inspect", "remote:x"]).code, 1);
    assert_eq!(port(&fake, &inspect).code, 0);
}

/// A relay reaches a loopback listener both ways and ends at the end of the socket.
#[test]
fn the_relay_bridges_to_a_loopback_port() {
    let fake = docker();
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("a loopback port");
    let port_number = listener.local_addr().expect("the bound port").port();
    let served = std::thread::spawn(move || {
        let (mut connection, _) = listener.accept().expect("the relay connects");
        let mut received = vec![0; "to the guest".len()];
        connection.read_exact(&mut received).expect("the request");
        connection.write_all(b"from the guest").expect("the reply");
        String::from_utf8(received).expect("text")
    });
    let port_text = port_number.to_string();
    let args: Vec<String> = ["exec", "-i", "w", "/x/vm-guest-agent", "relay", port_text.as_str()]
        .map(str::to_owned)
        .into();
    let reply = serve(fake.executable(), &args, &mut Vec::new(), &mut Vec::new()).unwrap();
    assert_eq!(reply, Reply::Relay(port_number));
    let mut output = Vec::new();
    let code = relay(port_number, Cursor::new(b"to the guest".to_vec()), &mut output);
    assert_eq!((code, output.as_slice()), (0, b"from the guest".as_slice()));
    assert_eq!(served.join().expect("the listener ends"), "to the guest");
}

#[test]
fn the_port_answers_as_git_by_its_name() {
    let fake = docker();
    let git = fake.directory().join("git");
    let call = |args: &[&str]| {
        let args: Vec<String> = args.iter().map(|arg| (*arg).to_owned()).collect();
        let mut stdout = Vec::new();
        let Reply::Exit(code) = serve(&git, &args, &mut stdout, &mut Vec::new()).unwrap() else {
            panic!("git relays");
        };
        (code, String::from_utf8(stdout).unwrap())
    };
    fs::write(fake.directory().join(GIT_HEAD), "abc\n").unwrap();
    fs::write(fake.directory().join(GIT_STATUS), " M a.txt\0").unwrap();
    assert_eq!(call(&["rev-parse", "--is-inside-work-tree"]), (0, "true\n".to_owned()));
    assert_eq!(call(&["-C", "/repo", "rev-parse", "HEAD"]), (0, "abc\n".to_owned()));
    assert_eq!(call(&["status", "-z"]), (0, " M a.txt\0".to_owned()));
    fs::write(fake.directory().join(GIT_FAILS), "1").unwrap();
    assert_eq!(call(&["status"]).0, 128);
    assert_eq!(
        fs::read_to_string(fake.directory().join(GIT_CALLS)).unwrap(),
        "rev-parse --is-inside-work-tree\n-C /repo rev-parse HEAD\nstatus -z\nstatus\n"
    );
}

/// The installed fake runs as a process: the script on Unix, and the copied program on Windows.
#[test]
fn the_installed_fake_docker_answers_as_a_process() {
    let fake = docker();
    let output = std::process::Command::new(fake.executable())
        .args(["version", "--format", "{{.Server.Os}}/{{.Server.Arch}}"])
        .output()
        .expect("the fake runs");
    assert_eq!(
        (output.status.code(), output.stdout.as_slice()),
        (Some(0), b"linux/arm64\n".as_slice())
    );
    assert_eq!(fake.calls(), ["version --format {{.Server.Os}}/{{.Server.Arch}}"]);
}

/// One step of a parity run.
#[cfg(unix)]
enum Step {
    Seed(Answer, &'static str),
    Forget(Answer),
    Call(&'static [&'static str]),
}

/// The port and the script answer every call of every verb alike, and leave the same files behind. Each runs in a
/// directory of its own, from the same seeds.
#[cfg(unix)]
#[test]
fn the_port_answers_every_call_as_the_script_does() {
    use Step::{Call, Forget, Seed};
    const STEPS: &[Step] = &[
        Call(&[]),
        Call(&["version"]),
        Seed(Answer::DockerVersion, "linux/amd64\n"),
        Seed(Answer::VersionExit, "3"),
        Call(&["version", "--format", "{{.Server.Os}}/{{.Server.Arch}}"]),
        Forget(Answer::VersionExit),
        Call(&["--version"]),
        Call(&["inspect", "--format", "x", "w"]),
        Call(&STATUS),
        Call(&["create", "--name", "w", "image:tag"]),
        Call(&STATUS),
        Call(&["inspect", "--type", "container", "--format", "{{.State.Running}}", "w"]),
        Call(&["inspect", "--type", "container", "--format", "{{.Id}}", "w"]),
        Seed(Answer::StartedState, "exited/1\n"),
        Call(&["start", "w"]),
        Call(&STATUS),
        Forget(Answer::StartedState),
        Call(&["start", "w"]),
        Call(&["inspect", "--type", "container", "--format", "{{.State.Running}}", "w"]),
        Call(&["rm", "w"]),
        Seed(Answer::Exit, "1"),
        Call(&["stop", "--time", "10", "w"]),
        Call(&["rm", "--force", "w"]),
        Call(&["create", "--name", "w", "image:tag"]),
        Call(&["volume", "rm", "air-w-data"]),
        Call(&["logs", "w"]),
        Forget(Answer::Exit),
        Call(&["stop", "--time", "10", "w"]),
        Call(&["rm", "w"]),
        Call(&["create", "--name", "w", "image:tag"]),
        Call(&["image", "inspect", "air-ui-worker:x"]),
        Call(&["build", "--tag", "air-ui-worker:x", "/ctx"]),
        Call(&["image", "inspect", "air-ui-worker:x"]),
        Call(&["pull", "remote:x"]),
        Seed(Answer::PullExit, "0"),
        Call(&["pull", "remote:x"]),
        Call(&["pull", "remote:y"]),
        Call(&["image", "inspect", "--format", "{{index .Config.Labels \"r\"}}", "remote:x"]),
        Seed(Answer::ImageRevision, "abc\n"),
        Call(&["image", "inspect", "--format", "{{index .Config.Labels \"r\"}}", "remote:x"]),
        Call(&["tag", "remote:x", "air-ui-worker:abc"]),
        Call(&["image", "rm", "remote:x"]),
        Call(&["image", "inspect", "remote:x"]),
        Call(&["image", "ls"]),
        Seed(Answer::ImagePresent, ""),
        Call(&["image", "inspect", "anything:else"]),
        Call(&["buildx", "build", "--push", "/ctx"]),
        Seed(Answer::PushExit, "1"),
        Call(&["buildx", "build", "--push", "/ctx"]),
        Call(&["run", "w"]),
        Call(&["ip", "w"]),
        Call(&["list", "--format", "json"]),
        Seed(Answer::ListJson, "[]"),
        Seed(Answer::ListQuiet, "w\n"),
        Seed(Answer::ListExit, "4"),
        Call(&["list", "--format", "json"]),
        Call(&["list", "--quiet"]),
        Seed(Answer::Status, "running\n"),
        Call(&["status", "w"]),
        Call(&["exec", "w", "/usr/bin/true"]),
        Seed(Answer::ExecStdout, "out"),
        Seed(Answer::ExecStderr, "err"),
        Seed(Answer::ExecExit, "5"),
        Call(&["exec", "w", "/usr/bin/true"]),
        Call(&["exec", "-i", "w", "/x/vm-guest-agent", "relay", "1", "more"]),
        Call(&["exec", "w", "/x/vm-guest-agent", "relay", "1"]),
        Call(&["exec", "w", "/x/vm-guest-agent", "read-file", "/f"]),
        Seed(Answer::ReadFile, "file"),
        Seed(Answer::ReadFileReceipt, "receipt\n"),
        Call(&["exec", "w", "/x/vm-guest-agent", "read-file", "/f"]),
        Seed(Answer::ReadFileFails, ""),
        Call(&["exec", "w", "/x/vm-guest-agent", "read-file", "/f"]),
        Call(&["exec", "w", "a b", "line\nbreak", ""]),
    ];
    let (script, ported) = (docker(), docker());
    for (index, step) in STEPS.iter().enumerate() {
        match step {
            Seed(answer, content) => {
                script.answer(*answer, content);
                ported.answer(*answer, content);
            }
            Forget(answer) => {
                script.forget(*answer);
                ported.forget(*answer);
            }
            Call(args) => {
                let expected = std::process::Command::new(script.executable())
                    .args(*args)
                    .output()
                    .expect("the script runs");
                let expected = Output {
                    stdout: String::from_utf8(expected.stdout).expect("text"),
                    stderr: String::from_utf8(expected.stderr).expect("text"),
                    code: expected.status.code().expect("the script exits"),
                };
                assert_eq!(port(&ported, args), expected, "step {index}: {args:?}");
            }
        }
    }
    assert_eq!(files(&ported), files(&script));
}

/// Every file a fake left in its directory, by name, without the executable and the script's staging files.
#[cfg(unix)]
fn files(fake: &Fake) -> Vec<(String, Vec<u8>)> {
    let mut files: Vec<(String, Vec<u8>)> = fs::read_dir(fake.directory())
        .expect("the directory lists")
        .map(|entry| entry.expect("an entry").path())
        .filter(|path| path.as_path() != fake.executable())
        .map(|path| {
            let name = path.file_name().unwrap().to_string_lossy().into_owned();
            (name, fs::read(&path).expect("the file reads"))
        })
        .filter(|(name, _)| !name.starts_with(".call-"))
        .collect();
    files.sort();
    files
}
