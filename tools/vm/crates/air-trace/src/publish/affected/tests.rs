use std::path::PathBuf;

use bt_core::fake::{FakeRuntime, area, flow_catalog_tree, kdoc};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;

/// The changed files of three commits and of the patch of a personal build, as TeamCity writes them.
const CHANGED_FILES_TEXT: &str = "\
plugins/air/frontend/src/Rows.kt:CHANGED:1111111111111111111111111111111111111111
community/tools/vm/crates/avl-vm/src/lane.rs:CHANGED:2222222222222222222222222222222222222222
plugins/air/frontend/src/Plain.kt:ADDED:2222222222222222222222222222222222222222

plugins/air/frontend/src/Gone.kt:REMOVED:3333333333333333333333333333333333333333
plugins/air/frontend/src/Rows.kt:CHANGED:<personal>
";

fn changed(path: &str, change: &str, revision: &str) -> ChangedFile {
    ChangedFile {
        path: path.to_owned(),
        change: change.to_owned(),
        revision: revision.to_owned(),
    }
}

/// Each line is `<path>:<change type>:<revision>`, a blank line is skipped, and a colon of a path stays in the path.
/// A personal build writes `<personal>` for the revision.
#[test]
fn the_changed_files_read_as_team_city_writes_them() {
    let files = parse_changed_files(CHANGED_FILES_TEXT).unwrap();
    assert_eq!(files.len(), 5);
    assert_eq!(
        files[0],
        changed(
            "plugins/air/frontend/src/Rows.kt",
            "CHANGED",
            "1111111111111111111111111111111111111111"
        )
    );
    assert_eq!(files[4], changed("plugins/air/frontend/src/Rows.kt", "CHANGED", "<personal>"));
    assert_eq!(
        parse_changed_files("docs/a:b.md:ADDED:abc\n").unwrap(),
        [changed("docs/a:b.md", "ADDED", "abc")]
    );
    assert_eq!(parse_changed_files("\n").unwrap(), []);
}

/// A line that is not `<path>:<change type>:<revision>` refuses the file, with its number.
#[test]
fn a_line_of_another_format_refuses_the_file() {
    assert_eq!(
        parse_changed_files("a.kt:CHANGED:abc\nb.kt:CHANGED\n"),
        Err("line 2 is not <path>:<change type>:<revision>: b.kt:CHANGED".to_owned())
    );
    assert_eq!(
        parse_changed_files(":CHANGED:abc\n"),
        Err("line 1 is not <path>:<change type>:<revision>: :CHANGED:abc".to_owned())
    );
}

/// The fixture catalog of the join, with a file of the controller, which is a lane-wide path of every lane.
fn checkout() -> FakeRuntime {
    let fake = FakeRuntime::with_tree(&flow_catalog_tree());
    fake.put("community/tools/vm/crates/avl-vm/src/lane.rs", "//! the lane\n");
    fake
}

/// Each commit names its files, the suites that they reach with the reasons of the join, and the files that reach no
/// suite. A file with a flow tag reaches the suites of its flow. A file of the controller reaches every suite as the
/// harness. A file with no tag is unmapped, and so is a file that the checkout no longer holds. The commits keep the
/// order of the file, and the patch of a personal build is a commit of its own.
#[test]
fn each_commit_names_the_suites_that_its_files_reach() {
    let files = parse_changed_files(CHANGED_FILES_TEXT).unwrap();
    let document = affected(&checkout(), area(), "4444444444444444444444444444444444444444", &files).unwrap();
    let launch_preset = |via: &[&str]| {
        [
            json!({
                "suite": "manage-launch-preset",
                "testClassName": "AirManageLaunchPresetGeneratedFlowUiTest",
                "lane": "GUI_CHAT",
                "via": via,
            }),
            json!({
                "suite": "manage-launch-preset-quick-start",
                "testClassName": "AirManageLaunchPresetQuickStartGeneratedFlowUiTest",
                "lane": "UI",
                "via": via,
            }),
        ]
    };
    let [gui_chat, ui] = launch_preset(&["flow:flow-manage-launch-preset"]);
    let [harness_gui_chat, harness_ui] = launch_preset(&["lane-wide:community/tools/vm"]);
    let rows = "plugins/air/frontend/src/Rows.kt";
    assert_eq!(
        serde_json::to_value(&document).unwrap(),
        json!({
            "schema": 1,
            "revision": "4444444444444444444444444444444444444444",
            "commits": [
                {
                    "revision": "1111111111111111111111111111111111111111",
                    "paths": [{"path": rows, "change": "CHANGED"}],
                    "reached": [gui_chat, ui],
                    "unmapped": [],
                },
                {
                    "revision": "2222222222222222222222222222222222222222",
                    "paths": [
                        {"path": "community/tools/vm/crates/avl-vm/src/lane.rs", "change": "CHANGED"},
                        {"path": "plugins/air/frontend/src/Plain.kt", "change": "ADDED"},
                    ],
                    "reached": [
                        harness_gui_chat,
                        harness_ui,
                        {
                            "suite": "new-session-terminal",
                            "testClassName": "AirNewSessionTerminalGeneratedFlowUiTest",
                            "lane": "UI_REAL",
                            "via": ["lane-wide:community/tools/vm"],
                        },
                    ],
                    "unmapped": [{"path": "plugins/air/frontend/src/Plain.kt", "reason": "no_flow_tag"}],
                },
                {
                    "revision": "3333333333333333333333333333333333333333",
                    "paths": [{"path": "plugins/air/frontend/src/Gone.kt", "change": "REMOVED"}],
                    "reached": [],
                    "unmapped": [{"path": "plugins/air/frontend/src/Gone.kt", "reason": "path_unreadable"}],
                },
                {
                    "revision": "<personal>",
                    "paths": [{"path": rows, "change": "CHANGED"}],
                    "reached": launch_preset(&["flow:flow-manage-launch-preset"]),
                    "unmapped": [],
                },
            ],
        })
    );
}

/// An unmapped path keeps the flow ids of its reason.
#[test]
fn an_unmapped_path_keeps_the_flows_of_its_reason() {
    let fake = checkout();
    let launch = "plugins/air/frontend/src/Launch.kt";
    fake.put(launch, &kdoc(&["@flow flow-new-session-launch"]));
    let document = affected(&fake, area(), "4444", &[changed(launch, "CHANGED", "1111")]).unwrap();
    assert_eq!(
        serde_json::to_value(&document.commits[0].unmapped).unwrap(),
        json!([{"path": launch, "reason": "flow_declares_no_suite", "flows": ["flow-new-session-launch"]}])
    );
}

/// A build of a TeamCity server, with the file of its changed files.
fn build(changed_files: Option<PathBuf>, revision: Option<&str>) -> Build {
    Build {
        server: "https://teamcity.example".to_owned(),
        build_type_id: "Air_UiTests_1".to_owned(),
        id: "1100310296".to_owned(),
        name: "Air Plugin UI Tests / 1".to_owned(),
        revision: revision.map(str::to_owned),
        changed_files,
        checkout_dir: None,
    }
}

/// A build without changed files has no document. A build that does not name the file, a file that is not there, a
/// file of another format and a build without a revision say why there is no document.
#[test]
fn a_build_without_its_changed_files_says_why_it_has_no_document() {
    let dir = tempfile::tempdir().unwrap();
    let revision = Some("4444");
    let no_document = "so the archive holds no affected.json";
    assert_eq!(
        of_build(&build(None, revision), None, None),
        Err(format!(
            "the build properties name no teamcity.build.changedFiles.file, which TeamCity leaves out for a build without \
             changes; {no_document}"
        ))
    );

    let missing = dir.path().join("teamcity.changedFiles.txt");
    assert_eq!(
        of_build(&build(Some(missing.clone()), revision), None, None),
        Err(format!(
            "teamcity.build.changedFiles.file {} is not there, as for a build without changes; {no_document}",
            missing.display()
        ))
    );

    fs::write(&missing, "\n").unwrap();
    assert_eq!(of_build(&build(Some(missing.clone()), revision), None, None), Ok(None));

    fs::write(&missing, "a.kt\n").unwrap();
    assert_eq!(
        of_build(&build(Some(missing.clone()), revision), None, None),
        Err(format!(
            "teamcity.build.changedFiles.file {}: line 1 is not <path>:<change type>:<revision>: a.kt; {no_document}",
            missing.display()
        ))
    );

    fs::write(&missing, "a.kt:CHANGED:1111\n").unwrap();
    assert_eq!(
        of_build(&build(Some(missing.clone()), None), None, None),
        Err(format!("the build properties name no build.vcs.number; {no_document}"))
    );
    assert_eq!(
        of_build(&build(Some(missing), revision), None, None),
        Err(format!(
            "no --repo, no teamcity.build.checkoutDir and no BUILD_WORKSPACE_DIRECTORY name the checkout; {no_document}"
        ))
    );
}
