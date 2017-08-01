load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "maven-jps-plugin",
    srcs = ["src"],
    iml_files = ["maven-jps-plugin.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea/plugins/maven/lib:plexus-utils-2.0.6",
        "//tools/idea/jps/model-serialization:jps-serialization-tests[module, test]",
        "//tools/idea/plugins/maven/aether-dependency-resolver[module]",
    ],
)
