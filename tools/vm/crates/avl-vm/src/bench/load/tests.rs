use pretty_assertions::assert_eq;

use avl_base::Exit;

use super::{HostLoad, check_max, parse_averages};

#[test]
fn reads_the_averages_of_sysctl_and_of_proc() {
    assert_eq!(parse_averages("{ 27.10 25.47 25.42 }\n"), Some((27.1, 25.47, 25.42)));
    assert_eq!(parse_averages("0.52 0.61 0.70 1/123 4567\n"), Some((0.52, 0.61, 0.7)));
    assert_eq!(parse_averages("{ 1.0 x 2.0 }"), None);
    assert_eq!(parse_averages(""), None);
}

#[test]
fn a_load_above_the_cpu_count_is_saturated() {
    let load = |one| HostLoad {
        one,
        five: 0.0,
        fifteen: 0.0,
        cpus: 16,
    };
    assert!(load(27.1).saturated());
    assert!(!load(16.0).saturated());
}

#[test]
fn a_load_above_the_max_load_is_the_refusal_host_busy() {
    let load = HostLoad {
        one: 28.44,
        five: 30.0,
        fifteen: 25.0,
        cpus: 18,
    };
    let refusal = check_max(Some(&load), Some(10.0)).expect_err("a busy host");
    assert_eq!(refusal.code, "host_busy");
    assert_eq!(refusal.exit, Exit::TEMP_FAIL);
    assert_eq!(
        refusal.message,
        "the 1-minute load is 28.4 on 18 CPUs, above --max-load 10; wait for the host to settle"
    );
    assert_eq!(check_max(Some(&load), Some(28.44)), Ok(Some(28.44)), "a load at the limit passes");
    assert_eq!(
        check_max(None, Some(0.001)),
        Ok(None),
        "a host without a load average passes unchecked"
    );
}

#[test]
fn without_a_max_load_the_limit_is_the_cpu_count() {
    let load = |one| HostLoad {
        one,
        five: 0.0,
        fifteen: 0.0,
        cpus: 18,
    };
    let refusal = check_max(Some(&load(28.44)), None).expect_err("a saturated host");
    assert_eq!(refusal.code, "host_busy");
    assert_eq!(
        refusal.message,
        "the 1-minute load is 28.4 on 18 CPUs, above the CPU count; wait for the host to settle, or set another limit with --max-load"
    );
    assert!(load(28.44).saturated(), "the refusal and the digest warning share one limit");
    assert_eq!(check_max(Some(&load(18.0)), None), Ok(Some(18.0)), "a load at the CPU count passes");
    assert!(!load(18.0).saturated());
    assert_eq!(
        check_max(Some(&load(28.44)), Some(30.0)),
        Ok(Some(30.0)),
        "--max-load sets another limit"
    );
    assert_eq!(check_max(None, None), Ok(None), "a host without a load average passes unchecked");
}

/// An unknown CPU count, 0, gives no default limit: the session passes and records no limit. `--max-load` still
/// applies.
#[test]
fn an_unknown_cpu_count_gives_no_default_limit() {
    let load = HostLoad {
        one: 28.44,
        five: 0.0,
        fifteen: 0.0,
        cpus: 0,
    };
    assert_eq!(check_max(Some(&load), None), Ok(None));
    assert!(!load.saturated());
    let refusal = check_max(Some(&load), Some(10.0)).expect_err("a busy host");
    assert_eq!(
        refusal.message,
        "the 1-minute load is 28.4, above --max-load 10; wait for the host to settle"
    );
}
