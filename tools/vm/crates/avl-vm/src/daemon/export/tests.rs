use std::fs;
use std::path::Path;

use avl_base::report::Buffer;
use avl_base::{Backend, GuestOs};
use avl_host_testkit::load_config;
use avl_wire::progress::{Event, RunFinished};
use avl_wire::report::Status;
use pretty_assertions::assert_eq;

use super::*;

const RUN_ID: &str = "develar-run-1";

/// A checkout and a runtime root of one finished iteration: a report with its XML and evidence, a zip of a passed
/// scenario, and a zip of a passed scenario and a failed one.
struct Fixture {
    checkout: tempfile::TempDir,
    runtime: tempfile::TempDir,
}

impl Fixture {
    fn new() -> Self {
        let fixture = Self {
            checkout: tempfile::tempdir().expect("a checkout"),
            runtime: tempfile::tempdir().expect("a runtime root"),
        };
        let reports = fixture.reports();
        fs::create_dir_all(reports.join("it-1-evidence")).expect("the evidence directory");
        fs::write(reports.join("it-1.json"), "{}").expect("the report");
        fs::write(reports.join("it-1.xml"), "<testsuites/>").expect("the XML");
        fs::write(reports.join("it-1-evidence").join("idea.log"), "log").expect("the evidence");
        let traces = fixture.traces();
        fs::create_dir_all(&traces).expect("the traces directory");
        fs::write(traces.join("001.zip"), "passed").expect("a zip");
        fs::write(traces.join("002.zip"), "mixed").expect("a zip");
        fixture
    }

    fn reports(&self) -> PathBuf {
        self.runtime.path().join("workers/air-docker-1/reports/run-ui-daemon-1")
    }

    fn traces(&self) -> PathBuf {
        self.runtime.path().join("runs").join(RUN_ID).join("traces/it-1")
    }

    fn settings(&self, export: Option<&str>) -> Config {
        let mut environment = vec![("HOME".to_owned(), self.runtime.path().to_string_lossy().into_owned())];
        environment.extend(export.map(|export| ("AIR_VM_EXPORT".to_owned(), export.to_owned())));
        load_config(Backend::Docker, GuestOs::Linux, &environment, self.checkout.path())
    }

    /// The export of the run in the checkout.
    fn exported(&self) -> PathBuf {
        self.checkout.path().join(VM_EXPORT_DIR).join("runs").join(RUN_ID)
    }

    /// Publishes the red iteration under the export of `settings`, and answers what the reporter said.
    fn publish(&self, settings: &Config) -> String {
        self.publish_run(settings, Status::Failed)
    }

    /// Publishes the iteration under the export of `settings`, red or green as `status` says, and answers what the
    /// reporter said.
    fn publish_run(&self, settings: &Config, status: Status) -> String {
        let red = status != Status::Passed;
        let (reporter, stderr): (Reporter, Buffer) = avl_host_testkit::prose();
        {
            let _export = attach(&reporter, settings, RUN_ID);
            let traces = self.traces();
            let last = if red { "failed" } else { "passed" };
            for (zip, scenario, bundle) in [("001.zip", "a", "passed"), ("002.zip", "b", "passed"), ("002.zip", "c", last)] {
                reporter.publish(
                    Event::TraceReady(TraceReady {
                        iteration_id: "it-1".to_owned(),
                        bundle_id: scenario.to_owned(),
                        test_class: "AirComposerGeneratedFlowUiTest".to_owned(),
                        scenario: scenario.to_owned(),
                        flow: None,
                        status: bundle.to_owned(),
                        has_video: true,
                        zip: traces.join(zip).to_string_lossy().into_owned(),
                        entry: format!("it-1/{scenario}/"),
                    }),
                    None,
                );
            }
            reporter.publish(
                Event::IterationReported(IterationReported {
                    iteration_id: "it-1".to_owned(),
                    daemon_run_id: "run-ui-daemon-1".to_owned(),
                    selection: "AirComposerGeneratedFlowUiTest".to_owned(),
                    status,
                    report_path: self.reports().join("it-1.json").to_string_lossy().into_owned(),
                    tests_failed: u32::from(red),
                    tests_started: 3,
                }),
                None,
            );
            reporter.publish(
                Event::RunFinished(RunFinished {
                    exit_code: if red { 6 } else { 0 },
                    code: red.then(|| "tests_failed".to_owned()),
                    message: None,
                }),
                None,
            );
        }
        stderr.text()
    }
}

/// The files below `directory`, relative to it, in order.
fn files(directory: &Path) -> Vec<String> {
    let mut found = Vec::new();
    let mut pending = vec![directory.to_path_buf()];
    while let Some(next) = pending.pop() {
        for entry in fs::read_dir(&next).expect("a directory") {
            let path = entry.expect("an entry").path();
            if path.is_dir() {
                pending.push(path);
            } else {
                let relative = path.strip_prefix(directory).expect("below the directory");
                found.push(relative.to_string_lossy().replace('\\', "/"));
            }
        }
    }
    found.sort();
    found
}

#[test]
fn a_failed_export_takes_the_journal_the_report_and_each_zip_with_a_scenario_that_did_not_pass() {
    let fixture = Fixture::new();
    let said = fixture.publish(&fixture.settings(Some("failed")));
    assert_eq!(
        files(&fixture.exported()),
        [
            "events.ndjson",
            "reports/it-1-evidence/idea.log",
            "reports/it-1.json",
            "reports/it-1.xml",
            "traces/it-1/002.zip",
        ]
    );
    assert_eq!(fs::read_to_string(fixture.exported().join("traces/it-1/002.zip")).unwrap(), "mixed");
    let journal = fs::read_to_string(fixture.exported().join("events.ndjson")).unwrap();
    assert_eq!(journal.lines().count(), 5, "every record up to runFinished: {journal}");
    assert!(journal.lines().last().is_some_and(|line| line.contains("runFinished")), "{journal}");
    assert!(
        said.contains("exported the journal, 1 report(s) and 1 trace zip(s) to out/air/vm-export/runs/develar-run-1"),
        "{said}"
    );
}

#[test]
fn an_all_export_takes_every_zip() {
    let fixture = Fixture::new();
    fixture.publish(&fixture.settings(Some("all")));
    let exported = files(&fixture.exported());
    assert!(exported.contains(&"traces/it-1/001.zip".to_owned()), "{exported:?}");
    assert!(exported.contains(&"traces/it-1/002.zip".to_owned()), "{exported:?}");
}

#[test]
fn a_run_exports_nothing_by_default() {
    let fixture = Fixture::new();
    let settings = fixture.settings(None);
    assert_eq!(settings.export, Export::Off);
    let said = fixture.publish(&settings);
    assert!(!fixture.checkout.path().join(VM_EXPORT_DIR).exists());
    assert!(!said.contains("export"), "{said}");
}

#[test]
fn a_copy_that_fails_is_noted_and_the_rest_goes_on() {
    let fixture = Fixture::new();
    fs::remove_file(fixture.reports().join("it-1.json")).unwrap();
    let said = fixture.publish(&fixture.settings(Some("failed")));
    assert!(fixture.exported().join("traces/it-1/002.zip").is_file());
    assert!(
        said.contains("the export in out/air/vm-export/runs/develar-run-1 is not complete: cannot export the report"),
        "{said}"
    );
}

#[test]
fn a_failed_export_of_a_run_that_failed_nothing_leaves_no_directory() {
    let fixture = Fixture::new();
    let said = fixture.publish_run(&fixture.settings(Some("failed")), Status::Passed);
    assert!(
        !fixture.checkout.path().join(VM_EXPORT_DIR).exists(),
        "{:?}",
        files(fixture.checkout.path())
    );
    assert!(said.contains("the run failed nothing, so it exported nothing"), "{said}");
}

#[test]
fn an_all_export_of_a_green_run_keeps_every_file() {
    let fixture = Fixture::new();
    fixture.publish_run(&fixture.settings(Some("all")), Status::Passed);
    let exported = files(&fixture.exported());
    assert!(exported.contains(&"reports/it-1.json".to_owned()), "{exported:?}");
    assert!(exported.contains(&"traces/it-1/002.zip".to_owned()), "{exported:?}");
}

#[test]
fn a_journal_of_the_export_that_cannot_be_created_is_a_failure_of_the_export() {
    let fixture = Fixture::new();
    fs::create_dir_all(fixture.exported()).unwrap();
    fs::write(fixture.exported().join("events.ndjson"), "").unwrap();
    let said = fixture.publish(&fixture.settings(Some("failed")));
    assert!(fixture.exported().join("traces/it-1/002.zip").is_file());
    assert!(
        said.contains("the export in out/air/vm-export/runs/develar-run-1 is not complete: cannot create the journal of the export"),
        "{said}"
    );
}
