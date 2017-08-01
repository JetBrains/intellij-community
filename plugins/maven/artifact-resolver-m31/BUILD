load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "maven-artifact-resolver-m31",
    srcs = ["src"],
    iml_files = ["maven-artifact-resolver-m31.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/maven/artifact-resolver/common:maven-artifact-resolver-common"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:maven-core-3.0.5",
        "//tools/idea/plugins/maven/maven30-server-impl/lib/maven3/lib:plexus-component-annotations-1.5.5",
        "//tools/idea/plugins/maven/artifact-resolver-m31/lib/eclipse-aether:aether-api-0.9.0.M2",
        "//tools/idea/plugins/maven/artifact-resolver/common:maven-artifact-resolver-common[module]",
    ],
)
