load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xslt.debugger",
    srcs = ["src"],
    iml_files = ["intellij.xslt.debugger.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/images:intellij.platform.images"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath:intellij.xpath[module]",
        "//tools/idea/plugins/xslt-debugger/engine:intellij.xslt.debugger.engine[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/xdebugger-api:intellij.platform.debugger[module]",
        "//tools/idea/platform/xdebugger-impl:intellij.platform.debugger.impl[module]",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
    ],
)
