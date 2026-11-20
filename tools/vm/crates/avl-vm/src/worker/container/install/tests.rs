//! The copy of the pinned Apple `container` install, over a fake install and a fake `codesign` in a temporary
//! directory. The rule compares devices, so it runs on every Unix host.

use std::fs;
use std::os::unix::fs::PermissionsExt as _;
use std::sync::Arc;

use avl_host_testkit::{path_runner, quiet};
use avl_testkit::fake_executable;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;
use crate::worker::container::{AppleContainer, SystemStatus};
use crate::worker::testing::Fixture;

/// The name of the install root, as the Bazel repository cache names a directory of its contents.
const PIN: &str = "5c812256-71c9-42a3-8166-80cace4e991a";

/// A fake install in `<tmp>/cache/<hash>/<PIN>`, laid out as the archive of `container.MODULE.bazel`, with the files
/// of the repository beside `bin` and `libexec`, and a fake `codesign` that logs its arguments and fails when
/// `codesign.fail` exists beside it.
struct Install {
    directory: TempDir,
    cli: PathBuf,
    codesign: PathBuf,
}

impl Install {
    fn new() -> Self {
        let directory = TempDir::new().unwrap();
        let root = directory.path().join("cache/1cd8e43f").join(PIN);
        for part in SIGNED_PARTS {
            let path = root.join(part);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fake_executable(path.parent().unwrap(), path.file_name().unwrap().to_str().unwrap(), "exit 0\n").unwrap();
        }
        fs::write(
            root.join("libexec/container/plugins/container-network-vmnet/config.toml"),
            "[plugin]\n",
        )
        .unwrap();
        fs::write(root.join("BUILD.bazel"), "filegroup()\n").unwrap();
        // A pin may be read-only, as Bazel leaves some outputs.
        fs::set_permissions(root.join("libexec"), fs::Permissions::from_mode(0o555)).unwrap();
        let tools = directory.path().join("tools");
        fs::create_dir_all(&tools).unwrap();
        let codesign = fake_executable(
            &tools,
            "codesign",
            "echo \"$*\" >> \"$(dirname \"$0\")/codesign.log\"\n\
             if [ -f \"$(dirname \"$0\")/codesign.fail\" ]; then echo 'a sealed resource is missing or invalid' >&2; exit 1; fi\n",
        )
        .unwrap();
        Self {
            cli: root.join("bin/container"),
            directory,
            codesign,
        }
    }

    fn runtime_root(&self) -> PathBuf {
        self.directory.path().join("runtime")
    }

    fn copies(&self) -> PathBuf {
        self.runtime_root().join(INSTALL_COPIES)
    }

    /// The lines `codesign` logged so far.
    fn verified(&self) -> Vec<String> {
        fs::read_to_string(self.codesign.with_file_name("codesign.log"))
            .unwrap_or_default()
            .lines()
            .map(str::to_owned)
            .collect()
    }

    async fn copy(&self) -> Result<PathBuf, Refusal> {
        copy_of(
            &Ctx::background(),
            &path_runner(),
            &quiet(),
            self.codesign.to_str().unwrap(),
            &self.cli,
            &self.copies(),
        )
        .await
    }

    /// The names in the directory of the copies.
    fn entries(&self) -> Vec<String> {
        let mut names: Vec<String> = fs::read_dir(self.copies())
            .map(|entries| {
                entries
                    .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
                    .collect()
            })
            .unwrap_or_default();
        names.sort();
        names
    }
}

impl Drop for Install {
    /// Makes the read-only directory of the pin writable again, so the temporary directory goes.
    fn drop(&mut self) {
        let libexec = install_root_of(&self.cli).join("libexec");
        let _ = fs::set_permissions(libexec, fs::Permissions::from_mode(0o755));
    }
}

/// The rule is the device: a directory and its child are on one volume, and `/dev` is a file system of its own on
/// macOS and on Linux.
#[test]
fn the_volume_is_the_device_of_the_directory() {
    let directory = TempDir::new().unwrap();
    let child = directory.path().join("child");
    fs::create_dir(&child).unwrap();
    assert!(on_one_volume(directory.path(), &child).unwrap());
    assert!(!on_one_volume(directory.path(), Path::new("/dev")).unwrap());
    on_one_volume(directory.path(), &directory.path().join("gone")).unwrap_err();
}

/// A pin on the volume of the runtime root runs as it is, and nothing is copied.
#[tokio::test]
async fn a_pin_on_the_volume_of_the_runtime_root_runs_as_it_is() {
    let install = Install::new();
    let runnable = runnable_cli(&Ctx::background(), &path_runner(), &quiet(), &install.cli, &install.runtime_root())
        .await
        .unwrap();
    assert_eq!(runnable, install.cli);
    assert!(!install.copies().exists());
}

/// The first command copies `bin` and `libexec` of the install into `<copies>/<pin>`, verifies the three signed
/// executables in the temporary directory before the rename, and runs the CLI of the copy. The next command reuses it
/// without a verification, and nothing is left beside it.
#[tokio::test]
async fn a_pin_on_another_volume_runs_from_one_verified_copy() {
    let install = Install::new();
    let copy = install.copies().join(PIN);

    let runnable = install.copy().await.unwrap();
    assert_eq!(runnable, copy.join("bin/container"));
    assert_eq!(fs::metadata(&runnable).unwrap().permissions().mode() & 0o777, 0o755);
    assert_eq!(
        fs::read_to_string(copy.join("libexec/container/plugins/container-network-vmnet/config.toml")).unwrap(),
        "[plugin]\n"
    );
    assert!(!copy.join("BUILD.bazel").exists());
    assert_eq!(fs::metadata(copy.join("libexec")).unwrap().permissions().mode() & 0o777, 0o755);
    let verified = install.verified();
    assert_eq!(verified.len(), SIGNED_PARTS.len(), "{verified:#?}");
    for (line, part) in verified.iter().zip(SIGNED_PARTS) {
        let (flags, path) = line.split_at("--verify --strict ".len());
        assert_eq!(flags, "--verify --strict ");
        let staged = Path::new(path).strip_prefix(install.copies()).unwrap();
        let first = staged.components().next().unwrap().as_os_str().to_string_lossy().into_owned();
        assert!(first.starts_with(&format!(".{PIN}-")) && first.ends_with(".tmp"), "{line}");
        assert!(staged.ends_with(part), "{line}");
    }
    let record: CopyRecord = serde_json::from_slice(&fs::read(copy.join(COPY_RECORD)).unwrap()).unwrap();
    assert_eq!(record.source, install_root_of(&install.cli));

    assert_eq!(install.copy().await.unwrap(), runnable);
    assert_eq!(install.verified().len(), SIGNED_PARTS.len(), "the second command verified again");
    assert_eq!(install.entries(), [PIN]);
}

/// A copy of another source under the same name, such as an install that Bazel extracted again, is replaced, and the
/// old copy goes.
#[tokio::test]
async fn a_changed_install_of_the_same_name_is_copied_again() {
    let install = Install::new();
    install.copy().await.unwrap();
    fs::write(&install.cli, "#!/bin/sh\necho another build\n").unwrap();

    let runnable = install.copy().await.unwrap();
    assert_eq!(fs::read_to_string(&runnable).unwrap(), "#!/bin/sh\necho another build\n");
    assert_eq!(install.verified().len(), 2 * SIGNED_PARTS.len());
    assert_eq!(install.entries(), [PIN]);
}

/// A copy that fails `codesign --verify --strict` is refused `container_copy_failed`, names the file and the way out,
/// and leaves nothing behind.
#[tokio::test]
async fn a_copy_that_fails_its_signature_check_is_not_used() {
    let install = Install::new();
    fs::write(install.codesign.with_file_name("codesign.fail"), "").unwrap();
    let refusal = install.copy().await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("container_copy_failed", Exit::UNAVAILABLE));
    for expected in [
        "bin/container fails `codesign --verify --strict` with exit 1",
        "a sealed resource is missing or invalid",
        "CONTAINER_BIN",
    ] {
        assert!(refusal.message.contains(expected), "{expected:?} in {}", refusal.message);
    }
    assert_eq!(install.entries(), Vec::<String>::new());
}

/// The prune keeps the copy of the server and a temporary directory a live command may still fill. It removes another
/// pin and a temporary directory older than an hour.
#[test]
fn the_prune_keeps_the_copy_of_the_server_and_a_live_temporary() {
    let install = Install::new();
    let copies = install.copies();
    for name in [
        PIN,
        "a09fec11-68c7-4eeb-b820-53b1094f815e",
        ".5c812256-live.tmp",
        ".5c812256-dead.tmp",
    ] {
        fs::create_dir_all(copies.join(name).join("bin")).unwrap();
    }
    let dead = fs::File::open(copies.join(".5c812256-dead.tmp")).unwrap();
    dead.set_modified(SystemTime::now() - Duration::from_hours(2)).unwrap();

    let notes = prune_other_copies(&copies.join(PIN));
    assert_eq!(notes.len(), 2, "{notes:#?}");
    assert!(notes.iter().all(|note| note.starts_with("removed ")), "{notes:#?}");
    assert_eq!(install.entries(), [".5c812256-live.tmp", PIN]);
}

/// The check of a server that runs from the copy of this pin prunes the other copies. A server of a CLI outside the
/// copies prunes nothing.
#[test]
fn a_server_of_the_copy_prunes_the_other_copies() {
    let fixture = Fixture::docker_container();
    let copies = fixture.settings.runtime_root.join(INSTALL_COPIES);
    let other = copies.join("a09fec11-68c7-4eeb-b820-53b1094f815e");
    fs::create_dir_all(&other).unwrap();
    let runner = fixture.manager.runner().clone();
    let apple = AppleContainer::new(
        Arc::clone(&fixture.settings),
        runner.clone(),
        quiet(),
        Arc::new(avl_host_sys::lock::LockManager::new(runner)),
    );
    let status = |root: &Path| SystemStatus {
        status: "running".to_owned(),
        paths: crate::worker::container::StatusPaths {
            install_root: root.to_string_lossy().into_owned(),
            ..Default::default()
        },
        ..Default::default()
    };

    let outside = Path::new("/opt/container/bin/container");
    apple.require_own_server(&status(Path::new("/opt/container")), outside).unwrap();
    assert!(other.exists());

    let own = copies.join(PIN);
    apple.require_own_server(&status(&own), &own.join("bin/container")).unwrap();
    assert!(!other.exists());
}
