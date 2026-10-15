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
