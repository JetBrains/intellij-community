use std::fs::FileTimes;

use avl_wire::progress::{Event, Record, RunStarted};
use jiff::SignedDuration;
use pretty_assertions::assert_eq;

use super::*;
use crate::format::words;

const S: Duration = Duration::from_secs(1);

fn base() -> Timestamp {
    "2026-09-24T12:00:00Z".parse().expect("an RFC 3339 instant")
}

fn at(offset: Duration) -> Timestamp {
    base() + SignedDuration::try_from(offset).expect("a small offset")
}

/// One journal as the controller writes it: one record per line, stamped by the host.
#[derive(Default)]
struct JournalWriter {
    lines: Vec<String>,
}

impl JournalWriter {
    fn add(&mut self, event: &Event, offset: Duration, worker: Option<&str>) {
        let record = Record::new(event, at(offset), worker);
        self.lines.push(serde_json::to_string(&record).expect("a record serializes"));
    }

    /// A forwarded daemon record with its own bytes, as the daemon wrote it.
    fn daemon(&mut self, raw: &str, offset: Duration, worker: &str) {
        let instant = at(offset).strftime("%Y-%m-%dT%H:%M:%S%.3fZ");
        self.lines.push(format!(
            r#"{{"schemaVersion":1,"event":"runProgress","at":"{instant}","worker":"{worker}","data":{raw}}}"#
        ));
    }

    fn phase(&mut self, phase: Phase, state: PhaseState, detail: Option<&str>, elapsed: Duration, offset: Duration, worker: Option<&str>) {
        self.add(
            &Event::Phase(PhaseChange {
                phase,
                state,
                detail: detail.map(str::to_owned),
                elapsed_ms: u64::try_from(elapsed.as_millis()).expect("a small duration"),
                error: None,
            }),
            offset,
            worker,
        );
    }
}

fn run_started(run_id: &str, args: &[&str]) -> Event {
    Event::RunStarted(RunStarted {
        run_id: run_id.to_owned(),
        command: "run".to_owned(),
        args: args.iter().map(|arg| (*arg).to_owned()).collect(),
        checkout: None,
    })
}

fn write_journal(root: &Path, run_id: &str, modified: Timestamp, writer: &JournalWriter) {
    let directory = root.join("runs").join(run_id);
    fs::create_dir_all(&directory).expect("a run directory");
    let path = directory.join(FILE_NAME);
    fs::write(&path, writer.lines.join("\n") + "\n").expect("a journal");
    let modified = SystemTime::from(modified);
    File::options()
        .write(true)
        .open(&path)
        .and_then(|file| file.set_times(FileTimes::new().set_modified(modified).set_accessed(modified)))
        .expect("the journal's time is set");
}

/// One `run flow-x` whose phases and classes took the given times.
fn lane_run(build: Duration, iteration: Duration, first: Duration, second: Duration, exit_code: i32) -> JournalWriter {
    let mut writer = JournalWriter::default();
    writer.add(&run_started("r", &["flow-x"]), Duration::ZERO, None);
    writer.phase(
        Phase::HostBuild,
        PhaseState::Started,
        Some("//x"),
        Duration::ZERO,
        Duration::ZERO,
        None,
    );
    writer.phase(Phase::HostBuild, PhaseState::Finished, None, build, build, None);
    let start = build;
    let w1 = Some("w1");
    writer.phase(
        Phase::Iteration,
        PhaseState::Started,
        Some("flow flow-x"),
        Duration::ZERO,
        start,
        w1,
    );
    writer.daemon(r#"{"event":"runStarted","iterationId":"i"}"#, start, "w1");
    writer.daemon(r#"{"event":"testStarted","className":"A","displayName":"a"}"#, start + S, "w1");
    writer.daemon(
        r#"{"event":"testFinished","className":"A","displayName":"a","status":"SUCCESSFUL"}"#,
        start + first,
        "w1",
    );
    writer.daemon(
        r#"{"event":"testFinished","className":"B","displayName":"b","status":"FAILED"}"#,
        start + first + second,
        "w1",
    );
    writer.daemon(r#"{"event":"summary","testsStarted":2}"#, start + iteration, "w1");
    writer.phase(Phase::Iteration, PhaseState::Finished, None, iteration, start + iteration, w1);
    writer.add(
        &Event::RunFinished(RunFinished {
            exit_code,
            code: None,
            message: None,
        }),
        start + iteration + S,
        None,
    );
    writer
}

fn hours_ago(hours: i64) -> Timestamp {
    base() - SignedDuration::from_hours(hours)
}

#[test]
fn a_typical_time_is_the_median_of_the_newest_runs() {
    let root = tempfile::tempdir().expect("a temporary directory");
    write_journal(root.path(), "one", hours_ago(3), &lane_run(40 * S, 100 * S, 10 * S, 20 * S, 0));
    write_journal(root.path(), "two", hours_ago(2), &lane_run(50 * S, 120 * S, 12 * S, 30 * S, 6));
    write_journal(root.path(), "three", hours_ago(1), &lane_run(90 * S, 110 * S, 14 * S, 25 * S, 0));

    let expected = read(root.path(), "run", &words(["flow-x"]));
    assert_eq!(expected.phase(Phase::HostBuild, None), Some(&Typical { ms: 50_000, samples: 3 }));
    assert_eq!(
        expected.phase(Phase::Iteration, Some("flow flow-x")).map(|typical| typical.ms),
        Some(110_000)
    );
    // A class's time starts where the class before it ended, so the first class includes the iteration's start.
    assert_eq!(expected.classes["A"].ms, 12_000);
    assert_eq!(expected.classes["B"].ms, 25_000);
    // A red lane is a verdict, so its length counts; the run took the build, the iteration and one second.
    assert_eq!(
        expected.run,
        Some(Typical {
            ms: 50_000 + 120_000 + 1_000,
            samples: 3
        })
    );
}

// One run shows no spread, so one measurement gives no typical time.
#[test]
fn one_run_gives_no_typical_time() {
    let root = tempfile::tempdir().expect("a temporary directory");
    write_journal(root.path(), "one", base(), &lane_run(40 * S, 100 * S, 10 * S, 20 * S, 0));
    assert_eq!(read(root.path(), "run", &words(["flow-x"])), Expected::default());
}

// The run's total is for the same command line only, and only for a run that ended with a verdict.
#[test]
fn the_run_total_is_for_the_same_command_line() {
    let root = tempfile::tempdir().expect("a temporary directory");
    write_journal(root.path(), "one", hours_ago(2), &lane_run(40 * S, 100 * S, 10 * S, 20 * S, 0));
    write_journal(root.path(), "two", hours_ago(1), &lane_run(40 * S, 100 * S, 10 * S, 20 * S, 70));
    assert_eq!(
        read(root.path(), "run", &words(["flow-x"])).run,
        None,
        "a run that stopped with 70 counted"
    );
    write_journal(root.path(), "three", base(), &lane_run(40 * S, 100 * S, 10 * S, 20 * S, 0));
    assert_eq!(
        read(root.path(), "run", &words(["flow-y"])).run,
        None,
        "another command line has a run total"
    );
    assert_eq!(
        read(root.path(), "run", &words(["flow-x"])).run.map(|typical| typical.samples),
        Some(2)
    );
}

// A damaged line is skipped, and a journal that ends inside an iteration still gives its finished classes. Stamps
// of any sub-second precision are read.
#[test]
fn a_damaged_journal_gives_what_it_can() {
    let root = tempfile::tempdir().expect("a temporary directory");
    for (index, name) in ["one", "two"].into_iter().enumerate() {
        let mut writer = JournalWriter::default();
        writer
            .lines
            .extend([r"{not json".to_owned(), r#"{"event":"phase","at":"yesterday"}"#.to_owned()]);
        writer.add(&run_started(name, &["flow-x"]), Duration::ZERO, None);
        writer.daemon(r#"{"event":"runStarted","iterationId":"i"}"#, Duration::ZERO, "");
        if index == 0 {
            writer.daemon(r#"{"event":"testFinished","className":"A","status":"SUCCESSFUL"}"#, 8 * S, "");
        } else {
            writer.lines.push(
                r#"{"schemaVersion":1,"event":"runProgress","at":"2026-09-24T12:00:08.000000000Z","worker":"","data":{"event":"testFinished","className":"A","status":"SUCCESSFUL"}}"#
                    .to_owned(),
            );
        }
        writer.daemon(r#"{"event":"testStarted","className":"B"}"#, 9 * S, "");
        let modified = base() + SignedDuration::from_mins(i64::try_from(index).expect("a small index"));
        write_journal(root.path(), name, modified, &writer);
    }
    let expected = read(root.path(), "run", &words(["flow-x"]));
    assert_eq!(expected.classes.get("A"), Some(&Typical { ms: 8_000, samples: 2 }));
    assert_eq!(
        expected.classes.get("B"),
        None,
        "a class whose test never finished has a typical time"
    );
}

#[test]
fn no_runs_directory_is_no_history() {
    let root = tempfile::tempdir().expect("a temporary directory");
    assert_eq!(read(&root.path().join("absent"), "run", &[]), Expected::default());
}

#[test]
fn a_typical_time_is_the_lower_median_of_at_most_seven() {
    let measured: Vec<Duration> = [9, 1, 8, 2, 7, 3, 6, 100, 200].iter().map(|s| *s * S).collect();
    // The newest seven are 9 1 8 2 7 3 6; sorted 1 2 3 6 7 8 9; the median is 6.
    assert_eq!(typical_of(&measured), Some(Typical { ms: 6_000, samples: 7 }));
    // An even count answers the lower middle one.
    assert_eq!(typical_of(&[4 * S, 2 * S]), Some(Typical { ms: 2_000, samples: 2 }));
    assert_eq!(typical_of(&[4 * S]), None);
}
