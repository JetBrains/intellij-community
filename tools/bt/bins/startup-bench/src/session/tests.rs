use pretty_assertions::assert_eq;

use super::{GENERAL_SETTINGS, RESULT_FILE, Sandbox, run_dirs, write_template};
use crate::arm::Arm;
use crate::record::{RunId, RunKind};

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
