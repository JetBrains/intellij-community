use pretty_assertions::assert_eq;

use super::{check_dist_label, dist_outputs, profiler_jar};

#[test]
fn a_label_without_the_dist_suffix_is_refused_before_the_build() {
    check_dist_label("//build:idea_dist").expect("a distribution label");
    for target in ["//build:idea", "//build:_dist", "idea_dist"] {
        let refusal = check_dist_label(target).expect_err("a refusal");
        assert_eq!(refusal.code, "usage", "{target}");
    }
}

#[test]
fn finds_the_dist_and_its_config_among_the_cquery_files() {
    let files = "bazel-out/x/bin/build/idea_dist.dist\nbazel-out/x/bin/build/idea_dist.ide.config\n";
    assert_eq!(
        dist_outputs(files),
        (
            Some("bazel-out/x/bin/build/idea_dist.dist"),
            Some("bazel-out/x/bin/build/idea_dist.ide.config")
        )
    );
    assert_eq!(dist_outputs("bazel-out/x/bin/build/idea.jar\n"), (None, None));
}

#[test]
fn finds_the_profiler_jar_among_the_cquery_files() {
    let files = "bazel-out/x/idea-async-profiler.jar\n\
                 external/lib+http/file/async-profiler-4.5-0.jar\n\
                 external/lib+http/file/async-profiler-4.5-0-sources.jar\n";
    assert_eq!(profiler_jar(files), Some("external/lib+http/file/async-profiler-4.5-0.jar"));
    assert_eq!(profiler_jar("bazel-out/x/idea-async-profiler.jar\n"), None);
}
