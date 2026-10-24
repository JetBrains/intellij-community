use pretty_assertions::assert_eq;
use yonder_link::proto::Reply;

use super::*;
use crate::testkit::{ALIAS, FakeProcesses, Said, Setup, calm, client, failed, ok};

/// The binary of the tests and its SHA-256.
const BINARY: &[u8] = b"a yonderd binary";

fn runs_reply(_: &yonder_link::proto::Request, channel: &mut yonder_link::Channel) {
    let _ = channel.send(&Reply::Runs { runs: Vec::new() });
}

/// A host whose ssh answers each call of `prepare`: the probe with `host_sum`, then the setup line of the agent.
fn host(setup: &Setup, host_sum: &str) -> FakeProcesses {
    let line = format!(
        "{{\"fingerprint\":\"{}\",\"port\":{},\"launchd\":\"restarted\"}}\n",
        setup.agent.identity.fingerprint(),
        setup.agent.address.port()
    );
    FakeProcesses::new(&[
        ("shasum -a 256", ok(&format!("{host_sum}  .local/bin/yonderd\n"))),
        ("yonderd setup", ok(&line)),
        ("ssh -G studio", ok("user develar\nhostname 127.0.0.1\nport 22\n")),
    ])
}

fn agent_file(setup: &Setup) -> PathBuf {
    let path = setup.dir.path().join("yonderd");
    fs::write(&path, BINARY).expect("the binary");
    path
}

fn request(agent: &Path) -> PrepareRequest<'_> {
    PrepareRequest {
        alias: ALIAS,
        agent,
        port: 7741,
        repo: "~/projects/idea-1",
        name: "bt on dev's Mac",
    }
}

#[test]
fn another_binary_is_copied_set_up_and_checked_over_the_link() {
    let setup = Setup::new(runs_reply);
    fs::remove_file(setup.config.host_path(ALIAS)).expect("no host file yet");
    let processes = host(&setup, "0000");
    let said = Said::default();
    let say = |line: &str| said.push(line);
    let agent = agent_file(&setup);
    let prepared = client(&setup, &processes, &say, &calm).prepare(&request(&agent)).expect("prepared");

    assert!(prepared.copied);
    assert_eq!(prepared.launchd, "restarted");
    assert_eq!(prepared.agent.address, setup.agent.address);
    let file = setup.config.read_host(ALIAS).expect("the host file");
    assert_eq!(file.fingerprint, setup.agent.identity.fingerprint());
    assert_eq!((file.hostname.as_str(), file.port), ("127.0.0.1", setup.agent.address.port()));
    assert_eq!(file.addresses, [setup.agent.address]);

    let calls = processes.calls();
    let ssh_prefix: Vec<String> = std::iter::once("ssh")
        .chain(SSH_OPTIONS)
        .chain([ALIAS])
        .map(str::to_owned)
        .collect();
    let copy = calls
        .iter()
        .find(|call| call.text().contains("cat > .local/bin/yonderd.new"))
        .expect("the copy");
    assert_eq!(copy.args[..=SSH_OPTIONS.len()], ssh_prefix[1..]);
    assert_eq!(copy.input, BINARY);
    assert_eq!(copy.limit, crate::process::Limit::Deadline(COPY_DEADLINE));
    let client_fingerprint = setup.config.identity().expect("the identity").fingerprint();
    assert!(
        processes.texts().iter().any(|text| text.ends_with(&format!(
            "studio .local/bin/yonderd setup --client {client_fingerprint} --name 'bt on dev'\\''s Mac' --port 7741"
        ))),
        "{:#?}",
        processes.texts()
    );
    assert!(said.lines().iter().any(|line| line.starts_with("yonder: copying yonderd (16 B)")));
}

#[test]
fn the_same_binary_is_not_copied_again() {
    let setup = Setup::new(runs_reply);
    let processes = host(&setup, &sha256_hex(BINARY));
    let agent = agent_file(&setup);
    let prepared = client(&setup, &processes, &|_| {}, &calm)
        .prepare(&request(&agent))
        .expect("prepared");
    assert!(!prepared.copied);
    assert!(!processes.texts().iter().any(|text| text.contains("cat >")));
}

#[test]
fn a_host_that_ssh_cannot_reach_is_refused_with_the_ssh_check() {
    let setup = Setup::new(runs_reply);
    let processes = FakeProcesses::new(&[("shasum", failed(255, "ssh: connect to host studio port 22: Operation timed out\n"))]);
    let agent = agent_file(&setup);
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .prepare(&request(&agent))
        .expect_err("no ssh");
    assert_eq!(refusal.code, code::SSH_FAILED);
    assert!(refusal.message.contains("Operation timed out"), "{}", refusal.message);
    assert!(refusal.message.contains("`ssh studio true`"), "{}", refusal.message);
}

#[test]
fn a_binary_that_does_not_run_on_the_host_is_refused() {
    let setup = Setup::new(runs_reply);
    let processes = FakeProcesses::new(&[
        ("shasum", ok("")),
        ("yonderd setup", failed(126, "zsh: exec format error: .local/bin/yonderd\n")),
    ]);
    let agent = agent_file(&setup);
    let refusal = client(&setup, &processes, &|_| {}, &calm)
        .prepare(&request(&agent))
        .expect_err("another platform");
    assert_eq!(refusal.code, code::AGENT_FAILED);
    assert!(refusal.message.contains("exec format error"), "{}", refusal.message);
}

#[test]
fn the_digest_is_lower_case_hex() {
    assert_eq!(
        sha256_hex(b"abc"),
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    );
}
