load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.xml.structureView",
    srcs = ["src"],
    iml_files = ["intellij.xml.structureView.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/xml/xml-psi-api:intellij.xml.psi"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module]",
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
    ],
)
