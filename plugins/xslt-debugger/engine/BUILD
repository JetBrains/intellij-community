load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xslt-debugger-engine",
    # do not sort: must match IML order
    srcs = [
        "src",
        "lib",
    ],
    iml_files = ["xslt-debugger-engine.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Trove4j"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath/xslt-rt[module]",
        "//tools/idea/plugins/xslt-debugger/engine:lib/rmi-stubs",
        "//tools/idea/.idea/libraries:Trove4j",
    ],
)

java_import(
    name = "lib/rmi-stubs",
    jars = ["lib/rmi-stubs.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)
