use pretty_assertions::assert_eq;

use super::*;
use crate::path_map::PathPrefix;

fn entry(path: &str, target: &str) -> ManifestEntry {
    ManifestEntry {
        path: path.to_owned(),
        target: target.to_owned(),
    }
}

#[test]
fn a_plain_line_is_a_path_and_a_target_and_an_empty_target_is_an_empty_file() {
    let manifest = RunfilesManifest::parse(
        "_main/pkg/data.txt C:/Users/air/repo/pkg/data.txt\n\
         _main/pkg/__init__.py \n\
         _main/pkg/empty\n\
         _main/pkg/with space.txt\n\
         \n\
         rules_rust/lib.rs C:/b/lib file.rs\r\n",
    )
    .unwrap();
    assert_eq!(
        manifest.entries,
        [
            entry("_main/pkg/data.txt", "C:/Users/air/repo/pkg/data.txt"),
            entry("_main/pkg/__init__.py", ""),
            entry("_main/pkg/empty", ""),
            // A path with a space is always escaped, so an unescaped space ends the path.
            entry("_main/pkg/with", "space.txt"),
            entry("rules_rust/lib.rs", "C:/b/lib file.rs"),
        ]
    );
}

/// An escaped line: `\s`, `\n` and `\b` in the path, `\n` and `\b` in the target, where a space stays a space.
#[test]
fn an_escaped_line_decodes_both_halves_in_one_pass() {
    let manifest = RunfilesManifest::parse(concat!(
        r" _main/a\sb\nc\bd C:\bUsers\bair\bx y\nz",
        "\n",
        r" _main/e\bs C:/f",
        "\n",
        r" _main/empty\sfile",
        "\n",
    ))
    .unwrap();
    assert_eq!(
        manifest.entries,
        [
            entry("_main/a b\nc\\d", "C:\\Users\\air\\x y\nz"),
            entry("_main/e\\s", "C:/f"),
            entry("_main/empty file", ""),
        ]
    );
}

#[test]
fn a_line_outside_the_format_names_its_number() {
    for (text, line, reason) in [
        (" _main/a\\x C:/f\n", 1, "an unknown escape in the path"),
        ("a C:/f\n _main/a C:/f\\s\n", 2, "an unknown escape in the target"),
        ("a C:/f\n\n _main/a\\\n", 3, "an unknown escape in the path"),
        ("  C:/f\n", 1, "an empty runfile path"),
    ] {
        assert_eq!(RunfilesManifest::parse(text), Err(ManifestError { line, reason }), "{text:?}");
    }
    assert_eq!(
        ManifestError {
            line: 3,
            reason: "an empty runfile path"
        }
        .to_string(),
        "runfiles MANIFEST line 3: an empty runfile path"
    );
}

#[test]
fn one_runfile_resolves_by_its_whole_path() {
    let manifest = RunfilesManifest::parse("_main/a C:/a\n_main/ab C:/ab\n_main/empty \n").unwrap();
    assert_eq!(manifest.resolve("_main/a"), Some("C:/a"));
    assert_eq!(manifest.resolve("_main/ab"), Some("C:/ab"));
    assert_eq!(manifest.resolve("_main/empty"), Some(""));
    assert_eq!(manifest.resolve("_main"), None);
    assert_eq!(manifest.resolve("_main/a/"), None);
}

// A symlink runfile's target is the link's text, relative to its own directory. The lookup follows it to the runfile
// it names, through a chain, and stops at an absolute host target. A chain that leaves the root or loops is no runfile.
#[test]
fn a_symlink_runfile_is_followed_through_the_manifest_to_its_host_target() {
    let manifest = RunfilesManifest::parse(
        "_main/pkg/node_modules/esbuild ../../store/esbuild/node_modules/esbuild\n\
         _main/store/esbuild/node_modules/esbuild ../real/esbuild\n\
         _main/store/esbuild/real/esbuild C:/out/bin/esbuild\n\
         _main/pkg/empty \n\
         _main/pkg/escape ../../../outside\n\
         _main/pkg/loop ./loop\n",
    )
    .unwrap();
    assert_eq!(manifest.host_target("_main/pkg/node_modules/esbuild"), Some("C:/out/bin/esbuild"));
    assert_eq!(
        manifest.host_target("_main/store/esbuild/node_modules/esbuild"),
        Some("C:/out/bin/esbuild")
    );
    assert_eq!(manifest.host_target("_main/store/esbuild/real/esbuild"), Some("C:/out/bin/esbuild"));
    assert_eq!(manifest.host_target("_main/pkg/empty"), Some(""));
    assert_eq!(manifest.host_target("_main/pkg/escape"), None);
    assert_eq!(manifest.host_target("_main/pkg/loop"), None);
    assert_eq!(manifest.host_target("_main/pkg/absent"), None);
}

// `render` is the inverse of `parse`, escaped lines included, so a MANIFEST the host rewrote reads back as the same
// entries on the guest.
#[test]
fn render_is_the_inverse_of_parse() {
    let text = "_main/a C:/a\n _main/b\\sc C:/with space/b\n _main/n\\nl C:/x\\by\n_main/empty \n";
    let manifest = RunfilesManifest::parse(text).unwrap();
    assert_eq!(manifest.render(), text);
    assert_eq!(RunfilesManifest::parse(&manifest.render()).unwrap(), manifest);
}

fn request(manifest: &str, table: PathMap) -> RunfilesTreeRequest {
    RunfilesTreeRequest {
        schema_version: SCHEMA_VERSION,
        manifest_text: manifest.to_owned(),
        path_map: table,
        destination: "/home/admin/WorkerData/runfiles".to_owned(),
        staged: Vec::new(),
        with_bytes: false,
        copy_package_stores: false,
    }
}

#[test]
fn the_request_and_the_result_travel_as_camel_case_json() {
    let mut request = request("_main/a C:/a\n", PathMap::new(vec![PathPrefix::new("C:/b", "/mnt/b")]));
    request.staged.push(StagedRunfile {
        path: "_main/lock.yaml".to_owned(),
        sha256: "ab".to_owned(),
        size: 2,
    });
    let text = serde_json::to_string(&request).unwrap();
    assert_eq!(
        text,
        r#"{"schemaVersion":4,"manifestText":"_main/a C:/a\n","pathMap":{"prefixes":[{"host":"C:/b","guest":"/mnt/b"}]},"destination":"/home/admin/WorkerData/runfiles","staged":[{"path":"_main/lock.yaml","sha256":"ab","size":2}],"withBytes":false,"copyPackageStores":false}"#
    );
    assert_eq!(serde_json::from_str::<RunfilesTreeRequest>(&text).unwrap(), request);
    let result = RunfilesTreeResult {
        root: "/d/abc".to_owned(),
        digest: "abc".to_owned(),
        entries: 2,
        reused: false,
        removed: vec!["/d/old".to_owned()],
    };
    assert_eq!(
        serde_json::to_string(&result).unwrap(),
        r#"{"root":"/d/abc","digest":"abc","entries":2,"reused":false,"removed":["/d/old"]}"#
    );
    // A reply without the list reads as one that removed nothing.
    let older: RunfilesTreeResult = serde_json::from_str(r#"{"root":"/d/abc","digest":"abc","entries":2,"reused":true}"#).unwrap();
    assert_eq!(older.removed, Vec::<String>::new());
}

/// The MANIFEST of `avl-worker-test.exe` that a Windows Bazel wrote, as it was: LF line ends, forward slashes, no
/// escaped line, and the output root in lower case.
const WINDOWS_MANIFEST: &str = r"_main/.agents/skills/vm-ui-tests/provision/versions.env C:/dev/iw/.agents/skills/vm-ui-tests/provision/versions.env
_main/fleet/build/internal/tools/libtest-junit-wrapper/libtest-junit-wrapper.exe C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/local_windows-fastbuild/bin/fleet/build/internal/tools/libtest-junit-wrapper/libtest-junit-wrapper.exe
_main/plugins/air/tests/integration/vm-lane/crates/avl-testkit/avl-fake.exe C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/local_windows-fastbuild/bin/plugins/air/tests/integration/vm-lane/crates/avl-testkit/avl-fake.exe
_main/plugins/air/tests/integration/vm-lane/crates/avl-worker/avl-worker-test.exe C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/local_windows-fastbuild/bin/plugins/air/tests/integration/vm-lane/crates/avl-worker/avl-worker-test.exe
_main/plugins/air/tests/integration/vm-lane/crates/avl-worker/avl-worker-test_libtest.exe C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/local_windows-fastbuild/bin/plugins/air/tests/integration/vm-lane/crates/avl-worker/avl-worker-test_libtest.exe
_main/plugins/air/tests/integration/vm-lane/docker/Dockerfile C:/dev/iw/plugins/air/tests/integration/vm-lane/docker/Dockerfile
_main/plugins/air/tests/integration/vm-lane/docker/air-display C:/dev/iw/plugins/air/tests/integration/vm-lane/docker/air-display
_main/plugins/air/tests/integration/vm-lane/tart.MODULE.bazel C:/dev/iw/plugins/air/tests/integration/vm-lane/tart.MODULE.bazel
_repo_mapping C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/local_windows-fastbuild/bin/plugins/air/tests/integration/vm-lane/crates/avl-worker/avl-worker-test.exe.repo_mapping
";

#[test]
fn a_manifest_that_a_windows_bazel_wrote_parses_and_maps() {
    let manifest = RunfilesManifest::parse(WINDOWS_MANIFEST).unwrap();
    assert_eq!(manifest.entries.len(), 9);
    assert!(
        manifest
            .entries
            .iter()
            .all(|entry| entry.target.starts_with("C:/") && !entry.target.contains('\\')),
        "{manifest:?}"
    );
    assert_eq!(
        manifest.resolve("_main/plugins/air/tests/integration/vm-lane/crates/avl-testkit/avl-fake.exe"),
        Some(concat!(
            "C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/local_windows-fastbuild/bin/",
            "plugins/air/tests/integration/vm-lane/crates/avl-testkit/avl-fake.exe"
        ))
    );
    // The two shares of that host hold every target: the checkout, and the Bazel root in its true case.
    let table = PathMap::new(vec![
        PathPrefix::new("C:/dev/iw", "/mnt/AirVmShares/repo"),
        PathPrefix::new("C:/ProgramData/_bazel", "/mnt/AirVmShares/bazel"),
    ]);
    for entry in &manifest.entries {
        let guest = table
            .map(&entry.target)
            .unwrap_or_else(|| panic!("{} is outside the table", entry.target));
        assert!(guest.starts_with("/mnt/AirVmShares/"), "{guest}");
    }
    let versions = manifest.resolve("_main/.agents/skills/vm-ui-tests/provision/versions.env").unwrap();
    assert_eq!(
        table.map(versions).as_deref(),
        Some("/mnt/AirVmShares/repo/.agents/skills/vm-ui-tests/provision/versions.env")
    );
}

/// The name depends on every input but the bytes flag, so the host predicts the root the guest builds.
#[test]
fn the_tree_digest_names_the_manifest_the_table_the_staged_runfiles_and_the_copy_rule() {
    let table = PathMap::new(vec![PathPrefix::new("C:/b", "/mnt/b")]);
    let base = request("_main/a C:/b/a\n", table.clone());
    let digest = tree_digest(&base);
    assert!(crate::stage::is_sha256_hex(&digest), "{digest}");
    assert_eq!(digest, tree_digest(&base));
    assert_eq!(
        digest,
        tree_digest(&RunfilesTreeRequest {
            with_bytes: true,
            ..base.clone()
        })
    );
    assert_ne!(digest, tree_digest(&request("_main/a C:/b/b\n", table)));
    assert_ne!(
        digest,
        tree_digest(&request("_main/a C:/b/a\n", PathMap::new(vec![PathPrefix::new("C:/b", "/mnt/c")])))
    );
    assert_ne!(digest, tree_digest(&request("_main/a C:/b/a\n", PathMap::default())));
    let staged = RunfilesTreeRequest {
        staged: vec![StagedRunfile {
            path: "_main/lock.yaml".to_owned(),
            sha256: "ab".to_owned(),
            size: 2,
        }],
        ..base.clone()
    };
    assert_ne!(digest, tree_digest(&staged));
    assert_ne!(
        digest,
        tree_digest(&RunfilesTreeRequest {
            copy_package_stores: true,
            ..base
        })
    );
}

/// The request is one JSON line, and the bytes of the staged runfiles follow it unchanged.
#[test]
fn the_stdin_is_the_request_line_and_then_the_bytes() {
    let request = request("_main/a C:/b/a\n", PathMap::default());
    let stdin = request_stdin(&request, &[b"one\n".to_vec(), b"two".to_vec()]);
    let (line, bytes) = split_request_stdin(&stdin);
    assert_eq!(serde_json::from_slice::<RunfilesTreeRequest>(line).unwrap(), request);
    assert_eq!(bytes, b"one\ntwo");
    assert_eq!(split_request_stdin(b"{}"), (&b"{}"[..], &b""[..]));
}
