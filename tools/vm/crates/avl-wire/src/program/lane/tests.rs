use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

/// A lane document of one local deterministic agent, one controlled registry agent and one journey suite.
fn document() -> Value {
    json!({
        "schemaVersion": 2, "lane": "UI",
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
    })
}

fn parse(value: &Value) -> Result<LaneDocument, String> {
    parse_lane_document(value.to_string().as_bytes())
}

/// The fixture document as the lane `UI_LIVE`, with its live context.
fn live_document() -> Value {
    let mut document = document();
    document["lane"] = json!(LIVE_LANE);
    document["live"] = json!({
        "loginInput": "AIR_LIVE_CENTRAL_LOGIN", "loginSource": "central login export", "centralRuntime": "central",
        "centralPort": 19516, "centralAgents": ["codex", "claude-code", "junie"],
        "runtimes": [{"name": "claude", "environment": {"DISABLE_AUTOUPDATER": "1"}}, {"name": "junie", "environment": {}}],
        "projectUnpackRoot": "/tmp/air-ui-live/project",
    });
    document
}

// The live lane states its live context, and the context names its inputs by name: no field holds a credential.
#[test]
fn the_live_lane_reads_its_live_context() {
    let live = parse(&live_document()).unwrap().live.expect("a live context");
    assert_eq!(live.login_input, "AIR_LIVE_CENTRAL_LOGIN");
    assert_eq!(live.central_port, 19516);
    assert_eq!(live.central_agents, ["codex", "claude-code", "junie"]);
    assert_eq!(live.runtimes[0].environment["DISABLE_AUTOUPDATER"], "1");
    assert_eq!(parse(&document()).unwrap().live, None);
}

// A live context belongs to the live lane alone, and the live lane cannot launch without one.
#[test]
fn a_live_context_fits_the_live_lane_only() {
    let mut other = live_document();
    other["lane"] = json!("UI");
    assert_eq!(
        parse(&other).unwrap_err(),
        "the lane UI states a live context, which only UI_LIVE has"
    );
    let mut missing = live_document();
    missing.as_object_mut().unwrap().remove("live");
    assert_eq!(parse(&missing).unwrap_err(), "the lane UI_LIVE states no live context");
}

// The launcher of the Central runtime is the guard, so a live runtime that is a declared CLI runtime too would give the
// lane IDE a second launcher of the real CLI. A login input is a file name in the run secrets directory.
#[test]
fn a_live_context_that_does_not_fit_its_lane_is_refused() {
    let mut document = live_document();
    document["live"]["centralRuntime"] = json!("codex");
    document["live"]["runtimes"][1]["name"] = json!("claude");
    document["live"]["loginInput"] = json!("../login");
    document["live"]["projectUnpackRoot"] = json!("relative/project");
    assert_eq!(
        parse(&document).unwrap_err(),
        [
            "the live login input '../login' is not a file name",
            "the live runtime 'codex' is a declared CLI runtime too, so the lane would get two launchers of it",
            "the live context names the runtime 'claude' twice",
            "the live project unpack root 'relative/project' is not an absolute slash path",
        ]
        .join("; ")
    );
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
    assert_eq!(
        document.stated_launch_properties_on(LaunchHost {
            linux: true,
            windows: false,
            team_city: true,
        }),
        vec![("idea.is.internal", "true"), ("jdk.gtk.version", "2")],
        "the stated properties leave the properties of the requirements out"
    );
}

#[test]
fn an_unknown_field_and_another_schema_version_are_refused() {
    let mut unknown = document();
    unknown["launchInputs"] = json!([]);
    assert!(parse(&unknown).unwrap_err().contains("unknown field `launchInputs`"));
    let mut newer = document();
    newer["schemaVersion"] = json!(3);
    assert_eq!(parse(&newer).unwrap_err(), "has the schema version 3, and this reader reads 2");
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
    deterministic["agents"][0]["controlOwner"] = json!("IDE");
    assert_eq!(
        parse(&deterministic).unwrap_err(),
        "the agent 'run' must state a control owner exactly when it is controlled"
    );
    let mut unknown = document();
    unknown["agents"][1]["controlOwner"] = json!("GUEST");
    assert!(parse(&unknown).unwrap_err().contains("GUEST"));
}

// The control server in the IDE answers the list of a controlled agent. A deterministic agent has no such answer.
#[test]
fn only_a_controlled_agent_lists_sessions() {
    assert!(!parse(&document()).unwrap().agents[1].lists_sessions);
    let mut listing = document();
    listing["agents"][1]["listsSessions"] = json!(true);
    assert!(parse(&listing).unwrap().agents[1].lists_sessions);
    let mut deterministic = document();
    deterministic["agents"][0]["listsSessions"] = json!(true);
    assert_eq!(
        parse(&deterministic).unwrap_err(),
        "the agent 'run' lists sessions, and only a controlled agent lists sessions"
    );
}

#[test]
fn a_document_that_does_not_fit_itself_names_each_mismatch() {
    let mut broken = document();
    broken["registryAgents"] = json!([]);
    broken["agents"][1]["deterministic"] =
        json!({"responsePrefix": "x", "responseDelayMs": 0, "asksPermission": false, "loadsSessions": false});
    broken["agents"][0]["acpJson"]["args"] = json!(["-cp", "classes"]);
    assert_eq!(
        parse(&broken).unwrap_err().split("; ").collect::<Vec<_>>(),
        vec![
            "the acp.json entry of 'run' runs ${java} [\"-cp\", \"classes\"], not ${java} -jar ${fakeAgentJar}",
            "the agent 'steer' must state an answer exactly when it is not controlled",
            "the registry agents [] are not the registry agents [\"steer\"] of the agents",
        ]
    );
}

/// One performance series in the shape the generator writes it.
fn series(class: &str, scenario: &str, artifact_path: &str) -> Value {
    json!({
        "testClassName": class, "scenario": scenario, "project": format!("air/{artifact_path}"),
        "method": format!("com.intellij.air.{class}#run"), "artifactPath": artifact_path,
    })
}

#[test]
fn a_lane_with_no_performance_series_leaves_them_out_and_a_series_is_read_with_every_field() {
    assert_eq!(parse(&document()).unwrap().performance_series, Vec::new());
    let mut perf = document();
    perf["performanceSeries"] = json!([series("AirManyAgentsUiTest", "many agents", "air-many-agents")]);
    assert_eq!(
        parse(&perf).unwrap().performance_series,
        vec![PerformanceSeries {
            test_class_name: "AirManyAgentsUiTest".to_owned(),
            scenario: "many agents".to_owned(),
            project: "air/air-many-agents".to_owned(),
            method: "com.intellij.air.AirManyAgentsUiTest#run".to_owned(),
            artifact_path: "air-many-agents".to_owned(),
        }]
    );
    perf["performanceSeries"][0]["owner"] = json!("someone");
    assert!(parse(&perf).unwrap_err().contains("unknown field `owner`"));
}

#[test]
fn a_performance_series_that_would_collide_or_leave_its_directory_is_refused() {
    let mut broken = document();
    let mut nameless = series("AirLongSessionUiTest", "nameless", "air-nameless");
    nameless["project"] = json!("");
    broken["performanceSeries"] = json!([
        series("AirLongSessionUiTest", "small", "air-long-session/SMALL"),
        series("AirLongSessionUiTest", "small", "air-long-session/LARGE"),
        series("AirLongSessionUiTest", "large", "air-long-session/LARGE"),
        series("AirLongSessionUiTest", "escape", "../outside"),
        series("AirLongSessionUiTest", "rooted", "/rooted"),
        nameless,
    ]);
    assert_eq!(
        parse(&broken).unwrap_err().split("; ").collect::<Vec<_>>(),
        vec![
            "the scenario AirLongSessionUiTest#small has two performance series",
            "the artifact path 'air-long-session/LARGE' is the path of two performance series",
            "the performance series of AirLongSessionUiTest#escape has the artifact path '../outside', which is not a relative slash path",
            "the performance series of AirLongSessionUiTest#rooted has the artifact path '/rooted', which is not a relative slash path",
            "the performance series of AirLongSessionUiTest#nameless names no project or no method",
        ]
    );
}
