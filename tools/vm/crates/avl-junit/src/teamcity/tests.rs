use super::*;

#[test]
fn a_value_escapes_as_teamcity_reads_it() {
    assert_eq!(escape("a|b'c[d]e\nf\rg\u{2028}"), "a||b|'c|[d|]e|nf|rg|l");
}

#[test]
fn a_build_problem_names_its_escaped_description_and_its_identity() {
    assert_eq!(
        build_problem("the budget ended 'restore' [2520 s]", "lane-run-budget"),
        "##teamcity[buildProblem description='the budget ended |'restore|' |[2520 s|]' identity='lane-run-budget']"
    );
}

#[test]
fn only_a_set_and_not_blank_version_is_teamcity() {
    assert!(is_team_city(|name| (name == TEAMCITY_VERSION).then(|| "2026.1".to_owned())));
    assert!(!is_team_city(|name| (name == TEAMCITY_VERSION).then(|| " ".to_owned())));
    assert!(!is_team_city(|_| None));
}
