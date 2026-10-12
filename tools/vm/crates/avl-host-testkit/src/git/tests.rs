use pretty_assertions::assert_eq;

use super::*;

fn git(fake: &FakeGit, args: &[&str]) -> (i32, String) {
    let output = std::process::Command::new(fake.path())
        .args(args)
        .output()
        .expect("the fake git runs");
    (
        output.status.code().unwrap_or(-1),
        String::from_utf8_lossy(&output.stdout).into_owned(),
    )
}

// The work-tree probe is a `rev-parse` too, so it is matched first; the head answers every other one.
#[test]
fn the_fake_git_answers_the_work_tree_the_head_and_the_status() {
    let fake = FakeGit::install();
    fake.status(&[" M a.txt"]);
    assert_eq!(git(&fake, &["rev-parse", "--is-inside-work-tree"]), (0, "true\n".to_owned()));
    assert_eq!(
        git(&fake, &["-C", "/repo", "rev-parse", "--verify", "HEAD"]),
        (0, format!("{FAKE_HEAD}\n"))
    );
    assert_eq!(git(&fake, &["status", "--porcelain=v1", "-z"]), (0, " M a.txt\0".to_owned()));
    fake.outside_work_tree();
    assert_eq!(git(&fake, &["rev-parse", "--is-inside-work-tree"]), (1, "false\n".to_owned()));
    fake.fail();
    assert_eq!(git(&fake, &["status"]).0, 128);
    assert_eq!(fake.calls().len(), 5);
    assert_eq!(fake.calls()[1], "-C /repo rev-parse --verify HEAD");
}
