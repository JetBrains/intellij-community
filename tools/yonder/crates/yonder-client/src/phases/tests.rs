use pretty_assertions::assert_eq;

use super::*;

fn step(name: &str, ms: u64) -> Phase {
    Phase { name: name.to_owned(), ms }
}

#[test]
fn a_duration_reads_in_seconds_then_minutes_then_hours() {
    assert_eq!(duration_text(0), "0.0s");
    assert_eq!(duration_text(3149), "3.1s");
    assert_eq!(duration_text(41_000), "41.0s");
    assert_eq!(duration_text(59_949), "59.9s");
    assert_eq!(duration_text(59_950), "1m00s");
    assert_eq!(duration_text(542_000), "9m02s");
    assert_eq!(duration_text(3_599_499), "59m59s");
    assert_eq!(duration_text(3_599_500), "1h00m00s");
    assert_eq!(duration_text(3_909_000), "1h05m09s");
}

#[test]
fn the_line_names_each_step_in_its_order() {
    let phases = [
        step("pack", 3100),
        step("upload", 2000),
        step("lock", 0),
        step("lease", 542_000),
        step("command", 25_100),
        step("fetch", 400),
        step("total", 3_909_000),
    ];
    assert_eq!(
        phases_line(&phases),
        "yonder: phases: pack 3.1s, upload 2.0s, lock 0.0s, lease 9m02s, command 25.1s, fetch 0.4s, total 1h05m09s"
    );
    assert_eq!(
        phases_short(&phases),
        "pack 3.1s, upload 2.0s, lease 9m02s, command 25.1s, fetch 0.4s, total 1h05m09s"
    );
    assert_eq!(phases_short(&[step("lock", 99)]), "");
}
