use std::path::{Path, PathBuf};

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_leading_tilde_is_the_home_directory() {
    let home = Home::new(PathBuf::from("/Users/me"));
    assert_eq!(home.expand("~/projects/idea-1"), Path::new("/Users/me/projects/idea-1"));
    assert_eq!(home.expand("~"), Path::new("/Users/me"));
    assert_eq!(home.expand("/srv/repo"), Path::new("/srv/repo"));
    assert_eq!(home.expand("~other/repo"), Path::new("~other/repo"));
}

#[test]
fn the_files_of_the_agent_are_under_config_and_cache() {
    let home = Home::new(PathBuf::from("/h"));
    assert_eq!(home.allow_file(), Path::new("/h/.config/yonderd/clients"));
    assert_eq!(home.runs_dir(), Path::new("/h/.cache/yonderd/runs"));
    assert_eq!(home.leases_dir(), Path::new("/h/.cache/yonderd/leases"));
    assert_eq!(
        home.launch_agent_file(),
        Path::new("/h/Library/LaunchAgents/com.jetbrains.yonderd.plist")
    );
}
