use std::collections::BTreeSet;
use std::path::Path;

use avl_testkit::traces::{example_bundle, example_file};
use avl_trace::bundle::{BundleStatus, MANIFEST_FILE, Manifest, ROOT_DIR_NAME, SPANS_FILE, bundle_path, decode_manifest};
use avl_trace_tools::discover::{SourceKind, Status};
use avl_wire::report::{
    self, ExecutionCounts, Failure, FailureKind, FailureSource, Integrity, ORDERING, RUN_REPORT_SCHEMA_VERSION, Retrieval, RunReport,
    TraceArchive, Watchdog, XmlCounts,
};
use bt_core::ResolutionInputs;
use bt_core::catalog::SuiteDocument;
use clap::Parser;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::cli::run_plan;
use super::commands::{BILLED_WARNING, HOST_WARNING, render, vm_run};
use super::names::git_list_files;
use super::*;

mod fixture;

use fixture::{Repo, documents_or_skip, fixture_repo, lane_word, run_git, suite_documents, zip_of};

/// The Air area of the fixture, which this suite installs before its first use.
fn air_area() -> &'static bt_core::Area {
    avl_affected::bridge::install_fixture();
    avl_affected::air_area()
}

/// The areas of the fixture, which this suite installs before its first use.
fn air_areas() -> &'static bt_core::Areas {
    avl_affected::bridge::install_fixture();
    avl_affected::air_areas()
}

fn plan(repo: &Path, inputs: Vec<Input>) -> PlanResult {
    avl_affected::bridge::install_fixture();
    resolve_all(repo, inputs).unwrap_or_else(|error| panic!("the planner refused: {error:#}"))
}

fn text(value: &str) -> Input {
    Input {
        text: value.to_owned(),
        ..Input::default()
    }
}

fn dropped(name: &str, content: &[u8]) -> Input {
    Input {
        file_name: name.to_owned(),
        content: content.to_vec(),
        ..Input::default()
    }
}

/// Plans with a file lookup of the test's own instead of `git ls-files`.
fn plan_listing(repo: &Path, list_files: impl Fn(&Path, &str) -> Result<Vec<String>, Refusal> + 'static, inputs: Vec<Input>) -> PlanResult {
    let notes = BtNotes::default();
    let runtime = bt_runtime(repo, notes.clone());
    let selectors = ResolutionInputs::new(&runtime, air_areas());
    Planner::new(repo, &runtime, &selectors, notes, Box::new(list_files))
        .plan(inputs)
        .unwrap_or_else(|error| panic!("the planner refused: {error:#}"))
}

/// The planned scenarios as `class/scenario@lane`, sorted, which is what the expectations are computed as.
fn scenario_keys(result: &PlanResult) -> Vec<String> {
    let mut keys: Vec<String> = result
        .scenarios
        .iter()
        .map(|scenario| format!("{}/{}@{}", scenario.test_class, scenario.scenario, scenario.lane))
        .collect();
    keys.sort();
    keys
}

fn keys_of(document: &SuiteDocument, include: impl Fn(&str, &str, &[String]) -> bool) -> Vec<String> {
    document
        .profiles
        .iter()
        .filter(|declared| {
            let step_flows: Vec<String> = declared.steps.iter().map(|step| step.flow.clone()).collect();
            include(&declared.name, &declared.flow, &step_flows)
        })
        .map(|declared| format!("{}/{}@{}", document.test_class_name, declared.name, lane_word(document)))
        .collect()
}

fn every_key(document: &SuiteDocument) -> Vec<String> {
    let mut keys = keys_of(document, |_, _, _| true);
    keys.sort();
    keys
}

fn command_of<'r>(result: &'r PlanResult, kind: CommandKind, lane: &str) -> &'r Command {
    result
        .commands
        .iter()
        .find(|command| command.kind == kind && command.title.contains(&format!("the {lane} lane")))
        .unwrap_or_else(|| panic!("no {kind:?} command for the {lane} lane in {:?}", result.commands))
}

fn widest(documents: &[SuiteDocument]) -> Option<SuiteDocument> {
    let widest = documents.iter().max_by_key(|document| document.profiles.len())?;
    if widest.profiles.len() < 2 {
        println!("skipped: no committed suite has two scenarios");
        return None;
    }
    Some(widest.clone())
}

/// Every flow a committed scenario tells or walks, and every implementation flow.
fn flows_of_documents(documents: &[SuiteDocument]) -> Vec<String> {
    let mut flows = BTreeSet::new();
    for document in documents {
        flows.extend(document.implementation_flows.iter().cloned());
        for declared in &document.profiles {
            flows.insert(declared.flow.clone());
            flows.extend(declared.steps.iter().map(|step| step.flow.clone()));
        }
    }
    flows.remove("");
    flows.into_iter().collect()
}

fn notes_of(result: &PlanResult) -> String {
    result.notes.join("\n")
}

// --- names ---------------------------------------------------------------------------------------------------

/// A generated class plans every scenario of its suite, in its own lane, with the VM sequence and the host run.
/// Asked of every committed suite, so a document the reader misreads is found by name.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn every_committed_class_plans_its_suites_scenarios() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    for document in suite_documents(&documents) {
        let suite = &document.suite;
        let result = plan(&repo.root, vec![text(&document.test_class_name)]);
        let want = every_key(&document);
        assert_eq!(scenario_keys(&result), want, "{suite}: the class planned the wrong scenarios");
        let lane = lane_word(&document);
        assert_eq!(result.lanes, [lane], "{suite}");
        assert_eq!(result.classes, vec![document.test_class_name.clone()], "{suite}");
        let vm = command_of(&result, CommandKind::Vm, lane);
        assert_eq!(vm.lines, vec![vm_run(lane, &[&document.test_class_name])], "{suite}");
        let host = command_of(&result, CommandKind::Host, lane);
        assert_eq!(
            host.lines,
            vec![format!("./community/tools/bt.cmd {}", document.test_class_name)],
            "{suite}"
        );
        assert_eq!(host.warning.as_deref(), Some(HOST_WARNING), "{suite}");
        assert!(HOST_WARNING.contains("takes your screen"));
        // The fully qualified spelling is the same class.
        let qualified = plan(
            &repo.root,
            vec![text(&format!("com.intellij.air.tests.{}", document.test_class_name))],
        );
        assert_eq!(scenario_keys(&qualified), want, "{suite}: the FQN planned the wrong scenarios");
    }
}

/// A VM line is one self-leased run: `vm.cmd run` without a receipt warms the analysis, takes a lease and releases
/// it, so the plan spells no lease script. A GUI-chat line names the Parallels guest, because that lane belongs on
/// the guest the user chose, and every other lane runs on the default Linux pool.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_vm_line_is_one_self_leased_run_on_the_lanes_guest() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let mut seen = BTreeSet::new();
    for document in suite_documents(&documents) {
        let lane = lane_word(&document);
        if !seen.insert(lane) {
            continue;
        }
        let result = plan(&repo.root, vec![text(&document.test_class_name)]);
        let vm = command_of(&result, CommandKind::Vm, lane);
        let want = if lane == "gui-chat" {
            format!("./community/tools/vm.cmd --backend parallels run {}", document.test_class_name)
        } else {
            format!("./community/tools/vm.cmd run {}", document.test_class_name)
        };
        assert_eq!(vm.lines, vec![want], "the {lane} lane's VM line");
        for line in &vm.lines {
            for script in ["lease acquire", "lease release", "--lease-file", "daemon warm", "jq"] {
                assert!(!line.contains(script), "a self-leased line spells {script:?}: {line}");
            }
        }
    }
    assert!(
        seen.len() >= 2,
        "the committed suites cover {} lane(s); the case needs a GUI-chat lane beside another",
        seen.len()
    );
}

/// Plans classes by lane with no catalog, the way a resolved class name adds one, and answers the plan.
fn plan_classes(classes: &[(&str, &str)]) -> PlanResult {
    let repo = tempfile::TempDir::new().expect("a temporary checkout");
    let notes = BtNotes::default();
    let runtime = bt_runtime(repo.path(), notes.clone());
    let selectors = ResolutionInputs::new(&runtime, air_areas());
    let mut planner = Planner::new(
        repo.path(),
        &runtime,
        &selectors,
        notes,
        Box::new(|_: &Path, _: &str| Ok::<_, Refusal>(Vec::new())),
    );
    for (class, lane) in classes {
        planner.add_class(class, lane);
    }
    planner.finish();
    planner.result
}

/// An explicit-only lane spends a billed turn per scenario, and its guest run needs the Central login piped in. The
/// plan says both: the VM line pipes the developer's own session, and the host line names the lane's passthroughs,
/// because only `--lane` applies them. A lane with a catalog lane keeps its plain lines.
#[test]
fn an_explicit_only_class_plans_the_piped_guest_run_and_the_billed_host_run() {
    let result = plan_classes(&[("AirLiveLaneEnvironmentUiTest", "ui-live"), ("AirFlowSessionUiTest", "ui")]);

    let vm = command_of(&result, CommandKind::Vm, "ui-live");
    assert_eq!(
        vm,
        &Command {
            kind: CommandKind::Vm,
            title: "the ui-live lane on a Linux VM worker, explicit-only (1 class(es))".to_owned(),
            lines: vec![
                "central login export | ./community/tools/vm.cmd run AirLiveLaneEnvironmentUiTest --test-env \
                 AIR_LIVE_CENTRAL_LOGIN=@-"
                    .to_owned()
            ],
            warning: Some(BILLED_WARNING.to_owned()),
        }
    );
    let host = command_of(&result, CommandKind::Host, "ui-live");
    assert_eq!(
        host,
        &Command {
            kind: CommandKind::Host,
            title: "the ui-live lane on this machine, explicit-only (1 class(es))".to_owned(),
            lines: vec![
                "./community/tools/bt.cmd AirLiveLaneEnvironmentUiTest --test-env AIR_LIVE_CREDENTIALS --test-env \
                 AIR_LIVE_REUSE_HOST_HOMES --test-env ANTHROPIC_API_KEY --test-env CLAUDE_BIN --test-env JUNIE_API_KEY \
                 --test-env JUNIE_BIN --test-env NODE_BIN --test-env NPM_BIN --test-env OPENAI_API_KEY"
                    .to_owned()
            ],
            warning: Some(format!("{HOST_WARNING}; {BILLED_WARNING}")),
        }
    );
    assert!(BILLED_WARNING.contains("billed turn"), "{BILLED_WARNING}");
    let text = render(&result);
    assert!(
        text.contains(
            "vm: the ui-live lane on a Linux VM worker, explicit-only (1 class(es))\n  # explicit-only: every scenario \
             spends a billed turn on a real account, so no --changed, suites, shard or flake runs it\n  central login export \
             | ./community/tools/vm.cmd run AirLiveLaneEnvironmentUiTest --test-env AIR_LIVE_CENTRAL_LOGIN=@-\n"
        ),
        "{text}"
    );

    let ui = command_of(&result, CommandKind::Vm, "ui");
    assert_eq!(ui.title, "the ui lane on a Linux VM worker (1 class(es))");
    assert_eq!(ui.lines, vec!["./community/tools/vm.cmd run AirFlowSessionUiTest".to_owned()]);
    assert_eq!(ui.warning, None);
    let ui_host = command_of(&result, CommandKind::Host, "ui");
    assert_eq!(ui_host.lines, vec!["./community/tools/bt.cmd AirFlowSessionUiTest".to_owned()]);
    assert_eq!(ui_host.warning.as_deref(), Some(HOST_WARNING));
}

/// A scenario name, a suite id, a step id and a check id each reach the scenarios that carry them, and a class
/// that runs more scenarios than were asked for says so, because `run` cannot select one of them.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_program_id_reaches_the_scenarios_that_run_it() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let Some(widest) = widest(&suite_documents(&documents)) else {
        return;
    };
    let first = &widest.profiles[0];
    let key = format!("{}/{}@{}", widest.test_class_name, first.name, lane_word(&widest));

    let result = plan(&repo.root, vec![text(&first.name)]);
    assert!(
        scenario_keys(&result).contains(&key),
        "the scenario name planned {:?}",
        scenario_keys(&result)
    );
    let wider = format!("{} runs {} scenarios", widest.test_class_name, widest.profiles.len());
    assert!(
        notes_of(&result).contains(&wider),
        "a class wider than the ask is not noted: {:?}",
        result.notes
    );

    let suite = plan(&repo.root, vec![text(&widest.suite)]);
    assert!(
        suite.scenarios.len() >= widest.profiles.len(),
        "the suite id planned {:?}",
        scenario_keys(&suite)
    );

    for step in &first.steps {
        let reached = plan(&repo.root, vec![text(&step.step)]);
        assert!(
            scenario_keys(&reached).contains(&key),
            "the step {} did not reach the scenario that walks it: {:?}",
            step.step,
            scenario_keys(&reached)
        );
        for instruction in step.operations.iter().flat_map(|operation| &operation.instructions) {
            if instruction.assertion.is_empty() {
                continue;
            }
            let checked = plan(&repo.root, vec![text(&instruction.assertion)]);
            assert!(
                scenario_keys(&checked).contains(&key),
                "the check {} did not reach its scenario: {:?}",
                instruction.assertion,
                scenario_keys(&checked)
            );
        }
    }
}

/// A name that is nothing the planner knows is unmapped with a reason, and the CLI says so with its own exit code
/// rather than with an empty success.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn an_unknown_name_is_unmapped_and_the_cli_exits_three() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let result = plan(&repo.root, vec![text("zz-no-such-thing")]);
    assert_eq!(result.unmapped.len(), 1, "{result:?}");
    assert_eq!(result.unmapped[0].reason, reason::UNKNOWN_NAME);
    assert!(result.commands.is_empty());

    let (code, stdout, stderr) = cli(&repo, &["--no-default-roots", "zz-no-such-thing"], b"");
    assert_eq!(code, 3, "the CLI exited {code}: {stdout}{stderr}");
    assert!(
        stdout.contains(&format!("zz-no-such-thing: {}", reason::UNKNOWN_NAME)),
        "the text does not name the reason: {stdout}"
    );
}

// --- flows and profiles --------------------------------------------------------------------------------------

/// A flow reaches every scenario that tells it or walks one of its steps, and every scenario of a suite whose story
/// flows implement it - whether it is named by its id or by its flow text, dropped or on disk.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn every_flow_reaches_the_scenarios_that_walk_or_implement_it() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let raw = suite_documents(&documents);
    for flow in flows_of_documents(&raw) {
        let mut want: Vec<String> = raw
            .iter()
            .flat_map(|document| {
                let implements = document.implementation_flows.contains(&flow);
                keys_of(document, |_, told, step_flows| {
                    implements || told == flow || step_flows.contains(&flow)
                })
            })
            .collect();
        want.sort();
        let by_id = plan(&repo.root, vec![text(&flow)]);
        assert_eq!(scenario_keys(&by_id), want, "the flow id {flow}");
        assert_eq!(by_id.read.len(), 1, "{flow}: {:?}", by_id.read);
        assert_eq!((by_id.read[0].kind, by_id.read[0].detail.as_str()), (Kind::Flow, flow.as_str()));
        // The generator's header line is what names the flow of a text, dropped or on disk.
        let flow_text = format!("# Air user flow, generated from Kotlin KDoc @flow* tags.\n\nflow        {flow}\n");
        let from_drop = plan(&repo.root, vec![dropped("downloaded.txt", flow_text.as_bytes())]);
        assert_eq!(scenario_keys(&from_drop), want, "the dropped text of {flow}");
        let on_disk = repo.write(&format!("plugins/air/docs/flows/{flow}.txt"), &flow_text);
        let from_disk = plan(&repo.root, vec![text(&on_disk.to_string_lossy())]);
        assert_eq!(scenario_keys(&from_disk), want, "the flow text of {flow} on disk");
    }
}

/// A flow with a text and no scenario is still a flow, and says why it plans nothing rather than being read as an
/// unknown name.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_flow_with_no_scenario_says_so() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    repo.write(
        "plugins/air/docs/flows/flow-zz-unscenarioed.txt",
        "flow        flow-zz-unscenarioed\n",
    );
    let result = plan(&repo.root, vec![text("flow-zz-unscenarioed")]);
    assert!(result.scenarios.is_empty(), "{result:?}");
    assert_eq!(result.unmapped.len(), 1, "{result:?}");
    assert_eq!(result.unmapped[0].reason, avl_affected::reason::FLOW_DECLARES_NO_SUITE);
}

/// A dropped suite document plans its own scenarios, as it reads, in its own lane.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_dropped_profile_plans_its_own_scenarios() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    for (name, content) in &documents {
        let document = bt_core::catalog::parse_suite_document(content).expect("a suite document");
        let result = plan(&repo.root, vec![dropped(name, content)]);
        assert_eq!(scenario_keys(&result), every_key(&document), "{name}");
        assert_eq!(result.read[0].kind, Kind::Profile, "{name}");
    }
}

// --- test results --------------------------------------------------------------------------------------------

/// A complete JUnit document with one case per scenario of a suite, the one named `failed` failing, and a case
/// named after no scenario when `class_level` is set.
fn junit_of(document: &SuiteDocument, failed: &str, class_level: bool) -> String {
    let class_name = format!("com.intellij.air.tests.flow.generated.{}", document.test_class_name);
    let mut cases = Vec::new();
    let mut failures = 0;
    for declared in &document.profiles {
        let body = if declared.name == failed {
            failures += 1;
            r#"><failure message="boom" type="java.lang.AssertionError">boom</failure></testcase>"#
        } else {
            "/>"
        };
        cases.push(format!(
            r#"<testcase classname="{class_name}" name="{}" time="1"{body}"#,
            declared.name
        ));
    }
    if class_level {
        cases.push(format!(
            r#"<testcase classname="{class_name}" name="scenarios()" time="1"><error message="factory" type="java.lang.IllegalStateException">factory</error></testcase>"#
        ));
        failures += 1;
    }
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?><testsuites><testsuite name="{class_name}" tests="{}" failures="{failures}" errors="0" skipped="0" time="1" timestamp="2026-09-23T10:00:00">{}</testsuite></testsuites>"#,
        cases.len(),
        cases.join("")
    )
}

/// A JUnit document plans the scenario whose case failed; a case that names no scenario is the suite failing
/// around them and plans all of them; a green document plans every case it holds.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_junit_document_plans_what_failed() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let Some(document) = widest(&suite_documents(&documents)) else {
        return;
    };
    let lane = lane_word(&document);
    let failed = &document.profiles[1].name;

    let result = plan(&repo.root, vec![dropped("test.xml", junit_of(&document, failed, false).as_bytes())]);
    assert_eq!(
        scenario_keys(&result),
        vec![format!("{}/{failed}@{lane}", document.test_class_name)]
    );
    assert_eq!(result.read[0].kind, Kind::Junit);
    assert!(result.read[0].detail.starts_with("1 failed"), "{:?}", result.read);

    let all = every_key(&document);
    let class_level = plan(&repo.root, vec![dropped("test.xml", junit_of(&document, "", true).as_bytes())]);
    assert_eq!(scenario_keys(&class_level), all, "a class-level failure plans every scenario");
    let green = plan(&repo.root, vec![dropped("test.xml", junit_of(&document, "", false).as_bytes())]);
    assert_eq!(scenario_keys(&green), all, "a green document plans every case");
    assert!(green.read[0].detail.contains("nothing failed"), "{:?}", green.read);

    // A truncated document is read for what it holds, and says so.
    let whole = junit_of(&document, failed, false);
    let truncated = &whole[..whole.rfind("</testsuite>").expect("the document closes its suite")];
    let cut = plan(&repo.root, vec![dropped("test.xml", truncated.as_bytes())]);
    assert!(
        scenario_keys(&cut).contains(&format!("{}/{failed}@{lane}", document.test_class_name)),
        "a truncated document planned {:?}",
        scenario_keys(&cut)
    );
    assert!(notes_of(&cut).contains("truncated"), "{:?}", cut.notes);
}

/// A persisted run report whose one failure is a scenario of the document.
fn failed_report(document: &SuiteDocument, scenario: &str, traces: Vec<TraceArchive>) -> RunReport {
    let class_name = format!("com.intellij.air.tests.flow.generated.{}", document.test_class_name);
    RunReport {
        report_schema_version: RUN_REPORT_SCHEMA_VERSION,
        iteration_id: "iter-plan-1".to_owned(),
        daemon_run_id: "run-ui-daemon-plan".to_owned(),
        daemon_boot_stamp: "boot-1".to_owned(),
        selection: format!("lane {}", lane_word(document)),
        status: report::Status::Failed,
        started_at: "2026-09-23T10:00:00Z".to_owned(),
        completed_at: "2026-09-23T10:05:00Z".to_owned(),
        duration_ms: 300_000.0,
        ordering: ORDERING.to_owned(),
        execution: ExecutionCounts::default(),
        xml: XmlCounts::default(),
        source: report::Source {
            guest_path: "/x/test.xml".to_owned(),
            retrieval: Retrieval::DaemonHttp,
            integrity: Integrity::Complete,
            diagnostic: None,
        },
        suites: Vec::new(),
        failures: vec![Failure {
            source: FailureSource::JunitXml,
            suite: Some(class_name.clone()),
            suite_timestamp: None,
            class_name: Some(class_name),
            test_name: scenario.to_owned(),
            kind: FailureKind::Failure,
            r#type: None,
            message: "boom".to_owned(),
            detail: None,
            relevant_frames: Vec::new(),
            message_truncated: false,
            detail_truncated: false,
        }],
        skipped_containers: Vec::new(),
        active_tests: Vec::new(),
        unreported_classes: Vec::new(),
        watchdog: Watchdog::default(),
        evidence: Vec::new(),
        trace_archives: traces,
        traces_error: None,
        tree: None,
        tree_error: None,
        protocol_diagnostic: None,
        verdict_diagnostic: None,
    }
}

fn example_manifest() -> Manifest {
    decode_manifest(&example_file(MANIFEST_FILE)).expect("the golden manifest decodes")
}

/// A `vm.cmd` envelope plans its failures, and names the bundles of the traces archive its report names. Found
/// wherever the report sits: a red run's `error.details`, after the NDJSON progress `--stream` writes.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_report_envelope_plans_its_failures_and_names_its_traces() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let Some(document) = widest(&suite_documents(&documents)) else {
        return;
    };
    let scenario = &document.profiles[0].name;
    let manifest = example_manifest();
    let entry = bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario);
    let archive = zip_of(&[
        (&format!("{entry}/{MANIFEST_FILE}"), &example_file(MANIFEST_FILE)),
        (&format!("{entry}/{SPANS_FILE}"), &example_file(SPANS_FILE)),
    ]);
    let mut report = failed_report(
        &document,
        scenario,
        vec![TraceArchive {
            path: archive.path.to_string_lossy().into_owned(),
            bytes: 1,
            bundles: Vec::new(),
        }],
    );
    let class_name = report.failures[0].class_name.clone().expect("the failure names its class");
    let envelope = json!({
        "schemaVersion": 1, "ok": false, "command": "run",
        "error": {"code": "tests_failed", "message": "1 test(s) failed",
            "details": {"report": report, "reproduce": [format!("run {class_name}")]}},
    });
    let streamed = format!("{}\n{envelope}", r#"{"schemaVersion":1,"event":"progress","message":"building"}"#);

    let result = plan(&repo.root, vec![dropped("vm-run.err", streamed.as_bytes())]);
    let lane = lane_word(&document);
    assert_eq!(
        scenario_keys(&result),
        vec![format!("{}/{scenario}@{lane}", document.test_class_name)]
    );
    assert_eq!(result.read[0].kind, Kind::Report);
    // The server names a zip by its real path, and so does the planner: on macOS the temporary directory is reached
    // through `/var`, a link to `/private/var`.
    let real_archive = fscopy::resolve_links(&archive.path).expect("resolve the archive");
    assert_eq!(result.bundles.len(), 1, "{:?}", result.bundles);
    let bundle = &result.bundles[0];
    assert_eq!(bundle.source.path, real_archive.to_string_lossy());
    assert_eq!(bundle.source.entry, format!("{entry}/"));
    assert_eq!(bundle.scenario, manifest.scenario);
    assert_eq!(bundle.status(), manifest.status.as_str());

    // The persisted report alone is the same answer, and an archive this machine lacks is named as missing.
    let gone = tempfile::TempDir::new().expect("a temporary directory");
    report.trace_archives = vec![TraceArchive {
        path: gone.path().join("gone.zip").to_string_lossy().into_owned(),
        bytes: 1,
        bundles: Vec::new(),
    }];
    let persisted = serde_json::to_string(&report).expect("a report serializes");
    let alone = plan(&repo.root, vec![dropped("iter-plan-1.json", persisted.as_bytes())]);
    assert_eq!(alone.scenarios.len(), 1, "{:?}", scenario_keys(&alone));
    assert_eq!(alone.bundles.len(), 1);
    assert_eq!(alone.bundles[0].status(), BUNDLE_MISSING);
    let answered = serde_json::to_value(&alone).expect("a plan serializes");
    assert_eq!(answered["bundles"][0]["status"], BUNDLE_MISSING);

    // A report that fails its own schema is refused as the wire refuses it, not guessed at.
    let broken = persisted.replacen(r#""status":"failed""#, r#""status":"green""#, 1);
    let refused = plan(&repo.root, vec![dropped("iter-plan-1.json", broken.as_bytes())]);
    assert!(!refused.unmapped.is_empty(), "{refused:?}");
    assert_eq!(refused.unmapped[0].reason, reason::UNREADABLE);
}

// --- bundles -------------------------------------------------------------------------------------------------

/// A bundle is opened, not re-recorded: it plans no scenario and no command, and is named with its manifest's
/// identity whether it is a directory, a zip, or one of its files dropped.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_bundle_is_opened_and_plans_nothing() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let example = example_bundle();
    let manifest = example_manifest();
    let check = |what: &str, result: &PlanResult| {
        assert!(
            result.scenarios.is_empty() && result.commands.is_empty(),
            "{what} planned {result:?}"
        );
        assert_eq!(result.bundles.len(), 1, "{what} named {:?}", result.bundles);
        let bundle = &result.bundles[0];
        assert_eq!(
            (&bundle.test_class, &bundle.scenario, &bundle.run_id),
            (&manifest.test_class, &manifest.scenario, &manifest.run_id),
            "{what}"
        );
        assert_eq!(result.read.len(), 1, "{what} was read as {:?}", result.read);
        assert_eq!(result.read[0].kind, Kind::Bundle, "{what}");
    };
    let directory = plan(&repo.root, vec![text(&example.to_string_lossy())]);
    check("the bundle directory", &directory);
    assert_eq!(directory.bundles[0].status(), manifest.status.as_str());
    check(
        "its spans.jsonl on disk",
        &plan(&repo.root, vec![text(&example.join(SPANS_FILE).to_string_lossy())]),
    );
    check(
        "a dropped bundle.json",
        &plan(&repo.root, vec![dropped(MANIFEST_FILE, &example_file(MANIFEST_FILE))]),
    );
    let spans = plan(&repo.root, vec![dropped(SPANS_FILE, &example_file(SPANS_FILE))]);
    check("a dropped spans.jsonl", &spans);
    assert_eq!(spans.bundles[0].status, Status::Truncated, "a spans file read alone is truncated");
    assert_eq!(spans.bundles[0].source.kind, SourceKind::File);

    // Bazel's `outputs.zip` holds the root itself, and the layout under it names a bundle whose manifest never got
    // written.
    let entry = format!(
        "{ROOT_DIR_NAME}/{}",
        bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario)
    );
    let archive = zip_of(&[(&format!("{entry}/{SPANS_FILE}"), &example_file(SPANS_FILE))]);
    let zipped = plan(&repo.root, vec![text(&archive.path.to_string_lossy())]);
    check("an outputs.zip", &zipped);
    assert_eq!(zipped.bundles[0].status, Status::Truncated);
    assert_eq!(zipped.bundles[0].source.entry, format!("{entry}/"));
    let content = std::fs::read(&archive.path).expect("read the zip");
    check("a dropped zip", &plan(&repo.root, vec![dropped("outputs.zip", &content)]));

    let empty = zip_of(&[("readme.txt", b"nothing here")]);
    let result = plan(&repo.root, vec![text(&empty.path.to_string_lossy())]);
    assert_eq!(result.unmapped.len(), 1, "{result:?}");
    assert_eq!(result.unmapped[0].reason, reason::NO_BUNDLE);
    assert_eq!(manifest.status, BundleStatus::Passed, "the golden bundle passed");
}

// --- source paths --------------------------------------------------------------------------------------------

/// A source path is joined the way `vm.cmd suites` joins it, and its answer is taken whole: every scenario of every
/// suite it reaches, its unmapped reasons verbatim, and one `run --changed` per lane with one flag per path.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_source_path_is_joined_as_vm_suites_joins_it() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let raw = suite_documents(&documents);
    let flows = flows_of_documents(&raw);
    let tagged = "plugins/air/frontend/zz/src/Tagged.kt";
    repo.write(tagged, &format!("package air\n\n/**\n * @flow {}\n */\nclass Tagged\n", flows[0]));
    let plain = "plugins/air/frontend/zz/src/Plain.kt";
    repo.write(plain, "package air\n\nclass Plain\n");

    // The reference answer is `bt`'s own, asked directly.
    let runtime = bt_runtime(&repo.root, BtNotes::default());
    let affected = avl_affected::affected_suites(&runtime, air_area(), &[tagged.to_owned(), plain.to_owned()]).expect("bt joins the paths");
    let mut want: Vec<String> = affected
        .suites
        .iter()
        .flat_map(|reached| raw.iter().filter(|document| document.suite == reached.suite))
        .flat_map(every_key)
        .collect();
    want.sort();

    let result = plan(&repo.root, vec![text(&format!("{tagged}\n{plain}"))]);
    assert_eq!(scenario_keys(&result), want);
    assert_eq!(result.unmapped.len(), affected.unmapped.len(), "{:?}", result.unmapped);
    assert_eq!(result.unmapped[0].path, plain);
    assert_eq!(result.unmapped[0].reason, affected.unmapped[0].reason);
    for lane in &affected.lanes {
        let vm = command_of(&result, CommandKind::Vm, lane);
        let changed: Vec<&String> = vm.lines.iter().filter(|line| line.contains(" run --changed ")).collect();
        assert_eq!(changed.len(), 1, "the {lane} sequence is {:?}", vm.lines);
        assert_eq!(changed[0].matches("--changed ").count(), 1, "{}", changed[0]);
        assert!(changed[0].contains(&format!("--changed {tagged}")), "{}", changed[0]);
        assert_eq!(
            changed[0].contains("--lane"),
            affected.lanes.len() > 1,
            "--lane is needed exactly when the paths reach several lanes ({:?}): {}",
            affected.lanes,
            changed[0]
        );
        for line in &vm.lines {
            assert!(!line.contains(" run Air"), "a class the changed run covers is run again: {line}");
        }
    }
}

/// A path with no tag reaches the suites whose routes walk its module, which is `bt`'s coarse relation, and those
/// are the documents whose `modules` name it.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn an_untagged_path_reaches_the_suites_that_walk_its_module() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let raw = suite_documents(&documents);
    let Some(module) = raw.iter().find_map(|document| document.modules.first().cloned()) else {
        println!("skipped: no committed suite walks a module");
        return;
    };
    repo.write(&format!("plugins/air/zz/module/{module}.iml"), "<module />");
    let path = repo.write("plugins/air/zz/module/src/Untagged.kt", "package air\n\nclass Untagged\n");
    let mut want: Vec<String> = raw
        .iter()
        .filter(|document| {
            document
                .modules
                .iter()
                .any(|walked| *walked == module || module.starts_with(&format!("{walked}.")))
        })
        .flat_map(every_key)
        .collect();
    want.sort();
    let result = plan(&repo.root, vec![text(&path.to_string_lossy())]);
    assert_eq!(scenario_keys(&result), want, "the module {module}");
    assert_eq!(result.read[0].kind, Kind::Path);
    assert_eq!(result.read[0].detail, "plugins/air/zz/module/src/Untagged.kt");
}

/// A bare file name is looked up among the checkout's tracked files, and a dropped file's bytes pick among several
/// of that name.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_file_name_is_looked_up_in_the_checkout() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let flow = flows_of_documents(&suite_documents(&documents))[0].clone();
    let first = "plugins/air/zz/one/Same.kt";
    let second = "plugins/air/zz/two/Same.kt";
    let first_path = repo.write(first, &format!("package one\n\n/**\n * @flow {flow}\n */\nclass Same\n"));
    repo.write(second, "package two\n\nclass Same\n");
    let asked = std::rc::Rc::new(std::cell::RefCell::new(Vec::new()));
    let listing = {
        let asked = std::rc::Rc::clone(&asked);
        move |_: &Path, name: &str| {
            asked.borrow_mut().push(name.to_owned());
            Ok(if name == "Same.kt" {
                vec![first.to_owned(), second.to_owned()]
            } else {
                Vec::new()
            })
        }
    };
    let content = std::fs::read(&first_path).expect("read the tagged file");
    let result = plan_listing(&repo.root, listing.clone(), vec![dropped("Same.kt", &content)]);
    assert_eq!(*asked.borrow(), vec!["Same.kt".to_owned()]);
    assert_eq!(result.read.len(), 1, "{:?}", result.read);
    assert_eq!(result.read[0].detail, first);
    assert!(!result.scenarios.is_empty(), "the tagged file planned nothing");

    let both = plan_listing(&repo.root, listing.clone(), vec![text("Same.kt")]);
    assert_eq!(both.read.len(), 2, "{:?}", both.read);
    assert!(notes_of(&both).contains("names 2 files"), "{:?}", both.notes);

    let none = plan_listing(&repo.root, listing.clone(), vec![text("Nowhere.kt")]);
    assert_eq!(none.unmapped.len(), 1, "{none:?}");
    assert_eq!(none.unmapped[0].reason, reason::NO_SUCH_FILE);

    // A path the checkout no longer has is the join's to answer, and is never looked up by its base name: that
    // would plan whichever other file happens to be called `Same.kt`.
    let gone = plan_listing(&repo.root, listing, vec![text("plugins/air/zz/deleted/Same.kt")]);
    assert!(gone.scenarios.is_empty(), "{:?}", scenario_keys(&gone));
    assert_eq!(gone.unmapped.len(), 1, "{:?}", gone.unmapped);
    assert_eq!(gone.unmapped[0].reason, avl_affected::reason::PATH_UNREADABLE);
}

/// `git ls-files` is what answers a file name, literally: a name with pattern characters matches only itself.
#[test]
fn git_lists_files_by_their_literal_name() {
    let repo = tempfile::TempDir::new().expect("a temporary checkout");
    for (relative, content) in [("a/b/Wanted.kt", "x"), ("a/Wanted.kt.bak", "x"), ("c/W*nted.kt", "x")] {
        fixture::write_bytes(repo.path(), relative, content.as_bytes());
    }
    for argv in [&["init", "-q"][..], &["add", "-A"][..]] {
        match run_git(repo.path(), argv) {
            Ok(output) if output.status.success() => {}
            other => {
                println!("skipped: git {argv:?} failed here: {other:?}");
                return;
            }
        }
    }
    assert_eq!(git_list_files(repo.path(), "Wanted.kt"), Ok(vec!["a/b/Wanted.kt".to_owned()]));
    assert_eq!(git_list_files(repo.path(), "W*nted.kt"), Ok(vec!["c/W*nted.kt".to_owned()]));
}

/// An authored suite has no scenario document. A path that reaches one plans it as a class with its own run, and a
/// note says why, rather than failing the whole join or dropping the suite.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn a_path_that_reaches_an_authored_suite_plans_its_class() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    repo.write(
        air_area().catalog().expect("a catalog").authored_suites_file,
        r#"{ "suites": [ { "suite": "problem-feedback", "lane": "GUI_CHAT", "testClassName": "AcpProblemFeedbackUiTest" } ] }"#,
    );
    let path = "plugins/air/tests/integration/gui-chat/test/AcpProblemFeedbackUiTest.kt";
    repo.write(path, "package air\n\nclass AcpProblemFeedbackUiTest\n");

    let result = plan(&repo.root, vec![text(path)]);
    assert!(result.scenarios.is_empty(), "{:?}", scenario_keys(&result));
    assert_eq!(result.classes, vec!["AcpProblemFeedbackUiTest".to_owned()]);
    assert_eq!(result.lanes, vec!["gui-chat".to_owned()]);
    assert!(
        notes_of(&result).contains("AcpProblemFeedbackUiTest, which have no generated profile"),
        "{:?}",
        result.notes
    );
    let vm = command_of(&result, CommandKind::Vm, "gui-chat");
    assert_eq!(vm.lines, vec![vm_run("gui-chat", &["AcpProblemFeedbackUiTest"])]);
}

// --- the CLI -------------------------------------------------------------------------------------------------

#[derive(Parser)]
struct PlanCli {
    #[command(flatten)]
    args: PlanArgs,
}

/// Runs the CLI over the fixture checkout: exit code, stdout, stderr. A command line clap refuses answers clap's
/// own exit code, which is the usage code.
fn cli(repo: &Repo, arguments: &[&str], stdin: &[u8]) -> (u8, String, String) {
    let root = repo.root.to_string_lossy().into_owned();
    let argv = ["plan", "--repo", root.as_str()].into_iter().chain(arguments.iter().copied());
    let args = match PlanCli::try_parse_from(argv) {
        Ok(parsed) => parsed.args,
        Err(error) => {
            return (u8::try_from(error.exit_code()).unwrap_or(1), String::new(), error.to_string());
        }
    };
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    let code = run_plan(&args, &mut &stdin[..], &mut stdout, &mut stderr);
    (
        code,
        String::from_utf8_lossy(&stdout).into_owned(),
        String::from_utf8_lossy(&stderr).into_owned(),
    )
}

/// The CLI answers JSON with `--json` and text otherwise, and reads standard input for `-`.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn the_cli_answers_json_text_and_standard_input() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let document = suite_documents(&documents).remove(0);
    // `--no-default-roots` keeps the answer to the checkout: the default roots are this machine's traces.
    let (code, stdout, stderr) = cli(&repo, &["--json", "--no-default-roots", &document.test_class_name], b"");
    assert_eq!(code, 0, "{stderr}");
    let decoded: Value = serde_json::from_str(&stdout).expect("the JSON answer decodes");
    assert_eq!(decoded["classes"], json!([document.test_class_name]));
    assert_eq!(decoded["commands"].as_array().map(Vec::len), Some(2));
    for field in [
        r#""read":"#,
        r#""lanes":"#,
        r#""unmapped": []"#,
        r#""bundles": []"#,
        r#""existing": []"#,
        r#""notes":"#,
    ] {
        assert!(
            stdout.contains(field),
            "the JSON answer lacks {field}, which is always present: {stdout}"
        );
    }

    let (code, text, stderr) = cli(&repo, &["--no-default-roots", &document.test_class_name], b"");
    assert_eq!(code, 0, "{stderr}");
    assert!(text.contains(&format!("vm: the {} lane", lane_word(&document))), "{text}");
    assert!(text.contains("# takes your screen"), "{text}");
    assert!(text.contains(&format!("bt.cmd {}", document.test_class_name)), "{text}");

    let junit = junit_of(&document, &document.profiles[0].name, false);
    let (code, stdout, stderr) = cli(&repo, &["--json", "--no-default-roots", "-"], junit.as_bytes());
    assert_eq!(code, 0, "{stderr}");
    assert!(
        stdout.contains(r#""as": "junit""#),
        "standard input was not read as the JUnit document it is: {stdout}"
    );

    let (code, _, _) = cli(&repo, &["--frobnicate"], b"");
    assert_eq!(code, 2, "an unknown option");
    let (code, _, _) = cli(&repo, &[], b"");
    assert_eq!(code, 2, "no input");
}

/// The CLI names the bundles already on this machine for the scenarios it plans, as the server's planner route
/// does. Here the golden bundle is zipped the way Bazel zips a test's outputs, under a root given with `--root`; the
/// default roots stay out, so the suite reads nothing of the machine it runs on.
#[test]
#[cfg_attr(windows, ignore = "the Windows CI host cannot fetch the jar of the suite documents (ADR 0186)")]
fn the_cli_lists_the_bundles_already_on_disk_for_its_scenarios() {
    let documents = documents_or_skip!();
    let repo = fixture_repo(&documents);
    let manifest = example_manifest();
    let flow = manifest.flow.clone().expect("the golden bundle tells a flow");
    let entry = format!(
        "{ROOT_DIR_NAME}/{}",
        bundle_path(&manifest.run_id, &manifest.test_class, &manifest.scenario)
    );
    let archive = zip_of(&[
        (&format!("{entry}/{MANIFEST_FILE}"), &example_file(MANIFEST_FILE)),
        (&format!("{entry}/{SPANS_FILE}"), &example_file(SPANS_FILE)),
    ]);
    let root = archive
        .path
        .parent()
        .expect("the zip has a directory")
        .to_string_lossy()
        .into_owned();
    let arguments = ["--no-default-roots", "--root", root.as_str(), flow.as_str()];

    let (code, stdout, stderr) = cli(&repo, &[&["--json"][..], &arguments[..]].concat(), b"");
    assert_eq!(code, 0, "{stderr}");
    let result: Value = serde_json::from_str(&stdout).expect("the JSON answer decodes");
    let key = json!({"testClass": manifest.test_class, "scenario": manifest.scenario, "flow": flow, "lane": "ui"});
    assert!(
        result["scenarios"].as_array().is_some_and(|scenarios| scenarios.contains(&key)),
        "the flow {flow} planned {}, which lacks the golden bundle's scenario",
        result["scenarios"]
    );
    let existing = result["existing"].as_array().expect("existing is a list");
    assert_eq!(existing.len(), 1, "{existing:?}");
    assert_eq!(existing[0]["runId"], json!(manifest.run_id));
    assert_eq!(existing[0]["source"]["kind"], json!("zip"));
    assert_eq!(existing[0]["source"]["entry"], json!(format!("{entry}/")));
    assert_eq!(result["bundles"], json!([]), "a flow names no bundle itself");

    let (code, text, stderr) = cli(&repo, &arguments, b"");
    assert_eq!(code, 0, "{stderr}");
    assert!(
        text.contains("1 bundle(s) of these scenarios on this machine, newest first:") && text.contains(&manifest.run_id),
        "the text answer does not list the bundle on disk:\n{text}"
    );

    let elsewhere = tempfile::TempDir::new().expect("a temporary directory");
    let empty_root = elsewhere.path().to_string_lossy().into_owned();
    let (code, stdout, _) = cli(&repo, &["--json", "--no-default-roots", "--root", &empty_root, &flow], b"");
    assert_eq!(code, 0);
    assert!(
        stdout.contains(r#""existing": []"#),
        "a root without the scenario's bundles answered {stdout}"
    );
}
