load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xslt.debugger.engine.impl",
    # do not sort: must match IML order
    srcs = [
        "src",
        "lib",
    ],
    iml_files = ["intellij.xslt.debugger.engine.impl.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath/xslt-rt:intellij.xslt.debugger.rt[module]",
        "//tools/idea/plugins/xslt-debugger/engine:intellij.xslt.debugger.engine[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/plugins/xslt-debugger/engine/impl/lib:saxon",
        "//tools/idea/plugins/xslt-debugger/engine/impl/lib:saxon9he",
        "//prebuilts/tools/common/m2/repository/xalan/serializer/2.7.2:jar",
        "//prebuilts/tools/common/m2/repository/xalan/xalan/2.7.2:jar",
    ],
)
