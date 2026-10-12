use avl_host_testkit::prose;

use super::*;

/// The viewer names a bundle by the real path of its zip, so a pulled zip is named by its real path. A path that
/// does not resolve stays as written, and a note names it, because its trace links can then miss.
#[test]
fn a_pulled_zip_is_named_by_its_real_path_or_noted() {
    let (reporter, stderr) = prose();
    let scope = Scope::worker("air-docker-1");
    let directory = tempfile::tempdir().unwrap();
    let zip = directory.path().join("traces.zip");
    std::fs::write(&zip, b"zip").unwrap();
    let real = fscopy::resolve_links(&zip).unwrap();
    assert_eq!(real_zip_path(zip, &reporter, &scope), real);
    assert!(stderr.is_empty(), "a resolved path said {:?}", stderr.text());

    let missing = directory.path().join("missing.zip");
    assert_eq!(real_zip_path(missing.clone(), &reporter, &scope), missing);
    let said = stderr.text();
    let note = format!("cannot resolve the real path of the pulled traces {}: ", missing.display());
    assert!(said.contains(&note), "the unresolved path was not noted: {said:?}");
}
