load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-tests",
    iml_files = ["tasks-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:spellchecker",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/tasks/tasks-api[module]",
        "//tools/idea/plugins/tasks/tasks-core[module]",
        "//tools/idea/plugins/tasks/tasks-java[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:compiler-impl_and_others[module, test]",
        "//tools/idea:vcs-impl[module]",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea/plugins/xslt-debugger[module]",
        "//tools/idea:xdebugger-impl[module, test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/tasks/tasks-core/jira[module, test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/plugins/git4idea[module, test]",
        "//tools/idea/plugins/hg4idea[module, test]",
        "//tools/idea/platform/vcs-tests[module, test]",
    ],
)
