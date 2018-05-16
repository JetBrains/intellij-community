load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xslt.debugger.engine",
    # do not sort: must match IML order
    srcs = [
        "src",
        "lib",
    ],
    iml_files = ["intellij.xslt.debugger.engine.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Trove4j"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath/xslt-rt:intellij.xslt.debugger.rt[module]",
        "//tools/idea/plugins/xslt-debugger/engine/lib:rmi-stubs",
        "//tools/idea/.idea/libraries:Trove4j",
    ],
)
