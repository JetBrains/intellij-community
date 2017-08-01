load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "maven-artifact-resolver-m3",
    srcs = ["src"],
    iml_files = ["maven-artifact-resolver-m3.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/maven/artifact-resolver/common:maven-artifact-resolver-common"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:aether-api-1.13.1",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:maven-core-3.0.5",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:maven-artifact-3.0.5",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:plexus-component-annotations-1.5.5",
        "//tools/idea/plugins/maven/artifact-resolver/common:maven-artifact-resolver-common[module]",
    ],
)
