load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.textmate",
    # do not sort: must match IML order
    srcs = [
        "src",
        "lib/themes",
        "lib/bundles",
    ],
    iml_files = ["intellij.textmate.iml"],
    test_resources = ["testResources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//prebuilts/tools/common/m2/repository/org/jruby/jcodings/jcodings/1.0.43:jar",
        "//prebuilts/tools/common/m2/repository/org/jruby/joni/joni/2.1.26:jar",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/spellchecker:intellij.spellchecker[module]",
    ],
)
