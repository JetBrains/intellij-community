use pretty_assertions::assert_eq;

use super::first_line;

const LOG: &str = "\
2026-10-22 16:53:18,401 [     12]   INFO - #c.i.i.StartupUtil - ------------------------------------------------------ IDE STARTED
2026-10-22 16:53:22,190 [   3801]   INFO - #o.r.c.p.m.i.CargoSyncTask - CargoSyncTask started
2026-10-22 16:53:30,000 [  11611]   INFO - #o.r.c.p.m.i.CargoSyncTask - CargoSyncTask started
";

/// The first line with the text gives its local time and the milliseconds of the log, and the UTC offset turns the
/// local time into a time since the epoch.
#[test]
fn the_first_line_gives_its_two_times() {
    let line = first_line(LOG, "CargoSyncTask started").expect("a log").expect("a line");
    assert_eq!(line.log_ms, 3801);
    assert_eq!(line.local.to_string(), "2026-10-22T16:53:22.19");
    // 2026-10-22T14:53:22.190Z
    assert_eq!(line.epoch_ms(7200).expect("a time"), 1_792_680_802_190);
    assert_eq!(line.epoch_ms(0).expect("a time") - line.epoch_ms(7200).expect("a time"), 7_200_000);
}

/// A log without the text has no line, and a line with the text of another shape is an error that names the line.
#[test]
fn a_missing_line_is_none_and_a_line_of_another_shape_is_refused() {
    assert_eq!(first_line(LOG, "Finished Cargo sync task").expect("a log"), None);
    let error = first_line("CargoSyncTask started\n", "CargoSyncTask started").expect_err("no time");
    assert_eq!(
        format!("{error:#}"),
        "line 1: CargoSyncTask started: no `yyyy-MM-dd HH:mm:ss,SSS` at the start"
    );
    let error = first_line("at the start of the log: CargoSyncTask started\n", "CargoSyncTask started").expect_err("no time");
    assert!(
        format!("{error:#}")
            .starts_with("line 1: at the start of the log: CargoSyncTask started: the time `at the start of the log` is not"),
        "{error:#}"
    );
    let error = first_line(
        "2026-10-22 16:53:22,190 [ abc]   INFO - CargoSyncTask started\n",
        "CargoSyncTask started",
    )
    .expect_err("no column");
    assert!(
        format!("{error:#}").contains("the column `[abc]` is not a number of milliseconds"),
        "{error:#}"
    );
}
