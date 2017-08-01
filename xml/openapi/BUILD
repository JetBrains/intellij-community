load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-openapi",
    srcs = ["src"],
    iml_files = ["xml-openapi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/xml-psi-api",
        "//tools/idea/xml/xml-analysis-api",
        "//tools/idea/xml/xml-structure-view-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea/xml/xml-analysis-api[module]",
        "//tools/idea/xml/xml-structure-view-api[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
