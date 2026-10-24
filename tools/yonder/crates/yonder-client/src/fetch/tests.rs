use pretty_assertions::assert_eq;

use super::*;

fn fetch() -> Vec<String> {
    vec!["out/bt-runs".to_owned()]
}

#[test]
fn a_fetched_path_stays_below_a_fetch_directory_of_the_checkout() {
    let fetch = fetch();
    let root = Path::new("/c");
    assert_eq!(
        local_target(root, "out/bt-runs/1/a.log", &fetch).expect("a staged file"),
        root.join("out").join("bt-runs").join("1").join("a.log")
    );
    for path in [
        "/etc/passwd",
        "out/bt-runs/../x",
        "out/bt-runs",
        "out/other/a",
        "out//bt-runs/a",
        "out/bt-runs/c:\\x",
        "out/bt-runs/1/a.log.yonder-part",
    ] {
        local_target(root, path, &fetch).expect_err(path);
    }
}

/// `yonder fetch` names no directory: any plain relative path goes below the checkout.
#[test]
fn without_fetch_directories_any_plain_path_is_accepted() {
    let root = Path::new("/c");
    assert_eq!(
        local_target(root, "a/b.txt", &[]).expect("a plain path"),
        root.join("a").join("b.txt")
    );
    local_target(root, "../x", &[]).expect_err("not plain");
}

#[test]
fn a_part_file_is_named_after_its_target() {
    assert_eq!(part_path(Path::new("/c/a.log")), Path::new("/c/a.log.yonder-part"));
}
