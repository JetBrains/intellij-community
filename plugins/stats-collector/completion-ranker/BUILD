load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.statsCollector.completionRanker",
    srcs = ["src"],
    iml_files = ["intellij.statsCollector.completionRanker.iml"],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/plugins/stats-collector/features:intellij.statsCollector.features[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:completion-ranking-java",
        "//tools/idea/.idea/libraries:completion-ranking-python",
        "//tools/idea/.idea/libraries:completion-ranking-kotlin",
    ],
)
