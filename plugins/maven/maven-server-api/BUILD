load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.maven.server",
    srcs = ["src"],
    iml_files = ["intellij.maven.server.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/plugins/maven/maven-server-api/lib:lucene-core-2.4.1",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/plugins/maven/maven-server-api/lib:lucene-core-2.4.1",
    ],
)
