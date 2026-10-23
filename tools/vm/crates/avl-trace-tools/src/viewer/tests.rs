use pretty_assertions::assert_eq;

use super::*;

/// The same literals are pinned by `docs/src/viewerUrls.test.ts` against `router.ts`. A change on one side fails
/// the test on the other.
#[test]
fn the_viewer_urls_are_the_site_routes() {
    let cases = [
        (runs_url(7357), "http://127.0.0.1:7357/air/runs"),
        (
            run_url(7357, "alice-run-1f2e"),
            "http://127.0.0.1:7357/air/runs/run?id=alice-run-1f2e",
        ),
        (run_url(7357, "a b&c"), "http://127.0.0.1:7357/air/runs/run?id=a+b%26c"),
        (
            viewer_url(7357, "0a1b2c3d4e5f60718293", "start in terminal"),
            "http://127.0.0.1:7357/air/runs/trace?src=0a1b2c3d4e5f60718293&scenario=start+in+terminal",
        ),
        (
            viewer_url(8000, "id", "a/b?c"),
            "http://127.0.0.1:8000/air/runs/trace?src=id&scenario=a%2Fb%3Fc",
        ),
    ];
    for (got, want) in cases {
        assert_eq!(got, want);
    }
}

/// A CI page names its build by the build id, and a trace page keeps each slash of its zip path. The rest is written
/// as `URLSearchParams` writes it. A slash at the end of the site is dropped.
#[test]
fn the_ci_urls_name_the_build_and_the_zip() {
    assert_eq!(
        ci_build_url(CI_SITE, "1100310296"),
        "https://ij-void-docs.labs.jb.gg/air/ci/1100310296"
    );
    assert_eq!(
        ci_trace_url("https://staging.example/air/", "1100310296", "ui_lane_test/iter-1/T/s.zip"),
        "https://staging.example/air/ci/1100310296/trace?zip=ui_lane_test/iter-1/T/s.zip"
    );
    assert_eq!(
        ci_trace_url(CI_SITE, "1100310296", "ui_lane_test/shard_1_of_2/iter-1/T/a b+c~d&e=\u{e9}.zip"),
        "https://ij-void-docs.labs.jb.gg/air/ci/1100310296/trace?zip=ui_lane_test/shard_1_of_2/iter-1/T/a+b%2Bc%7Ed%26e%3D%C3%A9.zip"
    );
}
