use std::path::Path;

use avl_base::{Environment, GuestOs, Selection};
use avl_wire::path_map::PathMap;
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::fixture_backend;

/// A Linux-guest config whose host paths are the two Windows roots of a real host.
fn windows_settings(root: &Path) -> Config {
    let settings = Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: fixture_backend(GuestOs::Linux),
            guest_os: GuestOs::Linux,
        },
        &Environment::from_pairs([("HOME", root.to_string_lossy().into_owned()), ("AIR_VM_DATA", "/data".to_owned())]),
        root,
    )
    .unwrap();
    settings.set_host_paths(r"C:\dev\iw", r"C:\ProgramData\_bazel").unwrap();
    settings
}

/// A MANIFEST in the form Bazel writes on Windows: forward slashes and a lower-case output root, and a symlink
/// runfile whose target is the link's text.
const WINDOWS_MANIFEST: &str = "_main/.agents/versions.env C:/dev/iw/.agents/versions.env\n\
                                _main/lib/a.jar C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/bin/a.jar\n\
                                _main/pkg/node_modules/a ../../lib/a.jar\n\
                                _main/empty.txt \n";

/// A descriptor whose only runfiles are a MANIFEST beside it, as on a Windows host.
fn windows_descriptor(directory: &Path) -> (PathBuf, String) {
    let descriptor = directory.join("ui_daemon.runtime.json");
    std::fs::write(&manifest_paths(&descriptor)[0], WINDOWS_MANIFEST).unwrap();
    (descriptor, WINDOWS_MANIFEST.to_owned())
}

/// A host reads the MANIFEST even beside a tree. The lane build builds no tree, so a tree on disk is one of an earlier
/// build, and a runfile that the build added or removed since is not in it.
#[test]
fn a_host_reads_the_manifest_even_beside_a_tree() {
    let directory = tempfile::tempdir().unwrap();
    let descriptor = directory.path().join("ui_daemon.runtime.json");
    let root = runfiles_root(&descriptor);
    std::fs::create_dir_all(root.join("_main/lib")).unwrap();
    std::fs::write(root.join("MANIFEST"), WINDOWS_MANIFEST).unwrap();
    std::fs::write(&manifest_paths(&descriptor)[0], WINDOWS_MANIFEST).unwrap();
    assert!(HostRunfiles::present(&descriptor));
    let runfiles = HostRunfiles::of_on(&descriptor, HostOs::Macos).unwrap();
    assert!(matches!(runfiles, HostRunfiles::Manifest { .. }), "{runfiles:?}");
    assert_ne!(runfiles.host_path("_main/lib/a.jar"), Some(root.join("_main/lib/a.jar")));
}

#[test]
fn a_host_with_only_a_manifest_reads_each_runfile_at_its_target() {
    let directory = tempfile::tempdir().unwrap();
    let (descriptor, _) = windows_descriptor(directory.path());
    assert!(HostRunfiles::present(&descriptor));
    let runfiles = HostRunfiles::of(&descriptor).unwrap();
    assert_eq!(runfiles.location(), manifest_paths(&descriptor)[0]);
    assert_eq!(
        runfiles.host_path("_main/lib/a.jar"),
        Some(PathBuf::from("C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/bin/a.jar"))
    );
    // A symlink runfile is read at the file its chain ends at.
    assert_eq!(
        runfiles.host_path("_main/pkg/node_modules/a"),
        Some(PathBuf::from("C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/bin/a.jar"))
    );
    // An empty target is an empty file, which no declared input is, and a path with no line is not a runfile.
    assert_eq!(runfiles.host_path("_main/empty.txt"), None);
    assert_eq!(runfiles.host_path("_main/absent.jar"), None);
}

/// A runfiles directory that holds only what Bazel writes without a tree is no tree, and its `MANIFEST` is read
/// when there is none beside the descriptor.
#[test]
fn a_runfiles_directory_with_only_a_manifest_is_no_tree() {
    let directory = tempfile::tempdir().unwrap();
    let descriptor = directory.path().join("ui_daemon.runtime.json");
    let root = runfiles_root(&descriptor);
    std::fs::create_dir_all(&root).unwrap();
    std::fs::write(root.join("_repo_mapping"), "").unwrap();
    std::fs::write(root.join("MANIFEST"), WINDOWS_MANIFEST).unwrap();
    let runfiles = HostRunfiles::of(&descriptor).unwrap();
    assert_eq!(runfiles.location(), root.join("MANIFEST"));
    assert!(matches!(runfiles, HostRunfiles::Manifest { .. }));
}

/// Windows Bazel writes `MANIFEST` and an empty `_main` directory into the runfiles directory. The empty directory is
/// no tree, so the MANIFEST is read and the runfile is found at its target.
#[test]
fn a_windows_runfiles_directory_with_an_empty_main_is_read_through_its_manifest() {
    let directory = tempfile::tempdir().unwrap();
    let descriptor = directory.path().join("ui_daemon.runtime.json");
    let root = runfiles_root(&descriptor);
    std::fs::create_dir_all(root.join("_main")).unwrap();
    std::fs::write(root.join("MANIFEST"), WINDOWS_MANIFEST).unwrap();
    assert!(HostRunfiles::present(&descriptor));
    let runfiles = HostRunfiles::of(&descriptor).unwrap();
    assert_eq!(runfiles.location(), root.join("MANIFEST"));
    assert_eq!(
        runfiles.host_path("_main/lib/a.jar"),
        Some(PathBuf::from("C:/programdata/_bazel/kxsaieyx/execroot/_main/bazel-out/bin/a.jar"))
    );
}

/// On a Windows host a target is resolved through its junctions before the guest sees it, because the guest's mount
/// cannot read a junction. A symbolic link stands in for the junction here, and the rendered bytes carry the result.
#[cfg(unix)]
#[test]
fn a_windows_host_resolves_each_target_through_its_links_before_the_guest_sees_it() {
    let directory = tempfile::tempdir().unwrap();
    let real = directory.path().join("cache/contents/kotlin-stdlib.jar");
    std::fs::create_dir_all(real.parent().unwrap()).unwrap();
    std::fs::write(&real, b"jar").unwrap();
    let external = directory.path().join("external");
    std::os::unix::fs::symlink(directory.path().join("cache/contents"), &external).unwrap();
    let descriptor = directory.path().join("ui_daemon.runtime.json");
    let linked = external.join("kotlin-stdlib.jar");
    std::fs::write(
        &manifest_paths(&descriptor)[0],
        format!("_main/lib/kotlin-stdlib.jar {}\n", linked.display()),
    )
    .unwrap();
    let resolved = fscopy::resolve_links(&real).unwrap().to_string_lossy().into_owned();

    let runfiles = HostRunfiles::of_on(&descriptor, HostOs::Windows).unwrap();
    assert_eq!(runfiles.host_path("_main/lib/kotlin-stdlib.jar"), Some(PathBuf::from(&resolved)));
    let HostRunfiles::Manifest { bytes, .. } = &runfiles else {
        panic!("a MANIFEST");
    };
    assert_eq!(
        String::from_utf8(bytes.clone()).unwrap(),
        format!("_main/lib/kotlin-stdlib.jar {resolved}\n")
    );
    // A Unix host keeps the target as written.
    let kept = HostRunfiles::of_on(&descriptor, HostOs::Linux).unwrap();
    assert_eq!(kept.host_path("_main/lib/kotlin-stdlib.jar"), Some(linked));
}

/// With neither a tree nor a MANIFEST the answer is the tree, which is not present, so the build refuses by name.
#[test]
fn a_host_with_neither_answers_the_missing_tree() {
    let directory = tempfile::tempdir().unwrap();
    let descriptor = directory.path().join("ui_daemon.runtime.json");
    assert!(!HostRunfiles::present(&descriptor));
    assert_eq!(
        HostRunfiles::of(&descriptor).unwrap(),
        HostRunfiles::Tree(runfiles_root(&descriptor))
    );
}

/// A MANIFEST that is there and cannot be read is refused by name, not taken for no runfiles.
#[test]
fn an_unreadable_manifest_is_refused_by_name() {
    let directory = tempfile::tempdir().unwrap();
    let descriptor = directory.path().join("ui_daemon.runtime.json");
    std::fs::write(&manifest_paths(&descriptor)[0], [0xff, 0xfe]).unwrap();
    let refusal = HostRunfiles::of(&descriptor).unwrap_err();
    assert_eq!(refusal.code, "runfiles_manifest_unreadable");
    assert!(
        refusal.message.contains("ui_daemon.runtime.json.runfiles_manifest"),
        "{}",
        refusal.message
    );
}

/// The inputs of a guest tree name the checkout, the table of the shares and the destination of the trees.
#[test]
fn the_tree_inputs_name_the_checkout_the_table_and_the_destination() {
    let directory = tempfile::tempdir().unwrap();
    let settings = windows_settings(directory.path());
    let inputs = GuestTreeInputs::of(&settings).unwrap();
    assert_eq!(guest_runfiles_destination(&settings), "/data/runfiles");
    assert_eq!(inputs.destination, "/data/runfiles");
    assert_eq!(inputs.host_repo, PathBuf::from(r"C:\dev\iw"));
    assert_eq!(inputs.path_map, GuestPaths::of(&settings).unwrap().map().clone());
}

/// A checkout, a Bazel root whose external repository links into the checkout, and the MANIFEST of a descriptor over
/// both, as a Unix host has them.
#[cfg(unix)]
struct UnixHost {
    _directory: tempfile::TempDir,
    repo: PathBuf,
    bazel: PathBuf,
    descriptor: PathBuf,
}

#[cfg(unix)]
impl UnixHost {
    fn new() -> Self {
        let directory = tempfile::tempdir().unwrap();
        let root = fscopy::resolve_links(directory.path()).unwrap();
        let (repo, bazel) = (root.join("repo"), root.join("bazel"));
        let write = |path: &Path, content: &str| {
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(path, content).unwrap();
        };
        write(&bazel.join("out/a.jar"), "jar");
        write(&repo.join("tools/pnpm-lock.yaml"), "lock: 1\n");
        write(&repo.join("community/java/x/rt.jar"), "rt");
        write(&repo.join("community/java/annotations/b.xml"), "b");
        write(&repo.join("community/java/annotations/c/d.xml"), "dd");
        std::fs::create_dir_all(bazel.join("external")).unwrap();
        std::os::unix::fs::symlink(repo.join("community"), bazel.join("external/community+")).unwrap();
        let descriptor = bazel.join("ui_daemon.runtime.json");
        let community = bazel.join("external/community+");
        let manifest = format!(
            "_main/lib/a.jar {}\n_main/tools/pnpm-lock.yaml {}\ncommunity+/java/x/rt.jar {}\ncommunity+/java/annotations {}\n\
             _main/lib/alias ../lib/a.jar\n_main/empty \n",
            bazel.join("out/a.jar").display(),
            repo.join("tools/pnpm-lock.yaml").display(),
            community.join("java/x/rt.jar").display(),
            community.join("java/annotations").display(),
        );
        std::fs::write(&manifest_paths(&descriptor)[0], manifest).unwrap();
        Self {
            _directory: directory,
            repo,
            bazel,
            descriptor,
        }
    }

    fn inputs(&self, host: HostOs) -> GuestTreeInputs {
        GuestTreeInputs {
            host_repo: self.repo.clone(),
            path_map: PathMap::new(vec![avl_wire::path_map::PathPrefix::identity(self.bazel.to_string_lossy())]),
            destination: "/data/runfiles".to_owned(),
            host,
        }
    }
}

/// A target on the Bazel share stays a MANIFEST line for the guest to link. A target in the checkout, directly or
/// through a link of the Bazel root, is a staged runfile with its digest and its length, and a directory there is one
/// staged runfile per file. The host names the root: the destination, then the digest of the request.
#[cfg(unix)]
#[test]
fn the_guest_tree_links_a_share_target_and_stages_a_checkout_target() {
    let host = UnixHost::new();
    let runfiles = HostRunfiles::of_on(&host.descriptor, HostOs::Linux).unwrap();
    let tree = runfiles.guest_tree(&host.inputs(HostOs::Linux)).unwrap();
    assert_eq!(
        tree.request.manifest_text,
        format!(
            "_main/lib/a.jar {}\n_main/lib/alias ../lib/a.jar\n_main/empty \n",
            host.bazel.join("out/a.jar").display()
        )
    );
    let sha = |text: &str| hex::encode(Sha256::digest(text.as_bytes()));
    let staged = |path: &str, text: &str| StagedRunfile {
        path: path.to_owned(),
        sha256: sha(text),
        size: text.len() as u64,
    };
    assert_eq!(
        tree.request.staged,
        [
            staged("_main/tools/pnpm-lock.yaml", "lock: 1\n"),
            staged("community+/java/x/rt.jar", "rt"),
            staged("community+/java/annotations/b.xml", "b"),
            staged("community+/java/annotations/c/d.xml", "dd"),
        ]
    );
    assert_eq!(
        tree.staged_files,
        [
            host.repo.join("tools/pnpm-lock.yaml"),
            host.repo.join("community/java/x/rt.jar"),
            host.repo.join("community/java/annotations/b.xml"),
            host.repo.join("community/java/annotations/c/d.xml"),
        ]
    );
    assert!(!tree.request.with_bytes && !tree.request.copy_package_stores);
    assert_eq!(tree.root, format!("/data/runfiles/{}", tree_digest(&tree.request)));

    // The tree Bazel built gives the same plan as its MANIFEST, and a Windows host copies the package stores.
    let windows = runfiles.guest_tree(&host.inputs(HostOs::Windows)).unwrap();
    assert!(windows.request.copy_package_stores);
    assert_ne!(windows.root, tree.root);
}

/// A tree without a MANIFEST gives the guest nothing to build from, so it is refused by name.
#[test]
fn a_tree_without_a_manifest_is_refused_by_name() {
    let directory = tempfile::tempdir().unwrap();
    let tree = HostRunfiles::Tree(directory.path().join("ui_daemon.runtime.json.runfiles"));
    let inputs = GuestTreeInputs {
        host_repo: directory.path().join("repo"),
        path_map: PathMap::default(),
        destination: "/data/runfiles".to_owned(),
        host: HostOs::Linux,
    };
    let refusal = tree.guest_tree(&inputs).unwrap_err();
    assert_eq!(refusal.code, "runfiles_manifest_missing");
    assert!(refusal.message.contains("ui_daemon.runtime.json.runfiles"), "{}", refusal.message);
}
