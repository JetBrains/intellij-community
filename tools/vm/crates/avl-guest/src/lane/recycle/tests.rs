use std::cell::RefCell;
use std::collections::VecDeque;
use std::time::Duration;

use pretty_assertions::assert_eq;

use super::*;

/// A reset that answers `resets` in order and then holds, and a recycle that answers `recycles` in order and then works.
struct Script {
    resets: RefCell<VecDeque<Result<(), String>>>,
    recycles: RefCell<VecDeque<Result<(), String>>>,
    recycled: RefCell<u32>,
}

impl Script {
    fn new(resets: Vec<Result<(), &str>>, recycles: Vec<Result<(), &str>>) -> Self {
        let owned = |outcomes: Vec<Result<(), &str>>| outcomes.into_iter().map(|outcome| outcome.map_err(str::to_owned)).collect();
        Self {
            resets: RefCell::new(owned(resets)),
            recycles: RefCell::new(owned(recycles)),
            recycled: RefCell::new(0),
        }
    }

    fn ensure(&self, isolation: &mut Isolation, scenario: &str) -> Result<(), ResetFailure> {
        self.ensure_within(isolation, scenario, None)
    }

    fn ensure_within(&self, isolation: &mut Isolation, scenario: &str, budget: Option<RecycleBudget>) -> Result<(), ResetFailure> {
        isolation.ensure_clean(
            ResetPhase::Before,
            scenario,
            budget,
            &mut || self.resets.borrow_mut().pop_front().unwrap_or(Ok(())),
            &mut || {
                *self.recycled.borrow_mut() += 1;
                self.recycles.borrow_mut().pop_front().unwrap_or(Ok(()))
            },
        )
    }
}

// A reset that holds costs nothing. A dirty reset recycles the IDE once and resets again, and the scenario runs on the
// clean IDE.
#[test]
fn a_dirty_reset_recycles_once_and_the_next_reset_holds() {
    let script = Script::new(vec![Ok(()), Err("tabs remain open"), Ok(())], vec![]);
    let mut isolation = Isolation::default();
    assert_eq!(script.ensure(&mut isolation, "first"), Ok(()));
    assert_eq!(script.ensure(&mut isolation, "second"), Ok(()));
    assert_eq!(*script.recycled.borrow(), 1);
    assert!(!isolation.exhausted());
    assert_eq!(
        isolation.report().as_deref(),
        Some("The IDE was recycled 1 time(s): before 'second': tabs remain open")
    );
}

// A reset that stays dirty after its recycle fails this scenario only: the recycle worked, and the budget decides
// whether the next scenario runs.
#[test]
fn a_reset_still_dirty_after_its_recycle_fails_this_scenario_only() {
    let script = Script::new(vec![Err("tabs remain open"), Err("tabs still open")], vec![]);
    let mut isolation = Isolation::default();
    let failure = script.ensure(&mut isolation, "first").unwrap_err();
    assert_eq!(
        failure,
        ResetFailure {
            message: "tabs remain open".to_owned(),
            suppressed: vec!["tabs still open".to_owned()],
        }
    );
    assert!(!isolation.exhausted());
    assert_eq!(script.ensure(&mut isolation, "second"), Ok(()));
}

// A recycle that fails leaves no usable IDE, so the run is over, and the reason names the failed remedy.
#[test]
fn a_failed_recycle_exhausts_the_run_and_names_the_remedy() {
    let script = Script::new(vec![Err("tabs remain open")], vec![Err("the IDE did not get ready")]);
    let mut isolation = Isolation::default();
    let failure = script.ensure(&mut isolation, "first").unwrap_err();
    assert_eq!(failure.suppressed, ["the IDE did not get ready"]);
    assert!(isolation.exhausted());
    assert_eq!(
        isolation.exhaustion_reason(),
        "the shared IDE could not be recycled into a usable one ('the IDE did not get ready'), so nothing restored the \
         state these resets failed on: before 'first': tabs remain open"
    );
    assert_eq!(
        never_ran("second", &isolation.exhaustion_reason()),
        format!("scenario 'second' never ran: {}", isolation.exhaustion_reason())
    );
}

// The budget is three recycles per run. The fourth dirty reset recycles nothing and exhausts the run.
#[test]
fn the_fourth_dirty_reset_exhausts_the_budget_of_three_recycles() {
    let resets = (0..MAX_RECYCLES)
        .flat_map(|_| [Err("dirty"), Ok(())])
        .chain([Err("dirty again")])
        .collect();
    let script = Script::new(resets, vec![]);
    let mut isolation = Isolation::default();
    for scenario in 0..MAX_RECYCLES {
        assert_eq!(script.ensure(&mut isolation, &format!("s{scenario}")), Ok(()));
    }
    let failure = script.ensure(&mut isolation, "last").unwrap_err();
    assert_eq!(failure.message, "dirty again");
    assert_eq!(*script.recycled.borrow(), u32::try_from(MAX_RECYCLES).unwrap());
    assert!(isolation.exhausted());
    assert_eq!(
        isolation.exhaustion_reason(),
        "the shared IDE was recycled 3 time(s) and isolation still failed: before 's0': dirty; before 's1': dirty; \
         before 's2': dirty; before 'last': dirty again"
    );
}

// A recycle that cannot end inside what is left of the time budget of the run is refused. The isolation is exhausted,
// so each scenario left is skipped with the reason, and a recycle with time enough still runs.
#[test]
fn a_recycle_that_cannot_fit_the_budget_skips_the_scenarios_left() {
    let need = Duration::from_mins(13);
    let script = Script::new(vec![Err("tabs remain open"), Ok(()), Err("tabs remain open")], vec![]);
    let mut isolation = Isolation::default();
    let fits = RecycleBudget {
        left: Duration::from_mins(20),
        need,
    };
    assert_eq!(script.ensure_within(&mut isolation, "first", Some(fits)), Ok(()));
    assert_eq!(*script.recycled.borrow(), 1);

    let short = RecycleBudget {
        left: Duration::from_mins(5),
        need,
    };
    let failure = script.ensure_within(&mut isolation, "second", Some(short)).unwrap_err();
    assert_eq!(*script.recycled.borrow(), 1, "a recycle ran past the budget");
    assert_eq!(failure.suppressed, ["the lane budget left 300 s, and a recycle needs up to 780 s"]);
    assert!(isolation.exhausted());
    assert_eq!(
        isolation.exhaustion_reason(),
        "the lane budget left 300 s, and a recycle needs up to 780 s, so no recycle answered these resets: before \
         'first': tabs remain open; before 'second': tabs remain open"
    );
}
