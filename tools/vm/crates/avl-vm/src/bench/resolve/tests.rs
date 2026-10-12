use std::path::Path;

use pretty_assertions::assert_eq;

use super::{dist_outputs, jbr_home, launcher_binary, launcher_java, launcher_label, profiler_jar};

#[test]
fn the_launcher_of_a_distribution_is_its_label_without_the_suffix() {
    assert_eq!(launcher_label("//build:idea_dist").expect("a launcher"), "//build:idea");
    for target in ["//build:idea", "//build:_dist", "idea_dist"] {
        let refusal = launcher_label(target).expect_err("a refusal");
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
fn finds_the_launcher_binary_by_the_target_name() {
    let files = "bazel-out/x/bin/build/idea.jar\nbazel-out/x/bin/build/idea\n";
    assert_eq!(launcher_binary(files, "//build:idea"), Some("bazel-out/x/bin/build/idea"));
    assert_eq!(launcher_binary(files, "//build:other"), None);
}

#[test]
fn reads_the_java_of_the_launch_manifest_and_its_home() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let binary = dir.path().join("idea");
    std::fs::write(dir.path().join("idea.launch.json"), r#"{"java":"jbr/bin/java","version":1}"#).expect("a manifest");
    let java = launcher_java(&binary).expect("the java");
    assert_eq!(java, dir.path().join("idea.runfiles").join("jbr/bin/java"));
    std::fs::create_dir_all(java.parent().expect("a bin")).expect("a bin directory");
    std::fs::write(&java, "").expect("a java");
    let home = jbr_home(&java).expect("the home");
    assert!(home.ends_with(Path::new("idea.runfiles/jbr")), "{}", home.display());

    std::fs::write(dir.path().join("idea.launch.json"), "{}").expect("a manifest");
    let error = launcher_java(&binary).expect_err("no java");
    assert!(format!("{error:#}").contains("has no `java` field"), "{error:#}");
    let error = jbr_home(&dir.path().join("idea.launch.json")).expect_err("not a bin/java");
    assert!(format!("{error:#}").contains("is not a bin/java of a JBR home"), "{error:#}");
}

#[test]
fn finds_the_profiler_jar_among_the_cquery_files() {
    let files = "bazel-out/x/idea-async-profiler.jar\n\
                 external/lib+http/file/async-profiler-4.5-0.jar\n\
                 external/lib+http/file/async-profiler-4.5-0-sources.jar\n";
    assert_eq!(profiler_jar(files), Some("external/lib+http/file/async-profiler-4.5-0.jar"));
    assert_eq!(profiler_jar("bazel-out/x/idea-async-profiler.jar\n"), None);
}
