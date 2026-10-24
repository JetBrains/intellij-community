use std::net::TcpListener;

use pretty_assertions::assert_eq;
use yonder_link::proto::Reply;

use super::*;
use crate::testkit::{ALIAS, FAST, Setup, host_file};

fn runs_reply(_: &Request, channel: &mut Channel) {
    let _ = channel.send(&Reply::Runs { runs: Vec::new() });
}

/// An address where nothing listens: a port that was free a moment ago.
fn dead_address() -> SocketAddr {
    let listener = TcpListener::bind("127.0.0.1:0").expect("a port");
    listener.local_addr().expect("the address")
}

#[test]
fn the_cached_address_answers_with_the_hello() {
    let setup = Setup::new(runs_reply);
    let mut host = Host::open(&setup.config, ALIAS, FAST).expect("the host");
    let (channel, info) = host.connect().expect("connected");
    channel.close();
    assert_eq!(
        info,
        AgentInfo {
            version: "0.1.0".to_owned(),
            os: "macos".to_owned(),
            arch: "aarch64".to_owned(),
            address: setup.agent.address,
        }
    );
}

/// A cached address that no longer answers costs one connect; the host name gives the new one, and the host file
/// keeps it first.
#[test]
fn a_dead_cached_address_falls_back_to_the_host_name_and_the_file_keeps_the_new_one() {
    let setup = Setup::new(runs_reply);
    let dead = dead_address();
    setup
        .config
        .write_host(&host_file(&setup.agent, vec![dead]))
        .expect("a stale host file");
    let mut host = Host::open(&setup.config, ALIAS, FAST).expect("the host");
    let (channel, info) = host.connect().expect("connected through the host name");
    channel.close();
    assert_eq!(info.address, setup.agent.address);
    let file = setup.config.read_host(ALIAS).expect("the host file");
    assert_eq!(file.addresses, [setup.agent.address, dead]);
}

#[test]
fn no_answer_names_each_address_the_host_name_and_the_prepare_commands() {
    let setup = Setup::new(runs_reply);
    let dead = dead_address();
    let mut file = host_file(&setup.agent, vec![dead]);
    file.port = dead.port();
    setup.config.write_host(&file).expect("a host file without an agent");
    let refusal = Host::open(&setup.config, ALIAS, FAST)
        .expect("the host")
        .connect()
        .expect_err("nothing answers");
    assert_eq!(refusal.code, code::UNREACHABLE);
    assert!(
        refusal
            .message
            .starts_with(&format!("no agent answers on studio: tried {dead} (no connection: ")),
        "{}",
        refusal.message
    );
    assert!(
        refusal.message.contains(&format!("127.0.0.1 resolved to {dead}")),
        "{}",
        refusal.message
    );
    assert!(refusal.message.contains("`bt --host studio --prepare`"), "{}", refusal.message);
}

#[test]
fn an_agent_with_another_certificate_is_untrusted() {
    let setup = Setup::new(runs_reply);
    let mut file = host_file(&setup.agent, vec![setup.agent.address]);
    file.fingerprint = Identity::generate().expect("another identity").fingerprint();
    setup.config.write_host(&file).expect("a host file with another pin");
    let refusal = Host::open(&setup.config, ALIAS, FAST)
        .expect("the host")
        .connect()
        .expect_err("another certificate");
    assert_eq!(refusal.code, code::UNTRUSTED);
}

#[test]
fn an_agent_that_does_not_allow_this_client_is_untrusted() {
    let setup = Setup::new(runs_reply);
    let stranger = Config::new(setup.dir.path().join("stranger"));
    stranger
        .write_host(&host_file(&setup.agent, vec![setup.agent.address]))
        .expect("a host file");
    let refusal = Host::open(&stranger, ALIAS, FAST)
        .expect("the host")
        .connect()
        .expect_err("a client of no allow list");
    assert_eq!(refusal.code, code::UNTRUSTED, "{}", refusal.message);
}

#[test]
fn an_agent_of_another_schema_is_refused_with_the_prepare_commands() {
    let setup = Setup::with_schema(SCHEMA_VERSION + 1, runs_reply);
    let refusal = Host::open(&setup.config, ALIAS, FAST)
        .expect("the host")
        .connect()
        .expect_err("another schema");
    assert_eq!(refusal.code, code::SCHEMA);
    assert!(refusal.message.contains("speaks schema 2"), "{}", refusal.message);
    assert!(refusal.message.contains("--prepare"), "{}", refusal.message);
}

#[test]
fn a_lookup_answers_ipv4_first_and_each_address_once() {
    let found = resolve("127.0.0.1", 7741, Duration::from_secs(2)).expect("a literal resolves");
    assert_eq!(found, ["127.0.0.1:7741".parse::<SocketAddr>().expect("an address")]);
    let said = resolve("no-such-host.invalid", 7741, Duration::from_secs(5)).expect_err("no such host");
    assert!(!said.is_empty());
}
