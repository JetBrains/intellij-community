load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.relaxng",
    srcs = ["src"],
    iml_files = ["intellij.relaxng.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
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
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/xml/relaxng/lib:rngom-20051226-patched",
        "//prebuilts/tools/common/m2/repository/isorelax/isorelax/20030108:jar",
        "//tools/idea/xml/relaxng/lib:trang-core",
        "//tools/idea/xml/relaxng/lib:jing",
    ],
)
