load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.statsCollector.completionRanker",
    srcs = ["src"],
    iml_files = ["intellij.statsCollector.completionRanker.iml"],
    test_srcs = ["test"],
    test_tags = ["no_test_windows"],  # b/137421177
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/plugins/stats-collector/features:intellij.statsCollector.features[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/intellij/deps/completion/completion-ranking-java/0.0.7:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/intellij/deps/completion/completion-ranking-kotlin/0.0.2:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/intellij/deps/completion/completion-ranking-python/0.0.4:jar",
    ],
)
