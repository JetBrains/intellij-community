load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.maven.artifactResolver.m3",
    srcs = ["src"],
    iml_files = ["intellij.maven.artifactResolver.m3.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/maven/artifact-resolver/common:intellij.maven.artifactResolver.common"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:aether-api-1.13.1",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:maven-core-3.0.5",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:maven-artifact-3.0.5",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:plexus-component-annotations-1.5.5",
        "//tools/idea/plugins/maven/artifact-resolver/common:intellij.maven.artifactResolver.common[module]",
    ],
)
