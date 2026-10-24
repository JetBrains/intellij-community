use super::*;

#[test]
fn a_fetch_directory_stays_inside_the_slot() {
    assert!(is_inner_path("out/bt-runs"));
    assert!(is_inner_path("out"));
    assert!(!is_inner_path(""));
    assert!(!is_inner_path("/out"));
    assert!(!is_inner_path("out/../.."));
    assert!(!is_inner_path("./out"));
}

#[test]
fn a_holder_names_a_lease_file() {
    check_holder("yonder-0123456789ab").expect("a holder");
    assert_eq!(check_holder("../x").expect_err("refused").code, BAD_REQUEST);
    assert_eq!(check_holder("").expect_err("refused").code, BAD_REQUEST);
}
