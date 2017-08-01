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
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/settings-repository/lib:jgit"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/plugins/settings-repository/lib:jgit",
        "//tools/idea/platform/projectModel-impl[module]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:Log4J",
        "//tools/idea/plugins/settings-repository/lib:jackson-annotations-2.8.1",
        "//tools/idea/plugins/settings-repository/lib:jackson-core-2.8.1",
        "//tools/idea/plugins/settings-repository/lib:jackson-databind-2.8.1",
        "//tools/idea/.idea/libraries:Slf4j",
        "//tools/idea/.idea/libraries:miglayout-swing",
        "//tools/idea/.idea/libraries:Guava",
    ],
)
