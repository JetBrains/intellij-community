use std::collections::BTreeMap;

use avl_host_sys::proc::HostProcess;
use pretty_assertions::assert_eq;

use super::*;
use crate::bench::files;
use crate::bench::testing::testdata_dir;

/// The sample of `project-run-01` of the fixture session: the IDE 4242 with `fsnotifier`, `cef_server` with three
/// helpers, and the three WebKit processes whose parent is launchd.
fn fixture() -> Sample {
    let text = files::read_text(&testdata_dir().join("session/project-run-01").join(MEMORY_FILE)).expect("the sample");
    parse(&text).expect("a sample")
}

fn host(pid: i32, parent_pid: i32, responsible_pid: i32, name: &str) -> HostProcess {
    HostProcess {
        pid,
        parent_pid,
        responsible_pid: Some(responsible_pid),
        name: name.to_owned(),
    }
}

/// The sample holds the IDE, its descendants and the processes that name it as their responsible process. The JCEF
/// and the WebKit processes of another IDE name that IDE, so the sample leaves them out, as it leaves out the parent.
#[test]
fn a_sample_holds_the_tree_of_the_ide_and_the_processes_that_it_is_responsible_for() {
    let table = [
        host(9845, 1, 9845, "jetbrains"),
        host(9900, 9845, 9845, "cef_server"),
        host(9901, 9900, 9845, "cef_server Helper"),
        host(9950, 1, 9845, "com.apple.WebKit.WebContent"),
        host(4100, 9845, 9845, "vm"),
        host(4242, 4100, 4242, "java"),
        host(4300, 4242, 4242, "cef_server"),
        host(4303, 4300, 4242, "cef_server Helper (Renderer)"),
        host(4400, 4303, 4400, "disclaimed grandchild"),
        host(5000, 1, 4242, "com.apple.WebKit.WebContent"),
        host(5001, 1, 4242, "com.apple.WebKit.GPU"),
    ];
    let pids: Vec<i32> = members(&table, 4242).iter().map(|process| process.pid).collect();
    assert_eq!(pids, [4242, 4300, 4303, 4400, 5000, 5001]);
    assert_eq!(members(&table, 7).len(), 0, "a pid without a process has no sample");
}

/// Each process of the sample is in one group: a `cef_server` helper is JCEF through its parent and its name, and a
/// WebKit process is WebKit by its name. A metric is in MB of 2^20 bytes, to a tenth.
#[test]
fn the_groups_of_a_sample_give_the_memory_metrics() {
    let sample = fixture();
    let groups: Vec<(i32, Group)> = sample
        .processes
        .iter()
        .map(|process| (process.pid, sample.group(process)))
        .collect();
    assert_eq!(
        groups,
        [
            (4242, Group::Ide),
            (4250, Group::Other),
            (4300, Group::Jcef),
            (4301, Group::Jcef),
            (4302, Group::Jcef),
            (4303, Group::Jcef),
            (5000, Group::WebKit),
            (5001, Group::WebKit),
            (5002, Group::WebKit),
        ]
    );
    let expected: BTreeMap<String, f64> = [
        (MEMORY_IDE, 1228.8),
        (MEMORY_JCEF, 200.0),
        (MEMORY_WEBKIT, 65.0),
        (MEMORY_OTHER, 1.0),
        (MEMORY_ALL, 1494.8),
        (MEMORY_IDE_RESIDENT, 1433.6),
        (MEMORY_ALL_RESIDENT, 1937.6),
        (PROCESSES_JCEF, 4.0),
        (PROCESSES_WEBKIT, 3.0),
        (PROCESSES_OTHER, 1.0),
    ]
    .into_iter()
    .map(|(metric, value)| (metric.to_owned(), value))
    .collect();
    assert_eq!(sample.metrics(), expected);
    assert_eq!(sample.foreign_responsible(), None);
}

/// A child of a JCEF helper is JCEF too, whatever its name.
#[test]
fn a_descendant_of_a_jcef_process_is_jcef() {
    let mut sample = fixture();
    let mut child = sample.processes[5].clone();
    child.pid = 4304;
    child.parent_pid = 4303;
    child.name = "crashpad_handler".to_owned();
    sample.processes.push(child.clone());
    assert_eq!(sample.group(&child), Group::Jcef);
}

/// An IDE that is not its own responsible process has no WebKit metric: no WebKit process names it.
#[test]
fn an_ide_that_names_another_responsible_process_has_no_webkit_metric() {
    let mut sample = fixture();
    sample.processes.retain(|process| !process.name.starts_with(WEBKIT_PREFIX));
    sample.processes[0].responsible_pid = Some(9845);
    assert_eq!(sample.foreign_responsible(), Some(9845));
    let metrics = sample.metrics();
    assert!(
        !metrics.contains_key(MEMORY_WEBKIT) && !metrics.contains_key(PROCESSES_WEBKIT),
        "{metrics:?}"
    );
    assert_eq!(metrics.get(MEMORY_ALL).copied(), Some(1429.8));
}

/// A sample without the IDE process, and a text of another shape, are refused.
#[test]
fn a_sample_without_the_ide_is_refused() {
    let refusal = parse(r#"{"pid": 1, "processes": []}"#).expect_err("a refusal");
    assert_eq!(format!("{refusal:#}"), "the sample has no process of the IDE 1");
    let refusal = parse("[]").expect_err("a refusal");
    assert!(format!("{refusal:#}").starts_with("not a memory sample: "), "{refusal:#}");
    let text = serde_json::to_string(&fixture()).expect("JSON");
    assert_eq!(parse(&text).expect("a sample"), fixture(), "a sample reads back");
}

/// On macOS a sample of this process holds it first, with its memory.
#[cfg(target_os = "macos")]
#[test]
fn a_sample_of_this_process_holds_it_first() {
    let pid = std::process::id();
    let sample = take(pid).expect("a sample");
    let own = &sample.processes[0];
    assert_eq!(i64::from(own.pid), i64::from(pid));
    assert!(own.footprint_bytes > 0 && own.resident_bytes > 0, "{own:?}");
    assert!(sample.metrics()[MEMORY_IDE] > 0.0);
}
