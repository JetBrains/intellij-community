use std::collections::HashSet;

use avl_wire::verb::AgentVerb;
use pretty_assertions::assert_eq;

use super::verb_refusal_code;

/// The refusal code of a verb is `guest_<verb>_failed`, with an underscore for each dash of the verb. So each verb has
/// a code of its own.
#[test]
fn the_refusal_code_of_a_verb_names_the_verb() {
    for (verb, code) in [
        (AgentVerb::Contract, "guest_contract_failed"),
        (AgentVerb::IdeGc, "guest_ide_gc_failed"),
        (AgentVerb::RunfilesTree, "guest_runfiles_tree_failed"),
        (AgentVerb::TracePackReady, "guest_trace_pack_ready_failed"),
    ] {
        assert_eq!(verb_refusal_code(verb), code);
    }
    let codes: HashSet<String> = AgentVerb::ALL.iter().map(|verb| verb_refusal_code(*verb)).collect();
    assert_eq!(codes.len(), AgentVerb::ALL.len(), "two verbs share a refusal code");
}
