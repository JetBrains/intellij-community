use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

/// A one-scenario suite whose instructions are `instructions`, stated by `vocabulary`.
fn program(vocabulary: &Value, instructions: &Value) -> SuiteProgram {
    let document = json!({
        "suite": "suite", "lane": "UI", "testClassName": "AirSuiteGeneratedFlowUiTest", "restartsIde": false,
        "labels": [{"name": "testClass", "value": "com.example.AirSuiteGeneratedFlowUiTest"}, {"name": "Subsystem", "value": "AI"}],
        "modules": [], "implementationFlows": [],
        "vocabulary": vocabulary,
        "profiles": [{"name": "scenario", "fixture": "fixture", "flow": "flow-one", "flags": {},
            "followsLastUsedPreset": false, "requiresPromptProbe": false, "setups": [],
            "steps": [{"flow": "flow-one", "step": "submit", "operations": [{"operation": "submit", "instructions": instructions}]}]}],
    });
    parse_suite_program(document.to_string().as_bytes()).unwrap()
}

fn spec(side: &str, params: &Value) -> Value {
    json!({"side": side, "timeoutMs": 180_000, "restarts": 0, "answers": [], "takes": [], "mayTake": [], "params": params})
}

#[test]
fn a_program_that_fits_its_vocabulary_has_no_refusal() {
    let surface =
        json!({"surface": {"type": "enum", "values": ["GLOBAL_PROMPT"], "optional": false}, "count": {"type": "int", "optional": true}});
    let vocabulary = json!({"runnerAnswers": ["terminalBaseline"], "actions": {
            "composer-type": spec("ide", &surface),
            "restart-ide": {"side": "host", "timeoutMs": 840_000, "restarts": 1, "answers": [], "takes": [], "mayTake": [], "params": {}},
        },
        "assertions": {"consent-download-fault-armed": spec("host", &json!({}))},
    });
    let instructions = json!([
        {"action": "composer-type", "args": {"surface": "GLOBAL_PROMPT", "count": 2}},
        {"action": "restart-ide"},
        {"assertion": "consent-download-fault-armed", "expectation": "The fault is armed."},
    ]);
    assert_eq!(program(&vocabulary, &instructions).refusals(), Vec::<String>::new());
}

#[test]
fn a_side_that_a_runner_does_not_know_is_refused() {
    let vocabulary = json!({"runnerAnswers": ["terminalBaseline"], "actions": {
            "open-prompt": spec("host", &json!({})),
            "restart-ide": spec("ide", &json!({})),
        },
        "assertions": {},
    });
    let instructions = json!([{"action": "open-prompt"}, {"action": "restart-ide"}]);
    assert_eq!(
        program(&vocabulary, &instructions).refusals(),
        vec![
            "the action 'open-prompt' is a host id, and the host vocabulary of a runner has no word for it".to_owned(),
            "the action 'restart-ide' is stated to run in the IDE, but a runner knows it as a host id".to_owned(),
        ]
    );
}

#[test]
fn an_argument_that_its_schema_refuses_is_refused() {
    let params =
        json!({"surface": {"type": "enum", "values": ["GLOBAL_PROMPT"], "optional": false}, "on": {"type": "boolean", "optional": true}});
    let vocabulary = json!({"runnerAnswers": ["terminalBaseline"], "actions": {"composer-type": spec("ide", &params)}, "assertions": {}});
    let instructions = json!([
        {"action": "composer-type", "args": {"surface": "INLINE_PROMPT"}},
        {"action": "composer-type", "args": {"surface": "GLOBAL_PROMPT", "on": 1}},
        {"action": "composer-type", "args": {"surface": "GLOBAL_PROMPT", "text": "EMPTY"}},
        {"action": "composer-type", "args": {"on": true}},
    ]);
    let place = "scenario 'scenario' operation 'submit'";
    assert_eq!(
        program(&vocabulary, &instructions).refusals(),
        vec![
            format!("{place}: the action 'composer-type' parameter 'surface' does not accept \"INLINE_PROMPT\""),
            format!("{place}: the action 'composer-type' parameter 'on' does not accept 1"),
            format!("{place}: the action 'composer-type' has no parameter 'text'"),
            format!("{place}: the action 'composer-type' needs the parameter 'surface'"),
        ]
    );
}

#[test]
fn an_id_that_the_vocabulary_leaves_out_or_never_runs_is_refused() {
    let vocabulary = json!({"runnerAnswers": ["terminalBaseline"], "actions": {"stale": spec("ide", &json!({}))},
        "assertions": {"restarted": {"side": "ide", "timeoutMs": 0, "restarts": 1, "answers": [], "takes": [], "mayTake": [], "params": {}}},
    });
    let instructions =
        json!([{"action": "missing"}, {"assertion": "restarted", "expectation": "It restarted."}, {"action": "a", "assertion": "b"}]);
    let place = "scenario 'scenario' operation 'submit'";
    assert_eq!(
        program(&vocabulary, &instructions).refusals(),
        vec![
            format!("{place} runs the action 'missing', which the vocabulary does not state"),
            format!("{place} has an instruction that is not exactly one action or one assertion"),
            "the vocabulary states the action 'stale', which no instruction runs".to_owned(),
            "the assertion 'restarted' has no deadline".to_owned(),
            "the assertion 'restarted' restarts the IDE, so it cannot run in the IDE alone".to_owned(),
        ]
    );
}

#[test]
fn an_id_spec_with_a_field_this_side_does_not_know_is_not_one() {
    let vocabulary = json!({"runnerAnswers": ["terminalBaseline"], "actions": {"send": {"side": "ide", "timeoutMs": 1, "restarts": 0, "answers": [], "takes": [], "mayTake": [], "params": {}, "more": 1}}, "assertions": {}});
    let document = json!({"suite": "s", "lane": "UI", "testClassName": "T", "labels": [], "vocabulary": vocabulary, "profiles": []});
    let error = parse_suite_program(document.to_string().as_bytes()).unwrap_err();
    assert!(error.contains("unknown field `more`"), "{error}");
}

#[test]
fn labels_that_name_another_class_are_refused() {
    let mut program = program(
        &json!({"runnerAnswers": ["terminalBaseline"], "actions": {}, "assertions": {}}),
        &json!([]),
    );
    program.labels = vec![SuiteLabel {
        name: "testClass".to_owned(),
        value: "com.example.AirOtherGeneratedFlowUiTest".to_owned(),
    }];
    assert_eq!(
        program.refusals(),
        vec![
            "the labels name the test class [SuiteLabel { name: \"testClass\", value: \"com.example.AirOtherGeneratedFlowUiTest\" }], not one class 'AirSuiteGeneratedFlowUiTest'"
                .to_owned()
        ]
    );
}

#[test]
fn a_value_that_no_earlier_instruction_answers_is_refused() {
    let seed = json!({"side": "ide", "timeoutMs": 1, "restarts": 0, "answers": ["path"], "takes": [], "mayTake": [], "params": {}});
    let select = json!({"side": "ide", "timeoutMs": 1, "restarts": 0, "answers": [], "takes": ["path", "terminalBaseline"], "mayTake": ["other"], "params": {}});
    let vocabulary = json!({"runnerAnswers": ["terminalBaseline"], "actions": {"seed": seed, "select": select}, "assertions": {}});
    let fits = json!([{"action": "seed"}, {"action": "select"}]);
    assert_eq!(program(&vocabulary, &fits).refusals(), Vec::<String>::new());
    let early = json!([{"action": "select"}, {"action": "seed"}]);
    assert_eq!(
        program(&vocabulary, &early).refusals(),
        vec!["scenario 'scenario' operation 'submit' takes the value 'path', which no earlier instruction answers".to_owned()]
    );
}
