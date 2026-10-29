use std::time::Duration;

use pretty_assertions::assert_eq;

use super::*;

fn moment() -> Moment {
    Moment {
        failed: 1,
        expired: false,
        exhausted: false,
        live: false,
        budget_left: Some(Duration::from_mins(20)),
        recycle_need: Duration::from_mins(13),
    }
}

#[test]
fn no_rerun_past_the_limit_after_an_expiry_on_an_exhausted_isolation_in_the_live_lane_or_short_of_budget() {
    assert_eq!(refusal(moment()), None);
    assert_eq!(
        refusal(Moment {
            failed: MAX_FAILED,
            ..moment()
        }),
        None
    );
    assert_eq!(
        refusal(Moment {
            budget_left: None,
            ..moment()
        }),
        None
    );
    let refused = |moment: Moment| refusal(moment).unwrap();
    assert_eq!(
        refused(Moment { failed: 4, ..moment() }),
        "4 scenarios and suite prologues failed, more than 3, which is a break and not a flake"
    );
    assert_eq!(refused(Moment { expired: true, ..moment() }), "the watchdog ended the run");
    assert_eq!(
        refused(Moment {
            exhausted: true,
            ..moment()
        }),
        "the isolation of the run is exhausted, so no later scenario runs"
    );
    assert_eq!(
        refused(Moment { live: true, ..moment() }),
        "a rerun in the live lane spends the turns of its real accounts again"
    );
    assert_eq!(
        refused(Moment {
            budget_left: Some(Duration::from_mins(12)),
            ..moment()
        }),
        "the lane budget left 720 s, and the recycle before the rerun needs up to 780 s"
    );
}

#[test]
fn the_injected_failures_come_from_the_variable_and_a_malformed_one_is_refused() {
    let parse = |value: Option<&str>| from_environment(&|name: &str| (name == INJECT_VARIABLE).then(|| value.map(str::to_owned)).flatten());
    assert_eq!(parse(None).unwrap(), Rerun::default());
    assert_eq!(parse(Some(" ")).unwrap(), Rerun::default());
    let rerun = parse(Some("first: rename, then close ; every:missing-file-note;")).unwrap();
    assert_eq!(
        rerun.injected,
        vec![
            Injection {
                attempts: Attempts::First,
                scenario: "rename, then close".to_owned(),
            },
            Injection {
                attempts: Attempts::Every,
                scenario: "missing-file-note".to_owned(),
            },
        ]
    );
    let [first, every] = rerun.injected.as_slice() else {
        unreachable!()
    };
    assert!(first.fails(&["rename, then close"], false));
    assert!(!first.fails(&["rename, then close"], true));
    assert!(!first.fails(&["missing-file-note"], false));
    assert!(every.fails(&["note", "missing-file-note"], true));
    assert_eq!(
        first.describe(),
        "$AIR_LANE_INJECT_FAILURE fails the first attempt of the scenario 'rename, then close'"
    );
    assert_eq!(
        every.describe(),
        "$AIR_LANE_INJECT_FAILURE fails each attempt of the scenario 'missing-file-note'"
    );
    for malformed in ["rename", "once:rename", "first:", "first: "] {
        let refusal = parse(Some(malformed)).unwrap_err();
        assert_eq!(refusal.code, INJECT_CODE, "{malformed}");
        assert!(refusal.message.contains(&format!("{:?}", malformed.trim())), "{}", refusal.message);
    }
}
