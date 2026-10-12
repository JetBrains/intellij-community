use std::collections::HashSet;

use pretty_assertions::assert_eq;

use super::*;
use crate::supervisor::Command;

/// The spellings the installed agents and Packer already send. A rename here is a wire change, so this list is
/// the oracle rather than a copy of the declaration.
#[test]
fn every_verb_keeps_its_wire_spelling() {
    let spelled: Vec<&str> = AgentVerb::ALL.iter().map(|verb| verb.as_str()).collect();
    assert_eq!(
        spelled,
        [
            "start",
            "supervise",
            "status",
            "active",
            "log",
            "cancel",
            "stage",
            "stage-check",
            "launch-prep",
            "gc",
            "provision-image",
            "validate-image",
            "validate-guest",
            "trace-pack-ready",
            "contract",
            "relay",
            "runfiles-tree",
            "read-file",
        ]
    );
    for verb in AgentVerb::ALL {
        assert_eq!(verb.as_str().parse::<AgentVerb>(), Ok(*verb));
    }
}

/// The supervisor's verbs are agent verbs with no spelling of their own.
#[test]
fn every_supervisor_command_is_a_distinct_agent_verb() {
    let verbs: HashSet<AgentVerb> = Command::ALL.iter().map(|command| command.verb()).collect();
    assert_eq!(verbs.len(), Command::ALL.len());
    for command in Command::ALL {
        assert_eq!(command.as_str(), command.verb().as_str());
    }
}
