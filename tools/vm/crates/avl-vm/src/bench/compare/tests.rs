use std::path::Path;

use avl_base::{Exit, Reporter};
use pretty_assertions::assert_eq;

use super::{Compared, MetricRow, cells, report};
use crate::bench::arm::Arm;
use crate::bench::command::replay;
use crate::bench::files;
use crate::bench::session::{SUMMARY_FILE, SessionInfo, read_info};
use crate::bench::summary::Summary;
use crate::bench::testing::{assert_golden, testdata_dir};

/// The factors of the five synthetic runs: a spread of 1 % around the run of the fixture.
const SPREADS: [f64; 5] = [0.98, 0.99, 1.0, 1.01, 1.02];

/// The distribution of the session A, which is the fixture's.
const DIST_A: &str = "5f1c0d9e2b7a4c3e8d6f1a0b9c8e7d6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d";
const DIST_B: &str = "0b0b0b0b0b0b4c3e8d6f1a0b9c8e7d6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d";

/// A replayed copy of the fixture session at `<root>/<name>`. Its `summary.json` holds five valid runs per arm: the
/// run of the fixture times each factor of [`SPREADS`], times `factor` of the arm. `edit` changes the session inputs.
fn session(root: &Path, name: &str, edit: impl FnOnce(&mut SessionInfo), factor: impl Fn(Arm) -> f64) -> Compared {
    session_with(root, name, edit, &SPREADS, factor)
}

/// A session as [`session`] gives it, with the factors `spreads` in place of [`SPREADS`].
fn session_with(root: &Path, name: &str, edit: impl FnOnce(&mut SessionInfo), spreads: &[f64], factor: impl Fn(Arm) -> f64) -> Compared {
    let dir = root.join(name);
    avl_testkit::traces::copy_tree(&testdata_dir().join("session"), &dir);
    let (reporter, _, _) = Reporter::in_memory("vm");
    let replayed = replay(&dir, &reporter).expect("a summary");
    let mut info = read_info(&dir).expect("session.json");
    edit(&mut info);
    let mut records = Vec::new();
    for (index, spread) in (1..).zip(spreads.iter().copied()) {
        for arm in replayed.arms.values() {
            let mut run = arm.runs[0].clone();
            run.index = index;
            for value in run.metrics.values_mut() {
                *value = (*value * spread * factor(run.arm) * 10.0).round() / 10.0;
            }
            records.push(run);
        }
    }
    let summary = Summary::build(&info, &dir.display().to_string(), &records);
    files::write_json(&dir.join(SUMMARY_FILE), &summary).expect("summary.json");
    Compared::read(dir).expect("a summary")
}

/// The base session A: the fixture, five runs per arm.
fn session_a(root: &Path) -> Compared {
    session(root, "develar-bench-aaaa1111", |_| {}, |_| 1.0)
}

/// The inputs of a session B: another distribution, and another commit with changes.
fn other_dist(info: &mut SessionInfo) {
    DIST_B.clone_into(&mut info.generation.dist_digest);
    "b0b0b0b0b0b01111111111111111111111111111".clone_into(&mut info.git.commit);
    info.git.dirty = true;
}

/// A session B of another distribution and commit, whose arms are `modal` and `non_modal` times the medians of A.
fn session_b(root: &Path, modal: f64, non_modal: f64) -> Compared {
    session(root, "develar-bench-bbbb2222", other_dist, |arm| {
        if arm == Arm::Modal { modal } else { non_modal }
    })
}

#[test]
fn a_session_whose_own_noise_is_over_a_fifth_of_its_modal_median_is_too_noisy_to_compare() {
    let root = tempfile::tempdir().expect("a temporary root");
    let noisy = session_with(
        root.path(),
        "develar-bench-dddd4444",
        other_dist,
        &[0.5, 0.8, 1.0, 1.3, 1.6],
        |_| 1.0,
    );
    let sessions = [session_a(root.path()), noisy];
    let comparison = report(&sessions, false).expect("a comparison");
    let check = &comparison.noise_arm[0];
    assert!(!check.comparable, "the medians agree, and the noise of B still hides any move");
    assert_eq!(
        (check.moved_ms, check.noise_ms, check.own_noise_ms),
        (Some(0.0), Some(34.7), Some(1039.0))
    );
    assert_eq!(check.note, "B is too noisy to compare: noise 1039 ms on a modal median of 2336 ms");
    assert!(comparison.text().contains("\n  B develar-bench-dddd4444 "), "B keeps its column");

    let [quiet, noisy] = sessions;
    let comparison = report(&[noisy, quiet], false).expect("a comparison");
    assert_eq!(
        comparison.noise_arm[0].note,
        "A is too noisy to compare: noise 1039 ms on a modal median of 2336 ms"
    );
}

/// The text of a comparison of A and B, whose modal arm agrees and whose non-modal arm is 10 % faster, equals
/// `testdata/bench/compare.txt`.
#[test]
fn the_comparison_of_two_sessions_matches_the_golden() {
    let root = tempfile::tempdir().expect("a temporary root");
    let sessions = [session_a(root.path()), session_b(root.path(), 1.002, 0.9)];
    let comparison = report(&sessions, true).expect("a comparison");
    let text = format!("{}\n", comparison.text());
    assert_golden("compare.txt", &text);

    let check = &comparison.noise_arm[0];
    assert!(check.comparable, "{}", check.note);
    assert_eq!(
        (check.arm, check.moved_ms, check.noise_ms),
        (Some(Arm::Modal), Some(4.7), Some(34.7))
    );
    let welcome = &comparison.arms[1].groups[0].1[0];
    assert_eq!(welcome.0, "welcomeBecameVisible");
    assert_eq!(
        (welcome.1.medians.clone(), welcome.1.deltas.clone()),
        (vec![Some(2718.0), Some(2446.2)], vec![Some(-271.8)])
    );
    assert_eq!(comparison.notes, Vec::<String>::new());
}

#[test]
fn a_modal_arm_that_moved_over_twice_the_noise_tells_that_the_sessions_do_not_compare() {
    let root = tempfile::tempdir().expect("a temporary root");
    let sessions = [session_a(root.path()), session_b(root.path(), 1.05, 1.0)];
    let comparison = report(&sessions, false).expect("a comparison");
    let check = &comparison.noise_arm[0];
    assert!(!check.comparable);
    assert_eq!(
        check.note,
        "the sessions do not compare: the modal arm moved by +117 ms between A and B (noise 35 ms)"
    );
    assert_eq!(
        (check.noise_ms, check.own_noise_ms),
        (Some(34.7), Some(36.3)),
        "the move is set against A alone"
    );
    let text = comparison.text();
    assert!(
        text.contains(&format!("\n{}\nmodal arm (median ms, or a count)", check.note)),
        "{text}"
    );
    assert!(
        text.contains("\nnon-modal arm (median ms, or a count)"),
        "the table follows still: {text}"
    );
}

/// A session below three valid runs has no noise arm and no delta. A session without the modal arm has no modal delta,
/// and its noise arm is the non-modal arm.
#[test]
fn a_session_below_three_valid_runs_or_without_the_modal_arm_has_no_delta_and_says_why() {
    let root = tempfile::tempdir().expect("a temporary root");
    let few = root.path().join("develar-bench-cccc3333");
    avl_testkit::traces::copy_tree(&testdata_dir().join("session"), &few);
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&few, &reporter).expect("a summary");
    let mut no_modal = session_b(root.path(), 1.0, 1.0);
    no_modal.summary.arms.remove(Arm::Modal.key());
    let sessions = [session_a(root.path()), Compared::read(few).expect("a summary"), no_modal];
    let comparison = report(&sessions, false).expect("a comparison");
    let checks: Vec<(Option<Arm>, bool, &str)> = comparison
        .noise_arm
        .iter()
        .map(|check| (check.arm, check.comparable, check.note.as_str()))
        .collect();
    assert_eq!(
        checks,
        vec![
            (
                None,
                false,
                "B is an A/A run of A; the comparison of A and B is not meaningful: no arm has 3 runs with its main metric \
                 in both sessions, so the noise is unknown (A: modal 5, non-modal 5; B: modal 1, non-modal 1)"
            ),
            (
                Some(Arm::NonModal),
                true,
                "the non-modal arm agrees within the noise: A 2718 ms, C 2718 ms (noise 40 ms)"
            ),
        ]
    );
    assert_eq!(comparison.noise_arm[0].noise_ms, None);
    assert_eq!(
        comparison.notes,
        vec![
            "no delta B-A in the modal arm: A has 5 valid runs and B has 1; a delta needs 3 per session",
            "no delta C-A in the modal arm: C has no modal arm",
            "no delta B-A in the non-modal arm: A has 5 valid runs and B has 1; a delta needs 3 per session",
        ]
    );
    let welcome = &comparison.arms[1].groups[0].1[0].1;
    assert_eq!(welcome.medians, vec![Some(2718.0), Some(2718.0), Some(2718.0)]);
    assert_eq!(welcome.deltas, vec![None, Some(0.0)]);
    assert!(comparison.text().contains("\n  welcomeBecameVisible  "), "{}", comparison.text());
}

/// Two sessions without the modal arm, such as two JetBrains Light sessions, take the noise from the non-modal arm.
#[test]
fn sessions_without_the_modal_arm_take_the_noise_from_the_first_arm_that_both_have() {
    let root = tempfile::tempdir().expect("a temporary root");
    let mut sessions = [session_a(root.path()), session_b(root.path(), 1.0, 1.002)];
    for compared in &mut sessions {
        compared.summary.arms.remove(Arm::Modal.key());
    }
    let comparison = report(&sessions, false).expect("a comparison");
    let check = &comparison.noise_arm[0];
    assert_eq!(
        (check.arm, check.comparable, check.note.as_str()),
        (
            Some(Arm::NonModal),
            true,
            "the non-modal arm agrees within the noise: A 2718 ms, B 2723 ms (noise 40 ms)"
        )
    );
    assert_eq!(comparison.notes, Vec::<String>::new());
    let json = serde_json::to_value(&comparison).expect("JSON");
    assert_eq!(json["noiseArm"][0]["arm"], "nonModal");
    assert!(
        comparison.text().contains(&format!("\n{}\nnon-modal arm", check.note)),
        "{}",
        comparison.text()
    );
}

/// Two sessions without a shared arm have no noise, and the note names the arms of each session.
#[test]
fn sessions_without_a_shared_arm_do_not_compare_and_name_their_arms() {
    let root = tempfile::tempdir().expect("a temporary root");
    let mut a = session_a(root.path());
    a.summary.arms.remove(Arm::NonModal.key());
    let mut b = session_b(root.path(), 1.0, 1.0);
    b.summary.arms.remove(Arm::Modal.key());
    let comparison = report(&[a, b], false).expect("a comparison");
    let check = &comparison.noise_arm[0];
    assert_eq!(
        (check.arm, check.comparable, check.moved_ms, check.note.as_str()),
        (
            None,
            false,
            None,
            "the comparison of A and B is not meaningful: no arm has 3 runs with its main metric in both sessions, \
             so the noise is unknown (A: modal 5; B: non-modal 5)"
        )
    );
    assert_eq!(
        comparison.notes,
        vec![
            "no delta B-A in the modal arm: B has no modal arm",
            "no delta B-A in the non-modal arm: A has no non-modal arm",
        ]
    );
    let json = serde_json::to_value(&comparison).expect("JSON");
    assert_eq!(json["noiseArm"][0]["arm"], serde_json::Value::Null);
}

/// A count prints as a whole number, also as a delta below 10. The class-loading times keep the precision of a time.
#[test]
fn a_count_prints_as_a_whole_number() {
    let row = MetricRow {
        medians: vec![Some(157.0), Some(157.3)],
        deltas: vec![Some(-0.3)],
    };
    for count in [
        "pluginClasses",
        "classLoading.requests",
        "classes.named",
        "classes.named@editor highlighting completed",
    ] {
        assert_eq!(cells(count, &row), ["157", "157", "+0"], "{count}");
    }
    for time in ["classLoading.time", "classLoading.edtTime", "bootstrap"] {
        assert_eq!(cells(time, &row), ["157", "157", "-0.3"], "{time}");
    }
    let near_ten = MetricRow {
        medians: vec![Some(9.96)],
        deltas: vec![],
    };
    assert_eq!(cells("classes.named", &near_ten), ["10"]);
    assert_eq!(cells("classLoading.edtTime", &near_ten), ["10.0"]);
}

#[test]
fn two_sessions_of_one_distribution_are_refused_only_when_a_change_is_expected() {
    let root = tempfile::tempdir().expect("a temporary root");
    let a = session_a(root.path());
    let again = Compared::read(a.dir.clone()).expect("a summary");
    let other = session_b(root.path(), 1.0, 1.0);
    let sessions = [a, other, again];
    let comparison = report(&sessions, false).expect("a comparison");
    assert_eq!(comparison.sessions[2].dist_digest, DIST_A);
    let refusal = report(&sessions, true).expect_err("one distribution");
    assert_eq!(refusal.code, "same_dist");
    assert_eq!(refusal.exit, Exit::USAGE);
    assert_eq!(
        refusal.message,
        "A develar-bench-aaaa1111 and C develar-bench-aaaa1111 share the distribution 5f1c0d9e2b7a; \
         --expect-different-dist expects another distribution in each session"
    );
}

/// A session B of the distribution of A is an A/A run. A move within the noise is the noise of this host, and a
/// larger move tells that the noise estimate of A is too low.
#[test]
fn a_session_of_the_distribution_of_a_is_an_a_a_run() {
    let root = tempfile::tempdir().expect("a temporary root");
    let same_dist =
        |root: &Path, name: &str, modal: f64| session(root, name, |_| {}, move |arm| if arm == Arm::Modal { modal } else { 1.0 });
    let sessions = [
        session_a(root.path()),
        same_dist(root.path(), "develar-bench-bbbb2222", 1.002),
        same_dist(root.path(), "develar-bench-cccc3333", 1.1),
        session_b(root.path(), 1.002, 1.0),
    ];
    let comparison = report(&sessions, false).expect("a comparison");
    let checks: Vec<(bool, bool, &str)> = comparison
        .noise_arm
        .iter()
        .map(|check| (check.same_dist, check.comparable, check.note.as_str()))
        .collect();
    assert_eq!(
        checks,
        [
            (
                true,
                true,
                "B is an A/A run of A: the modal arm moved by +4.7 ms, which is the noise of this host (noise of A 35 ms)"
            ),
            (
                true,
                false,
                "C is an A/A run of A: the modal arm moved by +234 ms, above the noise of A (35 ms); the noise estimate of A is too low"
            ),
            (
                false,
                true,
                "the modal arm agrees within the noise: A 2336 ms, D 2341 ms (noise 35 ms)"
            ),
        ]
    );
    let json = serde_json::to_value(&comparison).expect("JSON");
    assert_eq!(json["noiseArm"][0]["sameDist"], true);
    assert_eq!(json["noiseArm"][2]["sameDist"], false);
    assert!(
        comparison.text().contains("\nB is an A/A run of A: the modal arm moved by +4.7 ms"),
        "{}",
        comparison.text()
    );
}
