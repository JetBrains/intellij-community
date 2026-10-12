use avl_base::Reporter;

use crate::bench::command::replay;
use crate::bench::testing::{assert_golden, fixture_session};

/// The digest of a replay of the fixture session equals `testdata/bench/replay-digest.txt`.
#[test]
fn the_replay_digest_matches_the_golden() {
    let (_dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    let mut summary = replay(&session, &reporter).expect("a summary");
    summary.session = "<session>".to_owned();
    let digest = format!("{}\n", super::render(&summary));
    assert_golden("replay-digest.txt", &digest);
    let profile = digest
        .lines()
        .skip_while(|line| !line.ends_with("EDT samples):"))
        .take_while(|line| !line.contains("host load"))
        .count();
    assert_eq!(profile, 16, "the EDT block and the trigger block of the profile");
    assert!(
        digest.lines().count() - profile <= 55,
        "the digest of a welcome session without a profile is about 50 lines"
    );
}

#[test]
fn a_number_has_one_decimal_below_ten_and_a_clip_ends_with_an_ellipsis() {
    use super::{clip, number, signed};
    assert_eq!(number(4.25), "4.2");
    assert_eq!(number(4.0), "4");
    assert_eq!(number(1265.4), "1265");
    assert_eq!(signed(-0.5), "-0.5");
    assert_eq!(signed(12.0), "+12");
    assert_eq!(clip("run activity", 20), "run activity");
    assert_eq!(clip("run activity", 5), "run …");
}

#[test]
fn the_header_names_the_max_load_of_the_session() {
    let (_dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    let mut summary = replay(&session, &reporter).expect("a summary");
    let first = |summary: &crate::bench::summary::Summary| super::render(summary).lines().next().map(str::to_owned);
    assert!(
        first(&summary).is_some_and(|line| line.ends_with(", hold 3000 ms")),
        "{:?}",
        first(&summary)
    );
    summary.max_load = Some(10.0);
    assert!(
        first(&summary).is_some_and(|line| line.ends_with(", hold 3000 ms, max load 10")),
        "{:?}",
        first(&summary)
    );
}

/// A project session renders the editor group and the additional modules of the distribution.
#[test]
fn a_project_session_shows_the_editor_and_the_additional_modules() {
    use crate::bench::arm::Arm;
    use crate::bench::record::{self, LaunchFacts, RunId};
    use crate::bench::session;
    use crate::bench::summary::Summary;
    let (_dir, session_dir) = fixture_session();
    let mut info = session::read_info(&session_dir).expect("the session");
    info.command = "project".to_owned();
    info.arms = vec![Arm::Project];
    info.project = Some("<template>/projects/markdown".to_owned());
    info.project_file = Some("README.md".to_owned());
    info.additional_modules = vec!["intellij.markdown".to_owned(), "intellij.json".to_owned()];
    let id = RunId::parse("project-run-01").expect("a run");
    let run = record::collect(&session_dir.join("project-run-01"), &id, LaunchFacts::default());
    let summary = Summary::build(&info, "<session>", &[run]);
    let text = super::render(&summary);
    let lines: Vec<&str> = text.lines().collect();
    assert!(lines[0].ends_with(", hold 3000 ms, with 2 additional modules"), "{}", lines[0]);
    assert_eq!(lines[2], "project: <template>/projects/markdown, prime file README.md");
    let editor = lines.iter().position(|line| *line == "editor:").expect("the editor group");
    assert!(lines[editor + 1].starts_with("  editor highlighting completed "), "{text}");
    assert!(lines[editor + 2] == "platform spans:", "{text}");
    assert!(!text.contains("welcome spans:"), "{text}");
    let name = |line: &str| line.chars().take(super::NAME_WIDTH).collect::<String>().trim().to_owned();
    for row in ["editor restoring till paint", "editor restoring", "project frame creating"] {
        assert_eq!(lines.iter().filter(|line| name(line) == row).count(), 1, "{row}: {text}");
    }
}

/// A session with the empty-editor arm renders the composer group: its end, the spans with a fixed name, then the
/// prologue steps in name order. It names no prime file, because that arm opens none.
#[test]
fn an_empty_editor_session_shows_the_composer() {
    use crate::bench::arm::Arm;
    use crate::bench::record::{self, LaunchFacts, RunId};
    use crate::bench::session;
    use crate::bench::summary::Summary;
    use crate::bench::testing::{EMPTY_STATE_FIXTURE_SPANS, empty_editor_run};
    let (_dir, session_dir) = fixture_session();
    let mut info = session::read_info(&session_dir).expect("the session");
    info.command = "project".to_owned();
    info.arms = vec![Arm::EmptyEditor];
    info.cold = true;
    info.project = Some("<template>/projects/markdown".to_owned());
    let run_dir = empty_editor_run(&session_dir, &EMPTY_STATE_FIXTURE_SPANS);
    let id = RunId::parse("empty-editor-run-01").expect("a run");
    let run = record::collect(&run_dir, &id, LaunchFacts::default());
    let summary = Summary::build(&info, "<session>", &[run]);
    let text = super::render(&summary);
    let lines: Vec<&str> = text.lines().collect();
    assert!(lines[0].contains(", cold, "), "{}", lines[0]);
    assert_eq!(lines[2], "project: <template>/projects/markdown");
    assert!(lines[3].trim_start().starts_with("empty-editor 1/1 valid"), "{text}");
    let name = |line: &str| line.chars().take(super::NAME_WIDTH).collect::<String>().trim().to_owned();
    let group = lines
        .iter()
        .position(|line| *line == "empty-state composer:")
        .expect("the composer group");
    let rows: Vec<String> = lines[group + 1..]
        .iter()
        .take_while(|line| line.starts_with("  "))
        .map(|line| name(line))
        .collect();
    assert_eq!(
        rows,
        [
            "emptyStateBuilt",
            "air.emptyState.createComponent",
            "air.emptyState.prologue",
            "air.emptyState.buildOnEdt",
            "air.emptyState.createContent",
            "air.emptyState.initializeContent",
            "air.emptyState.prologue.aiSource",
            "air.emptyState.prologue.promptDocument",
        ]
    );
    assert!(!lines.contains(&"editor:"), "the editor group needs the project arm: {text}");
    assert!(text.contains("  classes.named@emptyStateBuilt "), "{text}");
}
