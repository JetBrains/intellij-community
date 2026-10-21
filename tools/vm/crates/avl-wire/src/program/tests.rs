use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

/// A one-scenario suite whose instructions are `instructions`, stated by `vocabulary`.
fn program(vocabulary: &Value, instructions: &Value) -> SuiteProgram {
    let document = json!({
        "schemaVersion": 2, "suite": "suite", "lane": "UI", "testClassName": "AirSuiteGeneratedFlowUiTest", "restartsIde": false,
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

#[test]
fn a_value_that_an_earlier_instruction_may_answer_is_answered() {
    let submit = json!({"side": "ide", "timeoutMs": 1, "restarts": 0, "answers": [], "mayAnswer": ["draft"], "takes": [], "mayTake": [], "params": {}});
    let kept = json!({"side": "ide", "timeoutMs": 1, "restarts": 0, "answers": [], "takes": ["draft"], "mayTake": [], "params": {}});
    let vocabulary = json!({"runnerAnswers": [], "actions": {"submit": submit}, "assertions": {"kept": kept}});
    let fits = json!([{"action": "submit"}, {"assertion": "kept", "expectation": "The draft tab is kept."}]);
    assert_eq!(program(&vocabulary, &fits).refusals(), Vec::<String>::new());
    let early = json!([{"assertion": "kept", "expectation": "The draft tab is kept."}, {"action": "submit"}]);
    assert_eq!(
        program(&vocabulary, &early).refusals(),
        vec!["scenario 'scenario' operation 'submit' takes the value 'draft', which no earlier instruction answers".to_owned()]
    );
}

/// A one-scenario authored suite whose program is `instructions`, with the one id `authored-step`.
fn authored(instructions: &Value) -> SuiteProgram {
    let key = |values: &[&str]| json!({"type": "enum", "values": values, "optional": false});
    let params = json!({"part": key(&["seed", "use"]), "scenario": key(&["scenario"]), "suite": key(&["authored"]),
        "timeoutMs": {"type": "int", "optional": false}});
    let document = json!({
        "schemaVersion": 2, "suite": "authored", "lane": "UI", "testClassName": "AgentAuthoredUiTest", "restartsIde": false,
        "authored": true,
        "labels": [{"name": "testClass", "value": "com.example.AgentAuthoredUiTest"}],
        "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {"authored-step": spec("ide", &params)}, "assertions": {}},
        "profiles": [{"name": "scenario", "fixture": "authored-authored", "flags": {}, "followsLastUsedPreset": false,
            "requiresPromptProbe": false, "setups": [], "steps": [], "program": instructions}],
    });
    parse_suite_program(document.to_string().as_bytes()).unwrap()
}

fn part(name: &str, takes: &[&str], answers: &[&str]) -> Value {
    json!({"action": "authored-step", "args": {"part": name, "scenario": "scenario", "suite": "authored", "timeoutMs": 1000},
        "uses": {"takes": takes, "answers": answers}})
}

#[test]
fn a_part_takes_a_value_that_an_earlier_part_answers() {
    let fits = json!([part("seed", &[], &["session"]), part("use", &["session"], &[])]);
    assert_eq!(authored(&fits).refusals(), Vec::<String>::new());
    let early = json!([part("use", &["session"], &[]), part("seed", &[], &["session"])]);
    assert_eq!(
        authored(&early).refusals(),
        vec!["scenario 'scenario' program takes the value 'session', which no earlier instruction answers".to_owned()]
    );
}

// A scenario may require only a value that the runner answers, as the runnerAnswers of its vocabulary list them.
#[test]
fn a_required_runner_value_is_one_of_the_runner_answers() {
    let mut program = authored(&json!([part("seed", &[], &[])]));
    program.profiles[0].requires_runner_values = vec!["realSessionLogs".to_owned()];
    assert_eq!(
        program.refusals(),
        vec![
            "scenario 'scenario' requires the runner value 'realSessionLogs', which the runnerAnswers of the vocabulary do not list"
                .to_owned()
        ]
    );
    program.vocabulary.runner_answers.push("realSessionLogs".to_owned());
    assert_eq!(program.refusals(), Vec::<String>::new());
}

#[test]
fn a_part_that_names_no_part_of_its_suite_is_refused() {
    let program = json!([part("other", &[], &[])]);
    assert_eq!(
        authored(&program).refusals(),
        vec!["scenario 'scenario' program: the action 'authored-step' parameter 'part' does not accept \"other\"".to_owned()]
    );
}

/// The suite document `startup-class-loading` of a bare launch: one authored program of one IDE id, and `launch`. A
/// `launch` of null leaves the field out.
fn bare(authored: bool, launch: &Value, actions: &Value) -> Result<SuiteProgram, String> {
    let mut document = json!({
        "schemaVersion": 2, "suite": "startup-class-loading", "lane": "UI", "testClassName": "AirStartupClassLoadingBudgetUiTest",
        "authored": authored, "restartsIde": false,
        "labels": [{"name": "testClass", "value": "com.example.AirStartupClassLoadingBudgetUiTest"}],
        "modules": [], "implementationFlows": [], "launch": launch,
        "vocabulary": {"runnerAnswers": [], "actions": actions, "assertions": {}},
        "profiles": [{"name": "Air loads no more classes than its budget while a project opens", "fixture": "bare",
            "flags": {}, "followsLastUsedPreset": false, "requiresPromptProbe": false, "setups": [], "steps": [],
            "program": [{"action": "startup-class-loading-within-budget"}]}],
    });
    if launch.is_null() {
        document.as_object_mut().unwrap().remove("launch");
    }
    parse_suite_program(document.to_string().as_bytes())
}

// A suite of a bare IDE states its profile in `launch`. The profile is a word of the runner, and the section has no
// other field, so a document of a later profile fails to parse. A document without `launch` runs on the shared IDE.
#[test]
fn a_bare_launch_is_a_profile_word_and_a_section_with_no_other_field() {
    let actions = json!({"startup-class-loading-within-budget": spec("ide", &json!({}))});
    let program = bare(true, &json!({"profile": "bare", "key": "air-startup-class-loading"}), &actions).unwrap();
    assert_eq!(
        program.launch,
        Some(SuiteLaunch {
            profile: LaunchProfile::Bare,
            key: "air-startup-class-loading".to_owned(),
        })
    );
    assert_eq!(program.refusals(), Vec::<String>::new());
    let unknown = bare(true, &json!({"profile": "warm", "key": "air-startup-class-loading"}), &actions).unwrap_err();
    assert!(unknown.contains("unknown variant `warm`, expected `bare`"), "{unknown}");
    let extra = bare(
        true,
        &json!({"profile": "bare", "key": "air-startup-class-loading", "port": 1}),
        &actions,
    )
    .unwrap_err();
    assert!(extra.contains("unknown field `port`"), "{extra}");
    assert_eq!(bare(true, &Value::Null, &actions).unwrap().launch, None);
}

// A bare IDE runs an authored program, and no host id: no restart, no recycle, no lane service.
#[test]
fn a_bare_launch_of_a_story_suite_or_of_a_host_id_is_refused() {
    let actions = json!({"startup-class-loading-within-budget": spec("ide", &json!({}))});
    assert_eq!(
        bare(false, &json!({"profile": "bare", "key": "air-startup-class-loading"}), &actions)
            .unwrap()
            .refusals(),
        vec!["the suite launches a bare IDE, which runs an authored program and no story scenario".to_owned()]
    );
    let with_restart = json!({
        "startup-class-loading-within-budget": spec("ide", &json!({})),
        "restart-ide": {"side": "host", "timeoutMs": 840_000, "restarts": 1, "answers": [], "takes": [], "mayTake": [], "params": {}},
    });
    let refusals = bare(true, &json!({"profile": "bare", "key": "air-startup-class-loading"}), &with_restart)
        .unwrap()
        .refusals();
    assert!(
        refusals.contains(&"the suite launches a bare IDE, which runs no host id, and the vocabulary states 'restart-ide'".to_owned()),
        "{refusals:?}"
    );
}

// A suite document states the schema version of this reader. A document of another version is refused, so a runner
// never reads a field that it does not know as an absent one.
#[test]
fn a_document_of_another_schema_version_is_refused() {
    let actions = json!({"startup-class-loading-within-budget": spec("ide", &json!({}))});
    let mut document: Value = serde_json::from_str(&format!(
        r#"{{"schemaVersion": 1, "suite": "s", "lane": "UI", "testClassName": "AirSUiTest", "labels": [],
            "vocabulary": {{"runnerAnswers": [], "actions": {actions}, "assertions": {{}}}}, "profiles": []}}"#
    ))
    .unwrap();
    assert_eq!(
        parse_suite_program(document.to_string().as_bytes()).unwrap_err(),
        "has the schema version 1, and this reader reads 2"
    );
    document.as_object_mut().unwrap().remove("schemaVersion");
    let missing = parse_suite_program(document.to_string().as_bytes()).unwrap_err();
    assert!(missing.contains("missing field `schemaVersion`"), "{missing}");
}

// The launch key names the context of the bare IDE, a directory below the IDE root, so it is a plain name.
#[test]
fn a_launch_key_that_is_no_plain_name_is_refused() {
    let actions = json!({"startup-class-loading-within-budget": spec("ide", &json!({}))});
    let program = bare(true, &json!({"profile": "bare", "key": "../lane"}), &actions).unwrap();
    assert_eq!(
        program.refusals(),
        vec!["the launch key '../lane' is no name of lowercase letters, digits and dashes".to_owned()]
    );
}

// A suite states the bridge request that installs its managed agents once, and a runner reads it verbatim. A bare IDE
// installs nothing, so a bare suite that states an install is refused.
#[test]
fn the_managed_agent_install_is_read_verbatim_and_a_bare_suite_states_none() {
    let install = r#"{"type":"com.intellij.air.integration.bridge.InstallManagedAgents","agents":[{"displayName":"Codex","fullAgentId":"acp.registry.codex-acp"}],"timeoutMs":300000}"#;
    let document = |extra: &str| {
        format!(
            r#"{{"schemaVersion": 2, "suite": "s", "lane": "UI", "testClassName": "AirSUiTest",
                "labels": [{{"name": "testClass", "value": "com.example.AirSUiTest"}}],
                "vocabulary": {{"runnerAnswers": [], "actions": {{}}, "assertions": {{}}}}, "profiles": [],
                {extra}"managedAgentInstall": {install}}}"#
        )
    };
    let program = parse_suite_program(document("").as_bytes()).unwrap();
    assert_eq!(program.managed_agent_install.as_deref().map(RawValue::get), Some(install));
    assert_eq!(program.refusals(), Vec::<String>::new());
    let bare = parse_suite_program(
        document(r#""authored": true, "launch": {"profile": "bare", "key": "air-startup-class-loading"}, "#).as_bytes(),
    )
    .unwrap();
    assert_eq!(
        bare.refusals(),
        vec!["the suite launches a bare IDE, which installs no managed agent, and it states managedAgentInstall".to_owned()]
    );
}
