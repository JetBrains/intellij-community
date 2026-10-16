use super::*;

#[test]
fn this_host_answers_more_than_one_gib() {
    let mib = memory_mib().expect("the memory probe of this host answers");
    assert!(mib > 1024, "{mib} MiB");
}

#[test]
fn meminfo_total_is_kib() {
    let meminfo = "MemTotal:       131072000 kB\nMemFree:          1024 kB\n";
    assert_eq!(meminfo_total_bytes(meminfo), Some(131_072_000 * 1024));
}

#[test]
fn meminfo_without_a_total_answers_none() {
    assert_eq!(meminfo_total_bytes("MemFree: 1024 kB\n"), None);
    assert_eq!(meminfo_total_bytes("MemTotal: many kB\n"), None);
}
