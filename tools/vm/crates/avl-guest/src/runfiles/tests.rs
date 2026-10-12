//! The verb over a fixture MANIFEST in a temporary directory, through the agent's own dispatch.

use std::fs;
use std::path::Path;

use avl_wire::path_map::{PathMap, PathPrefix};
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::testing::run_agent;

/// A MANIFEST of a Windows host, with its guest shares under `shares`: a checkout file, a Bazel output, an escaped
/// line, a relative link and an empty file.
const MANIFEST: &str = concat!(
    "_main/pkg/data.txt C:\\Users\\air\\repo\\pkg\\data.txt\n",
    "_main/pkg/lib.jar C:/Users/air/_bazel/execroot/_main/bazel-out/lib.jar\n",
    r" _main/pkg/with\sspace.txt C:/Users/air/repo/pkg/with space.txt",
    "\n",
    "_main/pkg/relative ../other/file\n",
    "_main/pkg/__init__.py \n",
);

fn table(shares: &Path) -> PathMap {
    PathMap::new(vec![
        PathPrefix::new(r"C:\Users\air\repo", shares.join("repo").to_string_lossy().into_owned()),
        PathPrefix::new("C:/Users/air/_bazel", shares.join("bazel").to_string_lossy().into_owned()),
    ])
}

fn request(manifest: &Path, shares: &Path, destination: &Path) -> Value {
    json!({
        "schemaVersion": 1,
        "manifest": manifest,
        "pathMap": table(shares),
        "destination": destination,
    })
}

fn run(request: &Value) -> crate::testing::Answered {
    run_agent(&["runfiles-tree"], request.to_string().as_bytes())
}

#[test]
fn every_line_becomes_its_runfile_under_the_digest_and_a_second_build_reuses_it() {
    let directory = tempfile::tempdir().unwrap();
    let (shares, destination) = (directory.path().join("shares"), directory.path().join("runfiles"));
    let manifest = directory.path().join("MANIFEST");
    fs::write(&manifest, MANIFEST).unwrap();
    let request = request(&manifest, &shares, &destination);

    let answered = run(&request);
    assert_eq!(answered.exit, 0, "{}", answered.stderr);
    let document = answered.document();
    assert_eq!(document["ok"], true, "{document}");
    assert_eq!(document["command"], "runfiles-tree");
    let data = &document["data"];
    let digest = data["digest"].as_str().unwrap().to_owned();
    assert!(avl_wire::stage::is_sha256_hex(&digest), "{digest}");
    let root = destination.join(&digest);
    assert_eq!(data["root"], root.to_string_lossy().as_ref());
    assert_eq!(data["entries"], 5);
    assert_eq!(data["reused"], false);

    let link = |logical: &str| fs::read_link(root.join(logical)).unwrap();
    assert_eq!(link("_main/pkg/data.txt"), shares.join("repo/pkg/data.txt"));
    assert_eq!(link("_main/pkg/lib.jar"), shares.join("bazel/execroot/_main/bazel-out/lib.jar"));
    assert_eq!(link("_main/pkg/with space.txt"), shares.join("repo/pkg/with space.txt"));
    // A relative target is kept as it is, and an empty one is an empty file.
    assert_eq!(link("_main/pkg/relative"), Path::new("../other/file"));
    let empty = fs::symlink_metadata(root.join("_main/pkg/__init__.py")).unwrap();
    assert!(empty.is_file() && empty.len() == 0);
    // Nothing is left beside the tree.
    let names: Vec<String> = fs::read_dir(&destination)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    assert_eq!(names, [digest]);

    let again = run(&request).document();
    assert_eq!(again["data"]["reused"], true, "{again}");
    assert_eq!(again["data"]["root"], data["root"]);
}

/// Another table links to other targets, so it is another digest and another tree.
#[test]
fn another_path_table_is_another_tree() {
    let directory = tempfile::tempdir().unwrap();
    let manifest = directory.path().join("MANIFEST");
    fs::write(&manifest, MANIFEST).unwrap();
    let destination = directory.path().join("runfiles");
    let first = run(&request(&manifest, &directory.path().join("a"), &destination)).document();
    let second = run(&request(&manifest, &directory.path().join("b"), &destination)).document();
    assert_ne!(first["data"]["digest"], second["data"]["digest"]);
    assert_eq!(second["data"]["reused"], false);
}

/// A target outside the table is its own refusal, naming the runfile and the target, and it leaves no tree and no
/// staging directory.
#[test]
fn a_target_outside_the_table_is_refused_by_name() {
    let directory = tempfile::tempdir().unwrap();
    let manifest = directory.path().join("MANIFEST");
    fs::write(&manifest, "_main/a C:/Users/air/repo/a\n_main/b D:/elsewhere/b\n").unwrap();
    let destination = directory.path().join("runfiles");
    let answered = run(&request(&manifest, &directory.path().join("shares"), &destination));
    assert_eq!(answered.exit, 70, "{}", answered.stderr);
    assert_eq!(answered.code(), UNMAPPED_TARGET_CODE);
    let message = answered.failure()["error"]["message"].as_str().unwrap().to_owned();
    assert!(message.contains("_main/b") && message.contains("D:/elsewhere/b"), "{message}");
    assert_eq!(fs::read_dir(&destination).unwrap().count(), 0);
}

#[test]
fn a_request_or_a_manifest_the_verb_cannot_act_on_is_the_verbs_refusal() {
    let directory = tempfile::tempdir().unwrap();
    let shares = directory.path().join("shares");
    let destination = directory.path().join("runfiles");
    let manifest = directory.path().join("MANIFEST");
    let refused = |request: &Value| {
        let answered = run(request);
        assert_eq!(answered.exit, 70, "{request}: {}", answered.stderr);
        answered.code()
    };

    for text in [
        "_main/../escape C:/Users/air/repo/a\n",
        "/abs C:/Users/air/repo/a\n",
        " _main/a\\x C:/f\n",
    ] {
        fs::write(&manifest, text).unwrap();
        assert_eq!(
            refused(&request(&manifest, &shares, &destination)),
            "guest_runfiles_tree_failed",
            "{text:?}"
        );
    }
    let missing = request(&directory.path().join("absent"), &shares, &destination);
    assert_eq!(refused(&missing), "guest_runfiles_tree_failed");

    fs::write(&manifest, MANIFEST).unwrap();
    let mut wrong = request(&manifest, &shares, &destination);
    wrong["schemaVersion"] = json!(2);
    assert_eq!(refused(&wrong), "guest_runfiles_tree_failed");
    let mut relative = request(&manifest, &shares, &destination);
    relative["destination"] = json!("runfiles");
    assert_eq!(refused(&relative), "guest_runfiles_tree_failed");
    let mut bad_table = request(&manifest, &shares, &destination);
    bad_table["pathMap"] = json!({"prefixes": [{"host": "C:/a", "guest": "mnt/a"}]});
    assert_eq!(refused(&bad_table), "guest_runfiles_tree_failed");

    let answered = run_agent(&["runfiles-tree"], b"{");
    assert_eq!(answered.code(), "guest_runfiles_tree_failed");
    // No refusal leaves a tree or a staging directory behind.
    assert!(fs::read_dir(&destination).is_ok_and(|mut entries| entries.next().is_none()));
}

/// A build keeps its own tree and the newest other one, and removes older trees, a dead build's staging directory
/// and anything else in the destination. A reuse refreshes its tree, so the next build keeps the tree used last.
#[test]
fn a_build_keeps_its_tree_and_the_newest_other_one() {
    let directory = tempfile::tempdir().unwrap();
    let (shares, destination) = (directory.path().join("shares"), directory.path().join("runfiles"));
    let manifest = directory.path().join("MANIFEST");
    let build = |text: &str| {
        fs::write(&manifest, text).unwrap();
        let document = run(&request(&manifest, &shares, &destination)).document();
        assert_eq!(document["ok"], true, "{document}");
        document["data"].clone()
    };
    let old = |root: &Value, seconds: u64| {
        let root = Path::new(root["root"].as_str().unwrap());
        let time = std::time::UNIX_EPOCH + std::time::Duration::from_secs(seconds);
        File::open(root).unwrap().set_modified(time).unwrap();
    };
    let first = build("_main/a C:/Users/air/repo/a\n");
    old(&first, 1_000);
    let second = build("_main/b C:/Users/air/repo/b\n");
    old(&second, 2_000);
    assert_eq!(second["removed"], json!([]), "{second}");
    let dead = destination.join(".0123.99999.partial");
    fs::create_dir_all(dead.join("_main")).unwrap();
    fs::write(destination.join("stray.txt"), "x").unwrap();

    let third = build("_main/c C:/Users/air/repo/c\n");
    let path = |data: &Value| data["root"].as_str().unwrap().to_owned();
    let mut removed = vec![
        path(&first),
        dead.to_string_lossy().into_owned(),
        destination.join("stray.txt").to_string_lossy().into_owned(),
    ];
    removed.sort();
    assert_eq!(third["removed"], json!(removed), "{third}");
    assert!(Path::new(&path(&second)).is_dir());
    assert!(Path::new(&path(&third)).is_dir());

    // Reusing the second tree makes it the one used last, so a new build removes the third.
    old(&third, 3_000);
    let reused = build("_main/b C:/Users/air/repo/b\n");
    assert_eq!(reused["reused"], true);
    assert_eq!(reused["removed"], json!([]), "{reused}");
    let fourth = build("_main/d C:/Users/air/repo/d\n");
    assert_eq!(fourth["removed"], json!([path(&third)]), "{fourth}");
    assert!(Path::new(&path(&second)).is_dir());
}
