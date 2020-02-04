load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xml",
    srcs = ["src"],
    iml_files = ["intellij.xml.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi",
        "//tools/idea/xml/xml-analysis-api:intellij.xml.analysis",
        "//tools/idea/xml/xml-structure-view-api:intellij.xml.structureView",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module]",
        "//tools/idea/xml/xml-analysis-api:intellij.xml.analysis[module]",
        "//tools/idea/xml/xml-structure-view-api:intellij.xml.structureView[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
