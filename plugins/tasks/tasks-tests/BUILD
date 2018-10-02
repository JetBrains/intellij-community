load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.tasks.tests",
    iml_files = ["intellij.tasks.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea:intellij.platform.jps.build_and_others"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/tasks/tasks-api:intellij.tasks[module]",
        "//tools/idea/plugins/tasks/tasks-core:intellij.tasks.core[module]",
        "//tools/idea/plugins/tasks/tasks-java:intellij.tasks.java[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea/plugins/xslt-debugger:intellij.xslt.debugger[module]",
        "//tools/idea/platform/xdebugger-impl:intellij.platform.debugger.impl[module, test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/tasks/tasks-core/jira:intellij.tasks.jira[module, test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/plugins/git4idea:intellij.vcs.git[module, test]",
        "//tools/idea/plugins/hg4idea:intellij.vcs.hg[module, test]",
        "//tools/idea/platform/vcs-tests:intellij.platform.vcs.tests[module, test]",
        "//tools/idea/platform/tasks-platform-api:intellij.platform.tasks[module, test]",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl[module, test]",
        "//tools/idea/.idea/libraries:http-client-3.1[test]",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:http-client[test]",
    ],
)
