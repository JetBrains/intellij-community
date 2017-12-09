load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dom-tests",
    iml_files = ["dom-tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_srcs = ["tests"],
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
        "//tools/idea/xml/dom-impl[module]",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:java-impl[module, test]",
    ],
)
