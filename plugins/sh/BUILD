load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.sh",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.sh.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
    ],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport:intellij.regexp",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea/plugins/terminal:intellij.terminal[module]",
        "//tools/idea/.idea/libraries:pty4j",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
    ],
)
