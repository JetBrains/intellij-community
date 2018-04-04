load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.maven.artifactResolver.m2",
    srcs = ["src"],
    iml_files = ["intellij.maven.artifactResolver.m2.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/maven/artifact-resolver/common:intellij.maven.artifactResolver.common"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven2-server-impl/lib/maven2/lib:maven-2.2.1-uber",
        "//tools/idea/plugins/maven/artifact-resolver/common:intellij.maven.artifactResolver.common[module]",
    ],
)
