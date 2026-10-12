//! The IDE root's retention.

use std::fs;
use std::io;
use std::path::Path;
use std::time::SystemTime;

/// Keeps the newest `keep` run directories under `root`, the current run always among them, and removes the rest.
///
/// It runs only for the IDE's root, `<home>/out/air-traces`, which nothing else cleans: a daemon iteration's root
/// goes with the iteration and a Bazel test's with its outputs. Newest is by modification time, which a run's
/// directory gains each time a scenario of it starts; a tie goes by name.
pub(crate) fn prune_runs(root: &Path, current: &str, keep: usize) -> io::Result<()> {
    let mut runs: Vec<(SystemTime, String)> = fs::read_dir(root)?
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let name = entry.file_name().into_string().ok()?;
            let metadata = entry.metadata().ok()?;
            (metadata.is_dir() && name != current).then_some((metadata.modified().ok()?, name))
        })
        .collect();
    if runs.len() < keep {
        return Ok(());
    }
    runs.sort_by(|a, b| b.0.cmp(&a.0).then_with(|| a.1.cmp(&b.1)));
    let mut failure = None;
    for (_, stale) in runs.split_off(keep.saturating_sub(1)) {
        if let Err(error) = fs::remove_dir_all(root.join(&stale)) {
            failure.get_or_insert(error);
        }
    }
    failure.map_or(Ok(()), Err)
}

#[cfg(test)]
mod tests {
    use std::fs::{self, FileTimes};
    use std::time::{Duration, SystemTime};

    use avl_trace::bundle::IDE_ROOT_RETAINED_RUNS;

    use super::*;

    /// A directory opened so that its times can be set: Windows opens one only with backup semantics, and sets its
    /// times only through a handle with the attribute-write right.
    fn open_dir(dir: &Path) -> io::Result<fs::File> {
        #[cfg(windows)]
        {
            use std::os::windows::fs::OpenOptionsExt;
            const FILE_WRITE_ATTRIBUTES: u32 = 0x0100;
            const FILE_FLAG_BACKUP_SEMANTICS: u32 = 0x0200_0000;
            fs::OpenOptions::new()
                .access_mode(FILE_WRITE_ATTRIBUTES)
                .custom_flags(FILE_FLAG_BACKUP_SEMANTICS)
                .open(dir)
        }
        #[cfg(not(windows))]
        {
            fs::File::open(dir)
        }
    }

    #[test]
    fn the_ide_root_keeps_its_newest_runs_and_the_current_one() {
        let root = tempfile::tempdir().unwrap();
        let base = SystemTime::now() - Duration::from_secs(3600);
        for index in 0..25_u64 {
            let dir = root.path().join(format!("run-{index:02}"));
            fs::create_dir(&dir).unwrap();
            // The current run is the oldest by its time, and is kept anyway.
            let modified = if index == 0 {
                base - Duration::from_secs(3600)
            } else {
                base + Duration::from_secs(60 * index)
            };
            open_dir(&dir).unwrap().set_times(FileTimes::new().set_modified(modified)).unwrap();
        }
        prune_runs(root.path(), "run-00", IDE_ROOT_RETAINED_RUNS).unwrap();
        let kept: Vec<String> = fs::read_dir(root.path())
            .unwrap()
            .map(|entry| entry.unwrap().file_name().into_string().unwrap())
            .collect();
        assert_eq!(kept.len(), IDE_ROOT_RETAINED_RUNS, "kept {kept:?}");
        for name in ["run-00", "run-24", "run-06"] {
            assert!(kept.contains(&name.to_owned()), "{name} was removed: {kept:?}");
        }
        for name in ["run-05", "run-01"] {
            assert!(!kept.contains(&name.to_owned()), "{name} was kept: {kept:?}");
        }
    }
}
