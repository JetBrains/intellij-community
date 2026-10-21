use std::fs;
use std::path::PathBuf;

use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

/// The id of the product workspace file of the fixture project.
const WORKSPACE_ID: &str = "2xNGZqRjrRwMqQUkwz4zEXmCQ9S";

fn testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/lane-context/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// A config directory whose product workspace directory holds `files`, each a name and its text.
fn config(files: &[(&str, &str)]) -> TempDir {
    let config = TempDir::new().unwrap();
    let directory = config.path().join(PRODUCT_WORKSPACE_DIR);
    fs::create_dir_all(&directory).unwrap();
    for (name, text) in files {
        fs::write(directory.join(name), text).unwrap();
    }
    config
}

// The fixture is a product workspace file as the IDE writes it after a scenario that left a Session View tab open, in
// the main window and in a window of the dock. The clear removes both components, keeps the tool window layout and every
// other component, and keeps the layout of the file.
#[test]
fn the_clear_removes_the_restored_tabs_and_keeps_every_other_component() {
    let fixture = fs::read_to_string(testdata("product-workspace.xml")).unwrap();
    let name = format!("{WORKSPACE_ID}.xml");
    let config = config(&[(&name, &fixture)]);
    let file = config.path().join(PRODUCT_WORKSPACE_DIR).join(&name);

    assert_eq!(clear_restored_editors(config.path()).unwrap(), [Cleared::Components(file.clone())]);

    let cleared = fs::read_to_string(&file).unwrap();
    expect_test::expect_file![testdata("product-workspace-cleared.xml")].assert_eq(&cleared);
    assert!(!cleared.contains("agent-thread-view://"), "{cleared}");
    assert_eq!(clear_restored_editors(config.path()).unwrap(), []);
}

// A file without restored tabs stays byte-equal, a file that is no XML file of the directory is not read, and a config
// directory that never held a project has nothing to clear.
#[test]
fn a_file_without_restored_tabs_stays_and_a_config_without_projects_has_nothing_to_clear() {
    let layout = "<project version=\"4\">\n  <component name=\"ToolWindowManager\" />\n</project>\n";
    let config = config(&[("one.xml", layout), ("notes.txt", "<component name=\"FileEditorManager\" />")]);

    assert_eq!(clear_restored_editors(config.path()).unwrap(), []);
    let directory = config.path().join(PRODUCT_WORKSPACE_DIR);
    assert_eq!(fs::read_to_string(directory.join("one.xml")).unwrap(), layout);
    assert_eq!(
        fs::read_to_string(directory.join("notes.txt")).unwrap(),
        "<component name=\"FileEditorManager\" />"
    );
    assert_eq!(clear_restored_editors(TempDir::new().unwrap().path()).unwrap(), []);
}

// A file whose component does not close, as a write cut by a kill would leave it, goes as a whole: the platform reads no
// state from it either, and no tab of it reopens.
#[test]
fn a_file_with_a_component_that_does_not_close_goes_as_a_whole() {
    let cut = "<project version=\"4\">\n  <component name=\"FileEditorManager\">\n    <leaf>\n";
    let config = config(&[("cut.xml", cut)]);
    let file = config.path().join(PRODUCT_WORKSPACE_DIR).join("cut.xml");

    let cleared = clear_restored_editors(config.path()).unwrap();

    assert_eq!(
        cleared,
        [Cleared::File {
            file: file.clone(),
            reason: "the component element at byte 24 does not close".to_owned(),
        }]
    );
    assert!(!file.exists());
    assert_eq!(
        cleared[0].note(),
        format!(
            "removed {}, because the component element at byte 24 does not close",
            file.display()
        )
    );
}

// An empty component element goes with its line. A component inside a component and a tag that only starts with the word
// are no top-level component of the names. A component that shares its line keeps the line.
#[test]
fn only_a_top_level_component_of_the_names_goes() {
    let xml = "<project>\n  <component name=\"DockManager\" />\n  <component name=\"Other\">\n    <component name=\"FileEditorManager\"><leaf /></component>\n    <componentX name=\"FileEditorManager\" />\n  </component>\n  <a /><component name=\"FileEditorManager\"><leaf /></component><b />\n</project>\n";

    let kept = without_components(xml, &RESTORED_EDITOR_COMPONENTS).unwrap().unwrap();

    assert_eq!(
        kept,
        "<project>\n  <component name=\"Other\">\n    <component name=\"FileEditorManager\"><leaf /></component>\n    <componentX name=\"FileEditorManager\" />\n  </component>\n  <a /><b />\n</project>\n"
    );
    assert_eq!(without_components("<project />\n", &RESTORED_EDITOR_COMPONENTS), Ok(None));
}
