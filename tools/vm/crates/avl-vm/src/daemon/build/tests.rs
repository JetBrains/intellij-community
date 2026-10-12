use std::fs;

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
/// identity of the content is the same as over the tree, and only the guest root differs: the tree the guest
/// builds, named by the digest of the MANIFEST and the path table.
#[tokio::test]
async fn the_stamp_through_a_manifest_is_the_stamp_of_the_tree() {
    let fixture = Fixture::new().await;
    let over_tree = fixture.prepared().await;
    assert!(matches!(over_tree.runfiles, HostRunfiles::Tree(_)));

    let descriptor = &fixture.bazel.descriptor_path;
    let store = crate::daemon::testing::replace_tree_with_manifest(descriptor);
    let manifest_path = &avl_host_sys::runfiles::manifest_paths(descriptor)[0];

    let over_manifest = fixture.prepared().await;
    let HostRunfiles::Manifest { path, .. } = &over_manifest.runfiles else {
        panic!("a host with only a MANIFEST read {:?}", over_manifest.runfiles);
    };
    assert_eq!(path, manifest_path);
    assert_eq!(over_manifest.runtime_digest, over_tree.runtime_digest);
    assert_eq!(over_manifest.product_digest, over_tree.product_digest);
    assert_eq!(over_manifest.mount_digest, over_tree.mount_digest);
    assert!(
        Path::new(&over_manifest.hot_jars[0].path).starts_with(&store),
        "a hot jar travels by its MANIFEST target, was {}",
        over_manifest.hot_jars[0].path
    );
    assert_eq!(
        over_manifest.guest_runfiles_root,
        over_manifest.runfiles.guest_root(&fixture.settings).unwrap()
    );
    assert!(
        over_manifest
            .guest_runfiles_root
            .starts_with(&format!("{}/runfiles/", fixture.settings.vm_data)),
        "{}",
        over_manifest.guest_runfiles_root
    );
    // The boot digest covers the root that is sent, so the other root is another launch.
    assert_ne!(over_manifest.launch_digest, over_tree.launch_digest);
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
    let boot = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, 27_100).unwrap();
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

// The daemon port is inside `@controller-boot`: changing it must restart the daemon, because the running one keeps
// listening where it was told to.
#[tokio::test]
async fn the_daemon_port_enters_the_launch_digest() {
    let fixture = Fixture::with_environment(&[("AIR_VM_DAEMON_PORT", "27101")]).await;
    let prep = fixture.prepared().await;
    let launched_on = |port| {
        let boot = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, port).unwrap();
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
    let boot = controller_boot_digest(&fixture.settings, &prep.guest_runfiles_root, 27_100).unwrap();
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
