load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xslt-debugger-engine-impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "lib",
    ],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath/xslt-rt[module]",
        "//tools/idea/plugins/xslt-debugger/engine:xslt-debugger-engine[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:Saxon-6.5.5",
        "//tools/idea/.idea/libraries:Saxon-9HE",
        "//tools/idea/.idea/libraries:Xalan-2.7.1",
    ],
)
