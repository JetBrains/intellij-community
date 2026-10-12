use pretty_assertions::assert_eq;

use std::path::Path;

use super::{
    GENERAL_SETTINGS, NO_README_SETTINGS, Project, ProjectSource, RESULT_FILE, Sandbox, first_file, live_sandbox, open_no_readme,
    own_welcome_project, run_dirs, write_project, write_template,
};
use crate::bench::arm::Arm;
use crate::bench::record::{RunId, RunKind};

#[test]
fn the_template_has_the_settings_and_the_license() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let license = dir.path().join("idea.key");
    std::fs::write(&license, "key").expect("a license");
    let template = Sandbox::new(dir.path().join("template"));
    write_template(&template, &license).expect("a template");
    assert_eq!(
        std::fs::read_to_string(template.config().join("options/ide.general.xml")).expect("the settings"),
        GENERAL_SETTINGS
    );
    assert_eq!(
        std::fs::read_to_string(template.config().join("idea.key")).expect("the license"),
        "key"
    );
    assert!(template.system().is_dir() && template.plugins().is_dir());
    let error = write_template(&template, &dir.path().join("absent")).expect_err("no license");
    assert!(format!("{error:#}").starts_with("cannot copy the license "), "{error:#}");
}

#[test]
fn lists_the_run_directories_that_have_a_result() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    for name in ["non-modal-run-02", "modal-prime", "modal-run-01", "template", "modal-run-03"] {
        std::fs::create_dir_all(dir.path().join(name)).expect("a directory");
        if name != "modal-run-03" {
            std::fs::write(dir.path().join(name).join(RESULT_FILE), "{}").expect("a result");
        }
    }
    let ids: Vec<RunId> = run_dirs(dir.path()).expect("the runs").into_iter().map(|(id, _)| id).collect();
    assert_eq!(
        ids,
        vec![
            RunId {
                arm: Arm::Modal,
                kind: RunKind::Prime,
                index: 0
            },
            RunId {
                arm: Arm::Modal,
                kind: RunKind::Measured,
                index: 1
            },
            RunId {
                arm: Arm::NonModal,
                kind: RunKind::Measured,
                index: 2
            },
        ]
    );
}

#[test]
fn a_sandbox_owns_its_welcome_project() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let sandbox = live_sandbox(&dir.path().join("s&t"), Arm::NonModal);
    assert_eq!(sandbox.root, dir.path().join("s&t").join("non-modal-sandbox"));
    own_welcome_project(&sandbox).expect("the settings");
    let settings = std::fs::read_to_string(sandbox.config().join("options/ide.general.local.xml")).expect("the settings");
    let projects = sandbox.root.join("projects").display().to_string().replace('&', "&amp;");
    assert_eq!(
        settings,
        format!(
            "<application>\n  <component name=\"GeneralLocalSettings\">\n    <option name=\"defaultProjectDirectory\" value=\"{projects}\" />\n  </component>\n</application>\n"
        )
    );
    assert!(Path::new(&projects.replace("&amp;", "&")).starts_with(&sandbox.root));
}

/// A sandbox of the empty-editor arm turns the README lookup of a first project open off, in the file of the advanced
/// settings.
#[test]
fn a_sandbox_can_open_no_readme() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let sandbox = live_sandbox(dir.path(), Arm::EmptyEditor);
    assert_eq!(sandbox.root, dir.path().join("empty-editor-sandbox"));
    open_no_readme(&sandbox).expect("the settings");
    let settings = std::fs::read_to_string(sandbox.config().join("options/advancedSettings.xml")).expect("the settings");
    assert_eq!(settings, NO_README_SETTINGS);
    assert!(
        settings.contains(r#"<entry key="ide.open.readme.md.on.startup" value="false" />"#),
        "{settings}"
    );
}

#[test]
fn a_run_of_another_distribution_is_refused() {
    use super::{GenerationRef, GitInfo, SessionInfo, check_digest};
    let info = SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea_dist".to_owned(),
        git: GitInfo::default(),
        generation: GenerationRef {
            path: "/g/abc".to_owned(),
            dist_digest: "abc".to_owned(),
        },
        arms: vec![Arm::Modal],
        runs: 1,
        cold: false,
        hold_ms: 0,
        profile: false,
        project: None,
        project_file: None,
        additional_modules: Vec::new(),
        max_load: None,
        warnings: Vec::new(),
    };
    assert_eq!(check_digest(&info, Some("abc")), Ok(()));
    assert_eq!(check_digest(&info, None), Ok(()), "a run before the generation names no digest");
    assert_eq!(
        check_digest(&info, Some("def")),
        Err("the run started from the distribution def, and the session is abc".to_owned())
    );
}

/// A directory under `runs` with `session.json`, and `summary.json` when `summary` is given.
fn session_dir(runs: &Path, name: &str, summary: Option<&str>) -> std::path::PathBuf {
    let session = runs.join(name);
    std::fs::create_dir_all(&session).expect("a session");
    std::fs::write(session.join(super::SESSION_FILE), "{}").expect("a session file");
    if let Some(summary) = summary {
        std::fs::write(session.join(super::SUMMARY_FILE), summary).expect("a summary");
    }
    session
}

fn code(result: Result<std::path::PathBuf, avl_base::Refusal>) -> (String, String) {
    let refusal = result.expect_err("a refusal");
    (refusal.code.into_owned(), refusal.message)
}

#[test]
fn a_session_is_located_by_path_by_name_by_a_part_of_a_name_or_as_the_latest() {
    use super::locate;
    let dir = tempfile::tempdir().expect("a temporary directory");
    let runs = dir.path().join("runtime/bench/runs");
    let first = session_dir(&runs, "develar-bench-2104c3b2-bf21", Some("{}"));
    let second = session_dir(&runs, "develar-bench-6291e91e-935b", Some("{}"));
    let running = session_dir(&runs, "develar-bench-6291e91f-0000", None);
    let elsewhere = session_dir(dir.path(), "mine", None);
    let started = std::time::SystemTime::now();
    for (session, age) in [(&first, 30), (&second, 20), (&running, 10)] {
        let file = std::fs::File::options()
            .write(true)
            .open(session.join(super::SESSION_FILE))
            .expect("a session file");
        file.set_modified(started - std::time::Duration::from_secs(age)).expect("an mtime");
    }

    assert_eq!(
        locate("latest", dir.path(), &runs).expect("the latest"),
        second,
        "a session without a summary is not the latest"
    );
    let order = super::newest_first(&runs);
    let order: Vec<&std::path::PathBuf> = order.iter().map(|(session, _)| session).collect();
    assert_eq!(order, [&running, &second, &first], "ls lists in this order");
    assert_eq!(locate("mine", dir.path(), &runs).expect("a path"), elsewhere);
    assert_eq!(
        locate(&elsewhere.display().to_string(), Path::new("/"), &runs).expect("a path"),
        elsewhere
    );
    assert_eq!(locate("develar-bench-2104c3b2-bf21", dir.path(), &runs).expect("a name"), first);
    assert_eq!(locate("2104c3b2", dir.path(), &runs).expect("a part of a name"), first);
    assert_eq!(locate("6291e91f", dir.path(), &runs).expect("a part of a name"), running);
    assert_eq!(
        code(locate("6291e91", dir.path(), &runs)),
        (
            "usage".to_owned(),
            "2 sessions match 6291e91: develar-bench-6291e91e-935b, develar-bench-6291e91f-0000; name a longer part of one".to_owned()
        )
    );
    let (refused, message) = code(locate("ffffffff", dir.path(), &runs));
    assert_eq!(refused, "not_a_session");
    assert!(message.starts_with("no session matches ffffffff: "), "{message}");
    let (refused, message) = code(locate(".", dir.path(), &runs));
    assert_eq!(refused, "not_a_session");
    assert!(message.ends_with("is not a session directory: it has no session.json"), "{message}");
    let (refused, _) = code(locate("latest", dir.path(), &dir.path().join("absent")));
    assert_eq!(refused, "not_a_session");
}

#[test]
fn a_session_without_a_summary_is_refused_with_the_replay_to_run() {
    use super::read_summary;
    let dir = tempfile::tempdir().expect("a temporary directory");
    let session = session_dir(dir.path(), "s", None);
    let refusal = read_summary(&session).expect_err("no summary");
    assert_eq!(refusal.code, "no_summary");
    assert_eq!(refusal.exit, avl_base::Exit::USAGE);
    assert_eq!(
        refusal.message,
        format!(
            "{} has no summary.json; run `bench replay {}` first",
            session.display(),
            session.display()
        )
    );
    std::fs::write(session.join(super::SUMMARY_FILE), "{}").expect("a summary");
    let refusal = read_summary(&session).expect_err("another shape");
    assert_eq!(refusal.code, "no_summary");
    assert!(refusal.message.contains("is not a summary: missing field"), "{}", refusal.message);
}

#[test]
fn the_runs_root_is_below_the_bench_directory() {
    assert_eq!(super::runs_root(Path::new("/r")), Path::new("/r/bench/runs"));
}

#[test]
fn the_default_run_is_the_median_of_the_non_modal_arm_else_of_the_modal_arm() {
    use super::{GenerationRef, GitInfo, SessionInfo, select_run};
    use crate::bench::record::{LaunchFacts, RunRecord, WELCOME_BECAME_VISIBLE};
    use crate::bench::summary::Summary;
    let run = |arm: Arm, index: u32, value: Option<f64>| RunRecord {
        arm,
        kind: RunKind::Measured,
        index,
        dir: RunId {
            arm,
            kind: RunKind::Measured,
            index,
        }
        .dir_name(),
        launch: LaunchFacts::default(),
        valid: value.is_some(),
        reason: None,
        notes: Vec::new(),
        metrics: value
            .map(|value| std::collections::BTreeMap::from([(WELCOME_BECAME_VISIBLE.to_owned(), value)]))
            .unwrap_or_default(),
        classes_by_plugin: std::collections::BTreeMap::new(),
        classes_by_module: std::collections::BTreeMap::new(),
        classes_by_plugin_at: std::collections::BTreeMap::new(),
        edt_samples: None,
        edt_frames: Vec::new(),
        triggers: Vec::new(),
        define_share: None,
        profile: None,
    };
    let info = SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea_dist".to_owned(),
        git: GitInfo::default(),
        generation: GenerationRef::default(),
        arms: vec![Arm::Modal, Arm::NonModal],
        runs: 3,
        cold: false,
        hold_ms: 0,
        profile: false,
        project: None,
        project_file: None,
        additional_modules: Vec::new(),
        max_load: None,
        warnings: Vec::new(),
    };
    let session = Path::new("/s");
    let records = [
        run(Arm::Modal, 1, Some(1200.0)),
        run(Arm::Modal, 2, Some(1100.0)),
        run(Arm::NonModal, 1, Some(1700.0)),
        run(Arm::NonModal, 2, Some(1500.0)),
        run(Arm::NonModal, 3, Some(1600.0)),
    ];
    let selected = select_run(session, &Summary::build(&info, "s", &records), None).expect("a run");
    assert_eq!(selected.dir, session.join("non-modal-run-03"));
    assert_eq!(selected.median_by, Some(WELCOME_BECAME_VISIBLE));

    let records = [
        run(Arm::Modal, 1, Some(1200.0)),
        run(Arm::Modal, 2, Some(1100.0)),
        run(Arm::NonModal, 1, None),
    ];
    let selected = select_run(session, &Summary::build(&info, "s", &records), None).expect("a run");
    assert_eq!(selected.dir, session.join("modal-run-02"), "the lower middle of two");

    let records = [run(Arm::Modal, 1, None), run(Arm::NonModal, 1, None)];
    let refusal = select_run(session, &Summary::build(&info, "s", &records), None).expect_err("no valid run");
    assert_eq!(
        refusal.message,
        "no arm of /s has a valid measured run, so it has no median run; name a run with --run"
    );
}

#[test]
fn a_named_run_of_a_session_without_runs_says_that_it_has_none() {
    use super::{GenerationRef, GitInfo, SessionInfo, select_run};
    use crate::bench::summary::Summary;
    let dir = tempfile::tempdir().expect("a temporary directory");
    let info = SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea_dist".to_owned(),
        git: GitInfo::default(),
        generation: GenerationRef::default(),
        arms: vec![Arm::Modal],
        runs: 1,
        cold: false,
        hold_ms: 0,
        profile: false,
        project: None,
        project_file: None,
        additional_modules: Vec::new(),
        max_load: None,
        warnings: Vec::new(),
    };
    let refusal = select_run(dir.path(), &Summary::build(&info, "s", &[]), Some("modal-run-01")).expect_err("no run");
    assert_eq!(
        refusal.message,
        format!("{} has no run modal-run-01; it has no run directory", dir.path().display())
    );
}

#[test]
fn the_max_load_goes_through_session_json_and_summary_json() {
    use super::{GenerationRef, GitInfo, SessionInfo, read_info};
    use crate::bench::summary::Summary;
    use crate::bench::testing::testdata_dir;
    let info = SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea_dist".to_owned(),
        git: GitInfo::default(),
        generation: GenerationRef::default(),
        arms: vec![Arm::Modal],
        runs: 1,
        cold: false,
        hold_ms: 0,
        profile: false,
        project: None,
        project_file: None,
        additional_modules: Vec::new(),
        max_load: Some(10.0),
        warnings: Vec::new(),
    };
    let text = serde_json::to_string(&info).expect("JSON");
    assert!(text.contains(r#""maxLoad":10.0"#), "{text}");
    assert_eq!(serde_json::from_str::<SessionInfo>(&text).expect("a session"), info);
    let summary = serde_json::to_value(Summary::build(&info, "s", &[])).expect("JSON");
    assert_eq!(summary["maxLoad"], 10.0);
    let fixture = read_info(&testdata_dir().join("session")).expect("the fixture session");
    assert_eq!(fixture.max_load, None, "a session of before the option has none");
}

#[test]
fn the_markdown_project_lives_in_the_template_and_opens_its_readme() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let template = Sandbox::new(dir.path().join("template"));
    let project = write_project(&template, &ProjectSource::Markdown).expect("a project");
    assert_eq!(
        project,
        Project {
            name: "markdown".to_owned(),
            file: "README.md".into()
        }
    );
    let paths = project.paths(&template);
    assert_eq!(paths.dir, dir.path().join("template/projects/markdown"));
    assert_eq!(paths.file, paths.dir.join("README.md"));
    let readme = std::fs::read_to_string(&paths.file).expect("the readme");
    assert_eq!(readme.lines().count(), 40);
    assert!(readme.starts_with("# Markdown project\n\n## Section 1\n"), "{readme}");
    for name in ["notes/a.md", "notes/b.md", "CHANGELOG.md"] {
        assert!(paths.dir.join(name).is_file(), "{name}");
    }
}

#[test]
fn a_directory_project_is_copied_and_opens_its_first_markdown_file() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let source = dir.path().join("docs");
    std::fs::create_dir_all(source.join("sub")).expect("a project");
    for name in [".hidden.md", "b.md", "a.txt", "c.md", "sub/0.md"] {
        std::fs::write(source.join(name), name).expect("a file");
    }
    assert_eq!(first_file(&source).expect("a listing"), Some("b.md".into()));
    let template = Sandbox::new(dir.path().join("template"));
    let project = write_project(&template, &ProjectSource::Dir(source.clone())).expect("a project");
    assert_eq!(project.name, "docs");
    assert_eq!(project.file, Path::new("b.md"));
    assert_eq!(
        std::fs::read_to_string(project.paths(&template).dir.join("sub/0.md")).expect("a copy"),
        "sub/0.md"
    );

    std::fs::remove_file(source.join("b.md")).expect("a removal");
    std::fs::remove_file(source.join("c.md")).expect("a removal");
    assert_eq!(
        first_file(&source).expect("a listing"),
        Some("a.txt".into()),
        "any regular file without a markdown one"
    );

    let empty = dir.path().join("empty");
    std::fs::create_dir_all(empty.join("sub")).expect("a directory");
    std::fs::write(empty.join(".gitignore"), "").expect("a hidden file");
    assert_eq!(first_file(&empty).expect("a listing"), None);
    let error = write_project(&template, &ProjectSource::Dir(empty.clone())).expect_err("no file to open");
    assert_eq!(format!("{error:#}"), format!("the project {} has no file to open", empty.display()));
}
