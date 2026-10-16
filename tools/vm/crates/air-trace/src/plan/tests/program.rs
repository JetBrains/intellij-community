//! Every committed suite document against the vocabulary a lane runner reads it by.

use avl_wire::program::{Side, parse_suite_program};

use super::fixture::documents_or_skip;

// A host id with no word of the runner's host vocabulary, a crossing id off the pinned list, or an argument its
// schema refuses would fail a run only when its instruction comes. This refuses it before any IDE starts.
#[test]
fn every_suite_document_fits_the_vocabulary_of_a_lane_runner() {
    let documents = documents_or_skip!();
    let mut refusals = Vec::new();
    let mut host_ids = 0;
    for (name, content) in &documents {
        match parse_suite_program(content) {
            Ok(program) => {
                refusals.extend(program.refusals().into_iter().map(|refusal| format!("{name}: {refusal}")));
                let specs = program.vocabulary.actions.values().chain(program.vocabulary.assertions.values());
                host_ids += specs.filter(|spec| spec.side == Side::Host).count();
            }
            Err(error) => refusals.push(format!("{name} {error}")),
        }
    }
    assert!(
        refusals.is_empty(),
        "the suite documents do not fit the vocabulary of a lane runner:\n{}",
        refusals.join("\n")
    );
    assert!(
        host_ids > 0,
        "no committed suite document runs a host id, so the host vocabulary is checked against nothing"
    );
}
