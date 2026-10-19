use std::fs;
use std::path::Path;

use avl_wire::ide::IdePrepared;
use avl_wire::supervisor::AgentExit;
use pretty_assertions::assert_eq;

use super::*;
use crate::ide::CODE_IDE_RUNNING;
use crate::ide::fixture::{FakeIde, Fixture, mode, prepared, wait_for_file};
use crate::supervisor::LiveSystem;

// A fresh preparation lays out the context, each directory private, unpacks the project and writes the disabled
// plugins. It writes no argument file and no launch record: the launch does.
#[test]
fn a_fresh_preparation_lays_out_the_context() {
    let fixture = Fixture::new();
    let answer = prepared(&fixture.context_document(true));
    let context = fixture.text("ide/key-1");
    assert_eq!(
        answer,
        IdePrepared {
            context_dir: context.clone(),
            config_dir: format!("{context}/config"),
            system_dir: format!("{context}/system"),
            plugins_dir: format!("{context}/plugins"),
            project_dir: format!("{context}/project/BookmarksTestProject"),
            home_dir: format!("{context}/home"),
            bin_dir: format!("{context}/bin"),
        }
    );
    for directory in [
        &answer.config_dir,
        &answer.system_dir,
        &answer.plugins_dir,
        &answer.home_dir,
        &answer.bin_dir,
        &format!("{context}/project"),
    ] {
        assert_eq!(mode(directory), 0o700, "{directory}");
    }
    assert_eq!(
        fs::read_to_string(format!("{}/src/Main.java", answer.project_dir)).unwrap(),
        "class Main {}\n"
    );
    assert_eq!(
        fs::read_to_string(format!("{}/disabled_plugins.txt", answer.config_dir)).unwrap(),
        "com.intellij.copyright\norg.jetbrains.junie\n"
    );
    for file in ["ide-jvm.args", "launch.json", "log"] {
        assert!(!Path::new(&format!("{context}/{file}")).exists(), "{file}");
    }
}

// A relaunch keeps the project, the home and the bin directory, as an IDE restart keeps them. A fresh preparation
// deletes the data directories, the home, the bin directory, the argument file and the launch record, and unpacks
// the project again. It keeps the logs of earlier launches.
#[test]
fn a_relaunch_keeps_the_project_and_a_fresh_preparation_unpacks_it_again() {
    let fixture = Fixture::new();
    let paths = prepared(&fixture.context_document(true));
    let edit = format!("{}/edited.txt", paths.project_dir);
    fs::write(&edit, "kept").unwrap();
    let seeded_home = format!("{}/.codex/config.toml", paths.home_dir);
    let launcher = format!("{}/claude", paths.bin_dir);
    for file in [&seeded_home, &launcher] {
        fs::create_dir_all(Path::new(file).parent().unwrap()).unwrap();
        fs::write(file, "seeded").unwrap();
    }

    let mut relaunch = fixture.context_document(false);
    relaunch.disabled_plugin_ids.clear();
    let relaunched = prepared(&relaunch);
    assert_eq!(relaunched, paths);
    for file in [&edit, &seeded_home, &launcher] {
        assert!(Path::new(file).is_file(), "a relaunch deleted {file}");
    }
    assert!(!Path::new(&format!("{}/disabled_plugins.txt", relaunched.config_dir)).exists());

    let context = fixture.context();
    let config_marker = format!("{}/options/other.xml", relaunched.config_dir);
    let system_marker = format!("{}/caches/marker", relaunched.system_dir);
    let earlier_log = context.join("log/launch-1");
    fs::create_dir_all(&earlier_log).unwrap();
    for marker in [&config_marker, &system_marker] {
        fs::create_dir_all(Path::new(marker).parent().unwrap()).unwrap();
        fs::write(marker, "earlier").unwrap();
    }
    for file in ["ide-jvm.args", "launch.json"] {
        fs::write(context.join(file), "earlier").unwrap();
    }

    let fresh = prepared(&fixture.context_document(true));
    for file in [&edit, &config_marker, &system_marker, &seeded_home, &launcher] {
        assert!(!Path::new(file).exists(), "a fresh preparation kept {file}");
    }
    for file in ["ide-jvm.args", "launch.json"] {
        assert!(!context.join(file).exists(), "a fresh preparation kept {file}");
    }
    assert!(Path::new(&fresh.home_dir).is_dir() && Path::new(&fresh.bin_dir).is_dir());
    assert!(earlier_log.is_dir(), "a fresh preparation deleted an earlier log");
}

// A relocated project unpacks into its own root, and a fresh preparation replaces that root.
#[test]
fn a_relocated_project_unpacks_into_its_own_root() {
    let fixture = Fixture::new();
    let mut document = fixture.context_document(true);
    document.project.relocate_to = Some(fixture.text("live/project"));
    fs::create_dir_all(fixture.path("live/project/stale")).unwrap();
    let answer = prepared(&document);
    assert_eq!(answer.project_dir, fixture.text("live/project/BookmarksTestProject"));
    assert!(Path::new(&answer.project_dir).join("src/Main.java").is_file());
    assert!(!fixture.path("live/project/stale").exists());
    assert!(!fixture.path("ide/key-1/project/BookmarksTestProject").exists());
}

// The preparation checks its document itself, so the lane daemon that calls it gets the check too. A refused document
// lays out nothing.
#[test]
fn a_context_document_that_the_preparation_cannot_act_on_is_refused() {
    let fixture = Fixture::new();
    for id in ["", " ", "com.intellij.copyright\norg.jetbrains.junie"] {
        let mut document = fixture.context_document(true);
        document.disabled_plugin_ids = vec![id.to_owned()];
        let refusal = prepare(&LiveSystem, &document).unwrap_err();
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("guest_ide_prepare_failed", AgentExit::Refused),
            "{id:?}"
        );
        assert!(
            refusal.message.contains(&format!("{id:?} is not a plugin id")),
            "{}",
            refusal.message
        );
    }
    let mut document = fixture.context_document(true);
    document.project.archive_root = "../outside".to_owned();
    let refusal = prepare(&LiveSystem, &document).unwrap_err();
    assert!(refusal.message.contains("project.archiveRoot"), "{}", refusal.message);
    assert!(!fixture.context().exists(), "a refused preparation laid out its context");
}

#[test]
fn an_archive_without_the_project_directory_is_refused() {
    let fixture = Fixture::new();
    let mut document = fixture.context_document(true);
    document.project.archive_root = "OtherProject".to_owned();
    let refusal = prepare(&LiveSystem, &document).unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("guest_ide_prepare_failed", AgentExit::Refused)
    );
    assert!(refusal.message.contains("\"OtherProject\""), "{}", refusal.message);
}

// A context whose run slot holds a live IDE refuses a preparation, fresh or not, and keeps its files.
#[test]
fn a_context_with_a_live_ide_refuses_a_preparation() {
    let Some(ide) = FakeIde::new() else {
        return;
    };
    let (context, _) = ide.launch("launch-1", true);
    wait_for_file(&ide.tools.join("argv.txt"));
    let marker = format!("{}/options/other.xml", context.config_dir);
    fs::create_dir_all(Path::new(&marker).parent().unwrap()).unwrap();
    fs::write(&marker, "live").unwrap();
    for fresh in [false, true] {
        let refusal = prepare(&LiveSystem, &ide.fixture.context_document(fresh)).unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), (CODE_IDE_RUNNING, AgentExit::Refused));
        assert!(refusal.message.contains("run-ide-launch-1"), "{}", refusal.message);
    }
    assert!(Path::new(&marker).is_file(), "a refused preparation deleted a file of the live IDE");
    ide.gc(true, None, 5);
}
