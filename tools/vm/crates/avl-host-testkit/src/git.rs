//! The host `git`, as a fake the settings name instead of the host's (`AIR_VM_HOST_GIT`). On Unix it is a script that
//! every fake `git` of every test process shares. On Windows it is the program of [`avl_testkit::fakebin`], which
//! reads the head from a file.

use std::path::{Path, PathBuf};

use tempfile::TempDir;

#[cfg(test)]
mod tests;

/// The head the fake `git` answers.
pub const FAKE_HEAD: &str = "1e46d8efe7b9e0000000000000000000000000000";

/// A fake `git` that records every argv. It calls the checkout a working tree, answers [`FAKE_HEAD`] to any other
/// `rev-parse`, and the status output a test writes to `status`.
pub struct FakeGit {
    directory: TempDir,
    path: PathBuf,
}

impl FakeGit {
    pub fn install() -> Self {
        let directory = tempfile::tempdir().expect("a directory for the fake git");
        let path = place(directory.path());
        let git = Self { directory, path };
        git.status(&[]);
        git
    }

    /// What the settings' git variable has to be set to.
    pub fn path(&self) -> &Path {
        &self.path
    }

    /// Sets the `git status --porcelain=v1 -z` output: one NUL-terminated record per entry.
    pub fn status(&self, records: &[&str]) {
        let output: String = records.iter().map(|record| format!("{record}\0")).collect();
        self.flag("status.txt", &output);
    }

    /// Makes every later call fail the way `git` does outside a checkout.
    pub fn fail(&self) {
        self.flag("fails.txt", "1");
    }

    /// Makes `rev-parse --is-inside-work-tree` answer `false`, exit 1: a configured checkout that is not a working
    /// tree.
    pub fn outside_work_tree(&self) {
        self.flag("outside.txt", "1");
    }

    /// Every argv so far, joined by spaces.
    pub fn calls(&self) -> Vec<String> {
        std::fs::read_to_string(self.directory.path().join("calls.txt"))
            .unwrap_or_default()
            .lines()
            .map(str::to_owned)
            .collect()
    }

    fn flag(&self, name: &str, content: &str) {
        std::fs::write(self.directory.path().join(name), content)
            .unwrap_or_else(|error| panic!("the fake git's {name} is not written: {error}"));
    }
}

/// Links the fake `git` into `directory` and returns its path. The script finds its files beside itself.
#[cfg(unix)]
fn place(directory: &Path) -> PathBuf {
    // `--is-inside-work-tree` first: it is a `rev-parse` too.
    let script = format!(
        "dir=$(dirname \"$0\")\n\
         printf '%s\\n' \"$*\" >> \"$dir/calls.txt\"\n\
         if [ -f \"$dir/fails.txt\" ]; then\n\
         \x20 echo 'fatal: not a git repository (or any of the parent directories): .git' >&2\n\
         \x20 exit 128\n\
         fi\n\
         case \" $* \" in\n\
         \x20 *\" --is-inside-work-tree \"*)\n\
         \x20   if [ -f \"$dir/outside.txt\" ]; then echo false; exit 1; fi\n\
         \x20   echo true ;;\n\
         \x20 *\" rev-parse \"*) printf '{FAKE_HEAD}\\n' ;;\n\
         \x20 *\" status \"*) cat \"$dir/status.txt\" ;;\n\
         esac\n"
    );
    avl_testkit::shared_fake_executable(directory, "git", &script).expect("the fake git is written")
}

/// Links the fake program into `directory` as `git.exe`, with the head it answers beside it.
#[cfg(windows)]
fn place(directory: &Path) -> PathBuf {
    std::fs::write(directory.join(avl_testkit::fakebin::GIT_HEAD), format!("{FAKE_HEAD}\n")).expect("the head of the fake git is written");
    avl_testkit::fakebin::place(directory, "git")
}
