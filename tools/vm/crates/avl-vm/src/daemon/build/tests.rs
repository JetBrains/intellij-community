use std::fs;
use std::os::unix::fs::PermissionsExt;

use avl_host_testkit::git::FAKE_HEAD;
use avl_host_testkit::refusal;
use avl_report::digest::FileDigestCache;
use avl_wire::runtime::RuntimeFile;
use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::testing::fixture_descriptor;

#[test]
fn declared_data_accepts_directories_but_classpath_inputs_do_not() {
    let root = tempfile::tempdir().unwrap();
    let package = root.path().join("_main/pi");
    fs::create_dir_all(&package).unwrap();
    let entry = package.join("cli.js");
    fs::write(&entry, "declared package").unwrap();
    let cache_dir = tempfile::tempdir().unwrap();
    let mut cache = FileDigestCache::open(&cache_dir.path().join("cache.json"));
    let file = RuntimeFile {
        exec_path: "bazel-out/bin/_main/pi".to_owned(),
        logical_path: "_main/pi".to_owned(),
        owner: "//pi:runtime".to_owned(),
    };

    let tree = HostRunfiles::Tree(root.path().to_owned());
    let initial = declared_input(&mut cache, &tree, &file, true).unwrap();
    let classpath = declared_input(&mut cache, &tree, &file, false);
    assert_eq!(
        refusal(classpath.map(|declared| declared.identity)).code,
        "daemon_runtime_input_missing",
        "a classpath input accepted a directory"
    );

    fs::write(&entry, "new package contents").unwrap();
    let changed = declared_input(&mut cache, &tree, &file, true).unwrap();
    assert_ne!(
        changed.identity.sha256, initial.identity.sha256,
        "the declared directory did not change its identity"
    );
}

/// A host that keeps no tree (Windows) stamps each input at its MANIFEST target. The bytes are the same, so every
/// identity of the content is the same as over the tree. The guest builds its tree from the MANIFEST on both hosts.
#[tokio::test]
async fn the_stamp_through_a_manifest_is_the_stamp_of_the_tree() {
    let fixture = Fixture::new().await;
    let over_tree = fixture.prepared().await;

    let descriptor = &fixture.bazel.descriptor_path;
    let store = crate::daemon::testing::replace_tree_with_manifest(descriptor);
    assert!(matches!(HostRunfiles::of(descriptor).unwrap(), HostRunfiles::Manifest { .. }));

    let over_manifest = fixture.prepared().await;
    assert_eq!(over_manifest.runtime_digest, over_tree.runtime_digest);
    assert_eq!(over_manifest.product_digest, over_tree.product_digest);
    assert_eq!(over_manifest.mount_digest, over_tree.mount_digest);
    assert!(
        Path::new(&over_manifest.hot_jars[0].path).starts_with(&store),
        "a hot jar travels by its MANIFEST target, was {}",
        over_manifest.hot_jars[0].path
    );
    assert_eq!(over_manifest.guest_runfiles_root, over_manifest.guest_tree.root);
    assert!(
        over_manifest
            .guest_runfiles_root
            .starts_with(&format!("{}/runfiles/", fixture.settings.vm_data)),
        "{}",
        over_manifest.guest_runfiles_root
    );
    // The same source runfile is staged, and the MANIFEST names other targets, so it is another tree and launch.
    assert_eq!(over_manifest.guest_tree.request.staged, over_tree.guest_tree.request.staged);
    assert_ne!(over_manifest.guest_runfiles_root, over_tree.guest_runfiles_root);
    assert_ne!(over_manifest.launch_digest, over_tree.launch_digest);
}

/// The fixture's source runfile lies in the checkout, so it is a staged runfile with its digest and its length. Every
/// other runfile stays a MANIFEST line for the guest to link.
#[tokio::test]
async fn a_runfile_in_the_checkout_is_staged_with_its_digest() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    let tree = &prep.guest_tree;
    let source = crate::daemon::testing::FIXTURE_SOURCE_RUNFILE;
    assert_eq!(tree.request.staged.len(), 1, "{:?}", tree.request.staged);
    let staged = &tree.request.staged[0];
    assert_eq!(
        (staged.path.as_str(), staged.sha256.as_str(), staged.size),
        (source, digest::sha256_text("data-a").as_str(), 6)
    );
    assert_eq!(tree.staged_files.len(), 1);
    assert!(!tree.request.manifest_text.contains(source), "{}", tree.request.manifest_text);
    assert_eq!(
        tree.request.manifest_text.lines().count(),
        // Every file runfile but the staged one, and the line of the distribution home.
        crate::daemon::testing::FIXTURE_RUNFILES.len()
    );
}

// The trap this closes: bazel replaces a jar by rename, so a host build during a run changes nothing the run can
// notice. The verdict is then about bytes the checkout no longer produces, and it reads green.
#[tokio::test]
async fn hot_jar_drift_names_a_jar_that_was_rebuilt_under_the_run() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    assert_eq!(prep.hot_jar_drift(), None, "an untouched build drifted");

    fs::write(&prep.hot_jars[0].path, "rebuilt by another agent").unwrap();
    let drift = prep.hot_jar_drift().expect("a rebuilt jar left the verdict believable");
    assert!(
        drift.contains("a.jar") && drift.contains("do not build while a run is active"),
        "the drift does not name the jar and the remedy: {drift}"
    );
}

// A jar that is gone is drift too, and the reason has to reach the reader rather than being swallowed.
#[tokio::test]
async fn hot_jar_drift_reports_a_jar_that_is_gone() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    fs::remove_file(&prep.hot_jars[0].path).unwrap();
    let drift = prep.hot_jar_drift().expect("a missing jar left the verdict believable");
    assert!(drift.contains("a.jar (unreadable: "), "{drift}");
}

// The launch digest is its three parts and nothing else: the runtime, the controller's boot settings, and the
// daemon environment. The hot tier travels by host path, in classpath order.
#[tokio::test]
async fn the_launch_digest_is_composed_of_the_runtime_the_boot_settings_and_the_environment() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    let boot = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, 27_100, &prep.runtime_digest);
    assert_eq!(
        prep.launch_digest,
        launch_digest(&prep.runtime_digest, &boot, &prep.daemon_environment)
    );
    assert_eq!(prep.hot_jars.len(), 1);
    assert!(
        prep.hot_jars[0].path.ends_with("_main/hot/a.jar")
            && Path::new(&prep.hot_jars[0].path).starts_with(runtime::runfiles_root(&fixture.bazel.descriptor_path)),
        "a hot jar travels by host path, was {}",
        prep.hot_jars[0].path
    );
    assert_eq!(prep.hot_jars[0].sha256, digest::sha256_text("hot-a"));
    assert_eq!(
        prep.daemon_environment.get("NODE_BIN"),
        Some(&fixture.settings.vm_node),
        "the daemon environment names the guest node the controller staged"
    );
    // Four distinct identities: none of them is another under a second name.
    let mut identities = vec![&prep.runtime_digest, &prep.launch_digest, &prep.product_digest, &prep.mount_digest];
    identities.sort();
    identities.dedup();
    assert_eq!(identities.len(), 4);
}

// A launch fact no file carries still moves the runtime identity: a changed static flag is a new generation.
#[tokio::test]
async fn a_changed_static_flag_changes_the_runtime_digest() {
    let fixture = Fixture::new().await;
    let before = fixture.prepared().await;
    let mut descriptor = fixture_descriptor();
    descriptor["staticJvmFlags"] = serde_json::json!(["-Xmx2g", "-Dflag=<v>"]);
    fs::write(&fixture.bazel.descriptor_path, descriptor.to_string()).unwrap();
    let after = fixture.prepared().await;
    assert_ne!(before.runtime_digest, after.runtime_digest);
    assert_ne!(before.launch_digest, after.launch_digest);
    // The product did not move, so neither did the mount.
    assert_eq!(before.product_digest, after.product_digest);
    assert_eq!(before.mount_digest, after.mount_digest);
}

// The daemon JVM runs on a copy of the JBR of the distribution, so a changed byte or mode of that JBR is a new
// generation. The product identity reads the fingerprint of the distribution, which covers the JBR, and not the JBR.
#[tokio::test]
async fn a_changed_jbr_of_the_distribution_changes_the_runtime_digest() {
    let fixture = Fixture::new().await;
    let before = fixture.prepared().await;
    let jbr = runtime::runfiles_root(&fixture.bazel.descriptor_path)
        .join(crate::daemon::testing::FIXTURE_DIST_HOME)
        .join(crate::daemon::testing::FIXTURE_JBR_DIRECTORY);
    let java = jbr.join("a&b/bin/java");
    fs::write(&java, "another java").unwrap();
    let rebuilt = fixture.prepared().await;
    assert_ne!(before.runtime_digest, rebuilt.runtime_digest);
    assert_ne!(before.launch_digest, rebuilt.launch_digest);
    assert_eq!(before.product_digest, rebuilt.product_digest);

    fs::set_permissions(&java, fs::Permissions::from_mode(0o755)).unwrap();
    let executable = fixture.prepared().await;
    assert_ne!(
        rebuilt.runtime_digest, executable.runtime_digest,
        "a changed mode kept the generation"
    );

    // Another file of the distribution home is not the JBR, so it keeps the generation.
    fs::write(jbr.parent().unwrap().join("build.txt"), "IU-262.1").unwrap();
    assert_eq!(executable.runtime_digest, fixture.prepared().await.runtime_digest);
}

// A distribution without its JBR refuses before anything is staged, and the refusal names the path.
#[tokio::test]
async fn a_distribution_without_its_jbr_refuses_with_the_path() {
    let fixture = Fixture::new().await;
    let jbr = runtime::runfiles_root(&fixture.bazel.descriptor_path)
        .join(crate::daemon::testing::FIXTURE_DIST_HOME)
        .join(crate::daemon::testing::FIXTURE_JBR_DIRECTORY);
    fs::remove_dir_all(&jbr).unwrap();
    let scope = BuildScope::worker(&fixture.settings, &fixture.worker).unwrap();
    let failure = refusal(
        fixture
            .host
            .prepare_build(&Ctx::background(), &scope)
            .await
            .map(|prep| prep.runtime_digest),
    );
    assert_eq!(failure.code, "daemon_runtime_input_missing");
    assert!(
        failure
            .message
            .contains(&format!("has no JBR: {} is not a directory", jbr.display())),
        "the refusal must name the JBR directory, said {:?}",
        failure.message
    );
}

// The IDE flags file is part of the IDE's identity: a restart keeps an IDE only of the same product, so a changed flag
// must relaunch the IDE. It does not restage the daemon runtime.
#[tokio::test]
async fn a_changed_ide_flags_file_changes_the_product_digest() {
    let fixture = Fixture::new().await;
    let before = fixture.prepared().await;
    let flags = runtime::runfiles_root(&fixture.bazel.descriptor_path).join("_main/ide/ide.jvm-flags.txt");
    fs::write(&flags, "-ea\n-Dchanged=true\n").unwrap();
    let after = fixture.prepared().await;
    assert_ne!(before.product_digest, after.product_digest);
    assert_ne!(before.mount_digest, after.mount_digest);
    assert_eq!(before.runtime_digest, after.runtime_digest);
    assert_eq!(before.launch_digest, after.launch_digest);
}

// The project the IDE opens is share-backed data: a changed archive refreshes the shares and keeps the product.
#[tokio::test]
async fn a_changed_ide_project_archive_changes_only_the_mount_digest() {
    let fixture = Fixture::new().await;
    let before = fixture.prepared().await;
    let archive = runtime::runfiles_root(&fixture.bazel.descriptor_path).join("_main/ide/project.zip");
    fs::write(&archive, "another project").unwrap();
    let after = fixture.prepared().await;
    assert_eq!(before.product_digest, after.product_digest);
    assert_ne!(before.mount_digest, after.mount_digest);
}

// The context plugin files travel by the push: a changed file moves only the plugin identity, so it refreshes no share
// and keeps the product, the runtime and the launch.
#[tokio::test]
async fn a_changed_context_plugin_file_changes_only_the_plugins_digest() {
    let fixture = Fixture::new().await;
    let before = fixture.prepared().await;
    assert_eq!(before.plugin_files.len(), 1);
    assert_eq!(before.plugin_files[0].destination, "bridge/lib/bridge.jar");
    let plugin = runtime::runfiles_root(&fixture.bazel.descriptor_path).join("_main/plugins/bridge/lib/bridge.jar");
    fs::write(&plugin, "another bridge").unwrap();
    let after = fixture.prepared().await;
    assert_ne!(before.plugins_digest, after.plugins_digest);
    assert_ne!(before.plugin_files[0].host.sha256, after.plugin_files[0].host.sha256);
    assert_ne!(before.plugin_directories["bridge"], after.plugin_directories["bridge"]);
    assert_eq!(before.product_digest, after.product_digest);
    assert_eq!(before.mount_digest, after.mount_digest);
    assert_eq!(before.runtime_digest, after.runtime_digest);
    assert_eq!(before.launch_digest, after.launch_digest);
}

// The lane inputs travel by the push: an edit of the fake agent moves no identity, so it refreshes no share, keeps the
// IDE and keeps the daemon. Only the digest that the push offers moves.
#[tokio::test]
async fn a_changed_fake_agent_jar_changes_no_identity_and_only_its_pushed_digest() {
    let fixture = Fixture::new().await;
    let before = fixture.prepared().await;
    let fields: Vec<&str> = before.lane_inputs.iter().map(|input| input.field).collect();
    assert_eq!(fields, ["fakeAgentJar", "laneDocuments", "suiteDocuments"]);
    // The jars of the documents are the hot jar of the fixture, so the push offers their digest once.
    assert_eq!(before.lane_inputs[1].host, before.hot_jars[0]);
    assert_eq!(before.lane_inputs[2].host, before.hot_jars[0]);

    let agent = runtime::runfiles_root(&fixture.bazel.descriptor_path).join(crate::daemon::testing::FIXTURE_FAKE_AGENT_JAR);
    fs::write(&agent, "another fake agent").unwrap();
    let after = fixture.prepared().await;
    assert_ne!(before.lane_inputs[0].host.sha256, after.lane_inputs[0].host.sha256);
    assert_eq!(before.mount_digest, after.mount_digest);
    assert_eq!(before.product_digest, after.product_digest);
    assert_eq!(before.runtime_digest, after.runtime_digest);
    assert_eq!(before.launch_digest, after.launch_digest);
    assert_eq!(before.plugins_digest, after.plugins_digest);
}

#[test]
fn the_lane_inputs_note_names_each_key_with_its_digest() {
    let input = |field, sha256: &str| LaneInput {
        field,
        host: PathDigest::new("/host/x.jar", sha256),
    };
    assert_eq!(
        lane_inputs_note(&[input("fakeAgentJar", "0123456789abcdef"), input("laneDocuments", "short")]),
        "lane inputs by digest: fakeAgentJar 0123456789ab, laneDocuments short"
    );
}

// Each context plugin directory has its own digest, so a bridge edit moves the bridge digest and keeps the digest of the
// resident part. The note names each directory with the first 12 hex digits.
#[test]
fn each_context_plugin_directory_has_its_own_digest_and_a_note() {
    let file = |destination: &str, sha256: &str| PluginFile {
        destination: destination.to_owned(),
        host: PathDigest::new("/host/x.jar", sha256),
    };
    let before = plugin_directories(&[file("bridge/lib/b.jar", "1"), file("resident/lib/r.jar", "2")]);
    let after = plugin_directories(&[file("bridge/lib/b.jar", "3"), file("resident/lib/r.jar", "2")]);
    assert_eq!(before.keys().collect::<Vec<_>>(), ["bridge", "resident"]);
    assert_ne!(before["bridge"], after["bridge"]);
    assert_eq!(before["resident"], after["resident"]);
    assert_eq!(
        plugin_directories_note(&before),
        format!(
            "context plugins by digest: bridge {}, resident {}",
            &before["bridge"][..12],
            &before["resident"][..12]
        )
    );
}

// The plugin identity reads the destination too: the same bytes at another place are another plugin directory.
#[test]
fn the_plugins_digest_reads_the_destination_and_the_content() {
    let file = |destination: &str, sha256: &str| PluginFile {
        destination: destination.to_owned(),
        host: PathDigest::new("/host/x.jar", sha256),
    };
    let base = plugins_digest(&[file("bridge/lib/x.jar", "a")]);
    assert_ne!(base, plugins_digest(&[file("bridge/lib/y.jar", "a")]));
    assert_ne!(base, plugins_digest(&[file("bridge/lib/x.jar", "b")]));
    assert_eq!(
        base,
        plugins_digest(&[PluginFile {
            destination: "bridge/lib/x.jar".to_owned(),
            host: PathDigest::new("/another/host/path.jar", "a"),
        }])
    );
}

// The daemon port is inside `@controller-boot`: changing it must restart the daemon, because the running one keeps
// listening where it was told to.
#[tokio::test]
async fn the_daemon_port_enters_the_launch_digest() {
    let fixture = Fixture::with_environment(&[("AIR_VM_DAEMON_PORT", "27101")]).await;
    let prep = fixture.prepared().await;
    let launched_on = |port| {
        let boot = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, port, &prep.runtime_digest);
        launch_digest(&prep.runtime_digest, &boot, &prep.daemon_environment)
    };
    assert_eq!(prep.launch_digest, launched_on(27_101));
    assert_ne!(prep.launch_digest, launched_on(27_100));
}

// The daemon environment is part of the launch identity. A JVM cannot change its own environment, so a change there
// must re-exec the daemon rather than wait for someone to restart it by hand.
#[tokio::test]
async fn the_daemon_environment_enters_the_launch_digest() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    assert!(
        !prep.daemon_environment.is_empty(),
        "the daemon environment is empty, so this test guards nothing"
    );
    let boot = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, 27_100, &prep.runtime_digest);
    let other = BTreeMap::from([("NODE_BIN".to_owned(), "/other/node".to_owned())]);
    assert_ne!(
        prep.launch_digest,
        launch_digest(&prep.runtime_digest, &boot, &other),
        "the daemon environment does not reach the launch digest"
    );
}

// A declared input that is not a file refuses by name and owner, before anything is staged.
#[tokio::test]
async fn a_missing_declared_input_refuses_naming_its_owner() {
    let fixture = Fixture::new().await;
    let victim = runtime::runfiles_root(&fixture.bazel.descriptor_path).join("_main/stable/two.jar");
    fs::remove_file(victim).unwrap();
    let scope = BuildScope::worker(&fixture.settings, &fixture.worker).unwrap();
    let failure = refusal(
        fixture
            .host
            .prepare_build(&Ctx::background(), &scope)
            .await
            .map(|prep| prep.runtime_digest),
    );
    assert_eq!(failure.code, "daemon_runtime_input_missing");
    assert!(
        failure.message.contains("_main/stable/two.jar") && failure.message.contains("//fixture:two.jar"),
        "the refusal must name the file and its owner, said {:?}",
        failure.message
    );
}

// The build reads the checkout beside itself, and the progress stream says which one it read.
#[tokio::test]
async fn the_build_reads_the_checkout_tree() {
    let fixture = Fixture::new().await;
    fixture.git().status(&[" M plugins/air/a.kt"]);
    let prep = fixture.prepared().await;
    let tree = prep.tree.expect("the tree is read");
    assert_eq!(tree.head, FAKE_HEAD);
    assert_eq!(tree.uncommitted, ["plugins/air/a.kt"]);
    assert!(
        fixture.stderr.text().contains(r#""uncommitted":1"#),
        "the checkout record is published: {}",
        fixture.stderr.text()
    );
}

// A `git` that fails is the build's tree error. It never fails the build.
#[tokio::test]
async fn a_tree_failure_is_recorded_and_never_fails_the_build() {
    let fixture = Fixture::new().await;
    fixture.git().fail();
    let prep = fixture.prepared().await;
    let refusal = prep.tree.expect_err("a failing git reads no tree");
    assert!(refusal.message.contains("not a git repository"), "{refusal}");
}

#[test]
fn the_environment_digest_is_by_name() {
    let one = BTreeMap::from([("B".to_owned(), "x".to_owned()), ("a".to_owned(), "y".to_owned())]);
    let swapped = BTreeMap::from([("B".to_owned(), "y".to_owned()), ("a".to_owned(), "x".to_owned())]);
    assert_ne!(lane_environment_digest(&one), lane_environment_digest(&swapped));
    assert_eq!(
        lane_environment_digest(&one),
        digest::path_sensitive_digest(&[
            PathDigest::new("B", digest::sha256_text("x")),
            PathDigest::new("a", digest::sha256_text("y")),
        ])
    );
}

/// The guest is a target and not a build option, so each guest OS has its own daemon target.
#[test]
fn each_guest_builds_its_own_daemon_target() {
    assert_eq!(daemon_label(GuestOs::Macos), LABEL);
    assert_eq!(daemon_label(GuestOs::Linux), LINUX_GUEST_LABEL);
}

// The runner is a boot setting: a change of it restarts the daemon. The JVM runner adds nothing to the boot settings,
// so the launch digest of a JVM daemon stays what it was.
#[tokio::test]
async fn the_runner_enters_the_launch_digest() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    let jvm = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, 27_100, &prep.runtime_digest);
    assert_eq!(
        prep.launch_digest,
        launch_digest(&prep.runtime_digest, &jvm, &prep.daemon_environment)
    );
    let mut settings = Config::clone(&fixture.settings);
    settings.daemon.runner = Runner::Daemon;
    let verb = controller_boot_digest(&settings, &prep.guest_runfiles_root, 27_100, &prep.runtime_digest);
    assert_ne!(jvm, verb);
}
