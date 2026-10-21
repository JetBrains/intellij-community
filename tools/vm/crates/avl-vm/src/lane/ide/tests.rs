use avl_base::GuestOs;
use avl_wire::ide::IdeRun;
use pretty_assertions::assert_eq;

use super::*;
use crate::lane::testing::pool;

#[test]
fn the_ide_contexts_live_under_the_guest_data_root() {
    let settings = pool(GuestOs::Linux);
    assert_eq!(guest_ide_root(&settings), format!("{}/ide", settings.vm_data));
}

#[test]
fn the_gc_note_names_what_the_gc_did() {
    let run = |key: &str| IdeRun {
        launch_key: key.to_owned(),
        run_id: format!("run-ide-{key}"),
    };
    let result = |kept: usize, stopped: usize| IdeGcResult {
        kept: vec![run("kept"); kept],
        stopped: vec![run("stopped"); stopped],
        removed: vec!["/vm/data/ide/kept/log/old".to_owned()],
        ..IdeGcResult::default()
    };
    assert_eq!(gc_note(&result(0, 0)), None);
    assert_eq!(gc_note(&result(1, 0)).as_deref(), Some("kept 1 lane IDE(s) of this product"));
    assert_eq!(gc_note(&result(0, 2)).as_deref(), Some("stopped 2 lane IDE(s)"));
    assert_eq!(
        gc_note(&result(1, 2)).as_deref(),
        Some("kept 1 lane IDE(s) of this product and stopped 2")
    );
    let stuck = IdeGcResult {
        killed: vec![run("killed")],
        abandoned: vec![run("abandoned")],
        ..IdeGcResult::default()
    };
    assert_eq!(
        gc_note(&stuck).as_deref(),
        Some(
            "killed 1 lane IDE(s) whose cancel did not end in time; the processes of the lane IDE run(s) run-ide-abandoned \
             outlived a kill; their slots are free, and `pool recycle` discards them"
        )
    );
}
