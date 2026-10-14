use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

/// A lane document of one local deterministic agent, one controlled registry agent and one journey suite.
fn document() -> Value {
    json!({
        "schemaVersion": 1, "lane": "UI", "vocabularyDigest": "digest",
        "launchProperties": [
            {"key": "idea.is.internal", "value": "true"},
            {"key": "jdk.gtk.version", "value": "2", "when": "linux"},
            {"key": "ide.enable.notification.trace.data.sharing", "value": "false", "when": "notTeamCity"},
        ],
        "requirements": [{"name": "WORKFLOWS", "properties": {"air.workflows": "true"}}, {"name": "FEEDBACK_CAPTURE", "properties": {}}],
        "agents": [
            {"key": "run", "delivery": "local", "configName": "air-scenario-run", "displayName": "air-scenario-run", "controlled": false,
             "deterministic": {"responsePrefix": "Done", "responseDelayMs": 0, "asksPermission": false, "loadsSessions": false},
             "acpJson": {"command": "${java}", "args": ["-jar", "${fakeAgentJar}"], "env": {"ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX": "Done"}}},
            {"key": "steer", "delivery": "registry", "registryId": "air-scenario-steer", "displayName": "Air Controlled steer",
             "controlled": true, "controlOwner": "IDE", "env": {}},
        ],
        "registryAgents": ["steer"],
        "disabledPluginIds": ["com.intellij.ml.llm"],
        "cliRuntimes": ["codex", "pi"],
        "services": {"jetBrainsCloud": "jba-jcp-emulator-1"},
        "journeySuites": [{"suite": "journey", "agents": ["run", "steer"], "requirements": ["WORKFLOWS"]}],
    })
}

fn parse(value: &Value) -> Result<LaneDocument, String> {
    parse_lane_document(value.to_string().as_bytes())
}

#[test]
fn a_document_that_fits_itself_is_read_with_every_field() {
    let document = parse(&document()).unwrap();
    assert_eq!(document.lane, "UI");
    assert_eq!(
        document.agents[0].acp_json.as_ref().unwrap().env["ACP_FAKE_AGENT_STANDALONE_RESPONSE_PREFIX"],
        "Done"
    );
    assert_eq!(document.agents[1].delivery, Delivery::Registry);
    assert_eq!(document.agents[1].control_owner, Some(ControlOwner::Ide));
    assert_eq!(document.agents[0].control_owner, None);
    assert_eq!(document.journey_suites[0].requirements, vec![Requirement::Workflows]);
}

#[test]
fn the_conditions_of_the_launch_properties_read_the_host() {
    let document = parse(&document()).unwrap();
    let on_mac = document.launch_properties_on(LaunchHost {
        linux: false,
        windows: false,
        team_city: false,
    });
    assert_eq!(
        on_mac,
        vec![
            ("idea.is.internal", "true"),
            ("ide.enable.notification.trace.data.sharing", "false"),
            ("air.workflows", "true")
        ]
    );
    let on_team_city_linux = document.launch_properties_on(LaunchHost {
        linux: true,
        windows: false,
        team_city: true,
    });
    assert_eq!(
        on_team_city_linux,
        vec![("idea.is.internal", "true"), ("jdk.gtk.version", "2"), ("air.workflows", "true")]
    );
}

#[test]
fn an_unknown_field_and_another_schema_version_are_refused() {
    let mut unknown = document();
    unknown["launchInputs"] = json!([]);
    assert!(parse(&unknown).unwrap_err().contains("unknown field `launchInputs`"));
    let mut newer = document();
    newer["schemaVersion"] = json!(2);
    assert_eq!(parse(&newer).unwrap_err(), "has the schema version 2, and this reader reads 1");
}

#[test]
fn a_control_owner_is_stated_exactly_for_a_controlled_agent() {
    let mut missing = document();
    missing["agents"][1].as_object_mut().unwrap().remove("controlOwner");
    assert_eq!(
        parse(&missing).unwrap_err(),
        "the agent 'steer' must state a control owner exactly when it is controlled"
    );
    let mut deterministic = document();
    deterministic["agents"][0]["controlOwner"] = json!("HOST");
    assert_eq!(
        parse(&deterministic).unwrap_err(),
        "the agent 'run' must state a control owner exactly when it is controlled"
    );
    let mut unknown = document();
    unknown["agents"][1]["controlOwner"] = json!("GUEST");
    assert!(parse(&unknown).unwrap_err().contains("GUEST"));
}

#[test]
fn a_document_that_does_not_fit_itself_names_each_mismatch() {
    let mut broken = document();
    broken["registryAgents"] = json!([]);
    broken["agents"][1]["deterministic"] =
        json!({"responsePrefix": "x", "responseDelayMs": 0, "asksPermission": false, "loadsSessions": false});
    broken["agents"][0]["acpJson"]["args"] = json!(["-cp", "classes"]);
    broken["journeySuites"][0]["agents"] = json!(["gone"]);
    assert_eq!(
        parse(&broken).unwrap_err().split("; ").collect::<Vec<_>>(),
        vec![
            "the acp.json entry of 'run' runs ${java} [\"-cp\", \"classes\"], not ${java} -jar ${fakeAgentJar}",
            "the agent 'steer' must state an answer exactly when it is not controlled",
            "the registry agents [] are not the registry agents [\"steer\"] of the agents",
            "the journey suite 'journey' names the agent 'gone', which the lane does not state",
        ]
    );
}
