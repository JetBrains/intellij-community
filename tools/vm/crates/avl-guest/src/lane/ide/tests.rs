use std::fs;
use std::path::Path;

use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

const BRIDGE: &str = "air-integrationTests-bridge-plugin";
const RESIDENT: &str = "air-integrationTests-bridge-resident-plugin";

/// A plugin file `destination` whose bytes are `content`, in `sources`.
fn plugin(sources: &Path, destination: &str, content: &str) -> ContextPlugin {
    let source = sources.join(destination.replace('/', "_"));
    fs::write(&source, content).unwrap();
    ContextPlugin {
        destination: destination.to_owned(),
        sha256: sha256(content),
        source,
    }
}

fn sha256(content: &str) -> String {
    super::super::package::sha256_hex(content.as_bytes())
}

/// The relative path of every file under `root`, sorted.
fn tree(root: &Path) -> Vec<String> {
    let mut files: Vec<String> = walkdir::WalkDir::new(root)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .map(|entry| entry.path().strip_prefix(root).unwrap().display().to_string())
        .collect();
    files.sort();
    files
}

// The manifest is one `<sha256> <destination>` line per file in destination order, the shape that the resident plugin
// reads for the digest of the bridge.
#[test]
fn the_manifest_lists_each_file_in_destination_order() {
    let sources = TempDir::new().unwrap();
    let files = [
        plugin(sources.path(), &format!("{RESIDENT}/lib/r.jar"), "r"),
        plugin(sources.path(), &format!("{BRIDGE}/lib/b.jar"), "b"),
    ];
    assert_eq!(
        manifest(&files),
        format!("{} {BRIDGE}/lib/b.jar\n{} {RESIDENT}/lib/r.jar\n", sha256("b"), sha256("r"))
    );
    assert_eq!(manifest(&[]), "");
}

// An install writes each plugin directory and the manifest last. A second install of other files replaces the changed
// directory and removes the one the new files do not name, and an install of the same files touches nothing.
#[test]
fn an_install_replaces_the_plugin_directories_and_writes_the_manifest_last() {
    let sources = TempDir::new().unwrap();
    let plugins = TempDir::new().unwrap();
    let first = [
        plugin(sources.path(), &format!("{BRIDGE}/lib/b.jar"), "b1"),
        plugin(sources.path(), &format!("{RESIDENT}/lib/r.jar"), "r"),
        plugin(sources.path(), "air-extra-plugin/lib/x.jar", "x"),
    ];
    install_plugins(plugins.path(), &first, &[]).unwrap();
    assert_eq!(
        tree(plugins.path()),
        [
            MANIFEST_FILE.to_owned(),
            "air-extra-plugin/lib/x.jar".to_owned(),
            format!("{BRIDGE}/lib/b.jar"),
            format!("{RESIDENT}/lib/r.jar"),
        ]
    );
    assert_eq!(installed_manifest(plugins.path()), manifest(&first));
    let second = [
        plugin(sources.path(), &format!("{BRIDGE}/lib/b.jar"), "b2"),
        plugin(sources.path(), &format!("{RESIDENT}/lib/r.jar"), "r"),
    ];
    install_plugins(plugins.path(), &second, &[]).unwrap();
    assert_eq!(
        tree(plugins.path()),
        [
            MANIFEST_FILE.to_owned(),
            format!("{BRIDGE}/lib/b.jar"),
            format!("{RESIDENT}/lib/r.jar")
        ]
    );
    assert_eq!(fs::read_to_string(plugins.path().join(BRIDGE).join("lib/b.jar")).unwrap(), "b2");
    // A file that no install wrote stays, because the owner removes only what a manifest named.
    fs::write(plugins.path().join("foreign.txt"), "kept").unwrap();
    install_plugins(plugins.path(), &second, &[]).unwrap();
    assert!(plugins.path().join("foreign.txt").is_file());
}

// The lane drives the IDE through the bridge and its resident part, so a set without either is refused, and so is a
// plugin that the distribution also carries.
#[test]
fn an_install_refuses_a_missing_bridge_and_a_doubled_plugin() {
    let sources = TempDir::new().unwrap();
    let plugins = TempDir::new().unwrap();
    let bridge_only = [plugin(sources.path(), &format!("{BRIDGE}/lib/b.jar"), "b")];
    let refusal = install_plugins(plugins.path(), &bridge_only, &[]).unwrap_err();
    assert!(refusal.message.contains(RESIDENT), "{}", refusal.message);
    let both = [
        plugin(sources.path(), &format!("{BRIDGE}/lib/b.jar"), "b"),
        plugin(sources.path(), &format!("{RESIDENT}/lib/r.jar"), "r"),
    ];
    let refusal = install_plugins(plugins.path(), &both, &[BRIDGE.to_owned()]).unwrap_err();
    assert!(refusal.message.contains("load them twice"), "{}", refusal.message);
    assert_eq!(tree(plugins.path()), Vec::<String>::new(), "a refused install writes nothing");
}

// A launch name is a supervisor run name: the base, then the milliseconds in base 36.
#[test]
fn a_launch_name_is_the_base_and_the_time_in_base_36() {
    assert_eq!(base36(0), "0");
    assert_eq!(base36(35), "z");
    assert_eq!(base36(36), "10");
    assert_eq!(base36(1_790_000_000_000), "mubbs7i8");
    let name = launch_name("recycle-2");
    assert!(name.starts_with("recycle-2-"), "{name}");
    assert!(wire_ide::is_launch_name(&name), "{name}");
}

// A bare IDE is the IDE of a user, so it keeps the plugin set of the product. The IDE of the lane disables the plugins
// of the lane document, and the one of the test document is AI Assistant.
#[test]
fn a_bare_context_disables_no_plugin_and_a_lane_context_disables_those_of_the_document() {
    let bytes = fs::read(avl_testkit::crate_path!("testdata/lane-context/ui.json")).unwrap();
    let document = avl_wire::program::lane::parse_lane_document(&bytes).unwrap();
    let inputs = LaunchInputs {
        ide_root: "/vm/data/ide".into(),
        home: "/vm/home".into(),
        dist_home: "/d".into(),
        ide_config: "/d.properties".into(),
        java_home: "/d/jbr".into(),
        flags_file: "/f".into(),
        project_archive: "/p.zip".into(),
        project_root: "plain-project".to_owned(),
        runtimes: Vec::new(),
        distribution_plugins: std::collections::BTreeSet::new(),
        trace: crate::lane::inputs::TraceInputs::default(),
    };
    let lane = fresh_context(&inputs, "air-ui-lane", &document, false);
    assert_eq!(lane.disabled_plugin_ids, ["com.intellij.ml.llm"]);
    let bare = fresh_context(&inputs, "air-startup", &document, true);
    assert_eq!(bare.disabled_plugin_ids, Vec::<String>::new());
    assert_eq!(bare.launch_key, "air-startup");
    assert_eq!(bare.ide_root, lane.ide_root);
    assert_eq!(bare.project.archive, "/p.zip");
}

// A bare IDE starts as the IDE of a user: the stated defaults of the lane without the internal mode, and without the
// properties of the requirements, such as the workflows and the debug server of the lane. The IDE of the lane gets
// both.
#[test]
fn a_bare_ide_gets_the_stated_defaults_without_the_internal_mode_or_the_requirements() {
    let bytes = fs::read(avl_testkit::crate_path!("testdata/lane-context/ui.json")).unwrap();
    let document = avl_wire::program::lane::parse_lane_document(&bytes).unwrap();
    let host = LaunchHost {
        linux: true,
        windows: false,
        team_city: false,
    };
    let lane = document_properties(&document, host, false);
    let bare = document_properties(&document, host, true);
    assert!(lane.contains(&("idea.is.internal", "true")), "{lane:?}");
    assert!(lane.contains(&("air.workflows", "true")), "{lane:?}");
    let expected: Vec<(&str, &str)> = document
        .stated_launch_properties_on(host)
        .into_iter()
        .filter(|(key, _)| *key != "idea.is.internal")
        .collect();
    assert_eq!(bare, expected);
    assert!(
        bare.iter()
            .all(|(key, _)| !matches!(*key, "idea.is.internal" | "air.workflows" | "air.acp.debug.server.enabled")),
        "{bare:?}"
    );
    assert!(!bare.is_empty());
}

// A recycle removes the editor tabs that the start would reopen, and the run log names the file. A `restart-ide` keeps
// them, because its scenario tests what a restart restores.
#[test]
fn a_recycle_removes_the_restored_tabs_and_a_restart_keeps_them() {
    let config = TempDir::new().unwrap();
    let workspace = config.path().join(super::super::context::editors::PRODUCT_WORKSPACE_DIR);
    fs::create_dir_all(&workspace).unwrap();
    let file = workspace.join("project.xml");
    let tabs = "<project version=\"4\">\n  <component name=\"FileEditorManager\">\n    <leaf />\n  </component>\n</project>\n";
    fs::write(&file, tabs).unwrap();
    let relaunch = |cause: Relaunch| {
        let mut lines = Vec::new();
        before_relaunch(
            cause,
            config.path(),
            &mut Records(&mut |record| {
                if let RunEventKind::Output(output) = record {
                    lines.push(output.text);
                }
            }),
        )
        .unwrap();
        lines
    };

    assert!(Relaunch::Restart.restores_editors());
    assert_eq!(relaunch(Relaunch::Restart), Vec::<String>::new());
    assert_eq!(fs::read_to_string(&file).unwrap(), tabs);

    assert!(!Relaunch::Recycle.restores_editors());
    assert_eq!(
        relaunch(Relaunch::Recycle),
        [format!(
            "removed the restored editor tabs (DockManager, FileEditorManager) from {}",
            file.display()
        )]
    );
    assert_eq!(fs::read_to_string(&file).unwrap(), "<project version=\"4\">\n</project>\n");
}

/// The inputs of an owner whose IDE root is `root`, with no file behind them.
fn owner_inputs(root: &Path) -> LaunchInputs {
    LaunchInputs {
        ide_root: root.join("ide"),
        home: root.join("home"),
        dist_home: root.join("dist"),
        ide_config: root.join("dist.properties"),
        java_home: root.join("dist/jbr"),
        flags_file: root.join("flags.txt"),
        project_archive: root.join("project.zip"),
        project_root: "plain-project".to_owned(),
        runtimes: Vec::new(),
        distribution_plugins: std::collections::BTreeSet::new(),
        trace: crate::lane::inputs::TraceInputs::default(),
    }
}

/// A held IDE of the context `context`, whose launch `launch_name` has no run on disk, so a quit finds it ended.
fn held_ide(context: &Path, launch_name: &str) -> HeldIde {
    let path = |name: &str| context.join(name).display().to_string();
    HeldIde {
        key: "air-ui-lane".to_owned(),
        token: "token".to_owned(),
        paths: IdePrepared {
            context_dir: context.display().to_string(),
            config_dir: path("config"),
            system_dir: path("system"),
            plugins_dir: path("plugins"),
            project_dir: path("project"),
            home_dir: path("home"),
            bin_dir: path("bin"),
        },
        launch_name: launch_name.to_owned(),
        pid: None,
        relaunches: 0,
        exit_reported: true,
        bare: false,
    }
}

// The watchdog kills the run that the owner published last, through a handle that it reads without the lock of the
// owner. A launch publishes its run, and a quit clears it. So a kill during a relaunch reaches the run that the
// relaunch launched, and a kill between the quit and the launch kills no run.
#[test]
fn the_kill_target_is_the_run_of_the_last_launch_and_a_quit_clears_it() {
    let root = TempDir::new().unwrap();
    let context = root.path().join("ide/air-ui-lane");
    let mut owner = IdeOwner::new(owner_inputs(root.path()));
    let target = owner.kill_target();
    assert_eq!(target.current(), None, "an owner with no IDE publishes no target");

    // What start_run does after the launch of the first run returns.
    owner.held = Some(held_ide(&context, "start-mve37si6"));
    owner.publish_target();
    let run_of = |target: &KillTarget| target.current().map(|(directory, run_id, _)| (directory, run_id));
    assert_eq!(run_of(&target), Some((context.clone(), "run-ide-start-mve37si6".to_owned())));

    // The relaunch quits the first run, and nothing is held until the next launch returns.
    owner.quit(&mut Records(&mut |_| {})).unwrap();
    assert_eq!(target.current(), None, "a quit leaves the run it ended as the target");

    owner.held = Some(held_ide(&context, "restart-1-mve37si7"));
    owner.publish_target();
    assert_eq!(run_of(&target), Some((context, "run-ide-restart-1-mve37si7".to_owned())));
}
