load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "settings-repository",
    # do not sort: must match IML order
    srcs = [
        "resources",
        "src",
    ],
    iml_files = ["settings-repository.iml"],
    package_prefixes = {"src": "org.jetbrains.settingsRepository"},
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/settings-repository/lib:jgit"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/RegExpSupport",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/plugins/settings-repository/lib:jgit",
        "//tools/idea/platform/vcs-impl[module]",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/plugins/settings-repository/lib:jackson-annotations-2.8.1",
        "//tools/idea/plugins/settings-repository/lib:jackson-core-2.8.1",
        "//tools/idea/plugins/settings-repository/lib:jackson-databind-2.8.1",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/platform/configuration-store-impl[module]",
        "//tools/idea/.idea/libraries:miglayout-swing",
        "//tools/idea/platform/credential-store[module]",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
