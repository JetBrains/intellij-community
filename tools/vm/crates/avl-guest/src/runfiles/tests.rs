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

fn request(manifest: &str, shares: &Path, destination: &Path) -> Value {
    json!({
        "schemaVersion": 4,
        "manifestText": manifest,
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
    let request = request(MANIFEST, &shares, &destination);

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

/// With the copy rule of a Windows host, a package directory of a `node_modules` store is copied into the tree, and
/// the dependency link beside it stays a link of the tree, so Node's real-path resolution from the copy finds the
/// tree's links. Without the rule the package is a link onto the share, as every other target.
#[test]
fn a_node_modules_package_directory_is_copied_into_the_tree() {
    let directory = tempfile::tempdir().unwrap();
    let (shares, destination) = (directory.path().join("shares"), directory.path().join("runfiles"));
    let store = shares.join("bazel/execroot/_main/bazel-out/bin/pkg/node_modules/.aspect_rules_js/x@1/node_modules/x");
    fs::create_dir_all(store.join("dist")).unwrap();
    fs::write(store.join("dist/index.js"), "export default 1;\n").unwrap();
    std::os::unix::fs::symlink("dist/index.js", store.join("main.js")).unwrap();
    let text = concat!(
        "_main/pkg/node_modules/.aspect_rules_js/x@1/node_modules/x ",
        "C:/Users/air/_bazel/execroot/_main/bazel-out/bin/pkg/node_modules/.aspect_rules_js/x@1/node_modules/x\n",
        "_main/pkg/node_modules/.aspect_rules_js/x@1/node_modules/y ../../y@1/node_modules/y\n",
        "_main/pkg/data.txt C:/Users/air/repo/pkg/data.txt\n",
    );
    let linked = run(&request(text, &shares, &destination)).document();
    let linked_root = Path::new(linked["data"]["root"].as_str().unwrap()).to_path_buf();
    assert!(
        fs::symlink_metadata(linked_root.join("_main/pkg/node_modules/.aspect_rules_js/x@1/node_modules/x"))
            .unwrap()
            .is_symlink()
    );
    let mut copying = request(text, &shares, &destination);
    copying["copyPackageStores"] = json!(true);
    let document = run(&copying).document();
    assert_eq!(document["ok"], true, "{document}");
    assert_ne!(document["data"]["digest"], linked["data"]["digest"]);
    let root = Path::new(document["data"]["root"].as_str().unwrap()).to_path_buf();
    let copied = root.join("_main/pkg/node_modules/.aspect_rules_js/x@1/node_modules/x");
    assert!(fs::symlink_metadata(&copied).unwrap().is_dir());
    assert_eq!(fs::read_to_string(copied.join("dist/index.js")).unwrap(), "export default 1;\n");
    assert_eq!(fs::read_link(copied.join("main.js")).unwrap(), Path::new("dist/index.js"));
    assert_eq!(
        fs::read_link(root.join("_main/pkg/node_modules/.aspect_rules_js/x@1/node_modules/y")).unwrap(),
        Path::new("../../y@1/node_modules/y")
    );
    // A file outside a store is still a link.
    assert!(fs::symlink_metadata(root.join("_main/pkg/data.txt")).unwrap().is_symlink());
}

/// The request with the staged runfiles and their bytes on one stdin.
fn staged_stdin(request: &Value, bytes: &[&[u8]]) -> Vec<u8> {
    let mut stdin = request.to_string().into_bytes();
    stdin.push(b'\n');
    for chunk in bytes {
        stdin.extend_from_slice(chunk);
    }
    stdin
}

fn staged_request(shares: &Path, destination: &Path, with_bytes: bool) -> Value {
    let mut request = request(
        "_main/pkg/lib.jar C:/Users/air/_bazel/execroot/_main/bazel-out/lib.jar\n",
        shares,
        destination,
    );
    request["staged"] = json!([
        {"path": "_main/tools/pnpm-lock.yaml", "sha256": hex::encode(Sha256::digest(b"lock: 1\n")), "size": 8},
        {"path": "community+/java/mockJDK/rt.jar", "sha256": hex::encode(Sha256::digest(b"jar")), "size": 3},
    ]);
    request["withBytes"] = json!(with_bytes);
    request
}

/// A staged runfile is a copy of its bytes in the tree. A request without the bytes reuses that tree, and with no tree
/// of its digest it is refused by its own code, so the host sends the bytes.
#[test]
fn a_staged_runfile_is_a_copy_and_a_request_without_bytes_only_reuses() {
    let directory = tempfile::tempdir().unwrap();
    let (shares, destination) = (directory.path().join("shares"), directory.path().join("runfiles"));

    let answered = run(&staged_request(&shares, &destination, false));
    assert_eq!(answered.exit, 70, "{}", answered.stderr);
    assert_eq!(answered.code(), STAGED_BYTES_MISSING_CODE);
    assert!(fs::read_dir(&destination).is_err_and(|error| error.kind() == std::io::ErrorKind::NotFound));

    let stdin = staged_stdin(&staged_request(&shares, &destination, true), &[b"lock: 1\n", b"jar"]);
    let document = run_agent(&["runfiles-tree"], &stdin).document();
    assert_eq!(document["ok"], true, "{document}");
    assert_eq!(document["data"]["entries"], 3);
    let root = Path::new(document["data"]["root"].as_str().unwrap()).to_path_buf();
    let lock = root.join("_main/tools/pnpm-lock.yaml");
    assert!(fs::symlink_metadata(&lock).unwrap().is_file());
    assert_eq!(fs::read(&lock).unwrap(), b"lock: 1\n");
    assert_eq!(fs::read(root.join("community+/java/mockJDK/rt.jar")).unwrap(), b"jar");
    assert!(fs::symlink_metadata(root.join("_main/pkg/lib.jar")).unwrap().is_symlink());

    let again = run(&staged_request(&shares, &destination, false)).document();
    assert_eq!(again["data"]["reused"], true, "{again}");
    assert_eq!(again["data"]["root"], document["data"]["root"]);
}

/// Bytes that are short, long or of another digest are refused, and leave no tree.
#[test]
fn staged_bytes_that_do_not_match_the_request_are_refused() {
    let directory = tempfile::tempdir().unwrap();
    let (shares, destination) = (directory.path().join("shares"), directory.path().join("runfiles"));
    let request = staged_request(&shares, &destination, true);
    for bytes in [&[&b"lock: 1\n"[..], b"ja"][..], &[b"lock: 1\n", b"jarX"], &[b"lock: 2\n", b"jar"]] {
        let answered = run_agent(&["runfiles-tree"], &staged_stdin(&request, bytes));
        assert_eq!(answered.exit, 70, "{}", answered.stderr);
        assert_eq!(answered.code(), "guest_runfiles_tree_failed");
    }
    assert!(fs::read_dir(&destination).is_ok_and(|mut entries| entries.next().is_none()));
}

/// Another table links to other targets, so it is another digest and another tree.
#[test]
fn another_path_table_is_another_tree() {
    let directory = tempfile::tempdir().unwrap();
    let destination = directory.path().join("runfiles");
    let first = run(&request(MANIFEST, &directory.path().join("a"), &destination)).document();
    let second = run(&request(MANIFEST, &directory.path().join("b"), &destination)).document();
    assert_ne!(first["data"]["digest"], second["data"]["digest"]);
    assert_eq!(second["data"]["reused"], false);
}

/// A target outside the table is its own refusal, naming the runfile and the target, and it leaves no tree and no
/// staging directory.
#[test]
fn a_target_outside_the_table_is_refused_by_name() {
    let directory = tempfile::tempdir().unwrap();
    let destination = directory.path().join("runfiles");
    let answered = run(&request(
        "_main/a C:/Users/air/repo/a\n_main/b D:/elsewhere/b\n",
        &directory.path().join("shares"),
        &destination,
    ));
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
        assert_eq!(
            refused(&request(text, &shares, &destination)),
            "guest_runfiles_tree_failed",
            "{text:?}"
        );
    }
    let mut wrong = request(MANIFEST, &shares, &destination);
    wrong["schemaVersion"] = json!(3);
    assert_eq!(refused(&wrong), "guest_runfiles_tree_failed");
    let mut relative = request(MANIFEST, &shares, &destination);
    relative["destination"] = json!("runfiles");
    assert_eq!(refused(&relative), "guest_runfiles_tree_failed");
    let mut bad_table = request(MANIFEST, &shares, &destination);
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
    let build = |text: &str| {
        let document = run(&request(text, &shares, &destination)).document();
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
