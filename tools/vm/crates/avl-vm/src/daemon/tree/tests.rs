use avl_host_sys::Ctx;
use avl_host_testkit::git::FAKE_HEAD;
use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;

async fn read(fixture: &Fixture) -> Result<Tree, Refusal> {
    read_checkout_tree(&Ctx::background(), &fixture.runner, &fixture.settings).await
}

// A clean tree names its head, an empty list and a zero count. The status asks only for the two lane roots and takes
// no optional lock, because other sessions use the same index.
#[tokio::test]
async fn a_clean_tree_names_its_head_and_no_paths() {
    let fixture = Fixture::new().await;
    let tree = read(&fixture).await.expect("a clean tree is read");
    assert_eq!(
        tree,
        Tree {
            head: FAKE_HEAD.to_owned(),
            uncommitted: Vec::new(),
            uncommitted_count: 0,
        }
    );
    let calls = fixture.git().calls();
    let root = fixture.root().display().to_string();
    assert_eq!(
        calls,
        [
            format!("-C {root} rev-parse --verify HEAD"),
            format!(
                "--no-optional-locks -C {root} status --porcelain=v1 -z --no-renames --untracked-files=all -- \
                 plugins/air community/platform"
            ),
        ]
    );
}

// A dirty tree names every modified, added, deleted and untracked path, in the order of `git status`.
#[tokio::test]
async fn a_dirty_tree_names_every_uncommitted_path() {
    let fixture = Fixture::new().await;
    fixture.git().status(&[
        " M plugins/air/a.kt",
        "A  plugins/air/added.kt",
        " D community/platform/gone.kt",
        "?? community/platform/new file.kt",
    ]);
    let tree = read(&fixture).await.expect("a dirty tree is read");
    let want = [
        "plugins/air/a.kt",
        "plugins/air/added.kt",
        "community/platform/gone.kt",
        "community/platform/new file.kt",
    ];
    assert_eq!(tree.uncommitted, want);
    assert_eq!(tree.uncommitted_count, want.len());
}

// A tree with more uncommitted paths than the bound names the first ones and counts all of them.
#[tokio::test]
async fn a_very_dirty_tree_is_bounded() {
    let fixture = Fixture::new().await;
    let total = MAX_TREE_PATHS + 7;
    let records: Vec<String> = (0..total).map(|index| format!(" M plugins/air/file{index:03}.kt")).collect();
    fixture.git().status(&records.iter().map(String::as_str).collect::<Vec<_>>());
    let tree = read(&fixture).await.expect("a dirty tree is read");
    assert_eq!(tree.uncommitted.len(), MAX_TREE_PATHS);
    assert_eq!(tree.uncommitted_count, total);
    assert_eq!(tree.uncommitted[0], "plugins/air/file000.kt");
    assert_eq!(
        tree.uncommitted[MAX_TREE_PATHS - 1],
        format!("plugins/air/file{:03}.kt", MAX_TREE_PATHS - 1)
    );
}

// A failing `git` is a refusal whose message the report carries as its `treeError`.
#[tokio::test]
async fn a_failing_git_is_an_error_text_and_a_checkout_event() {
    let fixture = Fixture::new().await;
    fixture.git().fail();
    let tree = read(&fixture).await;
    let refusal = tree.as_ref().expect_err("a failing git reads no tree");
    assert!(refusal.message.contains("not a git repository"), "{refusal}");
    let event = checkout_event(&tree);
    assert_eq!(event.head, None);
    assert_eq!(event.error.as_deref(), Some(refusal.message.as_str()));
}

#[test]
fn a_porcelain_record_without_a_path_is_no_path() {
    assert_eq!(porcelain_paths(" M a\0?? b c\0\0XY \0"), ["a", "b c"]);
    assert!(porcelain_paths("").is_empty());
}
