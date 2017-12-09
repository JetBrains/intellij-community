load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "stats-collector",
    srcs = ["src"],
    iml_files = ["stats-collector.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/plugins/stats-collector/log-events[module]",
    ],
)
