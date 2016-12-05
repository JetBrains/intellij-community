load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "maven32-server-impl",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven-server-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/.idea/libraries:Maven32",
        "//tools/idea/plugins/maven/maven3-server-common/lib:nexus-indexer-3.0.4",
        "//tools/idea/plugins/maven/maven3-server-common/lib:maven-dependency-tree-1.2",
        "//tools/idea/plugins/maven:maven_and_others[module]",
        "//tools/idea/plugins/maven/maven3-server-common/lib:archetype-common-2.2",
        "//tools/idea/plugins/maven/maven3-server-common/lib:archetype-catalog-2.2",
        "//tools/idea/plugins/maven/maven3-server-common/lib:nexus-indexer-artifact-1.0.1",
        "//tools/idea/plugins/maven/maven3-server-common[module]",
    ],
)
