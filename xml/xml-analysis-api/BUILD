load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xml.analysis",
    srcs = ["src"],
    iml_files = ["intellij.xml.analysis.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis",
        "//tools/idea/platform/core-api:intellij.platform.core",
        "//tools/idea/platform/extensions:intellij.platform.extensions",
        "//tools/idea/platform/util:intellij.platform.util",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module]",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
    ],
)
