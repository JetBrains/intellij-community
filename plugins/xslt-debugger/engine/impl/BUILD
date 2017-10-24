load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xslt-debugger-engine-impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "lib",
    ],
    iml_files = ["xslt-debugger-engine-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath/xslt-rt[module]",
        "//tools/idea/plugins/xslt-debugger/engine:xslt-debugger-engine[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/plugins/xslt-debugger/engine/impl/lib:saxon",
        "//tools/idea/plugins/xslt-debugger/engine/impl/lib:saxon9he",
        "//tools/idea/plugins/xslt-debugger/engine/impl/lib:serializer",
        "//tools/idea/plugins/xslt-debugger/engine/impl/lib:xalan",
    ],
)
