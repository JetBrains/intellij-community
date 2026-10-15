use std::cell::RefCell;
use std::fs;
use std::time::Duration;

use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::*;
use crate::lane::program::tests::FakeServices;

/// A host side over [`FakeServices`] whose restart performs `while_down` on `context`.
struct Side<'a> {
    services: &'a FakeServices,
    context: &'a Path,
    restarts: RefCell<u32>,
}

impl HostSide for Side<'_> {
    fn services(&self) -> &dyn ServiceControl {
        self.services
    }

    fn restart_ide(&mut self, while_down: &mut dyn FnMut(&Path) -> Result<(), String>) -> Result<(), String> {
        *self.restarts.borrow_mut() += 1;
        while_down(self.context)
    }
}

fn perform_id(id: HostId, args: Value, values: &mut Values, side: &mut Side<'_>) -> Result<(), String> {
    let args: BTreeMap<String, Value> = serde_json::from_value(args).unwrap();
    perform(id, "label", &args, values, side)
}

// The registry ids arm and clear the faults of the JVM driver: a held package answer for 120 s, and a 503 for the next
// package. The fault assertion holds only for the package fault.
#[test]
fn the_registry_ids_arm_hold_release_and_check_the_package_fault() {
    let services = FakeServices::default();
    let context = TempDir::new().unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let mut values = Values::default();
    perform_id(HostId::Action(HostAction::ConsentHoldDownload), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(
        *services.fault.lock().unwrap(),
        ("slow-package-response".to_owned(), None, Some(120_000))
    );
    let refused = perform_id(
        HostId::Assertion(HostAssertion::ConsentDownloadFaultArmed),
        json!({}),
        &mut values,
        &mut side,
    );
    assert_eq!(
        refused,
        Err("expected a package HTTP fault, found slow-package-response".to_owned())
    );
    perform_id(
        HostId::Action(HostAction::RegistryFailNextDownload),
        json!({}),
        &mut values,
        &mut side,
    )
    .unwrap();
    assert_eq!(*services.fault.lock().unwrap(), ("package-http-error".to_owned(), Some(503), None));
    perform_id(
        HostId::Assertion(HostAssertion::ConsentDownloadFaultArmed),
        json!({}),
        &mut values,
        &mut side,
    )
    .unwrap();
    perform_id(HostId::Action(HostAction::ReleaseDownload), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(services.fault.lock().unwrap().0, "none");
}

// The mark answers the count so far as `consentRegistryBaseline`, and the count reads the package requests after it
// only. Zero is read at once, and a wrong count names the request log.
#[test]
fn the_request_count_reads_the_package_requests_after_the_mark() {
    let services = FakeServices::default();
    services.paths.lock().unwrap().push("/packages/old.zip".to_owned());
    let context = TempDir::new().unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let mut values = Values::default();
    perform_id(HostId::Action(HostAction::RegistryMarkRequests), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(values.get(REGISTRY_BASELINE), Some(&json!(1)));
    services
        .paths
        .lock()
        .unwrap()
        .extend(["/registry.json".to_owned(), "/packages/new.zip".to_owned()]);
    perform_id(
        HostId::Assertion(HostAssertion::RegistryRequestCount),
        json!({"requests": 1}),
        &mut values,
        &mut side,
    )
    .unwrap();
    let refused = await_request_count(&services, 1, 0, Duration::ZERO).unwrap_err();
    assert!(
        refused.starts_with("expected 0 package request(s) after registry event 1, found 1\n"),
        "{refused}"
    );
    let missing = perform_id(
        HostId::Assertion(HostAssertion::RegistryRequestCount),
        json!({"requests": 0}),
        &mut Values::default(),
        &mut side,
    );
    assert_eq!(
        missing,
        Err("the instruction 'label' takes the value 'consentRegistryBaseline', and no earlier instruction answered it".to_owned())
    );
}

// The restart performs the operations of `whileDown` on the context while the IDE is down, answers each read in
// `whileDownReads`, and empties `whileDown`, so a later restart performs nothing twice.
#[test]
fn a_restart_performs_while_down_and_answers_the_reads() {
    let services = FakeServices::default();
    let context = TempDir::new().unwrap();
    let events = context.path().join("home/.junie/sessions/s/events.jsonl");
    fs::create_dir_all(events.parent().unwrap()).unwrap();
    fs::write(&events, "first\n").unwrap();
    let mut side = Side {
        services: &services,
        context: context.path(),
        restarts: RefCell::new(0),
    };
    let relative = "home/.junie/sessions/s/events.jsonl";
    let mut values = Values::new([(
        WHILE_DOWN.to_owned(),
        json!([{"type": "append", "path": relative, "text": "second\n"}, {"type": "read", "path": relative}]),
    )]);
    perform_id(HostId::Action(HostAction::RestartIde), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(*side.restarts.borrow(), 1);
    assert_eq!(values.get(WHILE_DOWN_READS), Some(&json!({relative: "first\nsecond\n"})));
    assert_eq!(values.get(WHILE_DOWN), Some(&json!([])));
    perform_id(HostId::Action(HostAction::RestartIde), json!({}), &mut values, &mut side).unwrap();
    assert_eq!(fs::read_to_string(&events).unwrap(), "first\nsecond\n");
}

// A file operation reaches files of the context only: no absolute path, no `..`, and no file that does not exist.
#[test]
fn a_file_operation_outside_the_context_or_on_a_missing_file_is_refused() {
    let context = TempDir::new().unwrap();
    let refused = |operations: Value| {
        let operations: Vec<ContextFileOp> = serde_json::from_value(operations).unwrap();
        apply_context_file_ops(context.path(), &operations).unwrap_err()
    };
    assert!(refused(json!([{"type": "read", "path": "../outside.xml"}])).contains("is not a file of the IDE context"));
    assert!(refused(json!([{"type": "read", "path": "/etc/hosts"}])).contains("is not a file of the IDE context"));
    assert!(refused(json!([{"type": "append", "path": "home/missing.jsonl", "text": "x"}])).contains("finds no file"));
}
