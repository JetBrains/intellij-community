load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.resources",
    srcs = ["src"],
    iml_files = ["intellij.java.resources.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/community-resources:intellij.idea.community.resources",
    ],
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
        "//tools/idea/platform/platform-resources:intellij.platform.resources[module]",
        "//tools/idea/images:intellij.platform.images[module]",
        "//tools/idea/community-resources:intellij.idea.community.resources[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en[module]",
    ],
)
