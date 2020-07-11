load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xslt.debugger.rt",
    srcs = ["src"],
    iml_files = ["intellij.xslt.debugger.rt.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Trove4j"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath/xslt-rt:intellij.xpath.rt[module]",
        "//tools/idea/plugins/xslt-debugger/engine/lib:rmi-stubs",
        "//tools/idea/.idea/libraries:Trove4j",
        "//prebuilts/studio/jdk:linux/jre/lib/rt",
    ],
)
