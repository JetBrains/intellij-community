load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "stream-debugger",
    srcs = ["src"],
    iml_files = ["stream-debugger.iml"],
    resources = ["resources"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:StreamEx",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/platform/xdebugger-impl[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/openapi[module]",
    ],
)
